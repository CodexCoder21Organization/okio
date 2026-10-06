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
import kotlin.test.assertTrue
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class FakeFileSystemLookupCostTest {
  @Test
  fun lookupAllocationsGrowLinearlyWithPathDepth() {
    val fileSystem = FakeFileSystem()
    val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    assertTrue(bean.isThreadAllocatedMemorySupported)
    bean.isThreadAllocatedMemoryEnabled = true
    val threadId = Thread.currentThread().id
    val paths = listOf(64, 128).map { depth ->
      ("/" + List(depth) { "segment" }.joinToString("/")).toPath()
    }
    try {
      for (path in paths) fileSystem.createDirectories(path)
      // Warm both depths together so the measurement excludes class loading and compilation.
      repeat(500) {
        for (path in paths) check(fileSystem.metadata(path).isDirectory)
      }
      val allocations = paths.map { path ->
        val before = bean.getThreadAllocatedBytes(threadId)
        repeat(100) { check(fileSystem.metadata(path).isDirectory) }
        bean.getThreadAllocatedBytes(threadId) - before
      }
      assertTrue(
        allocations[1] <= allocations[0] * 3,
        "Doubling lookup depth must have linear allocation cost: " +
          "depth 64=${allocations[0]}, depth 128=${allocations[1]} bytes",
      )
    } finally {
      fileSystem.checkNoOpenFiles()
    }
  }
}
