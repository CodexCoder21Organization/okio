import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import okio.BufferedSink;
import okio.Buffer;
import okio.ByteString;
import okio.FileHandle;
import okio.Okio;
import okio.Path;
import okio.fakefilesystem.FakeFileSystem;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FakeFileSystemBulkReadTest {
  private static final int SEGMENT_SIZE = 8192;
  private static final byte GUARD = (byte) 0x5a;
  private static final Path PATH = Path.Companion.get("/bulk-read", false);

  private static void write(FakeFileSystem fileSystem, byte[] contents) throws IOException {
    try (BufferedSink sink = Okio.buffer(fileSystem.sink(PATH, false))) {
      sink.write(contents);
    }
  }

  private static byte[] contents(int size) {
    byte[] result = new byte[size];
    for (int i = 0; i < result.length; i++) {
      result[i] = (byte) (i * 31 + (i >>> 7));
    }
    return result;
  }

  private static void assertEofWithoutMutation(FileHandle handle, long fileOffset) {
    byte[] destination = new byte[9];
    Arrays.fill(destination, GUARD);
    Integer result = null;
    Throwable failure = null;
    try {
      result = handle.read(fileOffset, destination, 2, 4);
    } catch (Exception thrown) {
      failure = thrown;
    }
    final Integer actualResult = result;
    final Throwable actualFailure = failure;
    assertAll("read at offset " + fileOffset,
        () -> assertNull(actualFailure, "read should return EOF, but threw " + actualFailure),
        () -> assertEquals(-1, actualResult, "an offset beyond the file should return EOF"),
        () -> assertArrayEquals(new byte[] { GUARD, GUARD, GUARD, GUARD, GUARD, GUARD, GUARD, GUARD, GUARD },
            destination, "EOF must leave the destination unchanged"));
  }

  @Test
  public void arrayReadCopiesAcrossSegmentsAndKeepsPublicFileHandleBehavior() throws Exception {
    FakeFileSystem fileSystem = new FakeFileSystem();
    byte[] expected = contents(4 * SEGMENT_SIZE + 33);
    FileHandle handle = null;
    try {
      write(fileSystem, expected);
      handle = fileSystem.openReadOnly(PATH);
      final FileHandle readHandle = handle;

      int sourceOffset = SEGMENT_SIZE - 3;
      int destinationOffset = 4;
      int byteCount = 2 * SEGMENT_SIZE + 17;
      byte[] destination = new byte[byteCount + destinationOffset + 6];
      Arrays.fill(destination, GUARD);
      int bytesRead = handle.read(sourceOffset, destination, destinationOffset, byteCount);
      assertEquals(byteCount, bytesRead, "read must copy the complete requested range");
      assertArrayEquals(Arrays.copyOfRange(expected, sourceOffset, sourceOffset + byteCount),
          Arrays.copyOfRange(destination, destinationOffset, destinationOffset + byteCount),
          "the copied bytes must match across ByteString segments");
      for (int i = 0; i < destinationOffset; i++) {
        assertEquals(GUARD, destination[i], "read must preserve the prefix guard at index " + i);
      }
      for (int i = destinationOffset + byteCount; i < destination.length; i++) {
        assertEquals(GUARD, destination[i], "read must preserve the suffix guard at index " + i);
      }

      byte[] eofDestination = new byte[8];
      Arrays.fill(eofDestination, GUARD);
      assertEquals(-1, handle.read(expected.length, eofDestination, 2, 4), "read at EOF must return -1");
      assertArrayEquals(new byte[] { GUARD, GUARD, GUARD, GUARD, GUARD, GUARD, GUARD, GUARD }, eofDestination,
          "read at EOF must leave the destination unchanged");

      byte[] zeroCountDestination = new byte[8];
      Arrays.fill(zeroCountDestination, GUARD);
      assertEquals(-1, handle.read(5, zeroCountDestination, 2, 0),
          "a zero-count read retains FakeFileSystem's existing no-bytes result");
      assertArrayEquals(new byte[] { GUARD, GUARD, GUARD, GUARD, GUARD, GUARD, GUARD, GUARD },
          zeroCountDestination, "a zero-count read must leave the destination unchanged");

      assertAll("large file offsets",
          () -> assertEofWithoutMutation(readHandle, 1L << 32),
          () -> assertEofWithoutMutation(readHandle, Long.MAX_VALUE));

      readHandle.close();
      final FileHandle closedHandle = readHandle;
      IllegalStateException closed = assertThrows(IllegalStateException.class,
          () -> closedHandle.read(0, new byte[1], 0, 1), "reads after close must fail through the public API");
      assertEquals("closed", closed.getMessage());
    } finally {
      if (handle != null) handle.close();
      fileSystem.checkNoOpenFiles();
    }
  }

  @RepeatedTest(10)
  public void repeatedBulkReadsCompleteWithBoundedWorkAndExactBytes() throws Exception {
    final int size = 32 * 1024 * 1024;
    byte[] expected = contents(size);
    FakeFileSystem fileSystem = new FakeFileSystem();
    FileHandle handle = null;
    try {
      write(fileSystem, expected);
      handle = fileSystem.openReadOnly(PATH);
      byte[] destination = new byte[size + 11];
      final FileHandle readHandle = handle;
      ThreadMXBean threadClock = ManagementFactory.getThreadMXBean();
      assertTrue(threadClock.isCurrentThreadCpuTimeSupported(), "The JVM must support current-thread CPU time");
      if (!threadClock.isThreadCpuTimeEnabled()) threadClock.setThreadCpuTimeEnabled(true);
      ByteString control = new Buffer().write(expected).snapshot();
      byte[] controlDestination = new byte[size + 11];
      long controlCpuNanos = 0L;
      long readCpuNanos = 0L;
      for (int iteration = 0; iteration < 1000; iteration++) {
        int sourceOffset = iteration & 1;
        int destinationOffset = (iteration & 1) == 0 ? 3 : 5;
        int byteCount = size - sourceOffset;
        Arrays.fill(destination, 0, destinationOffset, GUARD);
        Arrays.fill(destination, destinationOffset + byteCount, destination.length, GUARD);
        long controlStart = threadClock.getCurrentThreadCpuTime();
        control.copyInto(sourceOffset, controlDestination, destinationOffset, byteCount);
        controlCpuNanos += threadClock.getCurrentThreadCpuTime() - controlStart;
        long readStart = threadClock.getCurrentThreadCpuTime();
        int bytesRead = readHandle.read(sourceOffset, destination, destinationOffset, byteCount);
        readCpuNanos += threadClock.getCurrentThreadCpuTime() - readStart;
        assertTrue(controlCpuNanos > 0L, "Bulk-copy control must have a positive measured CPU cost");
        assertTrue(readCpuNanos <= controlCpuNanos * 8L,
            "FileHandle reads used " + readCpuNanos + " CPU ns versus " + controlCpuNanos
                + " for equivalent bulk copies at iteration " + iteration);
        assertEquals(byteCount, bytesRead, "iteration " + iteration + " must copy the requested bytes");
        if (Thread.currentThread().isInterrupted()) {
          throw new AssertionError("read loop interrupted after iteration " + iteration);
        }
        assertEquals(expected[sourceOffset], destination[destinationOffset],
            "first byte mismatch at iteration " + iteration);
        int middle = byteCount / 2;
        assertEquals(expected[sourceOffset + middle], destination[destinationOffset + middle],
            "middle byte mismatch at iteration " + iteration);
        assertEquals(expected[size - 1], destination[destinationOffset + byteCount - 1],
            "last byte mismatch at iteration " + iteration);
        for (int i = 0; i < destinationOffset; i++) {
          assertEquals(GUARD, destination[i], "prefix guard changed at iteration " + iteration + ", index " + i);
        }
        for (int i = destinationOffset + byteCount; i < destination.length; i++) {
          assertEquals(GUARD, destination[i], "suffix guard changed at iteration " + iteration + ", index " + i);
        }
      }

      System.out.println("1000 FileHandle reads used " + readCpuNanos + " CPU ns versus " + controlCpuNanos
          + " for equivalent ByteString.copyInto bulk copies");

      int finalOffset = 4;
      int finalCount = readHandle.read(0, destination, finalOffset, size);
      assertEquals(size, finalCount, "final complete read must return the full file size");
      for (int i = 0; i < size; i++) {
        if (expected[i] != destination[finalOffset + i]) {
          throw new AssertionError("full byte comparison failed at index " + i);
        }
      }
    } finally {
      if (handle != null) handle.close();
      fileSystem.checkNoOpenFiles();
    }
  }
}
