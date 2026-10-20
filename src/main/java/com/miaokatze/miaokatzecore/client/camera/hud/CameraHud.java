package com.miaokatze.miaokatzecore.client.camera.hud;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

import com.miaokatze.miaokatzecore.api.camera.HeightSlice;
import com.miaokatze.miaokatzecore.client.camera.CameraControl;
import com.miaokatze.miaokatzecore.client.camera.CameraControls;
import com.miaokatze.miaokatzecore.client.camera.CameraFrame;
import com.miaokatze.miaokatzecore.client.camera.CameraSettings;
import com.miaokatze.miaokatzecore.client.camera.SliceState;
import com.miaokatze.miaokatzecore.client.camera.hud.CameraHudLayout.Control;
import com.miaokatze.miaokatzecore.client.render.GlScope;

/**
 * A non-modal, client-thread camera toolbar. It never opens a GuiScreen, owns a lease, moves a player,
 * sends a packet or registers a Forge mouse listener. The input router owns the complete press/release
 * transaction and must call {@link #cancel()} when its lease, world, focus or GUI context changes.
 */
public final class CameraHud {

    private static final double DEFAULT_FEATHER = .25;
    private static final double SLIDER_MAX_FEATHER = 2;
    private static Control dragging = Control.NONE;
    private static int activeButton = -1;
    private static String ownerId;
    private static long ownerEpoch = Long.MIN_VALUE;
    private static boolean collapsed, positioned, sliceInitialized, hideAbove = true;
    private static double panelX, panelY, grabX, grabY, feather = DEFAULT_FEATHER;
    private static int configuredX, configuredY, selectedY;
    private static CameraFrame pendingFrame;
    private static HeightSlice pendingSlice;

    private CameraHud() {}

    /** Only the current raw event coordinates are accepted; these are not Mouse.getX()/getY() polls. */
    public static boolean pointer(CameraFrame frame, CameraSettings settings, CameraControls controls, int displayX,
        int displayY, int button, boolean pressed, int wheel) {
        if (!available(frame, settings, controls)) {
            dragging = Control.NONE;
            activeButton = -1;
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        CameraHudLayout layout = layout(settings.hud(), resolution.getScaledWidth(), resolution.getScaledHeight());
        double x = CameraPointerCapture.guiX(displayX, mc.displayWidth, resolution.getScaledWidth());
        double y = CameraPointerCapture.guiY(displayY, mc.displayHeight, resolution.getScaledHeight());
        HeightSlice slice = synchronize(frame, settings.navigation());
        if (dragging != Control.NONE) {
            if (button == activeButton && !pressed) {
                updateDrag(layout, x, y, frame, settings, controls, slice);
                dragging = Control.NONE;
                activeButton = -1;
                return true;
            }
            if (button == -1) {
                updateDrag(layout, x, y, frame, settings, controls, slice);
                return true;
            }
            return true;
        }
        Control hit = layout.hit(x, y);
        if (hit == Control.NONE) return false;
        // Wheel over the panel must not reach hotbar/business camera listeners.
        if (wheel != 0) {
            if (hit == Control.HEIGHT && slice.enabled() && enabled(hit, settings, controls, frame)) {
                selectedY = clampLayer((long) selectedY + (wheel > 0 ? 1 : -1), settings.navigation());
                request(frame, controls, currentSlice());
            }
            return true;
        }
        if (button < 0 || !pressed) return false;
        // The panel background and disabled controls also consume their matching release.
        activeButton = button;
        dragging = Control.PANEL;
        if (button != 0) return true;
        if (hit == Control.HEADER) {
            dragging = hit;
            grabX = x - layout.x;
            grabY = y - layout.y;
        } else if (hit == Control.COLLAPSE) {
            collapsed = !collapsed;
        } else if (enabled(hit, settings, controls, frame)) {
            switch (hit) {
                case YAW_MINUS:
                    controls.rotate(-15, 0);
                    break;
                case YAW_PLUS:
                    controls.rotate(15, 0);
                    break;
                case PITCH_MINUS:
                case PITCH_PLUS:
                    double desired = frame.pose()
                        .pitchDegrees() + (hit == Control.PITCH_PLUS ? 5 : -5);
                    double pitch = CameraHudLayout.clamp(
                        desired,
                        settings.navigation()
                            .minPitch(),
                        settings.navigation()
                            .maxPitch());
                    controls.rotate(
                        0,
                        pitch - frame.pose()
                            .pitchDegrees());
                    break;
                case ZOOM_MINUS:
                    zoom(frame, settings.navigation(), controls, 1 / 1.1);
                    break;
                case ZOOM_PLUS:
                    zoom(frame, settings.navigation(), controls, 1.1);
                    break;
                case RESET:
                    controls.resetView();
                    break;
                case SLICE_TOGGLE:
                    request(frame, controls, slice.enabled() ? HeightSlice.disabled() : currentSlice());
                    break;
                case SLICE_DIRECTION:
                    hideAbove = !hideAbove;
                    if (slice.enabled()) request(frame, controls, currentSlice());
                    break;
                case HEIGHT:
                case FEATHER:
                    if (slice.enabled()) {
                        dragging = hit;
                        updateDrag(layout, x, y, frame, settings, controls, slice);
                    }
                    break;
                default:
                    break;
            }
        }
        return true;
    }

    /** Cancels UI-local drag state; the router retains its stale-release quarantine. */
    public static void cancel() {
        dragging = Control.NONE;
        activeButton = -1;
        ownerId = null;
        ownerEpoch = Long.MIN_VALUE;
        pendingFrame = null;
        pendingSlice = null;
        sliceInitialized = false;
        feather = DEFAULT_FEATHER;
        hideAbove = true;
    }

    /** Draw from RenderGameOverlayEvent.Post(ALL); crosshair cancellation stays in the client hook. */
    public static void render(CameraFrame frame, CameraSettings settings, CameraControls controls) {
        if (!available(frame, settings, controls)) return;
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        CameraHudLayout layout = layout(settings.hud(), resolution.getScaledWidth(), resolution.getScaledHeight());
        HeightSlice slice = synchronize(frame, settings.navigation());
        CameraSettings.Hud policy = settings.hud();
        boolean shaderPrograms = GLContext.getCapabilities().OpenGL20;
        int previousProgram = shaderPrograms ? GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) : 0;
        try (GlScope ignored = new GlScope()) {
            if (shaderPrograms) GL20.glUseProgram(0);
            clip(layout, mc.displayWidth, mc.displayHeight, resolution.getScaledWidth(), resolution.getScaledHeight());
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glTranslated(layout.x, layout.y, 0);
            GL11.glScaled(layout.scale, layout.scale, 1);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            rect(0, 0, 192, collapsed ? 18 : 166, policy.backgroundArgb());
            rect(0, 0, 192, 18, shade(policy.accentArgb(), .45));
            double zoom = frame.scaledWidth() / (2 * frame.projection()
                .halfWidth())
                / settings.navigation()
                    .basePixelsPerWorldUnit();
            text(
                mc.fontRenderer,
                String.format(
                    Locale.ROOT,
                    "相机 %.0f°/%.0f° %.1fx",
                    frame.pose()
                        .yawDegrees(),
                    frame.pose()
                        .pitchDegrees(),
                    zoom),
                8,
                5,
                policy.textArgb());
            text(mc.fontRenderer, collapsed ? "+" : "-", 178, 5, policy.textArgb());
            if (collapsed) return;
            button(mc.fontRenderer, 8, 23, 56, "左转", Control.YAW_MINUS, settings, controls, frame);
            button(mc.fontRenderer, 68, 23, 56, "右转", Control.YAW_PLUS, settings, controls, frame);
            button(mc.fontRenderer, 128, 23, 56, "重置", Control.RESET, settings, controls, frame);
            button(mc.fontRenderer, 8, 43, 86, "俯角 -", Control.PITCH_MINUS, settings, controls, frame);
            button(mc.fontRenderer, 98, 43, 86, "俯角 +", Control.PITCH_PLUS, settings, controls, frame);
            button(mc.fontRenderer, 8, 63, 86, "缩小", Control.ZOOM_MINUS, settings, controls, frame);
            button(mc.fontRenderer, 98, 63, 86, "放大", Control.ZOOM_PLUS, settings, controls, frame);
            button(
                mc.fontRenderer,
                8,
                84,
                70,
                slice.enabled() ? "X光：开" : "X光：关",
                Control.SLICE_TOGGLE,
                settings,
                controls,
                frame);
            button(
                mc.fontRenderer,
                82,
                84,
                102,
                hideAbove ? "隐藏上方" : "隐藏下方",
                Control.SLICE_DIRECTION,
                settings,
                controls,
                frame);
            boolean canSlice = controls.supports(CameraControl.SLICE) && frame.sliceSupported();
            String layer = slice.enabled() && slice.hasLower() && slice.hasUpper()
                ? "保留Y" + slice.minimumY() + ".." + ((long) slice.maximumY() - 1)
                : "隐藏Y" + (hideAbove ? ">" : "<") + selectedY;
            text(mc.fontRenderer, layer, 10, 104, canSlice ? policy.textArgb() : disabledColor(policy.textArgb()));
            if (!frame.sliceSupported()) {
                rightText(mc.fontRenderer, "剖面不可用", 182, 104, disabledColor(policy.textArgb()));
            } else if (frame.sliceState() == SliceState.PREVIEW || frame.sliceState() == SliceState.APPLYING
                || frame.sliceState() == SliceState.RESTORING) {
                    rightText(mc.fontRenderer, "剖面更新中", 182, 104, policy.textArgb());
                }
            double span = (double) settings.navigation()
                .sliceMaximumY() - 1
                - settings.navigation()
                    .sliceMinimumY();
            slider(
                116,
                span <= 0 ? 0
                    : (selectedY - settings.navigation()
                        .sliceMinimumY()) / span,
                policy.accentArgb(),
                canSlice && slice.enabled());
            text(mc.fontRenderer, String.format(Locale.ROOT, "微渐变 %.2f格", feather), 10, 135, policy.textArgb());
            slider(147, feather / SLIDER_MAX_FEATHER, policy.accentArgb(), canSlice && slice.enabled());
        } finally {
            if (shaderPrograms) GL20.glUseProgram(previousProgram);
        }
    }

    private static boolean available(CameraFrame frame, CameraSettings settings, CameraControls controls) {
        if (frame == null || settings == null
            || controls == null
            || !settings.hud()
                .panelVisible())
            return false;
        if (frame.ownerEpoch() != ownerEpoch || !frame.ownerId()
            .equals(ownerId)) {
            cancel();
            ownerEpoch = frame.ownerEpoch();
            ownerId = frame.ownerId();
        }
        return true;
    }

    private static CameraHudLayout layout(CameraSettings.Hud policy, int width, int height) {
        if (!positioned || configuredX != policy.panelX() || configuredY != policy.panelY()) {
            panelX = policy.panelX();
            panelY = policy.panelY();
            configuredX = policy.panelX();
            configuredY = policy.panelY();
            positioned = true;
        }
        CameraHudLayout layout = new CameraHudLayout(width, height, panelX, panelY, policy.panelWidth(), collapsed);
        panelX = layout.x;
        panelY = layout.y;
        return layout;
    }

    private static HeightSlice synchronize(CameraFrame frame, CameraSettings.Navigation navigation) {
        HeightSlice source = frame.requestedSlice();
        HeightSlice slice = pendingSlice != null && frame == pendingFrame ? pendingSlice : source;
        if (slice != pendingSlice) {
            pendingSlice = null;
            pendingFrame = null;
        }
        if (!sliceInitialized) {
            selectedY = clampLayer(
                (long) Math.floor(
                    frame.pose()
                        .anchorY()),
                navigation);
            sliceInitialized = true;
        }
        if (slice.enabled()) {
            hideAbove = slice.hasUpper();
            selectedY = clampLayer(slice.hasUpper() ? (long) slice.maximumY() - 1 : slice.minimumY(), navigation);
            feather = slice.featherWidth();
        }
        return slice;
    }

    private static int clampLayer(long value, CameraSettings.Navigation navigation) {
        return (int) Math.max(navigation.sliceMinimumY(), Math.min((long) navigation.sliceMaximumY() - 1, value));
    }

    private static HeightSlice currentSlice() {
        return hideAbove ? HeightSlice.below(selectedY + 1, feather) : HeightSlice.above(selectedY, feather);
    }

    private static void request(CameraFrame frame, CameraControls controls, HeightSlice slice) {
        pendingFrame = frame;
        pendingSlice = slice;
        controls.requestSlice(slice);
    }

    private static void updateDrag(CameraHudLayout layout, double x, double y, CameraFrame frame,
        CameraSettings settings, CameraControls controls, HeightSlice slice) {
        if (dragging == Control.HEADER) {
            panelX = x - grabX;
            panelY = y - grabY;
        } else if (slice.enabled() && frame.sliceSupported() && controls.supports(CameraControl.SLICE)) {
            if (dragging == Control.HEIGHT) {
                long min = settings.navigation()
                    .sliceMinimumY(),
                    max = (long) settings.navigation()
                        .sliceMaximumY() - 1;
                selectedY = clampLayer(Math.round(min + (max - min) * layout.sliderFraction(x)), settings.navigation());
                request(frame, controls, currentSlice());
            } else if (dragging == Control.FEATHER) {
                feather = Math.round(SLIDER_MAX_FEATHER * layout.sliderFraction(x) * 100) / 100.0;
                request(frame, controls, currentSlice());
            }
        }
    }

    private static void zoom(CameraFrame frame, CameraSettings.Navigation navigation, CameraControls controls,
        double factor) {
        double current = frame.scaledWidth() / (2 * frame.projection()
            .halfWidth());
        controls.zoom(CameraHudLayout.clamp(current * factor, navigation.minScale(), navigation.maxScale()) / current);
    }

    private static boolean enabled(Control control, CameraSettings settings, CameraControls controls,
        CameraFrame frame) {
        switch (control) {
            case YAW_MINUS:
            case YAW_PLUS:
                return settings.navigation()
                    .yaw() && controls.supports(CameraControl.YAW);
            case PITCH_MINUS:
            case PITCH_PLUS:
                return settings.navigation()
                    .pitch() && controls.supports(CameraControl.PITCH);
            case ZOOM_MINUS:
            case ZOOM_PLUS:
                return settings.navigation()
                    .zoom() && controls.supports(CameraControl.ZOOM);
            case RESET:
                return settings.navigation()
                    .reset() && controls.supports(CameraControl.RESET);
            case SLICE_TOGGLE:
            case SLICE_DIRECTION:
            case HEIGHT:
            case FEATHER:
                return frame.sliceSupported() && controls.supports(CameraControl.SLICE);
            default:
                return false;
        }
    }

    private static void button(FontRenderer font, int x, int y, int width, String label, Control control,
        CameraSettings settings, CameraControls controls, CameraFrame frame) {
        boolean enabled = enabled(control, settings, controls, frame);
        int accent = settings.hud()
            .accentArgb();
        rect(x, y, width, 16, shade(accent, enabled ? .5 : .18));
        text(
            font,
            label,
            x + (width - font.getStringWidth(label)) / 2,
            y + 4,
            enabled ? settings.hud()
                .textArgb()
                : disabledColor(
                    settings.hud()
                        .textArgb()));
    }

    private static void slider(int y, double fraction, int color, boolean enabled) {
        fraction = CameraHudLayout.clamp(fraction, 0, 1);
        int tint = enabled ? color : disabledColor(color);
        rect(10, y + 5, 172, 4, shade(tint, .3));
        // A narrow alpha gradient also illustrates the slice feather instead of a hard-edged fill.
        gradient(10, y + 5, 172 * fraction, 4, shade(tint, .6), tint);
        rect(10 + 172 * fraction - 2, y + 2, 4, 10, tint);
    }

    private static int disabledColor(int color) {
        return shade(color, .38);
    }

    private static int shade(int color, double factor) {
        return (color & 0xFF000000) | (int) (((color >>> 16) & 255) * factor) << 16
            | (int) (((color >>> 8) & 255) * factor) << 8
            | (int) ((color & 255) * factor);
    }

    private static void text(FontRenderer font, String label, int x, int y, int color) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        font.drawString(label, x, y, color);
    }

    private static void rightText(FontRenderer font, String label, int right, int y, int color) {
        text(font, label, right - font.getStringWidth(label), y, color);
    }

    private static void rect(double x, double y, double width, double height, int color) {
        gradient(x, y, width, height, color, color);
    }

    private static void gradient(double x, double y, double width, double height, int left, int right) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBegin(GL11.GL_QUADS);
        color(left);
        GL11.glVertex2d(x, y);
        GL11.glVertex2d(x, y + height);
        color(right);
        GL11.glVertex2d(x + width, y + height);
        GL11.glVertex2d(x + width, y);
        GL11.glEnd();
    }

    private static void color(int argb) {
        GL11.glColor4f(
            ((argb >>> 16) & 255) / 255f,
            ((argb >>> 8) & 255) / 255f,
            (argb & 255) / 255f,
            ((argb >>> 24) & 255) / 255f);
    }

    private static void clip(CameraHudLayout layout, int displayWidth, int displayHeight, int guiWidth, int guiHeight) {
        int x = (int) Math.floor(layout.x * displayWidth / guiWidth);
        int right = (int) Math.ceil((layout.x + layout.width) * displayWidth / guiWidth);
        int y = (int) Math.floor((guiHeight - layout.y - layout.height) * displayHeight / guiHeight);
        int top = (int) Math.ceil((guiHeight - layout.y) * displayHeight / guiHeight);
        if (GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) {
            java.nio.IntBuffer previous = org.lwjgl.BufferUtils.createIntBuffer(4);
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, previous);
            x = Math.max(x, previous.get(0));
            y = Math.max(y, previous.get(1));
            right = Math.min(right, previous.get(0) + previous.get(2));
            top = Math.min(top, previous.get(1) + previous.get(3));
        }
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x, y, Math.max(0, right - x), Math.max(0, top - y));
    }
}
