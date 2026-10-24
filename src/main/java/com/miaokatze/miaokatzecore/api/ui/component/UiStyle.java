package com.miaokatze.miaokatzecore.api.ui.component;

import java.util.Objects;

/** Immutable state palette; colors use ARGB. State precedence: disabled, pressed, selected, hover, normal. */
public final class UiStyle {

    public static final class Paint {

        public final int background, border, text;

        public Paint(int background, int border, int text) {
            this.background = background;
            this.border = border;
            this.text = text;
        }
    }

    public static final UiStyle DEFAULT = new UiStyle(
        new Paint(0xE81A2230, 0xFF3B4E64, 0xFFE1ECF4),
        new Paint(0xF02D4054, 0xFF6BBFCB, 0xFFFFFFFF),
        new Paint(0xFF14202E, 0xFF80DAE2, 0xFFFFFFFF),
        new Paint(0xFF254955, 0xFF70D5CD, 0xFFFFFFFF),
        new Paint(0xA01A2028, 0xFF303B48, 0xFF697887));
    public static final UiStyle TEXT = new UiStyle(new Paint(0, 0, 0xFFE1ECF4));
    public final Paint normal, hover, pressed, selected, disabled;

    public UiStyle(Paint all) {
        this(all, all, all, all, all);
    }

    public UiStyle(Paint normal, Paint hover, Paint pressed, Paint selected, Paint disabled) {
        this.normal = Objects.requireNonNull(normal, "normal");
        this.hover = Objects.requireNonNull(hover, "hover");
        this.pressed = Objects.requireNonNull(pressed, "pressed");
        this.selected = Objects.requireNonNull(selected, "selected");
        this.disabled = Objects.requireNonNull(disabled, "disabled");
    }

    public Paint resolve(boolean enabled, boolean isHover, boolean isPressed, boolean isSelected) {
        return !enabled ? disabled : isPressed ? pressed : isSelected ? selected : isHover ? hover : normal;
    }
}
