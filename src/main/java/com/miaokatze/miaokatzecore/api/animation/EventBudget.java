// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.api.animation;

/**
 * Owner-thread-only exact rolling-second gate; share owner gate between all its
 * cursors.
 */
public final class EventBudget {

    private final Thread owner = Thread.currentThread();
    public final int maxPerFrame, maxPerSecond;
    private final long[] times;
    private int head, count, frameCount;
    private long frame = -1, lastTime = -1;

    public EventBudget(int maxPerFrame, int maxPerSecond) {
        AnimationV1.require(
            maxPerFrame >= 1 && maxPerFrame <= 128 && maxPerSecond >= 1 && maxPerSecond <= 4096,
            "event budget");
        this.maxPerFrame = maxPerFrame;
        this.maxPerSecond = maxPerSecond;
        times = new long[maxPerSecond];
    }

    void validateClock(long frameId, long monotonicMs) {
        if (Thread.currentThread() != owner) throw new IllegalStateException("event budget owner thread");
        AnimationV1.require(
            frameId >= frame && frameId >= 0 && monotonicMs >= lastTime && monotonicMs >= 0,
            "regressing budget clock/frame");
    }

    boolean available(long frameId, long monotonicMs) {
        validateClock(frameId, monotonicMs);
        if (frameId != frame) {
            frame = frameId;
            frameCount = 0;
        }
        lastTime = monotonicMs;
        while (count > 0 && monotonicMs - times[head] >= 1000) {
            head = (head + 1) % times.length;
            count--;
        }
        return frameCount < maxPerFrame && count < maxPerSecond;
    }

    void consume(long time) {
        times[(head + count) % times.length] = time;
        count++;
        frameCount++;
    }
}
