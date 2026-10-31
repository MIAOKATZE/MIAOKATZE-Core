package com.miaokatze.miaokatzecore.api.animation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.miaokatze.miaokatzecore.api.animation.runtime.FeatureAdmission;
import com.miaokatze.miaokatzecore.api.animation.runtime.MetricWindow;
import com.miaokatze.miaokatzecore.api.animation.runtime.QualityGovernor;
import com.miaokatze.miaokatzecore.api.animation.runtime.ResourceLedger;
import com.miaokatze.miaokatzecore.api.animation.runtime.RuntimeGenerations;
import com.miaokatze.miaokatzecore.api.animation.runtime.SessionLifecycle;
import com.miaokatze.miaokatzecore.api.miaovision.SceneBody;
import com.miaokatze.miaokatzecore.api.miaovision.SceneBounds;
import com.miaokatze.miaokatzecore.api.miaovision.SceneSnapshot;
import com.miaokatze.miaokatzecore.client.animation.AnimationSession;
import com.miaokatze.miaokatzecore.client.animation.ResourceOwner;

/** Production-class tests. No game, renderer, device or sound engine is mocked into the production classpath. */
public final class RuntimeContracts {

    private static int assertions;

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("RuntimeContracts PASS (" + assertions + " assertions)");
    }

    public static void run() throws Exception {
        generations();
        lifecycle();
        ledger();
        resourceOwnership();
        cleanupFailures();
        admission();
        metricWindows();
        quality();
        sessions();
        sessionReentrancy();
        sessionFailures();
        miaovisionBoundary();
    }

    private static void generations() throws Exception {
        RuntimeGenerations a = new RuntimeGenerations(), b = new RuntimeGenerations();
        RuntimeGenerations.Ticket cpu = a.ticket(RuntimeGenerations.Purpose.CPU_DECODE);
        RuntimeGenerations.Ticket gpu = a.ticket(RuntimeGenerations.Purpose.GPU_UPLOAD);
        RuntimeGenerations.Ticket frame = a.ticket(RuntimeGenerations.Purpose.PRESENTATION);
        check(a.accepts(cpu) && a.accepts(gpu) && a.accepts(frame), "fresh domains");
        check(!b.accepts(cpu) && !a.accepts(null), "equal numeric generations do not share owner");
        a.replacedInstance();
        check(a.accepts(cpu) && a.accepts(gpu) && !a.accepts(frame), "instance preserves decode/upload");
        a.changedContext();
        check(a.accepts(cpu) && !a.accepts(gpu), "context preserves only pure decode");
        a.reloadedAssets();
        check(!a.accepts(cpu), "reload rejects old decode");
        RuntimeGenerations.Ticket newer = a.ticket(RuntimeGenerations.Purpose.CPU_DECODE);
        a.changedDefinition();
        check(!a.accepts(newer), "definition rejects stale content");
        RuntimeGenerations.Ticket last = a.ticket(RuntimeGenerations.Purpose.PRESENTATION);
        a.close();
        a.close();
        check(!a.accepts(last) && b.accepts(b.ticket(RuntimeGenerations.Purpose.PRESENTATION)), "close is exact owner");
        expect(IllegalStateException.class, () -> a.ticket(RuntimeGenerations.Purpose.CPU_DECODE));
        foreignThread(() -> b.accepts(cpu));
    }

    private static void lifecycle() throws Exception {
        EpochKey epoch = new EpochKey(1, 2, 3);
        SessionLifecycle a = new SessionLifecycle(epoch), b = new SessionLifecycle(new EpochKey(1, 2, 3));
        SessionLifecycle.Ticket ticket = a.ticket();
        check(!b.canPublish(ticket), "epoch values alone are insufficient");
        a.suspend();
        check(a.canPublish(ticket) && !a.mayRender(), "suspend accepts pure cache only");
        a.resume();
        check(a.mayRender(), "resume");
        a.changedDocument();
        check(!a.canPublish(ticket), "document fences job");
        ticket = a.ticket();
        a.resourceReload();
        check(!a.canPublish(ticket), "reload fences job");
        ticket = a.ticket();
        a.close();
        check(!a.canPublish(ticket) && !a.mayRender(), "closed job cannot publish");
        expect(IllegalStateException.class, a::resume);
        foreignThread(b::state);
    }

    private static void ledger() throws Exception {
        ResourceLedger ledger = new ResourceLedger(Long.MAX_VALUE, 100, 3, 50, 70, 2);
        ResourceLedger.Lease old = ledger.reserve(Long.MAX_VALUE, 60, 1, 0, 0, 0);
        check(old != null, "exact long reservation");
        check(ledger.reserve(1, 0, 0, 0, 0, 0) == null, "addition cannot overflow quota");
        check(ledger.reserve(0, 41, 0, 0, 0, 0) == null, "old/new overlap charged");
        check(ledger.activeLeases() == 1 && ledger.used(ResourceLedger.Dimension.GPU_BYTES) == 60,
            "atomic refusal preserves all dimensions");
        ResourceLedger.Lease next = ledger.reserve(0, 40, 1, 0, 0, 0);
        check(next != null && ledger.used(ResourceLedger.Dimension.GPU_BYTES) == 100, "old plus new peak");
        old.close();
        old.close();
        check(ledger.used(ResourceLedger.Dimension.GPU_BYTES) == 40 && next.active(), "old close cannot release new");
        foreignThread(next::close);
        next.close();
        check(ledger.activeLeases() == 0, "reservations return once");
        for (int i = 0; i < ResourceLedger.MAX_LEASES; i++)
            check(ledger.reserve(0, 0, 0, 0, 0, 0) != null, "bounded zero-byte leases");
        check(ledger.reserve(0, 0, 0, 0, 0, 0) == null, "lease count bounded");
        expect(IllegalArgumentException.class, () -> ledger.reserve(-1, 0, 0, 0, 0, 0));
        ledger.close();
        ledger.close();
        check(ledger.activeLeases() == 0, "ledger accounting teardown");
        expect(IllegalStateException.class, () -> ledger.reserve(0, 0, 0, 0, 0, 0));
    }

    private static void resourceOwnership() throws Exception {
        RuntimeGenerations fence = new RuntimeGenerations();
        ResourceOwner owner = new ResourceOwner(fence);
        ResourceLedger ledger = smallLedger();
        Closing borrowed = new Closing(), old = new Closing(), replacement = new Closing();
        RuntimeGenerations.Ticket ticket = fence.ticket(RuntimeGenerations.Purpose.GPU_UPLOAD);
        ResourceOwner.Handle<Closing> atlas = owner.borrow(ticket, borrowed);
        expect(IllegalArgumentException.class,
            () -> owner.borrow(fence.ticket(RuntimeGenerations.Purpose.CPU_DECODE), new Object()));
        ResourceOwner.Handle<Closing> first = owner.own(ticket, old, ledger.reserve(0, 10, 1, 0, 0, 0));
        check(atlas.get() == borrowed && !atlas.owned() && first.owned(), "explicit owned versus borrowed");
        fence.reloadedAssets();
        check(!atlas.valid() && !first.valid(), "reload invalid immediately before retirement");
        expect(IllegalStateException.class, atlas::get);
        expect(IllegalArgumentException.class, () -> owner.borrow(ticket, new Object()));
        ResourceOwner.Handle<Closing> second = owner.own(fence.ticket(RuntimeGenerations.Purpose.GPU_UPLOAD),
            replacement, ledger.reserve(0, 10, 1, 0, 0, 0));
        check(ledger.used(ResourceLedger.Dimension.GPU_BYTES) == 20, "both generations remain billed");
        owner.retireInvalidated();
        check(old.closed == 1 && borrowed.closed == 0 && replacement.closed == 0, "retirement owns only old resource");
        first.close();
        check(second.valid() && ledger.used(ResourceLedger.Dimension.GPU_BYTES) == 10, "old handle cannot free new");
        foreignThread(second::get);
        fence.changedContext();
        expect(IllegalStateException.class, second::get);
        owner.retireInvalidated();
        check(replacement.closed == 1 && ledger.activeLeases() == 0, "owned resource released before reservation");
        owner.close();
        check(borrowed.closed == 0, "borrowed engine handle never deleted");
    }

    private static void cleanupFailures() throws Exception {
        RuntimeGenerations fence = new RuntimeGenerations();
        ResourceOwner owner = new ResourceOwner(fence);
        ResourceLedger ledger = smallLedger();
        Exception same = new Exception("same cleanup failure");
        Closing a = new Closing(), b = new Closing(), c = new Closing();
        a.failure = same;
        b.failure = same;
        ResourceLedger.Lease failed = ledger.reserve(0, 10, 1, 0, 0, 0);
        owner.own(fence.ticket(RuntimeGenerations.Purpose.GPU_UPLOAD), a, failed);
        owner.own(fence.ticket(RuntimeGenerations.Purpose.GPU_UPLOAD), b, ledger.reserve(0, 10, 1, 0, 0, 0));
        c.onClose = owner::close;
        owner.own(fence.ticket(RuntimeGenerations.Purpose.GPU_UPLOAD), c, ledger.reserve(0, 10, 1, 0, 0, 0));
        Throwable thrown = expect(Exception.class, owner::close);
        check(thrown == same && same.getSuppressed().length == 0, "same exception not self-suppressed");
        check(a.closed == 1 && b.closed == 1 && c.closed == 1, "failure still cleans remaining resources");
        check(owner.activeHandles() == 0 && owner.failedReleases() == 2, "failed retirement visible");
        check(failed.active() && ledger.used(ResourceLedger.Dimension.GPU_BYTES) == 20,
            "failed device deletion does not fake zero accounting");
        owner.close();
        check(a.closed == 1 && c.closed == 1, "no implicit cleanup retries");

        ResourceOwner reentrant = new ResourceOwner(fence);
        Closing resource = new Closing();
        ResourceLedger.Lease lease = ledger.reserve(0, 1, 1, 0, 0, 0);
        RuntimeGenerations.Ticket ticket = fence.ticket(RuntimeGenerations.Purpose.GPU_UPLOAD);
        resource.onClose = () -> expect(IllegalStateException.class,
            () -> reentrant.own(ticket, resource, lease));
        ResourceOwner.Handle<Closing> handle = reentrant.own(ticket, resource, lease);
        handle.close();
        check(!lease.active() && reentrant.activeHandles() == 0, "cleanup cannot rebind soon-to-be-released lease");
    }

    private static void admission() throws Exception {
        ResourceId a = ResourceId.parse("core:a"), b = ResourceId.parse("core:b"), c = ResourceId.parse("core:c");
        Set<ResourceId> required = set(b, a), optional = set(c), supported = set(a);
        FeatureAdmission.Decision result = FeatureAdmission.evaluate(required, optional, supported);
        check(result.status == FeatureAdmission.Status.REJECTED && result.missingRequired.equals(set(b)),
            "required feature rejects");
        check(result.omittedOptional.equals(set(c)), "optional omission reported even on rejection");
        required.clear();
        optional.clear();
        supported.clear();
        check(result.missingRequired.equals(set(b)), "decision snapshots inputs");
        expect(UnsupportedOperationException.class, () -> result.missingRequired.clear());
        check(FeatureAdmission.evaluate(set(a), set(b), set(a)).status == FeatureAdmission.Status.DEGRADED,
            "optional omission degrades");
        check(FeatureAdmission.evaluate(set(a), set(b), set(a, b)).status == FeatureAdmission.Status.SUPPORTED,
            "all supported");
        expect(IllegalArgumentException.class, () -> FeatureAdmission.evaluate(set(a), set(a), set(a)));
        Set<ResourceId> oversized = new HashSet<>();
        for (int i = 0; i < 129; i++) oversized.add(ResourceId.parse("core:f" + i));
        expect(IllegalArgumentException.class, () -> FeatureAdmission.evaluate(oversized, set(), set()));
        check(new ArrayList<>(FeatureAdmission.evaluate(set(c, a, b), set(), set()).missingRequired)
            .equals(Arrays.asList(a, b, c)), "stable ID order");
    }

    private static void metricWindows() throws Exception {
        MetricWindow window = new MetricWindow(10, 3);
        check(window.snapshot(0).p95 == -1, "empty is unavailable");
        window.record(0, 0);
        check(window.snapshot(0).p95 == 0, "measured zero is valid");
        window.record(1, 10);
        window.record(2, 20);
        window.record(3, 30);
        MetricWindow.Snapshot s = window.snapshot(3);
        check(s.samples == 3 && s.capacityEvictions == 1 && s.observations == 4 && s.coveredSpanNanos == 2,
            "hard capacity and actual covered span");
        check(s.p50 == 20 && s.p95 == 30 && s.p99 == 30 && s.max == 30, "nearest rank");
        s = window.snapshot(11);
        check(s.samples == 2 && s.timeExpirations == 1, "left boundary is exclusive");
        window.noteUpstreamDropped(Long.MAX_VALUE);
        window.noteUpstreamDropped(1);
        s = window.snapshot(13);
        check(s.samples == 0 && s.p50 == -1 && s.timeExpirations == 3 && s.capacityEvictions == 1
            && s.upstreamDropped == Long.MAX_VALUE && s.observations == 4, "cumulative saturated loss remains explicit");
        expect(IllegalArgumentException.class, () -> window.record(12, 100));
        expect(IllegalArgumentException.class, () -> window.record(15, -1));
        window.record(13, 7);
        check(window.snapshot(13).observations == 5, "invalid record does not advance clock or count");
        foreignThread(() -> window.snapshot(13));

        MetricWindow ranks = new MetricWindow(Long.MAX_VALUE, 100);
        for (int i = 100; i >= 1; i--) ranks.record(0, i);
        s = ranks.snapshot(0);
        check(s.p50 == 50 && s.p95 == 95 && s.p99 == 99 && s.max == 100, "independent ordered-distribution oracle");
        check(ranks.snapshot(Long.MAX_VALUE).samples == 0, "long boundary expires without overflow");
    }

    private static void quality() throws Exception {
        QualityGovernor governor = new QualityGovernor(QualityGovernor.Tier.HIGH, 10, 20, 5, 2, 3, 10);
        check(!governor.observe(0, 0, 21, 5, true).changed, "one high window holds");
        check(governor.observe(1, 1, -1, 0, false).reason == QualityGovernor.Reason.MISSING_OR_LOSSY,
            "unavailable is not zero load");
        check(!governor.observe(2, 2, 21, 5, true).changed, "loss clears hysteresis");
        QualityGovernor.Decision d = governor.observe(3, 3, 21, 5, true);
        check(d.changed && d.tier == QualityGovernor.Tier.MEDIUM && d.reason == QualityGovernor.Reason.OVER_BUDGET,
            "continuous high windows lower one tier");
        check(governor.observe(4, 4, 0, 5, true).reason == QualityGovernor.Reason.COOLDOWN, "cooldown clears evidence");
        check(!governor.observe(5, 13, 9, 5, true).changed, "recovery begins after cooldown");
        check(!governor.observe(6, 14, 9, 5, true).changed, "recovery requires all windows");
        check(governor.observe(7, 15, 9, 5, true).tier == QualityGovernor.Tier.HIGH, "headroom recovers one tier");
        governor.setLocked(true);
        check(governor.observe(8, 30, 100, 5, true).reason == QualityGovernor.Reason.LOCKED, "capture lock");
        governor.setLocked(false);
        expect(IllegalArgumentException.class, () -> governor.observe(8, 31, 100, 5, true));
        expect(IllegalArgumentException.class, () -> governor.observe(9, 31, 0, 0, true));
        check(governor.observe(9, 31, 100, 1, true).reason == QualityGovernor.Reason.TOO_FEW_SAMPLES,
            "insufficient sample resets");
        check(!governor.observe(10, 32, 20, 5, true).changed, "exact high threshold holds");
        check(!governor.observe(11, 33, 21, 5, true).changed, "held threshold cleared streak");
        foreignThread(() -> governor.setLocked(true));
        QualityGovernor off = new QualityGovernor(QualityGovernor.Tier.OFF, 10, 20, 1, 1, 1, 0);
        check(off.observe(0, 0, 0, 1, true).tier == QualityGovernor.Tier.LOW,
            "OFF is adaptive floor; separate host permission must enforce user disable");
    }

    private static void sessions() throws Exception {
        Host host = new Host();
        AnimationV1.Document initial = document("core:initial", 1);
        AnimationSession session = new AnimationSession(new EpochKey(0, 0, 0), initial, host);
        check(session.present(0) && session.presentedFrame().sampledFrom(initial), "compiled frame exact definition");
        SampledFrame old = session.presentedFrame();
        expect(IllegalArgumentException.class, () -> session.replaceDocument(oversizedDocument()));
        check(session.presentedFrame() == old && session.present(1) && host.last.sampledFrom(initial),
            "failed compiled replacement preserves valid plan and prior frame");
        session.suspend();
        check(!session.present(2) && session.presentedFrame() == null && host.stopped == 1, "suspend stops effects/draw");
        RuntimeGenerations.Ticket cpu = session.ticket(RuntimeGenerations.Purpose.CPU_DECODE);
        check(session.accepts(cpu), "suspended pure result can be retained");
        session.resume();
        check(session.seek(7) && host.stopped == 2 && session.presentedFrame().localTimeMs == 7,
            "seek stops transient handles without cues");
        RuntimeGenerations.Ticket gpu = session.ticket(RuntimeGenerations.Purpose.GPU_UPLOAD);
        session.contextChanged();
        check(session.accepts(cpu) && !session.accepts(gpu) && session.presentedFrame() == null,
            "context invalidates device/presentation, not pure decode");
        session.resourceReload();
        check(!session.accepts(cpu), "reload rejects late pure result");
        AnimationV1.Document replacement = document("core:replacement", 2);
        session.replaceDocument(replacement);
        check(session.present(0) && host.last.sampledFrom(replacement), "committed new compiled definition");
        foreignThread(() -> session.present(0));
        session.close();
        session.close();
        check(host.closed == 1 && session.presentedFrame() == null, "host closes exactly once");
    }

    private static void sessionReentrancy() throws Exception {
        Host host = new Host();
        AnimationSession session = new AnimationSession(new EpochKey(0, 0, 0), document("core:close", 0), host);
        host.onPresent = () -> {
            session.close();
            check(host.closed == 0, "device cleanup deferred during active presentation");
            check(session.state() == SessionLifecycle.State.CLOSED, "close fences immediately");
        };
        check(!session.present(0) && host.drawn == 1 && host.closed == 1 && session.presentedFrame() == null,
            "closed callback cannot commit its old frame");

        Host recursive = new Host();
        AnimationSession second = new AnimationSession(new EpochKey(0, 0, 0), document("core:recursive", 0), recursive);
        recursive.onPresent = () -> second.present(0);
        expect(IllegalStateException.class, () -> second.present(0));
        check(recursive.drawn == 1 && recursive.closed == 1, "recursive rendering refused, never retried");

        Host retiringHost = new Host();
        AnimationSession retiring = new AnimationSession(new EpochKey(0, 0, 0), document("core:retire", 0), retiringHost);
        Closing resource = new Closing();
        resource.onClose = () -> {
            retiring.close();
            check(retiringHost.closed == 0, "host close deferred during resource retirement");
        };
        retiring.resources().own(retiring.ticket(RuntimeGenerations.Purpose.GPU_UPLOAD), resource,
            smallLedger().reserve(0, 1, 1, 0, 0, 0));
        retiring.resourceReload();
        check(retiringHost.closed == 1 && resource.closed == 1 && retiring.state() == SessionLifecycle.State.CLOSED,
            "resource callback close retires once before returning");
    }

    private static void sessionFailures() throws Exception {
        Exception shared = new Exception("same draw and cleanup failure");
        Host host = new Host();
        host.drawFailure = shared;
        host.stopFailure = shared;
        host.closeFailure = shared;
        AnimationSession session = new AnimationSession(new EpochKey(0, 0, 0), document("core:failure", 0), host);
        ResourceOwner resources = session.resources();
        ResourceLedger ledger = smallLedger();
        Closing bad = new Closing(), good = new Closing();
        bad.failure = shared;
        RuntimeGenerations.Ticket ticket = session.ticket(RuntimeGenerations.Purpose.GPU_UPLOAD);
        resources.own(ticket, bad, ledger.reserve(0, 10, 1, 0, 0, 0));
        resources.own(ticket, good, ledger.reserve(0, 10, 1, 0, 0, 0));
        check(expect(Exception.class, () -> session.present(0)) == shared, "original draw failure preserved");
        check(shared.getSuppressed().length == 0, "shared cleanup exception cannot mask original");
        check(good.closed == 1 && bad.closed == 1 && host.closed == 1 && host.drawn == 1,
            "all cleanup owners attempted, draw called once");
        check(ledger.used(ResourceLedger.Dimension.GPU_BYTES) == 10 && resources.failedReleases() == 1,
            "failed release accounting survives session closure");

        Host distinct = new Host();
        distinct.drawFailure = new Exception("draw");
        distinct.closeFailure = new Exception("close");
        AnimationSession second = new AnimationSession(new EpochKey(0, 0, 0), document("core:distinct", 0), distinct);
        Throwable primary = expect(Exception.class, () -> second.present(0));
        check(primary == distinct.drawFailure && primary.getSuppressed().length == 1
            && primary.getSuppressed()[0] == distinct.closeFailure, "distinct cleanup preserved as suppressed");
    }

    private static void miaovisionBoundary() throws Exception {
        List<SceneBody> bodies = new ArrayList<>();
        for (int i = 0; i < 100; i++) bodies.add(new SceneBody("b" + i, .5, .5, .5, 0, 0, 0));
        SceneBounds bounds = new SceneBounds(-128, -128, -128, 128, 128, 128);
        for (SceneBody body : bodies) bounds.validate(body);
        SceneSnapshot snapshot = new SceneSnapshot("existing-contract", 0, 0, bodies);
        check(snapshot.bodies.size() == 100 && SceneSnapshot.MAX_BODIES == 100 && snapshot.schemaVersion == 1,
            "production MiaoVision admits 100 valid bodies without changing schema");
        bodies.add(new SceneBody("b100", .5, .5, .5, 0, 0, 0));
        expect(IllegalArgumentException.class, () -> new SceneSnapshot("existing-contract", 0, 0, bodies));
        check(snapshot.bodies.size() == 100, "101 refused rather than truncating input");
        expect(IllegalArgumentException.class, () -> new SceneBounds(-128, -128, -128, 129, 128, 128));
        expect(IllegalArgumentException.class,
            () -> bounds.validate(new SceneBody("outside", .5, .5, .5, 128, 0, 0)));
    }

    private static AnimationV1.Document document(String id, double x) {
        AnimationV1.Track track = new AnimationV1.Track("x", "body", AnimationV1.Property.X,
            Collections.singletonList(new AnimationV1.Key(0, x, AnimationV1.Interpolation.LINEAR)));
        return new AnimationV1.Document(id, 0, 100, 0, Collections.singletonList(track), Collections.emptyList(),
            Collections.emptySet());
    }

    private static AnimationV1.Document oversizedDocument() {
        List<AnimationV1.Key> keys = new ArrayList<>();
        for (int i = 0; i < 4096; i++)
            keys.add(new AnimationV1.Key(i, 123456.123456789, AnimationV1.Interpolation.SMOOTHSTEP));
        List<AnimationV1.Track> tracks = new ArrayList<>();
        for (int i = 0; i < 16; i++) tracks.add(new AnimationV1.Track("x" + i, "b" + i, AnimationV1.Property.X, keys));
        return new AnimationV1.Document("core:oversized", 0, 5000, 0, tracks, Collections.emptyList(),
            Collections.emptySet());
    }

    private static ResourceLedger smallLedger() {
        return new ResourceLedger(100, 100, 100, 100, 100, 100);
    }

    private static Set<ResourceId> set(ResourceId... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    private interface Action {
        void run() throws Exception;
    }

    private static final class Closing implements AutoCloseable {

        int closed;
        Exception failure;
        Action onClose;

        @Override
        public void close() throws Exception {
            closed++;
            if (onClose != null) onClose.run();
            if (failure != null) throw failure;
        }
    }

    private static final class Host implements AnimationSession.TrustedHost {

        int drawn, stopped, closed;
        SampledFrame last;
        Action onPresent;
        Exception drawFailure, stopFailure, closeFailure;

        @Override
        public void present(SampledFrame frame) throws Exception {
            drawn++;
            last = frame;
            if (onPresent != null) onPresent.run();
            if (drawFailure != null) throw drawFailure;
        }

        @Override
        public void stopTransientEffects() throws Exception {
            stopped++;
            if (stopFailure != null) throw stopFailure;
        }

        @Override
        public void close() throws Exception {
            closed++;
            if (closeFailure != null) throw closeFailure;
        }
    }

    private static void foreignThread(Action action) throws Exception {
        AtomicReference<Throwable> result = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                action.run();
            } catch (Throwable failure) {
                result.set(failure);
            }
        }, "runtime-contract-foreign-owner");
        thread.start();
        thread.join(5000);
        check(!thread.isAlive() && result.get() instanceof IllegalStateException, "foreign thread refused");
    }

    private static Throwable expect(Class<? extends Throwable> type, Action action) throws Exception {
        try {
            action.run();
        } catch (Throwable failure) {
            check(type.isInstance(failure), "expected " + type.getName() + ", got " + failure);
            return failure;
        }
        throw new AssertionError("expected " + type.getName());
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
