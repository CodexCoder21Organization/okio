import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import kotlinx.datetime.Clock;
import kotlinx.datetime.Instant;
import okio.*;
import okio.fakefilesystem.FakeFileSystem;
import org.junit.jupiter.api.RepeatedTest;
import static org.junit.jupiter.api.Assertions.*;

public class DanglingAdmissionReviewTest {
  private static Path path(String s) { return Path.Companion.get(s, false); }
  private static void write(FakeFileSystem fs, String name, String text) throws IOException {
    try (BufferedSink sink = Okio.buffer(fs.sink(path(name), false))) { sink.writeUtf8(text); }
  }
  @RepeatedTest(3)
  public void directoryAdmissionPreservesUnrelatedDirectory() throws Exception { check("directory"); }
  @RepeatedTest(3)
  public void symlinkAdmissionPreservesUnrelatedDirectory() throws Exception { check("symlink"); }
  @RepeatedTest(3)
  public void moveAdmissionPreservesUnrelatedDirectory() throws Exception { check("move"); }
  private static void check(String operation) throws Exception {
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
    fs.createDirectory(path("/parent"), false);
    fs.createDirectory(path("/parent/target"), false);
    fs.createDirectory(path("/real"), false);
    write(fs, "/parent/target/child", "confirmed");
    write(fs, "/source", "source");
    callback.set(() -> {
      try {
        ((FileSystem) fs).createSymlink(path("/target"), path("/parent/missing"));
        assertTrue(fs.exists(path("/parent/target/child")), "Callback must preserve the unrelated child.");
      } catch (IOException e) { throw new AssertionError(e); }
    });
    try {
      switch (operation) {
        case "directory": ((FileSystem) fs).createDirectory(path("/target"), false); break;
        case "symlink": ((FileSystem) fs).createSymlink(path("/target"), path("/real")); break;
        case "move": ((FileSystem) fs).atomicMove(path("/source"), path("/target")); break;
        default: throw new AssertionError(operation);
      }
      boolean directoryPreserved = fs.metadata(path("/parent/target")).isDirectory();
      boolean childPreserved;
      try { childPreserved = fs.exists(path("/parent/target/child")); }
      catch (IOException e) { childPreserved = false; }
      final boolean retained = childPreserved;
      assertAll(
          () -> assertTrue(directoryPreserved, "Admission to /target must preserve unrelated /parent/target directory type."),
          () -> assertTrue(retained, "Admission to /target must preserve unrelated /parent/target/child."));
    } finally { fs.checkNoOpenFiles(); }
  }
}
