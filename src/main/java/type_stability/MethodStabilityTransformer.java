package type_stability;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MethodStabilityTransformer<T extends NullnessLogger> {
    private final static Logger LOGGER = Logger.getLogger(MethodStabilityTransformer.class.getName());

    private final Class<T> loggerClass;

    public MethodStabilityTransformer(Class<T> loggerClass) {
        this.loggerClass = loggerClass;
    }

    public ClassNode transformClass(ClassNode cn) {
        LOGGER.info("Transforming " + cn.name);
        cn.methods = cn.methods.stream()
                .map(mn -> transformMethod(mn, cn.name))
                .collect(Collectors.toList());
        return cn;
    }

    public MethodNode transformMethod(MethodNode mn, String className) {
        if (!isSuitableMethod(mn)) {
            return mn;
        }
        LOGGER.info("Transforming " + mn.name + " with descriptor " + mn.desc + ".");
        boolean isStatic = (mn.access & Opcodes.ACC_STATIC) != 0;

        // Create a local variable to store parameter data until method exit
        // note: This is hacky. Normally you'd use a LocalVariablesSorter, but I don't think it fits the Tree API very well.
        Type varType = Type.getType(NullnessDataPoint.class);
        int dataPointVarIndex = mn.maxLocals;
        mn.maxLocals += varType.getSize();

        // Store parameters' nullness information in local variable
        InsnList prologue = generatePrologue(className, mn.name, Type.getMethodType(mn.desc), isStatic, dataPointVarIndex);
        mn.instructions.insert(prologue);

        // Update each exit point to log results
        mn.instructions.forEach((node) -> {
            if (node.getOpcode() == Opcodes.ARETURN) {
                mn.instructions.insertBefore(node, generateReturnEpilogue(dataPointVarIndex));
            } else if (node.getOpcode() == Opcodes.ATHROW) {
                mn.instructions.insertBefore(node, generateThrowEpilogue(dataPointVarIndex));
            }
        });

        return mn;
    }

    boolean isNullable(Type t) {
        return t.getSort() == Type.ARRAY || t.getSort() == Type.OBJECT;
    }

    boolean isSuitableMethod(MethodNode mn) {
        // Can't analyze null-stability of a constructor
        if (mn.name.equals("<init>")) {
            return false;
        }
        Type methodType = Type.getMethodType(mn.desc);
        // Can't analyze null-stability with primitive/void return type
        if (!isNullable(methodType.getReturnType())) {
            return false;
        }
        // Can't analyze null-stability with no reference-type parameters
        return Arrays.stream(methodType.getArgumentTypes()).anyMatch(this::isNullable);
    }

    // Create a type_stability.NullnessDataPoint and store it in the variable at index dataPointVarIndex
    InsnList generatePrologue(String className, String methodName, Type methodType, boolean isStatic, int dataPointVarIndex) {
        Constructor<?> ctor = NullnessDataPoint.class.getConstructors()[0]; // Assumption: only one constructor
        Type dataPointType = Type.getType(NullnessDataPoint.class);
        Type[] argumentTypes = methodType.getArgumentTypes();
        int numRefTypeParameters = (int) Arrays.stream(argumentTypes).filter(this::isNullable).count();

        InsnList prologue = new InsnList();

        prologue.add(new TypeInsnNode(Opcodes.NEW, dataPointType.getInternalName()));   // allocate object

        // Set up constructor call
        prologue.add(new InsnNode(Opcodes.DUP)); // push object ref
        prologue.add(new LdcInsnNode(className)); // push className
        prologue.add(new LdcInsnNode(methodName)); // push methodName
        // push array
        //  step 1: allocate array
        prologue.add(new LdcInsnNode(numRefTypeParameters));
        prologue.add(new TypeInsnNode(Opcodes.ANEWARRAY, Type.getInternalName(Object.class)));
        //  step 2: populate array with all of the ref-type parameters
        int localVarIdx = isStatic ? 0 : Type.getType(Object.class).getSize();
        int refTypeArrayIdx = 0;
        for (Type t : argumentTypes) {
            if (isNullable(t)) {
                prologue.add(new InsnNode(Opcodes.DUP));
                prologue.add(new LdcInsnNode(refTypeArrayIdx));
                prologue.add(new VarInsnNode(Opcodes.ALOAD, localVarIdx));
                prologue.add(new InsnNode(Opcodes.AASTORE));
                refTypeArrayIdx++;
            }
            localVarIdx += t.getSize(); // longs and doubles consume two "slots" in the local variables
        }

        // Constructor call
        prologue.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                Type.getInternalName(NullnessDataPoint.class),
                "<init>",
                Type.getConstructorDescriptor(ctor)
        ));
        // Write result to dataPoint variable
        prologue.add(new VarInsnNode(Opcodes.ASTORE, dataPointVarIndex));

        return prologue;
    }

    InsnList generateReturnEpilogue(int dataPointVarIndex) {
        InsnList epilogue = new InsnList();
        epilogue.add(new InsnNode(Opcodes.DUP)); // rv -> rv
        epilogue.add(new VarInsnNode(Opcodes.ALOAD, dataPointVarIndex)); // rv -> rv -> datapoint
        epilogue.add(new InsnNode(Opcodes.SWAP)); // rv -> datapoint -> rv
        epilogue.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(loggerClass),
                "logReturn",
                Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(NullnessDataPoint.class), Type.getType(Object.class)),
                false
        )); // rv
        return epilogue;
    }

    InsnList generateThrowEpilogue(int dataPointVarIndex) {
        InsnList epilogue = new InsnList();
        epilogue.add(new VarInsnNode(Opcodes.ALOAD, dataPointVarIndex)); // exn -> datapoint
        epilogue.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(loggerClass),
                "logThrow",
                Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(NullnessDataPoint.class)),
                false
        )); // exn
        return epilogue;
    }

}
