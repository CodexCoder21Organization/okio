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

class FakeFileSystemIndexedAccessTest {
  @Test
  fun distantReadsAndBoundaryMutationsKeepIndependentViews() {
    val fs = FakeFileSystem().apply {
      allowReadsWhileWriting = true
      allowWritesWhileWriting = true
    }
    val path = "/indexed-access".toPath()
    try {
      fs.openReadWrite(path, mustCreate = true).use { original ->
        val before = ByteArray(3 * 65536 + 3) { (it * 31 + 7).toByte() }
        original.write(0, before, 0, before.size)
        fs.openReadOnly(path).use { reader ->
          fs.openReadWrite(path, mustExist = true).use { replacement ->
            val sample = ByteArray(17)
            for (offset in listOf(131069, 65531, 196607, 0, 65536)) {
              val count = minOf(sample.size, before.size - offset)
              assertEquals(count, original.read(offset.toLong(), sample, 0, count))
              assertContentEquals(before.copyOfRange(offset, offset + count), sample.copyOf(count))
            }
            val patch = ByteArray(65536 + 17) { 93 }
            original.write(65531, patch, 2, patch.size - 4)
            val expected = before.copyOf()
            patch.copyInto(expected, 65531, 2, patch.size - 2)
            patch.fill(0)
            val actual = ByteArray(before.size)
            assertEquals(actual.size, reader.read(0, actual, 0, actual.size))
            assertContentEquals(expected, actual)
            assertEquals(actual.size, replacement.read(0, actual, 0, actual.size))
            assertContentEquals(before, actual)
            for (size in listOf(65537L, 65536L, 65535L, 0L)) {
              original.resize(size)
              original.resize(before.size.toLong())
              expected.fill(0, size.toInt())
              assertEquals(actual.size, reader.read(0, actual, 0, actual.size))
              assertContentEquals(expected, actual)
              assertEquals(actual.size, replacement.read(0, actual, 0, actual.size))
              assertContentEquals(before, actual)
            }
            original.write(4L * 65536 + 1, byteArrayOf(9), 0, 1)
            val gap = ByteArray(65535)
            assertEquals(gap.size, reader.read(before.size.toLong(), gap, 0, gap.size))
            assertContentEquals(ByteArray(gap.size - 1) + byteArrayOf(9), gap)
            assertEquals(before.size.toLong(), replacement.size())
          }
        }
      }
    } finally {
      fs.checkNoOpenFiles()
    }
  }
}
