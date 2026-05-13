package com.tetris.mab.ui;

import java.util.List;

/** Step 17 — formats {@link MabAlert} rows for the HUD's Alerts section. */
public final class MabAlertFormatter {

    private MabAlertFormatter() {}

    /**
     * Returns up to {@code maxRows} alerts, joined with newlines, in
     * the order received (callers should pre-sort newest-first).
     */
    public static String formatTopRows(List<MabAlert> alerts, int maxRows) {
        if (alerts == null || alerts.isEmpty()) return "(no alerts)";
        StringBuilder sb = new StringBuilder();
        int n = Math.min(maxRows, alerts.size());
        for (int i = 0; i < n; i++) {
            MabAlert a = alerts.get(i);
            sb.append('[').append(a.severity().shortTag()).append("] ")
                    .append(a.title());
            if (!a.message().isEmpty()) {
                sb.append(" — ").append(a.message());
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
