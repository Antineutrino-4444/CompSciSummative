package com.tetris.mab.intercept;

/** Outcome of {@link InterceptResolver#resolveIntercept}. */
public enum InterceptOutcome {
    FULLY_INTERCEPTED,
    PARTIALLY_INTERCEPTED,
    FAILED_NO_THREAT,
    FAILED_THREAT_NOT_ACTIVE,
    FAILED_TOO_LATE,
    FAILED_ALREADY_INTERCEPTED,
    FAILED_INVALID_TARGET,
    FAILED_ERROR
}
