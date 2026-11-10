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
