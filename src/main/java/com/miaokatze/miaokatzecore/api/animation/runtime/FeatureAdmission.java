// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.api.animation.runtime;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import com.miaokatze.miaokatzecore.api.animation.ResourceId;

/** Bounded preflight. Support declarations do not authorize resource loading or native adapters. */
public final class FeatureAdmission {

    public enum Status {
        SUPPORTED,
        DEGRADED,
        REJECTED
    }

    public static final class Decision {

        public final Status status;
        public final Set<ResourceId> missingRequired, omittedOptional;

        private Decision(Set<ResourceId> required, Set<ResourceId> optional) {
            missingRequired = Collections.unmodifiableSet(required);
            omittedOptional = Collections.unmodifiableSet(optional);
            status = !required.isEmpty() ? Status.REJECTED : optional.isEmpty() ? Status.SUPPORTED : Status.DEGRADED;
        }
    }

    private FeatureAdmission() {}

    public static Decision evaluate(Set<ResourceId> required, Set<ResourceId> optional, Set<ResourceId> supported) {
        Set<ResourceId> r = copy(required), o = copy(optional), s = copy(supported);
        for (ResourceId id : r)
            if (o.contains(id)) throw new IllegalArgumentException("feature both required and optional");
        r.removeAll(s);
        o.removeAll(s);
        return new Decision(r, o);
    }

    private static Set<ResourceId> copy(Set<ResourceId> source) {
        if (source == null || source.size() > 128) throw new IllegalArgumentException("feature count");
        Set<ResourceId> sorted = new TreeSet<>(Comparator.comparing(ResourceId::toString));
        for (ResourceId id : source) {
            if (id == null) throw new IllegalArgumentException("null feature");
            sorted.add(id);
        }
        return new LinkedHashSet<>(sorted);
    }
}
