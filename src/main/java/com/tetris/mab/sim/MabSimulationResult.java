package com.tetris.mab.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Step 14 — immutable result of a headless simulation run. */
public record MabSimulationResult(
        boolean success,
        String label,
        MabSimulationMode mode,
        int ticksRun,
        int totalEvents,
        int launchCount,
        int impactResolvedCount,
        int radarScanCount,
        int decoyActivatedCount,
        int civilDefenseActivatedCount,
        int upgradeAppliedCount,
        int aiDecisionExecutedCount,
        int aiDecisionSkippedCount,
        int garbageRowsApplied,
        int playerACharge,
        int playerBCharge,
        int playerASiloIntegrity,
        int playerBSiloIntegrity,
        String failureReason,
        List<String> invariantFailures,
        List<String> recentEvents,
        String summary) {

    public MabSimulationResult {
        if (label == null) label = mode == null ? "" : mode.name();
        if (failureReason == null) failureReason = "";
        if (summary == null) summary = "";
        invariantFailures = (invariantFailures == null)
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(invariantFailures));
        recentEvents = (recentEvents == null)
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(recentEvents));
    }
}
