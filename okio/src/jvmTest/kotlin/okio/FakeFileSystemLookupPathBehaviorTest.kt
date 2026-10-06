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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class FakeFileSystemLookupPathBehaviorTest {
  @Test
  fun ordinaryPathsAndMissingFinalComponents() {
    val fs = FakeFileSystem()
    try {
      fs.createDirectories("/a/b".toPath())
      assertNull(fs.metadataOrNull("/a/b/file".toPath()))
      fs.write("/a/b/file".toPath()) { writeUtf8("contents") }
      assertEquals("/a/b/file".toPath(), fs.canonicalize("/a/./b/../b/file".toPath(normalize = true)))
      assertEquals("/a/b/file".toPath(), fs.canonicalize("a/./b/../b/file".toPath()))
      assertEquals("contents", fs.read("/a/b/file".toPath()) { readUtf8() })
      assertEquals("/".toPath(), fs.canonicalize("/".toPath()))
      assertNull(fs.metadataOrNull("/absent/intermediate/file".toPath()))
      assertNull(fs.metadataOrNull("/absent".toPath()))
    } finally {
      fs.checkNoOpenFiles()
    }
  }

  @Test
  fun missingFinalDotDotPathKeepsMissingBehavior() {
    val fs = FakeFileSystem()
    try {
      fs.createDirectories("/a/b".toPath())
      val path = "/a/b/..".toPath()
      assertNull(fs.metadataOrNull(path))
      assertEquals(
        "no such file: /a/b/..",
        assertFailsWith<FileNotFoundException> { fs.canonicalize(path) }.message,
      )
    } finally {
      fs.checkNoOpenFiles()
    }
  }

  @Test
  fun notADirectoryReportsTheResolvedPrefix() {
    val fs = FakeFileSystem().apply { allowSymlinks = true }
    try {
      fs.createDirectories("/a".toPath())
      fs.write("/a/file".toPath()) { writeUtf8("contents") }
      assertEquals(
        "not a directory: /a/file",
        assertFailsWith<IOException> { fs.metadata("/a/file/child".toPath()) }.message,
      )
      fs.createSymlink("/a/link".toPath(), "file".toPath())
      assertEquals(
        "not a directory: /a/file",
        assertFailsWith<IOException> { fs.metadata("/a/link/child".toPath()) }.message,
      )
    } finally {
      fs.checkNoOpenFiles()
    }
  }

  @Test
  fun linkResolutionPreservesTheRemainingSuffixAndFinalLink() {
    val fs = FakeFileSystem().apply { allowSymlinks = true }
    try {
      fs.createDirectories("/real/parent".toPath())
      fs.createDirectories("/links".toPath())
      fs.write("/real/parent/file".toPath()) { writeUtf8("contents") }
      fs.createSymlink("/links/relative".toPath(), "../real".toPath())
      fs.createSymlink("/links/absolute".toPath(), "/links/relative".toPath())
      val linked = "/links/absolute/parent/file".toPath()
      assertEquals("/real/parent/file".toPath(), fs.canonicalize(linked))
      assertEquals("contents", fs.read(linked) { readUtf8() })
      val missing = "/links/absolute/parent/new".toPath()
      assertNull(fs.metadataOrNull(missing))
      fs.write(missing) { writeUtf8("new") }
      assertEquals("new", fs.read("/real/parent/new".toPath()) { readUtf8() })
      assertEquals("../real".toPath(), fs.metadata("/links/relative".toPath()).symlinkTarget)
      fs.delete("/links/relative".toPath())
      assertEquals("contents", fs.read("/real/parent/file".toPath()) { readUtf8() })
    } finally {
      fs.checkNoOpenFiles()
    }
  }

  @Test
  fun windowsPathsPreserveTheirRootAndErrorPrefix() {
    val fs = FakeFileSystem().apply { emulateWindows() }
    try {
      fs.createDirectories("C:\\a\\b".toPath())
      fs.write("C:\\a\\b\\file".toPath()) { writeUtf8("contents") }
      assertEquals("C:\\a\\b\\file".toPath(), fs.canonicalize("C:\\a\\b\\file".toPath()))
      assertEquals("C:\\".toPath(), fs.canonicalize("C:\\".toPath()))
      assertEquals(
        "not a directory: C:\\a\\b\\file",
        assertFailsWith<IOException> { fs.metadata("C:\\a\\b\\file\\child".toPath()) }.message,
      )
    } finally {
      fs.checkNoOpenFiles()
    }
  }
}
