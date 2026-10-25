package com.miaokatze.miaokatzecore.api.ui.guidance;

/** Immutable ARGB palette for passive guidance. Build once and reuse for every frame. */
public final class GuidanceStyle {

    /** Legacy GTMB colors, retained by renderer overloads without an explicit style. */
    public static final GuidanceStyle DEFAULT = builder().build();

    public final int background, titleText, detailText, highlight, pointerFill, pointerOutline, pressedHint,
        clickCorner, fadedClickCorner;

    private GuidanceStyle(Builder builder) {
        background = builder.background;
        titleText = builder.titleText;
        detailText = builder.detailText;
        highlight = builder.highlight;
        pointerFill = builder.pointerFill;
        pointerOutline = builder.pointerOutline;
        pressedHint = builder.pressedHint;
        clickCorner = builder.clickCorner;
        fadedClickCorner = builder.fadedClickCorner;
    }

    /** Unspecified roles retain the legacy palette; alpha is part of every supplied color. */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private int background = 0xE634302A, titleText = 0xFFFFE7B4, detailText = 0xFFD4C9B6, highlight = 0xFFF2CE91,
            pointerFill = 0xFFFFE7B4, pointerOutline = 0xFF30231A, pressedHint = 0x70EAB85F, clickCorner = 0xFFF2CE91,
            fadedClickCorner = 0x88FFD692;

        private Builder() {}

        public Builder background(int argb) {
            background = argb;
            return this;
        }

        public Builder titleText(int argb) {
            titleText = argb;
            return this;
        }

        public Builder detailText(int argb) {
            detailText = argb;
            return this;
        }

        /** The renderer multiplies this alpha by the original 165..255 highlight pulse. */
        public Builder highlight(int argb) {
            highlight = argb;
            return this;
        }

        public Builder pointerFill(int argb) {
            pointerFill = argb;
            return this;
        }

        public Builder pointerOutline(int argb) {
            pointerOutline = argb;
            return this;
        }

        public Builder pressedHint(int argb) {
            pressedHint = argb;
            return this;
        }

        public Builder clickCorner(int argb) {
            clickCorner = argb;
            return this;
        }

        public Builder fadedClickCorner(int argb) {
            fadedClickCorner = argb;
            return this;
        }

        public GuidanceStyle build() {
            return new GuidanceStyle(this);
        }
    }
}
