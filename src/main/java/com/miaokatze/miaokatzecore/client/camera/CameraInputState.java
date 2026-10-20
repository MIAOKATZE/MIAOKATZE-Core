package com.miaokatze.miaokatzecore.client.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.miaokatze.miaokatzecore.client.camera.hud.CameraHud;

/** Raw mouse ownership precedes Forge's event bus, so a captured sequence cannot reach business handlers. */
final class CameraInputState {

    private static final boolean UNIT_WHEEL = CameraInputState.class.getClassLoader()
        .getResource("org/lwjglx/input/Mouse.class") != null;

    private final CameraPointerState pointer = new CameraPointerState();
    private final CameraDragGesture rotation = new CameraDragGesture();
    private int navigating;
    private long exclusiveEpoch = Long.MIN_VALUE;
    private int lastX, lastY;
    private boolean cursorSaved, cursorWasGrabbed;

    void maintain(Minecraft mc, CameraSession session, CameraFrame frame) {
        if (session.interactive(mc) && session.settings()
            .input()
            .exclusiveKeyboard()) {
            long epoch = session.owner().epoch;
            if (exclusiveEpoch != epoch) {
                // Presses predating ownership must not remain held after their releases are filtered.
                KeyBinding.unPressAllKeys();
                exclusiveEpoch = epoch;
            }
        } else exclusiveEpoch = Long.MIN_VALUE;
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
            if (navigating != 0) {
                if (eventButton < 0) {
                    if (navigating == 2) {
                        if (rotation.move(eventX, eventY))
                            rotate(frame, settings.navigation(), controls, rotation.deltaX(), rotation.deltaY());
                    } else {
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
                }
            } else CameraHud.pointer(frame, settings, controls, eventX, eventY, eventButton, pressed, wheel);
            lastX = eventX;
            lastY = eventY;
            if (eventButton == capturedButton && !pressed) {
                // A release can carry the last movement even when no standalone motion event was queued.
                if (navigating == 2 && rotation.move(eventX, eventY)
                    && (rotation.deltaX() != 0 || rotation.deltaY() != 0))
                    rotate(frame, settings.navigation(), controls, rotation.deltaX(), rotation.deltaY());
                boolean click = navigating == 2 && rotation.click();
                WorldCameras.Entry clickOwner = session.owner();
                pointer.releaseSuppressed(capturedButton);
                finish();
                if (click && clickOwner.active() && session.interactive(mc))
                    clickOwner.source.rotationClick(capturedButton, eventX, eventY);
            }
            return true;
        }
        boolean hit = CameraHud.pointer(frame, settings, controls, eventX, eventY, eventButton, pressed, wheel);
        if (hit) {
            if (eventButton >= 0 && pressed) capture(session, frame, mc, eventButton, 0, eventX, eventY);
            return true;
        }
        CameraSettings.Navigation navigation = settings.navigation();
        if (pressed && eventButton >= 0
            && eventButton == navigation.rotateButton()
            && (!navigation.rotateExcludesControl() || !controlDown())
            && (navigation.rotateKey() == 0 || Keyboard.isKeyDown(navigation.rotateKey()))
            && (navigation.yaw() && controls.supports(CameraControl.YAW)
                || navigation.pitch() && controls.supports(CameraControl.PITCH))) {
            capture(session, frame, mc, eventButton, 2, eventX, eventY);
            return true;
        }
        if (eventButton >= 0 && eventButton == navigation.panButton()
            && pressed
            && (!navigation.panExcludesControl() || !controlDown())
            && settings.navigation()
                .pan()
            && controls.supports(CameraControl.PAN)) {
            capture(session, frame, mc, eventButton, 1, eventX, eventY);
            return true;
        }
        if (wheel != 0 && settings.navigation()
            .zoom() && controls.supports(CameraControl.ZOOM) && wheelZoom(navigation.wheelZoom())) {
            double current = frame.scaledWidth() / (2 * frame.projection()
                .halfWidth());
            double target = Math.max(
                settings.navigation()
                    .minScale(),
                Math.min(
                    settings.navigation()
                        .maxScale(),
                    current * CameraWheel.factor(wheel, navigation, UNIT_WHEEL)));
            controls.zoom(target / current);
            return true;
        }
        return false;
    }

    private static boolean wheelZoom(CameraSettings.Navigation.WheelZoom policy) {
        switch (policy) {
            case DIRECT:
                return true;
            case DIRECT_EXCEPT_ALT:
                return !Keyboard.isKeyDown(Keyboard.KEY_LMENU) && !Keyboard.isKeyDown(Keyboard.KEY_RMENU);
            default:
                return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        }
    }

    private static boolean controlDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    }

    private static void rotate(CameraFrame frame, CameraSettings.Navigation navigation, CameraControls controls, int dx,
        int dy) {
        double sensitivity = navigation.rotationDegreesPerPixel() * frame.scaledWidth() / frame.displayWidth();
        double yaw = navigation.yaw() && controls.supports(CameraControl.YAW) ? dx * sensitivity : 0;
        double pitch = 0;
        if (navigation.pitch() && controls.supports(CameraControl.PITCH)) {
            double current = frame.pose()
                .pitchDegrees();
            pitch = Math.max(navigation.minPitch(), Math.min(navigation.maxPitch(), current - dy * sensitivity))
                - current;
        }
        controls.rotate(yaw, pitch);
    }

    private void capture(CameraSession session, CameraFrame frame, Minecraft mc, int pressedButton, int mode, int x,
        int y) {
        if (!session.interactive(mc)) {
            pointer.suppress(pressedButton);
            return;
        }
        if (!pointer.capture(pressedButton, frame.ownerEpoch(), session.owner().epoch, mc.theWorld)) return;
        navigating = mode;
        if (mode == 2) rotation.begin(
            x,
            y,
            session.settings()
                .navigation()
                .rotationClickThreshold() * frame.displayWidth()
                / frame.scaledWidth());
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
        navigating = 0;
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
