package type_stability;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;


public class NullnessLogger {
    public static NullnessLogger instance = null;
    private static String DEFAULT_LOGFILE = "nullness.csv";

    public static void initialize(String outputFile) throws IOException {
        if (instance != null) {
            throw new RuntimeException(NullnessLogger.class.getName() + " was initialized twice.");
        }
        instance = new NullnessLogger(outputFile == null? DEFAULT_LOGFILE : outputFile);
    }

    public static void logReturn(NullnessDataPoint pt, Object result) {
        instance.log(pt, result == null ? NULL : NONNULL);
    }

    public static void logThrow(NullnessDataPoint pt) {
        System.out.println("something thrown");
        instance.log(pt, THROW);
    }


    private static final char NULL = '0';
    private static final char NONNULL = '1';
    private static final char THROW = '2';

    private final String outputFile;
    private final Writer outputWriter;

    private NullnessLogger(String outputFile) throws IOException {
        this.outputFile = outputFile;
        this.outputWriter = new BufferedWriter(new FileWriter(outputFile));

        Runtime.getRuntime().addShutdownHook(getCleanupThread());
    }

    private void log(NullnessDataPoint pt, char result) {
        try {
            outputWriter.append(pt.className);
            outputWriter.append(',');
            outputWriter.append(pt.methodName);
            outputWriter.append(',');
            outputWriter.append(Integer.toString(pt.numParameters));
            outputWriter.append(',');
            outputWriter.append(Long.toBinaryString(pt.parameters));
            outputWriter.append(',');
            outputWriter.append(result);
            outputWriter.append('\n');
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Thread getCleanupThread() {
        return new Thread(() -> {
            try {
                outputWriter.close();
            } catch (IOException e) {
                System.err.println("An exception occurred while closing the NullnessLogger file " + outputFile);
                e.printStackTrace(System.err);
            }
        });
    }


}
