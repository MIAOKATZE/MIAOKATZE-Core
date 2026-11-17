// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.miaokatze.miaokatzecore.api.tooltip.TooltipPages;
import com.miaokatze.miaokatzecore.api.tooltip.component.NativeTooltipPage;
import com.slprime.chromatictooltips.TooltipHandler;
import com.slprime.chromatictooltips.api.ITooltipComponent;
import com.slprime.chromatictooltips.api.TooltipContext;
import com.slprime.chromatictooltips.component.SectionComponent;
import com.slprime.chromatictooltips.event.RenderTooltipEvent;
import com.slprime.chromatictooltips.util.TooltipFontContext;
import com.slprime.chromatictooltips.util.TooltipUtils;

/** Coordinates are captured from drawn components, and the annotation uses the real outer tooltip bounds. */
final class ChartPointer {

    private static TooltipContext owner;
    private static int targetY, firstY;
    private static boolean target;
    private static String item;

    private ChartPointer() {}

    static void anchor(TooltipContext c, String key, String id, int y) {
        if (owner != c) {
            owner = c;
            item = key;
            firstY = y;
            target = false;
        }
        if (NativeTooltipPage.selected(key, id)) {
            targetY = y;
            target = true;
        }
    }

    static void rendered(RenderTooltipEvent event) {
        NativeTooltipMouse.rendered(event);
        if (owner == null) return;
        try {
            if (owner != event.context || !target) return;
            ScaledResolution screen = TooltipUtils.getScaledResolution();
            float s = owner.getScaleFactor() / (float) screen.getScaleFactor();
            if (s <= 0) return;
            float origin = s == 1 ? 0
                : event.y / s - event.page.getMargin()
                    .getTop();
            int y = (int) origin + targetY, first = (int) origin + firstY;
            int left = (int) (event.x / s), right = (int) ((event.x + event.width) / s),
                width = (int) (screen.getScaledWidth() / s);
            int height = (int) (screen.getScaledHeight() / s);
            String variant = StatCollector
                .translateToLocalFormatted("miaokatzecore.page.variant", TooltipPages.detailKeyLabel());
            String select = StatCollector
                .translateToLocalFormatted("miaokatzecore.page.select", TooltipController.selectKeyLabel());
            boolean multi = NativeTooltipPage.multiple(item);
            int vw = TooltipFontContext.getStringWidth(variant) + 18,
                sw = TooltipFontContext.getStringWidth(select) + 17;
            int want = Math.max(vw, multi ? sw : 0);
            boolean before = left >= want + 4 || left > width - right;
            int space = before ? left : width - right;
            int x = before ? left - want - 3 : right + 3;
            // If both side gutters are narrow, keep the captions in the top/bottom margin of this tooltip.
            if (space < want + 4) {
                x = Math.max(1, Math.min(width - want - 1, left));
                int top = (int) (event.y / s), bottom = (int) ((event.y + event.height) / s);
                y = top >= (multi ? 25 : 12) ? top - 12 : Math.min(height - 11, bottom + (multi ? 12 : 2));
                first = y;
            }
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
            GL11.glPushMatrix();
            try {
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glScalef(s, s, 1);
                if (multi) {
                    int cy = Math.max(0, first - 12);
                    Gui.drawRect(x - 1, cy - 1, x + sw + 1, cy + 10, 0xe0191723);
                    NativeGuideComponent.sprite(item, 0, x, cy, TooltipController.selectHeld());
                    TooltipFontContext.drawString(select, x + 14, cy, 0xffd8e2cf);
                }
                Gui.drawRect(x - 1, y - 1, x + vw + 1, y + 10, 0xe0191723);
                int nudge = TooltipPages.animationEnabled(item)
                    ? (int) Math.round(Math.cos(System.nanoTime() / 175000000.0))
                    : 0;
                String arrow = before ? "▶" : "◀";
                TooltipFontContext.drawString(variant, x + (before ? 1 : 14), y, 0xffecf6ab);
                TooltipFontContext.drawString(arrow, x + (before ? vw - 12 : 0) + nudge, y, 0xffecf6ab);
            } finally {
                GL11.glPopMatrix();
                GL11.glPopAttrib();
            }
        } finally {
            owner = null;
            item = null;
            target = false;
        }
    }

    static void follow(String key) {
        follow(TooltipHandler.getLastTooltipContext(), key);
    }

    static void follow(TooltipContext c, String key) {
        if (c == null || !key.equals(TooltipPages.identity(c.getItem()))) return;
        SectionComponent first = c.getActivePageComponent();
        if (first == null) return;
        do {
            if (contains(c.getActivePageComponent(), key)) return;
        } while (c.nextTooltipPage() && first != c.getActivePageComponent());
    }

    private static boolean contains(SectionComponent page, String key) {
        for (ITooltipComponent block : page.getComponents()) {
            if (block instanceof NativeChartComponent
                && NativeTooltipPage.selected(key, ((NativeChartComponent) block).chart().chartId)) return true;
            if (block instanceof SectionComponent && contains((SectionComponent) block, key)) return true;
        }
        return false;
    }
}
