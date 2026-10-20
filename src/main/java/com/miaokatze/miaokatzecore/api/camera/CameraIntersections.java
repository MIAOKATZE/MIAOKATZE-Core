package com.miaokatze.miaokatzecore.api.camera;

/** Allocation-free bounded intersections along normalized world rays. Bounds are inclusive. */
public final class CameraIntersections {

    private CameraIntersections() {}

    /**
     * Intersects n dot position = planeOffset; n need not be normalized. Returns world distance or NaN.
     * A ray contained in the plane hits at minDistance. A parallel, disjoint ray misses.
     */
    public static double plane(CameraRay ray, double nx, double ny, double nz, double planeOffset, double minDistance,
        double maxDistance) {
        CameraNumbers.finite(nx, "nx");
        CameraNumbers.finite(ny, "ny");
        CameraNumbers.finite(nz, "nz");
        CameraNumbers.finite(planeOffset, "planeOffset");
        CameraNumbers.distances(minDistance, maxDistance);
        double scale = Math.max(Math.abs(nx), Math.max(Math.abs(ny), Math.abs(nz)));
        if (scale == 0) throw new IllegalArgumentException("Plane normal must be nonzero");
        nx /= scale;
        ny /= scale;
        nz /= scale;
        double numerator = planeOffset / scale - (nx * ray.originX() + ny * ray.originY() + nz * ray.originZ());
        double denominator = nx * ray.directionX() + ny * ray.directionY() + nz * ray.directionZ();
        if (denominator == 0) return numerator == 0 ? minDistance : Double.NaN;
        double distance = numerator / denominator;
        return distance >= minDistance && distance <= maxDistance ? distance : Double.NaN;
    }

    /** Returns the first distance in the box and requested interval, including minDistance when already inside. */
    public static double aabb(CameraRay ray, double minX, double minY, double minZ, double maxX, double maxY,
        double maxZ, double minDistance, double maxDistance) {
        CameraNumbers.finite(minX, "minX");
        CameraNumbers.finite(minY, "minY");
        CameraNumbers.finite(minZ, "minZ");
        CameraNumbers.finite(maxX, "maxX");
        CameraNumbers.finite(maxY, "maxY");
        CameraNumbers.finite(maxZ, "maxZ");
        CameraNumbers.distances(minDistance, maxDistance);
        if (minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("Inverted box bounds");
        double enter = minDistance, exit = maxDistance;
        if (ray.directionX() == 0) {
            if (ray.originX() < minX || ray.originX() > maxX) return Double.NaN;
        } else {
            double a = (minX - ray.originX()) / ray.directionX();
            double b = (maxX - ray.originX()) / ray.directionX();
            enter = Math.max(enter, Math.min(a, b));
            exit = Math.min(exit, Math.max(a, b));
            if (enter > exit) return Double.NaN;
        }
        if (ray.directionY() == 0) {
            if (ray.originY() < minY || ray.originY() > maxY) return Double.NaN;
        } else {
            double a = (minY - ray.originY()) / ray.directionY();
            double b = (maxY - ray.originY()) / ray.directionY();
            enter = Math.max(enter, Math.min(a, b));
            exit = Math.min(exit, Math.max(a, b));
            if (enter > exit) return Double.NaN;
        }
        if (ray.directionZ() == 0) {
            if (ray.originZ() < minZ || ray.originZ() > maxZ) return Double.NaN;
        } else {
            double a = (minZ - ray.originZ()) / ray.directionZ();
            double b = (maxZ - ray.originZ()) / ray.directionZ();
            enter = Math.max(enter, Math.min(a, b));
            exit = Math.min(exit, Math.max(a, b));
        }
        return enter <= exit ? enter : Double.NaN;
    }
}
