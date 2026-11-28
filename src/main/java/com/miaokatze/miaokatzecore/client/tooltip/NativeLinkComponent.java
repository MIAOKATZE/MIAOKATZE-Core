// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import com.slprime.chromatictooltips.api.ITooltipComponent;
import com.slprime.chromatictooltips.api.TooltipContext;
import com.slprime.chromatictooltips.util.TooltipFontContext;

/** Ordinary HTTP(S) text, retaining the destination when a URL wraps across lines or pages. */
final class NativeLinkComponent implements ITooltipComponent {

    private static final class Row {

        final String text;
        final List<TooltipTextLinks.Span> links;

        Row(String text) {
            this(text, TooltipTextLinks.spans(text));
        }

        Row(String text, List<TooltipTextLinks.Span> links) {
            this.text = text;
            this.links = links;
        }
    }

    private final List<Row> rows;

    private NativeLinkComponent(List<Row> rows) {
        this.rows = rows;
    }

    static void convert(List<Object> lines) {
        for (int i = 0; i < lines.size(); i++) {
            Object line = lines.get(i);
            if (line instanceof String && !TooltipTextLinks.spans((String) line)
                .isEmpty()) lines.set(i, new NativeLinkComponent(Collections.singletonList(new Row((String) line))));
        }
    }

    static void textLinks(TooltipContext context, String text, int x, int y) {
        if (!TooltipPinning.interactive(context)) return;
        for (TooltipTextLinks.Span span : TooltipTextLinks.spans(text)) {
            int start = TooltipFontContext.getStringWidth(TooltipTextLinks.prefix(text, span.from));
            int end = TooltipFontContext.getStringWidth(TooltipTextLinks.prefix(text, span.to));
            link(context, span.url, x + start, y, end - start);
        }
    }

    private static void link(TooltipContext context, String url, double x, double y, double width) {
        if (!TooltipPinning.interactive(context)) return;
        GuiScreen gui = Minecraft.getMinecraft().currentScreen;
        NativeTooltipMouse.action(context, x, y, width, TooltipFontContext.getFontHeight(), () -> TooltipLinkBrowser.open(gui, url));
    }

    @Override
    public int getWidth() {
        int width = 0;
        for (Row row : rows) width = Math.max(width, TooltipFontContext.getStringWidth(row.text));
        return width;
    }

    @Override
    public int getHeight() {
        return rows.size() * TooltipFontContext.getFontHeight() - getSpacing();
    }

    @Override
    public int getSpacing() {
        return TooltipFontContext.DEFAULT_SPACING;
    }

    @Override
    public ITooltipComponent[] paginate(TooltipContext context, int maxWidth, int maxHeight) {
        List<Row> wrapped = new ArrayList<>();
        for (Row row : rows) {
            String source = TooltipTextLinks.plain(row.text);
            int cursor = 0;
            for (String line : TooltipFontContext.listFormattedStringToWidth(row.text, Math.max(1, maxWidth))) {
                String visible = TooltipTextLinks.plain(line);
                int offset = source.indexOf(visible, cursor);
                if (offset < 0) offset = cursor;
                int end = offset + visible.length();
                List<TooltipTextLinks.Span> spans = new ArrayList<>();
                for (TooltipTextLinks.Span span : row.links) {
                    int from = Math.max(offset, span.from), to = Math.min(end, span.to);
                    if (to > from) spans.add(new TooltipTextLinks.Span(from - offset, to - offset, span.url));
                }
                wrapped.add(new Row(line, spans));
                cursor = end;
            }
        }
        int count = Math.max(1, maxHeight / TooltipFontContext.getFontHeight());
        if (wrapped.size() > count) return new ITooltipComponent[] { new NativeLinkComponent(wrapped.subList(0, count)),
            new NativeLinkComponent(wrapped.subList(count, wrapped.size())) };
        return new ITooltipComponent[] { new NativeLinkComponent(wrapped) };
    }

    @Override
    public void draw(int x, int y, int width, TooltipContext context) {
        for (Row row : rows) {
            TooltipFontContext.drawString(row.text, x, y);
            for (TooltipTextLinks.Span span : row.links) {
                int start = TooltipFontContext.getStringWidth(TooltipTextLinks.prefix(row.text, span.from));
                int end = TooltipFontContext.getStringWidth(TooltipTextLinks.prefix(row.text, span.to));
                link(context, span.url, x + start, y, end - start);
            }
            y += TooltipFontContext.getFontHeight();
        }
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof NativeLinkComponent)) return false;
        NativeLinkComponent component = (NativeLinkComponent) other;
        if (rows.size() != component.rows.size()) return false;
        for (int i = 0; i < rows.size(); i++) {
            Row a = rows.get(i), b = component.rows.get(i);
            if (!a.text.equals(b.text) || a.links.size() != b.links.size()) return false;
            for (int j = 0; j < a.links.size(); j++) {
                TooltipTextLinks.Span x = a.links.get(j), y = b.links.get(j);
                if (x.from != y.from || x.to != y.to || !x.url.equals(y.url)) return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 1;
        for (Row row : rows) {
            hash = 31 * hash + row.text.hashCode();
            for (TooltipTextLinks.Span link : row.links) hash = 31 * hash + Objects.hash(link.from, link.to, link.url);
        }
        return hash;
    }
}
