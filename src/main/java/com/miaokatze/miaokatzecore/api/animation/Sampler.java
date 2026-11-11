// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.api.animation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** No accumulated clock, global RNG, MC classes or event dispatch. */
public strictfp final class Sampler {

    private Sampler() {}

    public static SampledFrame sample(AnimationV1.Document document, long localTimeMs) {
        if (document == null) throw new IllegalArgumentException("document");
        long time = Math.max(0, Math.min(document.durationMs, localTimeMs));
        Map<String, Double> values = new LinkedHashMap<String, Double>();
        for (AnimationV1.Track track : document.tracks) values.put(track.id, value(track, time));
        return new SampledFrame(document, time, values);
    }

    private static double value(AnimationV1.Track track, long time) {
        List<AnimationV1.Key> keys = track.keys;
        if (time <= keys.get(0).timeMs) return keys.get(0).value;
        if (time >= keys.get(keys.size() - 1).timeMs) return keys.get(keys.size() - 1).value;
        int lo = 0, hi = keys.size() - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (keys.get(mid).timeMs <= time) lo = mid;
            else hi = mid;
        }
        AnimationV1.Key a = keys.get(lo), b = keys.get(hi);
        if (a.interpolation == AnimationV1.Interpolation.STEP) return a.value;
        double u = (double) (time - a.timeMs) / (double) (b.timeMs - a.timeMs);
        if (a.interpolation == AnimationV1.Interpolation.SMOOTHSTEP) u = u * u * (3 - 2 * u);
        return a.value + (b.value - a.value) * u;
    }

    public static double randomUnit(long seed, String stableId, long ordinal) {
        AnimationV1.id(stableId);
        AnimationV1.require(ordinal >= 0, "ordinal");
        long z = seed ^ 0x9e3779b97f4a7c15L;
        for (int i = 0; i < stableId.length(); i++) z = (z ^ stableId.charAt(i)) * 0x100000001b3L;
        z += ordinal * 0x9e3779b97f4a7c15L;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }
}
