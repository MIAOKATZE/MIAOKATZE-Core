// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.api.animation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Values keyed by stable track ID; consumer owns immutable target bindings. */
public final class SampledFrame {

    private final AnimationV1.Document source;
    public final String documentId;
    public final long localTimeMs;
    public final Map<String, Double> valuesByTrack;

    SampledFrame(AnimationV1.Document document, long time, Map<String, Double> values) {
        source = document;
        documentId = document.id;
        localTimeMs = time;
        valuesByTrack = Collections.unmodifiableMap(new LinkedHashMap<String, Double>(values));
    }

    public boolean sampledFrom(AnimationV1.Document document) {
        return source == document;
    }

    public double value(String trackId) {
        Double value = valuesByTrack.get(trackId);
        if (value == null) throw new IllegalArgumentException("unknown track: " + trackId);
        return value.doubleValue();
    }
}
