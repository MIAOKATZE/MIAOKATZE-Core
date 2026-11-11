// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.api.testlab;

/** Assertion outcome. Report persistence and cleanup remain separate observations. */
public enum LabResult {
    PASS,
    FAIL,
    BLOCKED,
    SKIP,
    UNSUPPORTED,
    CANCELLED,
    TIMEOUT,
    RECOVERY_PENDING
}
