package type_stability;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.lang.instrument.*;
import java.security.ProtectionDomain;
import java.util.logging.Logger;

public class TypeStabilityAgent {
    public static void premain(String agentArgs, Instrumentation inst) throws IOException {
        String prefix = null;
        String logFile = null;
        if (agentArgs != null) {
            String[] args = agentArgs.split(" ");
            if (args.length > 2) {
                throw new IllegalArgumentException(
                        "Agent expects at most two arguments: the package prefix to instrument and a log file name.");
            }
            prefix = args.length > 0 ? args[0] : null;
            logFile = args.length == 2 ? args[1] : null;
        }
        NullnessLogger.initialize(logFile);
        inst.addTransformer(new TypeStabilityTransformer(prefix));
    }
}

class TypeStabilityTransformer implements ClassFileTransformer {
    private final static Logger LOGGER = Logger.getLogger(TypeStabilityTransformer.class.getName());

    String prefix;

    TypeStabilityTransformer(String prefix) {
        this.prefix = prefix;
    }

    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (!className.startsWith(this.prefix)) {
            return null;
        }
        LOGGER.info("Found transform candidate " + className + ".");

        // Read the byte representation into a ClassNode
        ClassReader cr = new ClassReader(classfileBuffer);
        ClassNode cn = new ClassNode();
        cr.accept(cn, ClassReader.EXPAND_FRAMES);

        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES);

        // Transform the methods of this class
        MethodStabilityTransformer m = new MethodStabilityTransformer();
        cn = m.transformClass(cn);

        // Write the ClassNode back to bytes
        cn.accept(cw);
        return cw.toByteArray();
    }
}
