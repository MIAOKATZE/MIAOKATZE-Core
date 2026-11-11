// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.api.camera;

/** Normalized device coordinates. Visible points are in [-1, 1] on all three axes; Y points upward. */
public final class ProjectedPoint {

    private final double ndcX, ndcY, ndcZ;

    public ProjectedPoint(double ndcX, double ndcY, double ndcZ) {
        this.ndcX = CameraNumbers.finite(ndcX, "ndcX");
        this.ndcY = CameraNumbers.finite(ndcY, "ndcY");
        this.ndcZ = CameraNumbers.finite(ndcZ, "ndcZ");
    }

    public double ndcX() {
        return ndcX;
    }

    public double ndcY() {
        return ndcY;
    }

    public double ndcZ() {
        return ndcZ;
    }
}
