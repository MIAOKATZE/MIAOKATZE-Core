// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import java.util.Objects;

import net.minecraft.util.ResourceLocation;

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

    NativePinComponent(String item) {
        this.item = item;
    }

    private String binding() {
        return TooltipController.pinKeyLabel() + "]";
    }

    @Override
    public int getWidth() {
        return TooltipFontContext.getStringWidth("[" + binding()) + 14;
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
        return new TextComponent("[" + binding()).paginate(context, Math.max(1, width), height);
    }

    @Override
    public void draw(int x, int y, int width, TooltipContext context) {
        boolean pinned = TooltipPinning.isPinned(context), reduced = !TooltipPages.animationEnabled(item);
        int frame = TooltipPinVisual.frame(pinned, TooltipAnimationClock.nowNanos(), reduced);
        int ink = TooltipPinVisual.binding(pinned, frame, reduced);
        TooltipFontContext.drawString("[", x, y, ink);
        int iconX = x + TooltipFontContext.getStringWidth("[") + 1;
        NativeGuideComponent
            .drawSprite(ATLAS, TooltipPinVisual.cel(pinned, frame) * 12, 0, 12, 12, 96, 12, iconX, y - 1);
        TooltipFontContext.drawString(binding(), iconX + 13, y, ink);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NativePinComponent && item.equals(((NativePinComponent) other).item);
    }
}
