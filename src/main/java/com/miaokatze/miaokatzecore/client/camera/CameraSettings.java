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

        private final boolean consumeHotbarScroll, blockDebugReloads, freeCursor, exclusiveKeyboard;
        private final WorldCameras.MovementPolicy movement;

        private Input(Builder builder) {
            consumeHotbarScroll = builder.consumeHotbarScroll;
            blockDebugReloads = builder.blockDebugReloads;
            freeCursor = builder.freeCursor;
            exclusiveKeyboard = builder.exclusiveKeyboard;
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

        /** Filter the raw keyboard queue through Source.allowKey before vanilla and FML see an event. */
        public boolean exclusiveKeyboard() {
            return exclusiveKeyboard;
        }

        public WorldCameras.MovementPolicy movement() {
            return movement;
        }

        public static final class Builder {

            private boolean consumeHotbarScroll, blockDebugReloads, freeCursor, exclusiveKeyboard;
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

            public Builder exclusiveKeyboard(boolean value) {
                exclusiveKeyboard = value;
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
        private final boolean panelVisible, sliceBarVisible, sliceDirectionVisible, sliceLabelVisible;
        private final int panelX, panelY, panelWidth, backgroundArgb, accentArgb, textArgb;

        private Hud(Builder builder) {
            crosshair = builder.crosshair;
            panelVisible = builder.panelVisible;
            sliceBarVisible = builder.sliceBarVisible;
            sliceDirectionVisible = builder.sliceDirectionVisible;
            sliceLabelVisible = builder.sliceLabelVisible;
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

        /** Compact vertical slice control; only visible while the requested slice is enabled. */
        public boolean sliceBarVisible() {
            return sliceBarVisible;
        }

        public boolean sliceDirectionVisible() {
            return sliceDirectionVisible;
        }

        public boolean sliceLabelVisible() {
            return sliceLabelVisible;
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
            private boolean sliceBarVisible, sliceDirectionVisible = true, sliceLabelVisible = true;
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

            public Builder sliceBarVisible(boolean value) {
                sliceBarVisible = value;
                return this;
            }

            public Builder sliceDirectionVisible(boolean value) {
                sliceDirectionVisible = value;
                return this;
            }

            public Builder sliceLabelVisible(boolean value) {
                sliceLabelVisible = value;
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

        public enum WheelZoom {
            CTRL,
            DIRECT,
            DIRECT_EXCEPT_ALT
        }

        public enum WheelDeltaUnit {
            LEGACY_120,
            NOTCH,
            AUTO
        }

        private final boolean pan, yaw, pitch, zoom, reset;
        private final int panButton, rotateButton, rotateKey;
        private final double rotationDegreesPerPixel;
        private final WheelZoom wheelZoom;
        private final WheelDeltaUnit wheelDeltaUnit;
        private final double wheelSensitivity, wheelZoomLimit;
        private final boolean panExcludesControl, rotateExcludesControl;
        private final double minPitch, maxPitch, minScale, maxScale, basePixelsPerWorldUnit;
        private final int sliceMinimumY, sliceMaximumY;

        private Navigation(Builder builder) {
            pan = builder.pan;
            yaw = builder.yaw;
            pitch = builder.pitch;
            zoom = builder.zoom;
            reset = builder.reset;
            panButton = builder.panButton;
            rotateButton = builder.rotateButton;
            rotateKey = builder.rotateKey;
            rotationDegreesPerPixel = builder.rotationDegreesPerPixel;
            wheelZoom = builder.wheelZoom;
            wheelDeltaUnit = builder.wheelDeltaUnit;
            wheelSensitivity = builder.wheelSensitivity;
            wheelZoomLimit = builder.wheelZoomLimit;
            panExcludesControl = builder.panExcludesControl;
            rotateExcludesControl = builder.rotateExcludesControl;
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

        public int panButton() {
            return panButton;
        }

        public int rotateButton() {
            return rotateButton;
        }

        /** LWJGL key code, or zero for no required modifier. */
        public int rotateKey() {
            return rotateKey;
        }

        /** Sensitivity is measured in scaled GUI pixels, independent of the display scale. */
        public double rotationDegreesPerPixel() {
            return rotationDegreesPerPixel;
        }

        public WheelZoom wheelZoom() {
            return wheelZoom;
        }

        public WheelDeltaUnit wheelDeltaUnit() {
            return wheelDeltaUnit;
        }

        public double wheelSensitivity() {
            return wheelSensitivity;
        }

        public double wheelZoomLimit() {
            return wheelZoomLimit;
        }

        public boolean panExcludesControl() {
            return panExcludesControl;
        }

        public boolean rotateExcludesControl() {
            return rotateExcludesControl;
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
            private int panButton = 2, rotateButton = -1, rotateKey;
            private double rotationDegreesPerPixel = .35;
            private WheelZoom wheelZoom = WheelZoom.CTRL;
            private WheelDeltaUnit wheelDeltaUnit = WheelDeltaUnit.LEGACY_120;
            private double wheelSensitivity = 1, wheelZoomLimit = Double.POSITIVE_INFINITY;
            private boolean panExcludesControl, rotateExcludesControl;
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

            public Builder panButton(int button) {
                if (button < -1 || button >= 16) throw new IllegalArgumentException("Invalid pan button");
                panButton = button;
                return this;
            }

            /** Use -1 to disable rotation dragging; requiredKey=0 means no modifier. */
            public Builder rotateDrag(int button, int requiredKey) {
                if (button < -1 || button >= 16 || requiredKey < 0 || requiredKey >= 256)
                    throw new IllegalArgumentException("Invalid rotation binding");
                rotateButton = button;
                rotateKey = requiredKey;
                return this;
            }

            public Builder rotationDegreesPerPixel(double value) {
                positive(value, "rotationDegreesPerPixel");
                rotationDegreesPerPixel = value;
                return this;
            }

            public Builder wheelZoom(WheelZoom value) {
                wheelZoom = Objects.requireNonNull(value, "wheelZoom");
                return this;
            }

            public Builder wheelDeltaUnit(WheelDeltaUnit value) {
                wheelDeltaUnit = Objects.requireNonNull(value, "wheelDeltaUnit");
                return this;
            }

            public Builder wheelSensitivity(double value) {
                positive(value, "wheelSensitivity");
                wheelSensitivity = value;
                return this;
            }

            /** Maximum multiplicative change per raw wheel event; legacy defaults to no additional limit. */
            public Builder wheelZoomLimit(double value) {
                positive(value, "wheelZoomLimit");
                if (value <= 1) throw new IllegalArgumentException("wheelZoomLimit must exceed one");
                wheelZoomLimit = value;
                return this;
            }

            public Builder panExcludesControl(boolean value) {
                panExcludesControl = value;
                return this;
            }

            public Builder rotateExcludesControl(boolean value) {
                rotateExcludesControl = value;
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
