package com.miaokatze.miaokatzecore.api.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Small lifecycle-independent engine; package visibility keeps execution out of the consumer API. */
final class RecipeRegistrationEngine {

    private enum State {
        OPEN,
        RUNNING,
        COMPLETE,
        FAILED
    }

    private final List<String> ids = new ArrayList<>();
    private final List<Entry> entries = new ArrayList<>();
    private State state = State.OPEN;

    synchronized void register(String id, Runnable recipes, Runnable derived) {
        if (state != State.OPEN)
            throw new IllegalStateException("Recipe registration is closed (" + state + "): " + id);
        if (id == null || !id.matches("[a-z0-9_.-]+"))
            throw new IllegalArgumentException("Invalid recipe registration ID: " + id);
        Objects.requireNonNull(recipes, "recipes");
        if (ids.contains(id)) throw new IllegalArgumentException("Duplicate recipe registration ID: " + id);
        ids.add(id);
        entries.add(new Entry(id, recipes, derived));
    }

    synchronized boolean isComplete() {
        return state == State.COMPLETE;
    }

    synchronized List<String> registeredIds() {
        return Collections.unmodifiableList(new ArrayList<>(ids));
    }

    void run(Runnable finalizeRecipes) {
        run(null, null, finalizeRecipes);
    }

    /** Fixed internal GT stages; the lifecycle-independent entry above remains GT-free. */
    void run(Runnable beforeMain, Runnable gtDerived, Runnable finalizeRecipes) {
        List<Entry> batch;
        synchronized (this) {
            if (state != State.OPEN) return;
            state = State.RUNNING;
            batch = new ArrayList<>(entries);
            entries.clear();
        }
        boolean complete = false;
        try {
            // External callbacks do not hold the monitor: diagnostics remain readable from worker threads.
            if (!batch.isEmpty() && beforeMain != null) execute("miaokatzecore", "gt-before-main", beforeMain);
            for (Entry entry : batch) execute(entry.id, "recipes", entry.recipes);
            if (!batch.isEmpty() && gtDerived != null) execute("miaokatzecore", "gt-derived", gtDerived);
            for (Entry entry : batch) if (entry.derived != null) execute(entry.id, "derived", entry.derived);
            if (!batch.isEmpty()) execute("miaokatzecore", "finalize", finalizeRecipes);
            complete = true;
        } finally {
            synchronized (this) {
                state = complete ? State.COMPLETE : State.FAILED;
            }
        }
    }

    private static void execute(String id, String phase, Runnable callback) {
        try {
            callback.run();
        } catch (Throwable failure) {
            throw new IllegalStateException("Recipe registration failed: id=" + id + ", phase=" + phase, failure);
        }
    }

    private static final class Entry {

        private final String id;
        private final Runnable recipes, derived;

        private Entry(String id, Runnable recipes, Runnable derived) {
            this.id = id;
            this.recipes = recipes;
            this.derived = derived;
        }
    }
}
