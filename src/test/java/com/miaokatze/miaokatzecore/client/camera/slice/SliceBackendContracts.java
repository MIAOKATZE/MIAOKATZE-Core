// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.client.camera.slice;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Executes real Core classes in isolation, with only backend selection and Iris status stubbed.
 * This checks source/lifecycle behavior without a game, GPU, shader compiler or native renderer.
 * An optional argument selects a packaged Core JAR, so old and fixed artifacts use identical checks.
 */
public final class SliceBackendContracts implements Opcodes {

    private static final String BACKEND = "com/gtnewhorizons/angelica/glsm/backend/RenderBackend";
    private static final String MANAGER = "com/gtnewhorizons/angelica/glsm/backend/BackendManager";
    private static final String IRIS = "com/gtnewhorizons/angelica/rendering/celeritas/api/IrisShaderProviderHolder";
    private static final String SLICE = "com.miaokatze.miaokatzecore.client.camera.slice.SliceShaders";
    private static final String CAMERA = "com.miaokatze.miaokatzecore.api.camera.";
    private static final String VERTEX = "#version 330\nvoid main() {\n  gl_Position = vec4(1.0);\n}\n";
    private static final String FRAGMENT = "#version 330\nvoid main() {\n}\n";
    private static final List<String> failures = new ArrayList<>();
    private static int checks;

    private SliceBackendContracts() {}

    public static void main(String[] args) throws Exception {
        URL[] target;
        if (args.length != 0) {
            target = new URL[] { new File(args[0]).toURI()
                .toURL() };
        } else {
            String resource = "assets/miaokatzecore/shaders/slice_fragment.glsl";
            URL shader = SliceShaders.class.getResource("/" + resource);
            String location = shader.toExternalForm();
            target = new URL[] { SliceShaders.class.getProtectionDomain()
                .getCodeSource()
                .getLocation(), new URL(location.substring(0, location.length() - resource.length())) };
        }
        scenario(target, true, false, false);
        scenario(target, true, true, false);
        scenario(target, false, false, false);
        scenario(target, false, true, false);
        scenario(target, false, false, true);
        if (!failures.isEmpty()) throw new AssertionError(String.join("\n", failures));
        System.out.println("Slice backend contracts: " + checks + " checks passed (isolated renderer stubs; no GPU).");
    }

    private static void scenario(URL[] target, boolean sdl, boolean iris, boolean absent) throws Exception {
        String mode = absent ? "absent Angelica" : (sdl ? "SDL GPU" : "OpenGL") + ", Iris=" + iris;
        try (URLClassLoader loader = new URLClassLoader(target, null) {

            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                String internal = name.replace('.', '/');
                if (internal.startsWith("com/gtnewhorizons/angelica/")) {
                    if (absent) throw new ClassNotFoundException(name);
                    byte[] bytes;
                    if (internal.equals(BACKEND)) bytes = backend(sdl);
                    else if (internal.equals(MANAGER)) bytes = manager();
                    else if (internal.equals(IRIS)) bytes = iris(iris);
                    else throw new ClassNotFoundException("Unexpected renderer dependency: " + name);
                    return defineClass(name, bytes, 0, bytes.length);
                }
                return super.findClass(name);
            }
        }) {
            Class<?> slice = loader.loadClass(SLICE);
            Field frame = slice.getDeclaredField("frame");
            frame.setAccessible(true);
            require(frame.get(null) == null, mode + ": source checks have no active frame");
            Method vertex = slice.getMethod("vertexSource", String.class, boolean.class);
            Method fragment = slice.getMethod("fragmentSource", String.class);
            Method terrain = slice.getMethod("terrainSource", String.class, String.class);
            String[] sources = { (String) vertex.invoke(null, VERTEX, true), (String) fragment.invoke(null, FRAGMENT),
                (String) terrain.invoke(null, "sodium:blocks/block_layer_opaque.vsh", VERTEX),
                (String) terrain.invoke(null, "angelica:blocks/block_layer_opaque.fsh", FRAGMENT) };
            for (int i = 0; i < sources.length; i++) {
                String original = (i & 1) == 0 ? VERTEX : FRAGMENT;
                require(
                    sdl ? sources[i] == original
                        : sources[i].contains((i & 1) == 0 ? "v_CoreSliceEye" : "u_CoreSliceEnabled"),
                    mode + ": shader route " + i + (sdl ? " returns the original source" : " retains injection"));
            }
            require(
                terrain.invoke(null, "other:shader", VERTEX) == VERTEX,
                mode + ": unrelated terrain resource remains unchanged");
            String reason = (String) slice.getMethod("unsupportedReason")
                .invoke(null);
            require(
                absent ? reason != null && reason.contains("unavailable")
                    : sdl ? reason != null && reason.contains("SDL GPU")
                        : iris ? reason != null && reason.contains("Iris") : reason == null,
                mode + ": capability reason matches actual backend and Iris status");
            Class<?> pose = loader.loadClass(CAMERA + "CameraPose");
            Class<?> projection = loader.loadClass(CAMERA + "OrthographicProjection");
            Class<?> data = loader.loadClass(SLICE + "$FrameData");
            Object camera = pose.getConstructor(double.class, double.class, double.class, double.class, double.class)
                .newInstance(0, 20, 0, 45, 35);
            Object ortho = projection
                .getConstructor(double.class, double.class, double.class, double.class, double.class)
                .newInstance(20, 15, 256, .05, 1024);
            Object requested = data
                .getConstructor(
                    boolean.class,
                    double.class,
                    double.class,
                    double.class,
                    pose,
                    projection,
                    int.class,
                    int.class,
                    int.class,
                    int.class)
                .newInstance(true, 0, 30, 1, camera, ortho, 0, 0, 640, 360);
            slice.getMethod("beginFrame", data)
                .invoke(null, requested);
            require(
                (frame.get(null) != null) == (!absent && !sdl && !iris),
                mode + ": frame admission follows capability");
            slice.getMethod("upload", int.class)
                .invoke(null, 1);
            slice.getMethod("endFrame")
                .invoke(null);
            require(frame.get(null) == null, mode + ": lifecycle leaves no active frame");
        }
    }

    private static byte[] backend(boolean sdl) {
        ClassWriter writer = type(BACKEND);
        bool(writer, "isSDLGPU", sdl, ACC_PUBLIC);
        // Any native operation means the supposedly headless lifecycle crossed the renderer boundary.
        for (String[] method : new String[][] { { "getInteger", "(I)I" },
            { "getUniformLocation", "(ILjava/lang/String;)I" }, { "uniform1i", "(II)V" }, { "uniform1f", "(IF)V" },
            { "uniform3f", "(IFFF)V" }, { "uniform4f", "(IFFFF)V" } }) {
            MethodVisitor visitor = writer.visitMethod(ACC_PUBLIC, method[0], method[1], null, null);
            visitor.visitCode();
            visitor.visitTypeInsn(NEW, "java/lang/AssertionError");
            visitor.visitInsn(DUP);
            visitor.visitLdcInsn("Unexpected native renderer operation: " + method[0]);
            visitor
                .visitMethodInsn(INVOKESPECIAL, "java/lang/AssertionError", "<init>", "(Ljava/lang/Object;)V", false);
            visitor.visitInsn(ATHROW);
            visitor.visitMaxs(0, 0);
            visitor.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] manager() {
        ClassWriter writer = type(MANAGER);
        writer.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "RENDER_BACKEND", "L" + BACKEND + ";", null, null)
            .visitEnd();
        MethodVisitor visitor = writer.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        visitor.visitCode();
        visitor.visitTypeInsn(NEW, BACKEND);
        visitor.visitInsn(DUP);
        visitor.visitMethodInsn(INVOKESPECIAL, BACKEND, "<init>", "()V", false);
        visitor.visitFieldInsn(PUTSTATIC, MANAGER, "RENDER_BACKEND", "L" + BACKEND + ";");
        visitor.visitInsn(RETURN);
        visitor.visitMaxs(0, 0);
        visitor.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] iris(boolean active) {
        ClassWriter writer = type(IRIS);
        bool(writer, "isActive", active, ACC_PUBLIC | ACC_STATIC);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static ClassWriter type(String name) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_8, ACC_PUBLIC | ACC_SUPER, name, null, "java/lang/Object", null);
        MethodVisitor visitor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        visitor.visitCode();
        visitor.visitVarInsn(ALOAD, 0);
        visitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        visitor.visitInsn(RETURN);
        visitor.visitMaxs(0, 0);
        visitor.visitEnd();
        return writer;
    }

    private static void bool(ClassWriter writer, String name, boolean value, int access) {
        MethodVisitor visitor = writer.visitMethod(access, name, "()Z", null, null);
        visitor.visitCode();
        visitor.visitInsn(value ? ICONST_1 : ICONST_0);
        visitor.visitInsn(IRETURN);
        visitor.visitMaxs(0, 0);
        visitor.visitEnd();
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) failures.add(message);
    }
}
