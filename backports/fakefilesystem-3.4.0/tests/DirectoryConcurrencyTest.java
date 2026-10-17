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
public class DirectoryConcurrencyTest {
  private static Path path(String value) { return Path.Companion.get(value, false); }
  private static void write(FakeFileSystem fs, Path file) throws IOException {
    try (BufferedSink sink = Okio.buffer(fs.sink(file, false))) { sink.writeUtf8("confirmed"); }
  }
  private static void assertChild(FakeFileSystem fs, Path child) throws IOException {
    assertTrue(fs.exists(child), "Directory creation must preserve a committed child.");
    try (BufferedSource source = Okio.buffer(fs.source(child))) {
      assertEquals("confirmed", source.readUtf8());
    }
    fs.checkNoOpenFiles();
  }

  @RepeatedTest(3)
  public void concurrentCreateDirectoriesPreservesCommittedChild() throws Exception {
    var paused = new CountDownLatch(1);
    var resume = new CountDownLatch(1);
    var first = new AtomicReference<Thread>();
    var armed = new AtomicBoolean(true);
    Clock clock = new Clock() {
      public Instant now() {
        if (Thread.currentThread() == first.get() && armed.compareAndSet(true, false)) {
          paused.countDown();
          try { assertTrue(resume.await(10, TimeUnit.SECONDS), "Second caller did not finish."); }
          catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
        }
        return Instant.Companion.fromEpochMilliseconds(0);
      }
    };
    var fs = new FakeFileSystem(clock);
    fs.createDirectory(path("/"), false);
    var executor = Executors.newSingleThreadExecutor();
    try {
      Future<?> operation = executor.submit(() -> {
        first.set(Thread.currentThread());
        try { fs.createDirectories(path("/proof"), false); }
        catch (IOException e) { throw new CompletionException(e); }
      });
      assertTrue(paused.await(10, TimeUnit.SECONDS), "First caller did not reach its clock.");
      fs.createDirectories(path("/proof"), false);
      write(fs, path("/proof/child"));
      assertChild(fs, path("/proof/child"));
      resume.countDown();
      operation.get(10, TimeUnit.SECONDS);
      assertChild(fs, path("/proof/child"));
    } finally {
      resume.countDown();
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
      fs.checkNoOpenFiles();
    }
  }

  @RepeatedTest(3)
  public void reentrantMustCreateRejectsCommittedDirectory() throws Exception {
    var reference = new AtomicReference<FakeFileSystem>();
    var armed = new AtomicBoolean(false);
    Clock clock = new Clock() {
      public Instant now() {
        if (armed.compareAndSet(true, false)) {
          try {
            reference.get().createDirectories(path("/proof"), false);
            write(reference.get(), path("/proof/child"));
          } catch (IOException e) { throw new AssertionError(e); }
        }
        return Instant.Companion.fromEpochMilliseconds(0);
      }
    };
    var fs = new FakeFileSystem(clock);
    reference.set(fs);
    fs.createDirectory(path("/"), false);
    try {
      armed.set(true);
      IOException failure = assertThrows(IOException.class, () -> fs.createDirectory(path("/proof"), true));
      assertEquals("already exists: /proof", failure.getMessage());
      assertChild(fs, path("/proof/child"));
    } finally { fs.checkNoOpenFiles(); }
  }

  @RepeatedTest(3)
  public void existingDirectoryPreservesChild() throws Exception {
    var fs = new FakeFileSystem();
    try {
      fs.createDirectories(path("/proof"), false);
      write(fs, path("/proof/child"));
      fs.createDirectory(path("/proof"), false);
      assertChild(fs, path("/proof/child"));
      assertEquals("already exists: /proof", assertThrows(IOException.class,
          () -> fs.createDirectory(path("/proof"), true)).getMessage());
      assertChild(fs, path("/proof/child"));
    } finally { fs.checkNoOpenFiles(); }
  }

  @RepeatedTest(3)
  public void existingFileIsNotReplacedByDirectory() throws Exception {
    var fs = new FakeFileSystem();
    try {
      fs.createDirectory(path("/"), false);
      write(fs, path("/proof"));
      assertEquals("already exists: /proof", assertThrows(IOException.class,
          () -> fs.createDirectory(path("/proof"), false)).getMessage());
      assertChild(fs, path("/proof"));
      assertEquals("already exists: /proof", assertThrows(IOException.class,
          () -> fs.createDirectory(path("/proof"), true)).getMessage());
    } finally { fs.checkNoOpenFiles(); }
  }
}
