import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.ProtectionDomain;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import okio.Buffer;
import okio.BufferedSource;
import okio.FileHandle;
import okio.FileSystem;
import okio.Okio;
import okio.Path;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Forces appends between real OS reads, then asserts only public FileHandle operations. */
public final class ShortReadRegression {
  private static final ArrayDeque<Runnable> appends = new ArrayDeque<>();
  private static int callbacks;
  private static int instrumentedReads;

  public static void premain(String arguments, Instrumentation instrumentation) {
    instrumentation.addTransformer(new ClassFileTransformer() {
      @Override public byte[] transform(ClassLoader loader, String name, Class<?> type,
          ProtectionDomain domain, byte[] bytes) {
        if (!name.equals("okio/JvmFileHandle")) return null;
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
          @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
              String signature, String[] exceptions) {
            MethodVisitor target = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (!name.equals("protectedRead")) return target;
            return new MethodVisitor(Opcodes.ASM9, target) {
              @Override public void visitMethodInsn(int opcode, String owner, String name,
                  String descriptor, boolean isInterface) {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                if (owner.equals("java/io/RandomAccessFile") && name.equals("read")
                    && descriptor.equals("([BII)I")) {
                  // Preserve the actual read result. The callback only appends file bytes.
                  super.visitInsn(Opcodes.DUP);
                  super.visitMethodInsn(Opcodes.INVOKESTATIC, "ShortReadRegression", "afterRead", "(I)V", false);
                  instrumentedReads++;
                }
              }
            };
          }
        }, 0);
        return writer.toByteArray();
      }
    });
  }

  public static void afterRead(int count) {
    if (count <= 0 || appends.isEmpty()) return;
    callbacks++;
    appends.removeFirst().run();
  }

  private static void appendLater(java.nio.file.Path file, String text) {
    appends.addLast(() -> {
      try {
        Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
      } catch (java.io.IOException failure) {
        throw new java.io.UncheckedIOException(failure);
      }
    });
  }

  public static void main(String[] arguments) throws Exception {
    java.nio.file.Path directory = Files.createTempDirectory("okio-short-read-");
    java.nio.file.Path file = directory.resolve("spool");
    List<String> failures = new ArrayList<>();
    try {
      // Sequential on purpose: each case forces appends during the read it is verifying.
      for (String mode : Arrays.asList("array-offset", "buffer-tail", "source-offset")) {
        Files.writeString(file, "xxprefix", StandardCharsets.UTF_8);
        callbacks = 0;
        appendLater(file, "tail");
        appendLater(file, "end");
        String actual;
        try (FileHandle handle = FileSystem.SYSTEM.openReadOnly(Path.get(file))) {
          if (mode.equals("array-offset")) {
            byte[] destination = new byte[25];
            Arrays.fill(destination, (byte) '-');
            int count = handle.read(2L, destination, 3, 20);
            if (count != 13) failures.add(mode + ": expected byte count 13, actual " + count);
            actual = new String(destination, StandardCharsets.UTF_8);
            // Construct the suffix from the unchanged destination capacity, not returned count.
            String expected = "---prefixtailend" + "-".repeat(25 - 3 - 13);
            if (!expected.equals(actual)) failures.add(mode + ": expected '" + expected + "', actual '" + actual + "'");
            if (handle.read(15L, destination, 0, 1) != -1) failures.add(mode + ": reading physical EOF must return -1");
          } else if (mode.equals("buffer-tail")) {
            Buffer buffer = new Buffer().writeUtf8("sentinel:");
            long count = handle.read(2L, buffer, 64L);
            if (count != 13L) failures.add(mode + ": expected byte count 13, actual " + count);
            actual = buffer.readUtf8();
            if (!"sentinel:prefixtailend".equals(actual)) failures.add(mode + ": expected 'sentinel:prefixtailend', actual '" + actual + "'");
          } else {
            try (BufferedSource source = Okio.buffer(handle.source(2L))) {
              actual = source.readUtf8();
              if (!"prefixtailend".equals(actual)) failures.add(mode + ": expected 'prefixtailend', actual '" + actual + "'");
              if (handle.position(source) != 15L) failures.add(mode + ": expected source position 15, actual " + handle.position(source));
            }
          }
        }
        if (callbacks != 2 || !appends.isEmpty()) failures.add(mode + ": expected two forced appends, actual " + callbacks);
        if (!"xxprefixtailend".equals(Files.readString(file))) {
          failures.add(mode + ": physical file differs from the completed producer writes");
        }
        System.out.println("SCENARIO " + mode + ": callbacks=" + callbacks + ", actual='" + actual + "'");
      }
      if (instrumentedReads != 1) throw new AssertionError("Expected exactly one instrumented OS read call site, actual " + instrumentedReads);
      if (!failures.isEmpty()) throw new AssertionError("FileHandle must preserve bytes across short reads: " + failures);
      System.out.println("ALL TESTS PASSED: fileHandlePreservesAppendBetweenShortReads (3 forced schedules)");
    } finally {
      appends.clear();
      Files.deleteIfExists(file);
      Files.delete(directory);
    }
  }
}
