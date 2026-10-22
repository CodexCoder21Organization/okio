Okio Fake File System
---------------------

This module contains an in-memory file system for tests. Open file handles
support sequential chunked writes and reads with amortized linear work. A
replacement writable open preserves the previous handles' independent view;
shrinking and regrowing either view does not change the other. Each byte-array
read observes a consistent file size and byte range during concurrent writes
and resize calls through the same file view.

```kotlin
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import okio.use

val fs = FakeFileSystem().apply { allowWritesWhileWriting = true }
val path = "/document".toPath()
fs.openReadWrite(path, mustCreate = true).use { original ->
  original.write(0, byteArrayOf(1, 2, 3), 0, 3)
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
includes deterministic chunked-read work and concurrent read/resize regressions.
Common behavior tests also run on the configured JavaScript and native targets.
The [root README](../README.md#chunked-fakefilesystem-writes) documents the
compatible 3.4.0 candidate and its standalone build and test commands.
