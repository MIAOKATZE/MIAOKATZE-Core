package com.miaokatze.miaokatzecore.mixin.camera;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

/** Exercises the compiled production handler, substituting only its Minecraft and camera-hook dependencies. */
public final class CameraRenderBoundaryContracts {

    private static final String MIXIN = "com/miaokatze/miaokatzecore/mixin/camera/MixinEntityRenderer";
    private static final String MINECRAFT = "net/minecraft/client/Minecraft";
    private static final String RENDERER = "net/minecraft/client/renderer/EntityRenderer";
    private static final String HOOKS = "com/miaokatze/miaokatzecore/client/camera/WorldCameraHooks";
    private static final Object CLIENT = new Object();
    private static final List<String> EVENTS = new ArrayList<>();
    private static String failingStage;
    private static Throwable failure;
    private static Throwable cleanupFailure;

    public static void main(String[] args) throws Exception {
        ClassNode mixin = read(MIXIN);
        MethodNode boundary = boundary(mixin);
        injectionShape(mixin, boundary);
        Method handler = executableBoundary(boundary);
        exercise(handler, null, null, null);
        for (String stage : Arrays.asList("begin", "render", "end")) {
            exercise(handler, stage, new IllegalStateException(stage), null);
            exercise(handler, stage, new AssertionError(stage), null);
        }
        exercise(handler, "render", new IllegalStateException("render"), new AssertionError("cleanup"));
        System.out.println("Camera render boundary contracts passed (no Minecraft initialization or OpenGL).");
    }

    private static MethodNode boundary(ClassNode mixin) {
        for (MethodNode method : mixin.methods) {
            if (method.name.equals("core$renderWorld")) return method;
        }
        throw new AssertionError("missing render boundary");
    }

    private static void injectionShape(ClassNode mixin, MethodNode boundary) {
        AnnotationNode wrap = annotation(boundary, "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;");
        require(wrap != null, "render scope wraps a call, preserving the renderWorld body for transformers");
        require(
            Arrays.asList("updateCameraAndRender(F)V")
                .equals(value(wrap, "method")),
            "scope belongs to the world-render caller");
        List<?> injectionPoints = (List<?>) value(wrap, "at");
        require(injectionPoints.size() == 1, "one invocation target covers the render boundary");
        AnnotationNode at = (AnnotationNode) injectionPoints.get(0);
        require("INVOKE".equals(value(at, "value")), "wrap only the invocation");
        require(
            ("L" + RENDERER + ";renderWorld(FJ)V").equals(value(at, "target")),
            "wrap both frame-limit branches without an ordinal restriction");
        require(value(at, "ordinal") == null, "both world-render invocations are covered");
        require(value(wrap, "slice") == null, "the world-render branches share the same scope");
        require(
            ("(L" + RENDERER + ";FJLcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)V")
                .equals(boundary.desc),
            "operation receives the renderer and the complete render arguments");
        for (MethodNode method : mixin.methods) {
            require(
                annotation(method, "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;") == null,
                "camera mixin must not move the renderWorld body into a generated method");
            require(
                annotation(method, "Lorg/spongepowered/asm/mixin/Overwrite;") == null,
                "camera mixin must preserve the renderWorld body");
        }
    }

    private static void exercise(Method handler, String stage, Throwable thrown, Throwable cleanup) throws Exception {
        EVENTS.clear();
        failingStage = stage;
        failure = thrown;
        cleanupFailure = cleanup;
        Object renderer = new Object();
        float partialTicks = .375F;
        long time = 9876543212345L;
        Operation<Void> original = arguments -> {
            require(arguments.length == 3, "forward the operation receiver and both arguments");
            require(arguments[0] == renderer, "preserve the actual renderer receiver");
            require(
                Float.valueOf(partialTicks)
                    .equals(arguments[1]),
                "preserve partial ticks");
            require(
                Long.valueOf(time)
                    .equals(arguments[2]),
                "preserve the render deadline");
            event("render");
            return null;
        };
        Throwable actual = null;
        try {
            handler.invoke(null, new Object(), renderer, partialTicks, time, original);
        } catch (InvocationTargetException invocation) {
            actual = invocation.getCause();
        }
        require(actual == (cleanup != null ? cleanup : thrown), "propagate the original exception identity");
        List<String> expected;
        if (stage == null) expected = Arrays.asList("begin", "render", "end", "complete");
        else if (stage.equals("begin")) expected = Arrays.asList("begin", "abort", "end");
        else if (stage.equals("end")) expected = Arrays.asList("begin", "render", "end", "abort");
        else if (cleanup != null) expected = Arrays.asList("begin", "render", "abort", "end", "abort");
        else expected = Arrays.asList("begin", "render", "abort", "end");
        require(EVENTS.equals(expected), "restore on every path; publish only after success: " + EVENTS);
    }

    private static Method executableBoundary(MethodNode method) throws Exception {
        // Keep the production control flow, exception table, and Operation.call untouched. A leading unused
        // Object replaces the instance's local slot; object stand-ins avoid loading Minecraft or an OpenGL class.
        method.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        method.desc = "(Ljava/lang/Object;" + standIns(method.desc.substring(1));
        method.signature = null;
        method.visibleAnnotations = null;
        method.invisibleAnnotations = null;
        method.localVariables = null;
        String fixture = CameraRenderBoundaryContracts.class.getName()
            .replace('.', '/');
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction
            != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.owner.equals(MINECRAFT) || call.owner.equals(HOOKS)) {
                call.owner = fixture;
                call.desc = standIns(call.desc);
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        String generated = MIXIN + "BoundaryContract";
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, generated, null, "java/lang/Object", null);
        method.accept(writer);
        writer.visitEnd();
        byte[] bytes = writer.toByteArray();
        Class<?> type = new ClassLoader(CameraRenderBoundaryContracts.class.getClassLoader()) {

            Class<?> define() {
                return defineClass(generated.replace('/', '.'), bytes, 0, bytes.length);
            }
        }.define();
        return type.getMethod(method.name, Object.class, Object.class, float.class, long.class, Operation.class);
    }

    private static String standIns(String descriptor) {
        return descriptor.replace("L" + MINECRAFT + ";", "Ljava/lang/Object;")
            .replace("L" + RENDERER + ";", "Ljava/lang/Object;");
    }

    private static ClassNode read(String name) throws IOException {
        try (InputStream input = CameraRenderBoundaryContracts.class.getClassLoader()
            .getResourceAsStream(name + ".class")) {
            require(input != null, "compiled production mixin is available");
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static AnnotationNode annotation(MethodNode method, String descriptor) {
        if (method.visibleAnnotations != null) {
            for (AnnotationNode node : method.visibleAnnotations) if (node.desc.equals(descriptor)) return node;
        }
        if (method.invisibleAnnotations != null) {
            for (AnnotationNode node : method.invisibleAnnotations) if (node.desc.equals(descriptor)) return node;
        }
        return null;
    }

    private static Object value(AnnotationNode annotation, String name) {
        if (annotation.values == null) return null;
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i)
                .equals(name)) return annotation.values.get(i + 1);
        }
        return null;
    }

    public static Object getMinecraft() {
        return CLIENT;
    }

    public static void beginWorldRender(Object client, float partialTicks) {
        require(client == CLIENT && partialTicks == .375F, "pass client and partial ticks to begin");
        event("begin");
    }

    public static void abortWorldRender() {
        event("abort");
    }

    public static void endWorldRender(Object client) {
        require(client == CLIENT, "restore using the same client");
        event("end");
        if (cleanupFailure != null) rethrow(cleanupFailure);
    }

    public static void completeWorldRender(Object client) {
        require(client == CLIENT, "publish using the same client");
        event("complete");
    }

    private static void event(String stage) {
        EVENTS.add(stage);
        if (stage.equals(failingStage)) rethrow(failure);
    }

    private static void rethrow(Throwable thrown) {
        if (thrown instanceof RuntimeException) throw (RuntimeException) thrown;
        throw (Error) thrown;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
