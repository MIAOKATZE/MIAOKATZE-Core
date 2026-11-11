// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.api.animation.runtime;

import com.miaokatze.miaokatzecore.api.animation.EpochKey;

/** Pure owner state. The host remains responsible for listener, input, audio and device cleanup. */
public final class SessionLifecycle implements AutoCloseable {

    public enum State {
        ACTIVE,
        SUSPENDED,
        CLOSED
    }

    private final Thread owner = Thread.currentThread();
    private final EpochKey epoch;
    private long resourceEpoch, documentRevision, jobGeneration;
    private State state = State.ACTIVE;

    public SessionLifecycle(EpochKey epoch) {
        if (epoch == null) throw new IllegalArgumentException("epoch");
        this.epoch = epoch;
    }

    public static final class Ticket {

        public final EpochKey epoch;
        public final long resourceEpoch, documentRevision, jobGeneration;
        private final SessionLifecycle source;

        private Ticket(SessionLifecycle source) {
            this.source = source;
            epoch = source.epoch;
            resourceEpoch = source.resourceEpoch;
            documentRevision = source.documentRevision;
            jobGeneration = source.jobGeneration;
        }
    }

    public Ticket ticket() {
        checkOpen();
        return new Ticket(this);
    }

    /** A suspended owner may accept pure cache results, but may not render. */
    public boolean canPublish(Ticket ticket) {
        check();
        return state != State.CLOSED && ticket != null
            && ticket.source == this
            && epoch.equals(ticket.epoch)
            && resourceEpoch == ticket.resourceEpoch
            && documentRevision == ticket.documentRevision
            && jobGeneration == ticket.jobGeneration;
    }

    public void changedDocument() {
        checkOpen();
        long revision = next(documentRevision), jobs = next(jobGeneration);
        documentRevision = revision;
        jobGeneration = jobs;
    }

    public void resourceReload() {
        checkOpen();
        long resources = next(resourceEpoch), jobs = next(jobGeneration);
        resourceEpoch = resources;
        jobGeneration = jobs;
    }

    public void suspend() {
        checkOpen();
        state = State.SUSPENDED;
    }

    public void resume() {
        checkOpen();
        state = State.ACTIVE;
    }

    public State state() {
        check();
        return state;
    }

    public boolean mayRender() {
        check();
        return state == State.ACTIVE;
    }

    @Override
    public void close() {
        check();
        state = State.CLOSED;
    }

    private static long next(long value) {
        if (value == Long.MAX_VALUE) throw new IllegalStateException("epoch exhausted; replace session");
        return value + 1;
    }

    private void check() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("owner thread required");
    }

    private void checkOpen() {
        check();
        if (state == State.CLOSED) throw new IllegalStateException("closed");
    }
}
