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

    private static final String KEY = "\\[(key|(?:key-)?(?:left|right|middle)):([^\\]\\r\\n]+)\\]";
    // Forge names are opaque strings (e.g. "molten.atomic separation catalyst"). Only token delimiters are reserved.
    private static final String MATERIAL = "\\[(item|fluid):([^\\[\\]|\\r\\n]*)\\|([^\\[\\]\\r\\n]+)\\]";
    private static final Pattern ALL = Pattern.compile(KEY + "|" + MATERIAL);
    public static final int MATERIAL_ADVANCE = 11; // 10-pixel inventory sprite + 1 pixel
    public static final int ADVANCE = 14; // 12-pixel sprite and a two-pixel gap

    public enum Mouse {
        LEFT,
        RIGHT,
        MIDDLE
    }

    public static final class Run {

        public final boolean key;
        public final Mouse mouse;
        public final String text;
        public final String itemId, fluidId;
        public final int meta;

        private Run(boolean key, Mouse mouse, String text) {
            this(key, mouse, text, null, null, 0);
        }

        private Run(boolean key, Mouse mouse, String text, String itemId, String fluidId, int meta) {
            this.itemId = itemId;
            this.fluidId = fluidId;
            this.meta = meta;
            this.key = key;
            this.mouse = mouse;
            this.text = text;
        }

        public int width(ToIntFunction<String> font) {
            return font.applyAsInt(text) + (itemId != null || fluidId != null ? MATERIAL_ADVANCE : 0)
                + (key ? ADVANCE : 0)
                + (mouse != null ? ADVANCE : 0);
        }
    }

    private final String source;
    private final String plain;
    private final List<Run> runs;

    private final boolean keys;

    private TooltipInput(String source, boolean keys) {
        this.source = Objects.requireNonNull(source);
        if (source.indexOf('\n') >= 0 || source.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Single line required");
        this.keys = keys;
        if (source.indexOf('[') < 0) {
            plain = source;
            runs = source.isEmpty() ? Collections.emptyList() : Collections.singletonList(new Run(false, null, source));
            return;
        }
        Matcher m = ALL.matcher(source);
        List<Run> parts = new ArrayList<>();
        StringBuilder fallback = new StringBuilder();
        int end = 0;
        String style = "";
        while (m.find()) {
            boolean material = m.group(1) == null;
            if (!material && !keys) continue;
            String before = source.substring(end, m.start());
            String label = "[" + m.group(material ? 5 : 2) + "]";
            fallback.append(before);
            if (material && !space(lastVisible(fallback))) {
                before += " ";
                fallback.append(' ');
            }
            if (!before.isEmpty()) parts.add(new Run(false, null, style + before));
            style = styleAfter(style, before);
            if (material) {
                int next = nextVisible(source, m.end());
                if (!space(next) && !punctuationOrSymbol(next)) label += " ";
                String id = m.group(4);
                int meta = 0;
                boolean item = m.group(3)
                    .equals("item");
                if (item) {
                    int at = id.lastIndexOf('@');
                    if (at >= 0) {
                        try {
                            meta = Integer.parseInt(id.substring(at + 1));
                            id = meta < 0 ? "" : id.substring(0, at);
                        } catch (NumberFormatException malformed) {
                            id = ""; // The localized label still belongs in the readable fallback.
                        }
                    }
                }
                parts.add(
                    new Run(
                        false,
                        null,
                        style + label,
                        item && !id.isEmpty() ? id : null,
                        !item && !id.isEmpty() ? id : null,
                        meta));
            } else {
                String kind = m.group(1);
                Mouse mouse = kind.equals("key") ? null
                    : Mouse.valueOf(
                        kind.replace("key-", "")
                            .toUpperCase(java.util.Locale.ROOT));
                parts.add(new Run(kind.startsWith("key"), mouse, style + label));
            }
            fallback.append(label);
            style = styleAfter(style, label);
            end = m.end();
        }
        String tail = source.substring(end);
        if (!tail.isEmpty()) parts.add(new Run(false, null, style + tail));
        this.plain = fallback.append(tail)
            .toString();
        this.runs = Collections.unmodifiableList(parts);
    }

    private static int lastVisible(CharSequence s) {
        int end = s.length();
        while (end >= 2 && s.charAt(end - 2) == '§') end -= 2;
        return end == 0 ? -1 : Character.codePointBefore(s, end);
    }

    private static int nextVisible(String s, int at) {
        while (at + 1 < s.length() && s.charAt(at) == '§') at += 2;
        return at == s.length() ? -1 : s.codePointAt(at);
    }

    private static boolean space(int ch) {
        return Character.isWhitespace(ch) || Character.isSpaceChar(ch);
    }

    private static boolean punctuationOrSymbol(int ch) {
        int type = Character.getType(ch);
        return type >= Character.DASH_PUNCTUATION && type <= Character.OTHER_SYMBOL
            || type == Character.INITIAL_QUOTE_PUNCTUATION
            || type == Character.FINAL_QUOTE_PUNCTUATION;
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
        return new TooltipInput(source, true);
    }

    public static boolean hasMaterial(String text) {
        return text.contains("[item:") || text.contains("[fluid:");
    }

    /** Only explicit material markers are special; other brackets remain ordinary text. */
    public static TooltipInput materials(String text) {
        return new TooltipInput(text, false);
    }

    static String plainMaterials(String text) {
        return hasMaterial(text) ? materials(text).plain() : text;
    }

    public List<Run> runs() {
        return runs;
    }

    public int width(ToIntFunction<String> font) {
        int width = 0;
        for (Run run : runs) width += run.width(font);
        return width;
    }

    /** Wrap ordinary text while keeping the sprite and bracketed name/key chord indivisible. */
    public List<List<Run>> wrap(int limit, ToIntFunction<String> font) {
        List<List<Run>> rows = new ArrayList<>();
        List<Run> row = new ArrayList<>();
        int used = 0;
        for (Run run : runs) {
            if (run.key || run.mouse != null || run.itemId != null || run.fluidId != null) {
                int advance = run.width(font);
                if (used > 0 && used + advance > limit) {
                    rows.add(row);
                    row = new ArrayList<>();
                    used = 0;
                }
                row.add(run);
                used += advance;
                continue;
            }
            String style = "";
            StringBuilder text = new StringBuilder();
            String value = run.text;
            for (int i = 0; i < value.length();) {
                int start = i;
                int cp = value.codePointAt(i);
                i += Character.charCount(cp);
                if (cp == '§' && i < value.length()) {
                    text.append(value, start, ++i);
                    style = styleAfter(style, value.substring(start, i));
                    continue;
                }
                // Keep a Latin word or a numerical quantity together when it fits on its own line.
                if (asciiWord(cp)) {
                    int end = i;
                    while (end < value.length() && asciiWord(value.charAt(end))) end++;
                    if (font.applyAsInt(style + value.substring(start, end)) <= limit) i = end;
                }
                String word = value.substring(start, i);
                int advance = font.applyAsInt(style + word);
                if (used > 0 && used + advance > limit) {
                    if (text.length() > 0) row.add(new Run(false, null, text.toString()));
                    rows.add(row);
                    row = new ArrayList<>();
                    text = new StringBuilder(style);
                    used = 0;
                }
                text.append(word);
                used += advance;
            }
            if (text.length() > 0) row.add(new Run(false, null, text.toString()));
        }
        if (!row.isEmpty() || rows.isEmpty()) rows.add(row);
        return rows;
    }

    private static boolean asciiWord(int c) {
        return c < 128 && (Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '%' || c == '/');
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
        return other instanceof TooltipInput && keys == ((TooltipInput) other).keys
            && source.equals(((TooltipInput) other).source);
    }

    @Override
    public int hashCode() {
        return source.hashCode();
    }
}
