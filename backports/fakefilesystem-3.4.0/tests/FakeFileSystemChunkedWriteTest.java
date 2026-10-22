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
}
