package type_stability;

import java.util.logging.Logger;

public class NullnessDataPoint {
    private final static Logger LOGGER = Logger.getLogger(NullnessLogger.class.getName());

    String className;
    Object[] fields; // bitmap

    public static long nullityBitmap(Object[] objs) {
        long res = 0;
        for (Object obj: objs) {
            res = (res << 1) + ((obj == null) ? 0 : 1);
        }
        return res;
    }

    public NullnessDataPoint(String className, Object[] fields) {
        this.className = className;

        if (fields.length > 64) {
            LOGGER.severe("Found an object " + className + " with more than 64 reftype fields. This is unsupported.");
            System.exit(1);
        }
        this.fields = fields;
    }
}
