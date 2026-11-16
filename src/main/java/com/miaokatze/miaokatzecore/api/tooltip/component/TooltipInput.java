// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.api.tooltip.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** An authored single line. Only explicit input markers draw icons; the fallback keeps the labels. */
public final class TooltipInput implements TooltipBlock {

    private static final Pattern MARK = Pattern.compile("\\[(key|(?:key-)?(?:left|right|middle)):([^\\]\\r\\n]+)\\]");
    public static final int ADVANCE = 14; // 12-pixel sprite and a two-pixel gap

    public enum Mouse {
        LEFT, RIGHT, MIDDLE
    }

    public static final class Run {

        public final boolean key;
        public final Mouse mouse;
        public final String text;

        private Run(boolean key, Mouse mouse, String text) {
            this.key = key;
            this.mouse = mouse;
            this.text = text;
        }

        public int width(ToIntFunction<String> font) {
            return font.applyAsInt(text) + (key ? ADVANCE : 0) + (mouse != null ? ADVANCE : 0);
        }
    }

    private final String source;
    private final String plain;
    private final List<Run> runs;

    private TooltipInput(String source) {
        this.source = Objects.requireNonNull(source);
        if (source.indexOf('\n') >= 0 || source.indexOf('\r') >= 0) throw new IllegalArgumentException("Single line required");
        Matcher m = MARK.matcher(source);
        List<Run> parts = new ArrayList<>();
        StringBuilder fallback = new StringBuilder();
        int end = 0;
        String style = "";
        while (m.find()) {
            String before = source.substring(end, m.start()), label = "[" + m.group(2) + "]", kind = m.group(1);
            if (!before.isEmpty()) parts.add(new Run(false, null, style + before));
            style = styleAfter(style, before);
            Mouse mouse = kind.equals("key") ? null : Mouse.valueOf(kind.replace("key-", "").toUpperCase(java.util.Locale.ROOT));
            parts.add(new Run(kind.startsWith("key"), mouse, style + label));
            fallback.append(before).append(label);
            style = styleAfter(style, label);
            end = m.end();
        }
        String tail = source.substring(end);
        if (!tail.isEmpty()) parts.add(new Run(false, null, style + tail));
        this.plain = fallback.append(tail).toString();
        this.runs = Collections.unmodifiableList(parts);
    }

    private static String styleAfter(String style, String text) {
        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) != '§') continue;
            char code = Character.toLowerCase(text.charAt(++i));
            if (code == 'r') style = "";
            else if ("0123456789abcdef".indexOf(code) >= 0) style = "§" + code;
            else if ("klmno".indexOf(code) >= 0) style += "§" + code;
        }
        return style;
    }

    public static TooltipInput of(String source) {
        return new TooltipInput(source);
    }

    public List<Run> runs() {
        return runs;
    }

    public int width(ToIntFunction<String> font) {
        int width = 0;
        for (Run run : runs) width += run.width(font);
        return width;
    }

    public String plain() {
        return plain;
    }

    @Override
    public List<String> fallbackLines() {
        return Collections.singletonList(plain);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TooltipInput && source.equals(((TooltipInput) other).source);
    }

    @Override
    public int hashCode() {
        return source.hashCode();
    }
}
