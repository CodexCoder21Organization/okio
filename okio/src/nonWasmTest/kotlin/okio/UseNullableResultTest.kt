package okio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class UseNullableResultTest {
  @Test
  fun fileSystemReadPreservesNullAndClosesExactlyOnce() {
    val delegate = FakeFileSystem()
    val path = "/record".toPath()
    delegate.write(path) { writeUtf8("legacy record") }
    var closeCount = 0
    val fileSystem = object : ForwardingFileSystem(delegate) {
      override fun source(file: Path): Source {
        return object : ForwardingSource(super.source(file)) {
          override fun close() {
            closeCount++
            super.close()
          }
        }
      }
    }

    val result = runCatching {
      fileSystem.read(path) {
        readUtf8()
        null
      }
    }

    assertEquals(1, closeCount)
    assertTrue(result.isSuccess, "read rejected a nullable block result: ${result.exceptionOrNull()}")
    assertNull(result.getOrThrow())
    delegate.checkNoOpenFiles()
  }

  @Test
  fun fileSystemWritePreservesNullAndClosesExactlyOnce() {
    val delegate = FakeFileSystem()
    val path = "/record".toPath()
    var closeCount = 0
    val fileSystem = object : ForwardingFileSystem(delegate) {
      override fun sink(file: Path, mustCreate: Boolean): Sink {
        return object : ForwardingSink(super.sink(file, mustCreate)) {
          override fun close() {
            closeCount++
            super.close()
          }
        }
      }
    }

    val result = runCatching {
      fileSystem.write(path) {
        writeUtf8("legacy record")
        null
      }
    }

    assertEquals(1, closeCount)
    assertTrue(result.isSuccess, "write rejected a nullable block result: ${result.exceptionOrNull()}")
    assertNull(result.getOrThrow())
    assertEquals("legacy record", delegate.read(path) { readUtf8() })
    delegate.checkNoOpenFiles()
  }

  @Test
  fun nullReceiverAcceptsNullResult() {
    val source: Source? = null

    val result = runCatching {
      source.use {
        assertNull(it)
        null
      }
    }

    assertTrue(result.isSuccess, "use rejected a nullable block result: ${result.exceptionOrNull()}")
    assertNull(result.getOrThrow())
  }

  @Test
  fun nullResultClosesExactlyOnce() {
    var closeCount = 0
    val sink = object : ForwardingSink(Buffer()) {
      override fun close() {
        closeCount++
        super.close()
      }
    }

    val result = runCatching { sink.use { null } }

    assertEquals(1, closeCount)
    assertTrue(result.isSuccess, "use rejected a nullable block result: ${result.exceptionOrNull()}")
    assertNull(result.getOrThrow())
  }

  @Test
  fun throwingBlockClosesExactlyOnce() {
    var closeCount = 0
    val sink = object : ForwardingSink(Buffer()) {
      override fun close() {
        closeCount++
        super.close()
      }
    }
    val failure = IOException("block could not read the record")

    val thrown = assertFailsWith<IOException> { sink.use { throw failure } }

    assertEquals(1, closeCount)
    assertSame(failure, thrown)
    assertEquals("block could not read the record", thrown.message)
  }

  @Test
  fun nullResultStillPropagatesCloseFailure() {
    var closeCount = 0
    val failure = IOException("sink could not close")
    val sink = object : ForwardingSink(Buffer()) {
      override fun close() {
        closeCount++
        super.close()
        throw failure
      }
    }

    val thrown = assertFailsWith<IOException> { sink.use { null } }

    assertEquals(1, closeCount)
    assertSame(failure, thrown)
    assertEquals("sink could not close", thrown.message)
  }

  @Test
  fun blockFailureKeepsCloseFailureSuppressed() {
    var closeCount = 0
    val blockFailure = IOException("block could not read the record")
    val closeFailure = IOException("sink could not close")
    val sink = object : ForwardingSink(Buffer()) {
      override fun close() {
        closeCount++
        super.close()
        throw closeFailure
      }
    }

    val thrown = assertFailsWith<IOException> { sink.use { throw blockFailure } }

    assertEquals(1, closeCount)
    assertSame(blockFailure, thrown)
    assertEquals(listOf(closeFailure), thrown.suppressedExceptions)
  }
}
