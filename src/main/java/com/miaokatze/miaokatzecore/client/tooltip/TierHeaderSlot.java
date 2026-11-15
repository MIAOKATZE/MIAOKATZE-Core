// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import com.slprime.chromatictooltips.api.ITooltipComponent;
import com.slprime.chromatictooltips.api.TooltipContext;
import com.slprime.chromatictooltips.component.KeyboardModifierComponent;
import com.slprime.chromatictooltips.component.SectionComponent;

/** The authored tier hint stays in itemInfo unless the assembled header contains the native title and key bar. */
final class TierHeaderSlot implements ITooltipComponent {

    private final ITooltipComponent hint;
    private boolean moved;

    TierHeaderSlot(ITooltipComponent hint) {
        this.hint = hint;
    }

    static void place(SectionComponent header, SectionComponent body) {
        if (header == null || body == null) return;
        SectionComponent title = null;
        boolean keyboardAfterTitle = false;
        for (ITooltipComponent part : header.getComponents()) {
            if (!(part instanceof SectionComponent)) continue;
            SectionComponent section = (SectionComponent) part;
            if ("title".equals(section.getSectionId()) && !section.isEmpty()) title = section;
            if (title != null && "keyboard-modifier".equals(section.getSectionId())
                && section.getComponents()
                    .stream()
                    .anyMatch(KeyboardModifierComponent.class::isInstance)) {
                keyboardAfterTitle = true;
                break;
            }
        }
        if (!keyboardAfterTitle) return;

        for (ITooltipComponent part : body.getComponents()) {
            if (!(part instanceof SectionComponent)) continue;
            SectionComponent info = (SectionComponent) part;
            if (!"itemInfo".equals(info.getSectionId())) continue;
            for (ITooltipComponent component : info.getComponents()) {
                if (!(component instanceof TierHeaderSlot)) continue;
                TierHeaderSlot slot = (TierHeaderSlot) component;
                if (!slot.moved) {
                    title.addComponent(slot.hint);
                    slot.moved = true;
                }
                return;
            }
        }
    }

    @Override
    public int getWidth() {
        return moved ? 0 : hint.getWidth();
    }

    @Override
    public int getHeight() {
        return moved ? 0 : hint.getHeight();
    }

    @Override
    public int getSpacing() {
        return moved ? 0 : hint.getSpacing();
    }

    @Override
    public ITooltipComponent[] paginate(TooltipContext context, int width, int height) {
        // SectionComponent.paginate ignores zero-height parts and recalculates every parent section's size.
        return moved ? new ITooltipComponent[] { this } : hint.paginate(context, width, height);
    }

    @Override
    public void draw(int x, int y, int width, TooltipContext context) {
        if (!moved) hint.draw(x, y, width, context);
    }

    @Override
    public int hashCode() {
        return hint.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TierHeaderSlot && hint.equals(((TierHeaderSlot) o).hint);
    }
}
