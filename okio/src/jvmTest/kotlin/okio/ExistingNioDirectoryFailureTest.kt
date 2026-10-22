package okio

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import okio.FileSystem.Companion.asOkioFileSystem
import okio.Path.Companion.toPath
import org.junit.jupiter.api.Test

class ExistingNioDirectoryFailureTest {
  @Test
  fun existingDirectoryDoesNotHideOtherCreationFailures() {
    Jimfs.newFileSystem(Configuration.unix()).use { backing ->
      val target = "/existing".toPath()
      Files.createDirectory(backing.getPath(target.toString()))
      val resolutions = AtomicInteger()
      val denied = AccessDeniedException(target.toString(), null, "directory creation was refused")
      val filesystem = DirectoryCreationNioFileSystem(backing) { path ->
        if (path == target.toString() && resolutions.incrementAndGet() == 2) throw denied
      }.asOkioFileSystem()
      val error = assertFailsWith<IOException> { filesystem.createDirectory(target) }
      assertEquals("failed to create directory: $target", error.message)
      assertSame(denied, error.cause)
      assertEquals("/existing: directory creation was refused", error.cause!!.message)
      assertTrue(filesystem.metadata(target).isDirectory)
    }
  }
}
