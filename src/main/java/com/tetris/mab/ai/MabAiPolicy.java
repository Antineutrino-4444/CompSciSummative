package com.tetris.mab.ai;

import com.tetris.mab.ActiveLaunchState;
import com.tetris.mab.IncomingThreatState;
import com.tetris.mab.MatchPhase;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.NukeBuildState;
import com.tetris.mab.ParticipantState;
import com.tetris.mab.balance.MabBalanceProfile;
import com.tetris.mab.balance.MabBalanceProfiles;
import com.tetris.mab.launch.LaunchPhase;
import com.tetris.mab.launch.ThreatStatus;
import com.tetris.mab.nuke.NukeDesign;

/**
 * Strategic decision policy for the MAB AI.
 *
 * <p>Rebuilt from first principles. Inputs are:
 * <ul>
 *   <li>Charge progress vs the design's effective build-charge requirement
 *       at the current DEFCON.</li>
 *   <li>Current Nuke Builder design (doctrine → launch tempo, intercept
 *       difficulty, route requirements).</li>
 *   <li>Incoming threats: status, pieces remaining, intercept window.</li>
 *   <li>Civil-defense charge availability, silo integrity, radiation
 *       pressure on the AI's own board, EMP-induced charge drain.</li>
 *   <li>Upgrade points and the difficulty-derived appetite for spending
 *       them.</li>
 * </ul>
 *
 * <p>The current MAB design has no route-scan, impact-delay readout, or feint
 * systems — defense is exclusively spin-intercept and civil-defense, so
 * this policy never emits ROUTE_SCAN or FEINT decisions.
 *
 * <p>The policy emits a single {@link MabAiDecision} per tick using a
 * priority ladder that values surviving an imminent impact above
 * everything else, then defending the silo, then preserving and
 * deploying nuke charge along the design's tempo profile.
 */
public final class MabAiPolicy {

    public MabAiDecision chooseDecision(MabAiState ai,
                                         MutuallyAssuredBlocksMatch match,
                                         ParticipantState self,
                                         ParticipantState opponent) {
        if (ai == null || !ai.isEnabled()) {
            return MabAiDecision.none(ai == null ? null : ai.getParticipantId(),
                    "ai disabled");
        }
        if (match == null || self == null) {
            return MabAiDecision.none(ai.getParticipantId(), "null match/self");
        }
        if (match.getCurrentPhase() != MatchPhase.ACTIVE
                || match.isPaused()
                || match.getWinner() != null) {
            return MabAiDecision.none(ai.getParticipantId(),
                    "phase=" + match.getCurrentPhase());
        }

        MabBalanceProfile profile = profile(ai);
        NukeDesign design = self.getNukeBuildState().getCurrentDesign();
        int defcon = match.getDefconState() == null ? 5
                : match.getDefconState().getLevel();
        int tier = ai.getDifficulty().tier();

        // ── 1. Resolve any IMPACT_READY threats immediately ────────────
        if (hasImpactReady(self)) {
            return MabAiDecision.executed(MabAiDecisionType.RESOLVE_IMPACT,
                    ai.getParticipantId(), "impact-ready", "resolving impacts");
        }

        // ── 2. Civil-defense for imminent inbound impacts ──────────────
        if (ai.getDefenseCooldownPieces() == 0
                && self.getCivilDefenseState().getActiveCharges() == 0
                && shouldRaiseShield(self, design, tier, profile, ai)) {
            return MabAiDecision.executed(MabAiDecisionType.CIVIL_DEFENSE,
                    ai.getParticipantId(), "impact-window",
                    "raising shield against incoming");
        }

        // ── 3. Upgrade spend (tier >= MEDIUM) ──────────────────────────
        if (ai.getUpgradeCooldownPieces() == 0
                && self.getUpgradeState().getUpgradePoints() > 0
                && ai.getAiTicks() >= profile.getAiMinimumTicksBeforeUpgrade()
                && tier >= MabAiDifficulty.MEDIUM.tier()) {
            return MabAiDecision.executed(MabAiDecisionType.OPEN_UPGRADE_PAUSE,
                    ai.getParticipantId(), "have-points",
                    "spending " + self.getUpgradeState().getUpgradePoints() + " pts");
        }

        // ── 4. Arm and push toward launch when charge is ready ─────────
        NukeBuildState nb = self.getNukeBuildState();
        int needed = Math.max(0,
                nb.getEffectiveBuildChargeRequired() - nb.getCurrentBuildCharge());
        if (!nb.isArmed()) {
            int budget = ai.getChargeBudget();
            if (needed > 0 && budget >= needed) {
                return MabAiDecision.executed(MabAiDecisionType.ARM_NUKE,
                        ai.getParticipantId(), nb.getCurrentDesign().getId(),
                        "topping off to armed");
            }
            int step = chargeStep(ai);
            if (budget >= step && step > 0) {
                return MabAiDecision.executed(MabAiDecisionType.ADD_CHARGE,
                        ai.getParticipantId(), Integer.toString(step),
                        "adding " + step + " charge");
            }
        }

        // ── 5. Always-on charge nibble (tempo) ─────────────────────────
        int step = chargeStep(ai);
        if (ai.getChargeBudget() >= step && step > 0) {
            return MabAiDecision.executed(MabAiDecisionType.ADD_CHARGE,
                    ai.getParticipantId(), Integer.toString(step),
                    "topping nuke charge");
        }
        return MabAiDecision.none(ai.getParticipantId(), "nothing actionable");
    }

    private static boolean hasImpactReady(ParticipantState self) {
        for (ActiveLaunchState l : self.getActiveLaunches()) {
            if (l.getPhase() == LaunchPhase.IMPACT_READY) return true;
        }
        for (IncomingThreatState t : self.getIncomingThreats()) {
            if (t.getStatus() == ThreatStatus.IMPACT_READY) return true;
        }
        return false;
    }

    private static boolean shouldRaiseShield(ParticipantState self, NukeDesign design,
                                              int tier, MabBalanceProfile profile,
                                              MabAiState ai) {
        if (ai.getAiTicks() < profile.getAiMinimumTicksBeforeCivilDefense()) {
            return hasImpactReady(self);
        }
        // Pick the earliest WARNING_ACTIVE threat and decide based on
        // pieces remaining and its rated severity.
        int earliest = Integer.MAX_VALUE;
        for (IncomingThreatState t : self.getIncomingThreats()) {
            if (t.getStatus() != ThreatStatus.WARNING_ACTIVE) continue;
            earliest = Math.min(earliest, t.getWarningPiecesRemaining());
        }
        if (earliest == Integer.MAX_VALUE) return false;
        // EASY: only react when very close (≤2 pieces). MASTER reacts
        // sooner so the shield is up before the missile resolves.
        int threshold = switch (tier) {
            case 0 -> 1;
            case 1 -> 2;
            case 2 -> 3;
            case 3 -> 4;
            default -> 5;
        };
        return earliest <= threshold;
    }

    public static int chargeStep(MabAiState ai) {
        return profile(ai).addChargeStepFor(ai.getDifficulty());
    }

    public static int chargeBudgetGainPerSimulatedPiece(MabAiState ai) {
        return profile(ai).chargeGainPerTickFor(ai.getDifficulty());
    }

    public static int chargeBudgetGainPerSimulatedPiece(MabAiDifficulty difficulty) {
        return MabBalanceProfiles.standardPve().chargeGainPerTickFor(difficulty);
    }

    private static MabBalanceProfile profile(MabAiState ai) {
        MabBalanceProfile p = ai.getBalanceProfile();
        return p == null ? MabBalanceProfiles.standardPve() : p;
    }
}
