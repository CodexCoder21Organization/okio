import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import okio.FileHandle;
import okio.Path;
import okio.fakefilesystem.FakeFileSystem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FakeFileSystemReplacementPublicationTest {
  @Test public void replacementIsCompleteBeforePathReadersCanObserveIt() throws Exception {
    FakeFileSystem fs = new FakeFileSystem();
    fs.emulateUnix();
    Path path = Path.Companion.get("/publication", false);
    byte[] expected = {1, 2, 3};
    AtomicReference<Throwable> readerFailure = new AtomicReference<>();
    AtomicReference<Throwable> openerFailure = new AtomicReference<>();
    AtomicReference<Throwable> observerFailure = new AtomicReference<>();
    AtomicReference<FileHandle> replacement = new AtomicReference<>();
    FakeFileSystemWorkAgent.readPaused = new CountDownLatch(1);
    FakeFileSystemWorkAgent.resumeRead = new CountDownLatch(1);
    try (FileHandle original = fs.openReadWrite(path, true, false)) {
      original.write(0, expected, 0, expected.length);
      Thread reader = new Thread(() -> {
        try {
          byte[] actual = new byte[expected.length];
          assertEquals(actual.length, original.read(0, actual, 0, actual.length));
          assertArrayEquals(expected, actual);
        } catch (Throwable failure) { readerFailure.set(failure); }
      }, "publication-reader");
      Thread opener = new Thread(() -> {
        try { replacement.set(fs.openReadWrite(path, false, true)); }
        catch (Throwable failure) { openerFailure.set(failure); }
      }, "publication-opener");
      Thread observer = new Thread(() -> {
        try {
          assertEquals(Long.valueOf(expected.length), fs.metadata(path).getSize(),
              "Replacement exposed an empty file before copying completed");
          try (FileHandle handle = fs.openReadOnly(path)) {
            byte[] actual = new byte[expected.length];
            assertEquals(actual.length, handle.read(0, actual, 0, actual.length));
            assertArrayEquals(expected, actual);
          }
        } catch (Throwable failure) { observerFailure.set(failure); }
      }, "publication-observer");
      FakeFileSystemWorkAgent.pausedReader = reader;
      try {
        reader.start();
        assertTrue(FakeFileSystemWorkAgent.readPaused.await(10, TimeUnit.SECONDS), "Read did not reach seek");
        opener.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        boolean copyWaiting = false;
        while (opener.isAlive() && System.nanoTime() < deadline) {
          ThreadInfo state = ManagementFactory.getThreadMXBean().getThreadInfo(opener.getId());
          if (state != null && state.getLockOwnerId() == reader.getId()) {
            copyWaiting = true;
            break;
          }
          LockSupport.parkNanos(100000);
        }
        assertTrue(copyWaiting, "Replacement did not wait for original content lock");
        observer.start();
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        boolean reachedBoundary = false;
        while (System.nanoTime() < deadline) {
          if (!observer.isAlive()) { reachedBoundary = true; break; }
          ThreadInfo state = ManagementFactory.getThreadMXBean().getThreadInfo(observer.getId());
          if (state != null && (state.getLockOwnerId() == reader.getId()
              || state.getLockOwnerId() == opener.getId())) {
            reachedBoundary = true;
            break;
          }
          LockSupport.parkNanos(100000);
        }
        assertTrue(reachedBoundary, "Observer neither finished nor waited for a file lock");
      } finally {
        FakeFileSystemWorkAgent.resumeRead.countDown();
        reader.join(10000);
        if (opener.getState() != Thread.State.NEW) opener.join(10000);
        if (observer.getState() != Thread.State.NEW) observer.join(10000);
        FakeFileSystemWorkAgent.pausedReader = null;
        if (replacement.get() != null) replacement.get().close();
      }
      assertFalse(reader.isAlive(), "Reader did not finish");
      assertFalse(opener.isAlive(), "Opener did not finish");
      assertFalse(observer.isAlive(), "Observer did not finish");
      if (readerFailure.get() != null) throw new AssertionError("Original read failed", readerFailure.get());
      if (openerFailure.get() != null) throw new AssertionError("Replacement open failed", openerFailure.get());
      if (observerFailure.get() != null) throw new AssertionError("Path observer failed", observerFailure.get());
      assertEquals(Long.valueOf(expected.length), fs.metadata(path).getSize());
    } finally { fs.checkNoOpenFiles(); }
  }
}
