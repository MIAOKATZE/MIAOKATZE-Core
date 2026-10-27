package com.miaokatze.miaokatzecore.api.miaovision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Versioned, transport-neutral DTO. Consumers own packet/NBT encoding and enforce their own identity epoch. */
public final class SceneSnapshot {

    public static final int SCHEMA_VERSION = 1, MAX_BODIES = 100;
    public final int schemaVersion;
    public final String layoutId;
    public final long revision, tick;
    public final List<SceneBody> bodies;

    public SceneSnapshot(String layoutId, long revision, long tick, List<SceneBody> bodies) {
        this(SCHEMA_VERSION, layoutId, revision, tick, bodies);
    }

    public SceneSnapshot(int schemaVersion, String layoutId, long revision, long tick, List<SceneBody> bodies) {
        if (schemaVersion != SCHEMA_VERSION || layoutId == null
            || layoutId.isEmpty()
            || layoutId.length() > 128
            || revision < 0
            || tick < 0
            || bodies == null
            || bodies.size() > MAX_BODIES) throw new IllegalArgumentException("snapshot header/capacity");
        Set<String> ids = new HashSet<>();
        List<SceneBody> copy = new ArrayList<>(bodies);
        for (SceneBody b : copy)
            if (b == null || !ids.add(b.id)) throw new IllegalArgumentException("null/duplicate body");
        Collections.sort(copy, (a, b) -> a.id.compareTo(b.id));
        this.schemaVersion = schemaVersion;
        this.layoutId = layoutId;
        this.revision = revision;
        this.tick = tick;
        this.bodies = Collections.unmodifiableList(copy);
    }

    public boolean isNewerThan(SceneSnapshot previous) {
        return previous == null
            || (layoutId.equals(previous.layoutId) && revision > previous.revision && tick >= previous.tick);
    }
}
