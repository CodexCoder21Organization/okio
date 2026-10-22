package okio

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import okio.FileSystem.Companion.asOkioFileSystem
import okio.Path.Companion.toPath
import org.junit.jupiter.api.Test

class ConcurrentNioDirectoryCreationTest {
  @Test
  fun concurrentDirectoryCreationUsesCurrentEntry() {
    Jimfs.newFileSystem(Configuration.unix()).use { backing ->
      val executor = Executors.newFixedThreadPool(2)
      try {
        // Rounds are ordered because each one checks the completed directory before the next round.
        repeat(32) { round ->
          for (mustCreate in listOf(false, true)) {
            val target = "/directory-$round-$mustCreate".toPath()
            val beforeCreate = CyclicBarrier(2)
            val resolutions = ThreadLocal.withInitial { 0 }
            val arrivals = AtomicInteger()
            val armed = AtomicBoolean(true)
            val nio = DirectoryCreationNioFileSystem(backing) { path ->
              if (armed.get() && path == target.toString()) {
                val count = resolutions.get() + 1
                resolutions.set(count)
                if (count == 2) {
                  // Both real metadata reads have completed before either real NIO create starts.
                  arrivals.incrementAndGet()
                  beforeCreate.await(30, TimeUnit.SECONDS)
                }
              }
            }
            val filesystem = nio.asOkioFileSystem()
            val futures = List(2) {
              executor.submit(
                Callable {
                  runCatching { filesystem.createDirectory(target, mustCreate) }
                },
              )
            }
            val results = futures.map { it.get(30, TimeUnit.SECONDS) }
            armed.set(false)
            assertEquals(2, arrivals.get())
            assertTrue(filesystem.metadata(target).isDirectory)
            val failures = results.mapNotNull { it.exceptionOrNull() }
            if (mustCreate) {
              assertEquals(1, failures.size)
              val failure = failures.single()
              assertEquals("failed to create directory: $target", failure.message)
              assertTrue(failure.cause is FileAlreadyExistsException)
            } else {
              // Rethrow the original error so a failed run retains its complete NIO cause.
              results.forEach { it.getOrThrow() }
            }
            filesystem.write(target / "child") { writeUtf8("retained-$round") }
            filesystem.createDirectory(target)
            assertEquals("retained-$round", filesystem.read(target / "child") { readUtf8() })
            assertEquals(
              "$target already exists.",
              assertFailsWith<IOException> {
                filesystem.createDirectory(target, mustCreate = true)
              }.message,
            )
          }
        }

        val filesystem = backing.asOkioFileSystem()
        val file = "/file-target".toPath()
        filesystem.write(file) { writeUtf8("file contents") }
        for (mustCreate in listOf(false, true)) {
          val error = assertFailsWith<IOException> { filesystem.createDirectory(file, mustCreate) }
          assertEquals("failed to create directory: $file", error.message)
          assertTrue(error.cause is FileAlreadyExistsException)
          assertEquals("file contents", filesystem.read(file) { readUtf8() })
        }

        // The same ordering with a competing file must still fail, preserving the file.
        val competingFile = "/competing-file".toPath()
        val resolutions = AtomicInteger()
        val creatingNio = DirectoryCreationNioFileSystem(backing) { path ->
          if (path == competingFile.toString() && resolutions.incrementAndGet() == 2) {
            Files.write(backing.getPath(path), byteArrayOf(7))
          }
        }
        val error = assertFailsWith<IOException> {
          creatingNio.asOkioFileSystem().createDirectory(competingFile)
        }
        assertEquals("failed to create directory: $competingFile", error.message)
        assertTrue(error.cause is FileAlreadyExistsException)
        assertEquals(listOf<Byte>(7), Files.readAllBytes(backing.getPath(competingFile.toString())).toList())
      } finally {
        executor.shutdownNow()
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS))
      }
    }
  }
}
