package com.tetris.mab.ui;

import java.util.Objects;

/**
 * Step 17 — one player-facing alert displayed in the HUD's Alerts
 * section. Built from event log entries or snapshot state by
 * {@link MabAlertModel}.
 */
public record MabAlert(long eventSequence,
                       MabAlertSeverity severity,
                       String title,
                       String message) {

    public MabAlert {
        Objects.requireNonNull(severity, "severity");
        title = title == null ? "" : title;
        message = message == null ? "" : message;
    }
}
