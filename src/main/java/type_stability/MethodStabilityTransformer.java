package type_stability;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
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
                .map(mn -> transformMethod(cn, mn))
                .collect(Collectors.toList());
        return cn;
    }

    public MethodNode transformMethod(ClassNode cn, MethodNode mn) {
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
        InsnList prologue = generatePrologue(cn, mn, Type.getMethodType(mn.desc), isStatic, dataPointVarIndex);
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

    boolean isNullable(String desc) {
        return desc.startsWith("L") || desc.startsWith("[");
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

    InsnList generateObjectArray(ArrayList<InsnList> values) {
        InsnList result = new InsnList();
        // step 1: allocate array
        result.add(new LdcInsnNode(values.size()));
        result.add(new TypeInsnNode(Opcodes.ANEWARRAY, Type.getInternalName(Object.class)));
        // step 2: populate array
        for (int i = 0; i < values.size(); i++) {
            result.add(new InsnNode(Opcodes.DUP));
            result.add(new LdcInsnNode(i));
            result.add(values.get(i));
            result.add(new InsnNode(Opcodes.AASTORE));
        }
        return result;
    }

    // Create a type_stability.NullnessDataPoint and store it in the variable at index dataPointVarIndex
    InsnList generatePrologue(ClassNode cn, MethodNode mn, Type methodType, boolean isStatic, int dataPointVarIndex) {
        Constructor<?> ctor = NullnessDataPoint.class.getConstructors()[0]; // Assumption: only one constructor
        Type dataPointType = Type.getType(NullnessDataPoint.class);
        Type[] argumentTypes = methodType.getArgumentTypes();
        InsnList prologue = new InsnList();

        // Allocate object
        prologue.add(new TypeInsnNode(Opcodes.NEW, dataPointType.getInternalName()));

        // Set up constructor call
        prologue.add(new InsnNode(Opcodes.DUP)); // push object ref
        prologue.add(new LdcInsnNode(cn.name)); // push className
        prologue.add(new LdcInsnNode(mn.name)); // push methodName

        // push fields array
        ArrayList<InsnList> readFields = new ArrayList<>();
        if ((mn.access & Opcodes.ACC_STATIC) == 0) {
            for (FieldNode fn : cn.fields) {
                if ((fn.access & Opcodes.ACC_STATIC) == 0 && isNullable(fn.desc)) {
                    InsnList list = new InsnList();
                    list.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    list.add(new FieldInsnNode(Opcodes.GETFIELD, cn.name, fn.name, fn.desc));
                    readFields.add(list);
                }
            }
        }
        prologue.add(generateObjectArray(readFields));


        // push parameter array
        ArrayList<InsnList> readParameters = new ArrayList<>();
        int localVarIdx = isStatic ? 0 : Type.getType(Object.class).getSize();
        for (Type t : argumentTypes) {
            if (isNullable(t)) {
                InsnList list = new InsnList();
                list.add(new VarInsnNode(Opcodes.ALOAD, localVarIdx));
                readParameters.add(list);
            }
            localVarIdx += t.getSize(); // longs and doubles consume two "slots" in the local variables
        }
        prologue.add(generateObjectArray(readParameters));

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
