package com.miaokatze.miaokatzecore.api.camera;

import java.util.Objects;

/** Precomputed orthonormal world axes for Tz(-depth) Rx(pitch) Ry(yaw) T(-anchor). */
public final class CameraBasis {

    private final CameraPose pose;
    private final double rightX, rightZ, upX, upY, upZ, forwardX, forwardY, forwardZ;

    public CameraBasis(CameraPose pose) {
        this.pose = Objects.requireNonNull(pose, "pose");
        double yaw = Math.toRadians(pose.yawDegrees() % 360);
        double pitch = Math.toRadians(pose.pitchDegrees() % 360);
        double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
        rightX = cy;
        rightZ = sy;
        upX = sy * sp;
        upY = cp;
        upZ = -cy * sp;
        forwardX = sy * cp;
        forwardY = -sp;
        forwardZ = -cy * cp;
    }

    public CameraPose pose() {
        return pose;
    }

    public double rightX() {
        return rightX;
    }

    public double rightY() {
        return 0;
    }

    public double rightZ() {
        return rightZ;
    }

    public double upX() {
        return upX;
    }

    public double upY() {
        return upY;
    }

    public double upZ() {
        return upZ;
    }

    public double forwardX() {
        return forwardX;
    }

    public double forwardY() {
        return forwardY;
    }

    public double forwardZ() {
        return forwardZ;
    }

    /** Converts screen-right/screen-up distances in world units to a world delta component. */
    public double screenDeltaX(double screenRight, double screenUp) {
        validateDelta(screenRight, screenUp);
        return CameraNumbers.finite(rightX * screenRight + upX * screenUp, "worldDeltaX");
    }

    public double screenDeltaY(double screenRight, double screenUp) {
        validateDelta(screenRight, screenUp);
        return CameraNumbers.finite(upY * screenUp, "worldDeltaY");
    }

    public double screenDeltaZ(double screenRight, double screenUp) {
        validateDelta(screenRight, screenUp);
        return CameraNumbers.finite(rightZ * screenRight + upZ * screenUp, "worldDeltaZ");
    }

    public ProjectedPoint project(OrthographicProjection projection, double worldX, double worldY, double worldZ) {
        Objects.requireNonNull(projection, "projection");
        CameraNumbers.finite(worldX, "worldX");
        CameraNumbers.finite(worldY, "worldY");
        CameraNumbers.finite(worldZ, "worldZ");
        double x = worldX - pose.anchorX(), y = worldY - pose.anchorY(), z = worldZ - pose.anchorZ();
        double distance = projection.depthOffset() + forwardX * x + forwardY * y + forwardZ * z;
        return new ProjectedPoint(
            (rightX * x + rightZ * z) / projection.halfWidth(),
            (upX * x + upY * y + upZ * z) / projection.halfHeight(),
            2 * ((distance - projection.near()) / (projection.far() - projection.near())) - 1);
    }

    /** NDC Y points upward; origin is the original near plane, including when the ray is later clipped. */
    public CameraRay ray(OrthographicProjection projection, double ndcX, double ndcY) {
        Objects.requireNonNull(projection, "projection");
        CameraNumbers.finite(ndcX, "ndcX");
        CameraNumbers.finite(ndcY, "ndcY");
        double x = ndcX * projection.halfWidth(), y = ndcY * projection.halfHeight();
        double z = projection.depthOffset() - projection.near();
        return new CameraRay(
            pose.anchorX() + rightX * x + upX * y - forwardX * z,
            pose.anchorY() + upY * y - forwardY * z,
            pose.anchorZ() + rightZ * x + upZ * y - forwardZ * z,
            forwardX,
            forwardY,
            forwardZ);
    }

    private static void validateDelta(double right, double up) {
        CameraNumbers.finite(right, "screenRight");
        CameraNumbers.finite(up, "screenUp");
    }
}
