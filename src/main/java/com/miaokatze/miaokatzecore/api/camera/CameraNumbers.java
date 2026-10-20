package com.miaokatze.miaokatzecore.api.camera;

final class CameraNumbers {

    private CameraNumbers() {}

    static double finite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
        return value;
    }

    static double positive(double value, String name) {
        finite(value, name);
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    static void distances(double min, double max) {
        finite(min, "minDistance");
        finite(max, "maxDistance");
        if (min < 0 || max < min) throw new IllegalArgumentException("Require 0 <= minDistance <= maxDistance");
    }

    static double unitInterval(double value, String name) {
        finite(value, name);
        if (value < 0 || value > 1) throw new IllegalArgumentException(name + " must be in [0, 1]");
        return value;
    }
}
