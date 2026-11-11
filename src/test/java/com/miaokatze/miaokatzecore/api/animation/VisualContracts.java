// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.api.animation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Pure production contracts for the closed visual profile; no native renderer or synthetic Minecraft classes. */
public final class VisualContracts {

    private static int checks;

    private VisualContracts() {}

    public static void main(String[] args) throws Exception {
        model();
        evaluation();
        codec();
        parserAndStreamLimits();
        portableBudget();
        System.out
            .println("VisualContracts: PASS (" + checks + " checks; visual model, binding, TRS, strict codec, limits)");
    }

    private static AnimationV1.Track track(String id, AnimationV1.Property property, double start, double end) {
        return new AnimationV1.Track(
            id,
            "box",
            property,
            Arrays.asList(
                new AnimationV1.Key(0, start, AnimationV1.Interpolation.LINEAR),
                new AnimationV1.Key(1000, end, AnimationV1.Interpolation.LINEAR)));
    }

    private static AnimationV1.Document scalar(List<AnimationV1.Track> tracks) {
        return new AnimationV1.Document(
            "core:visual/box",
            0,
            1000,
            Long.MIN_VALUE,
            tracks,
            Collections.<AnimationV1.Cue>emptyList(),
            Collections.<ResourceId>emptySet());
    }

    private static VisualClip.Transform identity() {
        return new VisualClip.Transform(0, 0, 0, 0, 0, 0, 1, 1, 1, 1);
    }

    private static VisualClip clip(AnimationV1.Document scalar, VisualClip.Transform transform, double opacity) {
        return new VisualClip(
            scalar,
            Collections.singletonList(new VisualClip.Target("box", "paint", transform)),
            Collections.singletonList(new VisualClip.Material("paint", 0x33aaff, opacity)));
    }

    private static VisualClip fixture() {
        return clip(
            scalar(
                Arrays.asList(
                    track("move", AnimationV1.Property.X, 1, 3),
                    track("size", AnimationV1.Property.SCALE, 1, 2))),
            identity(),
            1);
    }

    private static VisualFrame sample(VisualPlan plan, long time) {
        return plan.evaluate(Sampler.sample(plan.clip.scalar, time));
    }

    private static void model() {
        List<VisualClip.Target> targets = new ArrayList<>();
        List<VisualClip.Material> materials = new ArrayList<>();
        targets.add(new VisualClip.Target("box", "paint", identity()));
        materials.add(new VisualClip.Material("paint", 0x123456, .8));
        VisualClip copy = new VisualClip(scalar(Collections.<AnimationV1.Track>emptyList()), targets, materials);
        targets.clear();
        materials.clear();
        check(copy.targets.size() == 1 && copy.materials.size() == 1, "defensive definition lists");
        expect(UnsupportedOperationException.class, () -> copy.targets.clear());
        expect(UnsupportedOperationException.class, () -> copy.materials.clear());
        expect(IllegalArgumentException.class, () -> new VisualClip.Transform(0, 0, 0, 0, 0, 0, 0, 1, 1, 1));
        expect(IllegalArgumentException.class, () -> new VisualClip.Transform(0, 0, 0, 0, 0, 0, 2, 1, 1, 1));
        expect(IllegalArgumentException.class, () -> new VisualClip.Transform(0, 0, 0, 0, 0, 0, 1, 0, 1, 1));
        expect(IllegalArgumentException.class, () -> new VisualClip.Transform(0, 0, 0, 0, 0, 0, 1, 33, 1, 1));
        expect(IllegalArgumentException.class, () -> new VisualClip.Transform(Double.NaN, 0, 0, 0, 0, 0, 1, 1, 1, 1));
        expect(IllegalArgumentException.class, () -> new VisualClip.Transform(1000001, 0, 0, 0, 0, 0, 1, 1, 1, 1));
        expect(IllegalArgumentException.class, () -> new VisualClip.Material("paint", -1, 1));
        expect(IllegalArgumentException.class, () -> new VisualClip.Material("paint", 0x1000000, 1));
        expect(IllegalArgumentException.class, () -> new VisualClip.Material("paint", 0, Double.POSITIVE_INFINITY));
        expect(IllegalArgumentException.class, () -> new VisualClip.Material("paint", 0, 1.1));
        expect(IllegalArgumentException.class, () -> new VisualClip.Target("bad/id", "paint", identity()));
        expect(IllegalArgumentException.class, () -> new VisualClip.Target("box", "paint", null));
        expect(
            IllegalArgumentException.class,
            () -> new VisualClip(copy.scalar, Arrays.asList(copy.targets.get(0), copy.targets.get(0)), copy.materials));
        expect(
            IllegalArgumentException.class,
            () -> new VisualClip(
                copy.scalar,
                copy.targets,
                Arrays.asList(copy.materials.get(0), copy.materials.get(0))));
        expect(
            IllegalArgumentException.class,
            () -> new VisualClip(
                copy.scalar,
                Collections.singletonList(new VisualClip.Target("box", "missing", identity())),
                copy.materials));
        expect(
            IllegalArgumentException.class,
            () -> new VisualClip(
                fixture().scalar,
                Collections.singletonList(new VisualClip.Target("other", "paint", identity())),
                copy.materials));
        expect(
            IllegalArgumentException.class,
            () -> clip(
                scalar(Collections.singletonList(track("bright", AnimationV1.Property.BRIGHTNESS, 1, 2))),
                identity(),
                1));
        expect(
            IllegalArgumentException.class,
            () -> clip(
                scalar(Collections.singletonList(track("fov", AnimationV1.Property.FOV_DEGREES, 45, 60))),
                identity(),
                1));
        check(
            !VisualClip.supports(null) && !VisualClip.supports(AnimationV1.Property.BRIGHTNESS)
                && !VisualClip.supports(AnimationV1.Property.FOV_DEGREES),
            "closed channel whitelist");
        AnimationV1.Document withResource = new AnimationV1.Document(
            "core:visual/resources",
            0,
            1,
            0,
            Collections.<AnimationV1.Track>emptyList(),
            Collections.<AnimationV1.Cue>emptyList(),
            Collections.singleton(ResourceId.parse("core:texture")));
        expect(IllegalArgumentException.class, () -> clip(withResource, identity(), 1));
        AnimationV1.Document withCue = new AnimationV1.Document(
            "core:visual/cue",
            0,
            1,
            0,
            Collections.<AnimationV1.Track>emptyList(),
            Collections.singletonList(
                new AnimationV1.Cue(
                    "cue",
                    0,
                    AnimationV1.CueKind.CAMERA_IMPULSE,
                    AnimationV1.LatePolicy.DROP,
                    0,
                    null)),
            Collections.<ResourceId>emptySet());
        expect(IllegalArgumentException.class, () -> clip(withCue, identity(), 1));
        VisualClip.Transform wide = new VisualClip.Transform(0, 0, 0, 0, 0, 0, 1, 16, 2, 1);
        VisualPlan.compile(clip(fixture().scalar, wide, 1));
        expect(
            IllegalArgumentException.class,
            () -> clip(
                scalar(Collections.singletonList(track("scale", AnimationV1.Property.SCALE, 1, 2.0001))),
                wide,
                1));
        for (int i = 0; i < 100; i++) {
            targets.add(new VisualClip.Target("box-" + i, "paint-" + i, identity()));
            materials.add(new VisualClip.Material("paint-" + i, i, 1));
        }
        VisualClip max = new VisualClip(copy.scalar, targets, materials);
        check(sample(VisualPlan.compile(max), 0).targets.size() == 100, "100 targets and materials supported");
        targets.add(new VisualClip.Target("extra", "paint-0", identity()));
        expect(IllegalArgumentException.class, () -> new VisualClip(copy.scalar, targets, materials));
        materials.add(new VisualClip.Material("extra", 0, 1));
        expect(IllegalArgumentException.class, () -> new VisualClip(copy.scalar, copy.targets, materials));
        VisualClip empty = new VisualClip(
            copy.scalar,
            Collections.<VisualClip.Target>emptyList(),
            Collections.<VisualClip.Material>emptyList());
        check(sample(VisualPlan.compile(empty), 0).targets.isEmpty(), "empty authoring clip is explicit");
    }

    private static void evaluation() {
        double axis = StrictMath.sqrt(.5);
        VisualClip.Transform base = new VisualClip.Transform(42, 2, 3, axis, 0, 0, axis, 2, 3, 4);
        AnimationV1.Document scalar = scalar(
            Arrays.asList(
                track("x", AnimationV1.Property.X, 0, 10),
                track("scale", AnimationV1.Property.SCALE, 1, 2),
                track("rotate", AnimationV1.Property.ROTATION_DEGREES, 0, 180),
                track("fade", AnimationV1.Property.OPACITY, 0, 1)));
        VisualClip clip = clip(scalar, base, .8);
        VisualPlan plan = VisualPlan.compile(clip);
        VisualFrame frame = sample(plan, 500);
        VisualFrame.TargetPose pose = frame.targets.get(0);
        check(frame.sampledFrom(clip) && frame.localTimeMs == 500, "complete definition provenance");
        near(5, pose.x, "X replaces base, never adds to 42");
        near(2, pose.y, "unbound Y uses static pose");
        near(3, pose.z, "unbound Z uses static pose");
        near(3, pose.scaleX, "uniform multiplier retains nonuniform base X");
        near(4.5, pose.scaleY, "uniform multiplier retains nonuniform base Y");
        near(6, pose.scaleZ, "uniform multiplier retains nonuniform base Z");
        near(.4, pose.opacity, "opacity multiplies material alpha");
        check(pose.visible && pose.rgb == 0x33aaff && pose.materialId.equals("paint"), "material binding resolved");
        double[] matrix = pose.matrix();
        // Independent sequential operation: S(1,2,3), local Z90, base X90, then translate (5,2,3).
        near(-4, matrix[0] + 2 * matrix[4] + 3 * matrix[8] + matrix[12], "TRS point X");
        near(-16, matrix[1] + 2 * matrix[5] + 3 * matrix[9] + matrix[13], "TRS point Y");
        near(6, matrix[2] + 2 * matrix[6] + 3 * matrix[10] + matrix[14], "TRS point Z");
        near(1, matrix[15], "affine homogeneous component");
        matrix[12] = 123;
        near(5, pose.matrix()[12], "matrix is an isolated copy");
        expect(UnsupportedOperationException.class, () -> frame.targets.clear());
        expect(IllegalArgumentException.class, () -> plan.evaluate(Sampler.sample(fixture().scalar, 500)));
        expect(IllegalArgumentException.class, () -> plan.evaluate(null));

        VisualClip newMaterial = new VisualClip(
            clip.scalar,
            clip.targets,
            Collections.singletonList(new VisualClip.Material("paint", 0xff0000, 1)));
        check(!frame.sampledFrom(newMaterial), "same scalar with new material is a different complete definition");
        check(!plan.contentHash.equals(VisualPlan.compile(newMaterial).contentHash), "full digest includes material");
        check(
            VisualPlan.compile(newMaterial)
                .evaluate(Sampler.sample(clip.scalar, 500))
                .sampledFrom(newMaterial),
            "shared scalar can bind explicitly to another full clip");
        check(!sample(plan, 0).targets.get(0).visible, "zero alpha suppresses drawing");
        check(
            sample(plan, Long.MIN_VALUE).localTimeMs == 0 && sample(plan, Long.MAX_VALUE).localTimeMs == 1000,
            "scalar endpoints retained");
        VisualPlan zeroScale = VisualPlan.compile(
            clip(scalar(Collections.singletonList(track("size", AnimationV1.Property.SCALE, 0, 0))), identity(), 1));
        check(!sample(zeroScale, 0).targets.get(0).visible, "zero animated scale suppresses drawing");
        VisualPlan underflowScale = VisualPlan.compile(
            clip(
                scalar(Collections.singletonList(track("size", AnimationV1.Property.SCALE, .5, .5))),
                new VisualClip.Transform(0, 0, 0, 0, 0, 0, 1, Double.MIN_VALUE, 1, 1),
                1));
        check(
            !sample(underflowScale, 0).targets.get(0).visible,
            "effective zero axis suppresses drawing after underflow");
        AnimationV1.Track hidden = new AnimationV1.Track(
            "hide",
            "box",
            AnimationV1.Property.VISIBILITY,
            Collections.singletonList(new AnimationV1.Key(0, 0, AnimationV1.Interpolation.STEP)));
        check(
            !sample(VisualPlan.compile(clip(scalar(Collections.singletonList(hidden)), identity(), 1)), 0).targets
                .get(0).visible,
            "visibility STEP controls draw only");
        for (long time : new long[] { 800, 10, 1000, 500, 0, 800 }) {
            double[] first = sample(plan, time).targets.get(0)
                .matrix();
            double[] second = sample(plan, time).targets.get(0)
                .matrix();
            check(Arrays.equals(first, second), "seek/reverse/export are deterministic and stateless");
        }
        VisualClip roundoff = clip(
            scalar(Collections.<AnimationV1.Track>emptyList()),
            new VisualClip.Transform(0, 0, 0, 0, 0, 0, 1.000001, 1, 1, 1),
            1);
        near(
            1,
            sample(VisualPlan.compile(roundoff), 0).targets.get(0).qw,
            "accepted storage roundoff normalized on output");
        check(
            Arrays.equals(
                VisualClipCodec.encode(roundoff),
                VisualClipCodec.encode(VisualClipCodec.decode(VisualClipCodec.encode(roundoff)))),
            "stored quaternion not repeatedly normalized");
    }

    private static void codec() throws Exception {
        VisualClip original = fixture();
        byte[] bytes = VisualClipCodec.encode(original);
        String base = new String(bytes, StandardCharsets.UTF_8);
        VisualClip decoded = VisualClipCodec.decode(bytes);
        check(decoded.scalar.seed == Long.MIN_VALUE, "long seed exact inside envelope");
        check(Arrays.equals(bytes, VisualClipCodec.encode(decoded)), "visual canonical roundtrip");
        check(
            VisualClipCodec.contentHash(original)
                .equals(VisualClipCodec.contentHash(decoded)),
            "visual digest stable");
        byte[] golden = Files.readAllBytes(Paths.get("src/test/resources/animation/solid-box.visual-v1.json"));
        check(Arrays.equals(golden, bytes), "golden visual fixture");
        check(
            VisualClipCodec.contentHash(original)
                .equals("35618adb48a83ec2b5f4cff33389a35f81bd3c68694bf5bb5d93e1d20d54734b"),
            "golden visual digest");
        VisualPlan a = VisualPlan.compile(original), b = VisualPlan.compile(decoded);
        for (int t = 0; t <= 1000; t += 37) check(
            Arrays.equals(
                sample(a, t).targets.get(0)
                    .matrix(),
                sample(b, t).targets.get(0)
                    .matrix()),
            "actual codec roundtrip sampling");
        rejected(
            "DUPLICATE_KEY",
            base.replace("\"materialId\":\"paint\"", "\"materialId\":\"paint\",\"\\u006daterialId\":\"paint\""));
        rejected(
            "DUPLICATE_KEY",
            base.replace("\"seed\":-9223372036854775808", "\"seed\":-9223372036854775808,\"\\u0073eed\":0"));
        rejected("INTEGER_RANGE", base.replace("-9223372036854775808", "-9223372036854775809"));
        rejected("INTEGER", base.replace("\"durationMs\":1000", "\"durationMs\":1e3"));
        rejected("UNKNOWN_FIELD", base.replace("\"targets\":", "\"nodes\":[],\"targets\":"));
        rejected("UNKNOWN_FIELD", base.replace("\"materialId\":", "\"parentId\":\"box\",\"materialId\":"));
        rejected("UNKNOWN_FIELD", base.replace("\"rgb\":", "\"texture\":\"core:any\",\"rgb\":"));
        rejected("UNKNOWN_FIELD", base.replace("\"interpolation\":", "\"keyId\":\"k1\",\"interpolation\":"));
        rejected("UNSUPPORTED", base.replace("SOLID_BOX", "RING"));
        rejected("UNSUPPORTED", base.replace("SOLID_COLOR_UNLIT", "TEXTURED"));
        rejected("FORMAT", base.replace("core.animation.visual", "research.visual"));
        rejected("MAJOR", base.replaceFirst("\"major\":1", "\"major\":2"));
        rejected("MINOR", base.replaceFirst("\"minor\":0", "\"minor\":1"));
        rejected("UNSUPPORTED", base.replace("\"property\":\"X\"", "\"property\":\"BRIGHTNESS\""));
        rejected("UNSUPPORTED", base.replace("\"resources\":[]", "\"resources\":[\"core:texture\"]"));
        rejected(
            "UNSUPPORTED",
            base.replace(
                "\"cues\":[]",
                "\"cues\":[{\"id\":\"cue\",\"timeMs\":0,"
                    + "\"kind\":\"CAMERA_IMPULSE\",\"latePolicy\":\"DROP\",\"validForMs\":0,\"resource\":null}]"));
        rejected("CLIP", base.replace("\"materialId\":\"paint\"", "\"materialId\":\"absent\""));
        rejected("CLIP", base.replace("\"targetId\":\"box\"", "\"targetId\":\"absent\""));
        rejected("TRANSFORM", base.replace("\"scale\":[1.0,1.0,1.0]", "\"scale\":[0,1,1]"));
        rejected(
            "TRANSFORM",
            base.replace("\"rotationQuaternion\":[0.0,0.0,0.0,1.0]", "\"rotationQuaternion\":[0,0,0,0]"));
        rejected("COUNT", base.replace("\"position\":[0.0,0.0,0.0]", "\"position\":[0,0]"));
        rejected("FINITE", base.replace("\"position\":[0.0,0.0,0.0]", "\"position\":[1e309,0,0]"));
        rejected("INTEGER", base.replace("\"rgb\":3386111", "\"rgb\":3e6"));
        rejected("RANGE", base.replace("\"rgb\":3386111", "\"rgb\":4294967296"));
        rejected("TRAILING", base + " false");
        try {
            VisualClipCodec.decode(
                base.replace("\"durationMs\":1000", "\"durationMs\":1e3")
                    .getBytes(StandardCharsets.UTF_8));
            throw new AssertionError("invalid nested scalar accepted");
        } catch (StrictJsonCodec.CodecException invalid) {
            check(invalid.path.equals("$.scalar.durationMs"), "nested scalar error path preserved");
        }
        // Strict V0 continues rejecting every visual envelope rather than guessing its embedded type.
        expect(StrictJsonCodec.CodecException.class, () -> StrictJsonCodec.decode(bytes));
        expect(
            StrictJsonCodec.CodecException.class,
            () -> VisualClipCodec.decode(StrictJsonCodec.encode(original.scalar)));
    }

    private static void parserAndStreamLimits() throws Exception {
        byte[] encoded = VisualClipCodec.encode(fixture());
        String base = new String(encoded, StandardCharsets.UTF_8);
        rejectedBytes("UTF8", new byte[] { (byte) 0xc3, 0x28 });
        rejected("DEPTH", repeat("[", 33) + "0" + repeat("]", 33));
        rejected("STRING_LIMIT", "{\"" + repeat("x", 513) + "\":0}");
        rejected("NUMBER_LIMIT", repeat("1", 33));
        rejected("ARRAY_LIMIT", "[" + repeat("0,", 65536) + "0]");
        String block = "[" + repeat("0,", 65535) + "0]";
        rejected("VALUES", "[" + block + "," + block + "," + block + "," + block + "]");
        rejected(
            "COUNT",
            base.substring(0, base.indexOf("\"targets\":")) + "\"targets\":["
                + repeat("null,", 100)
                + "null],\"materials\":[]}");
        rejected(
            "COUNT",
            base.substring(0, base.indexOf("\"materials\":")) + "\"materials\":[" + repeat("null,", 100) + "null]}");
        byte[] boundary = new byte[VisualClipCodec.MAX_BYTES];
        Arrays.fill(boundary, (byte) ' ');
        System.arraycopy(encoded, 0, boundary, 0, encoded.length);
        check(
            VisualClipCodec.decode(new ByteArrayInputStream(boundary)).scalar.seed == Long.MIN_VALUE,
            "2 MiB stream inclusive");
        rejectedBytes("BYTE_LIMIT", Arrays.copyOf(boundary, boundary.length + 1));
        final int[] consumed = { 0 };
        final boolean[] closed = { false };
        InputStream infinite = new InputStream() {

            @Override
            public int read() {
                consumed[0]++;
                return ' ';
            }

            @Override
            public int read(byte[] bytes, int offset, int length) {
                Arrays.fill(bytes, offset, offset + length, (byte) ' ');
                consumed[0] += length;
                return length;
            }

            @Override
            public void close() {
                closed[0] = true;
            }
        };
        try {
            VisualClipCodec.decode(infinite);
            throw new AssertionError("unbounded source accepted");
        } catch (StrictJsonCodec.CodecException invalid) {
            check(
                invalid.code.equals("BYTE_LIMIT") && consumed[0] == VisualClipCodec.MAX_BYTES + 1,
                "stream rejects before DTO creation after one probe byte");
            check(!closed[0], "caller retains stream ownership");
        }
        IOException failure = new IOException("source read failed");
        try {
            VisualClipCodec.decode(new InputStream() {

                @Override
                public int read() throws IOException {
                    throw failure;
                }
            });
            throw new AssertionError("I/O error hidden");
        } catch (IOException invalid) {
            check(invalid == failure, "I/O failure preserved");
        }
    }

    private static void portableBudget() {
        List<AnimationV1.Key> keys = new ArrayList<>();
        for (int i = 0; i < 4096; i++) keys.add(new AnimationV1.Key(i, .5, AnimationV1.Interpolation.LINEAR));
        List<AnimationV1.Track> tracks = new ArrayList<>();
        List<VisualClip.Target> targets = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            String id = "box-" + i;
            targets.add(new VisualClip.Target(id, "paint", identity()));
            tracks.add(new AnimationV1.Track("alpha-" + i, id, AnimationV1.Property.OPACITY, keys));
        }
        AnimationV1.Document scalar = new AnimationV1.Document(
            "core:visual/oversize",
            0,
            4096,
            0,
            tracks,
            Collections.<AnimationV1.Cue>emptyList(),
            Collections.<ResourceId>emptySet());
        VisualClip large = new VisualClip(
            scalar,
            targets,
            Collections.singletonList(new VisualClip.Material("paint", 0, 1)));
        expect(StrictJsonCodec.CodecException.class, () -> VisualPlan.compile(large));

        VisualClip original = fixture();
        VisualPlan previous = VisualPlan.compile(original);
        try {
            VisualPlan rejected = VisualPlan.compile(large);
            throw new AssertionError("oversize plan accepted " + rejected);
        } catch (StrictJsonCodec.CodecException invalid) {
            check(
                previous.clip == original && sample(previous, 0).sampledFrom(original),
                "failed candidate cannot mutate immutable old plan");
        }
        List<VisualClip.Target> ordered = Arrays
            .asList(new VisualClip.Target("z", "b", identity()), new VisualClip.Target("a", "a", identity()));
        List<VisualClip.Material> materials = Arrays
            .asList(new VisualClip.Material("b", 2, 1), new VisualClip.Material("a", 1, 1));
        VisualClip first = new VisualClip(scalar(Collections.<AnimationV1.Track>emptyList()), ordered, materials);
        VisualClip reverse = new VisualClip(
            first.scalar,
            Arrays.asList(ordered.get(1), ordered.get(0)),
            Arrays.asList(materials.get(1), materials.get(0)));
        check(
            Arrays.equals(VisualClipCodec.encode(first), VisualClipCodec.encode(reverse)),
            "canonical target/material order");
        check(
            sample(VisualPlan.compile(first), 0).targets.get(0).targetId.equals("a"),
            "frame uses stable target order");
    }

    private static void rejected(String code, String text) {
        rejectedBytes(code, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void rejectedBytes(String code, byte[] bytes) {
        try {
            VisualClipCodec.decode(bytes);
            throw new AssertionError("accepted visual input expecting " + code);
        } catch (StrictJsonCodec.CodecException invalid) {
            check(code.equals(invalid.code), "expected " + code + ", got " + invalid.code);
        }
    }

    private static String repeat(String value, int count) {
        StringBuilder out = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) out.append(value);
        return out.toString();
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable failure) {
            check(type.isInstance(failure), "expected " + type.getName() + ", got " + failure);
            return;
        }
        throw new AssertionError("expected " + type.getName());
    }

    private static void near(double expected, double actual, String label) {
        check(Double.isFinite(actual) && Math.abs(expected - actual) < 1e-12, label + ": " + actual);
    }

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
}
