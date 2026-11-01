package com.miaokatze.miaokatzecore.api.animation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Explicit typed subset migration, not a parser for GTUDK's old
 * particle/project/rod formats.
 */
public final class LegacyScalarMigrator {

    private LegacyScalarMigrator() {}

    public static final class TickKey {

        public final long tick;
        public final double value;

        public TickKey(long tick, double value) {
            this.tick = tick;
            this.value = value;
        }
    }

    public static final class Result {

        public final AnimationV1.Document document;
        public final List<String> warnings;

        private Result(AnimationV1.Document document, List<String> warnings) {
            this.document = document;
            this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
        }
    }

    public static Result migrateBrightness(String id, long durationTicks, long seed, List<TickKey> fadeKeys) {
        if (durationTicks < 1 || durationTicks > AnimationV1.MAX_DURATION_MS / 50)
            throw new IllegalArgumentException("tick duration overflow/range");
        if (fadeKeys == null || fadeKeys.isEmpty() || fadeKeys.size() > AnimationV1.MAX_KEYS_PER_TRACK)
            throw new IllegalArgumentException("legacy key count");
        List<AnimationV1.Key> keys = new ArrayList<AnimationV1.Key>();
        for (TickKey key : fadeKeys) {
            if (key == null || key.tick < 0 || key.tick > durationTicks)
                throw new IllegalArgumentException("legacy tick");
            keys.add(new AnimationV1.Key(key.tick * 50, key.value, AnimationV1.Interpolation.LINEAR));
        }
        AnimationV1.Track track = new AnimationV1.Track(
            "legacy-brightness",
            "legacy-target",
            AnimationV1.Property.BRIGHTNESS,
            keys);
        return new Result(
            new AnimationV1.Document(
                id,
                0,
                durationTicks * 50,
                seed,
                Collections.singletonList(track),
                Collections.<AnimationV1.Cue>emptyList(),
                Collections.<ResourceId>emptySet()),
            Arrays.asList(
                "Typed legacy fade mapped to BRIGHTNESS, not OPACITY; no source file was modified.",
                "Caller must verify old easing and renderer brightness mapping before approving visual parity.",
                "Only this supplied scalar stream was migrated; unrepresented legacy behaviors are not silently claimed."));
    }
}
