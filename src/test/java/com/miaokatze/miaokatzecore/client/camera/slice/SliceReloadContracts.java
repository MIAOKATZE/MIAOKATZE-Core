package com.miaokatze.miaokatzecore.client.camera.slice;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.miaokatze.miaokatzecore.api.camera.CameraPose;
import com.miaokatze.miaokatzecore.api.camera.OrthographicProjection;

/** Real Core state transitions and native Angelica backend interface; driver calls are recorded, without GL. */
public final class SliceReloadContracts implements Opcodes {

    private static final String SLICE = "com.miaokatze.miaokatzecore.client.camera.slice.SliceShaders";
    private static final String BACKEND = "com/gtnewhorizons/angelica/glsm/backend/RenderBackend";
    private static final String MANAGER = "com/gtnewhorizons/angelica/glsm/backend/BackendManager";
    private static final String IRIS = "com/gtnewhorizons/angelica/rendering/celeritas/api/IrisShaderProviderHolder";

    private static final String RECORDING = "core/fixture/RecordingBackend";
    private static final Map<Integer, Integer> UNIFORMS = new HashMap<>();
    private static final int[] CURRENT = { 0 };

    public static void main(String[] args) throws Exception {
        URL target = args.length == 0 ? SliceShaders.class.getProtectionDomain()
            .getCodeSource()
            .getLocation()
            : new File(args[0]).toURI()
                .toURL();
        try (URLClassLoader loader = new URLClassLoader(
            new URL[] { target },
            SliceReloadContracts.class.getClassLoader()) {

            @Override
            protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                String internal = name.replace('.', '/');
                if (!name.startsWith(SLICE) && !internal.equals(MANAGER)
                    && !internal.equals(IRIS)
                    && !internal.equals(RECORDING)) {
                    return super.loadClass(name, resolve);
                }
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    if (internal.equals(MANAGER) || internal.equals(IRIS) || internal.equals(RECORDING)) {
                        byte[] bytes = adapter(internal);
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } else loaded = findClass(name);
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }) {
            Map<Integer, Integer> uniforms = UNIFORMS;
            int[] current = CURRENT;
            Object driver = loader.loadClass(RECORDING.replace('/', '.'))
                .newInstance();
            loader.loadClass(MANAGER.replace('/', '.'))
                .getField("RENDER_BACKEND")
                .set(null, driver);
            Class<?> slice = loader.loadClass(SLICE);
            Class<?> dataType = loader.loadClass(SLICE + "$FrameData");
            Object data = frame(dataType);
            Class<?> domainType = loader.loadClass(SLICE + "$Domain");
            Object terrain = domainType.getEnumConstants()[0];
            slice.getMethod("beginFrame", dataType)
                .invoke(null, data);
            AutoCloseable scope = (AutoCloseable) slice.getMethod("domain", domainType)
                .invoke(null, terrain);
            current[0] = 101;
            slice.getMethod("upload", int.class)
                .invoke(null, 101);
            current[0] = 202;
            slice.getMethod("upload", int.class)
                .invoke(null, 202);
            scope.close();
            slice.getMethod("endFrame")
                .invoke(null);
            check(uniforms.get(101) == 1 && uniforms.get(202) == 0, "earlier variant awaits lazy disable");
            slice.getMethod("resourceReloaded")
                .invoke(null);
            slice.getMethod("resourceReloaded")
                .invoke(null);
            check(count(slice) == 1, "reload retains pending earlier variant across repeated reload");
            check(current[0] == 202, "reload does not bind or delete foreign programs");
            current[0] = 101;
            slice.getMethod("upload", int.class)
                .invoke(null, 101);
            check(uniforms.get(101) == 0 && count(slice) == 0, "rebound earlier variant disables after reload");

            slice.getMethod("beginFrame", dataType)
                .invoke(null, data);
            scope = (AutoCloseable) slice.getMethod("domain", domainType)
                .invoke(null, terrain);
            slice.getMethod("upload", int.class)
                .invoke(null, 101);
            current[0] = 202;
            slice.getMethod("upload", int.class)
                .invoke(null, 202);
            slice.getMethod("forgetProgram", int.class)
                .invoke(null, 101);
            check(count(slice) == 1, "deletion forgets only its exact program");
            scope.close();
            slice.getMethod("endFrame")
                .invoke(null);
            slice.getMethod("resourceReloaded")
                .invoke(null);
            check(count(slice) == 0, "deleted/closed programs leave no pending count");
            Field locations = slice.getDeclaredField("LOCATIONS");
            locations.setAccessible(true);
            check(((Map<?, ?>) locations.get(null)).isEmpty(), "fully retired ledger clears on reload");
        }
        System.out
            .println("SliceReloadContracts: PASS (two variants, repeated reload, lazy disable, exact deletion; no GL)");
    }

    private static Object frame(Class<?> type) throws Exception {
        return type
            .getConstructor(
                boolean.class,
                double.class,
                double.class,
                double.class,
                CameraPose.class,
                OrthographicProjection.class,
                int.class,
                int.class,
                int.class,
                int.class)
            .newInstance(
                true,
                0,
                30,
                1,
                new CameraPose(0, 20, 0, 45, 35),
                new OrthographicProjection(20, 15, 256, .05, 1024),
                0,
                0,
                640,
                360);
    }

    private static int count(Class<?> slice) throws Exception {
        Field count = slice.getDeclaredField("enabledPrograms");
        count.setAccessible(true);
        return count.getInt(null);
    }

    private static byte[] adapter(String internal) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(
            V1_8,
            ACC_PUBLIC | ACC_SUPER,
            internal,
            null,
            internal.equals(RECORDING) ? BACKEND : "java/lang/Object",
            null);
        if (internal.equals(RECORDING)) {
            MethodVisitor constructor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            constructor.visitCode();
            constructor.visitVarInsn(ALOAD, 0);
            constructor.visitMethodInsn(INVOKESPECIAL, BACKEND, "<init>", "()V", false);
            constructor.visitInsn(RETURN);
            constructor.visitMaxs(0, 0);
            constructor.visitEnd();
            recordingMethod(writer, "getInteger", "(I)I", "currentProgram", "()I", 0);
            recordingMethod(
                writer,
                "getUniformLocation",
                "(ILjava/lang/CharSequence;)I",
                "uniformLocation",
                "(ILjava/lang/CharSequence;)I",
                2);
            recordingMethod(writer, "uniform1i", "(II)V", "writeUniform", "(II)V", 2);
            for (String[] method : new String[][] { { "uniform1f", "(IF)V" }, { "uniform3f", "(IFFF)V" },
                { "uniform4f", "(IFFFF)V" } }) {
                MethodVisitor noop = writer.visitMethod(ACC_PUBLIC, method[0], method[1], null, null);
                noop.visitCode();
                noop.visitInsn(RETURN);
                noop.visitMaxs(0, 0);
                noop.visitEnd();
            }
            writer.visitEnd();
            return writer.toByteArray();
        }
        if (internal.equals(MANAGER)) {
            writer.visitField(ACC_PUBLIC | ACC_STATIC, "RENDER_BACKEND", "L" + BACKEND + ";", null, null)
                .visitEnd();
        } else {
            MethodVisitor method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "isActive", "()Z", null, null);
            method.visitCode();
            method.visitInsn(ICONST_0);
            method.visitInsn(IRETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void recordingMethod(ClassWriter writer, String name, String descriptor, String target,
        String targetDescriptor, int arguments) {
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC, name, descriptor, null, null);
        method.visitCode();
        if (arguments != 0) {
            method.visitVarInsn(ILOAD, 1);
            method.visitVarInsn(name.equals("getUniformLocation") ? ALOAD : ILOAD, 2);
        }
        method.visitMethodInsn(
            INVOKESTATIC,
            SliceReloadContracts.class.getName()
                .replace('.', '/'),
            target,
            targetDescriptor,
            false);
        method.visitInsn(descriptor.endsWith("V") ? RETURN : IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    public static int currentProgram() {
        return CURRENT[0];
    }

    public static int uniformLocation(int program, CharSequence name) {
        return "u_CoreSliceEnabled".contentEquals(name) ? program * 10 : -1;
    }

    public static void writeUniform(int location, int value) {
        if (location < 0) return;
        check(location == CURRENT[0] * 10, "uniform belongs to bound program");
        UNIFORMS.put(CURRENT[0], value);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
