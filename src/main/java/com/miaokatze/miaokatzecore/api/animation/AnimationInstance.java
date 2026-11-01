package com.miaokatze.miaokatzecore.api.animation;

import java.io.IOException;
import java.io.InputStream;

/**
 * Owner-thread scalar playback identity and current plan. The host supplies
 * absolute time and owns event cursors/resources. Replacing a plan does not
 * replay cues or advance a clock.
 */
public final class AnimationInstance implements AutoCloseable {

    public final String instanceId;
    public final long playbackGeneration;
    private final Thread owner = Thread.currentThread();
    private CompiledPlan current;
    private boolean closed;
    private boolean preparing;

    public AnimationInstance(String instanceId, long playbackGeneration, CompiledPlan initialPlan) {
        this.instanceId = AnimationV1.id(instanceId);
        AnimationV1.require(playbackGeneration >= 0 && initialPlan != null, "instance identity/plan");
        this.playbackGeneration = playbackGeneration;
        current = initialPlan;
    }

    public CompiledPlan plan() {
        active();
        return current;
    }

    public SampledFrame evaluate(EvaluationInput input) {
        active();
        return current.sample(input);
    }

    public void replace(CompiledPlan plan) {
        active();
        if (preparing) throw new IllegalStateException("animation replacement already preparing");
        if (plan == null) throw new IllegalArgumentException("plan");
        current = plan;
    }

    /**
     * Parse and compile before publishing. Reentrant replacement while reading is
     * rejected; close is allowed and prevents publication. The caller continues to
     * own the stream.
     */
    public void replaceFrom(InputStream input) throws IOException {
        active();
        if (preparing) throw new IllegalStateException("animation replacement already preparing");
        preparing = true;
        CompiledPlan next;
        try {
            next = CompiledPlan.compile(StrictJsonCodec.decode(input));
        } finally {
            preparing = false;
        }
        replace(next);
    }

    private void active() {
        ownerThread();
        if (closed) throw new IllegalStateException("animation instance closed");
    }

    private void ownerThread() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("animation instance owner thread");
    }

    @Override
    public void close() {
        ownerThread();
        closed = true;
        current = null;
    }
}
