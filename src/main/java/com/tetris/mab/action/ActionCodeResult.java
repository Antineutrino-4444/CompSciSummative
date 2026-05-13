package com.tetris.mab.action;

/** Result returned by {@link ActionCodeManager} mutation methods. */
public enum ActionCodeResult {
    NOT_ACTIVE,
    ADVANCED,
    SEQUENCE_COMPLETED,
    PENDING_CONFIRMATION,
    CONFIRMED,
    COMPLETED,
    FAILED_RESET,
    CANCELLED,
    IGNORED
}
