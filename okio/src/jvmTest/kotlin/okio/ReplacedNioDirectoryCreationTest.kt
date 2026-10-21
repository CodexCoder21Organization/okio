package okio

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import okio.FileSystem.Companion.asOkioFileSystem
import okio.Path.Companion.toPath
import org.junit.jupiter.api.Test

class ReplacedNioDirectoryCreationTest {
  @Test
  fun directoryReplacedByFileDoesNotCountAsCreatedDirectory() {
    Jimfs.newFileSystem(Configuration.unix()).use { backing ->
      val target = "/replaced".toPath()
      val nioTarget = backing.getPath(target.toString())
      Files.createDirectory(nioTarget)
      val resolutions = AtomicInteger()
      val filesystem = DirectoryCreationNioFileSystem(backing) { path ->
        if (path == target.toString() && resolutions.incrementAndGet() == 2) {
          Files.delete(nioTarget)
          Files.write(nioTarget, byteArrayOf(17, 29))
        }
      }.asOkioFileSystem()
      val error = assertFailsWith<IOException> { filesystem.createDirectory(target) }
      assertEquals("failed to create directory: $target", error.message)
      assertTrue(error.cause is FileAlreadyExistsException)
      assertTrue(filesystem.metadata(target).isRegularFile)
      assertEquals(listOf<Byte>(17, 29), filesystem.read(target) { readByteArray().toList() })
    }
  }
}
