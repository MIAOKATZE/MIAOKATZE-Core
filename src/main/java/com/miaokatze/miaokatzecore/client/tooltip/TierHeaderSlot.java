// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.slprime.chromatictooltips.api.ITooltipComponent;
import com.slprime.chromatictooltips.api.TooltipContext;
import com.slprime.chromatictooltips.component.InlineComponent;
import com.slprime.chromatictooltips.component.KeyboardModifierComponent;
import com.slprime.chromatictooltips.component.SectionComponent;

/** A tier hint stays readable in its body until the assembled header accepts the inline row. */
final class TierHeaderSlot implements ITooltipComponent {

    private final ITooltipComponent hint;
    private boolean moved;

    TierHeaderSlot(ITooltipComponent hint) {
        this.hint = hint;
    }

    static SectionComponent compose(SectionComponent header, SectionComponent body, ITooltipComponent pin) {
        if (header == null) return null;
        List<ITooltipComponent> sections = new ArrayList<>(header.getComponents());
        TierHeaderSlot tier = findTier(body);
        List<ITooltipComponent> controls = new ArrayList<>();
        controls.add(pin);
        if (tier != null) {
            controls.add(tier.hint);
            tier.moved = true;
        }
        int afterTitle = sections.size();
        for (int i = 0; i < sections.size(); i++) {
            if (!(sections.get(i) instanceof SectionComponent)) continue;
            SectionComponent section = (SectionComponent) sections.get(i);
            if ("title".equals(section.getSectionId())) afterTitle = i + 1;
            if (!"keyboard-modifier".equals(section.getSectionId())) continue;

            List<ITooltipComponent> parts = new ArrayList<>(section.getComponents());
            for (int j = 0; j < parts.size(); j++) {
                ITooltipComponent nativeKeys = parts.get(j);
                if (!(nativeKeys instanceof KeyboardModifierComponent)) continue;
                controls.add(0, nativeKeys);
                parts.set(j, new KeyRow(controls));
                sections.set(i, new SectionComponent(section.getSectionId(), section, parts));
                return new SectionComponent(header.getSectionId(), header, sections);
            }
        }
        sections.add(afterTitle, new KeyRow(controls));
        return new SectionComponent(header.getSectionId(), header, sections);
    }

    private static TierHeaderSlot findTier(SectionComponent body) {
        if (body == null) return null;
        for (ITooltipComponent part : body.getComponents()) {
            if (!(part instanceof SectionComponent)) continue;
            SectionComponent section = (SectionComponent) part;
            if (!"itemInfo".equals(section.getSectionId())) continue;
            for (ITooltipComponent component : section.getComponents())
                if (component instanceof TierHeaderSlot && !((TierHeaderSlot) component).moved)
                    return (TierHeaderSlot) component;
        }
        return null;
    }

    private static final class KeyRow extends InlineComponent {

        KeyRow(List<ITooltipComponent> controls) {
            super(Collections.singletonList(controls), 4, 0);
        }

        @Override
        public ITooltipComponent[] paginate(TooltipContext context, int width, int height) {
            List<ITooltipComponent> parts = new ArrayList<>();
            for (ITooltipComponent control : lines.get(0)) {
                // The body can register native modifiers after the header has been collected.
                if (control instanceof KeyboardModifierComponent || control.getWidth() > width) {
                    for (ITooltipComponent part : control.paginate(context, width, height))
                        if (part.getHeight() > 0) parts.add(part);
                } else if (control.getHeight() > 0) parts.add(control);
            }
            return new InlineComponent(Collections.singletonList(parts), 4, 0).paginate(context, width, height);
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
