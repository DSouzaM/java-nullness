package type_stability;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

public class NullnessAggregateLogger extends NullnessLogger {
    private static final Logger LOGGER = Logger.getLogger(NullnessAggregateLogger.class.getName());

    // counts[n_params].get(bitmap)[outcome], where
    //   n_params: number of nullable parameters [0, 64)
    //   bitmap: long representing nullity of parameters (as bitmap)
    //   outcome: 0, 1, 2 (return null, return nonnull, throw)
    private final ArrayList<HashMap<Long, int[]>> counts;
    private static final int MAX_PARAMS = 64;

    protected NullnessAggregateLogger(String outputFile) throws IOException {
        super(outputFile);
        counts = new ArrayList<>(MAX_PARAMS);
        for (int i = 0; i < MAX_PARAMS; i++){
            counts.add(new HashMap<>());
        }
    }

    @Override
    protected synchronized void log(NullnessDataPoint pt, char result) {
        HashMap<Long, int[]> map = counts.get(pt.numParameters);
        // May be necessary to refine this lock (e.g. using double-checked locking)
        int[] resultArray = map.computeIfAbsent(pt.parameters, k -> new int[3]);
        resultArray[result - '0']++;
    }

    @Override
    protected void finish() {
        try {
            for (int numParameters = 0; numParameters < MAX_PARAMS; numParameters++) {
                HashMap<Long, int[]> subcounts = counts.get(numParameters);
                for (Long key : subcounts.keySet()) {
                    int[] events = subcounts.get(key);
                    for (int i = 0; i < events.length; i++) {
                        outputWriter.append(Integer.toString(numParameters));
                        outputWriter.append(',');
                        outputWriter.append(Long.toBinaryString(key));
                        outputWriter.append(',');
                        outputWriter.append(Integer.toString(i));
                        outputWriter.append(',');
                        outputWriter.append(Integer.toString(events[i]));
                        outputWriter.append('\n');
                    }
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
