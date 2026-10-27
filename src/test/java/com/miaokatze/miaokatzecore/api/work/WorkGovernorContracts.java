package com.miaokatze.miaokatzecore.api.work;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Deterministic admission, subscriber, epoch and permit lifecycle checks without game state. */
public final class WorkGovernorContracts {

    private static final java.util.concurrent.atomic.AtomicBoolean OWNER = new java.util.concurrent.atomic.AtomicBoolean();
    private static final WorkGovernor GOV = new WorkGovernor(1, 256L << 20, OWNER::get, null);

    private static final AtomicReference<WorkGovernor.Lease> RETIRED = new AtomicReference<>();
    private static final AtomicInteger REAPER_CALLS = new AtomicInteger(), REAPER_FAILURES = new AtomicInteger();
    private static volatile boolean reapFixture, failReaperFixture;
    private static final Runnable BROKEN_REAPER = () -> {
        if (failReaperFixture) {
            REAPER_FAILURES.incrementAndGet();
            throw new IllegalStateException("retired fixture");
        }
    };
    private static final Runnable RETIRED_REAPER = () -> {
        if (!reapFixture) return;
        check(!Thread.holdsLock(GOV), "retired callbacks run outside quota monitor");
        REAPER_CALLS.incrementAndGet();
        WorkGovernor.Lease nested = GOV.tryRetain("reaper-recursion", 0);
        check(nested != null, "reaper may close or account a lease without lock inversion");
        nested.close();
        WorkGovernor.Lease retired = RETIRED.getAndSet(null);
        if (retired != null) retired.close();
        reapFixture = false;
    };

    public static void run() throws Exception {
        byteAdmission();
        subscriberLifecycle();
        epochAndException();
        reentry();
        ownedProducerLifecycle();
        cooperativeCancellation();
        sharedOutputDispatch();
        fairWorkers();
        crossDomainRetiredReaper();
        independentAndProcessInstances();
        observerIsolation();
        twoPermitWorkers();
        interruptedWorker();
    }

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("WorkGovernorContracts passed");
    }

    private static void byteAdmission() throws Exception {
        long baseline = GOV.retainedBytes();
        WorkGovernor.Lease bytes = GOV.retain("test", 32);
        check(GOV.tryRetain("oversize", GOV.retainedByteLimit()) == null, "admission rejects before allocation");
        try {
            GOV.acquireHeavyCpu("hold-and-wait");
            throw new AssertionError("holding bytes must not wait for CPU");
        } catch (IllegalStateException expected) {}
        bytes.transfer();
        try (WorkGovernor.Lease ignored = GOV.acquireHeavyCpu("transferred")) {
            check(GOV.retainedBytes() == baseline + 32, "transfer preserves byte accounting");
        }
        bytes.close();
        bytes.close();
        check(GOV.retainedBytes() == baseline, "cross-owner close is exactly once");
        OWNER.set(true);
        try {
            GOV.acquireHeavyCpu("owner");
            throw new AssertionError("owner must never wait for CPU");
        } catch (IllegalStateException expected) {
            // Owner identity is supplied by the consumer, without importing a scheduler or game classes.
        } finally {
            OWNER.set(false);
        }
    }

    private static void subscriberLifecycle() throws Exception {
        List<Runnable> queued = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        Executor executor = queued::add;
        WorkGovernor.Subscription<Integer> first = GOV.singleFlight("same", 1, calls::incrementAndGet, executor);
        WorkGovernor.Subscription<Integer> second = GOV
            .singleFlight("same", 1, () -> { throw new AssertionError("duplicate producer"); }, executor);
        check(queued.size() == 1, "exact subscribers share one producer");
        first.cancel();
        queued.remove(0)
            .run();
        check(
            first.future()
                .isCancelled(),
            "one subscriber cancellation stays local");
        check(
            second.future()
                .get(1, TimeUnit.SECONDS) == 1 && calls.get() == 1,
            "remaining subscriber receives output");
        WorkGovernor.Subscription<Integer> cancelled = GOV
            .singleFlight("abandoned", 1, calls::incrementAndGet, executor);
        cancelled.cancel();
        queued.remove(0)
            .run();
        check(calls.get() == 1, "abandoned queued producer performs no work");
        check(GOV.activeHeavyCpu() == 0, "success releases permit");
    }

    private static void epochAndException() throws Exception {
        List<Runnable> queued = new ArrayList<>();
        WorkGovernor.Subscription<Integer> old = GOV.singleFlight("epoch", 7, () -> 7, queued::add);
        WorkGovernor.Subscription<Integer> fresh = GOV.singleFlight("epoch", 8, () -> 8, queued::add);
        GOV.cancelEpoch(7);
        for (Runnable task : queued) task.run();
        check(
            old.future()
                .isCancelled()
                && fresh.future()
                    .get(1, TimeUnit.SECONDS) == 8,
            "epoch fence is independent");
        WorkGovernor.Subscription<Integer> failed = GOV
            .singleFlight("failure", 0, () -> { throw new IllegalArgumentException("fixture"); }, Runnable::run);
        check(
            failed.future()
                .isCompletedExceptionally(),
            "producer exception reaches subscribers");
        check(GOV.activeHeavyCpu() == 0, "exception releases permit");
        WorkGovernor.Subscription<Integer> retry = GOV.singleFlight("failure", 0, () -> 9, Runnable::run);
        check(
            retry.future()
                .get(1, TimeUnit.SECONDS) == 9,
            "failure removes singleflight entry");
    }

    private static void reentry() throws Exception {
        WorkGovernor.Subscription<Integer> result = GOV.singleFlight("recursive", 0, () -> {
            try {
                GOV.singleFlight("recursive", 0, () -> 0, Runnable::run);
                throw new AssertionError("producer cannot subscribe to itself");
            } catch (IllegalStateException expected) {
                return 1;
            }
        }, Runnable::run);
        check(
            result.future()
                .get(1, TimeUnit.SECONDS) == 1,
            "reentry fails without deadlock");
    }

    private static void ownedProducerLifecycle() throws Exception {
        long baseline = GOV.retainedBytes();
        List<Runnable> queue = new ArrayList<>();
        OwnedProducer first = new OwnedProducer(), duplicate = new OwnedProducer();
        WorkGovernor.Subscription<Integer> a = GOV.singleFlight("owned", 0, first, queue::add);
        WorkGovernor.Subscription<Integer> b = GOV.singleFlight("owned", 0, duplicate, queue::add);
        WorkGovernor.Subscription<Integer> sameCallable = GOV.singleFlight("owned", 0, first, queue::add);
        check(duplicate.closes.get() == 1 && first.closes.get() == 0, "unused duplicate retires only its own input");
        sameCallable.cancel();
        a.cancel();
        check(first.closes.get() == 0, "peer still owns shared producer");
        java.util.concurrent.CompletableFuture<Integer> callback = b.future()
            .whenComplete((value, error) -> {
                check(first.closes.get() == 1, "producer input retires before completion callback");
                check(GOV.activeHeavyCpu() == 0, "CPU released before completion callback");
                check(!GOV.currentFlightCancelled(), "callback is outside producer context");
            });
        queue.remove(0)
            .run();
        callback.get(1, TimeUnit.SECONDS);
        check(
            b.future()
                .get(1, TimeUnit.SECONDS) == 1 && first.closes.get() == 1,
            "accepted producer retires input once after success");
        OwnedProducer abandoned = new OwnedProducer();
        WorkGovernor.Subscription<Integer> c = GOV.singleFlight("owned-cancel", 0, abandoned, queue::add);
        c.cancel();
        check(abandoned.closes.get() == 1, "last queued subscriber releases input immediately");
        queue.remove(0)
            .run();
        check(abandoned.calls.get() == 0 && abandoned.closes.get() == 1, "retired queued producer never runs");
        OwnedProducer rejected = new OwnedProducer();
        WorkGovernor.Subscription<Integer> d = GOV.singleFlight(
            "owned-reject",
            0,
            rejected,
            action -> { throw new java.util.concurrent.RejectedExecutionException("fixture"); });
        check(
            d.future()
                .isCompletedExceptionally() && rejected.closes.get() == 1,
            "executor rejection retires producer input");
        OwnedProducer failed = new OwnedProducer();
        failed.fail = true;
        WorkGovernor.Subscription<Integer> e = GOV.singleFlight("owned-failure", 0, failed, Runnable::run);
        check(
            e.future()
                .isCompletedExceptionally() && failed.closes.get() == 1,
            "producer exception retires input exactly once");
        List<WorkGovernor.Subscription<Integer>> saturated = new ArrayList<>();
        for (int i = 0; i < 64; i++) saturated.add(GOV.singleFlight("full-" + i, 99, new OwnedProducer(), queue::add));
        OwnedProducer overflow = new OwnedProducer();
        try {
            GOV.singleFlight("overflow", 99, overflow, queue::add);
            throw new AssertionError("bounded singleflight admission");
        } catch (java.util.concurrent.RejectedExecutionException expected) {
            check(overflow.closes.get() == 1, "flight-limit rejection retires unused input");
        }
        GOV.cancelEpoch(99);
        for (Runnable task : queue) task.run();
        check(GOV.retainedBytes() == baseline, "all owned inputs release to prior baseline");
    }

    private static void cooperativeCancellation() {
        List<Runnable> queue = new ArrayList<>();
        AtomicInteger abandonedOutput = new AtomicInteger();
        AtomicReference<WorkGovernor.Subscription<AutoCloseable>> owner = new AtomicReference<>();
        owner.set(GOV.singleFlight("running-cancel", 0, () -> {
            check(!GOV.currentFlightCancelled(), "live subscriber keeps producer active");
            owner.get()
                .cancel();
            check(GOV.currentFlightCancelled(), "producer checkpoint sees last cancellation");
            return abandonedOutput::incrementAndGet;
        }, queue::add));
        queue.remove(0)
            .run();
        check(abandonedOutput.get() == 1, "unclaimed closeable output closes once");
        check(
            !GOV.currentFlightCancelled() && GOV.activeHeavyCpu() == 0,
            "cancelled producer clears context and CPU permit");
    }

    private static void sharedOutputDispatch() throws Exception {
        List<Runnable> queue = new ArrayList<>();
        SharedFixture shared = new SharedFixture();
        WorkGovernor.Subscription<SharedFixture> first = GOV.singleFlight("shared-output", 0, () -> shared, queue::add);
        WorkGovernor.Subscription<SharedFixture> second = GOV.singleFlight(
            "shared-output",
            0,
            () -> { throw new AssertionError("duplicate shared producer"); },
            queue::add);
        AtomicInteger callbacks = new AtomicInteger();
        java.util.function.BiConsumer<SharedFixture, Throwable> retainView = (value, error) -> {
            check(
                value.producerCloses.get() == 0 && value.references.get() > 0,
                "producer base stays alive throughout every synchronous subscriber callback");
            value.references.incrementAndGet();
            callbacks.incrementAndGet();
            value.references.decrementAndGet(); // Simulate immediate view cancellation and cache eviction.
        };
        java.util.concurrent.CompletableFuture<SharedFixture> firstCallback = first.future()
            .whenComplete(retainView);
        java.util.concurrent.CompletableFuture<SharedFixture> secondCallback = second.future()
            .whenComplete(retainView);
        queue.remove(0)
            .run();
        firstCallback.get(1, TimeUnit.SECONDS);
        secondCallback.get(1, TimeUnit.SECONDS);
        check(
            callbacks.get() == 2 && shared.producerCloses.get() == 1 && shared.references.get() == 0,
            "shared producer base closes once only after all callbacks release their views");
        SharedFixture inline = new SharedFixture();
        AtomicInteger earlyCallbacks = new AtomicInteger();
        WorkGovernor.Subscription<SharedFixture> immediate = GOV
            .singleFlight("inline-shared-output", 0, () -> inline, Runnable::run, (value, error) -> {
                check(
                    value.producerCloses.get() == 0 && value.references.get() == 1,
                    "onReady callback is installed before even an inline producer completes");
                value.references.incrementAndGet();
                earlyCallbacks.incrementAndGet();
                value.references.decrementAndGet();
            });
        check(
            immediate.future()
                .isDone() && earlyCallbacks.get() == 1
                && inline.producerCloses.get() == 1,
            "producer completed before subscribe returned without retiring its callback input early");
    }

    private static void crossDomainRetiredReaper() {
        GOV.registerRetiredReaper(BROKEN_REAPER);
        GOV.registerRetiredReaper(RETIRED_REAPER);
        GOV.registerRetiredReaper(RETIRED_REAPER); // Identity-idempotent module registration.
        long baseline = GOV.retainedBytes();
        RETIRED.set(
            GOV.retain("other-domain-retired", 32)
                .transfer());
        int calls = REAPER_CALLS.get(), failures = REAPER_FAILURES.get();
        reapFixture = true;
        failReaperFixture = true;
        try {
            // A slow preceding module can use the shared soft allowance; round robin must still reach this owner.
            for (int turn = 0; turn < 32 && (reapFixture || REAPER_FAILURES.get() == failures); turn++) {
                WorkGovernor.Lease admitted = GOV.tryRetain("new-domain", 8);
                check(admitted != null, "one faulty reaper cannot reject unrelated domain admission");
                admitted.close();
            }
            check(!reapFixture && REAPER_CALLS.get() == calls + 1, "cross-domain cleanup progresses without recursion");
            check(REAPER_FAILURES.get() > failures, "faulty module callback was isolated");
            check(GOV.retainedBytes() == baseline, "retired domain lease releases on other admission");
        } finally {
            reapFixture = false;
            failReaperFixture = false;
            WorkGovernor.Lease retained = RETIRED.getAndSet(null);
            if (retained != null) retained.close();
        }
    }

    private static final class SharedFixture implements WorkGovernor.SharedOutput {

        final AtomicInteger references = new AtomicInteger(1), producerCloses = new AtomicInteger();

        @Override
        public void close() {
            producerCloses.incrementAndGet();
            references.decrementAndGet();
        }
    }

    private static final class OwnedProducer implements java.util.concurrent.Callable<Integer>, AutoCloseable {

        final AtomicInteger calls = new AtomicInteger(), closes = new AtomicInteger();
        boolean fail;
        private WorkGovernor.Lease input = GOV.retain("owned-input", 16)
            .transfer();

        @Override
        public Integer call() {
            calls.incrementAndGet();
            if (fail) throw new IllegalArgumentException("owned producer fixture");
            return calls.get();
        }

        @Override
        public void close() {
            closes.incrementAndGet();
            WorkGovernor.Lease retired = input;
            input = null;
            if (retired != null) retired.close();
        }
    }

    private static void fairWorkers() throws Exception {
        if (GOV.heavyCpuPermitLimit() != 1) return;
        List<String> order = java.util.Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(2);
        WorkGovernor.Lease blocker = GOV.tryHeavyCpu("blocker");
        check(blocker != null, "initial CPU admission");
        Thread first = waiter("first", order, failure, finished);
        Thread second = waiter("second", order, failure, finished);
        try {
            first.start();
            awaitQueued(1);
            second.start();
            awaitQueued(2);
        } finally {
            blocker.close();
        }
        check(finished.await(2, TimeUnit.SECONDS), "queued workers finish");
        if (failure.get() != null) throw new AssertionError("worker failed", failure.get());
        check(order.equals(Arrays.asList("first", "second")), "fair CPU wait preserves arrival order");
        check(
            GOV.activeHeavyCpu() == 0 && GOV.queuedHeavyCpu() == 0,
            "fair workers release all permits and queue entries");
    }

    private static Thread waiter(String name, List<String> order, AtomicReference<Throwable> failure,
        CountDownLatch finished) {
        return new Thread(() -> {
            try (WorkGovernor.Lease ignored = GOV.acquireHeavyCpu(name)) {
                order.add(name);
                check(GOV.activeHeavyCpu() == 1, "one process-wide CPU permit");
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            } finally {
                finished.countDown();
            }
        }, "governor-test-" + name);
    }

    private static void awaitQueued(int count) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (GOV.queuedHeavyCpu() < count && System.nanoTime() < deadline) Thread.yield();
        check(GOV.queuedHeavyCpu() >= count, "worker enqueued without holding owner thread");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void independentAndProcessInstances() throws Exception {
        WorkGovernor first = new WorkGovernor(1, 100, null, null);
        WorkGovernor second = new WorkGovernor(1, 100, null, null);
        try (WorkGovernor.Lease a = first.retain("first", 80); WorkGovernor.Lease b = second.retain("second", 100)) {
            check(first.tryRetain("overflow", 21) == null, "configured byte limit applies to its instance");
            check(
                first.retainedBytes() == 80 && second.retainedBytes() == 100,
                "isolated instances own separate quotas");
        }
        java.util.concurrent.atomic.AtomicBoolean processOwner = new java.util.concurrent.atomic.AtomicBoolean();
        WorkGovernor process = WorkGovernor.processShared(1, 256L << 20, processOwner::get, null);
        check(
            process == WorkGovernor.processShared(1, 256L << 20, null, null),
            "explicit process access returns the same governor across consumers");
        try {
            WorkGovernor.processShared(2, 256L << 20, null, null);
            throw new AssertionError("shared process configuration must not silently change");
        } catch (IllegalStateException expected) {}
        processOwner.set(true);
        try {
            process.acquireHeavyCpu("guarded consumer");
            throw new AssertionError("attached process owner guard");
        } catch (IllegalStateException expected) {
            check(process.queuedHeavyCpu() == 0, "owner rejection precedes queue admission");
        } finally {
            processOwner.set(false);
        }
    }

    private static void observerIsolation() throws Exception {
        AtomicReference<WorkGovernor> instance = new AtomicReference<>();
        AtomicInteger rejections = new AtomicInteger(), finished = new AtomicInteger();
        java.util.concurrent.atomic.AtomicLong bytes = new java.util.concurrent.atomic.AtomicLong(-1);
        WorkGovernor.Observer observer = new WorkGovernor.Observer() {

            @Override
            public void cpuState(int active, int queued) {
                throw new IllegalStateException("broken diagnostics");
            }

            @Override
            public void retainedBytes(long value) {
                check(!Thread.holdsLock(instance.get()), "observer runs outside byte quota monitor");
                bytes.set(value);
            }

            @Override
            public void rejected() {
                rejections.incrementAndGet();
            }

            @Override
            public void cpuFinished(String owner, long started, long elapsed) {
                check(
                    instance.get()
                        .activeHeavyCpu() == 0 && elapsed >= 0,
                    "observer sees completed CPU lease");
                finished.incrementAndGet();
            }
        };
        WorkGovernor governor = new WorkGovernor(1, 64, null, observer);
        instance.set(governor);
        try (WorkGovernor.Lease retained = governor.retain("observer", 64)) {
            check(governor.tryRetain("overflow", 1) == null && bytes.get() == 64, "rejection observer is passive");
        }
        try (WorkGovernor.Lease cpu = governor.acquireHeavyCpu("observed CPU")) {
            check(governor.activeHeavyCpu() == 1, "broken observer cannot lose CPU admission");
        }
        check(bytes.get() == 0 && rejections.get() == 1 && finished.get() == 1, "diagnostics do not alter cleanup");
    }

    private static void twoPermitWorkers() throws Exception {
        WorkGovernor governor = new WorkGovernor(2, 1024, null, null);
        CountDownLatch active = new CountDownLatch(2), release = new CountDownLatch(1),
            complete = new CountDownLatch(2);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Runnable worker = () -> {
            try (WorkGovernor.Lease lease = governor.acquireHeavyCpu("two-permit worker")) {
                active.countDown();
                check(release.await(2, TimeUnit.SECONDS), "two-permit release checkpoint");
            } catch (Throwable failure) {
                error.compareAndSet(null, failure);
            } finally {
                complete.countDown();
            }
        };
        Thread first = new Thread(worker, "work-contract-two-first"),
            second = new Thread(worker, "work-contract-two-second");
        first.setDaemon(true);
        second.setDaemon(true);
        first.start();
        second.start();
        try {
            check(active.await(2, TimeUnit.SECONDS), "two configured permits can run concurrently");
            check(
                governor.activeHeavyCpu() == 2 && governor.tryHeavyCpu("third") == null,
                "configured process capacity cannot be exceeded");
        } finally {
            release.countDown();
        }
        check(
            complete.await(2, TimeUnit.SECONDS) && error.get() == null && governor.activeHeavyCpu() == 0,
            "two-permit workers release all admissions");
    }

    private static void interruptedWorker() throws Exception {
        WorkGovernor governor = new WorkGovernor(1, 1024, null, null);
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try (WorkGovernor.Lease lease = governor.acquireHeavyCpu("interrupted")) {
                error.set(new AssertionError("interrupted waiter acquired CPU"));
            } catch (InterruptedException expected) {
                // Interrupt is the worker's cancellation signal, not a discarded CPU permit.
            } catch (Throwable failure) {
                error.set(failure);
            } finally {
                complete.countDown();
            }
        }, "work-contract-interrupted");
        worker.setDaemon(true);
        try (WorkGovernor.Lease blocker = governor.tryHeavyCpu("blocking fixture")) {
            worker.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (governor.queuedHeavyCpu() == 0 && System.nanoTime() < deadline) Thread.yield();
            check(governor.queuedHeavyCpu() == 1, "interruptible worker entered fair queue");
            worker.interrupt();
            check(
                complete.await(2, TimeUnit.SECONDS) && error.get() == null && governor.queuedHeavyCpu() == 0,
                "interrupt retires queue admission without releasing another owner's permit");
            check(governor.activeHeavyCpu() == 1, "blocker remains active after waiter cancellation");
        }
        check(governor.activeHeavyCpu() == 0, "interruption leaves no active CPU lease");
    }
}
