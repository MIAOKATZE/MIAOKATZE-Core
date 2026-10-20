package com.miaokatze.miaokatzecore.api.camera;

/** Pure transform matching Tz(-depth) Rx(pitch) Ry(yaw) T(-anchor), without reading graphics state. */
public final class CameraProjection {

    private CameraProjection() {}

    /** Projects a world point without clipping; returned Z is -1 at near and +1 at far. */
    public static ProjectedPoint project(CameraPose pose, OrthographicProjection projection, double worldX,
        double worldY, double worldZ) {
        CameraNumbers.finite(worldX, "worldX");
        CameraNumbers.finite(worldY, "worldY");
        CameraNumbers.finite(worldZ, "worldZ");
        double yaw = Math.toRadians(pose.yawDegrees() % 360), pitch = Math.toRadians(pose.pitchDegrees() % 360);
        double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
        double x = worldX - pose.anchorX(), y = worldY - pose.anchorY(), z = worldZ - pose.anchorZ();
        double rotatedX = cy * x + sy * z;
        double rotatedZ = -sy * x + cy * z;
        double viewY = cp * y - sp * rotatedZ;
        double distance = projection.depthOffset() - (sp * y + cp * rotatedZ);
        return new ProjectedPoint(
            rotatedX / projection.halfWidth(),
            viewY / projection.halfHeight(),
            2 * ((distance - projection.near()) / (projection.far() - projection.near())) - 1);
    }

    /** Returns the parallel world ray beginning on the near plane. NDC values may lie outside the viewport. */
    public static CameraRay unprojectRay(CameraPose pose, OrthographicProjection projection, double ndcX, double ndcY) {
        CameraNumbers.finite(ndcX, "ndcX");
        CameraNumbers.finite(ndcY, "ndcY");
        double yaw = Math.toRadians(pose.yawDegrees() % 360), pitch = Math.toRadians(pose.pitchDegrees() % 360);
        double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
        double x = ndcX * projection.halfWidth(), y = ndcY * projection.halfHeight();
        double z = projection.depthOffset() - projection.near();
        double rotatedY = cp * y + sp * z, rotatedZ = -sp * y + cp * z;
        return new CameraRay(
            pose.anchorX() + cy * x - sy * rotatedZ,
            pose.anchorY() + rotatedY,
            pose.anchorZ() + sy * x + cy * rotatedZ,
            sy * cp,
            -sp,
            -cy * cp);
    }
}
