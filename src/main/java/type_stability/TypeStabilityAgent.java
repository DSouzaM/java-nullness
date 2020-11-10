package type_stability;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.lang.instrument.*;
import java.security.ProtectionDomain;


public class TypeStabilityAgent {
    public static void premain(String agentArgs, Instrumentation inst) {
        String prefix = null;
        if (agentArgs != null) {
            String[] args = agentArgs.split(" ");
            if (args.length > 1) {
                throw new IllegalArgumentException("Agent takes at most one argument; " + args.length + " found.");
            }
            prefix = args.length == 1 ? args[0] : null;
        }
        inst.addTransformer(new TypeStabilityTransformer(prefix));
    }
}

class TypeStabilityTransformer implements ClassFileTransformer {
    String prefix;

    TypeStabilityTransformer(String prefix) {
        this.prefix = prefix;
    }

    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (!className.startsWith(this.prefix)) {
            return null;
        }

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
