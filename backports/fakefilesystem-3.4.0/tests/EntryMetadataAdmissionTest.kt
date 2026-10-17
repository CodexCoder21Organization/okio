import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull

class EntryMetadataAdmissionTest {
  @RepeatedTest(3)
  fun metadataUpdateResolvesCurrentSymlinkParentAfterClock() {
    var callback: (() -> Unit)? = null
    val clock = object : Clock {
      override fun now(): Instant {
        val action = callback
        callback = null
        action?.invoke()
        return Instant.fromEpochMilliseconds(0)
      }
    }
    val fs = FakeFileSystem(clock)
    fs.allowSymlinks = true
    fs.createDirectory("/old".toPath())
    fs.createDirectory("/new".toPath())
    fs.openReadWrite("/old/target".toPath()).close()
    fs.openReadWrite("/new/target".toPath()).close()
    fs.createSymlink("/alias".toPath(), "/old".toPath())
    callback = {
      fs.delete("/alias".toPath())
      fs.createSymlink("/alias".toPath(), "/new".toPath())
    }
    try {
      fs.setExtra("/alias/target".toPath(), String::class, "current")
      assertNull(fs.metadata("/old/target".toPath()).extras[String::class], "Metadata update must preserve the retired parent's entry.")
      assertEquals("current", fs.metadata("/new/target".toPath()).extras[String::class], "Metadata update must use the current symlink parent.")
    } finally {
      fs.checkNoOpenFiles()
    }
  }
}
