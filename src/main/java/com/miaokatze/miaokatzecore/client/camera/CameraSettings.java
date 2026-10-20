package com.miaokatze.miaokatzecore.client.camera;

import java.util.Objects;

import com.miaokatze.miaokatzecore.api.camera.HeightSlice;

/** Immutable named policies. Consumers may cache a settings instance until a policy or slice request changes. */
public final class CameraSettings {

    private final Render render;
    private final Input input;
    private final Hud hud;
    private final Navigation navigation;
    private final WorldCameras.GuiPolicy guiPolicy;
    private final HeightSlice slice;

    private CameraSettings(Builder builder) {
        render = builder.render;
        input = builder.input;
        hud = builder.hud;
        navigation = builder.navigation;
        guiPolicy = builder.guiPolicy;
        slice = builder.slice;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Render render() {
        return render;
    }

    public Input input() {
        return input;
    }

    public Hud hud() {
        return hud;
    }

    public Navigation navigation() {
        return navigation;
    }

    public WorldCameras.GuiPolicy guiPolicy() {
        return guiPolicy;
    }

    public HeightSlice slice() {
        return slice;
    }

    /** Preserves 0.1.3 behavior: vanilla crosshair, no Core panel/navigation and no slice request. */
    public static CameraSettings legacy(WorldCameras.Options options, WorldCameras.GuiPolicy guiPolicy) {
        Objects.requireNonNull(options, "options");
        return builder().render(
            Render.builder()
                .hideHand(options.hideHand)
                .disableFog(options.disableFog)
                .disableCull(options.disableCull)
                .disableVanillaFrustum(options.disableVanillaFrustum)
                .build())
            .input(
                Input.builder()
                    .consumeHotbarScroll(options.consumeHotbarScroll)
                    .blockDebugReloads(options.blockDebugReloads)
                    .movement(options.movement)
                    .freeCursor(options.freeCursor)
                    .build())
            .guiPolicy(guiPolicy)
            .build();
    }

    public static final class Builder {

        private Render render = Render.builder()
            .build();
        private Input input = Input.builder()
            .build();
        private Hud hud = Hud.builder()
            .build();
        private Navigation navigation = Navigation.builder()
            .build();
        private WorldCameras.GuiPolicy guiPolicy = WorldCameras.GuiPolicy.SUSPEND;
        private HeightSlice slice = HeightSlice.disabled();

        public Builder render(Render value) {
            render = Objects.requireNonNull(value, "render");
            return this;
        }

        public Builder input(Input value) {
            input = Objects.requireNonNull(value, "input");
            return this;
        }

        public Builder hud(Hud value) {
            hud = Objects.requireNonNull(value, "hud");
            return this;
        }

        public Builder navigation(Navigation value) {
            navigation = Objects.requireNonNull(value, "navigation");
            return this;
        }

        public Builder guiPolicy(WorldCameras.GuiPolicy value) {
            guiPolicy = Objects.requireNonNull(value, "guiPolicy");
            return this;
        }

        public Builder slice(HeightSlice value) {
            slice = Objects.requireNonNull(value, "slice");
            return this;
        }

        public CameraSettings build() {
            return new CameraSettings(this);
        }
    }

    public static final class Render {

        private final boolean hideHand, disableFog, disableCull, disableVanillaFrustum;

        private Render(Builder builder) {
            hideHand = builder.hideHand;
            disableFog = builder.disableFog;
            disableCull = builder.disableCull;
            disableVanillaFrustum = builder.disableVanillaFrustum;
        }

        public static Builder builder() {
            return new Builder();
        }

        public boolean hideHand() {
            return hideHand;
        }

        public boolean disableFog() {
            return disableFog;
        }

        public boolean disableCull() {
            return disableCull;
        }

        public boolean disableVanillaFrustum() {
            return disableVanillaFrustum;
        }

        public static final class Builder {

            private boolean hideHand, disableFog, disableCull, disableVanillaFrustum;

            public Builder hideHand(boolean value) {
                hideHand = value;
                return this;
            }

            public Builder disableFog(boolean value) {
                disableFog = value;
                return this;
            }

            public Builder disableCull(boolean value) {
                disableCull = value;
                return this;
            }

            public Builder disableVanillaFrustum(boolean value) {
                disableVanillaFrustum = value;
                return this;
            }

            public Render build() {
                return new Render(this);
            }
        }
    }

    public static final class Input {

        private final boolean consumeHotbarScroll, blockDebugReloads, freeCursor;
        private final WorldCameras.MovementPolicy movement;

        private Input(Builder builder) {
            consumeHotbarScroll = builder.consumeHotbarScroll;
            blockDebugReloads = builder.blockDebugReloads;
            freeCursor = builder.freeCursor;
            movement = builder.movement;
        }

        public static Builder builder() {
            return new Builder();
        }

        public boolean consumeHotbarScroll() {
            return consumeHotbarScroll;
        }

        public boolean blockDebugReloads() {
            return blockDebugReloads;
        }

        public boolean freeCursor() {
            return freeCursor;
        }

        public WorldCameras.MovementPolicy movement() {
            return movement;
        }

        public static final class Builder {

            private boolean consumeHotbarScroll, blockDebugReloads, freeCursor;
            private WorldCameras.MovementPolicy movement = WorldCameras.MovementPolicy.NONE;

            public Builder consumeHotbarScroll(boolean value) {
                consumeHotbarScroll = value;
                return this;
            }

            public Builder blockDebugReloads(boolean value) {
                blockDebugReloads = value;
                return this;
            }

            public Builder freeCursor(boolean value) {
                freeCursor = value;
                return this;
            }

            public Builder movement(WorldCameras.MovementPolicy value) {
                movement = Objects.requireNonNull(value, "movement");
                return this;
            }

            public Input build() {
                return new Input(this);
            }
        }
    }

    public static final class Hud {

        public enum Crosshair {
            KEEP,
            HIDDEN
        }

        private final Crosshair crosshair;
        private final boolean panelVisible;
        private final int panelX, panelY, panelWidth, backgroundArgb, accentArgb, textArgb;

        private Hud(Builder builder) {
            crosshair = builder.crosshair;
            panelVisible = builder.panelVisible;
            panelX = builder.panelX;
            panelY = builder.panelY;
            panelWidth = builder.panelWidth;
            backgroundArgb = builder.backgroundArgb;
            accentArgb = builder.accentArgb;
            textArgb = builder.textArgb;
        }

        public static Builder builder() {
            return new Builder();
        }

        public Crosshair crosshair() {
            return crosshair;
        }

        public boolean panelVisible() {
            return panelVisible;
        }

        /** Preferred GUI-pixel position; the nonmodal panel constrains its actual position to the viewport. */
        public int panelX() {
            return panelX;
        }

        public int panelY() {
            return panelY;
        }

        public int panelWidth() {
            return panelWidth;
        }

        public int backgroundArgb() {
            return backgroundArgb;
        }

        public int accentArgb() {
            return accentArgb;
        }

        public int textArgb() {
            return textArgb;
        }

        public static final class Builder {

            private Crosshair crosshair = Crosshair.KEEP;
            private boolean panelVisible;
            private int panelX = 12, panelY = 12, panelWidth = 192;
            private int backgroundArgb = 0xE81B2029, accentArgb = 0xFF65C6C2, textArgb = 0xFFF1F4F8;

            public Builder crosshair(Crosshair value) {
                crosshair = Objects.requireNonNull(value, "crosshair");
                return this;
            }

            public Builder panelVisible(boolean value) {
                panelVisible = value;
                return this;
            }

            public Builder panelPosition(int x, int y) {
                panelX = x;
                panelY = y;
                return this;
            }

            public Builder panelWidth(int value) {
                if (value <= 0) throw new IllegalArgumentException("panelWidth must be positive");
                panelWidth = value;
                return this;
            }

            public Builder backgroundArgb(int value) {
                backgroundArgb = value;
                return this;
            }

            public Builder accentArgb(int value) {
                accentArgb = value;
                return this;
            }

            public Builder textArgb(int value) {
                textArgb = value;
                return this;
            }

            public Hud build() {
                return new Hud(this);
            }
        }
    }

    public static final class Navigation {

        private final boolean pan, yaw, pitch, zoom, reset;
        private final double minPitch, maxPitch, minScale, maxScale, basePixelsPerWorldUnit;
        private final int sliceMinimumY, sliceMaximumY;

        private Navigation(Builder builder) {
            pan = builder.pan;
            yaw = builder.yaw;
            pitch = builder.pitch;
            zoom = builder.zoom;
            reset = builder.reset;
            minPitch = builder.minPitch;
            maxPitch = builder.maxPitch;
            minScale = builder.minScale;
            maxScale = builder.maxScale;
            basePixelsPerWorldUnit = builder.basePixelsPerWorldUnit;
            sliceMinimumY = builder.sliceMinimumY;
            sliceMaximumY = builder.sliceMaximumY;
        }

        public static Builder builder() {
            return new Builder();
        }

        public boolean pan() {
            return pan;
        }

        public boolean yaw() {
            return yaw;
        }

        public boolean pitch() {
            return pitch;
        }

        public boolean zoom() {
            return zoom;
        }

        public boolean reset() {
            return reset;
        }

        public double minPitch() {
            return minPitch;
        }

        public double maxPitch() {
            return maxPitch;
        }

        /** Scale limits are pixels per world unit, matching the current projection's GUI pixel density. */
        public double minScale() {
            return minScale;
        }

        public double maxScale() {
            return maxScale;
        }

        public double basePixelsPerWorldUnit() {
            return basePixelsPerWorldUnit;
        }

        public int sliceMinimumY() {
            return sliceMinimumY;
        }

        public int sliceMaximumY() {
            return sliceMaximumY;
        }

        public static final class Builder {

            private boolean pan, yaw, pitch, zoom, reset;
            private double minPitch = -89, maxPitch = 89, minScale = 2, maxScale = 128, basePixelsPerWorldUnit = 16;
            private int sliceMinimumY = 0, sliceMaximumY = 256;

            public Builder pan(boolean value) {
                pan = value;
                return this;
            }

            public Builder yaw(boolean value) {
                yaw = value;
                return this;
            }

            public Builder pitch(boolean value) {
                pitch = value;
                return this;
            }

            public Builder zoom(boolean value) {
                zoom = value;
                return this;
            }

            public Builder reset(boolean value) {
                reset = value;
                return this;
            }

            public Builder pitchRange(double minimum, double maximum) {
                finite(minimum, "minPitch");
                finite(maximum, "maxPitch");
                if (minimum >= maximum) throw new IllegalArgumentException("minPitch must be below maxPitch");
                minPitch = minimum;
                maxPitch = maximum;
                return this;
            }

            public Builder scaleRange(double minimum, double maximum) {
                positive(minimum, "minScale");
                positive(maximum, "maxScale");
                if (minimum >= maximum) throw new IllegalArgumentException("minScale must be below maxScale");
                minScale = minimum;
                maxScale = maximum;
                return this;
            }

            public Builder basePixelsPerWorldUnit(double value) {
                positive(value, "basePixelsPerWorldUnit");
                basePixelsPerWorldUnit = value;
                return this;
            }

            public Builder sliceRange(int minInclusive, int maxExclusive) {
                if (minInclusive >= maxExclusive) throw new IllegalArgumentException("Empty slice control range");
                sliceMinimumY = minInclusive;
                sliceMaximumY = maxExclusive;
                return this;
            }

            public Navigation build() {
                return new Navigation(this);
            }
        }
    }

    private static void finite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }

    private static void positive(double value, String name) {
        finite(value, name);
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
    }
}
