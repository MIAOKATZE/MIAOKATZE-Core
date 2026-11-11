// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.api.animation;

/**
 * Absolute local time in integer milliseconds; negative times clamp to the
 * first pose.
 */
public final class EvaluationInput {

    public final long localTimeMs;

    public EvaluationInput(long localTimeMs) {
        this.localTimeMs = localTimeMs;
    }
}
