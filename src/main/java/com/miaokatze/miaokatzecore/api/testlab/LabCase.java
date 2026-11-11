// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.api.testlab;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Immutable discovery metadata. A default suite is selected by each module, never inferred from its lane. */
public final class LabCase {

    private final String id, title, lane;
    private final Set<String> tags;
    private final boolean defaultSuite, requiresRealActor;

    public LabCase(String id, String title, String lane, Set<String> tags, boolean defaultSuite,
        boolean requiresRealActor) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = Objects.requireNonNull(title, "title");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.tags = Collections.unmodifiableSet(new LinkedHashSet<String>(Objects.requireNonNull(tags, "tags")));
        this.defaultSuite = defaultSuite;
        this.requiresRealActor = requiresRealActor;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String lane() {
        return lane;
    }

    public Set<String> tags() {
        return tags;
    }

    public boolean defaultSuite() {
        return defaultSuite;
    }

    public boolean requiresRealActor() {
        return requiresRealActor;
    }
}
