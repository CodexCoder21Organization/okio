import java.io.IOException;
import kotlinx.datetime.Clock;
import okio.*;
import okio.fakefilesystem.FakeFileSystem;
import org.junit.jupiter.api.RepeatedTest;
import static org.junit.jupiter.api.Assertions.*;

public class SymlinkResolutionBoundaryTest {
  private static Path path(String s) { return Path.Companion.get(s, false); }
  private static void write(FakeFileSystem fs, String name, String text) throws IOException {
    try (BufferedSink sink = Okio.buffer(fs.sink(path(name), false))) { sink.writeUtf8(text); }
  }
  private static String read(FakeFileSystem fs, String name) throws IOException {
    try (BufferedSource source = Okio.buffer(fs.source(path(name)))) { return source.readUtf8(); }
  }
  @RepeatedTest(3)
  public void existingDanglingDestinationReplacesOnlyTheLink() throws Exception {
    var fs = new FakeFileSystem(Clock.System.INSTANCE);
    fs.setAllowSymlinks(true);
    fs.createDirectories(path("/parent/target"), false);
    write(fs, "/parent/target/child", "confirmed");
    write(fs, "/source", "source");
    fs.createSymlink(path("/target"), path("/parent/missing"));
    try {
      ((FileSystem) fs).atomicMove(path("/source"), path("/target"));
      assertTrue(fs.metadata(path("/parent/target")).isDirectory(), "Unrelated directory must survive move to an existing dangling link.");
      assertEquals("confirmed", read(fs, "/parent/target/child"));
      assertEquals("source", read(fs, "/target"));
      assertFalse(fs.exists(path("/source")));
      assertFalse(fs.exists(path("/parent/missing")));
    } finally { fs.checkNoOpenFiles(); }
  }
  @RepeatedTest(3)
  public void existingLiveDestinationReplacesOnlyTheLink() throws Exception {
    var fs = new FakeFileSystem(Clock.System.INSTANCE);
    fs.setAllowSymlinks(true);
    fs.createDirectory(path("/parent"), false);
    write(fs, "/parent/referent", "referent");
    write(fs, "/parent/target", "unrelated");
    write(fs, "/source", "source");
    fs.createSymlink(path("/target"), path("/parent/referent"));
    try {
      ((FileSystem) fs).atomicMove(path("/source"), path("/target"));
      assertEquals("unrelated", read(fs, "/parent/target"), "Move must preserve an unrelated entry with the original basename.");
      assertEquals("referent", read(fs, "/parent/referent"));
      assertEquals("source", read(fs, "/target"));
      assertNull(fs.metadata(path("/target")).getSymlinkTarget());
      assertFalse(fs.exists(path("/source")));
    } finally { fs.checkNoOpenFiles(); }
  }
  @RepeatedTest(3)
  public void canonicalizeRejectsDanglingFinalLink() throws Exception {
    var fs = new FakeFileSystem(Clock.System.INSTANCE);
    fs.setAllowSymlinks(true);
    fs.createDirectory(path("/parent"), false);
    fs.createSymlink(path("/target"), path("/parent/missing"));
    try {
      IOException error = assertThrows(IOException.class, () -> fs.canonicalize(path("/target")));
      assertEquals("no such file: /target", error.getMessage());
      assertEquals(path("/parent/missing"), fs.metadata(path("/target")).getSymlinkTarget());
    } finally { fs.checkNoOpenFiles(); }
  }
}
