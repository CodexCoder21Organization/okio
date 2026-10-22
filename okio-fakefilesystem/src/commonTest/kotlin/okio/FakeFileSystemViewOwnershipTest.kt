/*
 * Copyright (c) 2026 Okio Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okio

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class FakeFileSystemViewOwnershipTest {
  @Test
  fun originalShrinkAndRegrowPreservesReplacement() {
    for (size in listOf(3, 8192, 8195, 24579)) {
      val fs = FakeFileSystem().apply { allowWritesWhileWriting = true }
      val path = "/views".toPath()
      val original = ByteArray(size) { 17 }
      try {
        fs.openReadWrite(path, mustCreate = true).use { first ->
          first.write(0, original, 0, size)
          fs.openReadWrite(path, mustExist = true).use { replacement ->
            first.resize(size - 2L)
            first.resize(size.toLong())
            val actual = ByteArray(size)
            assertEquals(size, replacement.read(0, actual, 0, size))
            assertContentEquals(original, actual)
            assertEquals(size, first.read(0, actual, 0, size))
            original.fill(0, size - 2, size)
            assertContentEquals(original, actual)
          }
        }
      } finally {
        fs.checkNoOpenFiles()
      }
    }
  }

  @Test
  fun originalShrinkAndAppendPreservesReplacement() {
    val fs = FakeFileSystem().apply { allowWritesWhileWriting = true }
    val path = "/views".toPath()
    try {
      fs.openReadWrite(path, mustCreate = true).use { first ->
        first.write(0, byteArrayOf(1, 2, 3), 0, 3)
        fs.openReadWrite(path, mustExist = true).use { replacement ->
          first.resize(1)
          first.write(1, byteArrayOf(9, 8), 0, 2)
          val actual = ByteArray(3)
          assertEquals(3, replacement.read(0, actual, 0, 3))
          assertContentEquals(byteArrayOf(1, 2, 3), actual)
          replacement.resize(1)
          replacement.resize(3)
          assertEquals(3, first.read(0, actual, 0, 3))
          assertContentEquals(byteArrayOf(1, 9, 8), actual)
        }
      }
    } finally {
      fs.checkNoOpenFiles()
    }
  }

  @Test
  fun readsAfterOverwriteResizeAndCloseUseCurrentBytes() {
    val fs = FakeFileSystem().apply { allowReadsWhileWriting = true }
    val path = "/cursor".toPath()
    val original = ByteArray(3 * 8192 + 3) { (it * 31 + 7).toByte() }
    try {
      fs.openReadWrite(path, mustCreate = true).use { handle ->
        handle.write(0, original, 0, original.size)
        fs.openReadOnly(path).use { reader ->
          val actual = ByteArray(original.size)
          assertEquals(10, reader.read(8190, actual, 0, 10))
          handle.write(8191, byteArrayOf(1, 2, 3), 0, 3)
          byteArrayOf(1, 2, 3).copyInto(original, 8191)
          assertEquals(actual.size, reader.read(0, actual, 0, actual.size))
          assertContentEquals(original, actual)
          handle.resize(1)
          handle.resize(original.size.toLong())
          original.fill(0, 1)
          assertEquals(actual.size, reader.read(0, actual, 0, actual.size))
          assertContentEquals(original, actual)
        }
        // Closing another handle also releases its retained cursor without closing the file.
        val actual = ByteArray(original.size)
        assertEquals(actual.size, handle.read(0, actual, 0, actual.size))
        assertContentEquals(original, actual)
      }
    } finally {
      fs.checkNoOpenFiles()
    }
  }
}
