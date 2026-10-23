package com.miaokatze.miaokatzecore.api.collection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Pure, bounded most-recent-first ordering. Consumers own identity, persistence and authorization.
 * The returned list is immutable; elements are retained by reference and the input list is never modified.
 *
 * @since 0.1.15
 */
public final class BoundedMru {

    private BoundedMru() {}

    /**
     * Moves the supplied entry to the front, removes older equivalent entries and truncates to a positive limit.
     * Existing duplicates also collapse, preserving the first occurrence. Identity must be an equivalence relation.
     */
    public static <T> List<T> moveToFront(List<T> entries, T entry, int limit,
        BiPredicate<? super T, ? super T> sameIdentity) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(sameIdentity, "sameIdentity");
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
        List<T> result = new ArrayList<>(Math.min(limit, 32));
        result.add(entry);
        for (T candidate : entries) {
            if (result.size() == limit) break;
            Objects.requireNonNull(candidate, "history entry");
            boolean duplicate = false;
            for (T accepted : result) if (sameIdentity.test(candidate, accepted)) {
                duplicate = true;
                break;
            }
            if (!duplicate) result.add(candidate);
        }
        return Collections.unmodifiableList(result);
    }
}
