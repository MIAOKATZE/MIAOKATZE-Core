package com.miaokatze.miaokatzecore.client.camera.slice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.miaokatze.miaokatzecore.api.camera.CameraPose;
import com.miaokatze.miaokatzecore.api.camera.OrthographicProjection;
import com.miaokatze.miaokatzecore.api.camera.SliceSurfaceRule;

/** RC2 default-terrain and emulated-FFP slice bridge. Mutable state belongs to the render thread. */
public final class SliceShaders {

    public enum Domain {
        TERRAIN,
        TILE_ENTITIES,
        WORLD_PREVIEW
    }

    private static final Map<Integer, Locations> LOCATIONS = new HashMap<>();
    private static FrameData frame;
    private static Domain currentDomain;
    private static long generation;
    private static int enabledPrograms;

    private SliceShaders() {}

    /** No GL or optional renderer classes are initialized by constructing a frame. */
    public static final class FrameData {

        public final boolean enabled;
        final float lower, upper, band, upY, upZ, eyeOffset, inverseX, inverseY, inverseZ, inverseOffsetZ;
        final boolean featheredCaps;
        final float layerOffset;
        public final int viewportX, viewportY, viewportWidth, viewportHeight;

        public FrameData(boolean enabled, double lowerBound, double upperBound, double bandWidth, CameraPose pose,
            OrthographicProjection projection, int viewportX, int viewportY, int viewportWidth, int viewportHeight) {
            this(
                enabled,
                lowerBound,
                upperBound,
                bandWidth,
                pose,
                projection,
                viewportX,
                viewportY,
                viewportWidth,
                viewportHeight,
                false);
        }

        public FrameData(boolean enabled, double lowerBound, double upperBound, double bandWidth, CameraPose pose,
            OrthographicProjection projection, int viewportX, int viewportY, int viewportWidth, int viewportHeight,
            boolean featheredCaps) {
            Objects.requireNonNull(pose, "pose");
            Objects.requireNonNull(projection, "projection");
            if (!Double.isFinite(lowerBound) || !Double.isFinite(upperBound)
                || lowerBound >= upperBound
                || !Double.isFinite(bandWidth)
                || bandWidth < 0
                || viewportWidth <= 0
                || viewportHeight <= 0) {
                throw new IllegalArgumentException("Invalid slice shader frame");
            }
            this.enabled = enabled;
            this.featheredCaps = featheredCaps;
            layerOffset = (float) (pose.anchorY() - Math.floor(pose.anchorY()));
            // Retain precision at large world coordinates by making heights relative to the camera anchor.
            lower = (float) (lowerBound - pose.anchorY());
            upper = (float) (upperBound - pose.anchorY());
            band = (float) bandWidth;
            double pitch = Math.toRadians(pose.pitchDegrees());
            upY = (float) Math.cos(pitch);
            upZ = (float) Math.sin(pitch);
            eyeOffset = (float) (Math.sin(pitch) * projection.depthOffset());
            inverseX = (float) projection.halfWidth();
            inverseY = (float) projection.halfHeight();
            inverseZ = (float) (-(projection.far() - projection.near()) * .5);
            inverseOffsetZ = (float) (-(projection.far() + projection.near()) * .5);
            this.viewportX = viewportX;
            this.viewportY = viewportY;
            this.viewportWidth = viewportWidth;
            this.viewportHeight = viewportHeight;
        }
    }

    public static void beginFrame(FrameData data) {
        endFrame();
        if (data != null && data.enabled && unsupportedReason() == null) frame = data;
    }

    public static void endFrame() {
        boolean owned = frame != null || currentDomain != null;
        frame = null;
        currentDomain = null;
        generation++;
        if (owned && enabledPrograms != 0) Bridge.disableCurrentProgram();
    }

    /** Null means the RC2 default shader path is available. Active shader packs are unsupported. */
    public static String unsupportedReason() {
        try {
            return Bridge.unsupportedReason();
        } catch (LinkageError missingRenderer) {
            return "Angelica 2.2.28 default shader backend is unavailable";
        }
    }

    public static Scope domain(Domain domain) {
        Objects.requireNonNull(domain, "domain");
        if (frame == null) return new Scope(null, generation, false);
        Domain previous = currentDomain;
        currentDomain = domain;
        if (enabledPrograms != 0) Bridge.disableCurrentProgram();
        return new Scope(previous, generation, true);
    }

    public static final class Scope implements AutoCloseable {

        private final Domain previous;
        private final long frameGeneration;
        private final boolean participating;
        private boolean closed;

        private Scope(Domain previous, long frameGeneration, boolean participating) {
            this.previous = previous;
            this.frameGeneration = frameGeneration;
            this.participating = participating;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (!participating || frameGeneration != generation) return;
            currentDomain = previous;
            if (enabledPrograms != 0) Bridge.disableCurrentProgram();
        }
    }

    /** Called after the actual FFP bind/upload, never with GLSM's logical activeProgram=0. */
    public static void upload(int program) {
        if (program > 0 && (wantsEnabled() || enabledPrograms != 0)) Bridge.upload(program);
    }

    public static void uploadCurrentProgram() {
        if (wantsEnabled() || enabledPrograms != 0) Bridge.uploadCurrentProgram();
    }

    public static void forgetProgram(int program) {
        Locations removed = LOCATIONS.remove(program);
        if (removed != null && removed.enabledValue) enabledPrograms--;
    }

    public static void resourceReloaded() {
        endFrame();
        LOCATIONS.clear();
        enabledPrograms = 0;
    }

    private static boolean wantsEnabled() {
        return frame != null && (currentDomain == Domain.TERRAIN || currentDomain == Domain.TILE_ENTITIES
            || currentDomain == Domain.WORLD_PREVIEW);
    }

    public static String terrainSource(String resource, String source) {
        if ("sodium:blocks/block_layer_opaque.vsh".equals(resource)) return vertexSource(source, false);
        if ("angelica:blocks/block_layer_opaque.fsh".equals(resource)) return fragmentSource(source);
        return source;
    }

    /** Final gl_Position includes instancing, cube and weather transforms in every FFP variant. */
    public static String vertexSource(String source, boolean fixedFunction) {
        if (source.contains("v_CoreSliceEye")) return source;
        String varying = fixedFunction && source.contains("v_Color_gs") ? "v_CoreSliceEye_gs" : "v_CoreSliceEye";
        String declarations = "out vec3 " + varying + ";\n";
        String assignment;
        if (fixedFunction) {
            declarations += "uniform vec4 u_CoreSliceInverseProjection;\n";
            assignment = "\n  " + varying
                + " = (gl_Position.xyz / gl_Position.w) * "
                + "u_CoreSliceInverseProjection.xyz + vec3(0.0, 0.0, u_CoreSliceInverseProjection.w);\n";
        } else {
            assignment = "\n  " + varying + " = (u_ModelViewMatrix * vec4(position, 1.0)).xyz;\n";
        }
        int main = source.indexOf("void main() {");
        int end = source.lastIndexOf('}');
        if (main < 0 || end < main) throw new IllegalStateException("Unsupported RC2 vertex shader layout");
        return source.substring(0, main) + declarations
            + source.substring(main, end)
            + assignment
            + source.substring(end);
    }

    public static String fragmentSource(String source) {
        if (source.contains("coreSliceDiscard")) return source;
        int main = source.indexOf("void main() {");
        if (main < 0) throw new IllegalStateException("Unsupported RC2 fragment shader layout");
        return source.substring(0, main) + FragmentSource.VALUE
            + source.substring(main)
                .replace("void main() {", "void main() {\n  coreSliceDiscard();");
    }

    /** Forward the new varying through wide-line near-plane interpolation. */
    public static String geometrySource(String source) {
        int main = source.indexOf("void main() {");
        if (main < 0) throw new IllegalStateException("Unsupported RC2 geometry shader layout");
        String result = source.substring(0, main) + "in vec3 v_CoreSliceEye_gs[];\nout vec3 v_CoreSliceEye;\n"
            + source.substring(main);
        for (int i = 0; i < 2; i++) {
            String anchor = "v_Color = mix(v_Color_gs[0], v_Color_gs[1], t" + i + ");";
            if (!result.contains(anchor)) throw new IllegalStateException("Unsupported RC2 wide-line interpolation");
            result = result.replace(
                anchor,
                anchor + "\n    v_CoreSliceEye = mix(v_CoreSliceEye_gs[0], " + "v_CoreSliceEye_gs[1], t" + i + ");");
        }
        return result;
    }

    private static final class FragmentSource {

        static final String VALUE = load();

        private static String load() {
            String path = "/assets/miaokatzecore/shaders/slice_fragment.glsl";
            try (InputStream input = SliceShaders.class.getResourceAsStream(path)) {
                if (input == null) throw new IllegalStateException("Missing " + path);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[2048];
                int count;
                while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                String text = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
                StringBuilder thresholds = new StringBuilder("const float coreSliceBayer[16] = float[16](");
                for (int y = 0; y < 4; y++) {
                    for (int x = 0; x < 4; x++) {
                        if (x != 0 || y != 0) thresholds.append(',');
                        thresholds.append(SliceSurfaceRule.thresholdAt(x, y));
                    }
                }
                thresholds.append(");\n");
                return text.replace(
                    "// CORE_SLICE_CONSTANTS",
                    thresholds.toString() + "const float coreSliceCapEpsilon = "
                        + SliceSurfaceRule.CAP_EPSILON
                        + ";\n"
                        + "const float coreSliceNormalThreshold = "
                        + SliceSurfaceRule.NORMAL_Y_THRESHOLD
                        + ";\n");
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot read slice shader", failure);
            }
        }
    }

    private static final class Locations {

        final int enabled, bounds, worldUp, inverseProjection, featheredCaps, layerOffset;
        boolean enabledValue;
        FrameData uploadedData;

        Locations(int program) {
            enabled = Bridge.location(program, "u_CoreSliceEnabled");
            bounds = Bridge.location(program, "u_CoreSliceBounds");
            worldUp = Bridge.location(program, "u_CoreSliceWorldUp");
            inverseProjection = Bridge.location(program, "u_CoreSliceInverseProjection");
            featheredCaps = Bridge.location(program, "u_CoreSliceFeatheredCaps");
            layerOffset = Bridge.location(program, "u_CoreSliceLayerOffset");
        }
    }

    /** Optional Angelica symbols stay in this lazy class; public lifecycle calls are safe without Angelica. */
    private static final class Bridge {

        private static String unsupportedReason() {
            return com.gtnewhorizons.angelica.rendering.celeritas.api.IrisShaderProviderHolder.isActive()
                ? "Height slicing does not support active Iris shader packs"
                : null;
        }

        private static int location(int program, String name) {
            return com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND
                .getUniformLocation(program, name);
        }

        private static void uploadCurrentProgram() {
            upload(com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND.getInteger(0x8B8D));
        }

        private static void upload(int program) {
            if (program <= 0) return;
            boolean enabled = wantsEnabled();
            Locations locations = LOCATIONS.get(program);
            if (locations == null) {
                // No frame/owner means no location lookups. Shader-pack programs are never discovered or cached.
                if (!enabled
                    || com.gtnewhorizons.angelica.rendering.celeritas.api.IrisShaderProviderHolder.isActive()) {
                    return;
                }
                locations = new Locations(program);
                if (locations.enabled < 0) return;
                LOCATIONS.put(program, locations);
            }
            com.gtnewhorizons.angelica.glsm.backend.RenderBackend backend = com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;
            FrameData data = frame;
            if (locations.enabledValue != enabled) {
                backend.uniform1i(locations.enabled, enabled ? 1 : 0);
                locations.enabledValue = enabled;
                enabledPrograms += enabled ? 1 : -1;
            }
            if (!enabled) return;
            if (locations.uploadedData == data) return;
            backend.uniform3f(locations.bounds, data.lower, data.upper, data.band);
            if (locations.featheredCaps >= 0) backend.uniform1i(locations.featheredCaps, data.featheredCaps ? 1 : 0);
            if (locations.layerOffset >= 0) backend.uniform1f(locations.layerOffset, data.layerOffset);
            backend.uniform4f(locations.worldUp, 0, data.upY, data.upZ, data.eyeOffset);
            if (locations.inverseProjection >= 0) backend.uniform4f(
                locations.inverseProjection,
                data.inverseX,
                data.inverseY,
                data.inverseZ,
                data.inverseOffsetZ);
            locations.uploadedData = data;
        }

        private static void disableCurrentProgram() {
            com.gtnewhorizons.angelica.glsm.backend.RenderBackend backend = com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;
            // The logical program is zero while an FFP program is physically bound.
            int current = backend.getInteger(0x8B8D);
            Locations locations = LOCATIONS.get(current);
            if (locations == null || !locations.enabledValue) return;
            backend.uniform1i(locations.enabled, 0);
            locations.enabledValue = false;
            enabledPrograms--;
        }
    }
}
