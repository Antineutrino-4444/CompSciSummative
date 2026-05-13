package com.tetris.mab.ai;

import com.tetris.mab.ActiveLaunchState;
import com.tetris.mab.IncomingThreatState;
import com.tetris.mab.MatchPhase;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.NukeBuildState;
import com.tetris.mab.ParticipantState;
import com.tetris.mab.balance.MabBalanceProfile;
import com.tetris.mab.balance.MabBalanceProfiles;
import com.tetris.mab.decoy.DecoyType;
import com.tetris.mab.launch.LaunchPhase;
import com.tetris.mab.launch.ThreatStatus;

/**
 * Step 13 — deterministic decision policy for the offline PvE AI.
 *
 * <p>Pure function: given the current AI state, the match, and both
 * participants, returns the {@link MabAiDecision} the driver should
 * try next. Does not mutate anything.
 */
public final class MabAiPolicy {

    public MabAiDecision chooseDecision(MabAiState ai,
                                         MutuallyAssuredBlocksMatch match,
                                         ParticipantState self,
                                         ParticipantState opponent) {
        if (ai == null || !ai.isEnabled()) {
            return MabAiDecision.none(ai == null ? null : ai.getParticipantId(), "ai disabled");
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

        // 3. Resolve impact-ready first.
        if (hasImpactReady(self)) {
            return MabAiDecision.executed(MabAiDecisionType.RESOLVE_IMPACT,
                    ai.getParticipantId(), "impact-ready", "resolving impacts");
        }

        // 4. Defensive: civil defence on incoming WARNING_ACTIVE threat.
        if (ai.getDefenseCooldownPieces() == 0 && hasActiveWarningThreat(self)
                && self.getCivilDefenseState().getActiveCharges() == 0
                && (ai.getAiTicks() >= profile(ai).getAiMinimumTicksBeforeCivilDefense()
                        || hasImpactReady(self))) {
            return MabAiDecision.executed(MabAiDecisionType.CIVIL_DEFENSE,
                    ai.getParticipantId(), "warning-active",
                    "shielding against incoming threat");
        }

        // 5. Radar pressure: scan if opponent has decoys or our intel is stale.
        if (ai.getRadarCooldownPieces() == 0
                && (opponent != null && (opponent.getActiveDecoyCount() > 0
                        || self.getRadarIntel().isEnemyIntelStale()))) {
            return MabAiDecision.executed(MabAiDecisionType.RADAR_SCAN,
                    ai.getParticipantId(), "intel-stale-or-decoyed",
                    "radar scan against opponent");
        }

        // 6/7. Offensive build support for legacy headless AI.
        // Live PvE launches are fired only by the simplified clear router,
        // after charge is ready and the same route pips as the player are
        // satisfied. Do not start a launch here just because the nuke is
        // armed; that bypasses the 4-Tetris / 2-spin player rule.
        NukeBuildState nb = self.getNukeBuildState();
        if (!nb.isArmed()) {
            int needed = Math.max(0, nb.getEffectiveBuildChargeRequired() - nb.getCurrentBuildCharge());
            int budget = ai.getChargeBudget();
            if (needed > 0 && budget >= needed) {
                return MabAiDecision.executed(MabAiDecisionType.ARM_NUKE,
                        ai.getParticipantId(), nb.getCurrentDesign().getId(),
                        "topping off to armed");
            }
            if (budget >= chargeStep(ai)) {
                return MabAiDecision.executed(MabAiDecisionType.ADD_CHARGE,
                        ai.getParticipantId(), Integer.toString(chargeStep(ai)),
                        "adding " + chargeStep(ai) + " charge");
            }
        }

        // 8. Decoy if archetype favours deception.
        if (ai.getDecoyCooldownPieces() == 0 && favorsDecoy(ai.getArchetype())
                && ai.getAiTicks() >= profile(ai).getAiMinimumTicksBeforeDecoy()) {
            DecoyType dt = decoyChoice(ai.getArchetype());
            return MabAiDecision.executed(MabAiDecisionType.DECOY,
                    ai.getParticipantId(), dt.name(),
                    "deploying " + dt + " decoy");
        }

        // 9. Upgrade attempt — only if cooldown ready and points available.
        if (ai.getUpgradeCooldownPieces() == 0
                && self.getUpgradeState().getUpgradePoints() > 0
                && ai.getAiTicks() >= profile(ai).getAiMinimumTicksBeforeUpgrade()) {
            return MabAiDecision.executed(MabAiDecisionType.OPEN_UPGRADE_PAUSE,
                    ai.getParticipantId(), "have-points",
                    "opening upgrade pause to spend " + self.getUpgradeState().getUpgradePoints() + " pts");
        }

        // 10. Otherwise add a small charge if we can.
        if (ai.getChargeBudget() >= chargeStep(ai)) {
            return MabAiDecision.executed(MabAiDecisionType.ADD_CHARGE,
                    ai.getParticipantId(), Integer.toString(chargeStep(ai)),
                    "topping nuke charge");
        }
        return MabAiDecision.none(ai.getParticipantId(), "nothing actionable");
    }

    /** Step size of an ADD_CHARGE decision, sourced from the balance profile. */
    public static int chargeStep(MabAiState ai) {
        return profile(ai).addChargeStepFor(ai.getDifficulty());
    }

    /** Per-tick simulated charge gain credited to the AI's budget, profile-aware. */
    public static int chargeBudgetGainPerSimulatedPiece(MabAiState ai) {
        return profile(ai).chargeGainPerTickFor(ai.getDifficulty());
    }

    /** Legacy difficulty-only fallback retained for backward compatibility. */
    public static int chargeBudgetGainPerSimulatedPiece(MabAiDifficulty difficulty) {
        return MabBalanceProfiles.standardPve().chargeGainPerTickFor(difficulty);
    }

    private static MabBalanceProfile profile(MabAiState ai) {
        MabBalanceProfile p = ai.getBalanceProfile();
        return p == null ? MabBalanceProfiles.standardPve() : p;
    }

    /** Decoy chosen for a given archetype. */
    public static DecoyType decoyChoice(MabAiArchetype archetype) {
        return switch (archetype) {
            case TACTICAL_SPAMMER     -> DecoyType.DECOY_LAUNCH;
            case DIRTY_BOMBER         -> DecoyType.FALSE_DOCTRINE_SIGNAL;
            case CONCRETE_STRATEGIST  -> DecoyType.DUMMY_SILO_HEAT;
            case MAD_DEFENDER         -> DecoyType.DECOY_LAUNCH;
            case MIRV_CONTROLLER      -> DecoyType.GHOST_MIRV;
            case DOOMSDAY_HOARDER     -> DecoyType.FALSE_DOCTRINE_SIGNAL;
            case BALANCED             -> DecoyType.DECOY_LAUNCH;
        };
    }

    private static boolean favorsDecoy(MabAiArchetype a) {
        return a == MabAiArchetype.DIRTY_BOMBER
            || a == MabAiArchetype.CONCRETE_STRATEGIST
            || a == MabAiArchetype.MIRV_CONTROLLER
            || a == MabAiArchetype.MAD_DEFENDER
            || a == MabAiArchetype.TACTICAL_SPAMMER
            || a == MabAiArchetype.BALANCED
            || a == MabAiArchetype.DOOMSDAY_HOARDER;
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

    private static boolean hasActiveWarningThreat(ParticipantState self) {
        for (IncomingThreatState t : self.getIncomingThreats()) {
            if (t.getStatus() == ThreatStatus.WARNING_ACTIVE) return true;
        }
        return false;
    }
}
