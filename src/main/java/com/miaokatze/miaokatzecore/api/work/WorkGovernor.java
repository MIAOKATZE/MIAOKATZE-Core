package com.miaokatze.miaokatzecore.api.work;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.miaokatze.miaokatzecore.testkit.Deadline;

/**
 * Explicit CPU, retained-memory and exact-key work admission with no executors, events or game dependencies.
 * Consumers own pure-work boundaries, scheduling, revision fences and cleanup callbacks.
 * 
 * @since 0.1.23
 */
public final class WorkGovernor {

    private static WorkGovernor processShared;
    private final int permits, maximumFlights;
    private final long byteLimit;
    private final Semaphore cpu;
    private final AtomicInteger active = new AtomicInteger(), queued = new AtomicInteger();
    private final ThreadLocal<Holdings> holdings = ThreadLocal.withInitial(Holdings::new);
    private final Map<FlightKey, Flight<?>> flights = new HashMap<>();
    private final ThreadLocal<Flight<?>> currentFlight = new ThreadLocal<>();
    private final java.util.concurrent.CopyOnWriteArrayList<Runnable> retiredReapers = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.concurrent.CopyOnWriteArrayList<java.util.function.BooleanSupplier> ownerGuards = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.concurrent.CopyOnWriteArrayList<Observer> observers = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final AtomicBoolean reaping = new AtomicBoolean();
    private int nextReaper;
    private long retained;

    /** Passive diagnostics; callbacks must be short, nonblocking, nonreentrant and take no game/owner locks. */
    public interface Observer {

        default void cpuState(int active, int queued) {}

        default void retainedBytes(long bytes) {}

        default void rejected() {}

        default void cpuFinished(String owner, long startedNanos, long elapsedNanos) {}
    }

    public WorkGovernor(int cpuPermits, long retainedByteLimit, java.util.function.BooleanSupplier ownerThreadGuard,
        Observer observer) {
        this(cpuPermits, retainedByteLimit, 64, ownerThreadGuard, observer);
    }

    public WorkGovernor(int cpuPermits, long retainedByteLimit, int maximumFlights,
        java.util.function.BooleanSupplier ownerThreadGuard, Observer observer) {
        if (cpuPermits < 1 || retainedByteLimit < 1 || maximumFlights < 1)
            throw new IllegalArgumentException("positive cpu, byte and flight limits required");
        permits = cpuPermits;
        byteLimit = retainedByteLimit;
        this.maximumFlights = maximumFlights;
        cpu = new Semaphore(permits, true);
        if (ownerThreadGuard != null) registerOwnerThreadGuard(ownerThreadGuard);
        if (observer != null) registerObserver(observer);
    }

    /**
     * Explicit process-wide singleton. The first caller sets its limits; later callers must request the same limits.
     * All attached guards and observers are passive module-level callbacks, not task/world/player closures.
     */
    public static synchronized WorkGovernor processShared(int cpuPermits, long retainedByteLimit,
        java.util.function.BooleanSupplier ownerThreadGuard, Observer observer) {
        if (processShared == null)
            processShared = new WorkGovernor(cpuPermits, retainedByteLimit, ownerThreadGuard, observer);
        else {
            if (processShared.permits != cpuPermits || processShared.byteLimit != retainedByteLimit)
                throw new IllegalStateException("process work limits already configured");
            if (ownerThreadGuard != null) processShared.registerOwnerThreadGuard(ownerThreadGuard);
            if (observer != null) processShared.registerObserver(observer);
        }
        return processShared;
    }

    public void registerOwnerThreadGuard(java.util.function.BooleanSupplier guard) {
        register(ownerGuards, Objects.requireNonNull(guard, "guard"));
    }

    public void registerObserver(Observer observer) {
        register(observers, Objects.requireNonNull(observer, "observer"));
    }

    private static <T> void register(java.util.concurrent.CopyOnWriteArrayList<T> registry, T callback) {
        synchronized (registry) {
            for (T registered : registry) if (registered == callback) return;
            if (registry.size() >= 16) throw new IllegalStateException("work callback registry full");
            registry.add(callback);
        }
    }

    /**
     * A shared producer reference, released after all synchronous completion callbacks have run.
     * Each subscriber uses the onReady overload to retain its own view before scheduling an asynchronous consumer.
     */
    public interface SharedOutput extends AutoCloseable {
    }

    /** Fair nonblocking admission, including when a worker already owns retained source bytes. */
    public Lease tryHeavyCpu(String owner) {
        Objects.requireNonNull(owner, "owner");
        if (holdings.get().cpu.get() != 0) throw new IllegalStateException("nested heavy cpu: " + owner);
        try {
            if (!cpu.tryAcquire(0, TimeUnit.NANOSECONDS)) return rejected();
        } catch (InterruptedException interrupted) {
            Thread.currentThread()
                .interrupt();
            return null;
        }
        return cpuLease(owner);
    }

    /** Worker only. Never wait holding retained memory, another cpu permit, or an owner thread. */
    public Lease acquireHeavyCpu(String owner) throws InterruptedException {
        Objects.requireNonNull(owner, "owner");
        requireUnleasedWorker(owner);
        queued.incrementAndGet();
        observeCpu();
        try {
            cpu.acquire();
            return cpuLease(owner);
        } finally {
            queued.decrementAndGet();
            observeCpu();
        }
    }

    /** Reserve before allocation; failure means busy, not malformed input. Never waits. */
    public Lease tryRetain(String owner, long bytes) {
        Objects.requireNonNull(owner, "owner");
        if (bytes < 0) throw new IllegalArgumentException("negative retained bytes");
        reapRetired();
        Lease lease;
        long current;
        synchronized (this) {
            if (bytes > byteLimit - retained) lease = null;
            else {
                Holdings held = holdings.get();
                held.bytes.incrementAndGet();
                retained += bytes;
                lease = new Lease(owner, bytes, false, held);
            }
            current = retained;
        }
        if (lease == null) return rejected();
        observeBytes(current);
        return lease;
    }

    /**
     * Register once per module, without capturing world/player or active task state. A callback must process
     * at most 256 references or roughly 0.5 ms per invocation. Admission must precede module owner/cache locks.
     * Callbacks execute outside the governor monitor and cannot recursively start another reaping pass.
     */
    public void registerRetiredReaper(Runnable reaper) {
        Objects.requireNonNull(reaper, "reaper");
        register(retiredReapers, reaper);
    }

    private void reapRetired() {
        if (retiredReapers.isEmpty() || !reaping.compareAndSet(false, true)) return;
        try {
            Deadline budget = Deadline.afterNanos(500_000L);
            int count = retiredReapers.size();
            for (int visited = 0; visited < count; visited++) {
                int index = Math.floorMod(nextReaper, count);
                nextReaper = (index + 1) % count;
                try {
                    retiredReapers.get(index)
                        .run();
                } catch (RuntimeException | LinkageError ignored) {
                    // A retired module must not prevent other domains from making allocation progress.
                }
                if (budget.isExpired()) break;
            }
        } finally {
            reaping.set(false);
        }
    }

    /** Explicit quota rejection; unlike cpu admission this method never blocks. */
    public Lease retain(String owner, long bytes) {
        Lease lease = tryRetain(owner, bytes);
        if (lease == null) throw new RejectedExecutionException("work retained-byte quota busy: " + owner);
        return lease;
    }

    public long retainedByteLimit() {
        return byteLimit;
    }

    public synchronized long retainedBytes() {
        return retained;
    }

    public int activeHeavyCpu() {
        return active.get();
    }

    public int heavyCpuPermitLimit() {
        return permits;
    }

    public int queuedHeavyCpu() {
        return queued.get();
    }

    private Lease rejected() {
        observeRejected();
        return null;
    }

    private Lease cpuLease(String owner) {
        Holdings held = holdings.get();
        held.cpu.incrementAndGet();
        active.incrementAndGet();
        observeCpu();
        return new Lease(owner, 0, true, held);
    }

    private void requireUnleasedWorker(String ownerName) {
        Holdings held = holdings.get();
        boolean owner = false;
        for (java.util.function.BooleanSupplier guard : ownerGuards) if (guard.getAsBoolean()) {
            owner = true;
            break;
        }
        if (owner || held.cpu.get() != 0 || held.bytes.get() != 0)
            throw new IllegalStateException("CPU wait requires an unleased worker: " + ownerName);
    }

    /** May be transferred with its buffer and closed by its final consumer. Close is exactly-once. */
    public final class Lease implements AutoCloseable {

        private final String owner;
        private final long bytes, started;
        private final boolean cpu;
        private Holdings held;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(String owner, long bytes, boolean cpu, Holdings held) {
            this.owner = owner;
            this.bytes = bytes;
            this.cpu = cpu;
            this.held = held;
            started = cpu ? System.nanoTime() : 0;
        }

        public long bytes() {
            return bytes;
        }

        public String owner() {
            return owner;
        }

        /** Detach worker wait-order accounting when ownership moves into a completion message/cache. */
        public synchronized Lease transfer() {
            if (cpu) throw new IllegalStateException("cpu permit cannot be transferred");
            if (!closed.get() && held != null) {
                held.bytes.decrementAndGet();
                held = null;
            }
            return this;
        }

        @Override
        public synchronized void close() {
            if (!closed.compareAndSet(false, true)) return;
            if (cpu) {
                held.cpu.decrementAndGet();
                active.decrementAndGet();
                WorkGovernor.this.cpu.release();
                observeCpu();
                observeFinished(owner, started);
            } else {
                long current;
                synchronized (WorkGovernor.this) {
                    if (held != null) held.bytes.decrementAndGet();
                    retained -= bytes;
                    current = retained;
                }
                observeBytes(current);
            }
        }
    }

    /**
     * Coalesces an immutable, consumer-namespaced exact key within an epoch on the supplied bounded executor.
     * Its callable is pure CPU and must not wait on I/O/futures or acquire another CPU permit.
     * Each caller owns an independent future; cancelling one subscription never cancels its peers.
     * After argument validation, an AutoCloseable callable transfers ownership to this method, including
     * when an existing producer is reused or admission fails. Close must release its frozen input references.
     */
    public <K, V> Subscription<V> singleFlight(K exactKey, long epoch, Callable<V> work, Executor executor) {
        return singleFlight(exactKey, epoch, work, executor, null);
    }

    /**
     * Installs the synchronous subscriber callback before a producer can complete, even with an inline executor.
     * SharedOutput consumers must retain their view in this callback rather than attach a late future callback.
     */
    public <K, V> Subscription<V> singleFlight(K exactKey, long epoch, Callable<V> work, Executor executor,
        java.util.function.BiConsumer<? super V, ? super Throwable> onReady) {
        Objects.requireNonNull(work, "work");
        Objects.requireNonNull(executor, "executor");
        FlightKey key = new FlightKey(Objects.requireNonNull(exactKey, "exactKey"), epoch);
        Flight<V> candidate = new Flight<>(key, work);
        Flight<V> flight;
        Subscription<V> subscription;
        try {
            synchronized (flights) {
                @SuppressWarnings("unchecked")
                Flight<V> existing = (Flight<V>) flights.get(key);
                // Reusing the identical callable cannot transfer its accepted ownership a second time.
                if (existing != null && existing.work == work) candidate.work = null;
                if (existing != null && existing.producer == Thread.currentThread())
                    throw new IllegalStateException("singleflight producer reentry");
                if (existing != null) {
                    flight = existing;
                    subscription = existing.subscribe(onReady);
                } else {
                    if (flights.size() >= maximumFlights)
                        throw new RejectedExecutionException("work singleflight queue full");
                    flight = candidate;
                    flights.put(key, flight);
                    subscription = flight.subscribe(onReady);
                }
            }
        } catch (RuntimeException rejected) {
            candidate.closeWork();
            throw rejected;
        }
        if (flight != candidate) {
            candidate.closeWork();
            return subscription;
        }
        try {
            executor.execute(flight::run);
        } catch (RuntimeException rejected) {
            flight.complete(null, rejected);
        }
        return subscription;
    }

    /** Cooperative producer checkpoint. Peer cancellation cannot cancel a still-subscribed producer. */
    public boolean currentFlightCancelled() {
        Flight<?> current = currentFlight.get();
        if (current == null) return false;
        synchronized (flights) {
            return current.subscribers.isEmpty();
        }
    }

    /** Cancel obsolete epoch subscribers. A running native unit exits before its permit is released. */
    public void cancelEpoch(long epoch) {
        java.util.List<Subscription<?>> obsolete = new java.util.ArrayList<>();
        synchronized (flights) {
            for (Flight<?> flight : flights.values())
                if (flight.key.epoch == epoch) obsolete.addAll(flight.subscribers);
        }
        for (Subscription<?> subscription : obsolete) subscription.cancel();
    }

    public final class Subscription<V> implements AutoCloseable {

        private final Flight<V> flight;
        private final CompletableFuture<V> future = new CompletableFuture<>();

        private Subscription(Flight<V> flight, java.util.function.BiConsumer<? super V, ? super Throwable> onReady) {
            this.flight = flight;
            future.whenComplete((value, error) -> {
                if (!future.isCancelled()) return;
                boolean abandoned;
                synchronized (flights) {
                    flight.subscribers.remove(this);
                    if (flight.subscribers.isEmpty()) flights.remove(flight.key, flight);
                    abandoned = flight.subscribers.isEmpty() && flight.producer == null;
                }
                if (abandoned) flight.complete(null, null);
            });
            if (onReady != null) future.whenComplete(onReady);
        }

        public CompletableFuture<V> future() {
            return future;
        }

        public boolean cancel() {
            return future.cancel(false);
        }

        @Override
        public void close() {
            cancel();
        }
    }

    private final class Flight<V> {

        final FlightKey key;
        Callable<V> work;
        final java.util.List<Subscription<V>> subscribers = new java.util.ArrayList<>();
        Thread producer;
        boolean completed;

        Flight(FlightKey key, Callable<V> work) {
            this.key = key;
            this.work = work;
        }

        Subscription<V> subscribe(java.util.function.BiConsumer<? super V, ? super Throwable> onReady) {
            Subscription<V> subscription = new Subscription<>(this, onReady);
            subscribers.add(subscription);
            return subscription;
        }

        void run() {
            boolean abandoned;
            synchronized (flights) {
                if (completed) return;
                abandoned = subscribers.isEmpty();
                if (!abandoned) producer = Thread.currentThread();
            }
            if (abandoned) {
                complete(null, null);
                return;
            }
            V value = null;
            Throwable failure = null;
            try (Lease ignored = acquire()) {
                if (ignored == null) return;
                synchronized (flights) {
                    if (subscribers.isEmpty()) return;
                }
                currentFlight.set(this);
                try {
                    value = work.call();
                } finally {
                    currentFlight.remove();
                }
            } catch (Throwable error) {
                failure = error;
                if (error instanceof InterruptedException) Thread.currentThread()
                    .interrupt();
            } finally {
                complete(value, failure);
            }
        }

        Lease acquire() throws InterruptedException {
            requireUnleasedWorker("singleflight");
            queued.incrementAndGet();
            observeCpu();
            try {
                while (true) {
                    synchronized (flights) {
                        if (subscribers.isEmpty()) return null;
                    }
                    if (cpu.tryAcquire(5, TimeUnit.MILLISECONDS)) return cpuLease("singleflight");
                }
            } finally {
                queued.decrementAndGet();
                observeCpu();
            }
        }

        void complete(V value, Throwable error) {
            java.util.List<Subscription<V>> done;
            synchronized (flights) {
                if (completed) return;
                completed = true;
                flights.remove(key, this);
                producer = null;
                done = new java.util.ArrayList<>(subscribers);
                subscribers.clear();
            }
            closeWork();
            int delivered = 0;
            for (Subscription<V> subscription : done) if (error == null) {
                if (subscription.future.complete(value)) delivered++;
            } else subscription.future.completeExceptionally(error);
            if (value instanceof SharedOutput || delivered == 0 && value instanceof AutoCloseable) try {
                ((AutoCloseable) value).close();
            } catch (Exception ignored) {
                // An abandoned producer must not retain its output; close is best-effort for foreign values.
            }
        }

        void closeWork() {
            Callable<V> retired;
            synchronized (this) {
                retired = work;
                work = null;
            }
            if (retired instanceof AutoCloseable) try {
                ((AutoCloseable) retired).close();
            } catch (Exception ignored) {
                // Consumer-owned cleanup is best effort; the callable reference is retired exactly once.
            }
        }
    }

    private static final class Holdings {

        final AtomicInteger cpu = new AtomicInteger(), bytes = new AtomicInteger();
    }

    private static final class FlightKey {

        final Object exact;
        final long epoch;

        FlightKey(Object exact, long epoch) {
            this.exact = exact;
            this.epoch = epoch;
        }

        @Override
        public int hashCode() {
            return 31 * exact.hashCode() + Long.hashCode(epoch);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof FlightKey)) return false;
            FlightKey key = (FlightKey) other;
            return epoch == key.epoch && exact.equals(key.exact);
        }
    }

    private void observeCpu() {
        for (Observer observer : observers) try {
            observer.cpuState(active.get(), queued.get());
        } catch (RuntimeException | LinkageError ignored) {}
    }

    private void observeBytes(long value) {
        for (Observer observer : observers) try {
            observer.retainedBytes(value);
        } catch (RuntimeException | LinkageError ignored) {}
    }

    private void observeRejected() {
        for (Observer observer : observers) try {
            observer.rejected();
        } catch (RuntimeException | LinkageError ignored) {}
    }

    private void observeFinished(String owner, long started) {
        long elapsed = Math.max(0, System.nanoTime() - started);
        for (Observer observer : observers) try {
            observer.cpuFinished(owner, started, elapsed);
        } catch (RuntimeException | LinkageError ignored) {}
    }
}
