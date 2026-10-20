package com.miaokatze.miaokatzecore;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.miaokatze.miaokatzecore.api.extension.ExtensionCatalog;
import com.miaokatze.miaokatzecore.api.itemfx.GlintStyle;
import com.miaokatze.miaokatzecore.testkit.Deadline;

/** Pure JVM contracts: no game world, GPU, sleeping or class stubs. */
public final class CoreContracts {

    public static void main(String[] args) {
        check(
            Deadline.startingAt(100L, 5L)
                .hasTimeRemaining(104L),
            "before boundary");
        check(
            Deadline.startingAt(100L, 5L)
                .isExpired(105L),
            "exact boundary");
        check(
            Deadline.startingAt(-100L, 5L)
                .isExpired(-95L),
            "negative clock");
        Deadline wrap = Deadline.startingAt(Long.MAX_VALUE - 2L, 5L);
        check(wrap.hasTimeRemaining(Long.MIN_VALUE + 1L), "wrapped before boundary");
        check(wrap.isExpired(Long.MIN_VALUE + 2L), "wrapped exact boundary");
        check(
            Deadline.startingAt(0L, 0L)
                .isExpired(0L),
            "zero budget");
        check(
            Deadline.startingAt(0L, -1L)
                .isExpired(0L),
            "negative budget");
        check(
            Deadline.startingAt(0L, Long.MAX_VALUE)
                .hasTimeRemaining(Long.MAX_VALUE - 1L),
            "large budget");
        testCatalog();
        testGlint();
        System.out.println("CoreContracts: PASS (deadline, catalog, glint)");
    }

    private static void testCatalog() {
        ExtensionCatalog<String> c = new ExtensionCatalog<>();
        AtomicInteger installed = new AtomicInteger(), removed = new AtomicInteger();
        expect(IllegalArgumentException.class, () -> c.install("invalid", "x", () -> {
            installed.incrementAndGet();
            return () -> {};
        }));
        check(
            installed.get() == 0 && c.snapshot()
                .isEmpty(),
            "invalid ID side effects");
        expect(
            IllegalStateException.class,
            () -> c.install("test:failure", "x", () -> { throw new IllegalStateException(); }));
        ExtensionCatalog.Handle<String> first = c
            .install("test:failure", "first", () -> () -> removed.incrementAndGet());
        List<String> snapshot = c.snapshot();
        expect(UnsupportedOperationException.class, () -> snapshot.add("x"));
        expect(IllegalArgumentException.class, () -> c.install("test:failure", "x", () -> {
            installed.incrementAndGet();
            return () -> {};
        }));
        check(installed.get() == 0, "duplicate installer must not run");
        first.close();
        ExtensionCatalog.Handle<String> replacement = c.install("test:failure", "replacement", () -> () -> {});
        first.close();
        check(
            removed.get() == 1 && !first.active() && replacement.active() && snapshot.equals(Arrays.asList("first")),
            "old handle and immutable snapshot");
        replacement.close();
        AtomicReference<ExtensionCatalog.Handle<String>> self = new AtomicReference<>();
        AtomicInteger attempts = new AtomicInteger();
        self.set(c.install("test:self", "self", () -> {
            expect(IllegalArgumentException.class, () -> c.install("test:self", "nested", () -> () -> {}));
            return () -> {
                self.get()
                    .close();
                if (attempts.incrementAndGet() == 1) throw new IllegalStateException();
            };
        }));
        expect(
            IllegalStateException.class,
            () -> self.get()
                .close());
        check(
            self.get()
                .active()
                && c.snapshot()
                    .equals(Arrays.asList("self")),
            "failed removal stays active");
        self.get()
            .close();
        self.get()
            .close();
        check(
            attempts.get() == 2 && c.snapshot()
                .isEmpty(),
            "retry and reentrant close");
        ExtensionCatalog<String> other = new ExtensionCatalog<>();
        c.install("test:shared", "outer", () -> {
            c.install("test:inner", "inner", () -> () -> {});
            return () -> {};
        });
        other.install("test:shared", "other", () -> () -> {});
        check(
            c.snapshot()
                .equals(Arrays.asList("inner", "outer"))
                && other.snapshot()
                    .equals(Arrays.asList("other")),
            "completion order and instance isolation");
    }

    private static void testGlint() {
        double[] speed = { .12, -.08 };
        float[] rotation = { -50, 10 };
        GlintStyle style = new GlintStyle(.9F, .63F, .12F, 1, .8F, speed, rotation);
        speed[0] = 99;
        rotation[0] = 99;
        style.getScrollSpeeds()[0] = 77;
        style.getRotationDegrees()[0] = 77;
        check(style.getScrollSpeed(0) == .12 && style.getRotationDegrees(0) == -50, "glint defensive copies");
        expect(
            IllegalArgumentException.class,
            () -> new GlintStyle(Float.NaN, 0, 0, 1, 1, new double[] { 0, 0 }, new float[] { 0, 0 }));
        expect(
            IllegalArgumentException.class,
            () -> new GlintStyle(0, 0, 0, 1, 0, new double[] { 0, 0 }, new float[] { 0, 0 }));
        expect(
            IllegalArgumentException.class,
            () -> new GlintStyle(0, 0, 0, 1, 1, new double[] { Double.POSITIVE_INFINITY, 0 }, new float[] { 0, 0 }));
        expect(
            IllegalArgumentException.class,
            () -> new GlintStyle(0, 0, 0, 1, 1, new double[] { 0 }, new float[] { 0, 0 }));
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable e) {
            if (type.isInstance(e)) return;
            throw new AssertionError("Unexpected exception", e);
        }
        throw new AssertionError("Expected " + type.getName());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
