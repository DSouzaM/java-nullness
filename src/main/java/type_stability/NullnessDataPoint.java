package type_stability;

import java.util.logging.Logger;

public class NullnessDataPoint {
    private final static Logger LOGGER = Logger.getLogger(NullnessLogger.class.getName());

    String className;
    String methodName;
    byte numFields;
    long fields; // bitmap
    byte numParameters;
    long parameters; // bitmap

    private static long nullityBitmap(Object[] objs) {
        long res = 0;
        for (Object obj: objs) {
            res = (res << 1) + ((obj == null) ? 0 : 1);
        }
        return res;
    }

    public NullnessDataPoint(String className, String methodName, Object[] fields, Object[] parameters) {
        this.className = className;
        this.methodName = methodName;

        if (fields.length > 64) {
            LOGGER.severe("Found an object " + className + " with more than 64 reftype fields. This is unsupported.");
            System.exit(1);
        }
        this.numFields = (byte) fields.length;
        this.fields = nullityBitmap(fields);

        if (parameters.length > 64) {
            LOGGER.severe("Found a method " + className + "::" + methodName +
                    " with more than 64 reftype parameters. This is unsupported.");
            System.exit(1);
        }
        this.numParameters = (byte) parameters.length;
        this.parameters = nullityBitmap(parameters);
    }
}
