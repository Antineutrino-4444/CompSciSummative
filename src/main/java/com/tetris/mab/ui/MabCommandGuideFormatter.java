package com.tetris.mab.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Step 17 — formats {@link MabCommandGuideEntry}s for display in the
 * HUD and command-guide window.
 */
public final class MabCommandGuideFormatter {

    private MabCommandGuideFormatter() {}

    /** Compact one-line-per-entry text suitable for the HUD section. */
    public static String formatCompactGuide(List<MabCommandGuideEntry> entries) {
        if (entries == null || entries.isEmpty()) return "(no commands)";
        StringBuilder sb = new StringBuilder();
        for (MabCommandGuideEntry e : entries) {
            sb.append(String.format("%-22s %-12s %s%n",
                    truncate(e.displayName(), 22),
                    truncate(e.codeText(), 12),
                    e.availabilityText()));
        }
        return sb.toString();
    }

    /** Full multi-section guide grouped by category. */
    public static String formatFullGuide(List<MabCommandGuideEntry> entries) {
        if (entries == null || entries.isEmpty()) return "(no commands)";
        StringBuilder sb = new StringBuilder();
        sb.append("Strategic commands are entered by clearing the listed line counts in order.\n");
        sb.append("Spins may replace ordinary line clears where the action-code system permits;\n");
        sb.append("anchored tokens (A1..A4) and confirmation tokens (C4) cannot be replaced.\n");
        sb.append("Token legend:  N = normal N-line clear (spin allowed)\n");
        sb.append("               AN = anchored N-line clear (no spin)\n");
        sb.append("               C4 = hard 4-line confirmation (no spin)\n\n");

        Map<String, java.util.List<MabCommandGuideEntry>> byCat = new LinkedHashMap<>();
        for (MabCommandGuideEntry e : entries) {
            byCat.computeIfAbsent(e.category(), k -> new java.util.ArrayList<>()).add(e);
        }
        for (var entry : byCat.entrySet()) {
            sb.append("=== ").append(entry.getKey()).append(" ===\n");
            for (MabCommandGuideEntry e : entry.getValue()) {
                sb.append(formatEntry(e)).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** Multi-line block for a single entry. */
    public static String formatEntry(MabCommandGuideEntry entry) {
        if (entry == null) return "(none)";
        StringBuilder sb = new StringBuilder();
        sb.append(entry.displayName()).append('\n');
        sb.append("  Code        : ").append(entry.codeText()).append('\n');
        sb.append("  Availability: ").append(entry.availabilityText()).append('\n');
        if (!entry.description().isEmpty()) {
            sb.append("  Notes       : ").append(entry.description()).append('\n');
        }
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 1)) + "\u2026";
    }
}
