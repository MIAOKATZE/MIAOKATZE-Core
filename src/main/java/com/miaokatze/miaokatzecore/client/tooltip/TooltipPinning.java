// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.PanelManager;
import com.miaokatze.miaokatzecore.api.tooltip.TooltipAnimationClock;
import com.miaokatze.miaokatzecore.api.tooltip.TooltipPages;
import com.slprime.chromatictooltips.TooltipHandler;
import com.slprime.chromatictooltips.TooltipRegistry;
import com.slprime.chromatictooltips.api.TooltipLines;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipPageBody;
import com.slprime.chromatictooltips.api.TooltipContext;
import com.slprime.chromatictooltips.api.TooltipModifier;
import com.slprime.chromatictooltips.api.TooltipRequest;
import com.slprime.chromatictooltips.api.TooltipTarget;
import com.slprime.chromatictooltips.event.RenderTooltipEvent;
import com.slprime.chromatictooltips.util.TooltipSpacing;
import com.slprime.chromatictooltips.util.TooltipUtils;

/** Keeps the rendered root and its optional material window in the owning GUI. */
public final class TooltipPinning {

    private static final int GAP = 4;
    private static final TooltipPinState<Frame> state = new TooltipPinState<>();
    private static Frame preview;
    private static Rectangle2D source;
    private static boolean drawing;
    private static Object world;
    private static Object panel;

    private static final class Frame {

        final TooltipRequest request;
        final MaterialSprite.Material material;
        final Point cursor = TooltipUtils.getMousePosition();
        final long captured = TooltipAnimationClock.nowNanos();
        TooltipContext context;
        TooltipContext template;
        TooltipPageBody document;
        Rectangle bounds;
        TooltipModifier modifier = TooltipUtils.getActiveModifier();
        boolean dirty;

        Frame(TooltipRequest request, TooltipContext context, Rectangle bounds, MaterialSprite.Material material) {
            this.request = request;
            this.context = context;
            this.bounds = bounds;
            this.material = material;
            this.document = NativeTooltipClient.document(context);
        }

        Frame frozen() {
            TooltipTarget real = context.getTarget();
            TooltipTarget target = real.isFluid() ? TooltipTarget.ofFluid(real.getFluid(), real.getStackAmount())
                : TooltipTarget.ofItem(real.getItem(), real.getStackAmount());
            if (real.isFluidContainer())
                target = target.withContainedFluid(real.getContainedFluid(), real.getContainedFluidAmount());
            Frame fixed = new Frame(new TooltipRequest(request, target), null, bounds, material);
            fixed.template = context;
            fixed.document = document;
            // Keep the already painted page for the first pinned frame. Future rebuilds use the copied target.
            fixed.modifier = modifier;
            fixed.dirty = dirty;
            return fixed;
        }

        void update() {
            TooltipModifier active = TooltipUtils.getActiveModifier();
            if (context == null || dirty
                || modifier != active
                || context.getScaleFactor() != TooltipUtils.getTooltipScale()) {
                context = TooltipHandler.createTooltipContext(request, context == null ? template : context);
                template = null;
                document = NativeTooltipClient.document(context);
                modifier = active;
                dirty = false;
            }
        }

        String key() {
            return TooltipPages.identity(request.target.getItem());
        }

        void draw(int x, int y) {
            context.drawAt(x, y);
            Rectangle painted = NativeTooltipMouse.bounds(context);
            if (painted != null) bounds = painted;
            TooltipPages.rendered(key());
        }

        void drawAtSavedPosition(ScaledResolution size) {
            update();
            Dimension dimensions = context.getTooltipSize();
            if (dimensions == null) return;
            TooltipSpacing margin = context.getActivePageComponent()
                .getMargin();
            double scale = context.getScaleFactor() / (double) size.getScaleFactor();
            draw(
                clamp(bounds.x - (int) (margin.getLeft() * scale), size.getScaledWidth() - dimensions.width),
                clamp(bounds.y - (int) (margin.getTop() * scale), size.getScaledHeight() - dimensions.height));
        }
    }

    private TooltipPinning() {}

    public static boolean pinned() {
        return state.fixed() != null;
    }

    public static boolean active(GuiScreen gui) {
        return state.active(gui);
    }

    public static String key() {
        Frame frame = selected();
        return frame == null ? "" : frame.key();
    }

    public static ItemStack pinnedStack() {
        TooltipContext context = context();
        return context == null || context.getItem() == null ? null
            : context.getItem()
                .copy();
    }

    public static TooltipContext context() {
        Frame frame = selected();
        return frame == null ? null : frame.context;
    }

    public static int x() {
        return pinned() ? state.fixed().bounds.x : 0;
    }

    public static int y() {
        return pinned() ? state.fixed().bounds.y : 0;
    }

    static boolean isPinned(TooltipContext context) {
        return hasContext(state.fixed(), context) || hasContext(state.child(), context);
    }

    private static boolean hasContext(Frame frame, TooltipContext context) {
        return frame != null && context != null && frame.context == context;
    }

    private static Frame selected() {
        return selected(TooltipUtils.getMousePosition());
    }

    private static Frame selected(Point mouse) {
        Frame root = state.fixed();
        Frame child = state.child();
        if (child == null) return root;
        TooltipContext context = NativeTooltipMouse.contextAt(Minecraft.getMinecraft().currentScreen, mouse, false);
        return hasContext(root, context) ? root : child;
    }

    static void screen(GuiScreen gui) {
        Object currentWorld = Minecraft.getMinecraft().theWorld;
        ModularScreen modular = ModularScreen.getCurrent();
        PanelManager panels = modular == null ? null : modular.getPanelManager();
        Object currentPanel = panels == null || panels.getOpenPanelCount() == 0 ? null : panels.getTopMostPanel();
        if (world != currentWorld || panel != currentPanel) clear();
        world = currentWorld;
        panel = currentPanel;
        if (state.screen(gui)) clearPaint();
    }

    static void clear() {
        state.screen(null);
        clearPaint();
    }

    private static void clearPaint() {
        preview = null;
        source = null;
        NativeTooltipMouse.clear();
    }

    static void beginFrame(GuiScreen gui) {
        screen(gui);
        state.beginFrame(gui);
    }

    /** Capture only what was actually drawn; public context components preserve GUI-specific extra lines. */
    static void rendered(RenderTooltipEvent event) {
        if (drawing) return;
        TooltipTarget target = event.context.getTarget();
        if (!target.isFluid() && TooltipPages.identity(target.getItem()).isEmpty()) return;
        TooltipRequest request = new TooltipRequest(event.context.getContextName(), target,
            new TooltipLines(event.context.getContextTooltip()), null);
        state.hover(Minecraft.getMinecraft().currentScreen, new Frame(request, event.context,
            new Rectangle(event.x, event.y, event.width, event.height), null));
    }

    static boolean interactive(TooltipContext context) {
        return state.fixed() != null && (hasContext(state.fixed(), context) || hasContext(state.child(), context)
            || hasContext(preview, context));
    }

    static TooltipPageBody inputDocument(GuiScreen gui, Point mouse, Supplier<ItemStack> stackAtPointer) {
        screen(gui);
        Frame frame = active(gui) ? selected(mouse) : state.hovered();
        if (frame == null || !active(gui) && !pointedAtHover(gui, frame, mouse, stackAtPointer)) return null;
        return frame.document;
    }

    static String inputKey(GuiScreen gui, Point mouse, Supplier<ItemStack> stackAtPointer) {
        Frame frame = active(gui) ? selected(mouse) : state.hovered();
        if (frame == null || !active(gui) && !pointedAtHover(gui, frame, mouse, stackAtPointer)) return "";
        return frame.key();
    }

    static MaterialSprite.Material windowMaterial(TooltipContext context) {
        if (hasContext(state.child(), context)) return state.child().material;
        return hasContext(preview, context) ? preview.material : null;
    }

    private static boolean matchesTarget(TooltipTarget rendered, TooltipTarget requested) {
        return requested.equivalentTo(rendered) || rendered.equivalentTo(TooltipRegistry.sanitizeTarget(requested));
    }

    private static boolean hasTarget(TooltipTarget target) {
        return target != null && (target.isItem() || target.isFluid());
    }

    private static boolean inGap(Point mouse) {
        if (preview == null || preview.bounds == null || source == null) return false;
        return nearWindow(new Point((int) source.getCenterX(), (int) source.getCenterY()), preview.bounds, mouse, 6);
    }

    private static boolean nearWindow(Point start, Rectangle bounds, Point mouse, int radius) {
        double startX = start.x, startY = start.y;
        double endX = Math.max(bounds.getMinX(), Math.min(startX, bounds.getMaxX()));
        double endY = Math.max(bounds.getMinY(), Math.min(startY, bounds.getMaxY()));
        return Line2D.ptSegDist(startX, startY, endX, endY, mouse.x, mouse.y) <= radius;
    }

    static boolean canPinHover(Point cursor, Point mouse, long age, boolean overWindow, boolean occupied,
        boolean sameStack) {
        return (!occupied || sameStack) && (overWindow || age <= 300_000_000L && (cursor.equals(mouse) || sameStack));
    }

    private static boolean pointedAtHover(GuiScreen gui, Frame hover, Point mouse, Supplier<ItemStack> stackAtPointer) {
        TooltipContext hit = NativeTooltipMouse.contextAt(gui, mouse, false);
        ItemStack pointed = hover.cursor.equals(mouse) ? null : stackAtPointer.get();
        boolean sameStack = pointed != null && matchesTarget(hover.context.getTarget(), TooltipTarget.ofItem(pointed));
        return canPinHover(
            hover.cursor,
            mouse,
            TooltipAnimationClock.nowNanos() - hover.captured,
            hasContext(hover, hit),
            pointed != null,
            sameStack);
    }

    static boolean toggle(GuiScreen gui, Point mouse, Supplier<ItemStack> stackAtPointer) {
        screen(gui);
        if (state.fixed() == null) {
            Frame hover = state.hovered();
            if (hover == null || !pointedAtHover(gui, hover, mouse, stackAtPointer)) return false;
            state.hover(gui, hover.frozen());
        }
        if (state.child() != null) {
            preview = null;
            source = null;
            return state.toggle(gui, null);
        }
        Frame childHover = state.fixed() != null && preview != null && childPointed(gui, mouse) ? preview.frozen() : null;
        boolean changed = state.toggle(gui, childHover);
        if (changed && state.fixed() == null) clearPaint();
        if (changed && childHover != null) {
            preview = null;
            source = null;
        }
        return changed;
    }

    private static boolean childPointed(GuiScreen gui, Point mouse) {
        TooltipContext hit = NativeTooltipMouse.contextAt(gui, mouse, false);
        return hasContext(preview, hit) || hasContext(state.fixed(), hit) && source != null && source.contains(mouse);
    }

    public static void refresh() {
        NativeTooltipMouse.clear();
        if (state.fixed() != null) state.fixed().dirty = true;
        if (state.child() != null) state.child().dirty = true;
        if (preview != null) preview.dirty = true;
        if (state.hovered() != null) state.hovered().dirty = true;
    }

    static void followSelected() {
        Frame frame = selected();
        if (frame != null) ChartPointer.follow(frame.context, frame.key());
    }

    /** Runs at Chromatic's end-of-GUI pass, after widget tooltips have submitted their requests. */
    public static boolean drawPinned() {
        GuiScreen gui = Minecraft.getMinecraft().currentScreen;
        screen(gui);
        Frame root = state.pinnedRoot(gui);
        if (root == null) {
            preview = null;
            source = null;
            return false;
        }

        drawing = true;
        try {
            ScaledResolution size = TooltipUtils.getScaledResolution();
            root.drawAtSavedPosition(size);
            if (state.child() != null) state.child()
                .drawAtSavedPosition(size);
            else drawPreview(gui, root, size);
        } finally {
            drawing = false;
        }
        return true;
    }

    private static void drawPreview(GuiScreen gui, Frame root, ScaledResolution size) {
        Point mouse = TooltipUtils.getMousePosition();
        TooltipContext previous = NativeTooltipMouse.contextAt(gui, mouse, true);
        NativeTooltipMouse.MaterialHit hit = NativeTooltipMouse.materialAt(gui, mouse, false);
        if (!hasContext(preview, previous) && hit != null) {
            if (preview == null || !preview.material.equals(hit.material)) {
                TooltipRequest request = new TooltipRequest(null, hit.material.target(), null, null);
                preview = new Frame(request, null, null, hit.material);
            }
            source = hit.bounds;
        } else if (!hasContext(preview, previous) && !inGap(mouse)) {
            preview = null;
            source = null;
        }
        if (preview == null) return;
        preview.update();
        Dimension dimensions = preview.context.getTooltipSize();
        if (dimensions == null) return;
        Point position = placeChild(root.bounds, source, dimensions, size.getScaledWidth(), size.getScaledHeight());
        preview.draw(position.x, position.y);
    }

    static Point placeChild(Rectangle parent, Rectangle2D source, Dimension child, int width, int height) {
        int right = parent.x + parent.width + GAP;
        int left = parent.x - child.width - GAP;
        int below = parent.y + parent.height + GAP;
        int above = parent.y - child.height - GAP;
        int x = clamp((int) source.getX(), width - child.width);
        int y = clamp((int) source.getY(), height - child.height);
        if (right + child.width <= width) return new Point(right, y);
        if (left >= 0) return new Point(left, y);
        if (below + child.height <= height) return new Point(x, below);
        if (above >= 0) return new Point(x, above);
        int belowSource = (int) Math.ceil(source.getMaxY()) + GAP;
        int aboveSource = (int) source.getMinY() - child.height - GAP;
        return new Point(
            x,
            clamp(belowSource + child.height <= height ? belowSource : aboveSource, height - child.height));
    }

    private static int clamp(int position, int max) {
        return Math.max(0, Math.min(position, max));
    }
}
