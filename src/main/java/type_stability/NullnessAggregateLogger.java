package type_stability;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public class NullnessAggregateLogger extends NullnessLogger {
    private static final Logger LOGGER = Logger.getLogger(NullnessAggregateLogger.class.getName());

    // counts[n_params].get(bitmap)[outcome], where
    //   n_params: number of nullable parameters [0, 64)
    //   bitmap: long representing nullity of parameters (as bitmap)
    //   outcome: 0, 1, 2 (return null, return nonnull, throw)
    private class Entry {
        final byte numFields;
        final long fields; // bitmap
        final byte numParameters;
        final long parameters; // bitmap
        final char result;

        Entry(byte numFields, long fields, byte numParameters, long parameters, char result) {
            this.numFields = numFields;
            this.fields = fields;
            this.numParameters = numParameters;
            this.parameters = parameters;
            this.result = result;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Entry entry = (Entry) o;
            return numFields == entry.numFields &&
                    fields == entry.fields &&
                    numParameters == entry.numParameters &&
                    parameters == entry.parameters &&
                    result == entry.result;
        }

        @Override
        public int hashCode() {
            return Objects.hash(numFields, fields, numParameters, parameters, result);
        }
    }

    private final HashMap<Entry, Integer> counts;

    protected NullnessAggregateLogger(String outputFile) throws IOException {
        super(outputFile);
        counts = new HashMap<>();
        outputWriter.append("fields,params,result,count\n");
    }

    @Override
    protected synchronized void log(NullnessDataPoint pt, char result) {
        Entry e = new Entry(pt.numFields, pt.fields, pt.numParameters, pt.parameters, result);
        int value = counts.getOrDefault(e, 0);
        counts.put(e, value+1);
    }

    @Override
    protected synchronized void finish() {
        try {
            for (Map.Entry<Entry, Integer> pair: counts.entrySet()) {
                Entry e = pair.getKey();
                logBitMap(e.numFields, e.fields);
                outputWriter.append(',');
                logBitMap(e.numParameters, e.parameters);
                outputWriter.append(',');
                outputWriter.append(e.result);
                outputWriter.append(',');
                outputWriter.append(pair.getValue().toString());
                outputWriter.append('\n');
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
