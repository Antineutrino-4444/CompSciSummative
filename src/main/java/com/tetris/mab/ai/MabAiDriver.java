package com.tetris.mab.ai;

import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ParticipantState;
import com.tetris.mab.balance.MabBalanceProfile;
import com.tetris.mab.balance.MabBalanceProfiles;
import com.tetris.mab.upgrade.UpgradeApplicationResult;
import com.tetris.mab.upgrade.UpgradeChoiceSet;
import com.tetris.mab.upgrade.UpgradeDefinition;
import com.tetris.mab.upgrade.UpgradeType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Offline strategic AI driver for the MAB layer.
 *
 * <p>From-first-principles rewrite. The driver ticks once per strategic
 * heartbeat (typically from {@link com.tetris.mab.ui.MabPlayerFacingController}),
 * lets {@link MabAiPolicy} pick a single decision, then executes it
 * against the match's public APIs (local-only, no rollback).
 *
 * <p>The decision menu spans civil-defense activation, charge management,
 * arming, upgrade drafting, and (for higher difficulty tiers) spin-style
 * intercepts of incoming threats. The current MAB design has no route-scan
 * or feint systems, so those decision branches are intentionally absent.
 */
public final class MabAiDriver {

    private final MutuallyAssuredBlocksMatch match;
    private final ParticipantId aiId;
    private final MabAiState state;
    private final MabAiPolicy policy;

    private boolean advanceHiddenClock = true;

    public MabAiDriver(MutuallyAssuredBlocksMatch match,
                       ParticipantId aiId,
                       MabAiArchetype archetype,
                       MabAiDifficulty difficulty) {
        this(match, aiId, archetype, difficulty, MabBalanceProfiles.standardPve());
    }

    public MabAiDriver(MutuallyAssuredBlocksMatch match,
                       ParticipantId aiId,
                       MabAiArchetype archetype,
                       MabAiDifficulty difficulty,
                       MabBalanceProfile balanceProfile) {
        if (match == null) throw new IllegalArgumentException("match");
        if (aiId == null) throw new IllegalArgumentException("aiId");
        this.match = match;
        this.aiId = aiId;
        this.state = new MabAiState(aiId, archetype, difficulty);
        this.state.setBalanceProfile(balanceProfile == null
                ? MabBalanceProfiles.standardPve() : balanceProfile);
        this.policy = new MabAiPolicy();
    }

    public MabAiState getState() { return state; }
    public MabAiPolicy getPolicy() { return policy; }
    public ParticipantId getParticipantId() { return aiId; }
    public boolean isEnabled() { return state.isEnabled(); }
    public void setAdvanceHiddenClock(boolean v) { this.advanceHiddenClock = v; }

    public void setEnabled(boolean enabled) {
        if (enabled == state.isEnabled()) return;
        if (enabled) {
            state.enable();
            log("AI_ENABLED", "ai enabled",
                    metaOf("archetype", state.getArchetype(),
                            "difficulty", state.getDifficulty()));
        } else {
            state.disable();
            log("AI_DISABLED", "ai disabled", metaOf());
        }
    }

    public MabAiDecision tick() {
        if (!state.isEnabled()) {
            return MabAiDecision.none(aiId, "ai disabled");
        }
        state.incrementAiTicks();
        state.tickCooldowns();

        if (advanceHiddenClock) {
            int applied = match.debugAdvanceStrategicClockOnly(aiId, 1);
            if (applied > 0) {
                state.addSimulatedPiece();
                state.addChargeBudget(MabAiPolicy.chargeBudgetGainPerSimulatedPiece(state));
            }
        }

        // Intercepts: the AI never calls the debug intercept resolver.
        // Spin-intercept is the live MAB defense and requires the board
        // AI to actually clear a T-spin (or other spin) before impact.
        // That awareness lives in the board AI's evaluator, which biases
        // toward spin-shaped boards while an active incoming threat is
        // present, rather than as a strategic call here.

        ParticipantState self = match.getParticipant(aiId);
        ParticipantState opponent = match.getOpponent(aiId);
        MabAiDecision decision = policy.chooseDecision(state, match, self, opponent);
        state.recordDecision();

        MabAiDecision result = decision;
        if (decision.executed()) {
            result = execute(decision);
        }
        state.setLastDecision(result);
        if (result.executed()) {
            log("AI_DECISION_EXECUTED", result.type() + " " + result.detail(),
                    metaOf("type", result.type(),
                            "detail", result.detail(),
                            "message", result.message()));
        } else {
            log("AI_DECISION_SKIPPED", result.type() + " " + result.message(),
                    metaOf("type", result.type(),
                            "detail", result.detail(),
                            "message", result.message()));
        }
        return result;
    }

    private MabAiDecision execute(MabAiDecision d) {
        try {
            return switch (d.type()) {
                case NONE -> d;
                case RESOLVE_IMPACT -> {
                    var impacts = match.debugResolveAllImpacts();
                    int n = impacts == null ? 0 : impacts.size();
                    state.recordImpactResolution();
                    yield MabAiDecision.executed(d.type(), aiId,
                            Integer.toString(n), "resolved " + n + " impacts");
                }
                case CIVIL_DEFENSE -> {
                    boolean ok = match.debugActivateCivilDefense(aiId);
                    if (ok) {
                        state.recordCivilDefense();
                        state.setDefenseCooldownPieces(defenseCooldownFor());
                        yield d;
                    }
                    yield MabAiDecision.skipped(d.type(), aiId, d.detail(),
                            "civil defence rejected");
                }
                case ROUTE_SCAN -> {
                    yield MabAiDecision.skipped(d.type(), aiId, d.detail(),
                            "legacy scan branch disabled");
                }
                case ADD_CHARGE -> {
                    int amt = parseInt(d.detail(), MabAiPolicy.chargeStep(state));
                    boolean ok = match.debugAddNukeCharge(aiId, amt);
                    if (ok) {
                        state.spendChargeBudget(amt);
                        yield d;
                    }
                    yield MabAiDecision.skipped(d.type(), aiId, d.detail(),
                            "add-charge rejected");
                }
                case ARM_NUKE -> {
                    int needed = Math.max(0,
                            match.getParticipant(aiId).getNukeBuildState().getEffectiveBuildChargeRequired()
                          - match.getParticipant(aiId).getNukeBuildState().getCurrentBuildCharge());
                    if (needed > 0) {
                        boolean ok = match.debugAddNukeCharge(aiId, needed);
                        if (ok) state.spendChargeBudget(needed);
                    }
                    boolean armed = match.debugArmCurrentNuke(aiId);
                    yield armed ? d
                            : MabAiDecision.skipped(d.type(), aiId, d.detail(),
                                    "arm rejected");
                }
                case LAUNCH -> {
                    // Launches in the current MAB design fire from the
                    // simplified clear routes (4 Tetris pips or 2 spin
                    // pips after armed) — not from a direct API call.
                    yield MabAiDecision.skipped(d.type(), aiId, d.detail(),
                            "launches fire from clear routes");
                }
                case FEINT -> {
                    yield MabAiDecision.skipped(d.type(), aiId, d.detail(),
                            "legacy false-target branch disabled");
                }
                case OPEN_UPGRADE_PAUSE -> attemptUpgradeSequence(d);
                case APPLY_UPGRADE, CLOSE_UPGRADE_PAUSE -> d;
            };
        } catch (RuntimeException ex) {
            return MabAiDecision.skipped(d.type(), aiId, d.detail(),
                    "exception: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private MabAiDecision attemptUpgradeSequence(MabAiDecision d) {
        boolean opened = match.openUpgradePause("ai:" + aiId);
        if (!opened) {
            return MabAiDecision.skipped(d.type(), aiId, d.detail(),
                    "upgrade pause not openable");
        }
        try {
            UpgradeChoiceSet choices = match.getUpgradeChoices(aiId);
            UpgradeType pick = pickUpgrade(choices);
            log("AI_UPGRADE_ATTEMPTED", "pick=" + pick,
                    metaOf("pick", pick,
                            "points", choices.availableUpgradePoints(),
                            "available", choices.choices() == null
                                    ? 0 : choices.choices().size()));
            if (pick == null) {
                state.setUpgradeCooldownPieces(upgradeCooldownFor());
                return MabAiDecision.executed(d.type(), aiId, "no-pick",
                        "no available upgrade");
            }
            UpgradeApplicationResult r = match.applyUpgrade(aiId, pick);
            if (r != null && r.success()) {
                state.recordUpgradePick();
                log("AI_UPGRADE_APPLIED", pick + " -> " + r.newLevel(),
                        metaOf("upgrade", pick,
                                "newLevel", r.newLevel(),
                                "costPaid", r.costPaid()));
                state.setUpgradeCooldownPieces(upgradeCooldownFor());
                return MabAiDecision.executed(MabAiDecisionType.APPLY_UPGRADE,
                        aiId, pick.name(), "applied " + pick);
            }
            log("AI_UPGRADE_SKIPPED",
                    pick + " rejected: " + (r == null ? "null" : r.message()),
                    metaOf("upgrade", pick,
                            "reason", r == null ? "null" : r.message()));
            state.setUpgradeCooldownPieces(upgradeCooldownFor());
            return MabAiDecision.skipped(MabAiDecisionType.APPLY_UPGRADE,
                    aiId, pick.name(),
                    "apply rejected: " + (r == null ? "null" : r.message()));
        } finally {
            match.closeUpgradePause("ai:" + aiId);
        }
    }

    /**
     * Pick an upgrade from the available choice set, scored by design
     * relevance + difficulty-driven appetite. Master tier scores all
     * options and picks the highest-EV one; lower tiers fall back to a
     * priority list with mild jitter.
     */
    private UpgradeType pickUpgrade(UpgradeChoiceSet choices) {
        if (choices == null || choices.choices() == null
                || choices.choices().isEmpty()) {
            return null;
        }
        List<UpgradeDefinition> defs = choices.choices();
        UpgradeType[] preferences = preferredUpgrades(state.getArchetype(),
                designDoctrine());
        for (UpgradeType pref : preferences) {
            for (UpgradeDefinition def : defs) {
                if (def.type() == pref) return pref;
            }
        }
        return defs.get(0).type();
    }

    private com.tetris.mab.nuke.NukeDoctrineType designDoctrine() {
        try {
            ParticipantState self = match.getParticipant(aiId);
            if (self == null) return null;
            var d = self.getNukeBuildState().getCurrentDesign();
            return d == null ? null : d.getDoctrineType();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static UpgradeType[] preferredUpgrades(MabAiArchetype a,
                                                    com.tetris.mab.nuke.NukeDoctrineType doc) {
        // Doctrine first — it's tied to the real design carrying the AI.
        if (doc != null) {
            switch (doc) {
                case TACTICAL_BLAST: return new UpgradeType[] {
                        UpgradeType.RAPID_LAUNCH_DRILLS,
                        UpgradeType.BUILD_EFFICIENCY,
                        UpgradeType.RAPID_ASSEMBLY_LINE,
                        UpgradeType.PENETRATION_PACKAGE };
                case HEAVY_BLAST: return new UpgradeType[] {
                        UpgradeType.WARHEAD_REFINEMENT,
                        UpgradeType.PENETRATION_PACKAGE,
                        UpgradeType.BLAST_DOORS,
                        UpgradeType.BUILD_EFFICIENCY };
                case DIRTY_BOMB:
                case DIRTY_PAYLOAD: return new UpgradeType[] {
                        UpgradeType.DIRTY_PAYLOAD_ENGINEERING,
                        UpgradeType.WARHEAD_REFINEMENT,
                        UpgradeType.PENETRATION_PACKAGE };
                case EMP_PAYLOAD: return new UpgradeType[] {
                        UpgradeType.PENETRATION_PACKAGE,
                        UpgradeType.WARHEAD_REFINEMENT,
                        UpgradeType.RAPID_LAUNCH_DRILLS };
                case BUNKER_BUSTER:
                case CONCRETE_BLASTER: return new UpgradeType[] {
                        UpgradeType.HARDENED_SILO,
                        UpgradeType.BLAST_DOORS,
                        UpgradeType.SECURE_LAUNCH_CHAIN,
                        UpgradeType.WARHEAD_REFINEMENT };
                case CLEAN_FUSION: return new UpgradeType[] {
                        UpgradeType.BUILD_EFFICIENCY,
                        UpgradeType.WARHEAD_REFINEMENT,
                        UpgradeType.RAPID_ASSEMBLY_LINE,
                        UpgradeType.SHELTERS };
                case DOOMSDAY: return new UpgradeType[] {
                        UpgradeType.WARHEAD_REFINEMENT,
                        UpgradeType.DEEP_BUNKER,
                        UpgradeType.DEAD_HAND_PROTOCOL,
                        UpgradeType.ASSURED_RETALIATION };
                case SALTED_PAYLOAD:
                case SALTED_WARHEAD: return new UpgradeType[] {
                        UpgradeType.WARHEAD_REFINEMENT,
                        UpgradeType.DIRTY_PAYLOAD_ENGINEERING,
                        UpgradeType.PENETRATION_PACKAGE };
                case MIRV:
                case DECOY_PACKAGE:
                    // Legacy doctrines (never produced by the live builder)
                    // — fall through to the archetype-driven fallback.
                    break;
                default: break;
            }
        }
        return switch (a) {
            case TACTICAL_SPAMMER -> new UpgradeType[] {
                    UpgradeType.RAPID_LAUNCH_DRILLS,
                    UpgradeType.BUILD_EFFICIENCY,
                    UpgradeType.RAPID_ASSEMBLY_LINE
            };
            case DIRTY_BOMBER -> new UpgradeType[] {
                    UpgradeType.DIRTY_PAYLOAD_ENGINEERING,
                    UpgradeType.WARHEAD_REFINEMENT,
                    UpgradeType.PENETRATION_PACKAGE
            };
            case CONCRETE_STRATEGIST -> new UpgradeType[] {
                    UpgradeType.HARDENED_SILO,
                    UpgradeType.BLAST_DOORS,
                    UpgradeType.SECURE_LAUNCH_CHAIN
            };
            case MAD_DEFENDER -> new UpgradeType[] {
                    UpgradeType.SHELTERS,
                    UpgradeType.GARBAGE_CONTROL,
                    UpgradeType.SECOND_STRIKE_DOCTRINE_I,
                    UpgradeType.ASSURED_RETALIATION
            };
            case PAYLOAD_CONTROLLER -> new UpgradeType[] {
                    UpgradeType.PENETRATION_PACKAGE,
                    UpgradeType.WARHEAD_REFINEMENT,
                    UpgradeType.BUILD_EFFICIENCY
            };
            case DOOMSDAY_HOARDER -> new UpgradeType[] {
                    UpgradeType.WARHEAD_REFINEMENT,
                    UpgradeType.DEEP_BUNKER,
                    UpgradeType.DEAD_HAND_PROTOCOL
            };
            case BALANCED -> new UpgradeType[] {
                    UpgradeType.BUILD_EFFICIENCY,
                    UpgradeType.SHELTERS,
                    UpgradeType.INTERCEPT_CREWS
            };
        };
    }

    private int defenseCooldownFor() {
        return state.getBalanceProfile().defenseCooldownFor(state.getDifficulty());
    }
    private int upgradeCooldownFor() {
        return state.getBalanceProfile().upgradeCooldownFor(state.getDifficulty());
    }

    public String toDebugString() { return state.toDebugString(); }

    private void log(String type, String message, Map<String, Object> meta) {
        match.debugLogEvent(type, aiId, message, meta);
    }

    private static Map<String, Object> metaOf(Object... kv) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    private static int parseInt(String s, int fallback) {
        if (s == null || s.isEmpty()) return fallback;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return fallback; }
    }
}
