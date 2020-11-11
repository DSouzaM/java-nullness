package type_stability;

import java.util.logging.Logger;

public class NullnessDataPoint {
    private final static Logger LOGGER = Logger.getLogger(NullnessLogger.class.getName());

    String className;
    String methodName;
    byte numParameters;
    long parameters; // bitmap


    public NullnessDataPoint(String className, String methodName, Object[] parameters) {
        this.className = className;
        this.methodName = methodName;

        if (parameters.length > 64) {
            LOGGER.severe("Found a method " + className + "::" + methodName +
                    " with more than 64 reftype parameters. This is unsupported.");
            System.exit(1);
        }
        this.numParameters = (byte) parameters.length;
        this.parameters = 0;
        for (Object parameter: parameters) {
            this.parameters <<= 1;
            this.parameters += (parameter == null) ? 0 : 1;
        }
    }
}
