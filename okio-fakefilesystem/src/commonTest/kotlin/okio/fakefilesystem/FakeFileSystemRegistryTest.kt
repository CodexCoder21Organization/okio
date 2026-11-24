/*
 * Copyright (C) 2020 Square, Inc.
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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import okio.Buffer
import okio.FileHandle
import okio.IOException
import okio.Path.Companion.toPath
import okio.buffer

/**
 * The open-file registry must behave as one indivisible unit: admission checks and registration
 * happen together, removal always happens, and observers see a consistent snapshot. These tests
 * drive that contract through the public API only.
 */
class FakeFileSystemRegistryTest {
  /**
   * A clock that opens a competing write handle on a chosen call, letting each test place the
   * re-entrant call at a different point inside an enclosing file system operation.
   */
  private class ReentrantClock : Clock {
    var fileSystem: FakeFileSystem? = null
    var openOnCall = -1
    var calls = 0
    var reentered: FileHandle? = null

    override fun now(): Instant {
      calls++
      if (calls == openOnCall) {
        openOnCall = -1 // Only re-enter once, so the nested call does not recurse forever.
        reentered = fileSystem!!.openReadWrite("/file".toPath())
      }
      return Instant.fromEpochMilliseconds(0)
    }
  }

  /**
   * A clock callback that runs while an open is in progress must not be able to slip a second
   * writer past the "already open for writing" check. Because the callback cannot know where in the
   * enclosing operation it will be invoked, this sweeps the re-entry across every clock call the
   * operation makes.
   */
  @Test
  fun clockReentryCannotAdmitTwoWriters() {
    var everReentered = false
    for (openOnCall in 1..6) {
      val clock = ReentrantClock()
      val fileSystem = FakeFileSystem(clock)
      clock.fileSystem = fileSystem
      val path = "/file".toPath()
      fileSystem.write(path) { writeUtf8("content") }

      clock.calls = 0
      clock.openOnCall = openOnCall
      var outer: FileHandle? = null
      try {
        try {
          outer = fileSystem.openReadWrite(path)
        } catch (expected: okio.IOException) {
          assertEquals("file is already open for writing $path", expected.message)
        }
        if (clock.reentered != null) everReentered = true
        // At most one writer may hold the path at any moment.
        assertEquals(
          listOf(path),
          fileSystem.openPaths,
          "re-entry on clock call $openOnCall admitted ${fileSystem.openPaths.size} writers",
        )
      } finally {
        clock.openOnCall = -1
        outer?.close()
        clock.reentered?.close()
      }
      fileSystem.checkNoOpenFiles()
      assertEquals(emptyList(), fileSystem.openPaths)
    }
    assertTrue(everReentered, "no clock call re-entered the file system, so nothing was exercised")
  }

  /**
   * Closing a handle must retire its registration even when the clock read that records the access
   * time fails, and the caller must still see that failure.
   */
  @Test
  fun clockFailureOnCloseStillRemovesRegistration() {
    var fail = false
    val clock = object : Clock {
      override fun now(): Instant {
        check(!fail) { "Cannot read the clock: injected close failure" }
        return Instant.fromEpochMilliseconds(0)
      }
    }
    val fileSystem = FakeFileSystem(clock)
    val path = "/file".toPath()
    fileSystem.write(path) { writeUtf8("content") }
    val handle = fileSystem.openReadOnly(path)
    try {
      fail = true
      val error = assertFailsWith<IllegalStateException> { handle.close() }
      assertEquals("Cannot read the clock: injected close failure", error.message)
    } finally {
      fail = false
    }
    assertEquals(emptyList(), fileSystem.openPaths)
    fileSystem.checkNoOpenFiles()
  }

  /**
   * A handle whose streams outlive it is retired by its last stream's close. When the clock read in
   * that deferred close fails, the failure must reach the caller and the registration must still
   * be removed, so later closes are harmless and the path can be opened for writing again.
   */
  @Test
  fun clockFailureOnDeferredSourceCloseStillRemovesRegistration() {
    val clock = FailingClock()
    val fileSystem = FakeFileSystem(clock)
    val path = "/file".toPath()
    fileSystem.write(path) { writeUtf8("content") }

    val handle = fileSystem.openReadOnly(path)
    val first = handle.source()
    val second = handle.source()
    handle.close()
    first.close()
    // The second source still holds the handle open.
    assertEquals(listOf(path), fileSystem.openPaths)

    clock.fail = true
    try {
      val error = assertFailsWith<IllegalStateException> { second.close() }
      assertEquals("Cannot read the clock: injected close failure", error.message)
      assertEquals(emptyList(), fileSystem.openPaths)

      // Repeating every close must not retire anything twice or read the clock again.
      second.close()
      first.close()
      handle.close()
    } finally {
      clock.fail = false
    }
    assertEquals(emptyList(), fileSystem.openPaths)
    fileSystem.checkNoOpenFiles()

    // Nothing still claims the path, so a writer is admitted.
    fileSystem.write(path) { writeUtf8("replaced") }
    assertEquals("replaced", fileSystem.read(path) { readUtf8() })
    fileSystem.checkNoOpenFiles()
  }

  /** Like [clockFailureOnDeferredSourceCloseStillRemovesRegistration], for a write handle's sinks. */
  @Test
  fun clockFailureOnDeferredSinkCloseStillRemovesRegistration() {
    val clock = FailingClock()
    val fileSystem = FakeFileSystem(clock)
    val path = "/file".toPath()

    val handle = fileSystem.openReadWrite(path)
    val first = handle.sink()
    val second = handle.sink(fileOffset = 3L)
    first.write(Buffer().writeUtf8("abc"), 3L)
    second.write(Buffer().writeUtf8("def"), 3L)
    handle.close()
    first.close()
    // The second sink still holds the handle, and therefore the write admission, open.
    assertEquals(listOf(path), fileSystem.openPaths)
    val blocked = assertFailsWith<IOException> { fileSystem.openReadWrite(path) }
    assertEquals("file is already open for writing $path", blocked.message)

    clock.fail = true
    try {
      val error = assertFailsWith<IllegalStateException> { second.close() }
      assertEquals("Cannot read the clock: injected close failure", error.message)
      assertEquals(emptyList(), fileSystem.openPaths)

      // Repeating every close must not retire anything twice or read the clock again.
      second.close()
      first.close()
      handle.close()
    } finally {
      clock.fail = false
    }
    assertEquals(emptyList(), fileSystem.openPaths)
    fileSystem.checkNoOpenFiles()
    assertEquals("abcdef", fileSystem.read(path) { readUtf8() })

    // Nothing still claims the path, so a new writer is admitted.
    fileSystem.write(path) { writeUtf8("replaced") }
    assertEquals("replaced", fileSystem.read(path) { readUtf8() })
    fileSystem.checkNoOpenFiles()
  }

  /** A clock that throws while [fail] is set. */
  private class FailingClock : Clock {
    var fail = false

    override fun now(): Instant {
      check(!fail) { "Cannot read the clock: injected close failure" }
      return Instant.fromEpochMilliseconds(0)
    }
  }

  /**
   * [FakeFileSystem.openPaths] is a snapshot the file system does not share: mutating it must not
   * change the registry, and a handle whose source is still open stays registered.
   */
  @Test
  fun openPathsIsASnapshotAndSourcesRetainRegistration() {
    val fileSystem = FakeFileSystem()
    val path = "/file".toPath()
    fileSystem.write(path) { writeUtf8("content") }
    val handle = fileSystem.openReadOnly(path)
    val source = handle.source().buffer()
    try {
      val snapshot = fileSystem.openPaths
      if (snapshot is MutableList) snapshot.clear()
      assertEquals(listOf(path), fileSystem.openPaths)

      // FileHandle stays open, and therefore registered, until its last stream is closed too.
      handle.close()
      assertEquals(listOf(path), fileSystem.openPaths)
      assertEquals("content", source.readUtf8())
    } finally {
      source.close()
      handle.close()
    }
    assertEquals(emptyList(), fileSystem.openPaths)
    fileSystem.checkNoOpenFiles()
  }
}
