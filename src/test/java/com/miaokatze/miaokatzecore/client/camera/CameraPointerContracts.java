package com.miaokatze.miaokatzecore.client.camera;

/** Raw sequence contracts independent of Forge event ordering or native input. */
public final class CameraPointerContracts {

    public static void main(String[] args) {
        rotationGestures();
        visibilityBoundary();
        CameraPointerState pointer = new CameraPointerState();
        Object world = new Object();
        require(pointer.capture(0, 1, 1, world) && pointer.matches(1, world), "capture exact owner/world");
        require(!pointer.matches(2, world) && !pointer.matches(1, new Object()), "preemption and world replacement");
        pointer.suppress(1);
        pointer.finish();
        require(pointer.consumeSuppressed(1, false), "secondary release remains isolated after main release");
        require(!pointer.captured(), "secondary release completes sequence");
        pointer.capture(0, 1, 1, world);
        pointer.cancel();
        require(pointer.captured() && pointer.consumeSuppressed(-1, false), "revoked drag motion isolated");
        require(pointer.consumeSuppressed(0, false) && !pointer.captured(), "queued old release isolated");
        require(!pointer.capture(0, 1, 2, world), "callback preemption cannot capture the new lease");
        require(pointer.button() < 0 && pointer.consumeSuppressed(0, false), "failed capture still isolates release");
        pointer.capture(2, 2, 2, world);
        pointer.cancel();
        pointer.reconcile(button -> true);
        require(pointer.captured(), "still-held external button remains isolated");
        pointer.reconcile(button -> false);
        require(
            !pointer.captured() && !pointer.consumeSuppressed(-1, false),
            "missing OS release recovers at queue tail");
        pointer.capture(0, 3, 3, world);
        pointer.cancel();
        require(pointer.consumeSuppressed(0, false), "release is consumed before tail reconciliation");
        pointer.reconcile(button -> false);
        require(!pointer.captured(), "no stale release debt");
        System.out.println("Camera pointer contracts passed (Minecraft/OpenGL not required).");
    }

    private static void visibilityBoundary() {
        CameraPointerState pointer = new CameraPointerState();
        Object world = new Object();
        // CameraInputState cancels on both F1 edges. An old press may never click the newly exposed surface.
        for (int button : new int[] { 0, 1, 2 }) {
            require(pointer.capture(button, 7, 7, world), "begin UI or navigation gesture before F1");
            pointer.cancel();
            pointer.cancel();
            require(pointer.button() < 0, "F1 removes the active pointer owner");
            require(pointer.consumeSuppressed(-1, false), "old drag motion remains quarantined");
            require(pointer.consumeSuppressed(button, false), "old release cannot click the restored HUD");
            pointer.reconcile(value -> false);
            require(!pointer.captured(), "F1 leaves no capture after release");
            require(pointer.capture(button, 7, 7, world), "fresh gesture works after visibility returns");
            pointer.finish();
        }
    }

    private static void rotationGestures() {
        CameraDragGesture gesture = new CameraDragGesture();
        gesture.begin(100, 200, 4);
        require(!gesture.move(103, 201) && gesture.click(), "short jitter remains a click without rotation");
        require(!gesture.move(104, 200) && gesture.click(), "threshold endpoint is still a click");
        require(gesture.move(105, 200) && !gesture.click(), "crossing the threshold latches drag");
        require(gesture.deltaX() == 5 && gesture.deltaY() == 0, "first drag includes movement held before threshold");
        require(gesture.move(100, 200) && !gesture.click(), "returning to origin must not select a point");
        gesture.begin(0, 0, 8);
        require(!gesture.move(6, 4), "scaled framebuffer threshold preserves logical-pixel jitter tolerance");
        require(gesture.move(7, 4) && !gesture.click(), "diagonal displacement also crosses threshold");
        gesture.begin(0, 0, 0);
        require(
            gesture.move(1, 0) && gesture.deltaX() == 1 && !gesture.click(),
            "legacy navigation rotates immediately");
        gesture.begin(0, 0, 4);
        require(gesture.click(), "a new capture clears the previous drag latch");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
