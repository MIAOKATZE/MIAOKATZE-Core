package com.miaokatze.miaokatzecore.api.camera;

/** Bounded contracts for cached axes, finite ray intervals and real-surface slice coverage. */
public final class Camera014MathContracts {

    private Camera014MathContracts() {}

    public static void main(String[] args) {
        basis();
        foregroundCloudPlane();
        intervals();
        surfaces();
        validation();
        System.out
            .println("Camera014MathContracts: PASS (basis, foreground clouds, intervals, slice surfaces, validation)");
    }

    private static void basis() {
        OrthographicProjection projection = new OrthographicProjection(20, 10, 100, .05, 500);
        CameraBasis straight = new CameraBasis(new CameraPose(10, 20, 30, 0, 0));
        near(2, straight.screenDeltaX(2, 3), "screen-right is world X");
        near(3, straight.screenDeltaY(2, 3), "screen-up is world Y");
        near(0, straight.screenDeltaZ(2, 3), "straight pan has no Z");
        for (int facing = 0; facing < 4; facing++) {
            CameraPose pose = new CameraPose(123, 70, -456, 45 + 90 * facing, 60);
            CameraBasis cached = new CameraBasis(pose);
            ProjectedPoint expected = CameraProjection.project(pose, projection, 125, 64, -460);
            ProjectedPoint actual = cached.project(projection, 125, 64, -460);
            near(expected.ndcX(), actual.ndcX(), "cached projection X");
            near(expected.ndcY(), actual.ndcY(), "cached projection Y");
            near(expected.ndcZ(), actual.ndcZ(), "cached projection Z");
            CameraRay old = CameraProjection.unprojectRay(pose, projection, .4, -.3);
            CameraRay ray = cached.ray(projection, .4, -.3);
            near(old.originX(), ray.originX(), "cached near origin X");
            near(old.originY(), ray.originY(), "cached near origin Y");
            near(old.originZ(), ray.originZ(), "cached near origin Z");
            near(old.directionX(), ray.directionX(), "cached forward X");
            near(old.directionY(), ray.directionY(), "cached forward Y");
            near(old.directionZ(), ray.directionZ(), "cached forward Z");
            near(0, cached.rightX() * cached.upX() + cached.rightZ() * cached.upZ(), "right/up orthogonal");
        }
    }

    /** Reproduces the ground-anchor/high-eye mismatch without a native renderer or a cloud texture. */
    private static void foregroundCloudPlane() {
        OrthographicProjection projection = new OrthographicProjection(20, 15, 512, .05, 1024);
        for (int facing = 0; facing < 4; facing++) {
            CameraPose pose = new CameraPose(8.5, 5.62, 8.5, 45 + facing * 90, 60);
            CameraBasis basis = new CameraBasis(pose);
            CameraRay ray = basis.ray(projection, 0, 0);
            check(pose.anchorY() < 128 && ray.originY() > 128, "ground anchor can have an eye above vanilla clouds");
            double maxDistance = projection.far() - projection.near();
            double cloud = CameraIntersections.plane(ray, 0, 1, 0, 128, 0, maxDistance);
            double ground = CameraIntersections.plane(ray, 0, 1, 0, pose.anchorY(), 0, maxDistance);
            check(Double.isFinite(cloud) && cloud < ground, "cloud geometry lies in front of the factory floor");
            double cloudX = ray.originX() + ray.directionX() * cloud;
            double cloudZ = ray.originZ() + ray.directionZ() * cloud;
            check(
                Math.abs(cloudX - pose.anchorX()) < 256 && Math.abs(cloudZ - pose.anchorZ()) < 256,
                "center ray crosses the native fast-cloud mesh centered on the anchor");
            ProjectedPoint projected = basis.project(projection, cloudX, 128, cloudZ);
            near(0, projected.ndcX(), "cloud crosses the center screen X");
            near(0, projected.ndcY(), "cloud crosses the center screen Y");
        }
    }

    private static void intervals() {
        CameraRay ray = new CameraRay(2, 100, 3, 0, -1, 0);
        RayInterval original = new RayInterval(ray, 0, 200);
        check(original.clip(HeightSlice.disabled()) == original, "disabled clip reuses interval");
        RayInterval clipped = original.clip(HeightSlice.between(60, 70, .25));
        check(clipped != null && clipped.ray() == ray, "clipping preserves original near-plane ray identity");
        near(30, clipped.minDistance(), "upper boundary entry");
        near(40, clipped.maxDistance(), "lower boundary exit");
        near(
            100,
            clipped.ray()
                .originY(),
            "near plane not shifted to cut plane");
        RayInterval shortRange = new RayInterval(ray, 32, 35).clip(HeightSlice.between(60, 70, 2));
        near(32, shortRange.minDistance(), "existing minimum respected");
        near(35, shortRange.maxDistance(), "feather does not extend maximum");
        check(new RayInterval(ray, 0, 20).clip(HeightSlice.between(60, 70, 0)) == null, "slice past ray end");
        CameraRay parallel = new CameraRay(0, 70, 0, 1, 0, 0);
        check(
            new RayInterval(parallel, 0, 20).clip(HeightSlice.between(60, 70, 0)) == null,
            "parallel ray on exclusive upper layer misses volume");
        CameraRay lower = new CameraRay(0, 60, 0, 1, 0, 0);
        RayInterval tangent = new RayInterval(lower, 0, 20);
        check(tangent.clip(HeightSlice.between(60, 70, 0)) == tangent, "parallel lower boundary kept");
        check(
            HeightSlice.between(60, 70, 0)
                .containsBlockY(60),
            "minimum layer inclusive");
        check(
            !HeightSlice.between(60, 70, 0)
                .containsBlockY(70),
            "maximum layer exclusive");
        check(
            HeightSlice.above(Integer.MIN_VALUE, 0)
                .containsBlockY(Integer.MAX_VALUE),
            "unbounded upper end");
        check(
            HeightSlice.below(Integer.MAX_VALUE, 0)
                .containsBlockY(Integer.MIN_VALUE),
            "unbounded lower end");
        check(
            HeightSlice.below(70, -0.0)
                .equals(HeightSlice.below(70, 0.0)),
            "zero feather canonicalized");
    }

    private static void surfaces() {
        HeightSlice below = HeightSlice.below(70, 2);
        near(1, SliceSurfaceRule.coverageAt(below, 70, 1), "new top cap remains opaque");
        near(1, SliceSurfaceRule.coverageAt(below, 69.999, 1), "native liquid top cap remains opaque");
        near(0, SliceSurfaceRule.coverageAt(below, 70, 0), "side at cut boundary is not a synthetic cap");
        near(.5, SliceSurfaceRule.coverageAt(below, 69, 0), "inside feather midpoint");
        near(1, SliceSurfaceRule.coverageAt(below, 68, 0), "interior surface remains opaque");
        near(0, SliceSurfaceRule.coverageAt(below, 71, 1), "exterior surface hidden");
        HeightSlice above = HeightSlice.above(60, 2);
        near(1, SliceSurfaceRule.coverageAt(above, 60, -1), "new bottom cap remains opaque");
        near(0, SliceSurfaceRule.coverageAt(above, 60, 1), "inward normal receives no cap override");
        near(.5, SliceSurfaceRule.coverageAt(above, 61, 0), "lower interior feather");
        int kept = 0;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                if (SliceSurfaceRule.keeps(below, 69, 0, x, y)) kept++;
                check(SliceSurfaceRule.keeps(below, 70, 1, x, y), "cap kept at every dither pixel");
                near(
                    SliceSurfaceRule.thresholdAt(x, y),
                    SliceSurfaceRule.thresholdAt(x + 4, y - 4),
                    "dither wraps display coordinates");
            }
        }
        check(kept == 8, "half coverage keeps eight of sixteen pixels");
        near(.03125, SliceSurfaceRule.thresholdAt(0, 0), "Bayer origin threshold");
        near(.53125, SliceSurfaceRule.thresholdAt(1, 0), "Bayer adjacent threshold");
        HeightSlice fading = HeightSlice.below(70, 1)
            .withFeatheredCaps(true);
        near(.5, SliceSurfaceRule.coverageAt(fading, 70, 1), "one-block feather fades exposed top");
        near(.5, SliceSurfaceRule.coverageAt(fading, 69.999, 1), "liquid top belongs to outer layer");
        near(.5, SliceSurfaceRule.coverageAt(fading, 69.1, 0), "outer block side has consistent coverage");
        near(1, SliceSurfaceRule.coverageAt(fading, 69, 1), "native face behind fading layer is opaque");
        near(0, SliceSurfaceRule.coverageAt(fading, 70.1, 0), "excluded layer remains invisible");
        HeightSlice lower = HeightSlice.above(-17, 1)
            .withFeatheredCaps(true);
        near(.5, SliceSurfaceRule.coverageAt(lower, -17, -1), "negative-Y bottom cap fades symmetrically");
        near(.5, SliceSurfaceRule.coverageAt(lower, -16.7, 0), "negative-Y side uses owning layer");
        near(1, SliceSurfaceRule.coverageAt(lower, -16, -1), "bottom face beyond feather stays opaque");
        near(
            1,
            SliceSurfaceRule.coverageAt(
                HeightSlice.below(70, 0)
                    .withFeatheredCaps(true),
                70,
                1),
            "zero-width optional feather preserves solid cap");
        kept = 0;
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) if (SliceSurfaceRule.keeps(fading, 70, 1, x, y)) kept++;
        check(kept == 8, "optional cap actually exposes the geometry behind eight of sixteen pixels");
        check(!fading.equals(HeightSlice.below(70, 1)), "appearance is part of immutable slice identity");
    }

    private static void validation() {
        expect(() -> HeightSlice.between(2, 2, 0));
        expect(() -> HeightSlice.above(1, -1));
        expect(() -> HeightSlice.below(1, Double.NaN));
        expect(() -> new RayInterval(new CameraRay(0, 0, 0, 1, 0, 0), 0, Double.POSITIVE_INFINITY));
        expect(() -> SliceSurfaceRule.coverageAt(HeightSlice.disabled(), Double.NaN, 0));
        expect(() -> SliceSurfaceRule.coverageAt(HeightSlice.disabled(), 0, 2));
        expect(() -> new CameraBasis(new CameraPose(0, 0, 0, 0, 0)).screenDeltaX(Double.NaN, 0));
    }

    private static void expect(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    private static void near(double expected, double actual, String label) {
        check(Double.isFinite(actual) && Math.abs(expected - actual) <= 1e-9, label + ": " + actual);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
