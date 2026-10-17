import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import kotlinx.datetime.Clock;
import kotlinx.datetime.Instant;
import okio.*;
import okio.fakefilesystem.FakeFileSystem;
import org.junit.jupiter.api.RepeatedTest;
import static org.junit.jupiter.api.Assertions.*;

public class ReadLookupReviewTest {
  private static Path path(String s) { return Path.Companion.get(s, false); }
  private static FakeFileSystem fileSystem() throws Exception {
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
    fs.createDirectory(path("/old"), false);
    fs.createDirectory(path("/new"), false);
    fs.createSymlink(path("/alias"), path("/old"));
    assertFalse(fs.exists(path("/alias/ghost")), "Requested path must be absent before the callback.");
    callback.set(() -> {
      try {
        ((FileSystem) fs).delete(path("/alias"), true);
        ((FileSystem) fs).createSymlink(path("/alias"), path("/new"));
        fs.createDirectory(path("/old/ghost"), false);
        assertFalse(fs.exists(path("/alias/ghost")), "Requested path must remain absent after the callback.");
      } catch (IOException e) { throw new AssertionError(e); }
    });
    return fs;
  }
  @RepeatedTest(3)
  public void metadataDoesNotReturnAnEntryNeverAtTheRequestedPath() throws Exception {
    var fs = fileSystem();
    try { assertNull(fs.metadataOrNull(path("/alias/ghost")), "Metadata must not use an entry created only in the retired parent."); }
    finally { fs.checkNoOpenFiles(); }
  }
  @RepeatedTest(3)
  public void canonicalizeRejectsAPathAbsentThroughoutItsCallback() throws Exception {
    var fs = fileSystem();
    try { assertThrows(IOException.class, () -> fs.canonicalize(path("/alias/ghost")), "Canonicalization must reject a path absent before and after its callback."); }
    finally { fs.checkNoOpenFiles(); }
  }
  @RepeatedTest(3)
  public void listDoesNotReturnAChildNeverAtTheRequestedPath() throws Exception {
    var fs = fileSystem();
    try { assertEquals(List.of(), fs.list(path("/alias")), "Listing must not map a retired parent's new child into the current alias."); }
    finally { fs.checkNoOpenFiles(); }
  }
  @RepeatedTest(3)
  public void createDirectoriesCreatesTheRequestedDirectory() throws Exception {
    var fs = fileSystem();
    try {
      fs.createDirectories(path("/alias/ghost"), false);
      assertTrue(fs.exists(path("/alias/ghost")), "Successful createDirectories must create the requested directory, not accept metadata from a retired parent.");
    } finally { fs.checkNoOpenFiles(); }
  }
}
