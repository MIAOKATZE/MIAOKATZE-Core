// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

import com.miaokatze.miaokatzecore.api.tooltip.TooltipPages;
import com.slprime.chromatictooltips.TooltipHandler;
import com.slprime.chromatictooltips.api.TooltipContext;
import com.slprime.chromatictooltips.api.TooltipModifier;
import com.slprime.chromatictooltips.api.TooltipRequest;
import com.slprime.chromatictooltips.api.TooltipTarget;
import com.slprime.chromatictooltips.event.RenderTooltipEvent;
import com.slprime.chromatictooltips.util.TooltipUtils;

/** Optional Chromatic adapter. Captures the actual rendered item, context, page and outer position. */
public final class TooltipPinning {

    private static final TooltipPinState<Frame> state = new TooltipPinState<>();
    private static TooltipRequest pending;
    private static boolean drawing;
    private static boolean dirty;
    private static Object world;

    private static final class Frame {

        ItemStack stack;
        TooltipRequest request;
        TooltipContext context;
        final int x, y;
        TooltipModifier modifier;

        Frame(TooltipRequest original, RenderTooltipEvent rendered) {
            // Ordinary hover only borrows this frame's references; large item NBT is copied on the pin action.
            stack = rendered.context.getItem();
            request = original;
            context = rendered.context;
            x = rendered.x;
            y = rendered.y;
            modifier = context.getActiveModifier();
        }

        void freeze() {
            stack = stack.copy();
            TooltipTarget target = TooltipTarget.ofItem(stack, request.target.getStackAmount());
            if (request.target.isFluidContainer()) target = target
                .withContainedFluid(request.target.getContainedFluid(), request.target.getContainedFluidAmount());
            request = new TooltipRequest(request, target);
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
        return pinned() ? TooltipPages.identity(state.fixed().stack) : "";
    }

    public static ItemStack pinnedStack() {
        return pinned() ? state.fixed().stack.copy() : null;
    }

    public static TooltipContext context() {
        return pinned() ? state.fixed().context : null;
    }

    public static int x() {
        return pinned() ? state.fixed().x : 0;
    }

    public static int y() {
        return pinned() ? state.fixed().y : 0;
    }

    static void screen(GuiScreen gui) {
        Object currentWorld = Minecraft.getMinecraft().theWorld;
        if (world != currentWorld) state.screen(null);
        world = currentWorld;
        state.screen(gui);
        if (!pinned()) dirty = false;
    }

    static void beginFrame(GuiScreen gui) {
        screen(gui);
        state.beginFrame(gui);
        pending = null;
    }

    /** Called before Chromatic accepts a new request, so another hovered item cannot replace the pinned window. */
    public static boolean request(TooltipRequest request) {
        GuiScreen gui = Minecraft.getMinecraft().currentScreen;
        screen(gui);
        if (drawing) return false;
        if (active(gui)) return true;
        pending = request;
        return false;
    }

    static void rendered(RenderTooltipEvent event) {
        if (drawing || event.context.getItem() == null
            || pending == null
            || pending.target == null
            || pending.target.getItem() == null) return;
        if (!ItemStack.areItemStacksEqual(pending.target.getItem(), event.context.getItem())) return;
        state.hover(Minecraft.getMinecraft().currentScreen, new Frame(pending, event));
    }

    static boolean toggle(GuiScreen gui) {
        boolean wasPinned = pinned();
        if (!wasPinned && state.hovered() != null) state.hovered()
            .freeze();
        if (!state.toggle(gui)) return false;
        dirty = !wasPinned;
        return true;
    }

    /** Marks authored values stale; the next fixed draw rebuilds them through Chromatic's real item enrichers. */
    public static void refresh() {
        dirty = true;
    }

    static void followSelected() {
        if (pinned()) ChartPointer.follow(state.fixed().context, key());
    }

    /** Runs at the native end-of-GUI tooltip pass, including frames with no hovered item. */
    public static boolean drawPinned() {
        GuiScreen gui = Minecraft.getMinecraft().currentScreen;
        screen(gui);
        if (!active(gui)) return false;
        Frame frame = state.fixed();
        drawing = true;
        try {
            TooltipModifier modifier = TooltipUtils.getActiveModifier();
            if (dirty || modifier != frame.modifier) {
                // The copied target, original context text and prior page are retained; itemInfo is rebuilt by
                // Chromatic's ItemInfoEnricher, including the target's own Shift structure and third-party lines.
                frame.context = TooltipHandler.createTooltipContext(frame.request, frame.context);
                frame.modifier = modifier;
                dirty = false;
            }
            double scale = frame.context.getScaleFactor() / (double) TooltipUtils.getScaledResolution()
                .getScaleFactor();
            // RenderTooltipEvent reports the visible frame after the page margin; drawAt expects its origin.
            frame.context.drawAt(
                frame.x - (int) (frame.context.getActivePageComponent()
                    .getMargin()
                    .getLeft() * scale),
                frame.y - (int) (frame.context.getActivePageComponent()
                    .getMargin()
                    .getTop() * scale));
            TooltipPages.rendered(key());
        } finally {
            drawing = false;
        }
        return true;
    }
}
