package type_stability;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.IOException;
import java.lang.instrument.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;


public class TypeStabilityAgent {
    public static void premain(String agentArgs, Instrumentation inst) throws Exception {
        Config conf = Config.parse(agentArgs);
        // Initialize whichever kind of logger it is.
        NullnessLogger.initialize(conf.loggerClass, conf.logFile);
        inst.addTransformer(new TypeStabilityTransformer(conf));
    }
}

class Config {
    String prefix;
    String logFile;
    String dumpDirectory;
    Class<? extends NullnessLogger> loggerClass;

    static void setLogLevel(Level level) {
        Logger rootLogger = LogManager.getLogManager().getLogger("");
        rootLogger.setLevel(level);
        for (Handler h : rootLogger.getHandlers()) {
            h.setLevel(level);
        }
    }

    static Config parse(String args) {
        Config result = new Config();
        result.loggerClass = NullnessLogger.class;
        setLogLevel(Level.WARNING);

        if (args == null || args.isEmpty()) {
            throw new IllegalArgumentException("Agent arguments cannot be empty. Usage: -p packagePrefix [-l logFile] [-d dumpDirectory] [--aggregate]");
        }

        String[] tokens = args.split(" ");
        for (int i = 0; i < tokens.length; i += 1) {
            switch(tokens[i]) {
                case "-p":
                    result.prefix = tokens[++i];
                    break;
                case "-l":
                    result.logFile = tokens[++i];
                    break;
                case "-d":
                    result.dumpDirectory = tokens[++i];
                    break;
                case "-v":
                    setLogLevel(Level.INFO);
                    break;
                case "--aggregate":
                    result.loggerClass = NullnessAggregateLogger.class;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid agent argument: " + tokens[i]);
            }
        }
        if (result.prefix == null) {
            throw new IllegalArgumentException("Package prefix required in agent arguments.");
        }
        return result;
    }
}

class TypeStabilityTransformer implements ClassFileTransformer {
    private final static Logger LOGGER = Logger.getLogger(TypeStabilityTransformer.class.getName());

    String prefix;
    Class<? extends NullnessLogger> loggerClass;
    String dumpDirectory;

    TypeStabilityTransformer(Config conf) {
        this.prefix = conf.prefix;
        this.loggerClass = conf.loggerClass;
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

        // TODO: It looks like COMPUTE_FRAMES is necessary for Java 1.7 and onward, because the JVM expects stack frame
        //  maps. However, somewhere in the call stack this causes asm to load classes, which it sometimes fails to do.
        //  Apparently you can override ClassWriter.getCommonSuperClass for this scenario but it's unclear what is
        //  causing only certain classes to fail (i.e., it's not just classes that are currently being loaded that fail).
        //  link: https://gitlab.ow2.org/asm/asm/-/issues/317918
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);

        // Transform the methods of this class
        byte[] result;
        MethodStabilityTransformer<? extends NullnessLogger> m = new MethodStabilityTransformer<>(loggerClass);
        try {
            cn = m.transformClass(cn);

            // Write the ClassNode back to bytes. We run the checker *after* this step, because the ClassWriter fixes up
            // stack size and frames, which is necessary for dataflow checks.
            cn.accept(cw);
            result = cw.toByteArray();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception occurred while transforming " + cn.name + ":", e);
            throw e;
        }

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
