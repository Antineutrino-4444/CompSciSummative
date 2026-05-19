package com.tetris.mab.balance;

import com.tetris.mab.ai.MabAiDifficulty;
import com.tetris.mab.sim.MabSimulationResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Step 19 — small reporter that summarises a {@link MabSimulationResult}
 * under a specific {@link MabBalanceProfile} into a compact, readable
 * {@link MabBalanceReport}.
 */
public final class MabBalanceReporter {

    private MabBalanceReporter() {}

    public static MabBalanceReport fromSimulation(MabSimulationResult result, MabBalanceProfile profile) {
        if (result == null) throw new IllegalArgumentException("result");
        if (profile == null) profile = MabBalanceProfiles.standardPve();
        List<String> notes = new ArrayList<>();
        if (result.invariantFailures() != null) {
            for (String f : result.invariantFailures()) notes.add("invariant: " + f);
        }
        if (!result.failureReason().isEmpty()) {
            notes.add("failure: " + result.failureReason());
        }
        return new MabBalanceReport(
                profile.getId(),
                profile.getDisplayName(),
                result.ticksRun(),
                result.launchCount(),
                result.impactResolvedCount(),
                result.routeScanCount(),
                result.feintActivatedCount(),
                result.civilDefenseActivatedCount(),
                result.upgradeAppliedCount(),
                result.aiDecisionExecutedCount(),
                result.aiDecisionSkippedCount(),
                result.playerACharge(),
                result.playerBCharge(),
                result.playerASiloIntegrity(),
                result.playerBSiloIntegrity(),
                result.invariantFailures() == null ? 0 : result.invariantFailures().size(),
                notes,
                result.summary());
    }

    public static String formatProfile(MabBalanceProfile p) {
        if (p == null) return "(null profile)";
        StringBuilder sb = new StringBuilder();
        sb.append("Profile: ").append(p.getDisplayName())
          .append(" [").append(p.getId()).append("]\n");
        if (!p.getDescription().isEmpty()) {
            sb.append("  ").append(p.getDescription()).append('\n');
        }
        sb.append("  earlyLaunchDelay=").append(p.getAiEarlyLaunchDelayTicks())
          .append("  minFeint=").append(p.getAiMinimumTicksBeforeFeint())
          .append("  minUpgrade=").append(p.getAiMinimumTicksBeforeUpgrade())
          .append("  minCivDef=").append(p.getAiMinimumTicksBeforeCivilDefense()).append('\n');
        sb.append("  maxLaunches: <80=").append(p.getMaxAiLaunchesBeforeTick80())
          .append(" <160=").append(p.getMaxAiLaunchesBeforeTick160()).append('\n');
        for (MabAiDifficulty d : MabAiDifficulty.values()) {
            sb.append(String.format("  %-6s gain/tick=%d step=%d cd[L=%d R=%d F=%d S=%d U=%d]%n",
                    d.name(),
                    p.chargeGainPerTickFor(d),
                    p.addChargeStepFor(d),
                    p.launchCooldownFor(d),
                    p.routeScanCooldownFor(d),
                    p.feintCooldownFor(d),
                    p.defenseCooldownFor(d),
                    p.upgradeCooldownFor(d)));
        }
        return sb.toString();
    }

    public static String formatReport(MabBalanceReport r) {
        if (r == null) return "(null report)";
        StringBuilder sb = new StringBuilder();
        sb.append("--- Balance Report: ").append(r.profileName())
          .append(" [").append(r.profileId()).append("] ---\n");
        sb.append("ticks=").append(r.ticks())
          .append("  launches=").append(r.launchCount())
          .append("  impacts=").append(r.impactCount())
          .append("  routeScans=").append(r.routeScanCount())
          .append("  feints=").append(r.feintCount())
          .append("  civDef=").append(r.civilDefenseCount())
          .append("  upgrades=").append(r.upgradeCount())
          .append('\n');
        sb.append("aiExec=").append(r.aiDecisionExecutedCount())
          .append("  aiSkip=").append(r.aiDecisionSkippedCount())
          .append("  invariantFailures=").append(r.invariantFailureCount())
          .append('\n');
        sb.append("state: A charge=").append(r.playerACharge())
          .append(" silo=").append(r.playerASiloIntegrity())
          .append(" | B charge=").append(r.playerBCharge())
          .append(" silo=").append(r.playerBSiloIntegrity())
          .append('\n');
        if (!r.notes().isEmpty()) {
            sb.append("notes:\n");
            for (String n : r.notes()) sb.append("  - ").append(n).append('\n');
        }
        return sb.toString();
    }
}
