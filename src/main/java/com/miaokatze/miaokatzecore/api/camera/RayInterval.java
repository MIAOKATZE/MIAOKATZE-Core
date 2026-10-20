package com.miaokatze.miaokatzecore.api.camera;

import java.util.Objects;

/** A finite, inclusive distance interval on the original normalized near-plane ray. */
public final class RayInterval {

    private final CameraRay ray;
    private final double minDistance, maxDistance;

    public RayInterval(CameraRay ray, double minDistance, double maxDistance) {
        this.ray = Objects.requireNonNull(ray, "ray");
        CameraNumbers.distances(minDistance, maxDistance);
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
    }

    public CameraRay ray() {
        return ray;
    }

    public double minDistance() {
        return minDistance;
    }

    public double maxDistance() {
        return maxDistance;
    }

    /**
     * Clips only distances; never moves the ray origin or invents a hit/face on a clipping plane.
     * Continuous boundary distances remain available for real outward-facing cap surfaces.
     * Feather operates inside the band and does not enlarge this interval. A miss returns null.
     */
    public RayInterval clip(HeightSlice slice) {
        Objects.requireNonNull(slice, "slice");
        if (!slice.enabled()) return this;
        double origin = ray.originY(), direction = ray.directionY();
        if (direction == 0) {
            return (slice.hasLower() && origin < slice.minimumY()) || (slice.hasUpper() && origin >= slice.maximumY())
                ? null
                : this;
        }
        double lower = minDistance, upper = maxDistance;
        if (slice.hasLower()) {
            double at = (slice.minimumY() - origin) / direction;
            if (direction > 0) lower = Math.max(lower, at);
            else upper = Math.min(upper, at);
        }
        if (slice.hasUpper()) {
            double at = (slice.maximumY() - origin) / direction;
            if (direction > 0) upper = Math.min(upper, at);
            else lower = Math.max(lower, at);
        }
        return lower <= upper ? new RayInterval(ray, lower, upper) : null;
    }
}
