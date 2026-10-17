import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import kotlinx.datetime.Clock;
import kotlinx.datetime.Instant;
import okio.*;
import okio.fakefilesystem.FakeFileSystem;
import org.junit.jupiter.api.RepeatedTest;
import static org.junit.jupiter.api.Assertions.*;

public class NeighborEntryReviewTest {
  private static Path path(String value) { return Path.Companion.get(value, false); }
  private static void write(FakeFileSystem fs, String name) throws IOException {
    try (BufferedSink sink = Okio.buffer(fs.sink(path(name), false))) {
      sink.writeUtf8("confirmed");
    }
  }
  private static FakeFileSystem fileSystem(AtomicBoolean armed) throws IOException {
    var ref = new AtomicReference<FakeFileSystem>();
    Clock clock = new Clock() {
      public Instant now() {
        if (armed.compareAndSet(true, false)) {
          try {
            FakeFileSystem fs = ref.get();
            fs.createDirectory(path("/target"), true);
            write(fs, "/target/child");
            assertTrue(fs.exists(path("/target/child")), "Callback must commit child before outer admission.");
          } catch (IOException failure) { throw new AssertionError(failure); }
        }
        return Instant.Companion.fromEpochMilliseconds(0);
      }
    };
    var fs = new FakeFileSystem(clock);
    ref.set(fs);
    fs.setAllowSymlinks(true);
    fs.createDirectory(path("/"), false);
    fs.createDirectory(path("/real"), false);
    return fs;
  }
  @RepeatedTest(3)
  public void mustCreateOpenPreservesDirectoryCreatedByClock() throws Exception {
    var armed = new AtomicBoolean(false);
    var fs = fileSystem(armed);
    try {
      armed.set(true);
      Throwable observed = null;
      try (FileHandle handle = fs.openReadWrite(path("/target"), true, false)) {
        // A returned handle means the required existing-entry check was missed.
      } catch (IOException failure) { observed = failure; }
      final Throwable failure = observed;
      assertAll(
          () -> assertInstanceOf(IOException.class, failure, "mustCreate must reject the callback's directory."),
          () -> assertTrue(fs.metadata(path("/target")).isDirectory(), "Open must preserve directory type."),
          () -> assertTrue(fs.exists(path("/target/child")), "Open must preserve the callback's committed child."));
    } finally { fs.checkNoOpenFiles(); }
  }
  @RepeatedTest(3)
  public void symlinkCreationPreservesDirectoryCreatedByClock() throws Exception {
    var armed = new AtomicBoolean(false);
    var fs = fileSystem(armed);
    try {
      armed.set(true);
      Throwable observed = null;
      try { ((FileSystem) fs).createSymlink(path("/target"), path("/real")); }
      catch (IOException failure) { observed = failure; }
      final Throwable failure = observed;
      assertAll(
          () -> assertInstanceOf(IOException.class, failure, "Symlink creation must reject the callback's directory."),
          () -> assertTrue(fs.metadata(path("/target")).isDirectory(), "Symlink creation must preserve directory type."),
          () -> assertTrue(fs.exists(path("/target/child")), "Symlink creation must preserve the callback's committed child."));
    } finally { fs.checkNoOpenFiles(); }
  }
  @RepeatedTest(3)
  public void movePreservesDirectoryCreatedBySourceLookupClock() throws Exception {
    var armed = new AtomicBoolean(false);
    var fs = fileSystem(armed);
    write(fs, "/real/source");
    fs.createSymlink(path("/alias"), path("/real"));
    try {
      armed.set(true);
      Throwable observed = null;
      try { ((FileSystem) fs).atomicMove(path("/alias/source"), path("/target")); }
      catch (IOException failure) { observed = failure; }
      final Throwable failure = observed;
      assertAll(
          () -> assertInstanceOf(IOException.class, failure, "Move must reject the callback's destination directory."),
          () -> assertTrue(fs.metadata(path("/target")).isDirectory(), "Move must preserve destination directory type."),
          () -> assertTrue(fs.exists(path("/target/child")), "Move must preserve the callback's committed child."),
          () -> assertTrue(fs.exists(path("/real/source")), "Rejected move must preserve its source."));
    } finally { fs.checkNoOpenFiles(); }
  }
}
