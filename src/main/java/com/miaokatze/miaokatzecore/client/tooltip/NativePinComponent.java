// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import java.util.Objects;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import com.miaokatze.miaokatzecore.api.tooltip.TooltipAnimationClock;
import com.miaokatze.miaokatzecore.api.tooltip.TooltipPages;
import com.slprime.chromatictooltips.api.ITooltipComponent;
import com.slprime.chromatictooltips.api.TooltipContext;
import com.slprime.chromatictooltips.component.TextComponent;
import com.slprime.chromatictooltips.util.TooltipFontContext;

/** Shared pin key. Paint state belongs to this window, since two windows may show the same item. */
final class NativePinComponent implements ITooltipComponent {

    private static final ResourceLocation ATLAS = new ResourceLocation("miaokatzecore", "textures/tooltip/pin.png");
    private final String item;
    private final boolean icon;

    NativePinComponent(String item) {
        this(item, true);
    }

    private NativePinComponent(String item, boolean icon) {
        this.item = item;
        this.icon = icon;
    }

    private static String binding(boolean pinned) {
        String state = StatCollector
            .translateToLocal(pinned ? "miaokatzecore.tooltip.pin.pinned" : "miaokatzecore.tooltip.pin.floating");
        return TooltipController.pinKeyLabel() + " " + state + "]";
    }

    @Override
    public int getWidth() {
        return TooltipFontContext.getStringWidth("[") + Math
            .max(TooltipFontContext.getStringWidth(binding(false)), TooltipFontContext.getStringWidth(binding(true)))
            + (icon ? 14 : 0);
    }

    @Override
    public int getHeight() {
        return Math.max(12, TooltipFontContext.getFontHeight());
    }

    @Override
    public int getSpacing() {
        return 1;
    }

    @Override
    public ITooltipComponent[] paginate(TooltipContext context, int width, int height) {
        if (getWidth() <= width && getHeight() <= height) return new ITooltipComponent[] { this };
        if (icon) return new NativePinComponent(item, false).paginate(context, width, height);
        return new TextComponent("[" + TooltipController.pinKeyLabel() + "]")
            .paginate(context, Math.max(1, width), height);
    }

    @Override
    public void draw(int x, int y, int width, TooltipContext context) {
        boolean pinned = TooltipPinning.isPinned(context), reduced = !TooltipPages.animationEnabled(item);
        int frame = TooltipPinVisual.frame(pinned, TooltipAnimationClock.nowNanos(), reduced);
        int ink = TooltipPinVisual.binding(pinned, frame, reduced);
        TooltipFontContext.drawString("[", x, y, ink);
        int textX = x + TooltipFontContext.getStringWidth("[");
        if (icon) {
            NativeGuideComponent
                .drawSprite(ATLAS, TooltipPinVisual.cel(pinned, frame) * 12, 0, 12, 12, 96, 12, textX + 1, y - 1);
            textX += 14;
        }
        TooltipFontContext.drawString(binding(pinned), textX, y, ink);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, icon);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof NativePinComponent)) return false;
        NativePinComponent component = (NativePinComponent) other;
        return icon == component.icon && item.equals(component.item);
    }
}
