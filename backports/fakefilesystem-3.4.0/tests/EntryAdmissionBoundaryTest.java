import java.io.IOException;
import java.util.concurrent.atomic.*;
import kotlinx.datetime.Clock;
import kotlinx.datetime.Instant;
import okio.*;
import okio.fakefilesystem.FakeFileSystem;
import org.junit.jupiter.api.RepeatedTest;
import static org.junit.jupiter.api.Assertions.*;

public class EntryAdmissionBoundaryTest {
  private static Path path(String name) { return Path.Companion.get(name, false); }
  private static void write(FakeFileSystem fs, String name, String text) throws IOException {
    try (BufferedSink sink = Okio.buffer(fs.sink(path(name), false))) { sink.writeUtf8(text); }
  }
  private static String read(FakeFileSystem fs, String name) throws IOException {
    try (BufferedSource source = Okio.buffer(fs.source(path(name)))) { return source.readUtf8(); }
  }
  private static FakeFileSystem fileSystem(AtomicReference<Runnable> callback) throws IOException {
    Clock clock = new Clock() {
      public Instant now() {
        Runnable action = callback.getAndSet(null);
        if (action != null) action.run();
        return Instant.Companion.fromEpochMilliseconds(0);
      }
    };
    var fs = new FakeFileSystem(clock);
    fs.setAllowSymlinks(true);
    fs.createDirectory(path("/"), false);
    return fs;
  }
  @RepeatedTest(3)
  public void sinkRejectsDirectoryCreatedByClock() throws Exception { directorySink(false); }
  @RepeatedTest(3)
  public void appendingSinkRejectsDirectoryCreatedByClock() throws Exception { directorySink(true); }
  private static void directorySink(boolean append) throws Exception {
    var callback = new AtomicReference<Runnable>();
    var fs = fileSystem(callback);
    callback.set(() -> {
      try { fs.createDirectory(path("/target"), true); write(fs, "/target/child", "confirmed"); }
      catch (IOException e) { throw new AssertionError(e); }
    });
    try {
      IOException failure = assertThrows(IOException.class, () -> {
        try (Sink sink = append ? fs.appendingSink(path("/target"), false) : fs.sink(path("/target"), false)) { }
      });
      assertEquals("destination is a directory: /target", failure.getMessage());
      assertTrue(fs.metadata(path("/target")).isDirectory());
      assertEquals("confirmed", read(fs, "/target/child"));
    } finally { fs.checkNoOpenFiles(); }
  }
  @RepeatedTest(3)
  public void mustCreateRejectsFileCreatedByClock() throws Exception {
    var callback = new AtomicReference<Runnable>();
    var fs = fileSystem(callback);
    callback.set(() -> {
      try { write(fs, "/target", "confirmed"); }
      catch (IOException e) { throw new AssertionError(e); }
    });
    try {
      IOException failure = assertThrows(IOException.class, () -> {
        try (FileHandle handle = fs.openReadWrite(path("/target"), true, false)) { }
      });
      assertEquals("/target already exists.", failure.getMessage());
      assertEquals("confirmed", read(fs, "/target"));
    } finally { fs.checkNoOpenFiles(); }
  }
  @RepeatedTest(3)
  public void mustExistRejectsFileDeletedByClock() throws Exception {
    var callback = new AtomicReference<Runnable>();
    var fs = fileSystem(callback);
    write(fs, "/target", "before");
    callback.set(() -> {
      try { ((FileSystem) fs).delete(path("/target"), true); }
      catch (IOException e) { throw new AssertionError(e); }
    });
    try {
      IOException failure = assertThrows(IOException.class, () -> {
        try (FileHandle handle = fs.openReadWrite(path("/target"), false, true)) { }
      });
      assertEquals("/target doesn't exist.", failure.getMessage());
      assertFalse(fs.exists(path("/target")));
    } finally { fs.checkNoOpenFiles(); }
  }
  @RepeatedTest(3)
  public void readOnlyReadsFileReplacedByClock() throws Exception {
    var callback = new AtomicReference<Runnable>();
    var fs = fileSystem(callback);
    write(fs, "/target", "before");
    callback.set(() -> {
      try { ((FileSystem) fs).delete(path("/target"), true); write(fs, "/target", "after"); }
      catch (IOException e) { throw new AssertionError(e); }
    });
    try { assertEquals("after", read(fs, "/target")); }
    finally { fs.checkNoOpenFiles(); }
  }
  @RepeatedTest(3)
  public void deleteResolvesCurrentSymlinkParentAfterClock() throws Exception {
    var callback = new AtomicReference<Runnable>();
    var fs = fileSystem(callback);
    fs.createDirectory(path("/old"), false);
    fs.createDirectory(path("/new"), false);
    write(fs, "/old/target", "old");
    write(fs, "/new/target", "new");
    ((FileSystem) fs).createSymlink(path("/alias"), path("/old"));
    callback.set(() -> {
      try { ((FileSystem) fs).delete(path("/alias"), true); ((FileSystem) fs).createSymlink(path("/alias"), path("/new")); }
      catch (IOException e) { throw new AssertionError(e); }
    });
    try {
      ((FileSystem) fs).delete(path("/alias/target"), true);
      assertAll(
          () -> assertTrue(fs.exists(path("/old/target")), "Delete must preserve the retired parent's file."),
          () -> assertFalse(fs.exists(path("/new/target")), "Delete must resolve the current symlink parent."));
    } finally { fs.checkNoOpenFiles(); }
  }
}
