import java.util.*;
import okio.*;
import okio.fakefilesystem.FakeFileSystem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FakeFileSystemAccessWorkTest {
  @Test public void sequentialOverwritesHaveLinearSegmentWork() throws Exception {
    long[] work = new long[2];
    int run = 0;
    for (int segments : new int[] {256, 1024}) {
      FakeFileSystem fs = new FakeFileSystem();
      Path path = Path.Companion.get("/overwrite", false);
      byte[] chunk = new byte[8192];
      try (FileHandle handle = fs.openReadWrite(path, true, false)) {
        for (int i = 0; i < segments; i++) handle.write((long)i * chunk.length, chunk, 0, chunk.length);
        Arrays.fill(chunk, (byte) 37);
        FakeFileSystemWorkAgent.visits.set(0);
        FakeFileSystemWorkAgent.measuredThread = Thread.currentThread();
        try {
          for (int i = 0; i < segments; i++) handle.write((long)i * chunk.length, chunk, 0, chunk.length);
        } finally { FakeFileSystemWorkAgent.measuredThread = null; }
        work[run++] = FakeFileSystemWorkAgent.visits.get();
        System.out.println("OVERWRITE_WORK segments=" + segments + " visits=" + work[run-1]);
        byte[] actual = new byte[chunk.length];
        for (int i = 0; i < segments; i++) {
          assertEquals(chunk.length, handle.read((long)i * chunk.length, actual, 0, actual.length));
          assertArrayEquals(chunk, actual);
        }
      }
      fs.checkNoOpenFiles();
    }
    assertTrue(work[1] <= work[0] * 6, "Four times the sequential overwrite input must not require quadratic segment work: " + Arrays.toString(work));
  }

  @Test public void randomAccessReadsAvoidLinearTraversalPerByte() throws Exception {
    long[] work = new long[2];
    int run = 0;
    for (int segments : new int[] {256, 1024}) {
      FakeFileSystem fs = new FakeFileSystem();
      Path path = Path.Companion.get("/random-reads", false);
      byte[] chunk = new byte[8192];
      Arrays.fill(chunk, (byte) 37);
      try (FileHandle handle = fs.openReadWrite(path, true, false)) {
        for (int i = 0; i < segments; i++) handle.write((long)i * chunk.length, chunk, 0, chunk.length);
        byte[] actual = new byte[1];
        FakeFileSystemWorkAgent.visits.set(0);
        FakeFileSystemWorkAgent.measuredThread = Thread.currentThread();
        try {
          for (int i = 0; i < segments; i++) {
            long offset = (long)((i & 1) == 0 ? segments / 4 : 3 * segments / 4) * chunk.length;
            assertEquals(1, handle.read(offset, actual, 0, 1));
            assertEquals(37, actual[0]);
          }
        } finally { FakeFileSystemWorkAgent.measuredThread = null; }
        work[run++] = FakeFileSystemWorkAgent.visits.get();
        System.out.println("RANDOM_READ_WORK segments=" + segments + " visits=" + work[run-1]);
      }
      fs.checkNoOpenFiles();
    }
    assertTrue(work[1] <= Math.max(1, work[0]) * 6, "Four times the random read input must not require quadratic segment work: " + Arrays.toString(work));
  }

  @Test public void replacementViewsSurviveBoundaryMutations() throws Exception {
    for (int size : new int[] {1, 2, 8191, 8192, 8193, 16384, 16385, 24579, 65535, 65536, 65537, 131075}) {
      for (int shrink : new int[] {0, 1, size/2, size-1, size}) {
        FakeFileSystem fs = new FakeFileSystem();
        fs.setAllowReadsWhileWriting(true);
        fs.setAllowWritesWhileWriting(true);
        Path path = Path.Companion.get("/views", false);
        byte[] before = new byte[size];
        for (int i=0; i<size; i++) before[i]=(byte)(i*31+7);
        try (FileHandle first=fs.openReadWrite(path,true,false)) {
          first.write(0,before,0,size);
          try (FileHandle second=fs.openReadWrite(path,false,true); FileHandle third=fs.openReadWrite(path,false,true)) {
            first.resize(shrink);
            first.resize(size+3);
            byte[] expected=Arrays.copyOf(before,size+3);
            Arrays.fill(expected,shrink,expected.length,(byte)0);
            byte[] actual=new byte[size+3];
            assertEquals(actual.length,first.read(0,actual,0,actual.length));
            assertArrayEquals(expected,actual);
            byte[] unchanged=new byte[size];
            assertEquals(size,second.read(0,unchanged,0,size));
            assertArrayEquals(before,unchanged);
            second.resize(shrink);
            byte[] tail=new byte[size-shrink+3];
            Arrays.fill(tail,(byte)77);
            second.write(shrink,tail,0,tail.length);
            assertEquals(size,third.read(0,unchanged,0,size));
            assertArrayEquals(before,unchanged);
            assertEquals(actual.length,first.read(0,actual,0,actual.length));
            assertArrayEquals(expected,actual);
          }
        }
        fs.checkNoOpenFiles();
      }
    }
  }

  @Test public void openStreamsSurviveHandleCloseMoveCopyAndDelete() throws Exception {
    FakeFileSystem fs=new FakeFileSystem();
    fs.emulateUnix();
    Path a=Path.Companion.get("/a",false), b=Path.Companion.get("/b",false), c=Path.Companion.get("/c",false);
    FileHandle h=fs.openReadWrite(a,true,false);
    byte[] data=new byte[24579];
    for(int i=0;i<data.length;i++) data[i]=(byte)(i*17+3);
    h.write(0,data,0,data.length);
    try(Source source=h.source(0); Sink sink=h.appendingSink()) {
      h.close();
      fs.atomicMove(a,b);
      fs.copy(b,c);
      fs.delete(b,true);
      Buffer appended=new Buffer().writeUtf8("tail");
      sink.write(appended,4);
      Buffer result=new Buffer();
      while(source.read(result,997)!=-1) {}
      byte[] expected=Arrays.copyOf(data,data.length+4);
      System.arraycopy(new byte[]{116,97,105,108},0,expected,data.length,4);
      assertArrayEquals(expected,result.readByteArray());
      try(FileHandle copy=fs.openReadOnly(c)) {
        byte[] actual=new byte[data.length];
        assertEquals(data.length,copy.read(0,actual,0,actual.length));
        assertArrayEquals(data,actual);
      }
    } finally {h.close();}
    fs.checkNoOpenFiles();
  }
  @Test public void randomMutationsMatchIndependentViewModels() throws Exception {
    FakeFileSystem fs=new FakeFileSystem();
    fs.emulateUnix();
    Path path=Path.Companion.get("/model",false);
    Random random=new Random(20261022L);
    List<FileHandle> handles=new ArrayList<>();
    List<byte[]> models=new ArrayList<>();
    handles.add(fs.openReadWrite(path,true,false));
    models.add(new byte[0]);
    try {
      for(int step=0;step<4000;step++) {
        if(step%100==0 && handles.size()<12) {
          handles.add(fs.openReadWrite(path,false,true));
          models.add(models.get(models.size()-1).clone());
        }
        int view=random.nextInt(handles.size());
        FileHandle handle=handles.get(view);
        byte[] before=models.get(view);
        int operation=random.nextInt(4);
        if(operation==0) {
          int size=random.nextInt(4 * 65536 + 2);
          handle.resize(size);
          models.set(view,Arrays.copyOf(before,size));
        } else if(operation<=2) {
          int offset=operation==2 ? before.length : random.nextInt(4 * 65536 + 2);
          byte[] patch=new byte[random.nextInt(8196)];
          random.nextBytes(patch);
          byte[] expected=Arrays.copyOf(before,Math.max(before.length,offset+patch.length));
          System.arraycopy(patch,0,expected,offset,patch.length);
          handle.write(offset,patch,0,patch.length);
          models.set(view,expected);
        } else {
          int offset=random.nextInt(before.length+5);
          byte[] actual=new byte[random.nextInt(8196)+1];
          int expected=offset>=before.length ? -1 : Math.min(actual.length,before.length-offset);
          assertEquals(expected,handle.read(offset,actual,0,actual.length),"step="+step);
          if(expected>0) assertArrayEquals(Arrays.copyOfRange(before,offset,offset+expected),Arrays.copyOf(actual,expected));
        }
        if(step%37==0) for(int i=0;i<handles.size();i++) {
          byte[] expected=models.get(i), actual=new byte[expected.length];
          assertEquals(expected.length,handles.get(i).size());
          if(actual.length>0) {
            assertEquals(actual.length,handles.get(i).read(0,actual,0,actual.length));
            assertArrayEquals(expected,actual,"step="+step+" view="+i);
          }
        }
      }
    } finally {
      for(FileHandle handle:handles) handle.close();
      fs.checkNoOpenFiles();
    }
  }

  @Test public void concurrentReadWriteResizeAndMetadataStayConsistent() throws Exception {
    FakeFileSystem fs=new FakeFileSystem();
    fs.emulateUnix();
    Path path=Path.Companion.get("/concurrent-paths",false);
    int size=3*8192+3;
    try(FileHandle writer=fs.openReadWrite(path,true,false); FileHandle reader=fs.openReadOnly(path)) {
      writer.resize(size);
      java.util.concurrent.ExecutorService workers=java.util.concurrent.Executors.newFixedThreadPool(3, task -> {
        Thread thread=new Thread(task,"rv-okio8b-consistency"); thread.setDaemon(true); return thread;
      });
      try {
        java.util.concurrent.Future<?> writes=workers.submit(() -> {
          try {
            byte[] bytes=new byte[size];
            for(int i=0;i<2000;i++) {
              writer.resize(0); writer.resize(size);
              Arrays.fill(bytes,(byte)((i&1)==0?17:37));
              writer.write(0,bytes,0,bytes.length);
            }
          } catch(Exception e) {throw new RuntimeException(e);}
        });
        java.util.concurrent.Future<?> reads=workers.submit(() -> {
          try {
            byte[] bytes=new byte[size];
            for(int i=0;i<4000;i++) {
              int count=reader.read(0,bytes,0,bytes.length);
              assertTrue(count==-1 || count==size,"Unexpected partial size "+count);
              if(count>0) {
                byte value=bytes[0]; assertTrue(value==0 || value==17 || value==37);
                for(byte b:bytes) assertEquals(value,b);
              }
            }
          } catch(Exception e) {throw new RuntimeException(e);}
        });
        java.util.concurrent.Future<?> metadata=workers.submit(() -> {
          try {
            for(int i=0;i<2000;i++) {
              long length=fs.metadata(path).getSize();
              assertTrue(length==0 || length==size);
              try(FileHandle another=fs.openReadOnly(path)) {
                byte[] first=new byte[1];
                int count=another.read(0,first,0,1);
                assertTrue(count==-1 || count==1);
              }
            }
          } catch(Exception e) {throw new RuntimeException(e);}
        });
        writes.get(20,java.util.concurrent.TimeUnit.SECONDS);
        reads.get(20,java.util.concurrent.TimeUnit.SECONDS);
        metadata.get(20,java.util.concurrent.TimeUnit.SECONDS);
      } finally {
        workers.shutdownNow();
        assertTrue(workers.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS),"Concurrent helpers did not stop");
      }
    } finally {fs.checkNoOpenFiles();}
  }

}
