package com.miaokatze.miaokatzecore.client.camera.slice;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Predicate;

/** Actual native progress fence, independent of Minecraft so cancelled/hidden/upload transitions can be exercised. */
final class SliceMeshProgress<S, T> {

    final Set<S> pending = Collections.newSetFromMap(new IdentityHashMap<S, Boolean>());
    final IdentityHashMap<S, T> installed = new IdentityHashMap<>();
    final IdentityHashMap<S, T> building = new IdentityHashMap<>();
    private T target;

    void target(T value) {
        target = value;
    }

    boolean acknowledge(S section, T stamp, boolean latestSubmission) {
        if (!latestSubmission) return false;
        if (building.get(section) == stamp) building.remove(section);
        if (stamp == null) installed.remove(section);
        else installed.put(section, stamp);
        if (stamp != target) return false;
        pending.remove(section);
        return true;
    }

    boolean shouldSchedule(S section, boolean hasToken, boolean queued) {
        return pending.contains(section) && !queued && (!hasToken || building.get(section) != target);
    }

    boolean hasRequired(Predicate<S> visible) {
        for (S section : pending) if (visible.test(section)) return true;
        return false;
    }

    void clear() {
        pending.clear();
        installed.clear();
        building.clear();
        target = null;
    }
}
