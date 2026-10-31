package com.miaokatze.miaokatzecore.api.animation;

import java.util.concurrent.atomic.AtomicReference;

import com.miaokatze.miaokatzecore.api.animation.runtime.QualityGovernor;

/** Production indexed policy contracts; frame filtering and effect ratios remain host responsibilities. */
public final class QualityDemandContracts {

    private static int assertions;

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("QualityDemandContracts PASS (" + assertions + " assertions)");
    }

    public static void run() throws Exception {
        QualityGovernor.Indexed policy = new QualityGovernor.Indexed(0, 5, .25, 3);
        check(policy.observeDemand(4, false, .125) == 0, "pressure duration first half");
        check(policy.observeDemand(4, false, .125) == 1, "exact down boundary, one step");
        check(policy.observeDemand(4, true, 10) == 2, "pressure wins recovery and no multi-step catch-up");
        check(policy.observeDemand(0, true, 2.75) == 2, "recovery duration");
        check(policy.observeDemand(0, false, .25) == 2, "closed recovery gate discards duration");
        check(policy.observeDemand(0, true, 2.75) == 2, "fresh recovery interval");
        check(policy.observeDemand(0, true, .25) == 1, "exact up boundary");
        check(policy.observeDemand(0, true, 100) == 0, "one recovery step");
        check(policy.observeDemand(0, true, 100) == 0, "highest tier is bounded");
        policy.resetTier(4);
        check(policy.observeDemand(4, false, 100) == 4, "lowest tier is bounded");
        policy.resetTier(2);
        policy.observeDemand(4, false, .2);
        policy.resetTier(2);
        check(policy.observeDemand(4, false, .1) == 2, "reset discards pressure");
        policy.setLocked(true);
        policy.resetTier(0);
        check(policy.tierIndex() == 2, "host reset cannot change locked tier");
        check(policy.observeDemand(4, false, 100) == 2, "locked pressure ignored");
        check(policy.observeDemand(0, true, 100) == 2, "locked recovery ignored");
        policy.setLocked(false);
        check(policy.observeDemand(4, false, .1) == 2, "unlock discards prior pressure");
        check(policy.observeDemand(4, false, .15) == 3, "normal pressure resumes");
        policy.observeDemand(0, true, 2.9);
        policy.setLocked(true);
        policy.setLocked(false);
        check(policy.observeDemand(0, true, .2) == 3, "lock also discards recovery");
        check(policy.observeDemand(3, false, 0) == 3, "zero elapsed holds");
        QualityGovernor.Indexed single = new QualityGovernor.Indexed(0, 1, .25, 3);
        check(single.observeDemand(0, true, Double.MAX_VALUE) == 0, "one tier is stable");

        expect(IllegalArgumentException.class, () -> new QualityGovernor.Indexed(0, 0, .25, 3));
        expect(IllegalArgumentException.class, () -> new QualityGovernor.Indexed(-1, 5, .25, 3));
        expect(IllegalArgumentException.class, () -> new QualityGovernor.Indexed(5, 5, .25, 3));
        for (double invalid : new double[] { -1, 0, Double.NaN, Double.POSITIVE_INFINITY }) {
            expect(IllegalArgumentException.class, () -> new QualityGovernor.Indexed(0, 5, invalid, 3));
            expect(IllegalArgumentException.class, () -> new QualityGovernor.Indexed(0, 5, .25, invalid));
        }
        for (double invalid : new double[] { -1, Double.NaN, Double.POSITIVE_INFINITY })
            expect(IllegalArgumentException.class, () -> policy.observeDemand(0, true, invalid));
        expect(IllegalArgumentException.class, () -> policy.observeDemand(-1, false, .1));
        expect(IllegalArgumentException.class, () -> policy.observeDemand(5, false, .1));
        expect(IllegalArgumentException.class, () -> policy.resetTier(5));
        check(policy.tierIndex() == 3, "invalid calls preserve state");
        foreign(policy::tierIndex);
        foreign(() -> policy.observeDemand(4, false, 1));
        foreign(() -> policy.resetTier(0));
        foreign(() -> policy.setLocked(true));
        check(policy.tierIndex() == 3, "foreign calls preserve state");
    }

    private static void foreign(Runnable action) throws Exception {
        AtomicReference<Throwable> result = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                action.run();
            } catch (Throwable failure) {
                result.set(failure);
            }
        }, "quality-contract-foreign-owner");
        thread.start();
        thread.join(5000);
        check(!thread.isAlive() && result.get() instanceof IllegalStateException, "foreign thread refused");
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable failure) {
            check(type.isInstance(failure), "expected " + type.getName() + ", got " + failure);
            return;
        }
        throw new AssertionError("expected " + type.getName());
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
