package com.miaokatze.miaokatzecore.api.miaovision;

/** Immutable visual proxy, never an inventory or Minecraft entity. Units are blocks and seconds. */
public final class SceneBody {

    public final String id;
    public final double halfX, halfY, halfZ, x, y, z, qx, qy, qz, qw, vx, vy, vz, wx, wy, wz;
    public final boolean sleeping;
    /** Integrator sleep hysteresis, persisted for exact resume. */
    public final int quietTicks;

    public SceneBody(String id, double halfX, double halfY, double halfZ, double x, double y, double z) {
        this(id, halfX, halfY, halfZ, x, y, z, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, false);
    }

    public SceneBody(String id, double halfX, double halfY, double halfZ, double x, double y, double z, double qx,
        double qy, double qz, double qw, double vx, double vy, double vz, double wx, double wy, double wz,
        boolean sleeping) {
        this(id, halfX, halfY, halfZ, x, y, z, qx, qy, qz, qw, vx, vy, vz, wx, wy, wz, sleeping, 0);
    }

    public SceneBody(String id, double halfX, double halfY, double halfZ, double x, double y, double z, double qx,
        double qy, double qz, double qw, double vx, double vy, double vz, double wx, double wy, double wz,
        boolean sleeping, int quietTicks) {
        if (quietTicks < 0 || quietTicks > 20) throw new IllegalArgumentException("quietTicks");
        this.quietTicks = quietTicks;
        if (id == null || id.isEmpty() || id.length() > 128) throw new IllegalArgumentException("body id");
        for (double n : new double[] { halfX, halfY, halfZ, x, y, z, qx, qy, qz, qw, vx, vy, vz, wx, wy, wz })
            if (!Double.isFinite(n) || Math.abs(n) > 1000000)
                throw new IllegalArgumentException("non-finite/unbounded body");
        if (halfX <= 0 || halfY <= 0 || halfZ <= 0 || halfX > 16 || halfY > 16 || halfZ > 16)
            throw new IllegalArgumentException("half extents outside (0,16]");
        double norm = qx * qx + qy * qy + qz * qz + qw * qw;
        if (Math.abs(norm - 1) > 0.00001) throw new IllegalArgumentException("quaternion must be normalized");
        if (Math.max(Math.max(Math.abs(vx), Math.abs(vy)), Math.abs(vz)) > 32
            || Math.max(Math.max(Math.abs(wx), Math.abs(wy)), Math.abs(wz)) > 32)
            throw new IllegalArgumentException("velocity outside [-32,32]");
        this.id = id;
        this.halfX = halfX;
        this.halfY = halfY;
        this.halfZ = halfZ;
        this.x = x;
        this.y = y;
        this.z = z;
        this.qx = qx;
        this.qy = qy;
        this.qz = qz;
        this.qw = qw;
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        this.wx = wx;
        this.wy = wy;
        this.wz = wz;
        this.sleeping = sleeping;
    }

    public SceneBody withPosition(double x, double y, double z) {
        return new SceneBody(
            id,
            halfX,
            halfY,
            halfZ,
            x,
            y,
            z,
            qx,
            qy,
            qz,
            qw,
            vx,
            vy,
            vz,
            wx,
            wy,
            wz,
            sleeping,
            quietTicks);
    }

    public SceneBody withVelocity(double vx, double vy, double vz, double wx, double wy, double wz) {
        return new SceneBody(
            id,
            halfX,
            halfY,
            halfZ,
            x,
            y,
            z,
            qx,
            qy,
            qz,
            qw,
            vx,
            vy,
            vz,
            wx,
            wy,
            wz,
            sleeping,
            quietTicks);
    }

    public SceneBody withRotation(double qx, double qy, double qz, double qw) {
        return new SceneBody(
            id,
            halfX,
            halfY,
            halfZ,
            x,
            y,
            z,
            qx,
            qy,
            qz,
            qw,
            vx,
            vy,
            vz,
            wx,
            wy,
            wz,
            sleeping,
            quietTicks);
    }

    public SceneBody withSleeping(boolean sleeping) {
        return new SceneBody(
            id,
            halfX,
            halfY,
            halfZ,
            x,
            y,
            z,
            qx,
            qy,
            qz,
            qw,
            vx,
            vy,
            vz,
            wx,
            wy,
            wz,
            sleeping,
            quietTicks);
    }

    public SceneBody withQuietTicks(int ticks) {
        return new SceneBody(id, halfX, halfY, halfZ, x, y, z, qx, qy, qz, qw, vx, vy, vz, wx, wy, wz, sleeping, ticks);
    }

    /** Rotation-aware conservative contact extents; no promise of a full rigid-body engine. */
    public double extentX() {
        return Math.abs(1 - 2 * (qy * qy + qz * qz)) * halfX + Math.abs(2 * (qx * qy - qz * qw)) * halfY
            + Math.abs(2 * (qx * qz + qy * qw)) * halfZ;
    }

    public double extentY() {
        return Math.abs(2 * (qx * qy + qz * qw)) * halfX + Math.abs(1 - 2 * (qx * qx + qz * qz)) * halfY
            + Math.abs(2 * (qy * qz - qx * qw)) * halfZ;
    }

    public double extentZ() {
        return Math.abs(2 * (qx * qz - qy * qw)) * halfX + Math.abs(2 * (qy * qz + qx * qw)) * halfY
            + Math.abs(1 - 2 * (qx * qx + qy * qy)) * halfZ;
    }
}
