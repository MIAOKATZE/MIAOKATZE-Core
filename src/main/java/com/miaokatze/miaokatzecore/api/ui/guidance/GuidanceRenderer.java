package com.miaokatze.miaokatzecore.api.ui.guidance;

import com.miaokatze.miaokatzecore.api.ui.component.UiRect;
import com.miaokatze.miaokatzecore.api.ui.component.UiRenderer;

/**
 * Passive visuals using an already-begun UiRenderer; hosts own eligibility, input and persistence.
 * Pointer pixels and expanding click corners follow GTMB's editor tutorial. No simulated input is dispatched.
 */
public final class GuidanceRenderer {

    private GuidanceRenderer() {}

    public static void render(UiRenderer renderer, UiRect anchor, UiRect viewport, String title, String detail,
        long elapsedMillis, boolean click, UiRect... reserved) {
        if (anchor == null || anchor.empty() || viewport.empty()) return;
        renderer.pushClip(viewport);
        try {
            if (anchor.intersect(viewport)
                .empty()) return;
            double pulse = .5 + .5 * Math.sin(elapsedMillis / 420.0);
            int accent = ((int) (165 + pulse * 90) << 24) | 0xF2CE91;
            frame(renderer, anchor, 2, accent);
            double pointerX = Math.max(viewport.x, Math.min(viewport.right() - 8, anchor.x + anchor.width * .62));
            double pointerY = Math.max(viewport.y, Math.min(viewport.bottom() - 13, anchor.y + anchor.height * .55));
            UiRect pointer = new UiRect(pointerX - 5, pointerY - 5, 18, 22);
            UiRect[] exclusions = new UiRect[(reserved == null ? 0 : reserved.length) + (click ? 1 : 0)];
            if (reserved != null) System.arraycopy(reserved, 0, exclusions, 0, reserved.length);
            if (click) exclusions[exclusions.length - 1] = pointer;
            double width = Math.min(232, Math.max(renderer.textWidth(title), renderer.textWidth(detail)) + 16);
            UiRect caption = GuidanceLayout.place(anchor, viewport, width, renderer.textHeight() * 2 + 16, exclusions);
            if (caption != null) card(renderer, caption, title, detail, elapsedMillis);
            if (click) {
                clickCorners(renderer, anchor, elapsedMillis);
                long phase = Math.floorMod(elapsedMillis, 1440);
                pointer(renderer, pointerX, pointerY, phase >= 720 && phase < 1080);
            }
        } finally {
            renderer.popClip();
        }
    }

    /** Card-only entry point for hosts with an existing HUD/minimap reservation policy. */
    public static void card(UiRenderer renderer, UiRect box, String title, String detail, long elapsedMillis) {
        if (box.empty()) return;
        renderer.pushClip(box);
        try {
            renderer.fill(box, 0xE634302A);
            int accent = ((int) (165 + (.5 + .5 * Math.sin(elapsedMillis / 420.0)) * 90) << 24) | 0xF2CE91;
            fill(renderer, box.x, box.y, box.width, 1, accent);
            fill(renderer, box.x, box.y, 2, box.height, accent);
            renderer.text(fit(renderer, title, box.width - 14), box.x + 7, box.y + 5, 0xFFFFE7B4);
            renderer
                .text(fit(renderer, detail, box.width - 14), box.x + 7, box.y + renderer.textHeight() + 10, 0xFFD4C9B6);
        } finally {
            renderer.popClip();
        }
    }

    private static void clickCorners(UiRenderer renderer, UiRect anchor, long elapsedMillis) {
        int phase = (int) (Math.floorMod(elapsedMillis, 1440) / 180);
        if (phase < 3 || phase > 6) return;
        int radius = phase - 2, color = phase == 6 ? 0x88FFD692 : 0xFFF2CE91;
        double length = Math.min(6, anchor.width / 2) + radius;
        fill(renderer, anchor.x - radius, anchor.y - radius, length, 1, color);
        fill(renderer, anchor.right() + radius - length, anchor.y - radius, length, 1, color);
        fill(renderer, anchor.x - radius, anchor.bottom() + radius - 1, length, 1, color);
        fill(renderer, anchor.right() + radius - length, anchor.bottom() + radius - 1, length, 1, color);
    }

    private static void pointer(UiRenderer renderer, double x, double y, boolean pressed) {
        if (pressed) fill(renderer, x - 4, y - 4, 12, 12, 0x70EAB85F);
        for (int row = 0; row < 12; row++) {
            int width = Math.min(row / 2 + 1, 6);
            fill(renderer, x - 1, y + row - 1, width + 2, 2, 0xFF30231A);
            fill(renderer, x, y + row, width, 1, 0xFFFFE7B4);
        }
    }

    private static void frame(UiRenderer renderer, UiRect box, double edge, int color) {
        edge = Math.min(edge, Math.min(box.width, box.height) / 2);
        fill(renderer, box.x, box.y, box.width, edge, color);
        fill(renderer, box.x, box.bottom() - edge, box.width, edge, color);
        fill(renderer, box.x, box.y + edge, edge, box.height - edge * 2, color);
        fill(renderer, box.right() - edge, box.y + edge, edge, box.height - edge * 2, color);
    }

    private static void fill(UiRenderer renderer, double x, double y, double width, double height, int color) {
        renderer.fill(new UiRect(x, y, width, height), color);
    }

    private static String fit(UiRenderer renderer, String text, double width) {
        if (width <= 0) return "";
        if (renderer.textWidth(text) <= width) return text;
        String suffix = renderer.textWidth("...") <= width ? "..." : "";
        int end = text.length();
        while (end > 0 && renderer.textWidth(text.substring(0, end) + suffix) > width)
            end = text.offsetByCodePoints(end, -1);
        return text.substring(0, end) + suffix;
    }
}
