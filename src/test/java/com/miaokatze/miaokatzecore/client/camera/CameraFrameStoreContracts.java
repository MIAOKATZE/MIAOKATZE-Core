package com.miaokatze.miaokatzecore.client.camera;

import com.miaokatze.miaokatzecore.api.camera.CameraBasis;
import com.miaokatze.miaokatzecore.api.camera.CameraPose;
import com.miaokatze.miaokatzecore.api.camera.HeightSlice;
import com.miaokatze.miaokatzecore.api.camera.OrthographicProjection;

/** Display publication and transform-cache contracts, without Minecraft or OpenGL. */
public final class CameraFrameStoreContracts {

    public static void main(String[] args) {
        immediateSlicePicking();
        nativeWideLineShaders();
        CameraFrameStore store = new CameraFrameStore();
        WorldCameras.View view = view(1);
        long revision = store.transformRevision(view, 1280, 720, 640, 360);
        CameraBasis basis = store.basis(view, revision);
        CameraSettings settings = CameraSettings.builder()
            .build();
        CameraFrame first = frame(view, basis, revision);
        store.begin(first, view, settings, 0, 0, 0, 0, 0, 0);
        require(store.displayed() == null, "unapplied candidate is private");
        store.complete();
        require(store.displayed() == null, "unapplied invocation cannot publish");
        store.begin(first, view, settings, 0, 0, 0, 0, 0, 0);
        store.candidate.applied = true;
        require(store.displayed() == null, "applied invocation is still private before success");
        store.complete();
        require(store.displayed() == first && store.displayedSettings() == settings, "successful frozen snapshot");
        WorldCameras.View equal = view(1);
        require(
            store.transformRevision(equal, 1280, 720, 640, 360) == revision,
            "equal contents in fresh objects do not change revision");
        require(store.basis(equal, revision) == basis, "equal transform reuses trigonometry");
        WorldCameras.View moved = view(2);
        long movedRevision = store.transformRevision(moved, 1280, 720, 640, 360);
        require(
            movedRevision > revision && store.basis(moved, movedRevision) != basis,
            "anchor change invalidates basis");
        store.begin(frame(moved, store.basis(moved, movedRevision), movedRevision), moved, settings, 0, 0, 0, 0, 0, 0);
        store.candidate.applied = true;
        store.abort();
        require(
            store.displayed() == null && store.candidate == null && store.displayedSettings() == null,
            "throwing render clears all public display state");
        System.out.println("Camera frame-store contracts passed (Minecraft/OpenGL not required).");
    }

    private static void nativeWideLineShaders() {
        try {
            Class<?> keyType = Class.forName("com.gtnewhorizons.angelica.glsm.ffp.VertexKey");
            java.lang.reflect.Constructor<?> keyConstructor = keyType.getDeclaredConstructor(long.class);
            keyConstructor.setAccessible(true);
            java.lang.reflect.Method generate = Class
                .forName("com.gtnewhorizons.angelica.glsm.ffp.VertexShaderGenerator")
                .getMethod("generate", keyType);
            java.lang.reflect.Method patch = Class
                .forName("com.miaokatze.miaokatzecore.client.camera.slice.SliceShaders")
                .getMethod("vertexSource", String.class, boolean.class);
            long lineQuads = shaderBit(keyType, "BIT_LINE_QUADS");
            long features = shaderBit(keyType, "BIT_FOG") | shaderBit(keyType, "BIT_CLIP_PLANES")
                | shaderBit(keyType, "BIT_LINE_STIPPLE")
                | shaderBit(keyType, "BIT_HAS_VERTEX_COLOR")
                | shaderBit(keyType, "BIT_HAS_VERTEX_TEX")
                | shaderBit(keyType, "BIT_UNIT_TEX_BASE");
            java.lang.reflect.Field instancingBit = keyType.getDeclaredField("BIT_INSTANCING");
            instancingBit.setAccessible(true);
            for (int instancing = 0; instancing < 5; instancing++) {
                for (long options : new long[] { 0, features }) {
                    for (boolean wideLine : new boolean[] { false, true }) {
                        if (wideLine && instancing != 0) continue;
                        long flags = ((long) instancing << instancingBit.getInt(null)) | options
                            | (wideLine ? lineQuads : 0);
                        String nativeSource = (String) generate.invoke(null, keyConstructor.newInstance(flags));
                        String patched = (String) patch.invoke(null, nativeSource, true);
                        require(patched.contains("out vec3 v_CoreSliceEye;"), "slice output varying is declared");
                        require(patched.split("v_CoreSliceEye = ", -1).length == 2, "one slice coordinate per vertex");
                        require(patched.equals(patch.invoke(null, patched, true)), "patch is idempotent");
                        if (wideLine) {
                            require(
                                patched.indexOf("vec4 coreSliceLineThis = gl_Position;")
                                    < patched.indexOf("gl_Position = angelica_lineQuad("),
                                "capture the centerline before screen-space widening");
                            require(
                                patched.contains(
                                    "mix(lineStart ? coreSliceLineThis : lineOther, lineStart ? lineOther : coreSliceLineThis, lineT)"),
                                "both endpoints follow the native near-plane interpolation parameter");
                            require(
                                patched.contains("v_CoreSliceEye = (coreSliceLineCenter.xyz / coreSliceLineCenter.w)"),
                                "slice height excludes line width and pixel alignment offsets");
                            require(
                                !patched.contains("v_CoreSliceEye = (gl_Position.xyz / gl_Position.w)"),
                                "wide lines never reconstruct from expanded corners");
                        } else {
                            require(
                                patched.contains("v_CoreSliceEye = (gl_Position.xyz / gl_Position.w)"),
                                "ordinary, instanced, cube, particle and weather paths retain their final transform");
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("native wide-line shader contract", failure);
        }
    }

    private static long shaderBit(Class<?> keyType, String name) throws ReflectiveOperationException {
        java.lang.reflect.Field bit = keyType.getDeclaredField(name);
        bit.setAccessible(true);
        return 1L << bit.getInt(null);
    }

    private static void immediateSlicePicking() {
        try {
            Class<?> backend = Class.forName("com.miaokatze.miaokatzecore.client.camera.slice.SliceBackend");
            java.util.Map<java.lang.reflect.Field, Object> saved = new java.util.HashMap<>();
            HeightSlice slice = HeightSlice.below(20, 1);
            Object[][] state = { { "session", true }, { "epoch", 12L }, { "requestedRevision", 5L },
                { "displayedRevision", 5L }, { "requested", slice }, { "lastDisplayed", slice },
                { "meshCapabilityGeneration", 0L } };
            try {
                for (Object[] value : state) {
                    java.lang.reflect.Field field = backend.getDeclaredField((String) value[0]);
                    field.setAccessible(true);
                    saved.put(field, field.get(null));
                    field.set(null, value[1]);
                }
                WorldCameras.View view = view(1);
                CameraFrame applying = new CameraFrame(
                    "test:camera",
                    12,
                    view.pose,
                    view.projection,
                    new CameraBasis(view.pose),
                    1280,
                    720,
                    640,
                    360,
                    1,
                    0,
                    5,
                    5,
                    -1,
                    slice,
                    slice,
                    HeightSlice.disabled(),
                    SliceState.APPLYING,
                    true,
                    true,
                    "");
                java.lang.reflect.Method canPick = backend.getMethod("canPick", CameraFrame.class);
                require(
                    (Boolean) canPick.invoke(null, applying),
                    "displayed slice picks before boundary upload commits");
                java.lang.reflect.Field revision = backend.getDeclaredField("requestedRevision");
                revision.setAccessible(true);
                revision.set(null, 6L);
                require(
                    !(Boolean) canPick.invoke(null, applying),
                    "a new request rejects the previous displayed frame");
                revision.set(null, 5L);
                java.lang.reflect.Field epoch = backend.getDeclaredField("epoch");
                epoch.setAccessible(true);
                epoch.set(null, 13L);
                require(!(Boolean) canPick.invoke(null, applying), "a new owner cannot pick from the old frame");
                epoch.set(null, 12L);
                java.lang.reflect.Field requested = backend.getDeclaredField("requested");
                requested.setAccessible(true);
                requested.set(null, HeightSlice.below(21, 1));
                require(!(Boolean) canPick.invoke(null, applying), "changed slice bounds invalidate old picking");
            } finally {
                for (java.util.Map.Entry<java.lang.reflect.Field, Object> entry : saved.entrySet()) entry.getKey()
                    .set(null, entry.getValue());
            }
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("slice picking contract", failure);
        }
    }

    private static WorldCameras.View view(double x) {
        return new WorldCameras.View(
            new CameraPose(x, 20, 30, 45, 35),
            new OrthographicProjection(20, 15, 256, .05, 1024),
            true);
    }

    private static CameraFrame frame(WorldCameras.View view, CameraBasis basis, long revision) {
        return new CameraFrame(
            "test:camera",
            1,
            view.pose,
            view.projection,
            1280,
            720,
            640,
            360,
            revision,
            0,
            HeightSlice.disabled(),
            HeightSlice.disabled(),
            HeightSlice.disabled(),
            SliceState.DISABLED,
            true,
            basis);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
