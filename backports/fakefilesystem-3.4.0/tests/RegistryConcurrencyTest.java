import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import okio.*;
import okio.fakefilesystem.FakeFileSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

public class RegistryConcurrencyTest {
  @Test @Timeout(120)
  public void concurrentDistinctFileOpenClose() throws Exception {
    int failedRuns = 0;
    for (int trial = 0; trial < 20; trial++) {
      var fs = new FakeFileSystem();
      var paths = new ArrayList<Path>();
      for (int i = 0; i < 9; i++) {
        Path path = Path.Companion.get("/file-" + i, false);
        paths.add(path);
        try (BufferedSink sink = Okio.buffer(fs.sink(path, false))) { sink.writeUtf8(path.name()); }
      }
      var errors = new ConcurrentLinkedQueue<Throwable>();
      var start = new CountDownLatch(1);
      var stop = new AtomicBoolean(false);
      var threads = new ArrayList<Thread>();
      for (int i = 0; i < 9; i++) {
        final int index = i;
        Path path = paths.get(i);
        threads.add(new Thread(() -> {
          try {
            start.await();
            for (int operation = 0; operation < 10000 && !stop.get(); operation++) {
              if (index == 0) {
                try (BufferedSource source = Okio.buffer(fs.source(path))) { assertEquals(path.name(), source.readUtf8()); }
              } else {
                try (FileHandle handle = fs.openReadOnly(path)) { assertEquals(path.name().length(), handle.size()); }
              }
            }
          } catch (Throwable failure) { errors.add(failure); stop.set(true); }
        }, "registry-" + trial + "-" + i));
      }
      try {
        threads.forEach(Thread::start);
        start.countDown();
        for (Thread thread : threads) thread.join();
        try { fs.checkNoOpenFiles(); assertEquals(Collections.emptyList(), fs.openPaths()); }
        catch (Throwable failure) { errors.add(failure); }
        if (!errors.isEmpty()) { failedRuns++; errors.forEach(Throwable::printStackTrace); }
        System.out.println("R576 trial " + (trial + 1) + ": " + (errors.isEmpty() ? "PASS" : "FAIL"));
      } finally {
        stop.set(true);
        start.countDown();
        for (Thread thread : threads) thread.join();
      }
    }
    assertEquals(0, failedRuns, "Concurrent distinct-file opens and closes must preserve the registry");
  }
}
