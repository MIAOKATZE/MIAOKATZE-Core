package com.miaokatze.miaokatzecore.client.camera;

/** Raw sequence contracts independent of Forge event ordering or native input. */
public final class CameraPointerContracts {

    public static void main(String[] args) {
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

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
