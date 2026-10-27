package com.miaokatze.miaokatzecore.api.miaovision;

import java.util.Arrays;
import java.util.Collections;

/** Pure JVM production contracts; does not claim Minecraft/OpenGL runtime verification. */
public final class MiaoVisionContracts {

    private static int checks;

    public static void main(String[] args) {
        SceneBounds bounds = new SceneBounds(-10, 0, -10, 10, 20, 10);
        SceneSimulation a = new SceneSimulation("room", bounds);
        check(!a.isEnabled() && a.isSettled(), "default off");
        SceneBody falling = new SceneBody("one", .5, .5, .5, 0, 5, 0).withVelocity(1, 0, 0, 0, .4, 0);
        a.put(falling);
        check(!a.isEnabled(), "put cannot enable");
        SceneSnapshot before = a.snapshot();
        for (int i = 0; i < 100; i++) check(!a.step(), "disabled no step");
        check(equal(before, a.snapshot()), "disabled exact pose/revision");
        a.setEnabled(true);
        a.step();
        check(a.tick() == 1, "one caller tick");
        a.setEnabled(false);
        before = a.snapshot();
        check(!a.step() && equal(before, a.snapshot()), "disable freezes");
        a.setEnabled(true);
        a.setEnabled(true);
        a.step();
        a.setEnabled(false);
        a.setEnabled(false);
        SceneSnapshot saved = a.snapshot();
        SceneSimulation b = new SceneSimulation("room", bounds);
        b.restore(saved);
        check(!b.isEnabled(), "restore never enables");
        check(equal(saved, b.snapshot()), "exact restore");
        a.setEnabled(true);
        b.setEnabled(true);
        for (int i = 0; i < 400; i++) {
            a.step();
            b.step();
            check(equal(a.snapshot(), b.snapshot()), "deterministic restored trajectory");
        }
        check(a.isSettled(), "falls and settles");
        before = a.snapshot();
        check(!a.step() && equal(before, a.snapshot()), "settled zero steps");
        a.setEnabled(true);
        a.restore(saved);
        check(!a.isEnabled(), "restore disables already active");
        before = a.snapshot();
        final SceneSimulation same = a;
        rejects(
            () -> same.restore(new SceneSnapshot("wrong", 0, 0, Collections.<SceneBody>emptyList())),
            "wrong layout");
        rejects(
            () -> same
                .restore(new SceneSnapshot("room", 0, 0, Arrays.asList(new SceneBody("bad", .5, .5, .5, 100, 5, 0)))),
            "off-room");
        check(equal(before, a.snapshot()), "rejected restore atomic");
        rejects(() -> new SceneBody("bad", .5, .5, .5, Double.NaN, 0, 0), "NaN");
        rejects(() -> falling.withRotation(0, 0, 0, 0), "invalid quaternion");
        rejects(() -> new SceneSnapshot("room", 0, 0, Arrays.asList(falling, falling)), "duplicate IDs");
        rejects(() -> new SceneSnapshot(2, "room", 0, 0, Arrays.asList(falling)), "schema");
        check(!saved.isNewerThan(saved), "equal revision rejected");
        SceneSimulation support = new SceneSimulation("room", bounds);
        SceneBody low = new SceneBody("low", .5, .5, .5, 0, .5, 0).withSleeping(true);
        SceneBody high = new SceneBody("high", .5, .5, .5, 0, 1.5, 0).withSleeping(true);
        support.restore(new SceneSnapshot("room", 0, 0, Arrays.asList(low, high)));
        support.remove("low");
        check(!support.isEnabled() && !support.isSettled(), "support removal wakes but not enable");
        support.setEnabled(true);
        for (int i = 0; i < 150; i++) support.step();
        check(support.snapshot().bodies.get(0).y < .501, "support removed collapses");
        check(support.isSettled(), "collapse settles");
        SceneSimulation stress = new SceneSimulation("room", bounds);
        for (int i = 0; i < 100; i++) stress
            .put(new SceneBody(String.format("%03d", i), .35, .35, .35, (i % 10 - 4.5) * .9, 2 + (i / 10) * .8, 0));
        rejects(() -> stress.put(new SceneBody("overflow", .3, .3, .3, 0, 15, 0)), "100 cap");
        stress.setEnabled(true);
        for (int i = 0; i < 600; i++) stress.step();
        for (SceneBody body : stress.snapshot().bodies) {
            bounds.validate(body);
            check(Double.isFinite(body.y), "100 finite");
        }
        check(stress.size() == 100, "stress keeps all identities");
        check(stress.isSettled(), "100-body pile sleeps");
        SceneSnapshot pile = stress.snapshot();
        for (SceneBody x : pile.bodies) for (SceneBody y : pile.bodies) if (!x.id.equals(y.id)) {
            double ox = x.extentX() + y.extentX() - Math.abs(x.x - y.x),
                oy = x.extentY() + y.extentY() - Math.abs(x.y - y.y),
                oz = x.extentZ() + y.extentZ() - Math.abs(x.z - y.z);
            check(Math.min(ox, Math.min(oy, oz)) < .00001, "100-body non-overlap");
        }
        SceneSimulation quiet = new SceneSimulation("room", bounds);
        quiet.put(new SceneBody("quiet", .5, .5, .5, 0, .5, 0));
        quiet.setEnabled(true);
        for (int i = 0; i < 7; i++) quiet.step();
        SceneSimulation resumed = new SceneSimulation("room", bounds);
        resumed.restore(quiet.snapshot());
        resumed.setEnabled(true);
        for (int i = 0; i < 30; i++) {
            quiet.step();
            resumed.step();
            check(equal(quiet.snapshot(), resumed.snapshot()), "sleep hysteresis exact resume");
        }
        SceneSimulation fast = new SceneSimulation("room", bounds);
        fast.put(new SceneBody("left", .1, .1, .1, -1, .1, 0).withVelocity(32, 0, 0, 0, 0, 0));
        fast.put(new SceneBody("right", .1, .1, .1, 1, .1, 0).withVelocity(-32, 0, 0, 0, 0, 0));
        fast.setEnabled(true);
        for (int i = 0; i < 100; i++) fast.step();
        check(fast.snapshot().bodies.get(0).x < fast.snapshot().bodies.get(1).x, "thin fast proxies do not tunnel");
        checks += com.miaokatze.miaokatzecore.client.miaovision.MiaoVisionModelContracts.run();
        System.out.println("MiaoVision contracts PASS: " + checks);
    }

    private static boolean equal(SceneSnapshot a, SceneSnapshot b) {
        if (a.revision != b.revision || a.tick != b.tick || a.bodies.size() != b.bodies.size()) return false;
        for (int i = 0; i < a.bodies.size(); i++) {
            SceneBody x = a.bodies.get(i), y = b.bodies.get(i);
            if (!x.id.equals(y.id) || x.x != y.x
                || x.y != y.y
                || x.z != y.z
                || x.qx != y.qx
                || x.qy != y.qy
                || x.qz != y.qz
                || x.qw != y.qw
                || x.vx != y.vx
                || x.vy != y.vy
                || x.vz != y.vz
                || x.wx != y.wx
                || x.wy != y.wy
                || x.wz != y.wz
                || x.sleeping != y.sleeping
                || x.quietTicks != y.quietTicks) return false;
        }
        return true;
    }

    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }

    private static void rejects(Runnable operation, String label) {
        boolean rejected = false;
        try {
            operation.run();
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        check(rejected, label);
    }
}
