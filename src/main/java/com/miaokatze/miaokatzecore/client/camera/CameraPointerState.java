package com.miaokatze.miaokatzecore.client.camera;

import java.util.function.IntPredicate;

/** Pure state for a physical pointer sequence; reconciliation happens only after the event queue has drained. */
final class CameraPointerState {

    private int button = -1;
    private long epoch, suppressed;
    private Object world;

    int button() {
        return button;
    }

    boolean captured() {
        return button >= 0 || suppressed != 0;
    }

    boolean matches(long ownerEpoch, Object worldToken) {
        return epoch == ownerEpoch && world == worldToken;
    }

    boolean capture(int pressedButton, long frameEpoch, long currentEpoch, Object worldToken) {
        if (frameEpoch != currentEpoch) {
            suppress(pressedButton);
            return false;
        }
        button = pressedButton;
        epoch = currentEpoch;
        world = worldToken;
        return true;
    }

    boolean consumeSuppressed(int eventButton, boolean pressed) {
        if (eventButton >= 0 && eventButton < Long.SIZE && (suppressed & 1L << eventButton) != 0) {
            releaseSuppressed(eventButton);
            // A new native press proves the previous sequence ended, even if its external release was lost.
            return !pressed;
        }
        return button < 0 && suppressed != 0 && eventButton < 0;
    }

    void suppress(int pressedButton) {
        if (pressedButton >= 0 && pressedButton < Long.SIZE) suppressed |= 1L << pressedButton;
    }

    void releaseSuppressed(int releasedButton) {
        if (releasedButton >= 0 && releasedButton < Long.SIZE) suppressed &= ~(1L << releasedButton);
    }

    void cancel() {
        suppress(button);
        finish();
    }

    void finish() {
        button = -1;
        epoch = 0;
        world = null;
    }

    /** Only after all queued releases were isolated may missing OS releases be forgotten. */
    void reconcile(IntPredicate physicallyDown) {
        long remaining = suppressed;
        while (remaining != 0) {
            int bit = Long.numberOfTrailingZeros(remaining);
            if (!physicallyDown.test(bit)) releaseSuppressed(bit);
            remaining &= remaining - 1;
        }
    }
}
