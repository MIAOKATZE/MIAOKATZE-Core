// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import com.miaokatze.miaokatzecore.api.tooltip.TooltipAnimationClock;
import com.miaokatze.miaokatzecore.api.tooltip.TooltipPages;
import com.miaokatze.miaokatzecore.api.tooltip.component.NativeTooltipPage;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipInput;
import com.slprime.chromatictooltips.api.TooltipContext;
import com.slprime.chromatictooltips.event.RenderTooltipEvent;
import com.slprime.chromatictooltips.util.TooltipFontContext;
import com.slprime.chromatictooltips.util.TooltipUtils;

/** Only the actual painted glyph rectangles become actions; tooltip bounds form a click fence. */
final class NativeTooltipMouse {

    private static final Map<TooltipContext, List<Hit>> pending = new IdentityHashMap<>();
    private static final List<Hit> active = new ArrayList<>();
    private static GuiScreen owner;
    private static String item = "";
    private static long drawn;
    private static double left, top, right, bottom;

    private static final class Hit {

        final double x, y, width, height;
        final Runnable action;

        Hit(double x, double y, double width, double height, Runnable action) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.action = action;
        }

        boolean contains(double px, double py) {
            return px >= x && py >= y && px < x + width && py < y + height;
        }
    }

    static void newFrame() {
        pending.clear();
        active.clear();
        owner = null;
    }

    static void chartLabel(TooltipContext context, String key, String chart, int index, String label, double x,
        double y, float scale) {
        Runnable action = () -> {
            if (NativeTooltipPage.selectVariant(key, chart, index)) {
                ChartPointer.follow(key);
                TooltipPinning.refresh();
            }
        };
        for (TooltipInput.Run run : TooltipInput.materials(label)
            .runs()) {
            if (run.itemId != null || run.fluidId != null) x += TooltipInput.MATERIAL_ADVANCE * scale;
            int width = TooltipFontContext.getStringWidth(run.text);
            add(context, x, y, width * scale, TooltipFontContext.getFontHeight() * scale, action);
            x += width * scale;
        }
    }

    static void link(TooltipContext context, String url, double x, double y, double width) {
        GuiScreen gui = Minecraft.getMinecraft().currentScreen;
        add(context, x, y, width, TooltipFontContext.getFontHeight(), () -> TooltipLinkBrowser.open(gui, url));
    }

    static void textLinks(TooltipContext context, String text, int x, int y) {
        for (TooltipTextLinks.Span span : TooltipTextLinks.spans(text)) {
            int start = TooltipFontContext.getStringWidth(TooltipTextLinks.prefix(text, span.from));
            int end = TooltipFontContext.getStringWidth(TooltipTextLinks.prefix(text, span.to));
            link(context, span.url, x + start, y, end - start);
        }
    }

    private static void add(TooltipContext context, double x, double y, double width, double height, Runnable action) {
        if (width <= 0 || height <= 0) return;
        pending.computeIfAbsent(context, ignored -> new ArrayList<>())
            .add(new Hit(x, y, width, height, action));
    }

    static void rendered(RenderTooltipEvent event) {
        String key = TooltipPages.identity(event.context.getItem());
        List<Hit> hits = pending.remove(event.context);
        // A hovered item underneath the pinned tooltip cannot replace its interaction regions.
        if (TooltipController.pinned() && !TooltipController.isPinned(key)) return;
        active.clear();
        if (key.isEmpty()) {
            owner = null;
            return;
        }
        owner = Minecraft.getMinecraft().currentScreen;
        item = key;
        drawn = TooltipAnimationClock.nowNanos();
        left = event.x;
        top = event.y;
        right = event.x + event.width;
        bottom = event.y + event.height;
        if (hits == null) return;
        double scale = event.context.getScaleFactor() / (double) TooltipUtils.getScaledResolution()
            .getScaleFactor();
        double dx = scale == 1 ? 0
            : event.x - event.page.getMargin()
                .getLeft() * scale;
        double dy = scale == 1 ? 0
            : event.y - event.page.getMargin()
                .getTop() * scale;
        for (Hit hit : hits) {
            double x = Math.max(left, dx + hit.x * scale), y = Math.max(top, dy + hit.y * scale);
            double endX = Math.min(right, dx + (hit.x + hit.width) * scale);
            double endY = Math.min(bottom, dy + (hit.y + hit.height) * scale);
            if (endX > x && endY > y) active.add(new Hit(x, y, endX - x, endY - y, hit.action));
        }
    }

    static boolean click(GuiScreen gui, int x, int y, int button) {
        if (!consumeRelease(gui, x, y, button)) return false;
        if (button == 0) {
            for (int i = active.size() - 1; i >= 0; i--) {
                Hit hit = active.get(i);
                if (hit.contains(x, y)) {
                    hit.action.run();
                    break;
                }
            }
        }
        return true;
    }

    static boolean consumeRelease(GuiScreen gui, int x, int y, int button) {
        if (gui == null || gui != owner
            || gui != Minecraft.getMinecraft().currentScreen
            || TooltipAnimationClock.nowNanos() - drawn > 300000000L
            || (TooltipController.pinned() && (!TooltipPinning.active(gui) || !TooltipController.isPinned(item))))
            return false;
        return x >= left && y >= top && x < right && y < bottom;
    }
}
