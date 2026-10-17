import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import okio.BufferedSink;
import okio.Okio;
import okio.Path;
import okio.fakefilesystem.FakeFileSystem;
import kotlinx.datetime.Clock;
import kotlinx.datetime.Instant;
import org.junit.jupiter.api.RepeatedTest;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DirectoryProof {
  @RepeatedTest(3)
  public void reentrantDirectoryCreationPreservesCommittedChild() throws Exception {
    var reference = new AtomicReference<FakeFileSystem>();
    var armed = new AtomicBoolean(false);
    var entered = new AtomicBoolean(false);
    Path directory = Path.Companion.get("/proof", false);
    Path child = Path.Companion.get("/proof/1.absent", false);
    Clock clock = new Clock() {
      @Override public Instant now() {
        if (armed.compareAndSet(true, false)) {
          try {
            FakeFileSystem fs = reference.get();
            fs.createDirectories(directory, false);
            try (BufferedSink sink = Okio.buffer(fs.sink(child, false))) {
              sink.writeUtf8("confirmed");
            }
            assertTrue(fs.exists(child), "The nested caller must commit its file before returning.");
            entered.set(true);
          } catch (Exception failure) { throw new RuntimeException(failure); }
        }
        return Instant.Companion.fromEpochMilliseconds(0L);
      }
    };
    var fs = new FakeFileSystem(clock);
    reference.set(fs);
    fs.createDirectory(Path.Companion.get("/", false), false);
    try {
      armed.set(true);
      fs.createDirectories(directory, false);
      assertTrue(entered.get(), "Directory creation must reach the public clock callback.");
      assertTrue(fs.exists(child),
          "A directory creation must preserve a child committed by a re-entering caller.");
    } finally { fs.checkNoOpenFiles(); }
  }
}
