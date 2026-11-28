// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import com.miaokatze.miaokatzecore.api.tooltip.TooltipAnimationClock;
import com.miaokatze.miaokatzecore.api.tooltip.TooltipPages;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipInput;
import com.slprime.chromatictooltips.api.TooltipContext;
import com.slprime.chromatictooltips.event.RenderTooltipEvent;
import com.slprime.chromatictooltips.util.TooltipFontContext;
import com.slprime.chromatictooltips.util.TooltipUtils;

/** Mouse regions are tied to drawn windows, including independent hit lists for two pins. */
final class NativeTooltipMouse {

    private static final long FRESH_NANOS = 300000000L;
    private static final Map<TooltipContext, List<Hit>> pending = new IdentityHashMap<>();
    private static final List<Painted> painted = new ArrayList<>();
    private static final List<Painted> previous = new ArrayList<>();

    /** Coordinates of a component drawn inside a scaled chart, or directly in the tooltip. */
    static final class Drawing {

        final TooltipContext context;
        final int originX, originY;
        final float scale;
        final boolean collect;

        Drawing(TooltipContext context, int originX, int originY, float scale, boolean collect) {
            this.context = context;
            this.originX = originX;
            this.originY = originY;
            this.scale = scale;
            this.collect = collect;
        }

        Drawing(TooltipContext context, boolean collect) {
            this(context, 0, 0, 1, collect);
        }

        void material(MaterialSprite.Material material, int x, int y, int width, int height) {
            if (collect && material != null)
                add(context, originX + x * scale, originY + y * scale, width * scale, height * scale, null, material);
        }
    }

    static final class MaterialHit {

        final Rectangle2D bounds;
        final MaterialSprite.Material material;

        MaterialHit(Rectangle2D bounds, MaterialSprite.Material material) {
            this.bounds = bounds;
            this.material = material;
        }
    }

    private static final class Hit {

        final Rectangle2D bounds;
        final BooleanSupplier action;
        final MaterialSprite.Material material;

        Hit(Rectangle2D bounds, BooleanSupplier action, MaterialSprite.Material material) {
            this.bounds = bounds;
            this.action = action;
            this.material = material;
        }
    }

    private static final class Painted {

        final GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        final long time = TooltipAnimationClock.nowNanos();
        final TooltipContext context;
        final Object page;
        final int width, height;
        final Rectangle bounds;
        final List<Hit> hits = new ArrayList<>();

        Painted(RenderTooltipEvent event) {
            context = event.context;
            page = event.page;
            width = Minecraft.getMinecraft().displayWidth;
            height = Minecraft.getMinecraft().displayHeight;
            bounds = new Rectangle(event.x, event.y, event.width, event.height);
        }

        boolean contains(GuiScreen gui, int x, int y) {
            return gui != null && gui == screen
                && gui == Minecraft.getMinecraft().currentScreen
                && TooltipAnimationClock.nowNanos() - time <= FRESH_NANOS
                && width == Minecraft.getMinecraft().displayWidth
                && height == Minecraft.getMinecraft().displayHeight
                && context.getScaleFactor() == TooltipUtils.getTooltipScale()
                && page == context.getActivePageComponent()
                && bounds.contains(x, y);
        }
    }

    private NativeTooltipMouse() {}

    static void newFrame() {
        pending.clear();
        previous.clear();
        previous.addAll(painted);
        painted.clear();
    }

    static void clear() {
        pending.clear();
        painted.clear();
        previous.clear();
    }

    static void chartLabel(TooltipContext context, String label, double x, double y, float scale,
        BooleanSupplier action) {
        for (TooltipInput.Run run : TooltipInput.materials(label).runs()) {
            if (run.itemId != null || run.fluidId != null) x += TooltipInput.MATERIAL_ADVANCE * scale;
            int width = TooltipFontContext.getStringWidth(run.text);
            add(context, x, y, width * scale, TooltipFontContext.getFontHeight() * scale, action, null);
            x += width * scale;
        }
    }

    static void action(TooltipContext context, double x, double y, double width, double height,
        BooleanSupplier action) {
        add(context, x, y, width, height, action, null);
    }

    private static void add(TooltipContext context, double x, double y, double width, double height, BooleanSupplier action,
        MaterialSprite.Material material) {
        if (context == null || width <= 0 || height <= 0) return;
        pending.computeIfAbsent(context, ignored -> new ArrayList<>())
            .add(new Hit(new Rectangle2D.Double(x, y, width, height), action, material));
    }

    static void rendered(RenderTooltipEvent event) {
        List<Hit> hits = pending.remove(event.context);
        if (!event.context.getTarget()
            .isFluid() && TooltipPages.identity(event.context.getItem())
                .isEmpty())
            return;
        Painted window = new Painted(event);
        painted.add(window);
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
            Rectangle2D bounds = hit.bounds;
            Rectangle2D scaled = new Rectangle2D.Double(
                dx + bounds.getX() * scale,
                dy + bounds.getY() * scale,
                bounds.getWidth() * scale,
                bounds.getHeight() * scale);
            Rectangle2D clipped = scaled.createIntersection(window.bounds);
            if (!clipped.isEmpty()) window.hits.add(new Hit(clipped, hit.action, hit.material));
        }
    }

    static Rectangle bounds(TooltipContext context) {
        for (Painted window : painted) if (window.context == context) return window.bounds;
        return null;
    }

    private static Painted windowAt(GuiScreen gui, int x, int y, List<Painted> windows) {
        for (int i = windows.size() - 1; i >= 0; i--) {
            Painted window = windows.get(i);
            if (window.contains(gui, x, y)) return window;
        }
        return null;
    }

    static TooltipContext contextAt(GuiScreen gui, Point mouse, boolean beforeDraw) {
        Painted window = windowAt(gui, mouse.x, mouse.y, beforeDraw ? previous : painted);
        return window == null ? null : window.context;
    }

    static MaterialHit materialAt(GuiScreen gui, Point mouse, boolean beforeDraw) {
        Painted window = windowAt(gui, mouse.x, mouse.y, beforeDraw ? previous : painted);
        if (window == null) return null;
        for (int i = window.hits.size() - 1; i >= 0; i--) {
            Hit hit = window.hits.get(i);
            if (hit.material != null && hit.bounds.contains(mouse)) return new MaterialHit(hit.bounds, hit.material);
        }
        return null;
    }

    static final class Pointer {
        final TooltipContext context;
        final BooleanSupplier action;
        final MaterialSprite.Material material;

        Pointer(TooltipContext context, BooleanSupplier action, MaterialSprite.Material material) {
            this.context = context;
            this.action = action;
            this.material = material;
        }
    }

    /** Geometry only: neither a painted rectangle nor a hit grants input ownership. */
    static Pointer pointerAt(GuiScreen gui, Point mouse) {
        Painted window = windowAt(gui, mouse.x, mouse.y, painted);
        if (window == null) return null;
        BooleanSupplier action = null;
        MaterialSprite.Material material = null;
        for (int i = window.hits.size() - 1; i >= 0; i--) {
            Hit hit = window.hits.get(i);
            if (!hit.bounds.contains(mouse)) continue;
            if (action == null) action = hit.action;
            if (material == null) material = hit.material;
        }
        return new Pointer(window.context, action, material);
    }
}
