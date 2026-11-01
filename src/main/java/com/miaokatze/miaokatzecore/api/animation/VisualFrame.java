package com.miaokatze.miaokatzecore.api.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable presentation values with exact complete-clip provenance, including bindings and materials. */
public strictfp final class VisualFrame {

    private final VisualClip source;
    public final String documentId;
    public final long localTimeMs;
    public final List<TargetPose> targets;

    VisualFrame(VisualClip source, long localTimeMs, List<TargetPose> targets) {
        this.source = source;
        documentId = source.scalar.id;
        this.localTimeMs = localTimeMs;
        this.targets = Collections.unmodifiableList(new ArrayList<>(targets));
    }

    public boolean sampledFrom(VisualClip clip) {
        return source == clip;
    }

    /** Resolved unit-box pose; no property interpretation is left to the renderer. */
    public static final class TargetPose {

        public final String targetId, materialId;
        public final double x, y, z, qx, qy, qz, qw, scaleX, scaleY, scaleZ;
        public final int rgb;
        public final double opacity;
        public final boolean visible;

        TargetPose(String targetId, String materialId, double x, double y, double z, double qx, double qy, double qz,
            double qw, double scaleX, double scaleY, double scaleZ, int rgb, double opacity, boolean visible) {
            this.targetId = targetId;
            this.materialId = materialId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.qx = qx;
            this.qy = qy;
            this.qz = qz;
            this.qw = qw;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.scaleZ = scaleZ;
            this.rgb = rgb;
            this.opacity = opacity;
            this.visible = visible;
        }

        /** Returns a fresh column-major matrix M = translation * rotation * scale. */
        public double[] matrix() {
            return new double[] { (1 - 2 * (qy * qy + qz * qz)) * scaleX, 2 * (qx * qy + qz * qw) * scaleX,
                2 * (qx * qz - qy * qw) * scaleX, 0, 2 * (qx * qy - qz * qw) * scaleY,
                (1 - 2 * (qx * qx + qz * qz)) * scaleY, 2 * (qy * qz + qx * qw) * scaleY, 0,
                2 * (qx * qz + qy * qw) * scaleZ, 2 * (qy * qz - qx * qw) * scaleZ,
                (1 - 2 * (qx * qx + qy * qy)) * scaleZ, 0, x, y, z, 1 };
        }
    }
}
