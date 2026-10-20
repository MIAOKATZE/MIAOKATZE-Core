package com.miaokatze.miaokatzecore.client.camera;

/** Pure wheel units and bounded zoom response, shared by input and dependency-free contracts. */
final class CameraWheel {

    private CameraWheel() {}

    static double factor(int delta, CameraSettings.Navigation policy, boolean unitNotches) {
        boolean notch = policy.wheelDeltaUnit() == CameraSettings.Navigation.WheelDeltaUnit.NOTCH
            || policy.wheelDeltaUnit() == CameraSettings.Navigation.WheelDeltaUnit.AUTO && unitNotches;
        double exponent = delta / (notch ? 1.0 : 120.0) * policy.wheelSensitivity();
        double limit = policy.wheelZoomLimit();
        if (Double.isFinite(limit)) {
            double maximumExponent = Math.log(limit) / Math.log(1.1);
            exponent = Math.max(-maximumExponent, Math.min(maximumExponent, exponent));
        }
        return Math.pow(1.1, exponent);
    }
}
