import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okio.FileHandle;
import okio.Path;
import okio.fakefilesystem.FakeFileSystem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FakeFileSystemReviewTest {
  @Test public void originalShrinkAndRegrowPreservesReplacement() throws Exception {
    for (int size : new int[] {3, 8192, 8192 + 3, 3 * 8192 + 3}) {
      FakeFileSystem fs = new FakeFileSystem();
      fs.setAllowWritesWhileWriting(true);
      Path path = Path.Companion.get("/views", false);
      byte[] original = new byte[size];
      Arrays.fill(original, (byte) 17);
      try {
        try (FileHandle first = fs.openReadWrite(path, true, false)) {
          first.write(0, original, 0, size);
          try (FileHandle replacement = fs.openReadWrite(path, false, true)) {
            first.resize(size - 2);
            first.resize(size);
            byte[] actual = new byte[size];
            assertEquals(size, replacement.read(0, actual, 0, size));
            assertArrayEquals(original, actual, "Original shrink/regrow changed replacement at size " + size);
            assertEquals(size, first.read(0, actual, 0, size));
            byte[] regrown = original.clone();
            Arrays.fill(regrown, size - 2, size, (byte) 0);
            assertArrayEquals(regrown, actual);
          }
        }
      } finally { fs.checkNoOpenFiles(); }
    }
  }

  @Test public void originalShrinkAndAppendPreservesReplacement() throws Exception {
    FakeFileSystem fs = new FakeFileSystem();
    fs.setAllowWritesWhileWriting(true);
    Path path = Path.Companion.get("/views", false);
    try {
      try (FileHandle first = fs.openReadWrite(path, true, false)) {
        first.write(0, new byte[] {1, 2, 3}, 0, 3);
        try (FileHandle replacement = fs.openReadWrite(path, false, true)) {
          first.resize(1);
          first.write(1, new byte[] {9, 8}, 0, 2);
          byte[] actual = new byte[3];
          assertEquals(3, replacement.read(0, actual, 0, 3));
          assertArrayEquals(new byte[] {1, 2, 3}, actual);
          replacement.resize(1);
          replacement.resize(3);
          assertEquals(3, first.read(0, actual, 0, 3));
          assertArrayEquals(new byte[] {1, 9, 8}, actual);
        }
      }
    } finally { fs.checkNoOpenFiles(); }
  }

  @Test public void sequentialChunkedReadsHaveLinearSegmentWork() throws Exception {
    for (int segments : new int[] {256, 1024}) {
      FakeFileSystem fs = new FakeFileSystem();
      Path path = Path.Companion.get("/chunks", false);
      byte[] chunk = new byte[8192];
      Arrays.fill(chunk, (byte) 37);
      try {
        try (FileHandle writer = fs.openReadWrite(path, true, false)) {
          for (int i = 0; i < segments; i++) writer.write((long) i * chunk.length, chunk, 0, chunk.length);
        }
        try (FileHandle handle = fs.openReadOnly(path)) {
          byte[] actual = new byte[chunk.length];
          FakeFileSystemWorkAgent.visits.set(0);
          FakeFileSystemWorkAgent.measuredThread = Thread.currentThread();
          try {
            for (int i = 0; i < segments; i++) {
              assertEquals(chunk.length, handle.read((long) i * chunk.length, actual, 0, actual.length));
              assertArrayEquals(chunk, actual);
            }
          } finally { FakeFileSystemWorkAgent.measuredThread = null; }
          long visits = FakeFileSystemWorkAgent.visits.get();
          System.out.println("CHUNKED_READ_WORK segments=" + segments + " visits=" + visits);
          assertTrue(visits > 0, "Traversal instrumentation must observe segment visits");
          assertTrue(visits <= 4L * segments, "Sequential reads must visit at most four segment links per chunk: " + visits);
        }
      } finally { fs.checkNoOpenFiles(); }
    }
  }

  @Test public void readAndResizeObserveOneConsistentContentState() throws Exception {
    FakeFileSystem fs = new FakeFileSystem();
    Path path = Path.Companion.get("/concurrent", false);
    AtomicReference<Throwable> readFailure = new AtomicReference<>();
    AtomicReference<Throwable> resizeFailure = new AtomicReference<>();
    CountDownLatch resizeStarted = new CountDownLatch(1);
    FakeFileSystemWorkAgent.readPaused = new CountDownLatch(1);
    FakeFileSystemWorkAgent.resumeRead = new CountDownLatch(1);
    try {
      try (FileHandle handle = fs.openReadWrite(path, true, false)) {
        handle.write(0, new byte[] {1, 2, 3}, 0, 3);
        Thread reader = new Thread(() -> {
          try {
            byte[] actual = new byte[3];
            assertEquals(3, handle.read(0, actual, 0, 3));
            assertArrayEquals(new byte[] {1, 2, 3}, actual);
          } catch (Throwable failure) { readFailure.set(failure); }
        }, "review-reader");
        Thread resizer = new Thread(() -> {
          resizeStarted.countDown();
          try { handle.resize(0); } catch (Throwable failure) { resizeFailure.set(failure); }
        }, "review-resizer");
        FakeFileSystemWorkAgent.pausedReader = reader;
        try {
          reader.start();
          assertTrue(FakeFileSystemWorkAgent.readPaused.await(10, TimeUnit.SECONDS), "Read did not reach seek");
          resizer.start();
          assertTrue(resizeStarted.await(10, TimeUnit.SECONDS), "Resize did not start");
          long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
          while (resizer.isAlive() && resizer.getState() != Thread.State.BLOCKED
              && resizer.getState() != Thread.State.WAITING && System.nanoTime() < deadline) Thread.yield();
          assertTrue(!resizer.isAlive() || resizer.getState() == Thread.State.BLOCKED
              || resizer.getState() == Thread.State.WAITING, "Resize neither finished nor waited for the content lock");
        } finally {
          FakeFileSystemWorkAgent.resumeRead.countDown();
          reader.join(10000);
          if (resizer.getState() != Thread.State.NEW) resizer.join(10000);
          FakeFileSystemWorkAgent.pausedReader = null;
        }
        assertFalse(reader.isAlive(), "Reader did not finish");
        assertFalse(resizer.isAlive(), "Resizer did not finish");
        assertNull(readFailure.get(), "Read failed at the ordered resize: " + readFailure.get());
        assertNull(resizeFailure.get(), "Resize failed: " + resizeFailure.get());
        assertEquals(0, handle.size());
      }
    } finally { fs.checkNoOpenFiles(); }
  }
}
