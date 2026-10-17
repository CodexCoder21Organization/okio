import java.io.IOException;
import okio.*;
import okio.fakefilesystem.FakeFileSystem;
import org.junit.jupiter.api.RepeatedTest;
import static org.junit.jupiter.api.Assertions.*;

public class DirectoryWrapperReviewTest {
  @RepeatedTest(3)
  public void createDirectoriesRejectsExistingFile() throws Exception {
    var fs = new FakeFileSystem();
    Path file = Path.Companion.get("/file", false);
    try {
      try (BufferedSink sink = Okio.buffer(fs.sink(file, false))) {
        sink.writeUtf8("confirmed");
      }
      assertThrows(IOException.class, () -> fs.createDirectories(file, false),
          "Directory creation wrapper must report a file at the requested directory path.");
    } finally { fs.checkNoOpenFiles(); }
  }
}
