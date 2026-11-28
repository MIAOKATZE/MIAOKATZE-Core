// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.api.tooltip.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** Exact fallback span and the block offsets within it. Core constructs this only after a page is authored. */
public final class TooltipPageBody implements TooltipBlock {

    public static final class Span {

        public final int from, to;
        public final TooltipBlock block;

        private Span(int from, int to, TooltipBlock block) {
            this.from = from;
            this.to = to;
            this.block = block;
        }
    }

    public final String key;
    public final Map<String, Integer> charts;
    public final boolean compact, single;
    public final NativeTooltipPage.Tier tier;
    private final List<String> text;
    public final List<Span> spans;

    TooltipPageBody(List<Object> parts) {
        this(parts, "", Collections.emptyMap(), false, false, NativeTooltipPage.Tier.COMMON);
    }

    TooltipPageBody(List<Object> parts, String key, Map<String, Integer> charts, boolean compact, boolean single,
        NativeTooltipPage.Tier tier) {
        this.key = key;
        this.charts = Collections.unmodifiableMap(new LinkedHashMap<>(charts));
        this.compact = compact;
        this.single = single;
        this.tier = tier;
        List<String> lines = new ArrayList<>();
        List<Span> slices = new ArrayList<>();
        for (Object part : parts) {
            if (part instanceof String) lines.add((String) part);
            else {
                TooltipBlock block = (TooltipBlock) part;
                int from = lines.size();
                lines.addAll(block.fallbackLines());
                if (from < lines.size()) slices.add(new Span(from, lines.size(), block));
            }
        }
        text = Collections.unmodifiableList(lines);
        spans = Collections.unmodifiableList(slices);
    }

    @Override
    public List<String> fallbackLines() {
        return text;
    }
}
