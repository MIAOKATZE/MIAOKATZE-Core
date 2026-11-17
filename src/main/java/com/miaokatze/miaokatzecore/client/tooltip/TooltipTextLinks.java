// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** URL spans use visible character offsets, excluding Minecraft formatting codes. */
final class TooltipTextLinks {

    private static final Pattern URL = Pattern.compile("(?i)https?://[^\\s<>\\[\\]\\\"'，。；！？、（）【】]+");

    static final class Span {

        final int from, to;
        final String url;

        Span(int from, int to, String url) {
            this.from = from;
            this.to = to;
            this.url = url;
        }
    }

    static String plain(String text) {
        return text.replaceAll("§.", "");
    }

    static List<Span> spans(String text) {
        String visible = plain(text);
        List<Span> result = new ArrayList<>();
        Matcher matcher = URL.matcher(visible);
        while (matcher.find()) {
            int end = matcher.end();
            while (end > matcher.start() && ".,;!?:)".indexOf(visible.charAt(end - 1)) >= 0) end--;
            String value = visible.substring(matcher.start(), end);
            if (valid(value)) result.add(new Span(matcher.start(), end, value));
        }
        return result;
    }

    static boolean valid(String url) {
        try {
            URI uri = URI.create(url);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null
                && !uri.getHost()
                    .isEmpty();
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    static String prefix(String text, int visibleCount) {
        int index = 0, count = 0;
        while (index < text.length() && count < visibleCount) {
            if (text.charAt(index) == '§' && index + 1 < text.length()) index += 2;
            else {
                index++;
                count++;
            }
        }
        return text.substring(0, index);
    }
}
