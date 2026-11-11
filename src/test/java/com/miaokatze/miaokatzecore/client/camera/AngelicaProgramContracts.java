// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.client.camera;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.miaokatze.miaokatzecore.client.camera.slice.SliceShaders;

/** Native GLSMPrograms.glDeleteProgram and event bus, with capture/compat/backend boundary doubles; no GL. */
public final class AngelicaProgramContracts implements Opcodes {

    private static final String PROGRAMS = "com.gtnewhorizons.angelica.glsm.GLSMPrograms";
    private static final String STATE = "com/gtnewhorizons/angelica/glsm/GLStateManager";
    private static final String COMPAT = "com/gtnewhorizons/angelica/glsm/CompatUniformManager";
    private static final String MANAGER = "com/gtnewhorizons/angelica/glsm/backend/BackendManager";
    private static final String BACKEND = "com/gtnewhorizons/angelica/glsm/backend/RenderBackend";
    private static final String RECORDING = "core/fixture/DeleteBackend";
    private static final String SELF = "com/miaokatze/miaokatzecore/client/camera/AngelicaProgramContracts";
    private static int stage, deletes;

    public static void main(String[] args) throws Exception {
        String[] paths = System.getProperty("java.class.path")
            .split(File.pathSeparator);
        URL[] urls = new URL[paths.length];
        for (int i = 0; i < paths.length; i++) urls[i] = new File(paths[i]).toURI()
            .toURL();
        try (URLClassLoader loader = new URLClassLoader(urls, AngelicaProgramContracts.class.getClassLoader()) {

            @Override
            protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                String internal = name.replace('.', '/');
                if (!name.equals(PROGRAMS) && !internal.equals(STATE)
                    && !internal.equals(COMPAT)
                    && !internal.equals(MANAGER)
                    && !internal.equals(RECORDING)) return super.loadClass(name, resolve);
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    if (name.equals(PROGRAMS)) type = findClass(name);
                    else {
                        byte[] bytes = adapter(internal);
                        type = defineClass(name, bytes, 0, bytes.length);
                    }
                }
                if (resolve) resolveClass(type);
                return type;
            }
        }) {
            loader.loadClass(MANAGER.replace('/', '.'))
                .getField("RENDER_BACKEND")
                .set(
                    null,
                    loader.loadClass(RECORDING.replace('/', '.'))
                        .newInstance());
            Class<?> nativePrograms = loader.loadClass(PROGRAMS);
            Field locations = SliceShaders.class.getDeclaredField("LOCATIONS");
            locations.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Integer, Object> ledger = (Map<Integer, Object>) locations.get(null);
            AngelicaProgramBridge.install();
            AngelicaProgramBridge.install();
            ledger.put(0, null);
            invoke(nativePrograms, 0, false);
            check(ledger.containsKey(0) && deletes == 0, "zero is ignored before deletion event");
            ledger.put(101, null);
            stage = 1;
            invoke(nativePrograms, 101, true);
            check(ledger.containsKey(101) && deletes == 0, "capture refusal retains ledger");
            stage = 2;
            invoke(nativePrograms, 101, true);
            check(ledger.containsKey(101) && deletes == 0, "compat failure before event retains ledger");
            stage = 3;
            invoke(nativePrograms, 101, true);
            check(!ledger.containsKey(101) && deletes == 1, "event precedes a failing backend deletion");
            stage = 0;
            ledger.put(101, null);
            ledger.put(202, null);
            invoke(nativePrograms, 101, false);
            check(!ledger.containsKey(101) && ledger.containsKey(202), "exact deletion leaves peer program");
            ledger.put(101, null);
            check(ledger.containsKey(101), "reused id admits a fresh ledger entry");
            invoke(nativePrograms, 101, false);
            check(!ledger.containsKey(101), "reused id gets its own later deletion");
            ledger.clear();
            System.out.println(
                "AngelicaProgramContracts: PASS (native delete order, zero, capture, compat failure, backend failure, exact id/reuse)");
        }
    }

    private static void invoke(Class<?> programs, int program, boolean fails) throws Exception {
        try {
            programs.getMethod("glDeleteProgram", int.class)
                .invoke(null, program);
            check(!fails, "expected boundary failure");
        } catch (InvocationTargetException failed) {
            if (!fails || !(failed.getCause() instanceof IllegalStateException)) throw failed;
        }
    }

    public static void capture() {
        if (stage == 1) throw new IllegalStateException("capture refuses delete");
    }

    public static void compat(int program) {
        if (stage == 2) throw new IllegalStateException("compat cleanup failed");
    }

    public static void delete(int program) {
        deletes++;
        if (stage == 3) throw new IllegalStateException("backend refused delete");
    }

    private static byte[] adapter(String name) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_8, ACC_PUBLIC, name, null, name.equals(RECORDING) ? BACKEND : "java/lang/Object", null);
        if (name.equals(MANAGER)) {
            writer.visitField(ACC_PUBLIC | ACC_STATIC, "RENDER_BACKEND", "L" + BACKEND + ";", null, null)
                .visitEnd();
        } else if (name.equals(STATE)) {
            bridge(writer, "rejectExitCapture", "()V", "capture");
        } else if (name.equals(COMPAT)) {
            bridge(writer, "onDeleteProgram", "(I)V", "compat");
        } else if (name.equals(RECORDING)) {
            MethodVisitor constructor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            constructor.visitCode();
            constructor.visitVarInsn(ALOAD, 0);
            constructor.visitMethodInsn(INVOKESPECIAL, BACKEND, "<init>", "()V", false);
            constructor.visitInsn(RETURN);
            constructor.visitMaxs(0, 0);
            constructor.visitEnd();
            MethodVisitor delete = writer.visitMethod(ACC_PUBLIC, "deleteProgram", "(I)V", null, null);
            delete.visitCode();
            delete.visitVarInsn(ILOAD, 1);
            delete.visitMethodInsn(INVOKESTATIC, SELF, "delete", "(I)V", false);
            delete.visitInsn(RETURN);
            delete.visitMaxs(0, 0);
            delete.visitEnd();
            MethodVisitor exists = writer.visitMethod(ACC_PUBLIC, "isProgram", "(I)Z", null, null);
            exists.visitCode();
            exists.visitInsn(ICONST_0);
            exists.visitInsn(IRETURN);
            exists.visitMaxs(0, 0);
            exists.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void bridge(ClassWriter writer, String method, String descriptor, String target) {
        MethodVisitor visitor = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, method, descriptor, null, null);
        visitor.visitCode();
        if (descriptor.equals("(I)V")) visitor.visitVarInsn(ILOAD, 0);
        visitor.visitMethodInsn(INVOKESTATIC, SELF, target, descriptor, false);
        visitor.visitInsn(RETURN);
        visitor.visitMaxs(0, 0);
        visitor.visitEnd();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
