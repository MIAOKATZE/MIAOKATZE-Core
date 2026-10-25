package com.miaokatze.miaokatzecore.api.ui.guidance;

import java.util.Objects;

import com.miaokatze.miaokatzecore.api.ui.component.UiRect;

/** Stateless caption placement in the caller's GUI coordinates; targets and reserved surfaces stay clear. */
public final class GuidanceLayout {

    private GuidanceLayout() {}

    /** Returns null when no readable, unobstructed caption fits. The target may still be highlighted. */
    public static UiRect place(UiRect anchor, UiRect viewport, double width, double height, UiRect... reserved) {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(viewport, "viewport");
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0)
            throw new IllegalArgumentException("Invalid caption size");
        double gap = 8, left = viewport.x + 4, top = viewport.y + 4;
        double right = viewport.right() - 4, bottom = viewport.bottom() - 4;
        width = Math.min(width, right - left);
        if (width < 24 || bottom - top < height) return null;
        double[][] candidates = { { anchor.right() + gap, anchor.y + (anchor.height - height) / 2 },
            { anchor.x - width - gap, anchor.y + (anchor.height - height) / 2 }, { anchor.x, anchor.bottom() + gap },
            { anchor.x, anchor.y - height - gap }, { left, top }, { right - width, top }, { left, bottom - height },
            { right - width, bottom - height } };
        UiRect best = null;
        double distance = Double.POSITIVE_INFINITY;
        for (double[] candidate : candidates) {
            UiRect box = new UiRect(
                Math.max(left, Math.min(right - width, candidate[0])),
                Math.max(top, Math.min(bottom - height, candidate[1])),
                width,
                height);
            if (overlaps(box, anchor, 3)) continue;
            boolean blocked = false;
            if (reserved != null) for (UiRect obstacle : reserved) {
                if (obstacle != null && overlaps(box, obstacle, 3)) {
                    blocked = true;
                    break;
                }
            }
            if (blocked) continue;
            double dx = Math.max(0, Math.max(anchor.x - box.right(), box.x - anchor.right()));
            double dy = Math.max(0, Math.max(anchor.y - box.bottom(), box.y - anchor.bottom()));
            double next = dx * dx + dy * dy;
            if (next < distance) {
                best = box;
                distance = next;
            }
        }
        return best;
    }

    private static boolean overlaps(UiRect a, UiRect b, double gap) {
        return a.x < b.right() + gap && a.right() > b.x - gap && a.y < b.bottom() + gap && a.bottom() > b.y - gap;
    }
}
