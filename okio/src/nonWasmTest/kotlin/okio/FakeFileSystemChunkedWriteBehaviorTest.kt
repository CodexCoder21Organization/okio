/*
 * Copyright (c) 2026 Okio Authors
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
package okio

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class FakeFileSystemChunkedWriteBehaviorTest {
  @Test
  fun overwriteResizeAndReopenPreserveBytes() {
    val fs = FakeFileSystem()
    val path = "/ranges".toPath()
    val original = ByteArray(3 * 8192 + 17) { (it * 31 + 7).toByte() }
    try {
      fs.openReadWrite(path, mustCreate = true).use { handle ->
        handle.write(0, original, 0, original.size)
        val patch = ByteArray(8192 + 11) { 93 }
        val offset = 8192 - 3
        handle.write(offset.toLong(), patch, 2, patch.size - 4)
        patch.copyInto(original, offset, 2, patch.size - 2)
        patch.fill(0)
        val actual = ByteArray(original.size + 6) { 42 }
        assertEquals(original.size, handle.read(0, actual, 3, original.size))
        assertContentEquals(original, actual.copyOfRange(3, actual.size - 3))
        assertEquals(42.toByte(), actual.first())
        assertEquals(42.toByte(), actual.last())
        actual.fill(0)
        handle.resize(8192 + 5L)
        handle.resize(original.size.toLong())
        original.fill(0, 8192 + 5, original.size)
        assertEquals(original.size, handle.read(0, actual, 3, original.size))
        assertContentEquals(original, actual.copyOfRange(3, actual.size - 3))
        handle.write(original.size + 11L, byteArrayOf(9, 8, 7), 0, 3)
        val gap = ByteArray(14)
        assertEquals(14, handle.read(original.size.toLong(), gap, 0, gap.size))
        assertContentEquals(ByteArray(11) + byteArrayOf(9, 8, 7), gap)
        assertEquals(original.size + 14L, fs.metadata(path).size)
      }
      fs.openReadOnly(path).use { reopened ->
        assertEquals(original.size + 14L, reopened.size())
        assertEquals(-1, reopened.read(1L shl 32, ByteArray(1), 0, 1))
      }
    } finally {
      fs.checkNoOpenFiles()
    }
  }

  @Test
  fun replacementOpenKeepsPriorHandlesIndependent() {
    val fs = FakeFileSystem().apply {
      allowReadsWhileWriting = true
      allowWritesWhileWriting = true
    }
    val path = "/views".toPath()
    val original = ByteArray(8192 + 13) { 17 }
    try {
      fs.openReadWrite(path, mustCreate = true).use { first ->
        first.write(0, original, 0, original.size)
        fs.openReadOnly(path).use { reader ->
          fs.openReadWrite(path, mustExist = true).use { replacement ->
            replacement.write(8190, byteArrayOf(1, 2, 3, 4, 5), 0, 5)
            replacement.resize(original.size + 20L)
            val prior = ByteArray(original.size)
            assertEquals(prior.size, reader.read(0, prior, 0, prior.size))
            assertContentEquals(original, prior)
            assertEquals(original.size.toLong(), first.size())
            first.write(0, byteArrayOf(77), 0, 1)
            val current = ByteArray(6)
            assertEquals(current.size, replacement.read(0, current, 0, current.size))
            assertContentEquals(ByteArray(6) { 17 }, current)
            assertEquals(5, replacement.read(8190, current, 0, 5))
            assertContentEquals(byteArrayOf(1, 2, 3, 4, 5), current.copyOf(5))
          }
        }
      }
    } finally {
      fs.checkNoOpenFiles()
    }
  }

  @Test
  fun closedHandleCannotChangeStoredBytes() {
    val fs = FakeFileSystem()
    val path = "/closed".toPath()
    try {
      val handle = fs.openReadWrite(path)
      handle.use { it.write(0, byteArrayOf(1, 2, 3), 0, 3) }
      assertEquals("closed", assertFailsWith<IllegalStateException> { handle.write(0, byteArrayOf(9), 0, 1) }.message)
      assertEquals("closed", assertFailsWith<IllegalStateException> { handle.resize(0) }.message)
      fs.openReadOnly(path).use { reopened ->
        val actual = ByteArray(3)
        assertEquals(3, reopened.read(0, actual, 0, actual.size))
        assertContentEquals(byteArrayOf(1, 2, 3), actual)
      }
    } finally {
      fs.checkNoOpenFiles()
    }
  }
}
