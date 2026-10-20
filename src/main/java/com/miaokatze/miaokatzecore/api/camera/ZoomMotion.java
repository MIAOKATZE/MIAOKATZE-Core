package com.miaokatze.miaokatzecore.api.camera;

/**
 * Caller-driven target/actual zoom with Outpost's frame-based inertia and smoothing formulas.
 * The caller owns input, bounds and frame scheduling. Instances are not thread safe.
 */
public final class ZoomMotion {

    private double minimum, maximum, actual, target, velocity;

    public ZoomMotion(double initial, double minimum, double maximum) {
        bounds(minimum, maximum);
        snap(initial);
    }

    /** Changes the caller's permitted interval and clamps both current values into it. */
    public void bounds(double minimum, double maximum) {
        boundsPreservingActual(minimum, maximum);
        this.actual = clamp(actual);
    }

    /**
     * Changes the permitted interval and clamps only the target, preserving actual and residual inertia.
     * The next advance calls smoothly bring an out-of-bounds actual value into the new interval.
     */
    public void boundsPreservingActual(double minimum, double maximum) {
        CameraNumbers.positive(minimum, "minimum");
        CameraNumbers.positive(maximum, "maximum");
        if (maximum < minimum) throw new IllegalArgumentException("maximum must not be below minimum");
        this.minimum = minimum;
        this.maximum = maximum;
        this.target = clamp(target);
    }

    /** Immediately sets both values, clearing residual inertia. */
    public void snap(double value) {
        CameraNumbers.finite(value, "value");
        actual = target = clamp(value);
        velocity = 0;
    }

    /**
     * Multiplies the target, adding (clamped target delta * strength) to inertia. Zero strength disables new impulses.
     */
    public void multiply(double factor, double inertiaStrength) {
        CameraNumbers.positive(factor, "factor");
        CameraNumbers.finite(inertiaStrength, "inertiaStrength");
        if (inertiaStrength < 0) throw new IllegalArgumentException("inertiaStrength must not be negative");
        double next = clamp(target * factor);
        double nextVelocity = velocity + (next - target) * inertiaStrength;
        CameraNumbers.finite(nextVelocity, "resulting velocity");
        target = next;
        velocity = nextVelocity;
    }

    /**
     * Advances one frame. Smoothing and decay are in [0,1]; smoothing=1 snaps and clears inertia.
     * Otherwise decay precedes the clamped goal, alpha has a .05 floor, velocity snaps below 1e-4,
     * and an idle actual value snaps to target below 1e-3. Outpost uses decay=.85.
     */
    public void advance(double smoothing, double decay) {
        CameraNumbers.unitInterval(smoothing, "smoothing");
        CameraNumbers.unitInterval(decay, "decay");
        if (smoothing == 1) {
            actual = target;
            velocity = 0;
            return;
        }
        velocity *= decay;
        if (Math.abs(velocity) < 1e-4) velocity = 0;
        double goal = clamp(target + velocity);
        actual += (goal - actual) * Math.max(.05, smoothing);
        if (velocity == 0 && Math.abs(target - actual) < 1e-3) actual = target;
    }

    public double actual() {
        return actual;
    }

    public double target() {
        return target;
    }

    private double clamp(double value) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
