import java.util.Arrays;
import okio.FileHandle;
import okio.Path;
import okio.fakefilesystem.FakeFileSystem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FakeFileSystemRejectedResizeTest {
  @Test public void resizeErrorWordingFollowsEachIndependentView() throws Exception {
    for (int scenario = 0; scenario < 6; scenario++) {
      FakeFileSystem fs = new FakeFileSystem();
      fs.emulateUnix();
      Path path = Path.Companion.get("/message", false);
      try (FileHandle original = fs.openReadWrite(path, true, false)) {
        boolean includesIndexes;
        switch (scenario) {
          case 0: original.resize(3); includesIndexes = false; break;
          case 1:
            original.write(0, new byte[] {1, 2, 3}, 0, 3);
            original.resize(4);
            includesIndexes = false;
            break;
          case 2: original.resize(4096); original.resize(3); includesIndexes = true; break;
          case 3:
            original.write(0, new byte[] {1, 2, 3}, 0, 3);
            original.resize(3);
            includesIndexes = true;
            break;
          case 4:
            original.write(0, new byte[] {1, 2, 3}, 0, 3);
            original.resize(0);
            includesIndexes = false;
            break;
          default:
            original.resize(3);
            original.write(0, new byte[0], 0, 0);
            includesIndexes = true;
        }
        long size = original.size();
        byte[] expected = new byte[(int) size];
        if (size > 0) assertEquals(expected.length, original.read(0, expected, 0, expected.length));
        String message = includesIndexes ? "endIndex=-1 < beginIndex=0" : "endIndex < beginIndex";
        try (FileHandle replacement = fs.openReadWrite(path, false, true)) {
          IllegalArgumentException originalFailure = assertThrows(IllegalArgumentException.class, () -> original.resize(-1));
          assertEquals(message, originalFailure.getMessage(), "Original message in scenario " + scenario);
          assertEquals(size, original.size());
          original.resize(size + 1);
          assertEquals("endIndex < beginIndex",
              assertThrows(IllegalArgumentException.class, () -> original.resize(-1)).getMessage());
          assertEquals(message,
              assertThrows(IllegalArgumentException.class, () -> replacement.resize(-1)).getMessage(),
              "Replacement message in scenario " + scenario);
          assertEquals(size, replacement.size());
          if (size > 0) {
            byte[] actual = new byte[expected.length];
            assertEquals(actual.length, replacement.read(0, actual, 0, actual.length));
            assertArrayEquals(expected, actual);
          }
        }
      } finally { fs.checkNoOpenFiles(); }
    }
  }

  @Test public void emptyFileRejectsNegativeResize() throws Exception {
    FakeFileSystem fs = new FakeFileSystem();
    Path path = Path.Companion.get("/empty", false);
    try (FileHandle handle = fs.openReadWrite(path, true, false)) {
      IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> handle.resize(-1));
      assertEquals("endIndex < beginIndex", failure.getMessage());
      assertEquals(0, handle.size());
      assertEquals(Long.valueOf(0), fs.metadata(path).getSize());
      assertEquals(-1, handle.read(0, new byte[1], 0, 1));
      handle.write(0, new byte[] {7}, 0, 1);
      assertEquals(1, handle.size());
    } finally { fs.checkNoOpenFiles(); }
  }

  @Test public void rejectedNegativeResizePreservesEveryPage() throws Exception {
    FakeFileSystem fs = new FakeFileSystem();
    fs.emulateUnix();
    Path path = Path.Companion.get("/pages", false);
    byte[] expected = new byte[131075];
    for (int i = 0; i < expected.length; i++) expected[i] = (byte) (i * 31 + 7);
    try (FileHandle handle = fs.openReadWrite(path, true, false)) {
      handle.write(0, expected, 0, expected.length);
      for (long size : new long[] {-1, -65537, Long.MIN_VALUE, -4294967296L}) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> handle.resize(size));
        assertEquals("endIndex=" + size + " < beginIndex=0", failure.getMessage());
        assertEquals(expected.length, handle.size());
        assertEquals(Long.valueOf(expected.length), fs.metadata(path).getSize());
        byte[] actual = new byte[expected.length];
        assertEquals(actual.length, handle.read(0, actual, 0, actual.length));
        assertArrayEquals(expected, actual);
        try (FileHandle observer = fs.openReadOnly(path)) {
          Arrays.fill(actual, (byte) 0);
          assertEquals(actual.length, observer.read(0, actual, 0, actual.length));
          assertArrayEquals(expected, actual);
        }
      }
      handle.write(expected.length, new byte[] {9}, 0, 1);
      assertEquals(expected.length + 1, handle.size());
      byte[] appended = new byte[1];
      assertEquals(1, handle.read(expected.length, appended, 0, 1));
      assertArrayEquals(new byte[] {9}, appended);
    } finally { fs.checkNoOpenFiles(); }
  }

  @Test public void negativeWholePageResizePreservesSmallFile() throws Exception {
    FakeFileSystem fs = new FakeFileSystem();
    Path path = Path.Companion.get("/small", false);
    byte[] expected = {1, 2, 3};
    try (FileHandle handle = fs.openReadWrite(path, true, false)) {
      handle.write(0, expected, 0, expected.length);
      IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> handle.resize(-65537));
      assertEquals("endIndex=-65537 < beginIndex=0", failure.getMessage());
      assertEquals(expected.length, handle.size());
      assertEquals(Long.valueOf(expected.length), fs.metadata(path).getSize());
      byte[] actual = new byte[expected.length];
      assertEquals(actual.length, handle.read(0, actual, 0, actual.length));
      assertArrayEquals(expected, actual);
      handle.resize(0);
      assertEquals(0, handle.size());
    } finally { fs.checkNoOpenFiles(); }
  }
}
