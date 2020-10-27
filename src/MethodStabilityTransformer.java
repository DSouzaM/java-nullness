import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.commons.*;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.stream.Collectors;

public class MethodStabilityTransformer {
    public ClassNode transformClass(ClassNode cn) {
        System.out.println("Transforming " + cn.name);
        cn.methods = cn.methods.stream()
                .map(mn -> transformMethod(mn, cn.name))
                .collect(Collectors.toList());
        return cn;
    }

    public MethodNode transformMethod(MethodNode mn, String className) {
        if (!isSuitableMethod(mn)) {
            return mn;
        }

        // Create a local variable to store parameter data until method exit
        LocalVariablesSorter lvs = new LocalVariablesSorter(mn.access, mn.desc, null);
        Type varType = Type.getType(NullnessDataPoint.class);
        int dataPointVarIndex = lvs.newLocal(varType);
        LabelNode scopeStart = new LabelNode();
        LabelNode scopeEnd = new LabelNode();
        String DATAPOINT_NAME = "NULLNESS_DATAPOINT";
        mn.localVariables.add(new LocalVariableNode(
                DATAPOINT_NAME, varType.getDescriptor(), null,
                scopeStart, scopeEnd, dataPointVarIndex
        ));
        mn.instructions.insert(scopeStart);
        mn.instructions.add(scopeEnd);

        // Store parameters' nullness information in local variable
        InsnList prologue = generatePrologue(className, mn.name, Type.getMethodType(mn.desc), dataPointVarIndex);
        mn.instructions.insert(scopeStart, prologue);

        // Update each exit point to log results
        mn.instructions.forEach((node) -> {
            if (node.getOpcode() == Opcodes.ARETURN) {
                mn.instructions.insertBefore(node, generateReturnEpilogue(dataPointVarIndex));
            } else if (node.getOpcode() == Opcodes.ATHROW) {
                mn.instructions.insertBefore(node, generateThrowEpilogue(dataPointVarIndex));
            }
        });

        mn.accept(lvs);

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

    // Create a NullnessDataPoint and store it in the variable at index dataPointVarIndex
    InsnList generatePrologue(String className, String methodName, Type methodType, int dataPointVarIndex) {
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
        int refTypeArrayIdx = 0;
        int localVarIdx = 0;
        for (Type t : argumentTypes) {
            localVarIdx += t.getSize(); // longs and doubles consume two "slots" in the local variables
            if (!isNullable(t)) continue;

            prologue.add(new InsnNode(Opcodes.DUP));
            prologue.add(new LdcInsnNode(refTypeArrayIdx));
            prologue.add(new VarInsnNode(Opcodes.ALOAD, localVarIdx));
            prologue.add(new InsnNode(Opcodes.AASTORE));

            refTypeArrayIdx++;
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
                Opcodes.INVOKEVIRTUAL,
                Type.getInternalName(NullnessDataPoint.class),
                "completeReturn",
                Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Object.class)),
                false
        )); // rv
        return epilogue;
    }

    InsnList generateThrowEpilogue(int dataPointVarIndex) {
        InsnList epilogue = new InsnList();
        epilogue.add(new InsnNode(Opcodes.DUP)); // exn -> exn
        epilogue.add(new VarInsnNode(Opcodes.ALOAD, dataPointVarIndex)); // exn -> exn -> datapoint
        epilogue.add(new InsnNode(Opcodes.SWAP)); // exn -> datapoint -> exn
        epilogue.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                Type.getInternalName(NullnessDataPoint.class),
                "completeThrow",
                Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Exception.class)),
                false
        )); // exn
        return epilogue;
    }

}
