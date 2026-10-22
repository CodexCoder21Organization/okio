import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import okio.FileHandle;
import okio.Path;
import okio.fakefilesystem.FakeFileSystem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FakeFileSystemChunkedWriteTest {
  @Test
  public void chunkedWritesAllocateLinearly() throws Exception {
    ThreadMXBean counter = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    assertTrue(counter.isThreadAllocatedMemorySupported());
    boolean enabled = counter.isThreadAllocatedMemoryEnabled();
    counter.setThreadAllocatedMemoryEnabled(true);
    long thread = Thread.currentThread().getId();
    byte[] chunk = new byte[8192];
    Arrays.fill(chunk, (byte) 37);
    FakeFileSystem fs = new FakeFileSystem();
    Path path = Path.Companion.get("/chunked", false);
    long[] allocations = new long[2];
    try {
      // Warm both sizes before measuring; no elapsed-time assertion or external files.
      for (int run = 0; run < 6; run++) {
        int chunks = (run & 1) == 0 ? 256 : 1024;
        try (FileHandle handle = fs.openReadWrite(path, true, false)) {
          long before = counter.getThreadAllocatedBytes(thread);
          for (int i = 0; i < chunks; i++) handle.write((long) i * chunk.length, chunk, 0, chunk.length);
          long allocated = counter.getThreadAllocatedBytes(thread) - before;
          assertEquals((long) chunks * chunk.length, handle.size());
          byte[] actual = new byte[chunk.length];
          assertEquals(actual.length, handle.read(handle.size() - actual.length, actual, 0, actual.length));
          assertArrayEquals(chunk, actual);
          if (run >= 4) allocations[run - 4] = allocated;
        }
        fs.delete(path, true);
      }
      System.out.println("CHUNKED_WRITE_ALLOCATION N=" + allocations[0] + " 4N=" + allocations[1]);
      assertTrue(allocations[0] > 0);
      assertTrue(allocations[1] <= allocations[0] * 6,
          "Quadrupling chunked writes must allocate at most six times as much: N="
              + allocations[0] + " bytes, 4N=" + allocations[1] + " bytes");
    } finally {
      fs.checkNoOpenFiles();
      counter.setThreadAllocatedMemoryEnabled(enabled);
    }
  }

  @Test
  public void overwriteResizeAndReopenPreserveBytes() throws Exception {
    FakeFileSystem fs = new FakeFileSystem();
    Path path = Path.Companion.get("/ranges", false);
    byte[] original = new byte[3 * 8192 + 17];
    for (int i = 0; i < original.length; i++) original[i] = (byte) (i * 31 + 7);
    try {
      try (FileHandle handle = fs.openReadWrite(path, true, false)) {
        handle.write(0, original, 0, original.length);
        byte[] patch = new byte[8192 + 11];
        Arrays.fill(patch, (byte) 93);
        int offset = 8192 - 3;
        handle.write(offset, patch, 2, patch.length - 4);
        System.arraycopy(patch, 2, original, offset, patch.length - 4);
        Arrays.fill(patch, (byte) 0); // Changing a caller's input cannot change the file.
        byte[] actual = new byte[original.length + 6];
        Arrays.fill(actual, (byte) 42);
        assertEquals(original.length, handle.read(0, actual, 3, original.length));
        assertArrayEquals(original, Arrays.copyOfRange(actual, 3, actual.length - 3));
        assertEquals(42, actual[0]);
        assertEquals(42, actual[actual.length - 1]);
        Arrays.fill(actual, (byte) 0); // Changing returned bytes cannot change the file.
        handle.resize(8192 + 5);
        handle.resize(original.length);
        Arrays.fill(original, 8192 + 5, original.length, (byte) 0);
        assertEquals(original.length, handle.read(0, actual, 3, original.length));
        assertArrayEquals(original, Arrays.copyOfRange(actual, 3, actual.length - 3));
        byte[] tail = new byte[] { 9, 8, 7 };
        handle.write(original.length + 11L, tail, 0, tail.length);
        byte[] gap = new byte[14];
        assertEquals(14, handle.read(original.length, gap, 0, 14));
        assertArrayEquals(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 9, 8, 7}, gap);
      }
      try (FileHandle reopened = fs.openReadOnly(path)) {
        assertEquals(original.length + 14L, reopened.size());
        assertEquals(-1, reopened.read(1L << 32, new byte[1], 0, 1));
      }
    } finally {
      fs.checkNoOpenFiles();
    }
  }

  @Test
  public void replacementOpenKeepsPriorHandlesIndependent() throws Exception {
    FakeFileSystem fs = new FakeFileSystem();
    fs.setAllowReadsWhileWriting(true);
    fs.setAllowWritesWhileWriting(true);
    Path path = Path.Companion.get("/views", false);
    byte[] original = new byte[8192 + 13];
    Arrays.fill(original, (byte) 17);
    try {
      try (FileHandle first = fs.openReadWrite(path, true, false)) {
        first.write(0, original, 0, original.length);
        try (FileHandle reader = fs.openReadOnly(path);
             FileHandle replacement = fs.openReadWrite(path, false, true)) {
          replacement.write(8190, new byte[] { 1, 2, 3, 4, 5 }, 0, 5);
          replacement.resize(original.length + 20L);
          byte[] prior = new byte[original.length];
          assertEquals(prior.length, reader.read(0, prior, 0, prior.length));
          assertArrayEquals(original, prior);
          assertEquals(original.length, first.size());
          first.write(0, new byte[] { 77 }, 0, 1);
          byte[] current = new byte[6];
          assertEquals(current.length, replacement.read(0, current, 0, current.length));
          assertArrayEquals(new byte[] { 17, 17, 17, 17, 17, 17 }, current);
          assertEquals(5, replacement.read(8190, current, 0, 5));
          assertArrayEquals(new byte[] { 1, 2, 3, 4, 5 }, Arrays.copyOf(current, 5));
        }
      }
    } finally {
      fs.checkNoOpenFiles();
    }
  }

}
