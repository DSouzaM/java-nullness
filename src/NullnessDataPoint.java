
public class NullnessDataPoint {
    String className;
    String methodName;
    byte numParameters;
    long parameters; // bitmap


    public NullnessDataPoint(String className, String methodName, Object[] parameters) {
        System.out.println("NullnessDataPoint constructor called with " + parameters.length + " parameters");
        this.className = className;
        this.methodName = methodName;

        if (parameters.length > 64) {
            System.err.println("Found a method " + className + "::" + methodName +
                    " with more than 64 reftype parameters. This is unsupported.");
        }
        this.numParameters = (byte) parameters.length;
        this.parameters = 0;
        for (Object parameter: parameters) {
            this.parameters <<= 1;
            this.parameters += (parameter == null) ? 0 : 1;
        }
    }

    public void completeReturn(Object result) {
        String res = result == null ? "null" : "non-null";
        System.out.println(className + "::" + methodName + " with bitmap " + parameters + " returned " + res);
    }

    public void completeThrow(Exception e) {
        System.out.println(className + "::" + methodName + " with bitmap " + parameters + " threw exception " + e.getClass().getName());
    }
}