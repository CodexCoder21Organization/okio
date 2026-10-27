import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import okio.BufferedSink;
import okio.FileHandle;
import okio.Okio;
import okio.Path;
import okio.fakefilesystem.FakeFileSystem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FakeFileSystemAppendWorkTest {
  private static final int BLOCK_SIZE = 8192;
  private static final int SMALL_BLOCK_COUNT = 1024;
  private static final int LARGE_BLOCK_COUNT = 4096;
  private static final int WARM_UP_BLOCK_COUNT = 128;
  private static final long FIXED_ALLOCATION_ALLOWANCE = 4L * 1024 * 1024;
  private static final long MAX_ALLOCATION_GROWTH_FACTOR = 6L;
  private static final long MAX_ALLOCATED_BYTES_PER_INPUT_BYTE = 12L;
  private static final Path PATH = Path.Companion.get("/append-work", false);
  private static final byte[] BLOCK = block();

  @Test
  public void fileHandleWritesAtEndHaveBoundedAllocationAndExactBytes() throws Exception {
    com.sun.management.ThreadMXBean allocationBean = allocationBean();
    measureFileHandleAppends(allocationBean, WARM_UP_BLOCK_COUNT);

    long smallAllocation = measureFileHandleAppends(allocationBean, SMALL_BLOCK_COUNT);
    long largeAllocation = measureFileHandleAppends(allocationBean, LARGE_BLOCK_COUNT);

    assertBoundedAllocationGrowth("FileHandle.write", smallAllocation, largeAllocation,
        SMALL_BLOCK_COUNT * BLOCK_SIZE, LARGE_BLOCK_COUNT * BLOCK_SIZE);
  }

  @Test
  public void reopenedAppendingSinksHaveBoundedAllocationAndExactBytes() throws Exception {
    com.sun.management.ThreadMXBean allocationBean = allocationBean();
    measureAppendingSinkAppends(allocationBean, WARM_UP_BLOCK_COUNT);

    long smallAllocation = measureAppendingSinkAppends(allocationBean, SMALL_BLOCK_COUNT);
    long largeAllocation = measureAppendingSinkAppends(allocationBean, LARGE_BLOCK_COUNT);

    assertBoundedAllocationGrowth("reopened appendingSink", smallAllocation, largeAllocation,
        SMALL_BLOCK_COUNT * BLOCK_SIZE, LARGE_BLOCK_COUNT * BLOCK_SIZE);
  }

  private static com.sun.management.ThreadMXBean allocationBean() {
    java.lang.management.ThreadMXBean managementBean = ManagementFactory.getThreadMXBean();
    assertTrue(managementBean instanceof com.sun.management.ThreadMXBean,
        "The JVM must expose its public thread-allocation counter");
    com.sun.management.ThreadMXBean allocationBean = (com.sun.management.ThreadMXBean) managementBean;
    assertTrue(allocationBean.isThreadAllocatedMemorySupported(),
        "The JVM must support current-thread allocated-byte measurements");
    if (!allocationBean.isThreadAllocatedMemoryEnabled()) {
      allocationBean.setThreadAllocatedMemoryEnabled(true);
    }
    assertTrue(allocationBean.isThreadAllocatedMemoryEnabled(),
        "Current-thread allocated-byte measurements must be enabled");
    return allocationBean;
  }

  private static byte[] block() {
    byte[] result = new byte[BLOCK_SIZE];
    for (int i = 0; i < result.length; i++) {
      result[i] = (byte) (i * 31 + (i >>> 7));
    }
    return result;
  }

  private static long measureFileHandleAppends(com.sun.management.ThreadMXBean allocationBean, int blockCount)
      throws IOException {
    FakeFileSystem fileSystem = new FakeFileSystem();
    FileHandle handle = null;
    try {
      handle = fileSystem.openReadWrite(PATH, false, false);
      long threadId = Thread.currentThread().getId();
      long allocatedBefore = allocationBean.getThreadAllocatedBytes(threadId);
      if (allocatedBefore < 0L) throw new AssertionError("The JVM did not report current-thread allocations");
      long fileOffset = 0L;
      for (int i = 0; i < blockCount; i++) {
        handle.write(fileOffset, BLOCK, 0, BLOCK.length);
        fileOffset += BLOCK.length;
      }
      long allocatedAfter = allocationBean.getThreadAllocatedBytes(threadId);
      if (allocatedAfter < allocatedBefore) throw new AssertionError("The current-thread allocation counter decreased");
      long allocatedBytes = allocatedAfter - allocatedBefore;
      assertExactContents(handle, fileOffset);
      return allocatedBytes;
    } finally {
      try {
        if (handle != null) handle.close();
      } finally {
        fileSystem.checkNoOpenFiles();
      }
    }
  }

  private static long measureAppendingSinkAppends(com.sun.management.ThreadMXBean allocationBean, int blockCount)
      throws IOException {
    FakeFileSystem fileSystem = new FakeFileSystem();
    try {
      long threadId = Thread.currentThread().getId();
      long allocatedBefore = allocationBean.getThreadAllocatedBytes(threadId);
      if (allocatedBefore < 0L) throw new AssertionError("The JVM did not report current-thread allocations");
      for (int i = 0; i < blockCount; i++) {
        try (BufferedSink sink = Okio.buffer(fileSystem.appendingSink(PATH, false))) {
          sink.write(BLOCK);
        }
      }
      long allocatedAfter = allocationBean.getThreadAllocatedBytes(threadId);
      if (allocatedAfter < allocatedBefore) throw new AssertionError("The current-thread allocation counter decreased");
      long allocatedBytes = allocatedAfter - allocatedBefore;
      try (FileHandle handle = fileSystem.openReadOnly(PATH)) {
        assertExactContents(handle, (long) blockCount * BLOCK_SIZE);
      }
      return allocatedBytes;
    } finally {
      fileSystem.checkNoOpenFiles();
    }
  }

  private static void assertExactContents(FileHandle handle, long expectedByteCount) throws IOException {
    assertEquals(expectedByteCount, handle.size(), "The append result must have the expected total size");
    byte[] actual = new byte[BLOCK_SIZE];
    for (long offset = 0L; offset < expectedByteCount; offset += BLOCK_SIZE) {
      int bytesRead = handle.read(offset, actual, 0, actual.length);
      assertEquals(BLOCK_SIZE, bytesRead, "Every complete block must be readable at offset " + offset);
      if (!Arrays.equals(BLOCK, actual)) {
        for (int i = 0; i < BLOCK_SIZE; i++) {
          if (BLOCK[i] != actual[i]) {
            throw new AssertionError("Appended byte differs at file offset " + (offset + i));
          }
        }
        throw new AssertionError("Appended block differs at file offset " + offset);
      }
    }
    byte[] eofProbe = new byte[1];
    assertEquals(-1, handle.read(expectedByteCount, eofProbe, 0, 1), "Reading after the append result must return EOF");
  }

  private static void assertBoundedAllocationGrowth(String appendPath, long smallAllocation, long largeAllocation,
      long smallInputBytes, long largeInputBytes) {
    long maximumGrowth = smallAllocation * MAX_ALLOCATION_GROWTH_FACTOR + FIXED_ALLOCATION_ALLOWANCE;
    assertTrue(largeAllocation <= maximumGrowth,
        appendPath + " allocated " + largeAllocation + " bytes for 32 MiB, versus " + smallAllocation
            + " bytes for 8 MiB; growth must stay within " + MAX_ALLOCATION_GROWTH_FACTOR
            + "x plus " + FIXED_ALLOCATION_ALLOWANCE + " fixed bytes");

    long maximumAbsoluteAllocation = largeInputBytes * MAX_ALLOCATED_BYTES_PER_INPUT_BYTE
        + FIXED_ALLOCATION_ALLOWANCE;
    assertTrue(largeAllocation <= maximumAbsoluteAllocation,
        appendPath + " allocated " + largeAllocation + " bytes for " + largeInputBytes
            + " input bytes; the absolute proportional limit is " + MAX_ALLOCATED_BYTES_PER_INPUT_BYTE
            + " allocated bytes per input byte plus " + FIXED_ALLOCATION_ALLOWANCE);
    assertTrue(smallAllocation <= smallInputBytes * MAX_ALLOCATED_BYTES_PER_INPUT_BYTE
            + FIXED_ALLOCATION_ALLOWANCE,
        appendPath + " allocated " + smallAllocation + " bytes for " + smallInputBytes
            + " input bytes; the absolute proportional limit is " + MAX_ALLOCATED_BYTES_PER_INPUT_BYTE
            + " allocated bytes per input byte plus " + FIXED_ALLOCATION_ALLOWANCE);
    System.out.println(appendPath + " allocated bytes: 8 MiB=" + smallAllocation + ", 32 MiB=" + largeAllocation);
  }
}
