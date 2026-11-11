// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.api.animation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Executable production contracts. Assertions remain active without the JVM -ea
 * flag.
 */
public final class AnimationContracts {

    private static int checks;

    private AnimationContracts() {}

    public static void main(String[] args) throws Exception {
        sampling();
        modelLimits();
        codec();
        parserLimits();
        streamLimit();
        instances();
        events();
        eventClocks();
        timebase();
        migration();
        System.out
            .println("AnimationContracts: PASS (" + checks + " checks; scalar, strict codec, plans, events, timebase)");
    }

    private static AnimationV1.Key key(long time, double value) {
        return new AnimationV1.Key(time, value, AnimationV1.Interpolation.LINEAR);
    }

    private static AnimationV1.Cue cue(String id, long time, AnimationV1.LatePolicy policy, long ttl) {
        return new AnimationV1.Cue(id, time, AnimationV1.CueKind.CAMERA_IMPULSE, policy, ttl, null);
    }

    private static AnimationV1.Document document(long duration, List<AnimationV1.Track> tracks,
        List<AnimationV1.Cue> cues) {
        return new AnimationV1.Document(
            "core:test/animation",
            0,
            duration,
            Long.MAX_VALUE,
            tracks,
            cues,
            Collections.<ResourceId>emptySet());
    }

    private static AnimationV1.Document fixture() {
        return document(
            1000,
            Collections.singletonList(
                new AnimationV1.Track(
                    "alpha",
                    "ring",
                    AnimationV1.Property.OPACITY,
                    Arrays.asList(key(0, 0), key(1000, 1)))),
            Collections.singletonList(cue("start", 0, AnimationV1.LatePolicy.DROP, 0)));
    }

    private static void sampling() {
        Random random = new Random(0x511ceL);
        for (int trial = 0; trial < 40; trial++) {
            List<AnimationV1.Key> keys = new ArrayList<>();
            long end = 0;
            for (int i = 0; i < 30; i++) {
                end += 1 + random.nextInt(100);
                keys.add(new AnimationV1.Key(end, random.nextDouble(), AnimationV1.Interpolation.values()[i % 3]));
            }
            AnimationV1.Track track = new AnimationV1.Track("alpha", "ring", AnimationV1.Property.OPACITY, keys);
            AnimationV1.Document doc = document(
                end + 10,
                Collections.singletonList(track),
                Collections.<AnimationV1.Cue>emptyList());
            for (int sample = 0; sample < 100; sample++) {
                long requested = random.nextInt((int) end + 100) - 50;
                long t = Math.max(0, Math.min(doc.durationMs, requested));
                double expected = keys.get(0).value;
                // Independent forward scan, compared with the production binary search.
                for (int i = 0; i < keys.size(); i++) {
                    AnimationV1.Key a = keys.get(i);
                    if (t < a.timeMs) break;
                    expected = a.value;
                    if (i + 1 < keys.size() && t < keys.get(i + 1).timeMs) {
                        AnimationV1.Key b = keys.get(i + 1);
                        double u = (t - a.timeMs) / (double) (b.timeMs - a.timeMs);
                        if (a.interpolation == AnimationV1.Interpolation.SMOOTHSTEP) u = u * u * (3 - 2 * u);
                        if (a.interpolation != AnimationV1.Interpolation.STEP)
                            expected = a.value * (1 - u) + b.value * u;
                        break;
                    }
                }
                SampledFrame frame = Sampler.sample(doc, requested);
                check(Math.abs(expected - frame.value("alpha")) < 1e-14, "independent interpolation oracle");
                check(frame.sampledFrom(doc) && frame.localTimeMs == t, "exact definition and clamp");
                check(frame.valuesByTrack.equals(Sampler.sample(doc, requested).valuesByTrack), "repeat deterministic");
            }
            keys.clear();
            check(track.keys.size() == 30, "keys defensively copied");
            AnimationV1.Document other = document(doc.durationMs, doc.tracks, doc.cues);
            check(
                !Sampler.sample(doc, 0)
                    .sampledFrom(other),
                "logical ID is not exact definition");
        }
        AnimationV1.Document doc = fixture();
        check(
            Sampler.sample(doc, Long.MIN_VALUE)
                .value("alpha") == 0,
            "negative time clamps");
        check(
            Sampler.sample(doc, Long.MAX_VALUE)
                .value("alpha") == 1,
            "terminal pose retained");
        expect(
            IllegalArgumentException.class,
            () -> Sampler.sample(doc, 0)
                .value("missing"));
        expect(UnsupportedOperationException.class, () -> Sampler.sample(doc, 0).valuesByTrack.clear());
        // Fixed independent vectors for the retained mod-2^64 random algorithm.
        check(
            Double.doubleToLongBits(Sampler.randomUnit(0, "particles", 0)) == 0x3fd8260c63c2f746L,
            "random golden seed 0");
        check(
            Double.doubleToLongBits(Sampler.randomUnit(Long.MAX_VALUE, "particles", 7)) == 0x3fe3d008006d5a6dL,
            "random golden long seed");
        expect(IllegalArgumentException.class, () -> Sampler.randomUnit(1, "bad/id", 0));
        expect(IllegalArgumentException.class, () -> Sampler.randomUnit(1, "valid", -1));
    }

    private static void modelLimits() {
        expect(
            IllegalArgumentException.class,
            () -> new AnimationV1.Key(0, Double.NaN, AnimationV1.Interpolation.LINEAR));
        expect(
            IllegalArgumentException.class,
            () -> new AnimationV1.Key(0, Double.POSITIVE_INFINITY, AnimationV1.Interpolation.LINEAR));
        expect(
            IllegalArgumentException.class,
            () -> new AnimationV1.Track(
                "x",
                "ring",
                AnimationV1.Property.OPACITY,
                Arrays.asList(key(0, 0), key(0, 1))));
        expect(
            IllegalArgumentException.class,
            () -> new AnimationV1.Track(
                "x",
                "ring",
                AnimationV1.Property.VISIBILITY,
                Collections.singletonList(key(0, 1))));
        expect(
            IllegalArgumentException.class,
            () -> new AnimationV1.Track(
                "x",
                "ring",
                AnimationV1.Property.VISIBILITY,
                Collections.singletonList(new AnimationV1.Key(0, .5, AnimationV1.Interpolation.STEP))));
        expect(
            IllegalArgumentException.class,
            () -> new AnimationV1.Track(
                "x",
                "ring",
                AnimationV1.Property.FOV_DEGREES,
                Collections.singletonList(key(0, 9))));
        AnimationV1.Document valid = fixture();
        expect(
            IllegalArgumentException.class,
            () -> document(
                1000,
                Arrays.asList(
                    valid.tracks.get(0),
                    new AnimationV1.Track(
                        "other",
                        "ring",
                        AnimationV1.Property.OPACITY,
                        Collections.singletonList(key(0, 0)))),
                valid.cues));
        expect(IllegalArgumentException.class, () -> document(999, valid.tracks, valid.cues));
        expect(
            IllegalArgumentException.class,
            () -> document(
                1000,
                valid.tracks,
                Collections.singletonList(
                    new AnimationV1.Cue(
                        "sound",
                        0,
                        AnimationV1.CueKind.SOUND,
                        AnimationV1.LatePolicy.DROP,
                        0,
                        ResourceId.parse("core:missing")))));
        String[] invalidIds = { "http://bad/x", "core:../x", "core:/x", "core:x//y", "core:x/./y", "core:x\\y",
            "Core:x", "core:x/", "core:./x", "core:x#z" };
        for (String id : invalidIds) expect(IllegalArgumentException.class, () -> ResourceId.parse(id));
        check(
            ResourceId.parse("core:a/b")
                .equals(ResourceId.parse("core:a/b")),
            "resource value identity");

        List<AnimationV1.Key> keys = new ArrayList<>();
        for (int i = 0; i < 4096; i++) keys.add(key(i, .5));
        AnimationV1.Track fullTrack = new AnimationV1.Track("full", "target", AnimationV1.Property.OPACITY, keys);
        keys.add(key(4096, .5));
        expect(
            IllegalArgumentException.class,
            () -> new AnimationV1.Track("over", "target", AnimationV1.Property.OPACITY, keys));
        List<AnimationV1.Track> tracks = new ArrayList<>();
        for (int i = 0; i < 16; i++) tracks
            .add(new AnimationV1.Track("track-" + i, "target-" + i, AnimationV1.Property.OPACITY, fullTrack.keys));
        AnimationV1.Document maxKeys = document(4096, tracks, Collections.<AnimationV1.Cue>emptyList());
        check(maxKeys.tracks.size() == 16, "65536 total keys admitted by model");
        expect(StrictJsonCodec.CodecException.class, () -> CompiledPlan.compile(maxKeys));
        tracks.add(
            new AnimationV1.Track(
                "extra",
                "extra",
                AnimationV1.Property.OPACITY,
                Collections.singletonList(key(0, 0))));
        expect(IllegalArgumentException.class, () -> document(4096, tracks, Collections.<AnimationV1.Cue>emptyList()));

        List<AnimationV1.Track> count = new ArrayList<>();
        for (int i = 0; i < 512; i++) count.add(
            new AnimationV1.Track(
                "t" + i,
                "n" + i,
                AnimationV1.Property.OPACITY,
                Collections.singletonList(key(0, 0))));
        check(
            document(1, count, Collections.<AnimationV1.Cue>emptyList()).tracks.size() == 512,
            "track limit inclusive");
        count.add(
            new AnimationV1.Track(
                "extra",
                "extra",
                AnimationV1.Property.OPACITY,
                Collections.singletonList(key(0, 0))));
        expect(IllegalArgumentException.class, () -> document(1, count, Collections.<AnimationV1.Cue>emptyList()));
    }

    private static void codec() throws Exception {
        AnimationV1.Document original = fixture();
        String base = new String(StrictJsonCodec.encode(original), StandardCharsets.UTF_8);
        for (long seed : new long[] { Long.MIN_VALUE, Long.MAX_VALUE, 9007199254740993L, 0 }) {
            AnimationV1.Document decoded = decode(base.replace(Long.toString(Long.MAX_VALUE), Long.toString(seed)));
            check(decoded.seed == seed, "integer seed never passes through double");
            byte[] encoded = StrictJsonCodec.encode(decoded);
            check(
                Arrays.equals(encoded, StrictJsonCodec.encode(StrictJsonCodec.decode(encoded))),
                "canonical bytes stable");
            check(
                StrictJsonCodec.contentHash(decoded)
                    .equals(StrictJsonCodec.contentHash(StrictJsonCodec.decode(encoded))),
                "digest stable");
        }
        byte[] golden = Files.readAllBytes(Paths.get("src/test/resources/animation/decorative-ring.v0.json"));
        AnimationV1.Document fixture = StrictJsonCodec.decode(golden);
        check(Arrays.equals(golden, StrictJsonCodec.encode(fixture)), "golden canonical fixture");
        check(
            StrictJsonCodec.contentHash(fixture)
                .equals("cb2546d02326dff010359f8bd142e270a9b56e505f00a8f8d34136bdd3ffd810"),
            "golden digest");
        for (long t : new long[] { 0, 200, 600, 1200 }) {
            check(
                Sampler.sample(fixture, t).valuesByTrack
                    .equals(Sampler.sample(StrictJsonCodec.decode(golden), t).valuesByTrack),
                "fixture sampling after actual decode");
        }

        rejected(
            "DUPLICATE_KEY",
            base.replace(
                "\"id\":\"core:test/animation\"",
                "\"id\":\"core:test/animation\",\"\\u0069d\":\"core:other\""));
        rejected("INTEGER", base.replace("\"durationMs\":1000", "\"durationMs\":1e3"));
        rejected("FINITE", base.replace("\"value\":0.0", "\"value\":1e309"));
        rejected("NUMBER", base.replace("\"value\":0.0", "\"value\":NaN"));
        rejected("NUMBER", base.replace("\"value\":0.0", "\"value\":01"));
        rejected("NUMBER", base.replace("\"value\":0.0", "\"value\":+1"));
        rejected("INTEGER_RANGE", base.replace("9223372036854775807", "9223372036854775808"));
        rejected("INTEGER_RANGE", base.replace("9223372036854775807", "-9223372036854775809"));
        rejected("TYPE", base.replace("\"schemaVersion\":{\"major\":1,\"minor\":0}", "\"schemaVersion\":true"));
        rejected("MAJOR", base.replace("\"major\":1", "\"major\":2"));
        rejected("MINOR", base.replace("\"minor\":0", "\"minor\":1"));
        rejected("UNKNOWN_FIELD", base.replace("\"tracks\":", "\"nodes\":[],\"tracks\":"));
        rejected("FORMAT", base.replace("core.animation.scalar", "photon.research"));
        rejected("MISSING_FIELD", base.replace("\"resources\":[],", ""));
        rejected("TRAILING", base + " true");
        rejected("ESCAPE", base.replace("core:test/animation", "core:test/\\u００６１"));
        rejected("SURROGATE", base.replace("core:test/animation", "\\ud800"));
        rejected("SURROGATE", base.replace("core:test/animation", "\\udc00"));
        rejected("CONTROL", base.replace("core:test/animation", "core:test\nname"));
        rejectedBytes("UTF8", new byte[] { (byte) 0xc3, 0x28 });
        rejectedBytes("UTF8", new byte[] { (byte) 0xed, (byte) 0xa0, (byte) 0x80 });
        rejected("NUMBER", "\ufeff" + base);
        try {
            decode(base.replace("\"value\":0.0", "\"value\":1e309"));
            throw new AssertionError("finite accepted");
        } catch (StrictJsonCodec.CodecException error) {
            check(error.path.equals("$.tracks[0].keys[0].value"), "precise finite path");
        }
    }

    private static void parserLimits() {
        rejected("TYPE", repeat("[", 32) + "0" + repeat("]", 32));
        rejected("DEPTH", repeat("[", 33) + "0" + repeat("]", 33));
        rejected("UNKNOWN_FIELD", "{\"" + repeat("x", 512) + "\":0}");
        rejected("STRING_LIMIT", "{\"" + repeat("x", 513) + "\":0}");
        rejected("TYPE", repeat("1", 32));
        rejected("NUMBER_LIMIT", repeat("1", 33));
        rejected("TYPE", "[" + repeat("0,", 65535) + "0]");
        rejected("ARRAY_LIMIT", "[" + repeat("0,", 65536) + "0]");
        String block = "[" + repeat("0,", 65535) + "0]";
        rejected("VALUES", "[" + block + "," + block + "," + block + "," + block + "]");
        String base = new String(StrictJsonCodec.encode(fixture()), StandardCharsets.UTF_8);
        rejected(
            "COUNT",
            base.replace("\"resources\":[]", "\"resources\":[" + repeat("\"core:x\",", 256) + "\"core:x\"]"));
        rejected("DUPLICATE_RESOURCE", base.replace("\"resources\":[]", "\"resources\":[\"core:x\",\"core:x\"]"));
        rejected(
            "COUNT",
            base.substring(0, base.indexOf("\"tracks\":")) + "\"tracks\":["
                + repeat("null,", 512)
                + "null],\"cues\":[]}");
        rejected(
            "COUNT",
            base.substring(0, base.indexOf("\"cues\":")) + "\"cues\":[" + repeat("null,", 128) + "null]}");
        String longPath = "{\"" + repeat("x", 512)
            + "\":{\""
            + repeat("y", 512)
            + "\":0,\""
            + repeat("y", 512)
            + "\":0}}";
        try {
            decode(longPath);
            throw new AssertionError("duplicate accepted");
        } catch (StrictJsonCodec.CodecException error) {
            check(
                error.path.length() <= 515 && error.getMessage()
                    .length() < 820,
                "diagnostic bounded");
        }
    }

    private static void streamLimit() throws Exception {
        byte[] encoded = StrictJsonCodec.encode(fixture());
        byte[] boundary = new byte[StrictJsonCodec.MAX_BYTES];
        Arrays.fill(boundary, (byte) ' ');
        System.arraycopy(encoded, 0, boundary, 0, encoded.length);
        check(StrictJsonCodec.decode(new ByteArrayInputStream(boundary)).seed == Long.MAX_VALUE, "2 MiB accepted");
        check(
            StrictJsonCodec.decode(Arrays.copyOf(boundary, boundary.length - 1)).seed == Long.MAX_VALUE,
            "2 MiB minus one accepted");
        rejectedBytes("BYTE_LIMIT", Arrays.copyOf(boundary, boundary.length + 1));
        CountingStream endless = new CountingStream();
        try {
            StrictJsonCodec.decode(endless);
            throw new AssertionError("unbounded stream accepted");
        } catch (StrictJsonCodec.CodecException error) {
            check(error.code.equals("BYTE_LIMIT"), "stream rejected before parsing");
            check(
                endless.read == StrictJsonCodec.MAX_BYTES + 1 && !endless.closed,
                "bounded read and caller ownership");
        }
        IOException failure = new IOException("source failed");
        try {
            StrictJsonCodec.decode(new InputStream() {

                @Override
                public int read() throws IOException {
                    throw failure;
                }
            });
            throw new AssertionError("I/O failure hidden");
        } catch (IOException error) {
            check(error == failure, "I/O error preserved");
        }
    }

    private static void instances() throws Exception {
        CompiledPlan plan = CompiledPlan.compile(fixture());
        AnimationInstance instance = new AnimationInstance("preview", 7, plan);
        for (long time : new long[] { 0, 1000, 100, -1, Long.MAX_VALUE, 0 }) {
            check(
                instance.evaluate(new EvaluationInput(time)).valuesByTrack
                    .equals(Sampler.sample(plan.document, time).valuesByTrack),
                "instance seek/reverse/export equals direct pure sample");
        }
        try {
            instance.replaceFrom(new ByteArrayInputStream("{\"nodes\":[]}".getBytes(StandardCharsets.UTF_8)));
            throw new AssertionError("bad replacement accepted");
        } catch (StrictJsonCodec.CodecException expected) {
            check(instance.plan() == plan, "rejected import retains exact previous plan");
        }
        instance.replaceFrom(new ByteArrayInputStream(StrictJsonCodec.encode(fixture())));
        check(
            instance.plan() != plan && instance.plan().contentHash.equals(plan.contentHash),
            "valid replacement published");
        check(
            !instance.evaluate(new EvaluationInput(0))
                .sampledFrom(plan.document),
            "replace preserves exact provenance");
        CompiledPlan beforeReentry = instance.plan();
        try {
            instance.replaceFrom(new ByteArrayInputStream(StrictJsonCodec.encode(fixture())) {

                @Override
                public synchronized int read(byte[] buffer, int offset, int length) {
                    instance.replace(plan);
                    return super.read(buffer, offset, length);
                }
            });
            throw new AssertionError("reentrant replacement accepted");
        } catch (IllegalStateException expected) {
            check(instance.plan() == beforeReentry, "reentrant replace rejected without overwriting active plan");
        }
        instance.replace(plan);
        check(instance.plan() == plan, "failed preparation clears busy state");
        AnimationInstance closing = new AnimationInstance("closing", 1, plan);
        try {
            closing.replaceFrom(new ByteArrayInputStream(StrictJsonCodec.encode(fixture())) {

                @Override
                public synchronized int read(byte[] buffer, int offset, int length) {
                    closing.close();
                    return super.read(buffer, offset, length);
                }
            });
            throw new AssertionError("publication after close");
        } catch (IllegalStateException expected) {
            expect(IllegalStateException.class, () -> closing.plan());
        }
        AtomicReference<Throwable> wrong = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                instance.evaluate(new EvaluationInput(0));
            } catch (Throwable error) {
                wrong.set(error);
            }
        });
        worker.start();
        worker.join();
        check(wrong.get() instanceof IllegalStateException, "instance owner thread");
        instance.close();
        instance.close();
        expect(IllegalStateException.class, () -> instance.plan());
        expect(IllegalStateException.class, () -> instance.replace(plan));
    }

    private static EventCursor cursor(AnimationV1.Document doc, boolean looping, EventBudget owner) {
        return new EventCursor(new EpochKey(1, 2, 3), "preview", 7, doc, looping, new EventBudget(128, 4096), owner);
    }

    private static void events() {
        AnimationV1.Document doc = document(
            1000,
            Collections.<AnimationV1.Track>emptyList(),
            Arrays.asList(
                cue("zero", 0, AnimationV1.LatePolicy.LATEST, 100),
                cue("half", 500, AnimationV1.LatePolicy.LATEST, 100)));
        EventCursor cursor = cursor(doc, true, new EventBudget(128, 4096));
        check(cursor.start(0, true, 0, 0).events.size() == 1, "fresh t0");
        check(cursor.advance(0, 0, 0).events.isEmpty(), "t0 not duplicated");
        check(cursor.advance(500, 1, 500).events.size() == 1, "midpoint cue");
        check(cursor.advance(1000, 2, 1000).events.get(0).loopOccurrence == 1, "loop occurrence at D");
        cursor.seekSilently(0);
        check(cursor.advance(500, 3, 1500).events.isEmpty(), "reverse never replays");
        cursor.seekSilently(1800);
        check(cursor.advance(1800, 4, 1800).events.isEmpty(), "forward seek silent");
        check(cursor.advance(2000, 5, 2000).events.size() == 1, "future event after seek");
        EventCursor.Dispatch huge = cursor.advance(1000000000L, 6, 3000);
        check(huge.scanned == 2 && huge.events.size() == 1, "million loops bounded by cue count");
        check(
            huge.events.get(0).loopOccurrence == 1000000L && huge.events.get(0).playbackGeneration == 7,
            "loop preserves session generation");
        cursor.close();
        expect(IllegalArgumentException.class, () -> cursor.advance(2000000000L, 7, 4000));
        EventCursor late = cursor(doc, true, new EventBudget(128, 4096));
        check(late.start(1050, false, 0, 0).events.size() == 1, "late latest only");
        EventCursor expired = cursor(doc, false, new EventBudget(128, 4096));
        EventCursor.Dispatch expiry = expired.start(1000, false, 0, 0);
        check(expiry.events.isEmpty() && expiry.expired == 2, "age evaluated before t0 dispatch");
        AnimationV1.Document policies = document(
            1000,
            Collections.<AnimationV1.Track>emptyList(),
            Arrays.asList(
                cue("drop", 0, AnimationV1.LatePolicy.DROP, 100),
                cue("once", 990, AnimationV1.LatePolicy.ONCE_IF_CURRENT, 100)));
        check(
            cursor(policies, true, new EventBudget(128, 4096)).start(1005, false, 0, 0).events.isEmpty(),
            "late DROP and previous cycle ONCE dropped");
        EventCursor drop = cursor(policies, true, new EventBudget(128, 4096));
        drop.start(0, true, 0, 0);
        check(drop.advance(1000000, 1, 1000).events.isEmpty(), "DROP after whole skipped loop");
        AnimationV1.Document end = document(
            10,
            Collections.<AnimationV1.Track>emptyList(),
            Collections.singletonList(cue("end", 10, AnimationV1.LatePolicy.LATEST, 1)));
        expect(IllegalArgumentException.class, () -> cursor(end, true, new EventBudget(10, 10)));
        EventCursor nonloop = cursor(end, false, new EventBudget(10, 10));
        nonloop.start(0, true, 0, 0);
        check(nonloop.advance(10, 1, 10).events.size() == 1, "nonloop terminal cue");

        Random random = new Random(781L);
        EventCursor sequential = cursor(doc, true, new EventBudget(128, 4096));
        sequential.start(0, true, 0, 0);
        long previous = 0;
        for (int i = 1; i <= 1000; i++) {
            long age = previous + 1 + random.nextInt(50);
            List<String> expected = new ArrayList<>();
            for (long t = previous + 1; t <= age; t++) {
                if (t % 1000 == 0) expected.add("zero");
                else if (t % 1000 == 500) expected.add("half");
            }
            EventCursor.Dispatch result = sequential.advance(age, i, i * 50L);
            check(result.events.size() == expected.size(), "small independent event enumeration");
            for (int k = 0; k < expected.size(); k++)
                check(result.events.get(k).cueId.equals(expected.get(k)), "event ordering");
            previous = age;
        }
    }

    private static void eventClocks() throws Exception {
        AnimationV1.Document pulse = document(
            1,
            Collections.<AnimationV1.Track>emptyList(),
            Collections.singletonList(cue("pulse", 0, AnimationV1.LatePolicy.LATEST, 0)));
        EventBudget owner = new EventBudget(3, 7);
        EventCursor a = cursor(pulse, true, owner), b = cursor(pulse, true, owner);
        int delivered = a.start(0, true, 0, 0).events.size() + b.start(0, true, 0, 0).events.size();
        int dropped = 0;
        for (int i = 1; i < 20; i++) {
            EventCursor.Dispatch x = a.advance(i, i, i * 10L), y = b.advance(i, i, i * 10L);
            delivered += x.events.size() + y.events.size();
            dropped += x.budgetDropped + y.budgetDropped;
        }
        check(delivered == 7 && dropped == 33, "shared exact rolling budget");
        check(a.advance(1000, 20, 1000).events.size() == 1, "window expiry with no queued burst");
        expect(IllegalArgumentException.class, () -> a.advance(1001, 21, 999));
        expect(IllegalArgumentException.class, () -> a.advance(AnimationV1.MAX_AGE_MS + 1, 21, 1001));
        EventCursor invalidFresh = cursor(pulse, true, new EventBudget(128, 4096));
        expect(IllegalArgumentException.class, () -> invalidFresh.start(1, true, 100, 100));
        check(invalidFresh.start(0, true, 0, 0).events.size() == 1, "invalid fresh leaves clocks untouched");

        // Regression: validating local budget must not mutate it before shared clock
        // rejection.
        EventBudget local = new EventBudget(128, 4096), shared = new EventBudget(128, 4096);
        EventCursor advanceOwner = cursor(pulse, true, shared);
        advanceOwner.start(0, true, 10, 100);
        EventCursor rejected = new EventCursor(new EpochKey(1, 2, 3), "rejected", 1, pulse, true, local, shared);
        expect(IllegalArgumentException.class, () -> rejected.start(0, true, 20, 99));
        EventCursor usable = new EventCursor(
            new EpochKey(1, 2, 3),
            "usable",
            1,
            pulse,
            true,
            local,
            new EventBudget(128, 4096));
        check(usable.start(0, true, 0, 0).events.size() == 1, "second gate failure preserves first gate");
        AtomicReference<Throwable> wrong = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                usable.seekSilently(0);
            } catch (Throwable error) {
                wrong.set(error);
            }
        });
        worker.start();
        worker.join();
        check(wrong.get() instanceof IllegalStateException, "event owner thread");
    }

    private static void timebase() {
        check(
            TimelineTimebase.fromMilliseconds(1000) == 120000 && TimelineTimebase.fromLegacyTicks20(20) == 120000,
            "explicit unit bridge");
        check(
            TimelineTimebase.frameTimeFloor(1, 60, 1) == 2000
                && TimelineTimebase.frameTimeFloor(1, 30000, 1001) == 4004,
            "absolute rational frame time");
        check(TimelineTimebase.toMillisecondsFloor(2000) == 16, "millisecond quantization explicit");
        check(
            TimelineTimebase.frameCountCeiling(120000, 60, 1) == 60
                && TimelineTimebase.frameCountCeiling(0, 60, 1) == 0,
            "half-open export range");
        expect(IllegalArgumentException.class, () -> TimelineTimebase.toMillisecondsExact(2000));
        expect(IllegalArgumentException.class, () -> TimelineTimebase.fromMilliseconds(Long.MAX_VALUE));
        expect(IllegalArgumentException.class, () -> TimelineTimebase.fromLegacyTicks20(-1));
        expect(
            IllegalArgumentException.class,
            () -> TimelineTimebase.frameTimeFloor(Long.MAX_VALUE, 1, Long.MAX_VALUE));
        expect(
            IllegalArgumentException.class,
            () -> TimelineTimebase.frameCountCeiling(Long.MAX_VALUE, Long.MAX_VALUE, 1));
        expect(IllegalArgumentException.class, () -> TimelineTimebase.frameTimeFloor(0, 0, 1));
        Random random = new Random(120000);
        for (int i = 0; i < 200; i++) {
            long n = random.nextInt(100000), p = 1 + random.nextInt(120000), q = 1 + random.nextInt(1001);
            long units = TimelineTimebase.frameTimeFloor(n, p, q);
            BigInteger numerator = BigInteger.valueOf(n)
                .multiply(BigInteger.valueOf(120000))
                .multiply(BigInteger.valueOf(q));
            BigInteger lower = BigInteger.valueOf(units)
                .multiply(BigInteger.valueOf(p));
            check(
                lower.compareTo(numerator) <= 0 && lower.add(BigInteger.valueOf(p))
                    .compareTo(numerator) > 0,
                "rational floor inequality");
            long ms = BigInteger.valueOf(n)
                .multiply(BigInteger.valueOf(1000))
                .multiply(BigInteger.valueOf(q))
                .divide(BigInteger.valueOf(p))
                .longValue();
            check(TimelineTimebase.toMillisecondsFloor(units) == ms, "no accumulated rounding");
        }
    }

    private static void migration() {
        LegacyScalarMigrator.Result migrated = LegacyScalarMigrator.migrateBrightness(
            "core:migrated",
            20,
            1,
            Arrays.asList(new LegacyScalarMigrator.TickKey(0, 0), new LegacyScalarMigrator.TickKey(20, 1)));
        check(
            migrated.document.durationMs == 1000
                && migrated.document.tracks.get(0).property == AnimationV1.Property.BRIGHTNESS,
            "typed legacy tick brightness mapping");
        check(migrated.warnings.size() == 3, "migration limitations explicit");
        expect(
            IllegalArgumentException.class,
            () -> LegacyScalarMigrator
                .migrateBrightness("core:x", Long.MAX_VALUE, 0, Collections.<LegacyScalarMigrator.TickKey>emptyList()));
    }

    private static AnimationV1.Document decode(String text) {
        return StrictJsonCodec.decode(text.getBytes(StandardCharsets.UTF_8));
    }

    private static void rejected(String code, String text) {
        rejectedBytes(code, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void rejectedBytes(String code, byte[] bytes) {
        try {
            StrictJsonCodec.decode(bytes);
            throw new AssertionError("accepted input expected " + code);
        } catch (StrictJsonCodec.CodecException error) {
            check(error.code.equals(code), "expected " + code + ", got " + error.code);
        }
    }

    private static String repeat(String text, int count) {
        StringBuilder out = new StringBuilder(text.length() * count);
        for (int i = 0; i < count; i++) out.append(text);
        return out.toString();
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable error) {
            check(type.isInstance(error), "expected " + type.getName() + ", got " + error);
            return;
        }
        throw new AssertionError("expected " + type.getName());
    }

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }

    private static final class CountingStream extends InputStream {

        int read;
        boolean closed;

        @Override
        public int read() {
            read++;
            return ' ';
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            Arrays.fill(buffer, offset, offset + length, (byte) ' ');
            read += length;
            return length;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
