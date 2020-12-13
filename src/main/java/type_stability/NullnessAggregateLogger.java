package type_stability;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class NullnessAggregateLogger extends NullnessLogger {
    private static final Logger LOGGER = Logger.getLogger(NullnessAggregateLogger.class.getName());

    // number of times a class' fields were logged
    private final HashMap<String, Integer> countPerClass;
    // field descriptors for each class
    private final HashMap<String, String[]> fieldTypesPerClass;
    // overall counts of non-null fields per class
    private final HashMap<String, int[]> fieldCounts;

    protected NullnessAggregateLogger(String outputFile) throws IOException {
        super(outputFile);
        fieldCounts = new HashMap<>();
        fieldTypesPerClass = new HashMap<>();
        countPerClass = new HashMap<>();
        outputWriter.append("class,field,nonnull_count,total_count,ratio\n");
    }

    @Override
    protected synchronized void log(NullnessDataPoint pt) {
        // Increment # times this class was logged
        int timesLogged = countPerClass.getOrDefault(pt.className, 0);
        countPerClass.put(pt.className, timesLogged + 1);
        fieldTypesPerClass.putIfAbsent(pt.className, pt.fieldTypes);
        // Increment per-field nullness
        int[] counts = fieldCounts.computeIfAbsent(pt.className, k -> new int[pt.fields.length]);
        for (int i = 0; i < pt.fields.length; i++) {
            if (pt.fields[i] != null) {
                counts[i]++;
            }
        }
    }

    @Override
    protected synchronized void finish() {
        try {
            for (String className : countPerClass.keySet()) {
                int timesLogged = countPerClass.get(className);
                String[] fieldTypes = fieldTypesPerClass.get(className);
                int[] counts = fieldCounts.get(className);
                for (int i = 0; i < counts.length; i++) {
                    outputWriter.append(className); // class name
                    outputWriter.append(',');
                    outputWriter.append(fieldTypes[i]); // field descriptor
                    outputWriter.append(',');
                    outputWriter.append(Integer.toString(counts[i])); // numerator
                    outputWriter.append(',');
                    outputWriter.append(Integer.toString(timesLogged)); // denominator
                    outputWriter.append(',');
                    outputWriter.append(String.format("%.2f", (float) counts[i]/timesLogged)); // ratio
                    outputWriter.append('\n');
                }
            }
            outputWriter.flush();

            if (outputFile != null) {
                // Don't close stdout!
                outputWriter.close();
            }
        } catch (IOException e) {
            LOGGER.severe("An exception occurred while closing the NullnessAggregateLogger file " + outputFile);
            LOGGER.severe(e.getMessage());
        }
    }
}
