package com.miaokatze.miaokatzecore.api.recipe;

import java.util.List;

/**
 * Process-wide recipe registration. Register callbacks during mod loading; registration never executes them.
 * All main callbacks run in registration order before any derived callbacks, also in registration order.
 * Callbacks run synchronously once per JVM, not once per world. Item, block and machine registration stays in
 * the consumer's existing early lifecycle. IDs are stable consumer IDs such as {@code gtit}.
 *
 * <p>
 * Duplicate IDs and submissions after execution begins are errors. A failed callback stops the pipeline;
 * partial recipe changes are not rolled back or retried. The public contract deliberately exposes no physical
 * loading event, so Core can adjust scheduling independently of consumers.
 *
 * @since 0.1.27
 */
public final class RecipeRegistration {

    static final RecipeRegistrationEngine ENGINE = new RecipeRegistrationEngine();

    private RecipeRegistration() {}

    public static void register(String id, Runnable recipes) {
        register(id, recipes, null);
    }

    /** The optional derived callback may be null. The main callback must not be null. */
    public static void register(String id, Runnable recipes, Runnable derived) {
        ENGINE.register(id, recipes, derived);
    }

    /** True only after the entire pipeline, including successful finalization, has completed. */
    public static boolean isComplete() {
        return ENGINE.isComplete();
    }

    /** Immutable registration-order snapshot, retained after success or failure for diagnostics. */
    public static List<String> registeredIds() {
        return ENGINE.registeredIds();
    }
}
