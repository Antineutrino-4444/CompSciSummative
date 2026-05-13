package com.tetris.mab.balance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Step 19 — immutable balance observation report from a single
 * headless simulation run under a particular {@link MabBalanceProfile}.
 */
public record MabBalanceReport(
        String profileId,
        String profileName,
        int ticks,
        int launchCount,
        int impactCount,
        int radarScanCount,
        int decoyCount,
        int civilDefenseCount,
        int upgradeCount,
        int aiDecisionExecutedCount,
        int aiDecisionSkippedCount,
        int playerACharge,
        int playerBCharge,
        int playerASiloIntegrity,
        int playerBSiloIntegrity,
        int invariantFailureCount,
        List<String> notes,
        String summary) {

    public MabBalanceReport {
        if (profileId == null) profileId = "";
        if (profileName == null) profileName = "";
        if (summary == null) summary = "";
        notes = (notes == null) ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(notes));
    }
}
