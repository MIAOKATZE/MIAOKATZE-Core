package com.miaokatze.miaokatzecore.api.ui.component;

/**
 * Pointer events bubble from target to ancestors until consumed. Coordinates already include inverse scene transform.
 */
public final class UiEvent {

    public enum Type {
        DOWN,
        MOVE,
        UP,
        SCROLL,
        CANCEL
    }

    public final Type type;
    public final UiNode target;
    public UiNode currentTarget;
    public final double x, y, wheel;
    public final int button;
    private final UiScene scene;
    private boolean consumed;

    UiEvent(UiScene scene, Type type, UiNode target, double x, double y, int button, double wheel) {
        this.scene = scene;
        this.type = type;
        this.target = target;
        this.x = x;
        this.y = y;
        this.button = button;
        this.wheel = wheel;
    }

    public double localX() {
        return x - currentTarget.bounds().x;
    }

    public double localY() {
        return y - currentTarget.bounds().y;
    }

    public void consume() {
        consumed = true;
    }

    public boolean consumed() {
        return consumed;
    }

    /** Capture is only granted during DOWN, and remains until the same button releases or lifecycle cancellation. */
    public void capturePointer() {
        if (type == Type.DOWN) scene.capture(currentTarget, button);
    }

    public void releasePointer() {
        scene.release(currentTarget);
    }
}
