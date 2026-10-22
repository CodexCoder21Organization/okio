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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import okio.IOException
import okio.Path.Companion.toPath

class FakeFileSystemDirectoryTest {
  /** A clock that only moves when the test advances it. */
  private class SteppingClock : Clock {
    var millis = 1_000L

    override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
  }

  /**
   * Creating a directory that already exists, without requiring it to be new, must leave it as it
   * is: its children, their contents, and the directory's own metadata and extras.
   */
  @Test
  fun createDirectoryKeepsAnExistingDirectory() {
    val clock = SteppingClock()
    val fileSystem = FakeFileSystem(clock)
    val parent = "/parent".toPath()
    val child = parent / "child"
    val file = parent / "file"
    fileSystem.createDirectories(child)
    fileSystem.write(file) { writeUtf8("content") }
    fileSystem.setExtra(parent, String::class, "extra on parent")
    val before = fileSystem.metadata(parent)
    assertEquals("extra on parent", before.extra(String::class))

    // A replacement directory would be stamped with this later time.
    clock.millis += 60_000L
    fileSystem.createDirectory(parent, mustCreate = false)

    // FileMetadata has no equals(); its toString() lists every field, extras included.
    assertEquals(before.toString(), fileSystem.metadata(parent).toString())
    assertEquals(1_000L, fileSystem.metadata(parent).createdAtMillis)
    assertEquals("extra on parent", fileSystem.metadata(parent).extra(String::class))
    assertEquals(listOf(child, file), fileSystem.list(parent))
    assertEquals("content", fileSystem.read(file) { readUtf8() })
    assertEquals(true, fileSystem.metadata(child).isDirectory)
    fileSystem.checkNoOpenFiles()
  }

  /** Requiring a new directory where one already exists fails and changes nothing. */
  @Test
  fun createDirectoryMustCreateRejectsAnExistingDirectory() {
    val clock = SteppingClock()
    val fileSystem = FakeFileSystem(clock)
    val parent = "/parent".toPath()
    val file = parent / "file"
    fileSystem.createDirectory(parent)
    fileSystem.write(file) { writeUtf8("content") }
    val before = fileSystem.metadata(parent)

    clock.millis += 60_000L
    val error = assertFailsWith<IOException> {
      fileSystem.createDirectory(parent, mustCreate = true)
    }

    assertEquals("already exists: /parent", error.message)
    // FileMetadata has no equals(); its toString() lists every field, extras included.
    assertEquals(before.toString(), fileSystem.metadata(parent).toString())
    assertEquals(1_000L, fileSystem.metadata(parent).createdAtMillis)
    assertEquals(listOf(file), fileSystem.list(parent))
    assertEquals("content", fileSystem.read(file) { readUtf8() })
    fileSystem.checkNoOpenFiles()
  }
}
