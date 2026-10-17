import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import kotlinx.datetime.Clock;
import kotlinx.datetime.Instant;
import okio.*;
import okio.fakefilesystem.FakeFileSystem;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
public class EntryAdmissionConcurrencyTest {
  private static Path path(String value) { return Path.Companion.get(value, false); }

  @RepeatedTest(3)
  public void concurrentOpenPreservesCommittedDirectory() throws Exception { checkAdmission("open"); }
  @RepeatedTest(3)
  public void concurrentSymlinkPreservesCommittedDirectory() throws Exception { checkAdmission("symlink"); }
  @RepeatedTest(3)
  public void concurrentMovePreservesCommittedDirectory() throws Exception { checkAdmission("move"); }

  private static void checkAdmission(String operation) throws Exception {
    var paused = new CountDownLatch(1);
    var resume = new CountDownLatch(1);
    var worker = new AtomicReference<Thread>();
    var armed = new AtomicBoolean(false);
    Clock clock = new Clock() {
      public Instant now() {
        if (Thread.currentThread() == worker.get() && armed.compareAndSet(true, false)) {
          paused.countDown();
          try { assertTrue(resume.await(10, TimeUnit.SECONDS), "Concurrent directory admission did not finish."); }
          catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
        }
        return Instant.Companion.fromEpochMilliseconds(0);
      }
    };
    var fs = new FakeFileSystem(clock);
    fs.setAllowSymlinks(true);
    fs.createDirectory(path("/"), false);
    fs.createDirectory(path("/real"), false);
    try (Sink sink = fs.sink(path("/real/source"), false)) { sink.write(new Buffer().writeUtf8("source"), 6); }
    fs.createSymlink(path("/alias"), path("/real"));
    var executor = Executors.newSingleThreadExecutor();
    try {
      armed.set(true);
      Future<IOException> result = executor.submit(() -> {
        worker.set(Thread.currentThread());
        try {
          switch (operation) {
            case "open": try (FileHandle handle = fs.openReadWrite(path("/target"), true, false)) { } break;
            case "symlink": ((FileSystem) fs).createSymlink(path("/target"), path("/real")); break;
            case "move": ((FileSystem) fs).atomicMove(path("/alias/source"), path("/target")); break;
            default: throw new AssertionError(operation);
          }
          return null;
        } catch (IOException expected) { return expected; }
      });
      assertTrue(paused.await(10, TimeUnit.SECONDS), "Outer operation did not reach its clock.");
      fs.createDirectory(path("/target"), true);
      try (Sink sink = fs.sink(path("/target/child"), false)) { sink.write(new Buffer().writeUtf8("confirmed"), 9); }
      assertTrue(fs.exists(path("/target/child")), "Second caller must commit the child before resuming admission.");
      resume.countDown();
      IOException failure = result.get(10, TimeUnit.SECONDS);
      assertAll(
          () -> assertNotNull(failure, "Admission must reject the concurrent directory."),
          () -> assertTrue(fs.metadata(path("/target")).isDirectory(), "Admission must preserve directory type."),
          () -> assertTrue(fs.exists(path("/target/child")), "Admission must preserve the committed child."),
          () -> assertTrue(fs.exists(path("/real/source")), "Rejected admission must preserve the source."));
      assertEquals(operation.equals("open") ? "/target already exists." : operation.equals("symlink") ? "already exists: /target" : "target is a directory: /target", failure.getMessage());
      try (BufferedSource source = Okio.buffer(fs.source(path("/target/child")))) { assertEquals("confirmed", source.readUtf8()); }
    } finally {
      resume.countDown();
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
      fs.checkNoOpenFiles();
    }
  }
}
