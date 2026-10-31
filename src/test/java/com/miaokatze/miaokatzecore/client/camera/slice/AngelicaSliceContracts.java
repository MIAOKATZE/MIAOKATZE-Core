package com.miaokatze.miaokatzecore.client.camera.slice;

import java.io.InputStream;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Checks required slice injection anchors against the actual pinned Angelica bytecode, without class initialization.
 */
public final class AngelicaSliceContracts {

    private static final String TASK = "com/gtnewhorizons/angelica/rendering/celeritas/AngelicaChunkBuilderMeshingTask";
    private static final String MIXIN = "com/miaokatze/miaokatzecore/mixin/camera/slice/mesh/MixinAngelicaSliceTask";

    public static void main(String[] args) throws Exception {
        ClassNode task = read(TASK);
        ClassNode mixin = read(MIXIN);
        int checked = 0;
        for (MethodNode handler : mixin.methods) {
            AnnotationNode injection = annotation(handler, "WrapOperation;");
            if (injection == null) injection = annotation(handler, "ModifyExpressionValue;");
            if (injection == null) continue;
            require(
                !Integer.valueOf(0)
                    .equals(value(injection, "require")),
                "required anchor must remain required");
            String methodName = (String) ((List<?>) value(injection, "method")).get(0);
            MethodNode method = method(task, methodName);
            Object atValue = value(injection, "at");
            AnnotationNode at = atValue instanceof List<?> ? (AnnotationNode) ((List<?>) atValue).get(0)
                : (AnnotationNode) atValue;
            String target = (String) value(at, "target");
            int callIndex = -1;
            int matches = 0;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                String selector = "NEW".equals(value(at, "value")) && call.name.equals("<init>")
                    ? call.desc.substring(0, call.desc.length() - 1) + "L" + call.owner + ";"
                    : "L" + call.owner + ";" + call.name + call.desc;
                if (target.equals(selector)) {
                    matches++;
                    callIndex = method.instructions.indexOf(call);
                }
            }
            require(matches == 1, methodName + " has exactly one required call site: " + target);
            if (methodName.equals("finishDeferred")) {
                for (String local : new String[] { "slice", "x", "y", "z" }) {
                    boolean live = false;
                    for (LocalVariableNode variable : method.localVariables) {
                        if (variable.name.equals(local) && method.instructions.indexOf(variable.start) <= callIndex
                            && callIndex < method.instructions.indexOf(variable.end)) live = true;
                    }
                    require(live, "deferred encoding captures the live per-block " + local);
                }
            }
            checked++;
        }
        require(checked == 4, "boundary, immediate encoding, deferred encoding and shared output stamp are checked");
        for (String caller : new String[] { "execute", "finishDeferred" }) {
            boolean finalizes = false;
            for (AbstractInsnNode instruction : method(task, caller).instructions.toArray()) {
                if (instruction instanceof MethodInsnNode && ((MethodInsnNode) instruction).owner.equals(TASK)
                    && ((MethodInsnNode) instruction).name.equals("finalizeSection")) finalizes = true;
            }
            require(finalizes, caller + " publishes through the stamped finalizer");
        }
        MethodNode encode = method(task, "encodeTessellated");
        require(encode.desc.startsWith("([III[I[I"), "native encoding retains offset and vertex count separately");
        ClassNode deletion = read("com/miaokatze/miaokatzecore/mixin/camera/slice/shader/MixinSliceGlStateManager");
        AnnotationNode mixinTarget = annotation(deletion.invisibleAnnotations, "Mixin;");
        require(
            ((List<?>) value(mixinTarget, "targets")).contains("com.gtnewhorizons.angelica.glsm.GLSMPrograms"),
            "program cleanup targets the actual program owner");
        require(
            method(read("com/gtnewhorizons/angelica/glsm/GLSMPrograms"), "glDeleteProgram").desc.equals("(I)V"),
            "program cleanup target descriptor exists");
        System.out
            .println("Angelica slice contracts passed (required anchors, deferred locals and finalization; no GL).");
    }

    private static ClassNode read(String name) throws Exception {
        try (InputStream input = AngelicaSliceContracts.class.getResourceAsStream("/" + name + ".class")) {
            require(input != null, "missing class " + name);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static MethodNode method(ClassNode owner, String name) {
        for (MethodNode method : owner.methods) {
            if (method.name.equals(name)) return method;
        }
        throw new AssertionError(owner.name + "." + name + " is absent");
    }

    private static AnnotationNode annotation(MethodNode method, String suffix) {
        AnnotationNode found = annotation(method.visibleAnnotations, suffix);
        return found == null ? annotation(method.invisibleAnnotations, suffix) : found;
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String suffix) {
        if (annotations != null) {
            for (AnnotationNode annotation : annotations) {
                if (annotation.desc.endsWith(suffix)) return annotation;
            }
        }
        return null;
    }

    private static Object value(AnnotationNode annotation, String name) {
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (name.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        return null;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
