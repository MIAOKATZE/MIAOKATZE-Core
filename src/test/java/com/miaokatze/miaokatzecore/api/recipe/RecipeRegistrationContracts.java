package com.miaokatze.miaokatzecore.api.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import cpw.mods.fml.common.eventhandler.EventBus;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;

/** No game instance: exercise the engine and adapter with actual FML tick event values. */
public final class RecipeRegistrationContracts {

    public static void main(String[] args) {
        orderAndOnce();
        invalidAndLate();
        failure("recipes");
        failure("derived");
        failure("finalize");
        empty();
        physicalSide(Side.CLIENT);
        physicalSide(Side.SERVER);
        failedAdapterUnregisters();
        publicApi();
        System.out.println("RecipeRegistrationContracts: PASS (order, once, failure, empty, FML tick sides, API)");
    }

    private static void orderAndOnce() {
        RecipeRegistrationEngine engine = new RecipeRegistrationEngine();
        List<String> calls = new ArrayList<>();
        engine.register("first", () -> calls.add("first-main"), () -> calls.add("first-derived"));
        List<String> snapshot = engine.registeredIds();
        engine.register("second", () -> calls.add("second-main"), null);
        engine.register("third", () -> calls.add("third-main"), () -> calls.add("third-derived"));
        check(calls.isEmpty() && !engine.isComplete(), "registration must not execute callbacks");
        check(snapshot.equals(Arrays.asList("first")), "diagnostic snapshot is detached");
        expect(UnsupportedOperationException.class, () -> snapshot.add("other"));
        Runnable finish = () -> calls.add("finish");
        engine.run(finish);
        engine.run(finish); // Includes subsequent worlds: there is no world lifecycle reset.
        check(
            calls.equals(
                Arrays.asList("first-main", "second-main", "third-main", "first-derived", "third-derived", "finish")),
            "all main callbacks precede all derived callbacks and one finalizer");
        check(engine.isComplete(), "success is complete");
        check(
            engine.registeredIds()
                .equals(Arrays.asList("first", "second", "third")),
            "IDs survive completion");
    }

    private static void invalidAndLate() {
        RecipeRegistrationEngine engine = new RecipeRegistrationEngine();
        expect(IllegalArgumentException.class, () -> engine.register("", () -> {}, null));
        expect(NullPointerException.class, () -> engine.register("null_main", null, null));
        engine.register("same", () -> {
            IllegalStateException late = expect(
                IllegalStateException.class,
                () -> engine.register("inside", () -> {}, null));
            check(
                late.getMessage()
                    .contains("RUNNING"),
                "execution seals submissions before callbacks");
        }, null);
        expect(IllegalArgumentException.class, () -> engine.register("same", () -> {}, null));
        engine.run(() -> {});
        expect(IllegalStateException.class, () -> engine.register("after", () -> {}, null));
    }

    private static void failure(String phase) {
        RecipeRegistrationEngine engine = new RecipeRegistrationEngine();
        List<String> calls = new ArrayList<>();
        RuntimeException cause = new RuntimeException("fixture");
        engine.register("broken", () -> {
            calls.add("main");
            if (phase.equals("recipes")) throw cause;
        }, () -> {
            calls.add("derived");
            if (phase.equals("derived")) throw cause;
        });
        engine.register("following", () -> calls.add("following-main"), () -> calls.add("following-derived"));
        IllegalStateException error = expect(IllegalStateException.class, () -> engine.run(() -> {
            calls.add("finish");
            throw cause;
        }));
        check(
            error.getMessage()
                .contains("phase=" + phase),
            "failure identifies phase");
        check(
            error.getMessage()
                .contains("id=" + (phase.equals("finalize") ? "miaokatzecore" : "broken")),
            "failure identifies ID");
        check(error.getCause() == cause && !engine.isComplete(), "original failure is preserved");
        List<String> atFailure = new ArrayList<>(calls);
        engine.run(() -> calls.add("retry"));
        check(calls.equals(atFailure), "failed pipeline never retries");
        check(!calls.contains("finish") || phase.equals("finalize"), "registration failure skips finalization");
        if (phase.equals("recipes")) check(calls.equals(Arrays.asList("main")), "main failure stops remaining work");
        if (phase.equals("derived")) check(
            calls.equals(Arrays.asList("main", "following-main", "derived")),
            "derived failure stops remaining work");
        IllegalStateException closed = expect(
            IllegalStateException.class,
            () -> engine.register("late", () -> {}, null));
        check(
            closed.getMessage()
                .contains("FAILED"),
            "failure state is sticky");
    }

    private static void empty() {
        RecipeRegistrationEngine engine = new RecipeRegistrationEngine();
        AtomicInteger finishes = new AtomicInteger();
        engine.run(finishes::incrementAndGet);
        engine.run(finishes::incrementAndGet);
        check(engine.isComplete() && finishes.get() == 0, "empty pipeline seals without touching caches");
    }

    private static void physicalSide(Side side) {
        RecipeRegistrationEngine engine = new RecipeRegistrationEngine();
        CountingBus bus = new CountingBus();
        AtomicInteger runs = new AtomicInteger(), finishes = new AtomicInteger();
        engine.register("consumer", runs::incrementAndGet, null);
        RecipeRegistrationLifecycle adapter = new RecipeRegistrationLifecycle(
            engine,
            side,
            bus,
            finishes::incrementAndGet);
        adapter.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.START));
        adapter.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.START));
        if (side == Side.CLIENT) adapter.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        else adapter.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
        check(runs.get() == 0 && bus.removals == 0, "wrong phase and physical side cannot execute or detach");
        // Simulates a later loadComplete handler submitting after the listener was prepared.
        engine.register("later", runs::incrementAndGet, null);
        if (side == Side.CLIENT) adapter.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
        else adapter.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        check(runs.get() == 2 && finishes.get() == 1 && bus.removals == 1, "legal tick executes and unregisters");
        adapter.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
        adapter.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        check(runs.get() == 2 && finishes.get() == 1, "later ticks and integrated server cannot replay");

        CountingBus emptyBus = new CountingBus();
        RecipeRegistrationLifecycle empty = new RecipeRegistrationLifecycle(
            new RecipeRegistrationEngine(),
            side,
            emptyBus,
            finishes::incrementAndGet);
        if (side == Side.CLIENT) empty.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
        else empty.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        check(emptyBus.removals == 1 && finishes.get() == 1, "empty listener detaches without finalization");
    }

    private static void failedAdapterUnregisters() {
        RecipeRegistrationEngine engine = new RecipeRegistrationEngine();
        CountingBus bus = new CountingBus();
        engine.register("broken", () -> { throw new IllegalArgumentException("fixture"); }, null);
        RecipeRegistrationLifecycle adapter = new RecipeRegistrationLifecycle(engine, Side.CLIENT, bus, () -> {});
        expect(
            IllegalStateException.class,
            () -> adapter.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.END)));
        check(bus.removals == 1 && !engine.isComplete(), "failed adapter detaches");
    }

    private static void publicApi() {
        AtomicInteger called = new AtomicInteger();
        RecipeRegistration.register("two_args", () -> {
            called.incrementAndGet();
            diagnosticsDuringCallback();
        });
        RecipeRegistration.register("three_args", called::incrementAndGet, null);
        check(called.get() == 0 && !RecipeRegistration.isComplete(), "public overloads only enqueue");
        check(
            RecipeRegistration.registeredIds()
                .equals(Arrays.asList("two_args", "three_args")),
            "public diagnostic order");
        RecipeRegistration.ENGINE.run(() -> {});
        check(called.get() == 2 && RecipeRegistration.isComplete(), "public completion follows engine");
    }

    private static void diagnosticsDuringCallback() {
        FutureTask<Boolean> diagnostics = new FutureTask<>(
            () -> RecipeRegistration.registeredIds()
                .equals(Arrays.asList("two_args", "three_args")) && !RecipeRegistration.isComplete());
        new Thread(diagnostics, "recipe-registration-diagnostics").start();
        try {
            check(diagnostics.get(2, TimeUnit.SECONDS), "worker reads public diagnostics while callback waits");
        } catch (InterruptedException interrupted) {
            Thread.currentThread()
                .interrupt();
            throw new AssertionError("Diagnostic wait interrupted", interrupted);
        } catch (ExecutionException | TimeoutException failure) {
            throw new AssertionError("Public diagnostics must not wait for the callback's engine monitor", failure);
        }
    }

    private static final class CountingBus extends EventBus {

        private int removals;

        @Override
        public void unregister(Object listener) {
            removals++;
            super.unregister(listener);
        }
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }

    private static <T extends Throwable> T expect(Class<T> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) return type.cast(failure);
            throw new AssertionError("Expected " + type.getName(), failure);
        }
        throw new AssertionError("Expected " + type.getName());
    }
}
