package com.miaokatze.miaokatzecore.client.animation;

import com.miaokatze.miaokatzecore.api.animation.AnimationV1;
import com.miaokatze.miaokatzecore.api.animation.CompiledPlan;
import com.miaokatze.miaokatzecore.api.animation.EpochKey;
import com.miaokatze.miaokatzecore.api.animation.EvaluationInput;
import com.miaokatze.miaokatzecore.api.animation.SampledFrame;
import com.miaokatze.miaokatzecore.api.animation.runtime.RuntimeGenerations;
import com.miaokatze.miaokatzecore.api.animation.runtime.SessionLifecycle;

/**
 * Owner-driven scalar presentation seam. No tick registration, cue dispatch, camera acquisition or gameplay action.
 * Hosts are audited application code, never chosen by an untrusted JSON class name or callback.
 */
public final class AnimationSession implements AutoCloseable {

    public interface TrustedHost extends AutoCloseable {

        /** Validate bindings/viewport and exact camera lease around any native callbacks. Never retry failed draw. */
        void present(SampledFrame frame) throws Exception;

        /** Actively stop this host's audio/impulses; an empty event queue does not stop existing sound handles. */
        void stopTransientEffects() throws Exception;

        /** Close only exact owned handles (including an existing WorldCameras.Lease), never global camera state. */
        @Override
        void close() throws Exception;
    }

    private final Thread owner = Thread.currentThread();
    private final SessionLifecycle lifecycle;
    private final RuntimeGenerations generations = new RuntimeGenerations();
    private final ResourceOwner resources = new ResourceOwner(generations);
    private final TrustedHost host;
    private CompiledPlan plan;
    private SampledFrame presented;
    private boolean callbackActive, cleaned;

    public AnimationSession(EpochKey epoch, AnimationV1.Document document, TrustedHost host) {
        if (document == null || host == null) throw new IllegalArgumentException("document/host");
        plan = CompiledPlan.compile(document);
        lifecycle = new SessionLifecycle(epoch);
        this.host = host;
    }

    public SessionLifecycle.State state() {
        return lifecycle.state();
    }

    public RuntimeGenerations.Ticket ticket(RuntimeGenerations.Purpose purpose) {
        checkOpen();
        return generations.ticket(purpose);
    }

    public boolean accepts(RuntimeGenerations.Ticket ticket) {
        return generations.accepts(ticket);
    }

    public ResourceOwner resources() {
        checkOpen();
        return resources;
    }

    /** Last successfully completed presentation, cleared by suspend, seek, invalidation and close. */
    public SampledFrame presentedFrame() {
        check();
        return presented;
    }

    /** Prepare a pure frame, call the host once, then commit only if the exact session is still active. */
    public boolean present(long localTimeMs) throws Exception {
        checkMutation();
        if (!lifecycle.mayRender()) return false;
        SampledFrame frame = plan.sample(new EvaluationInput(localTimeMs));
        RuntimeGenerations.Ticket publication = generations.ticket(RuntimeGenerations.Purpose.PRESENTATION);
        try {
            callbackActive = true;
            try {
                host.present(frame);
            } finally {
                callbackActive = false;
            }
            if (state() == SessionLifecycle.State.CLOSED) {
                cleanup();
                return false;
            }
            if (!lifecycle.mayRender() || !generations.accepts(publication)) return false;
            presented = frame;
            return true;
        } catch (Exception | Error failure) {
            fail(failure);
            throw failure;
        }
    }

    /** No cue dispatch. A separately owned EventCursor must also seekSilently before playback resumes. */
    public boolean seek(long localTimeMs) throws Exception {
        checkMutation();
        presented = null;
        stopTransientEffects();
        return state() != SessionLifecycle.State.CLOSED && present(localTimeMs);
    }

    public void suspend() throws Exception {
        checkMutation();
        lifecycle.suspend();
        presented = null;
        stopTransientEffects();
    }

    public void resume() {
        checkMutation();
        lifecycle.resume();
    }

    /** Prepare the complete compiled plan first; rejected preparation leaves the previous plan and frame intact. */
    public void replaceDocument(AnimationV1.Document replacement) throws Exception {
        checkMutation();
        if (replacement == null) throw new IllegalArgumentException("document");
        CompiledPlan prepared = CompiledPlan.compile(replacement);
        generations.changedDefinition();
        plan = prepared;
        invalidate();
    }

    /** Call before reusing resources after reload. Borrowed mappings become unreadable immediately. */
    public void resourceReload() throws Exception {
        checkMutation();
        generations.reloadedAssets();
        invalidate();
    }

    /** Call before the old context is destroyed; host release callbacks must know their exact original context. */
    public void contextChanged() throws Exception {
        checkMutation();
        generations.changedContext();
        invalidate();
    }

    private void invalidate() throws Exception {
        presented = null;
        try {
            stopTransientEffects();
            callbackActive = true;
            try {
                resources.retireInvalidated();
            } finally {
                callbackActive = false;
            }
            if (state() == SessionLifecycle.State.CLOSED) cleanup();
        } catch (Exception | Error failure) {
            fail(failure);
            throw failure;
        }
    }

    private void stopTransientEffects() throws Exception {
        try {
            callbackActive = true;
            try {
                host.stopTransientEffects();
            } finally {
                callbackActive = false;
            }
            if (state() == SessionLifecycle.State.CLOSED) cleanup();
        } catch (Exception | Error failure) {
            fail(failure);
            throw failure;
        }
    }

    private void fail(Throwable primary) {
        try {
            close();
        } catch (Exception | Error cleanup) {
            if (cleanup != primary) primary.addSuppressed(cleanup);
        }
    }

    /** Close inside a host callback fences immediately; actual cleanup waits until that callback returns. */
    @Override
    public void close() throws Exception {
        check();
        lifecycle.close();
        generations.close();
        presented = null;
        if (!callbackActive) cleanup();
    }

    private void cleanup() throws Exception {
        if (cleaned) return;
        cleaned = true;
        Throwable failure = null;
        try {
            host.stopTransientEffects();
        } catch (Exception | Error thrown) {
            failure = thrown;
        }
        try {
            resources.close();
        } catch (Exception | Error thrown) {
            if (failure == null) failure = thrown;
            else if (failure != thrown) failure.addSuppressed(thrown);
        }
        try {
            host.close();
        } catch (Exception | Error thrown) {
            if (failure == null) failure = thrown;
            else if (failure != thrown) failure.addSuppressed(thrown);
        }
        if (failure instanceof Error) throw (Error) failure;
        if (failure != null) throw (Exception) failure;
    }

    private void check() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("owner thread required");
    }

    private void checkOpen() {
        check();
        if (state() == SessionLifecycle.State.CLOSED) throw new IllegalStateException("closed");
    }

    private void checkMutation() {
        checkOpen();
        if (callbackActive) throw new IllegalStateException("reentrant host mutation");
    }
}
