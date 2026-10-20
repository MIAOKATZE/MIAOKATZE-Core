package com.miaokatze.miaokatzecore.client.camera;

import net.minecraft.client.Minecraft;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.miaokatze.miaokatzecore.client.camera.hud.CameraHud;

/** Raw mouse ownership precedes Forge's event bus, so a captured sequence cannot reach business handlers. */
final class CameraInputState {

    private final CameraPointerState pointer = new CameraPointerState();
    private boolean navigating;
    private int lastX, lastY;
    private boolean cursorSaved, cursorWasGrabbed;

    void maintain(Minecraft mc, CameraSession session, CameraFrame frame) {
        if (pointer.button() >= 0 && (!session.interactive(mc) || frame == null
            || !session.matches(frame, mc)
            || !pointer.matches(frame.ownerEpoch(), mc.theWorld))) cancel();
        if (session.interactive(mc) && session.settings()
            .input()
            .freeCursor()) {
            if (!cursorSaved) {
                cursorWasGrabbed = Mouse.isGrabbed();
                cursorSaved = true;
            }
            if (Mouse.isGrabbed()) Mouse.setGrabbed(false);
        } else restoreCursor(mc);
    }

    boolean event(Minecraft mc, CameraSession session, CameraFrame frame, CameraSettings settings) {
        int eventButton = Mouse.getEventButton();
        boolean pressed = Mouse.getEventButtonState();
        int eventX = Mouse.getEventX(), eventY = Mouse.getEventY(), wheel = Mouse.getEventDWheel();
        maintain(mc, session, frame);
        if (pointer.consumeSuppressed(eventButton, pressed)) return true;
        if (!session.interactive(mc) || frame == null || settings == null || !session.matches(frame, mc)) return false;
        CameraControls controls = session.controls();
        if (pointer.button() >= 0) {
            int capturedButton = pointer.button();
            if (eventButton >= 0 && eventButton != capturedButton && pressed) pointer.suppress(eventButton);
            if (navigating) {
                if (eventButton < 0) {
                    double dx = -2.0 * (eventX - lastX)
                        * frame.projection()
                            .halfWidth()
                        / frame.displayWidth();
                    double dy = -2.0 * (eventY - lastY)
                        * frame.projection()
                            .halfHeight()
                        / frame.displayHeight();
                    controls.panWorld(
                        dx * frame.basis()
                            .rightX() + dy
                                * frame.basis()
                                    .upX(),
                        dx * frame.basis()
                            .rightY() + dy
                                * frame.basis()
                                    .upY(),
                        dx * frame.basis()
                            .rightZ() + dy
                                * frame.basis()
                                    .upZ());
                }
            } else CameraHud.pointer(frame, settings, controls, eventX, eventY, eventButton, pressed, wheel);
            lastX = eventX;
            lastY = eventY;
            if (eventButton == capturedButton && !pressed) {
                pointer.releaseSuppressed(capturedButton);
                finish();
            }
            return true;
        }
        boolean hit = CameraHud.pointer(frame, settings, controls, eventX, eventY, eventButton, pressed, wheel);
        if (hit) {
            if (eventButton >= 0 && pressed) capture(session, frame, mc, eventButton, false, eventX, eventY);
            return true;
        }
        if (eventButton == 2 && pressed
            && settings.navigation()
                .pan()
            && controls.supports(CameraControl.PAN)) {
            capture(session, frame, mc, eventButton, true, eventX, eventY);
            return true;
        }
        if (wheel != 0 && settings.navigation()
            .zoom()
            && controls.supports(CameraControl.ZOOM)
            && (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))) {
            double current = frame.scaledWidth() / (2 * frame.projection()
                .halfWidth());
            double target = Math.max(
                settings.navigation()
                    .minScale(),
                Math.min(
                    settings.navigation()
                        .maxScale(),
                    current * Math.pow(1.1, wheel / 120.0)));
            controls.zoom(target / current);
            return true;
        }
        return false;
    }

    private void capture(CameraSession session, CameraFrame frame, Minecraft mc, int pressedButton, boolean pan, int x,
        int y) {
        if (!session.interactive(mc)) {
            pointer.suppress(pressedButton);
            return;
        }
        if (!pointer.capture(pressedButton, frame.ownerEpoch(), session.owner().epoch, mc.theWorld)) return;
        navigating = pan;
        lastX = x;
        lastY = y;
    }

    boolean captured() {
        return pointer.captured();
    }

    void cancel() {
        pointer.cancel();
        finish();
        CameraHud.cancel();
    }

    private void finish() {
        pointer.finish();
        navigating = false;
    }

    void endTick() {
        // A window-external release may have no LWJGL event. Never reconcile before queued old releases are consumed.
        if (pointer.button() >= 0 && !Mouse.isButtonDown(pointer.button())) cancel();
        pointer.reconcile(Mouse::isButtonDown);
    }

    void restoreCursor(Minecraft mc) {
        if (!cursorSaved) return;
        cursorSaved = false;
        if (cursorWasGrabbed && mc.currentScreen == null && mc.inGameHasFocus && mc.theWorld != null) {
            mc.mouseHelper.grabMouseCursor();
        }
    }
}
