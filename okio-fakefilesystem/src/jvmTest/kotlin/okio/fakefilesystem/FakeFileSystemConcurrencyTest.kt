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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import okio.Path.Companion.toPath
import okio.buffer

/**
 * Several threads sharing one [FakeFileSystem] must be able to open and close handles on
 * independent paths at the same time without corrupting its open-file registry.
 */
class FakeFileSystemConcurrencyTest {
  /**
   * Nine real threads per trial each open and close their own file 10,000 times: one through
   * [FakeFileSystem.source], which looks the path up in the registry before it opens it, and eight
   * through [FakeFileSystem.openReadOnly]. No two threads ever touch the same path, so every
   * failure here is the registry itself being torn. Twenty independent file systems are tried.
   */
  @Test
  fun concurrentDistinctFileOpenClose() {
    val trials = 20
    val callers = 9
    val operations = 10_000
    var failedRuns = 0
    repeat(trials) { trial ->
      val fileSystem = FakeFileSystem()
      val paths = (0 until callers).map { "/file-$it".toPath() }
      paths.forEach { path -> fileSystem.write(path) { writeUtf8(path.name) } }
      val errors = ConcurrentLinkedQueue<Throwable>()
      val start = CountDownLatch(1)
      val stop = AtomicBoolean(false)
      val threads = paths.mapIndexed { index, path ->
        Thread(
          {
            try {
              start.await()
              repeat(operations) {
                if (stop.get()) return@Thread
                if (index == 0) {
                  fileSystem.source(path).buffer().use { source ->
                    assertEquals(path.name, source.readUtf8())
                  }
                } else {
                  fileSystem.openReadOnly(path).use { handle ->
                    assertEquals(path.name.length.toLong(), handle.size())
                  }
                }
              }
            } catch (t: Throwable) {
              errors.add(t)
              stop.set(true)
            }
          },
          "fakefs-$trial-$index",
        )
      }
      try {
        threads.forEach { it.start() }
        start.countDown()
        threads.forEach { it.join() }
        try {
          fileSystem.checkNoOpenFiles()
          assertEquals(emptyList(), fileSystem.openPaths)
        } catch (t: Throwable) {
          errors.add(t)
        }
        if (errors.isNotEmpty()) {
          failedRuns++
          println("trial ${trial + 1}: FAIL (${errors.size} errors)")
          errors.forEach { it.printStackTrace(System.out) }
        } else {
          println("trial ${trial + 1}: PASS")
        }
      } finally {
        stop.set(true)
        start.countDown()
        threads.forEach { it.join() }
      }
    }
    println(
      "RESULT: $failedRuns failures / $trials runs; " +
        "$callers callers x $operations operations maximum per run",
    )
    assertEquals(
      0,
      failedRuns,
      "Concurrent distinct-file opens and closes must preserve the registry",
    )
  }
}
