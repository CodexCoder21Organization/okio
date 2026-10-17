import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import kotlinx.datetime.Clock;
import kotlinx.datetime.Instant;
import okio.*;
import okio.fakefilesystem.FakeFileSystem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class LookupBoundaryReviewTest {
  private static Path path(String value) { return Path.Companion.get(value, false); }
  private interface Action { void run(FakeFileSystem fs) throws Exception; }

  @Test public void loopAndDepthBoundariesUseOneClockRead() throws Exception {
    var reads = new AtomicInteger();
    Clock clock = new Clock() {
      public Instant now() {
        reads.incrementAndGet();
        return Instant.Companion.fromEpochMilliseconds(0);
      }
    };
    var fs = new FakeFileSystem(clock);
    fs.setAllowSymlinks(true);
    fs.createDirectory(path("/real"), false);
    fs.createSymlink(path("/self"), path("/self"));
    fs.createSymlink(path("/a"), path("/b"));
    fs.createSymlink(path("/b"), path("/a"));
    for (String name : new String[]{"/self", "/a", "/b"}) {
      reads.set(0);
      IOException error = assertThrows(IOException.class, () -> fs.canonicalize(path(name)));
      assertEquals("symlink cycle?", error.getMessage());
      assertEquals(1, reads.get(), "Recursive lookup must reuse one captured time.");
    }
    for (int i = 40; i >= 0; i--) {
      fs.createSymlink(path("/link" + i), path(i == 40 ? "/real" : "/link" + (i + 1)));
    }
    reads.set(0);
    assertEquals(path("/real"), fs.canonicalize(path("/link1")));
    assertEquals(1, reads.get());
    reads.set(0);
    IOException error = assertThrows(IOException.class, () -> fs.canonicalize(path("/link0")));
    assertEquals("symlink cycle?", error.getMessage());
    assertEquals(1, reads.get());
    assertEquals(path("/self"), fs.metadata(path("/self")).getSymlinkTarget());
    fs.delete(path("/self"), true);
    assertFalse(fs.exists(path("/self")));
    fs.checkNoOpenFiles();
  }

  @Test public void intermediateLoopsReleaseTheRegistryLock() throws Exception {
    Action[] operations = new Action[]{
      fs -> fs.metadataOrNull(path("/loop/child")),
      fs -> fs.list(path("/loop/child")),
      fs -> fs.openReadOnly(path("/loop/child")).close(),
      fs -> fs.openReadWrite(path("/loop/child"), false, false).close(),
      fs -> fs.createDirectory(path("/loop/child"), false),
      fs -> fs.createSymlink(path("/loop/child"), path("/real")),
      fs -> fs.delete(path("/loop/child"), false),
      fs -> fs.atomicMove(path("/real"), path("/loop/child"))
    };
    for (Action operation : operations) {
      var fs = new FakeFileSystem(Clock.System.INSTANCE);
      fs.setAllowSymlinks(true);
      fs.createDirectory(path("/real"), false);
      fs.createSymlink(path("/loop"), path("/loop"));
      IOException error = assertThrows(IOException.class, () -> operation.run(fs));
      assertEquals("symlink cycle?", error.getMessage());
      var executor = Executors.newSingleThreadExecutor();
      try {
        assertTrue(executor.submit(() -> fs.metadata(path("/real")).isDirectory()).get(5, TimeUnit.SECONDS));
      } finally {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        fs.checkNoOpenFiles();
      }
    }
  }

  @Test public void clockCallbacksCanWaitForAnotherFilesystemCaller() throws Exception {
    Action[] operations = new Action[]{
      fs -> fs.canonicalize(path("/real")),
      fs -> fs.metadataOrNull(path("/real")),
      fs -> fs.list(path("/real")),
      fs -> fs.openReadWrite(path("/new"), false, false).close(),
      fs -> fs.createDirectory(path("/new"), false),
      fs -> fs.createSymlink(path("/new"), path("/real")),
      fs -> fs.delete(path("/real"), true),
      fs -> fs.atomicMove(path("/real"), path("/new"))
    };
    for (Action operation : operations) {
      var callback = new AtomicReference<Runnable>();
      Clock clock = new Clock() {
        public Instant now() {
          Runnable action = callback.getAndSet(null);
          if (action != null) action.run();
          return Instant.Companion.fromEpochMilliseconds(0);
        }
      };
      var fs = new FakeFileSystem(clock);
      fs.setAllowSymlinks(true);
      fs.createDirectory(path("/real"), false);
      var executor = Executors.newSingleThreadExecutor();
      var callbacks = new AtomicInteger();
      callback.set(() -> {
        callbacks.incrementAndGet();
        try {
          assertTrue(executor.submit(() -> fs.metadata(path("/real")).isDirectory()).get(5, TimeUnit.SECONDS));
        } catch (Exception e) { throw new AssertionError(e); }
      });
      try {
        operation.run(fs);
        assertEquals(1, callbacks.get());
      } finally {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        fs.checkNoOpenFiles();
      }
    }
  }
}
