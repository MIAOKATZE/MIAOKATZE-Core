package com.miaokatze.miaokatzecore.api.camera;

/** Immutable world ray. Its direction is normalized, so its parameter is world distance. */
public final class CameraRay {

    private final double originX, originY, originZ, directionX, directionY, directionZ;

    public CameraRay(double originX, double originY, double originZ, double directionX, double directionY,
        double directionZ) {
        this.originX = CameraNumbers.finite(originX, "originX");
        this.originY = CameraNumbers.finite(originY, "originY");
        this.originZ = CameraNumbers.finite(originZ, "originZ");
        CameraNumbers.finite(directionX, "directionX");
        CameraNumbers.finite(directionY, "directionY");
        CameraNumbers.finite(directionZ, "directionZ");
        double scale = Math.max(Math.abs(directionX), Math.max(Math.abs(directionY), Math.abs(directionZ)));
        if (scale == 0) throw new IllegalArgumentException("Direction must be nonzero");
        double x = directionX / scale, y = directionY / scale, z = directionZ / scale;
        double length = Math.sqrt(x * x + y * y + z * z);
        this.directionX = x / length;
        this.directionY = y / length;
        this.directionZ = z / length;
    }

    public double originX() {
        return originX;
    }

    public double originY() {
        return originY;
    }

    public double originZ() {
        return originZ;
    }

    public double directionX() {
        return directionX;
    }

    public double directionY() {
        return directionY;
    }

    public double directionZ() {
        return directionZ;
    }
}
