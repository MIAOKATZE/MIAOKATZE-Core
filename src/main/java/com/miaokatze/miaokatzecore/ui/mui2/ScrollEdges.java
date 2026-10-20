package com.miaokatze.miaokatzecore.ui.mui2;

import com.cleanroommc.modularui.drawable.GuiDraw;

/** Draw-only decoration. It does not own clipping, scrolling, focus or hit rectangles. */
public final class ScrollEdges {

    private ScrollEdges() {}

    public static final class Style {

        public final int depth, sideWidth, topShade, bottomShade, sideShade, topBorder, border;

        public Style(int depth, int sideWidth, int topShade, int bottomShade, int sideShade, int topBorder,
            int border) {
            if (depth < 1 || sideWidth < 1) throw new IllegalArgumentException("Positive edge dimensions required");
            this.depth = depth;
            this.sideWidth = sideWidth;
            this.topShade = topShade;
            this.bottomShade = bottomShade;
            this.sideShade = sideShade;
            this.topBorder = topBorder;
            this.border = border;
        }
    }

    public static void draw(int x, int y, int width, int height, Style style) {
        if (width < 3 || height < 3) return;
        int depth = Math.min(style.depth, Math.max(1, height / 8));
        int side = Math.min(style.sideWidth, width - 2);
        int topTransparent = style.topShade & 0x00ffffff;
        int bottomTransparent = style.bottomShade & 0x00ffffff;
        int sideTransparent = style.sideShade & 0x00ffffff;
        GuiDraw.drawVerticalGradientRect(x + 1, y + 1, width - 2, depth, style.topShade, topTransparent);
        GuiDraw.drawVerticalGradientRect(
            x + 1,
            y + height - depth - 1,
            width - 2,
            depth,
            bottomTransparent,
            style.bottomShade);
        GuiDraw.drawHorizontalGradientRect(x + 1, y + 1, side, height - 2, style.sideShade, sideTransparent);
        GuiDraw.drawHorizontalGradientRect(
            x + width - side - 1,
            y + 1,
            side,
            height - 2,
            sideTransparent,
            style.sideShade);
        GuiDraw.drawRect(x, y, width, 1, style.topBorder);
        GuiDraw.drawRect(x, y + height - 1, width, 1, style.border);
        GuiDraw.drawRect(x, y + 1, 1, height - 2, style.border);
        GuiDraw.drawRect(x + width - 1, y + 1, 1, height - 2, style.border);
    }
}
