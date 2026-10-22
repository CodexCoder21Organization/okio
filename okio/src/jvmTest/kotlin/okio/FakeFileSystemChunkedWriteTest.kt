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

import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class FakeFileSystemChunkedWriteTest {
  @Test
  fun chunkedWritesAllocateLinearly() {
    val counter = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    assertTrue(counter.isThreadAllocatedMemorySupported)
    val enabled = counter.isThreadAllocatedMemoryEnabled
    counter.isThreadAllocatedMemoryEnabled = true
    val thread = Thread.currentThread().id
    val chunk = ByteArray(8192) { 37 }
    val fs = FakeFileSystem()
    val path = "/chunked".toPath()
    val allocations = LongArray(2)
    try {
      // Warm both sizes before measuring the same work through FileHandle.
      repeat(6) { run ->
        val chunks = if (run % 2 == 0) 256 else 1024
        fs.openReadWrite(path, mustCreate = true).use { handle ->
          val before = counter.getThreadAllocatedBytes(thread)
          repeat(chunks) { handle.write(it.toLong() * chunk.size, chunk) }
          val allocated = counter.getThreadAllocatedBytes(thread) - before
          assertEquals(chunks.toLong() * chunk.size, handle.size())
          val actual = ByteArray(chunk.size)
          assertEquals(actual.size, handle.read(handle.size() - actual.size, actual))
          assertContentEquals(chunk, actual)
          if (run >= 4) allocations[run - 4] = allocated
        }
        fs.delete(path)
      }
      assertTrue(allocations[0] > 0)
      assertTrue(
        allocations[1] <= allocations[0] * 6,
        "Quadrupling chunked writes must allocate at most six times as much: " +
          "N=${allocations[0]} bytes, 4N=${allocations[1]} bytes",
      )
    } finally {
      try {
        fs.checkNoOpenFiles()
      } finally {
        counter.isThreadAllocatedMemoryEnabled = enabled
      }
    }
  }
}
