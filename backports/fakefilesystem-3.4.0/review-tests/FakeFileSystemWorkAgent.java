import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Counts actual cursor traversal and controls the read/resize ordering in public API tests. */
public final class FakeFileSystemWorkAgent {
  static volatile Thread measuredThread;
  static final AtomicLong visits = new AtomicLong();
  static volatile Thread pausedReader;
  static volatile CountDownLatch readPaused;
  static volatile CountDownLatch resumeRead;

  public static void segmentVisit() {
    if (Thread.currentThread() == measuredThread) visits.incrementAndGet();
  }

  public static void beforeSeek() {
    if (Thread.currentThread() != pausedReader) return;
    readPaused.countDown();
    try {
      if (!resumeRead.await(10, TimeUnit.SECONDS)) throw new AssertionError("Read was not resumed within 10 seconds");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Read scheduling was interrupted", e);
    }
  }

  public static void premain(String arguments, Instrumentation instrumentation) {
    instrumentation.addTransformer(new ClassFileTransformer() {
      @Override public byte[] transform(ClassLoader loader, String name, Class<?> redefining,
          ProtectionDomain domain, byte[] bytes) {
        if (!name.equals("okio/Buffer$UnsafeCursor")) return null;
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
          @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
              String signature, String[] exceptions) {
            MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (!name.equals("seek")) return delegate;
            return new MethodVisitor(Opcodes.ASM9, delegate) {
              @Override public void visitCode() {
                super.visitCode();
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "FakeFileSystemWorkAgent", "beforeSeek", "()V", false);
              }
              @Override public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                if (opcode == Opcodes.GETFIELD && owner.equals("okio/Segment")
                    && (name.equals("next") || name.equals("prev"))) {
                  super.visitMethodInsn(Opcodes.INVOKESTATIC, "FakeFileSystemWorkAgent", "segmentVisit", "()V", false);
                }
                super.visitFieldInsn(opcode, owner, name, descriptor);
              }
            };
          }
        }, 0);
        return writer.toByteArray();
      }
    });
  }
}
