import java.util.Arrays;
import kotlinx.datetime.Clock;
import kotlinx.datetime.Instant;
import okio.ByteString;
import okio.FileHandle;
import okio.Path;
import okio.fakefilesystem.FakeFileSystem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FakeFileSystemContentOwnershipTest {
  private static final Path PATH = Path.Companion.get("/contents", false);

  private static byte[] read(FileHandle file) throws Exception {
    byte[] bytes = new byte[(int) file.size()];
    if (bytes.length > 0) assertEquals(bytes.length, file.read(0, bytes, 0, bytes.length));
    return bytes;
  }

  @Test public void generationsKeepTheirBytesWhenOtherHandlesAppendOverwriteOrResize() throws Exception {
    FakeFileSystem fs = new FakeFileSystem();
    fs.setAllowReadsWhileWriting(true);
    fs.setAllowWritesWhileWriting(true);
    try (FileHandle first = fs.openReadWrite(PATH, false, false)) {
      first.write(0, new byte[] {1, 2, 3, 4}, 0, 4);
      try (FileHandle reader = fs.openReadOnly(PATH);
           FileHandle second = fs.openReadWrite(PATH, false, true)) {
        second.write(4, new byte[] {5, 6}, 0, 2);
        assertArrayEquals(new byte[] {1, 2, 3, 4}, read(reader));
        first.write(4, new byte[] {7, 8}, 0, 2);
        assertArrayEquals(new byte[] {1, 2, 3, 4, 7, 8}, read(reader));
        assertArrayEquals(new byte[] {1, 2, 3, 4, 5, 6}, read(second));
        second.write(1, new byte[] {9}, 0, 1);
        assertArrayEquals(new byte[] {1, 2, 3, 4, 7, 8}, read(reader));
        try (FileHandle third = fs.openReadWrite(PATH, false, true)) {
          second.resize(2);
          second.resize(8);
          assertArrayEquals(new byte[] {1, 9, 0, 0, 0, 0, 0, 0}, read(second));
          assertArrayEquals(new byte[] {1, 9, 3, 4, 5, 6}, read(third));
          third.write(9, new byte[] {10}, 0, 1);
          assertArrayEquals(new byte[] {1, 9, 3, 4, 5, 6, 0, 0, 0, 10}, read(third));
          assertEquals(10L, fs.metadata(PATH).getSize());
        }
      }
    } finally {
      fs.checkNoOpenFiles();
    }
  }

  @Test public void callerArraysAndSnapshotsStayDetachedAndClosedHandlesStayClosed() throws Exception {
    FakeFileSystem fs = new FakeFileSystem();
    FileHandle file = fs.openReadWrite(PATH, false, false);
    try {
      byte[] input = new byte[8195];
      Arrays.fill(input, (byte) 3);
      file.write(0, input, 0, input.length);
      Arrays.fill(input, (byte) 9);
      byte[] output = read(file);
      assertEquals(3, output[8194]);
      ByteString snapshot = ByteString.Companion.of(output, 0, output.length);
      Arrays.fill(output, (byte) 7);
      file.write(8190, new byte[] {8, 8, 8}, 0, 3);
      file.resize(2);
      file.resize(8195);
      byte[] expected = new byte[8195];
      expected[0] = expected[1] = 3;
      assertArrayEquals(expected, read(file));
      assertEquals(3, snapshot.getByte(8191));
      file.close();
      file.close();
      assertEquals("closed", assertThrows(IllegalStateException.class,
          () -> file.write(0, new byte[] {1}, 0, 1)).getMessage());
      assertEquals("closed", assertThrows(IllegalStateException.class, file::size).getMessage());
    } finally {
      file.close();
      fs.checkNoOpenFiles();
    }
  }

  @Test public void writePublishesBeforeClockCallbackOpensTheNextGeneration() throws Exception {
    final Runnable[] callback = {null};
    FakeFileSystem fs = new FakeFileSystem(new Clock() {
      @Override public Instant now() {
        Runnable action = callback[0];
        callback[0] = null;
        if (action != null) action.run();
        return Instant.Companion.fromEpochMilliseconds(1234L);
      }
    });
    fs.setAllowReadsWhileWriting(true);
    fs.setAllowWritesWhileWriting(true);
    final FileHandle[] next = {null};
    try (FileHandle first = fs.openReadWrite(PATH, false, false)) {
      callback[0] = () -> {
        try {
          next[0] = fs.openReadWrite(PATH, false, true);
          first.close();
        } catch (Exception e) { throw new RuntimeException(e); }
      };
      first.write(0, new byte[] {1, 2, 3}, 0, 3);
      assertNotNull(next[0]);
      assertArrayEquals(new byte[] {1, 2, 3}, read(next[0]));
      next[0].write(3, new byte[] {4}, 0, 1);
      assertArrayEquals(new byte[] {1, 2, 3, 4}, read(next[0]));
      assertEquals("closed", assertThrows(IllegalStateException.class, first::size).getMessage());
    } finally {
      if (next[0] != null) next[0].close();
      fs.checkNoOpenFiles();
    }
  }
}
