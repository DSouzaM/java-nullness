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

        // Log field nullity
        InsnList prologue = generatePrologue(cn, mn);
        mn.instructions.insert(prologue);

        return mn;
    }

    boolean isNullable(Type t) {
        return t.getSort() == Type.ARRAY || t.getSort() == Type.OBJECT;
    }

    boolean isNullable(String desc) {
        return desc.startsWith("L") || desc.startsWith("[");
    }

    boolean isSuitableMethod(MethodNode mn) {
        // Fields are obviously null in the ctor; static methods have no fields
        return !mn.name.equals("<init>") && ((mn.access & Opcodes.ACC_STATIC) == 0);
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
    InsnList generatePrologue(ClassNode cn, MethodNode mn) {
        Constructor<?> ctor = NullnessDataPoint.class.getConstructors()[0]; // Assumption: only one constructor
        Type dataPointType = Type.getType(NullnessDataPoint.class);
        InsnList prologue = new InsnList();

        // Allocate object
        prologue.add(new TypeInsnNode(Opcodes.NEW, dataPointType.getInternalName())); // pt

        // Set up constructor call
        prologue.add(new InsnNode(Opcodes.DUP)); // pt -> pt
        prologue.add(new LdcInsnNode(cn.name)); // pt -> pt -> name

        // generate field types and field values arrays
        ArrayList<String> fieldTypes = new ArrayList<>();
        ArrayList<InsnList> readFields = new ArrayList<>();
        if ((mn.access & Opcodes.ACC_STATIC) == 0) {
            for (FieldNode fn : cn.fields) {
                if ((fn.access & Opcodes.ACC_STATIC) == 0 && isNullable(fn.desc)) {
                    fieldTypes.add(fn.desc);
                    InsnList list = new InsnList();
                    list.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    list.add(new FieldInsnNode(Opcodes.GETFIELD, cn.name, fn.name, fn.desc));
                    readFields.add(list);
                }
            }
        }

        InsnList createFieldTypeArray = new InsnList();
        createFieldTypeArray.add(new LdcInsnNode(fieldTypes.size()));
        createFieldTypeArray.add(new TypeInsnNode(Opcodes.ANEWARRAY, Type.getInternalName(String.class)));
        for (int i = 0; i < fieldTypes.size(); i++) {
            createFieldTypeArray.add(new InsnNode(Opcodes.DUP));
            createFieldTypeArray.add(new LdcInsnNode(i));
            createFieldTypeArray.add(new LdcInsnNode(fieldTypes.get(i)));
            createFieldTypeArray.add(new InsnNode(Opcodes.AASTORE));
        }
        prologue.add(createFieldTypeArray); // pt -> pt -> name -> fieldTypes
        prologue.add(generateObjectArray(readFields)); // pt -> pt -> name -> fieldTypes -> fields

        // Constructor call
        prologue.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                Type.getInternalName(NullnessDataPoint.class),
                "<init>",
                Type.getConstructorDescriptor(ctor)
        )); // pt

        // Call log function
        prologue.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(loggerClass),
                "logPoint",
                Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(NullnessDataPoint.class)),
                false
        ));

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
