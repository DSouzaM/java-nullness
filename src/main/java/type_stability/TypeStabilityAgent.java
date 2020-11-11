package type_stability;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.instrument.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.logging.Logger;


public class TypeStabilityAgent {
    public static void premain(String agentArgs, Instrumentation inst) throws IOException {
        Config conf = Config.parse(agentArgs);
        NullnessLogger.initialize(conf.logFile);
        inst.addTransformer(new TypeStabilityTransformer(conf));
    }
}

class Config {
    String prefix;
    String logFile;
    String dumpDirectory;

    static Config parse(String args) {
        Config result = new Config();
        if (args == null) {
            return result;
        }

        String[] tokens = args.split(" ");
        if (tokens.length % 2 != 0) {
            throw new IllegalArgumentException("Invalid agent argument string: " + args);
        }
        for (int i = 0; i < tokens.length; i += 2) {
            String value = tokens[i+1];
            switch(tokens[i]) {
                case "-p":
                    result.prefix = value;
                    break;
                case "-l":
                    result.logFile = value;
                    break;
                case "-d":
                    result.dumpDirectory = value;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid agent argument: " + tokens[i]);
            }
        }
        return result;
    }
}

class TypeStabilityTransformer implements ClassFileTransformer {
    private final static Logger LOGGER = Logger.getLogger(TypeStabilityTransformer.class.getName());

    String prefix;
    String dumpDirectory;

    TypeStabilityTransformer(Config conf) {
        this.prefix = conf.prefix;
        this.dumpDirectory = conf.dumpDirectory;
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

        // Write the ClassNode back to bytes. We run the checker *after* this step, because the ClassWriter fixes up
        // stack size and frames, which is necessary for dataflow checks.
        cn.accept(cw);
        byte[] result = cw.toByteArray();

        if (dumpDirectory != null) {
            Path path = Paths.get(dumpDirectory, className + ".class");
            LOGGER.info("Dumping results to " + path.toString() + ".");
            try {
                Files.createDirectories(path.getParent());
                Files.write(path, result);
            } catch (IOException e) {
                LOGGER.severe("Exception while dumping " + className + " to file.");
            }
        }

        LOGGER.info("Validating " + className + ".");
        try {
            CheckClassAdapter checker = new CheckClassAdapter(null);
            cr = new ClassReader(result);
            cn = new ClassNode();
            cr.accept(cn, 0);
            cn.accept(checker);
        } catch (Exception e) {
            LOGGER.severe("Invalid bytecode generated for " + className + ":");
            LOGGER.severe(e.getMessage());
            throw e;
        }
        LOGGER.info("Successfully transformed " + className + ".");

        return result;
    }
}
