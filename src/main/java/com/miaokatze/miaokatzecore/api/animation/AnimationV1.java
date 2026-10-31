package com.miaokatze.miaokatzecore.api.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable, validated scalar V0 definitions. Time fields are integer
 * milliseconds.
 */
public final class AnimationV1 {
    public static final int MAX_TRACKS = 512, MAX_KEYS = 65536, MAX_KEYS_PER_TRACK = 4096;
    public static final int MAX_CUES = 128, MAX_RESOURCES = 256;
    public static final long MAX_DURATION_MS = 3600000L, MAX_AGE_MS = 315360000000L;

    private AnimationV1() {
    }

    public enum Property {
        X(-1000000, 1000000, "block"), Y(-1000000, 1000000, "block"), Z(-1000000, 1000000, "block"),
        SCALE(0, 1024, "ratio"), OPACITY(0, 1, "ratio"), BRIGHTNESS(0, 16, "linear"),
        ROTATION_DEGREES(-360000, 360000, "degree"), VISIBILITY(0, 1, "boolean"), FOV_DEGREES(10, 150, "degree");

        public final double min, max;
        public final String unit;

        Property(double min, double max, String unit) {
            this.min = min;
            this.max = max;
            this.unit = unit;
        }

        public boolean supports(Interpolation interpolation) {
            return interpolation != null && (this != VISIBILITY || interpolation == Interpolation.STEP);
        }
    }

    public enum Interpolation {
        STEP, LINEAR, SMOOTHSTEP
    }

    public enum CueKind {
        SOUND, CAMERA_IMPULSE
    }

    public enum LatePolicy {
        DROP, LATEST, ONCE_IF_CURRENT
    }

    public static final class Key {
        public final long timeMs;
        public final double value;
        public final Interpolation interpolation;

        public Key(long timeMs, double value, Interpolation interpolation) {
            require(timeMs >= 0 && timeMs <= MAX_DURATION_MS, "key time");
            require(!Double.isNaN(value) && !Double.isInfinite(value), "non-finite key");
            require(interpolation != null, "interpolation");
            this.timeMs = timeMs;
            this.value = value;
            this.interpolation = interpolation;
        }
    }

    public static final class Track {
        public final String id, targetId;
        public final Property property;
        public final List<Key> keys;

        public Track(String id, String targetId, Property property, List<Key> keys) {
            this.id = id(id);
            this.targetId = id(targetId);
            require(property != null, "property");
            this.property = property;
            require(keys != null && !keys.isEmpty() && keys.size() <= MAX_KEYS_PER_TRACK, "key count");
            ArrayList<Key> copy = new ArrayList<Key>(keys.size());
            long previous = -1;
            for (Key key : keys) {
                require(key != null && key.timeMs > previous, "keys must be strictly ascending");
                require(key.value >= property.min && key.value <= property.max, "property range");
                require(property.supports(key.interpolation), "interpolation unsupported by property");
                require(property != Property.VISIBILITY || key.value == 0 || key.value == 1,
                        "visibility must be 0 or 1");
                previous = key.timeMs;
                copy.add(key);
            }
            this.keys = Collections.unmodifiableList(copy);
        }
    }

    public static final class Cue {
        public final String id;
        public final long timeMs, validForMs;
        public final CueKind kind;
        public final LatePolicy latePolicy;
        public final ResourceId resource;

        public Cue(String id, long timeMs, CueKind kind, LatePolicy latePolicy, long validForMs, ResourceId resource) {
            this.id = id(id);
            require(timeMs >= 0 && timeMs <= MAX_DURATION_MS, "cue time");
            require(kind != null && latePolicy != null, "cue enums");
            require(validForMs >= 0 && validForMs <= 10000, "cue max lateness");
            require(kind != CueKind.SOUND || resource != null, "sound resource required");
            require(kind != CueKind.CAMERA_IMPULSE || resource == null, "camera impulse has no resource");
            this.timeMs = timeMs;
            this.kind = kind;
            this.latePolicy = latePolicy;
            this.validForMs = validForMs;
            this.resource = resource;
        }
    }

    public static final class Document {
        public final String id;
        public final int schemaMinor;
        public final long durationMs, seed;
        public final List<Track> tracks;
        public final List<Cue> cues;
        public final Set<ResourceId> resources;

        public Document(String id, int schemaMinor, long durationMs, long seed, List<Track> tracks, List<Cue> cues,
                Set<ResourceId> resources) {
            this.id = ResourceId.parse(id).toString();
            require(schemaMinor == 0, "unsupported schema minor / required features");
            require(durationMs >= 1 && durationMs <= MAX_DURATION_MS, "duration");
            require(tracks != null && tracks.size() <= MAX_TRACKS, "track count");
            require(cues != null && cues.size() <= MAX_CUES, "cue count");
            require(resources != null && resources.size() <= MAX_RESOURCES, "resource count");
            Set<ResourceId> resourceCopy = new LinkedHashSet<ResourceId>();
            for (ResourceId resource : resources) {
                require(resource != null, "null resource");
                resourceCopy.add(resource);
            }
            Set<String> ids = new HashSet<String>(), claims = new HashSet<String>();
            int count = 0;
            for (Track track : tracks) {
                require(track != null && ids.add(track.id), "duplicate/null track");
                require(claims.add(track.targetId + ":" + track.property.name()), "duplicate target property");
                count += track.keys.size();
                require(count <= MAX_KEYS, "total key count");
                require(track.keys.get(track.keys.size() - 1).timeMs <= durationMs, "key beyond duration");
            }
            ids.clear();
            for (Cue cue : cues) {
                require(cue != null && ids.add(cue.id), "duplicate/null cue");
                require(cue.timeMs <= durationMs, "cue beyond duration");
                require(cue.resource == null || resourceCopy.contains(cue.resource), "undeclared cue resource");
            }
            this.schemaMinor = schemaMinor;
            this.durationMs = durationMs;
            this.seed = seed;
            this.tracks = Collections.unmodifiableList(new ArrayList<Track>(tracks));
            this.cues = Collections.unmodifiableList(new ArrayList<Cue>(cues));
            this.resources = Collections.unmodifiableSet(resourceCopy);
        }
    }

    static String id(String id) {
        require(id != null && id.matches("[a-z0-9][a-z0-9_.-]{0,63}"), "invalid stable id");
        return id;
    }

    static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalArgumentException(message);
    }
}
