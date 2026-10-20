package com.miaokatze.miaokatzecore.api.camera;

/** World anchor and OpenGL-style view rotations, in degrees. No world or mode policy is retained. */
public final class CameraPose {

    private final double anchorX, anchorY, anchorZ, yawDegrees, pitchDegrees;

    public CameraPose(double anchorX, double anchorY, double anchorZ, double yawDegrees, double pitchDegrees) {
        this.anchorX = CameraNumbers.finite(anchorX, "anchorX");
        this.anchorY = CameraNumbers.finite(anchorY, "anchorY");
        this.anchorZ = CameraNumbers.finite(anchorZ, "anchorZ");
        this.yawDegrees = CameraNumbers.finite(yawDegrees, "yawDegrees");
        this.pitchDegrees = CameraNumbers.finite(pitchDegrees, "pitchDegrees");
    }

    public double anchorX() {
        return anchorX;
    }

    public double anchorY() {
        return anchorY;
    }

    public double anchorZ() {
        return anchorZ;
    }

    public double yawDegrees() {
        return yawDegrees;
    }

    public double pitchDegrees() {
        return pitchDegrees;
    }
}
