package com.miaokatze.miaokatzecore.api.miaovision;

/** Finite local room. This is not a dimension registration or a reference to a game world. */
public final class SceneBounds {

    public final double minX, minY, minZ, maxX, maxY, maxZ;

    public SceneBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        for (double n : new double[] { minX, minY, minZ, maxX, maxY, maxZ })
            if (!Double.isFinite(n) || Math.abs(n) > 1000000) throw new IllegalArgumentException("bounds");
        if (maxX <= minX || maxY <= minY || maxZ <= minZ || maxX - minX > 256 || maxY - minY > 256 || maxZ - minZ > 256)
            throw new IllegalArgumentException("room dimensions must be in (0,256]");
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public void validate(SceneBody body) {
        double a = body.extentX(), b = body.extentY(), c = body.extentZ();
        // Positions may touch a wall; tiny contact roundoff is tolerated, never an arbitrary off-room pose.
        if (a * 2 > maxX - minX || b * 2 > maxY - minY
            || c * 2 > maxZ - minZ
            || body.x < minX + a - 0.00001
            || body.x > maxX - a + 0.00001
            || body.y < minY + b - 0.00001
            || body.y > maxY - b + 0.00001
            || body.z < minZ + c - 0.00001
            || body.z > maxZ - c + 0.00001) throw new IllegalArgumentException("body outside room: " + body.id);
    }
}
