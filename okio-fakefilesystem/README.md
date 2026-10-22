Okio Fake File System
---------------------

This module contains an in-memory file system for tests. Open file handles
support sequential chunked reads, writes, appends and overwrites with amortized
linear work. Random-access positioning has bounded work independent of file size. A
replacement writable open preserves the previous handles' independent view;
shrinking and regrowing either view does not change the other. Each byte-array
read observes a consistent file size and byte range during concurrent writes
and resize calls through the same file view. A negative resize size throws
`IllegalArgumentException("endIndex < beginIndex")` and leaves the size and bytes
unchanged. Metadata and new readers see complete contents while a replacement
writable open waits to copy an existing view.

```kotlin
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import okio.use

val fs = FakeFileSystem().apply { allowWritesWhileWriting = true }
val path = "/document".toPath()
fs.openReadWrite(path, mustCreate = true).use { original ->
  original.write(0, byteArrayOf(1, 2, 3), 0, 3)
  val rejection = runCatching { original.resize(-1) }.exceptionOrNull()
  check(rejection is IllegalArgumentException)
  check(original.size() == 3L)
  fs.openReadWrite(path, mustExist = true).use { replacement ->
    original.resize(1)
    original.resize(3)
    val bytes = ByteArray(3)
    check(replacement.read(0, bytes, 0, bytes.size) == 3)
    check(bytes.contentEquals(byteArrayOf(1, 2, 3)))
  }
}
fs.checkNoOpenFiles()
```

From the repository root, build with `./gradlew :okio-fakefilesystem:jvmJar`
and run the JVM tests with `./gradlew :okio-fakefilesystem:jvmTest`. The JVM task
includes deterministic sequential-read, random-read and overwrite work counts,
plus concurrent read/resize and independent-view regressions.
Common behavior tests also run on the configured JavaScript and native targets.
The [root README](../README.md#chunked-fakefilesystem-writes) documents the
compatible 3.4.0 candidate and its standalone build and test commands.
