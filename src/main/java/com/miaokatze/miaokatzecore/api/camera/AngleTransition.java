package com.miaokatze.miaokatzecore.api.camera;

/** Caller-timed shortest-angle quadratic ease-in/out. Instances are not thread safe. */
public final class AngleTransition {

    private double current, from, delta, destination;
    private long startedNanos, durationNanos;
    private boolean transitioning;

    public AngleTransition(double initial) {
        snap(initial);
    }

    public void snap(double degrees) {
        current = CameraNumbers.finite(degrees, "degrees");
        transitioning = false;
    }

    /**
     * Restarts continuously from the sampled angle. A zero duration snaps; negative durations are rejected.
     * Exactly opposite angles turn +180 degrees. Completion returns the exact caller-supplied destination.
     */
    public void transitionTo(double degrees, long nowNanos, long durationNanos) {
        CameraNumbers.finite(degrees, "degrees");
        if (durationNanos < 0) throw new IllegalArgumentException("durationNanos must not be negative");
        if (durationNanos == 0) {
            snap(degrees);
            return;
        }
        from = sample(nowNanos);
        delta = (degrees % 360 - from % 360) % 360;
        if (delta > 180) delta -= 360;
        if (delta <= -180) delta += 360;
        destination = degrees;
        startedNanos = nowNanos;
        this.durationNanos = durationNanos;
        transitioning = true;
    }

    /**
     * Uses nanoTime subtraction, supporting clock wraparound for elapsed intervals shorter than 2^63 ns.
     * Timestamps at or before the start sample the starting value. The caller supplies one monotonic clock.
     */
    public double sample(long nowNanos) {
        if (!transitioning) return current;
        long elapsed = nowNanos - startedNanos;
        if (elapsed >= durationNanos) {
            transitioning = false;
            current = destination;
        } else if (elapsed <= 0) {
            current = from;
        } else {
            double t = (double) elapsed / durationNanos;
            double eased = t < .5 ? 2 * t * t : 1 - (-2 * t + 2) * (-2 * t + 2) / 2;
            current = from + delta * eased;
        }
        return current;
    }
}
