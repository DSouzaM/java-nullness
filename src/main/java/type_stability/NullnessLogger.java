package type_stability;

import java.io.*;
import java.lang.reflect.Constructor;
import java.util.logging.Logger;


public class NullnessLogger {
    private static final Logger LOGGER = Logger.getLogger(NullnessLogger.class.getName());

    public static NullnessLogger instance = null;

    public static void initialize(Class<? extends NullnessLogger> loggerClass, String outputFile) throws Exception {
        if (instance != null) {
            throw new RuntimeException(NullnessLogger.class.getName() + " was initialized twice.");
        }
        Constructor<? extends NullnessLogger> ctor = loggerClass.getDeclaredConstructor(String.class);
        ctor.setAccessible(true);
        instance = ctor.newInstance(outputFile);
    }

    public static void logPoint(NullnessDataPoint pt) {
        instance.log(pt);
    }

    protected static final char NULL = '0';
    protected static final char NONNULL = '1';
    protected static final char THROW = '2';

    protected final String outputFile;
    protected final Writer outputWriter;

    protected NullnessLogger(String outputFile) throws IOException {
        this.outputFile = outputFile;
        if (outputFile == null) {
            this.outputWriter = new BufferedWriter(new OutputStreamWriter(System.out));
        } else {
            this.outputWriter = new BufferedWriter(new FileWriter(outputFile));
        }
        Runtime.getRuntime().addShutdownHook(getCleanupThread());
    }

    protected void logBitMap(byte length, long bitmap) throws IOException {
        long i = 1 << (length - 1);
        while (i > 0) {
            outputWriter.append((i & bitmap) != 0 ? '1' : '0');
            i >>= 1;
        }
    }

    protected synchronized void log(NullnessDataPoint pt) {
        try {
            outputWriter.append(pt.className);
            outputWriter.append(',');
            logBitMap((byte)pt.fields.length, NullnessDataPoint.nullityBitmap(pt.fields));
            outputWriter.append('\n');
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void finish() {
        try {
            if (outputFile == null) {
                // Don't close stdout!
                outputWriter.flush();
            } else {
                outputWriter.close();
            }
        } catch (IOException e) {
            LOGGER.severe("An exception occurred while closing the NullnessLogger file " + outputFile);
            LOGGER.severe(e.getMessage());
        }
    }

    protected Thread getCleanupThread() {
        return new Thread(this::finish);
    }


}
