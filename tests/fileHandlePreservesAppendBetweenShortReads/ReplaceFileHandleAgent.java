import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;

/** Loads the class compiled from the checked-out Okio source for local dependency verification. */
public final class ReplaceFileHandleAgent {
    public static void premain(String sourceClass, Instrumentation instrumentation) throws Exception {
        final byte[] compiledClass = Files.readAllBytes(Path.of(sourceClass));
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> type,
                    ProtectionDomain domain, byte[] bytes) {
                if (!name.equals("okio/JvmFileHandle")) return null;
                System.err.println("LOCAL OKIO SOURCE: " + sourceClass);
                return compiledClass;
            }
        });
    }
}
