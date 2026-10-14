package okio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class UseNullableResultTest {
  @Test
  fun nullResultClosesExactlyOnce() {
    var closeCount = 0
    val sink = object : ForwardingSink(Buffer()) {
      override fun close() {
        closeCount++
        super.close()
      }
    }

    val result = runCatching { sink.use { null as String? } }

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

    val thrown = assertFailsWith<IOException> { sink.use { null as String? } }

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
