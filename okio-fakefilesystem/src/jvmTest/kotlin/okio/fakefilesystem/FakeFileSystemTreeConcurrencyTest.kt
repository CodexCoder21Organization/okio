/*
 * Copyright (C) 2026 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okio.fakefilesystem

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import okio.Path
import okio.Path.Companion.toPath

/**
 * Threads that change independent paths of one [FakeFileSystem] must not lose each other's
 * entries: creating a root, a directory, a file, a symlink or moving an entry has to be one
 * indivisible step against the directory tree, just like the open-file registry.
 */
class FakeFileSystemTreeConcurrencyTest {
  /**
   * Pauses one chosen thread inside its first clock read, so a test can place another thread's
   * complete operation exactly there. Every other read returns immediately.
   */
  private class PausingClock : Clock {
    @Volatile var pausedThread: Thread? = null
    val paused = CountDownLatch(1)
    val release = CountDownLatch(1)
    private val pausedOnce = AtomicBoolean(false)

    override fun now(): Instant {
      if (Thread.currentThread() === pausedThread && pausedOnce.compareAndSet(false, true)) {
        paused.countDown()
        release.await()
      }
      return Instant.fromEpochMilliseconds(0)
    }
  }

  /**
   * Starts [paused] on its own thread and holds it inside its first clock read while [interleaved]
   * runs to completion on another thread, then lets [paused] finish. The interleaved operation is
   * independent of the paused one, so it must neither wait for it nor be undone by it.
   */
  private fun interleave(clock: PausingClock, paused: () -> Unit, interleaved: () -> Unit) {
    val pausedFailure = AtomicReference<Throwable?>()
    val interleavedFailure = AtomicReference<Throwable?>()
    val pausedThread = Thread({
      try {
        paused()
      } catch (t: Throwable) {
        pausedFailure.set(t)
      }
    }, "paused")
    pausedThread.isDaemon = true
    clock.pausedThread = pausedThread
    val interleavedThread = Thread({
      try {
        interleaved()
      } catch (t: Throwable) {
        interleavedFailure.set(t)
      }
    }, "interleaved")
    interleavedThread.isDaemon = true
    try {
      pausedThread.start()
      assertTrue(clock.paused.await(30, TimeUnit.SECONDS), "the paused operation never read the clock")
      interleavedThread.start()
      interleavedThread.join(30_000)
      assertTrue(
        !interleavedThread.isAlive,
        "an independent operation waited for one paused inside the clock: the clock was read under a lock",
      )
    } finally {
      clock.release.countDown()
      pausedThread.join(30_000)
      interleavedThread.join(30_000)
    }
    assertTrue(!pausedThread.isAlive, "the paused operation did not finish after it was released")
    interleavedFailure.get()?.let { throw AssertionError("interleaved operation failed", it) }
    pausedFailure.get()?.let { throw AssertionError("paused operation failed", it) }
  }

  /** Two writers creating files in a fresh file system must not each create their own root. */
  @Test
  fun rootCreationKeepsAnIndependentFile() {
    val clock = PausingClock()
    val fileSystem = FakeFileSystem(clock)
    val a = "/a".toPath()
    val b = "/b".toPath()

    interleave(
      clock,
      paused = { fileSystem.write(a) { writeUtf8("a") } },
      interleaved = {
        fileSystem.write(b) { writeUtf8("b") }
        assertEquals("b", fileSystem.read(b) { readUtf8() })
      },
    )

    assertEquals("a", fileSystem.read(a) { readUtf8() })
    assertEquals("b", fileSystem.read(b) { readUtf8() })
    assertTrue(fileSystem.exists(a))
    assertTrue(fileSystem.exists(b))
    assertEquals(listOf(a, b), fileSystem.list("/".toPath()))
    fileSystem.checkNoOpenFiles()
  }

  /** A symlink created at a fresh root must not replace the root another thread created. */
  @Test
  fun rootCreationBySymlinkKeepsAnIndependentFile() {
    val clock = PausingClock()
    val fileSystem = FakeFileSystem(clock)
    fileSystem.allowSymlinks = true
    val link = "/link".toPath()
    val b = "/b".toPath()

    interleave(
      clock,
      paused = { fileSystem.createSymlink(link, "/target".toPath()) },
      interleaved = { fileSystem.write(b) { writeUtf8("b") } },
    )

    assertEquals("b", fileSystem.read(b) { readUtf8() })
    assertEquals("/target".toPath(), fileSystem.metadata(link).symlinkTarget)
    assertEquals(listOf(b, link), fileSystem.list("/".toPath()))
    fileSystem.checkNoOpenFiles()
  }

  /**
   * Two callers creating independent directories that share a new parent must both keep their
   * contents: the caller that finds the parent missing first must not replace the parent the other
   * caller created and populated in the meantime.
   */
  @Test
  fun directoryCreationKeepsAnIndependentSibling() {
    val clock = PausingClock()
    val fileSystem = FakeFileSystem(clock)
    fileSystem.write("/existing".toPath()) { writeUtf8("root exists") }
    val parent = "/parent".toPath()
    val file = parent / "b" / "file"

    interleave(
      clock,
      paused = { fileSystem.createDirectories(parent / "a") },
      interleaved = {
        fileSystem.createDirectories(parent / "b")
        fileSystem.write(file) { writeUtf8("b") }
      },
    )

    assertEquals("b", fileSystem.read(file) { readUtf8() })
    assertTrue(fileSystem.metadata(parent / "a").isDirectory)
    assertEquals(listOf(parent / "a", parent / "b"), fileSystem.list(parent))
    fileSystem.checkNoOpenFiles()
  }

  /**
   * Eight threads each create files, directories and symlinks and move entries, all under one
   * shared existing parent but never on the same name. Every entry every thread made must survive
   * with its own content and type.
   */
  @Test
  fun independentChildrenOfASharedParentAreAllKept() {
    sharedParentTrials(withLister = false)
  }

  /**
   * Like [independentChildrenOfASharedParentAreAllKept], while a ninth thread keeps listing the
   * shared parent. Listing must neither fail nor disturb the changes.
   */
  @Test
  fun listingASharedParentWhileIndependentChildrenChange() {
    sharedParentTrials(withLister = true)
  }

  private fun sharedParentTrials(withLister: Boolean) {
    val trials = 20
    val workers = 8
    val operations = 500
    val listings = 200
    var failedRuns = 0
    repeat(trials) { trial ->
      val fileSystem = FakeFileSystem()
      fileSystem.allowSymlinks = true
      val parent = "/parent".toPath()
      fileSystem.createDirectory(parent)
      val errors = ConcurrentLinkedQueue<Throwable>()
      val start = CountDownLatch(1)
      val stop = AtomicBoolean(false)
      val workerThreads = (0 until workers).map { worker ->
        Thread(
          {
            try {
              start.await()
              repeat(operations) { i ->
                if (stop.get()) return@Thread
                val file = parent / "file-$worker-$i"
                fileSystem.write(file) { writeUtf8("$worker-$i") }
                fileSystem.createDirectory(parent / "dir-$worker-$i")
                fileSystem.createSymlink(parent / "link-$worker-$i", file)
                fileSystem.atomicMove(file, parent / "moved-$worker-$i")
              }
            } catch (t: Throwable) {
              errors.add(t)
              stop.set(true)
            }
          },
          "fakefs-tree-$trial-$worker",
        ).apply { isDaemon = true }
      }
      val lister = Thread(
        {
          try {
            start.await()
            // A bounded number of listings, so the lister cannot crowd the workers out.
            repeat(listings) {
              if (stop.get()) return@Thread
              fileSystem.list(parent)
            }
          } catch (t: Throwable) {
            errors.add(t)
            stop.set(true)
          }
        },
        "fakefs-tree-lister-$trial",
      ).apply { isDaemon = true }
      try {
        workerThreads.forEach { it.start() }
        if (withLister) lister.start()
        start.countDown()
        workerThreads.forEach { it.join(60_000) }
        if (withLister) lister.join(60_000)
        // Record stragglers before stopping them, so a stopped worker's missing entries are never
        // mistaken for lost ones.
        (workerThreads + lister).filter { it.isAlive }.forEach {
          errors.add(AssertionError("${it.name} did not finish"))
        }
        stop.set(true)
        if (errors.isEmpty()) {
          errors.addAll(missingEntries(fileSystem, parent, workers, operations))
        }
        if (errors.isNotEmpty()) {
          failedRuns++
          println("tree trial ${trial + 1}: FAIL (${errors.size} errors)")
          errors.take(10).forEach { it.printStackTrace(System.out) }
        } else {
          println("tree trial ${trial + 1}: PASS")
        }
      } finally {
        stop.set(true)
        start.countDown()
      }
    }
    println(
      "TREE RESULT (lister=$withLister): $failedRuns failures / $trials runs; " +
        "$workers workers x $operations x 4 operations per run" +
        if (withLister) ", 1 lister x $listings listings" else "",
    )
    assertEquals(0, failedRuns, "Independent changes under one parent must all be kept")
  }

  private fun missingEntries(
    fileSystem: FakeFileSystem,
    parent: Path,
    workers: Int,
    operations: Int,
  ): List<Throwable> {
    val problems = mutableListOf<Throwable>()
    val expected = mutableListOf<Path>()
    for (worker in 0 until workers) {
      for (i in 0 until operations) {
        val moved = parent / "moved-$worker-$i"
        val dir = parent / "dir-$worker-$i"
        val link = parent / "link-$worker-$i"
        expected += listOf(moved, dir, link)
        try {
          assertEquals("$worker-$i", fileSystem.read(moved) { readUtf8() })
          assertTrue(fileSystem.metadata(dir).isDirectory, "$dir is not a directory")
          assertEquals(parent / "file-$worker-$i", fileSystem.metadata(link).symlinkTarget)
        } catch (t: Throwable) {
          problems += AssertionError("entry for $worker-$i is wrong", t)
        }
      }
    }
    try {
      assertEquals(expected.sorted(), fileSystem.list(parent))
      fileSystem.checkNoOpenFiles()
    } catch (t: Throwable) {
      problems += t
    }
    return problems
  }
}
