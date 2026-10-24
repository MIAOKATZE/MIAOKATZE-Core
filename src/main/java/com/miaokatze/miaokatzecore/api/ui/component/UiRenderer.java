package com.miaokatze.miaokatzecore.api.ui.component;

import com.miaokatze.miaokatzecore.api.ui.CanvasTransform;

/** Host drawing bridge. All drawing and clip coordinates are scene-local; begin applies the screen transform. */
public interface UiRenderer {

    default void begin(CanvasTransform transform) {}

    default void end() {}

    void fill(UiRect rectangle, int argb);

    void text(String text, double x, double y, int argb);

    void pushClip(UiRect rectangle);

    void popClip();

    default double textWidth(String text) {
        return text.length() * 6.0;
    }

    default double textHeight() {
        return 9;
    }
}
