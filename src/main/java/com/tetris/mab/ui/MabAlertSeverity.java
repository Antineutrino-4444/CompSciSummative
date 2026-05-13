package com.tetris.mab.ui;

/** Step 17 — alert severity for the player-facing HUD. */
public enum MabAlertSeverity {
    INFO("INFO"),
    SUCCESS("OK"),
    WARNING("WARN"),
    CRITICAL("CRIT");

    private final String shortTag;

    MabAlertSeverity(String shortTag) { this.shortTag = shortTag; }

    /** Compact bracketed tag used in HUD rows, e.g. "[CRIT]". */
    public String shortTag() { return shortTag; }
}
