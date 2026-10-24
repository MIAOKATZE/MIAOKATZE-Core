package com.miaokatze.miaokatzecore.api.camera;

/** Small standalone JVM contracts, with independent known values and bounded geometric cases. */
public final class CameraContracts {

    private CameraContracts() {}

    public static void main(String[] args) {
        projection();
        expandedZoom();
        intersections();
        invalidNumbers();
        motion();
        System.out.println("CameraContracts: PASS (projection, intersections, validation, zoom, angles)");
    }

    private static void projection() {
        OrthographicProjection projection = new OrthographicProjection(20, 10, 100, .05, 500);
        CameraPose straight = new CameraPose(10, 20, 30, 0, 0);
        CameraRay center = CameraProjection.unprojectRay(straight, projection, 0, 0);
        near(10, center.originX(), "center x");
        near(20, center.originY(), "center y");
        near(129.95, center.originZ(), "near-plane z");
        near(-1, center.directionZ(), "forward is -Z");
        ProjectedPoint edge = CameraProjection.project(straight, projection, 30, 30, 129.95);
        near(1, edge.ndcX(), "right edge");
        near(1, edge.ndcY(), "top edge");
        near(-1, edge.ndcZ(), "near z");
        near(
            1,
            CameraProjection.project(straight, projection, 10, 20, -370)
                .ndcZ(),
            "far z");
        for (int facing = 0; facing < 4; facing++) {
            CameraPose pose = new CameraPose(123, 70, -456, 45 + 90 * facing, 60);
            CameraRay ray = CameraProjection.unprojectRay(pose, projection, .4, -.3);
            double distance = CameraIntersections.plane(ray, 0, 1, 0, 64, 0, 500);
            check(Double.isFinite(distance), "four-direction ground hit");
            ProjectedPoint hit = CameraProjection.project(
                pose,
                projection,
                ray.originX() + distance * ray.directionX(),
                ray.originY() + distance * ray.directionY(),
                ray.originZ() + distance * ray.directionZ());
            near(.4, hit.ndcX(), "four-direction screen x");
            near(-.3, hit.ndcY(), "four-direction screen y");
            near(-1 + 2 * distance / (500 - .05), hit.ndcZ(), "ray distance matches projected depth");
            CameraRay another = CameraProjection.unprojectRay(pose, projection, -.8, .7);
            near(ray.directionX(), another.directionX(), "orthographic parallel x");
            near(ray.directionY(), another.directionY(), "orthographic parallel y");
            near(ray.directionZ(), another.directionZ(), "orthographic parallel z");
        }
        OrthographicProjection lowZoom = OrthographicProjection.scaled(854, 480, 16 * .125, 200);
        near(213.5, lowZoom.halfWidth(), "low zoom half width");
        near(120, lowZoom.halfHeight(), "low zoom half height");
        near(384, lowZoom.depthOffset(), "low zoom dynamic near protection");
        near(1016, lowZoom.far(), "low zoom dynamic far");
        expect(() -> OrthographicProjection.scaled(854, 480, .01, 200));
    }

    private static void intersections() {
        CameraRay ray = new CameraRay(-2, 0, .5, 2, 0, 0);
        near(2, CameraIntersections.aabb(ray, 0, 0, 0, 1, 1, 1, 0, 10), "parallel on boundary");
        near(2, CameraIntersections.aabb(ray, 0, 0, 0, 1, 1, 1, 2, 2), "inclusive exact interval");
        check(Double.isNaN(CameraIntersections.aabb(ray, 0, 0, 0, 1, 1, 1, 0, 1.9)), "beyond range");
        CameraRay parallelOutside = new CameraRay(-2, -1, .5, 1, 0, 0);
        check(Double.isNaN(CameraIntersections.aabb(parallelOutside, 0, 0, 0, 1, 1, 1, 0, 10)), "parallel outside");
        CameraRay inside = new CameraRay(.5, .5, .5, 1, 0, 0);
        near(0, CameraIntersections.aabb(inside, 0, 0, 0, 1, 1, 1, 0, 10), "inside starts immediately");
        near(.2, CameraIntersections.aabb(inside, 0, 0, 0, 1, 1, 1, .2, 10), "inside at lower bound");
        CameraRay outward = new CameraRay(1, .5, .5, 1, 0, 0);
        near(0, CameraIntersections.aabb(outward, 0, 0, 0, 1, 1, 1, 0, 10), "outward surface origin");
        check(Double.isNaN(CameraIntersections.aabb(outward, 0, 0, 0, 1, 1, 1, .01, 10)), "outward past surface");
        near(3, CameraIntersections.plane(ray, 2, 0, 0, 2, 0, 10), "unnormalized plane normal");
        near(.2, CameraIntersections.plane(ray, 0, 1, 0, 0, .2, 10), "coplanar ray");
        check(Double.isNaN(CameraIntersections.plane(ray, 0, 1, 0, 1, 0, 10)), "disjoint parallel plane");
        CameraRay huge = new CameraRay(0, 0, 0, Double.MAX_VALUE, Double.MAX_VALUE, 0);
        near(Math.sqrt(.5), huge.directionX(), "overflow-safe normalization");
        CameraRay tiny = new CameraRay(0, 0, 0, Double.MIN_VALUE, 0, 0);
        near(1, tiny.directionX(), "subnormal normalization");
    }

    private static void expandedZoom() {
        OrthographicProjection previous = OrthographicProjection.scaled(854, 480, 16 * .125, 512);
        OrthographicProjection expanded = OrthographicProjection.scaled(854, 480, 16 * .025, 512);
        near(previous.halfWidth() * 5, expanded.halfWidth(), "five times the visible width");
        near(previous.halfHeight() * 5, expanded.halfHeight(), "five times the visible height");
        near(1920, expanded.depthOffset(), "expanded near protection");
        near(4280, expanded.far(), "expanded dynamic far");
        OrthographicProjection highResolution = OrthographicProjection.scaled(7680, 4320, 16 * .025, 512);
        near(17280, highResolution.depthOffset(), "8K minimum zoom depth");
        near(36920, highResolution.far(), "8K minimum zoom far");
        OrthographicProjection capped = OrthographicProjection.scaled(7680, 6000, 16 * .025, 512);
        near(40000, capped.far(), "expanded finite far cap");
        expect(() -> OrthographicProjection.scaled(7680, 10000, 16 * .025, 512));
        for (double pitch : new double[] { 15, 60, 85 }) {
            for (int facing = 0; facing < 4; facing++) {
                CameraBasis basis = new CameraBasis(new CameraPose(123, 70, -456, 45 + 90 * facing, pitch));
                for (OrthographicProjection view : new OrthographicProjection[] { expanded, highResolution, capped }) {
                    ProjectedPoint anchor = basis.project(view, 123, 70, -456);
                    check(anchor.ndcZ() > -1 && anchor.ndcZ() < 1, "anchor stays inside clipping interval");
                    CameraRay ray = basis.ray(view, .4, -.3);
                    double distance = CameraIntersections.plane(ray, 0, 1, 0, 70, 0, view.far() - view.near());
                    check(Double.isFinite(distance), "expanded view ground hit");
                    ProjectedPoint hit = basis.project(
                        view,
                        ray.originX() + distance * ray.directionX(),
                        ray.originY() + distance * ray.directionY(),
                        ray.originZ() + distance * ray.directionZ());
                    near(.4, hit.ndcX(), "expanded ray screen X");
                    near(-.3, hit.ndcY(), "expanded ray screen Y");
                }
                double oldPan = 2 * previous.halfWidth() / 854;
                double newPan = 2 * expanded.halfWidth() / 854;
                near(5 * basis.screenDeltaX(oldPan, oldPan), basis.screenDeltaX(newPan, newPan), "scaled pan X");
                near(5 * basis.screenDeltaY(oldPan, oldPan), basis.screenDeltaY(newPan, newPan), "scaled pan Y");
                near(5 * basis.screenDeltaZ(oldPan, oldPan), basis.screenDeltaZ(newPan, newPan), "scaled pan Z");
            }
        }
        ZoomMotion zoom = new ZoomMotion(1, .025, 8);
        zoom.multiply(.001, .5);
        near(.025, zoom.target(), "expanded minimum clamps target");
        for (int i = 0; i < 200; i++) zoom.advance(.25, .85);
        near(.025, zoom.actual(), "expanded minimum settles exactly");
        zoom.snap(100);
        near(8, zoom.actual(), "maximum zoom remains unchanged");
    }

    private static void invalidNumbers() {
        expect(() -> new CameraPose(Double.NaN, 0, 0, 0, 0));
        expect(() -> new CameraPose(0, 0, 0, Double.POSITIVE_INFINITY, 0));
        expect(() -> new CameraRay(0, 0, 0, 0, 0, 0));
        expect(() -> new CameraRay(0, 0, Double.NEGATIVE_INFINITY, 1, 0, 0));
        expect(() -> new OrthographicProjection(0, 1, 1, .05, 10));
        expect(() -> new OrthographicProjection(1, 1, 1, 10, 10));
        expect(() -> OrthographicProjection.scaled(854, 480, Double.NaN, 200));
        expect(() -> OrthographicProjection.scaled(854, 480, 16, Double.POSITIVE_INFINITY));
        expect(() -> new ProjectedPoint(0, 0, Double.NaN));
        CameraRay ray = new CameraRay(0, 0, 0, 1, 0, 0);
        expect(() -> CameraIntersections.plane(ray, 0, 0, 0, 0, 0, 10));
        expect(() -> CameraIntersections.plane(ray, 1, 0, 0, 0, 0, Double.POSITIVE_INFINITY));
        expect(() -> CameraIntersections.aabb(ray, 1, 0, 0, 0, 1, 1, 0, 10));
        expect(() -> CameraIntersections.aabb(ray, 0, 0, 0, 1, 1, 1, -1, 10));
        expect(() -> new ZoomMotion(1, 0, 8));
        expect(() -> new ZoomMotion(Double.NaN, .125, 8));
        ZoomMotion zoom = new ZoomMotion(1, .125, 8);
        expect(() -> zoom.multiply(Double.NaN, 0));
        expect(() -> zoom.multiply(1.1, -1));
        expect(() -> zoom.advance(1.1, .85));
        expect(() -> zoom.advance(.2, Double.NaN));
        AngleTransition angle = new AngleTransition(0);
        expect(() -> angle.transitionTo(Double.NaN, 0, 1));
        expect(() -> angle.transitionTo(90, 0, -1));
    }

    private static void motion() {
        ZoomMotion zoom = new ZoomMotion(1, .125, 8);
        zoom.multiply(1.1, .5);
        near(1.1, zoom.target(), "wheel target");
        near(1, zoom.actual(), "wheel leaves actual untouched");
        zoom.advance(.2, .85);
        near(1.0285, zoom.actual(), "decay before goal and interpolation");
        zoom.advance(1, .85);
        near(1.1, zoom.actual(), "smoothing 1 direct value");
        zoom.snap(1);
        zoom.multiply(2, 0);
        zoom.advance(0, .85);
        near(1.05, zoom.actual(), "zero smoothing alpha floor");
        for (int i = 0; i < 200; i++) zoom.advance(.2, .85);
        check(zoom.actual() == 2, "exact settled target");
        zoom.bounds(.5, 1.5);
        near(1.5, zoom.actual(), "bounds clamp actual");
        near(1.5, zoom.target(), "bounds clamp target");
        ZoomMotion liveBounds = new ZoomMotion(1, .125, 8);
        liveBounds.multiply(1.1, .5);
        liveBounds.boundsPreservingActual(1.2, 8);
        near(1, liveBounds.actual(), "live bounds preserve actual");
        near(1.2, liveBounds.target(), "live bounds clamp target");
        liveBounds.advance(.2, .85);
        near(1.0485, liveBounds.actual(), "live bounds preserve inertia and smooth into new range");
        AngleTransition angle = new AngleTransition(350);
        long start = Long.MAX_VALUE - 20;
        angle.transitionTo(10, start, 100);
        near(352.5, angle.sample(start + 25), "quarter quadratic ease with clock wrap");
        near(360, angle.sample(start + 50), "shortest angle across 360");
        near(367.5, angle.sample(start + 75), "three-quarter quadratic ease");
        near(10, angle.sample(start + 100), "exact destination");
        angle.transitionTo(100, 1000, 100);
        near(55, angle.sample(1050), "midpoint");
        angle.transitionTo(145, 1050, 100);
        near(55, angle.sample(1050), "restart continuity");
        near(100, angle.sample(1100), "restarted midpoint");
        angle.snap(0);
        angle.transitionTo(-180, 0, 100);
        near(90, angle.sample(50), "positive half-turn tie");
        angle.transitionTo(45, 50, 0);
        near(45, angle.sample(50), "zero duration snaps");
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
