// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.api.tooltip.component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A compact text line with its own presentation icon. */
public final class TooltipGuide implements TooltipBlock {

    public enum Kind {
        TIER,
        WARNING,
        STRUCTURE,
        DIVIDER
    }

    public final Kind kind;
    public final String text;

    TooltipGuide(Kind kind, String text) {
        this.kind = Objects.requireNonNull(kind);
        this.text = Objects.requireNonNull(text);
    }

    @Override
    public List<String> fallbackLines() {
        return Collections.singletonList(text);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TooltipGuide && ((TooltipGuide) o).kind == kind && ((TooltipGuide) o).text.equals(text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, text);
    }
}
