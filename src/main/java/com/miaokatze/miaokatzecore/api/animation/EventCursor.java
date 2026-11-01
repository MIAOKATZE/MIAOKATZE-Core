package com.miaokatze.miaokatzecore.api.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Bounded cue selection, NOT an audio/camera executor. No gameplay is
 * represented here.
 */
public final class EventCursor implements AutoCloseable {

    private final Thread owner = Thread.currentThread();
    public final EpochKey epoch;
    public final String instanceId;
    public final long playbackGeneration;
    private final AnimationV1.Document document;
    private final boolean loop;
    private final EventBudget localBudget, ownerBudget;
    private boolean started, closed;
    private long highWatermark = -1, lastFrame = -1, lastWall = -1;

    public EventCursor(EpochKey epoch, String instanceId, long generation, AnimationV1.Document document,
        boolean looping, EventBudget instanceBudget, EventBudget ownerBudget) {
        AnimationV1.require(epoch != null && document != null && generation >= 0, "cursor identity");
        AnimationV1.require(
            instanceBudget != null && ownerBudget != null && instanceBudget != ownerBudget,
            "distinct instance/owner budgets");
        this.epoch = epoch;
        this.instanceId = AnimationV1.id(instanceId);
        playbackGeneration = generation;
        this.document = document;
        loop = looping;
        localBudget = instanceBudget;
        this.ownerBudget = ownerBudget;
        if (loop) for (AnimationV1.Cue cue : document.cues)
            AnimationV1.require(cue.timeMs < document.durationMs, "loop cue must be in [0,duration)");
    }

    public static final class Occurrence {

        public final EpochKey epoch;
        public final String instanceId, cueId;
        public final long playbackGeneration, loopOccurrence, absoluteTimeMs;
        public final AnimationV1.Cue cue;

        private Occurrence(EventCursor owner, AnimationV1.Cue cue, long occurrence, long absoluteTime) {
            epoch = owner.epoch;
            instanceId = owner.instanceId;
            cueId = cue.id;
            playbackGeneration = owner.playbackGeneration;
            loopOccurrence = occurrence;
            absoluteTimeMs = absoluteTime;
            this.cue = cue;
        }
    }

    public static final class Dispatch {

        public final List<Occurrence> events;
        public final int scanned, expired, policyDropped, budgetDropped;

        private Dispatch(List<Occurrence> events, int scanned, int expired, int policyDropped, int budgetDropped) {
            this.events = Collections.unmodifiableList(new ArrayList<Occurrence>(events));
            this.scanned = scanned;
            this.expired = expired;
            this.policyDropped = policyDropped;
            this.budgetDropped = budgetDropped;
        }
    }

    public Dispatch start(long ageMs, boolean fresh, long frameId, long monotonicMs) {
        ownerThread();
        AnimationV1.require(!started && !closed, "already started/closed");
        AnimationV1.require(!fresh || ageMs == 0, "fresh start must be t=0");
        check(ageMs, frameId, monotonicMs);
        started = true;
        return collect(ageMs, frameId, monotonicMs, true, fresh);
    }

    public Dispatch advance(long ageMs, long frameId, long monotonicMs) {
        ownerThread();
        AnimationV1.require(started && !closed, "not active");
        check(ageMs, frameId, monotonicMs);
        // After a backward silent seek, cue side effects remain behind their high
        // watermark.
        if (ageMs <= highWatermark) return new Dispatch(Collections.<Occurrence>emptyList(), 0, 0, 0, 0);
        return collect(ageMs, frameId, monotonicMs, false, false);
    }

    public void seekSilently(long ageMs) {
        ownerThread();
        AnimationV1.require(started && !closed && ageMs >= 0 && ageMs <= AnimationV1.MAX_AGE_MS, "seek");
        highWatermark = Math.max(highWatermark, ageMs);
        // Host must reconcile persistent sound/camera handles separately; no stop cue
        // assumption.
    }

    private void check(long age, long frame, long wall) {
        AnimationV1.require(age >= 0 && age <= AnimationV1.MAX_AGE_MS, "age range");
        AnimationV1.require(
            frame >= lastFrame && frame >= 0 && wall >= lastWall && wall >= 0,
            "regressing presentation clock");
        // Validate both budget clocks before committing cursor state.
        localBudget.validateClock(frame, wall);
        ownerBudget.validateClock(frame, wall);
        localBudget.available(frame, wall);
        ownerBudget.available(frame, wall);
        lastFrame = frame;
        lastWall = wall;
    }

    private Dispatch collect(long age, long frame, long wall, boolean initial, boolean fresh) {
        List<Occurrence> candidates = new ArrayList<Occurrence>();
        int scanned = 0, expired = 0, policyDropped = 0;
        long previous = highWatermark;
        for (AnimationV1.Cue cue : document.cues) {
            scanned++;
            if (age < cue.timeMs) continue;
            long occurrence = loop ? (age - cue.timeMs) / document.durationMs : 0;
            long absolute = occurrence * document.durationMs + cue.timeMs;
            if (absolute <= previous) continue;
            if (age - absolute > cue.validForMs) {
                expired++;
                continue;
            }
            boolean discard = false;
            if (initial && !fresh && cue.latePolicy == AnimationV1.LatePolicy.DROP) discard = true;
            if (loop && cue.latePolicy == AnimationV1.LatePolicy.ONCE_IF_CURRENT
                && occurrence != age / document.durationMs) discard = true;
            if (loop && !initial
                && cue.latePolicy == AnimationV1.LatePolicy.DROP
                && age - previous >= document.durationMs) discard = true;
            if (discard) {
                policyDropped++;
                continue;
            }
            candidates.add(new Occurrence(this, cue, occurrence, absolute));
        }
        // O(cue-count log cue-count), never O(skipped loops). Stable ties do not depend
        // on import order.
        Collections.sort(candidates, new Comparator<Occurrence>() {

            public int compare(Occurrence a, Occurrence b) {
                int byTime = Long.compare(a.absoluteTimeMs, b.absoluteTimeMs);
                return byTime != 0 ? byTime : a.cueId.compareTo(b.cueId);
            }
        });
        List<Occurrence> delivered = new ArrayList<Occurrence>();
        int dropped = 0;
        for (Occurrence event : candidates) {
            if (localBudget.available(frame, wall) && ownerBudget.available(frame, wall)) {
                localBudget.consume(wall);
                ownerBudget.consume(wall);
                delivered.add(event);
            } else dropped++;
        }
        highWatermark = age; // Dropped occurrences are never queued for a later burst.
        return new Dispatch(delivered, scanned, expired, policyDropped, dropped);
    }

    private void ownerThread() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("event cursor owner thread");
    }

    @Override
    public void close() {
        ownerThread();
        closed = true;
    }
}
