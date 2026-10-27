package com.miaokatze.miaokatzecore.api.miaovision;

/** Pure deterministic display-physics regressions; never creates a Minecraft world or GL context. */
public final class OrientedSceneContracts {

    public static void main(String[] args) {
        SceneBounds bounds = new SceneBounds(-5, 0, -3, 5, 8, 3);
        SceneSimulation scene = new SceneSimulation("mixed", bounds, SceneSimulation.ContactModel.ORIENTED_BOX);
        for (int i = 0; i < 100; i++) {
            double h = i % 4 == 0 ? .22 : .027;
            double angle = .3 + (i % 9) * .1;
            scene.put(
                new SceneBody("body" + i, .22, .22, h, (i % 10 - 4.5) * .65, 1 + (i / 10) * .56, (i % 3 - 1) * .65)
                    .withRotation(Math.sin(angle / 2), 0, 0, Math.cos(angle / 2))
                    .withVelocity((i % 5 - 2) * .3, 0, (i % 7 - 3) * .25, 1.1, .3, .7));
        }
        require(!scene.isEnabled(), "construct/put stays off");
        require(!scene.step(), "disabled performs no step");
        scene.setEnabled(true);
        for (int i = 0; i < 60; i++) scene.step();
        SceneSnapshot save = scene.snapshot();
        SceneSimulation restored = new SceneSimulation("mixed", bounds, SceneSimulation.ContactModel.ORIENTED_BOX);
        restored.restore(save);
        require(!restored.isEnabled() && !restored.step(), "restore never activates");
        restored.setEnabled(true);
        for (int i = 0; i < 1800; i++) {
            scene.step();
            restored.step();
            SceneSnapshot a = scene.snapshot(), b = restored.snapshot();
            require(a.tick == b.tick && a.revision == b.revision, "resume clock");
            for (int j = 0; j < a.bodies.size(); j++) {
                SceneBody x = a.bodies.get(j), y = b.bodies.get(j);
                bounds.validate(x);
                require(
                    x.x == y.x && x.y == y.y
                        && x.z == y.z
                        && x.qx == y.qx
                        && x.qy == y.qy
                        && x.qz == y.qz
                        && x.qw == y.qw
                        && x.quietTicks == y.quietTicks,
                    "exact deterministic save/resume");
            }
            if (scene.isSettled()) break;
        }
        int asleep = 0;
        double maxY = 0;
        int flat = 0;
        for (SceneBody body : scene.snapshot().bodies) {
            if (body.sleeping) asleep++;
            maxY = Math.max(maxY, body.y + body.extentY());
            if (body.halfZ < .1 && body.extentY() < .08) flat++;
        }
        System.out
            .println("mixed bodies asleep=" + asleep + " flat=" + flat + " top=" + maxY + " tick=" + scene.tick());
        java.util.List<SceneBody> pile = scene.snapshot().bodies;
        for (int i = 0; i < pile.size(); i++) for (int j = i + 1; j < pile.size(); j++)
            require(separated(pile.get(i), pile.get(j)), "settled oriented shapes do not intersect");
        require(scene.size() == 100, "all identities preserved");
        require(scene.isSettled(), "100 mixed shapes settle");
        require(flat >= 65, "thin sprites rest on broad face, not upright columns");
        require(maxY < 3, "pile not artificial tall columns");
        long tick = scene.tick(), revision = scene.revision();
        for (int i = 0; i < 1000; i++) require(!scene.step(), "settled zero work");
        require(scene.tick() == tick && scene.revision() == revision, "idle no clocks/dirty frames");
        SceneSimulation stream = new SceneSimulation("stream", bounds, SceneSimulation.ContactModel.ORIENTED_BOX);
        stream.setEnabled(true);
        for (int i = 0; i < 100; i++) {
            double spread = ((i * 37 % 17) - 8) / 8.0;
            stream.put(
                new SceneBody("drop" + i, .27, .27, i % 4 == 0 ? .27 : .027, 3.1, 3, .5)
                    .withRotation(.2, 0, 0, Math.sqrt(.96))
                    .withVelocity(-(1.8 + i % 5 * .18), -.15, spread * 1.6, 1.1, spread, .8));
            for (int j = 0; j < 4; j++) stream.step();
        }
        for (int i = 0; i < 1800 && !stream.isSettled(); i++) stream.step();
        require(stream.isSettled(), "single-outlet 100-drop stream settles");
        double top = 0, minX = 5, maxX = -5;
        for (SceneBody body : stream.snapshot().bodies) {
            bounds.validate(body);
            top = Math.max(top, body.y + body.extentY());
            minX = Math.min(minX, body.x);
            maxX = Math.max(maxX, body.x);
        }
        require(top < 3 && maxX - minX > 1, "outlet pile spreads instead of vertical tower");
        System.out.println("outlet stream top=" + top + " spanX=" + (maxX - minX) + " tick=" + stream.tick());
        System.out.println("OrientedScene contracts PASS");
    }

    private static double[][] basis(SceneBody b) {
        double x = b.qx, y = b.qy, z = b.qz, w = b.qw;
        return new double[][] { { 1 - 2 * (y * y + z * z), 2 * (x * y + z * w), 2 * (x * z - y * w) },
            { 2 * (x * y - z * w), 1 - 2 * (x * x + z * z), 2 * (y * z + x * w) },
            { 2 * (x * z + y * w), 2 * (y * z - x * w), 1 - 2 * (x * x + y * y) } };
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static boolean separated(SceneBody a, SceneBody b) {
        double[][] aa = basis(a), bb = basis(b);
        double[] delta = { b.x - a.x, b.y - a.y, b.z - a.z }, ah = { a.halfX, a.halfY, a.halfZ },
            bh = { b.halfX, b.halfY, b.halfZ };
        for (int axis = 0; axis < 15; axis++) {
            double[] n;
            if (axis < 6) n = axis < 3 ? aa[axis] : bb[axis - 3];
            else {
                double[] u = aa[(axis - 6) / 3], v = bb[(axis - 6) % 3];
                n = new double[] { u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0] };
            }
            double length = Math.sqrt(dot(n, n));
            if (length < .00001) continue;
            double r = 0;
            for (int i = 0; i < 3; i++) r += ah[i] * Math.abs(dot(aa[i], n)) + bh[i] * Math.abs(dot(bb[i], n));
            if (r - Math.abs(dot(delta, n)) < .002 * length) return true;
        }
        return false;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
