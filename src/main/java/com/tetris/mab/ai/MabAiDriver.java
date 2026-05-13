package com.tetris.mab.ai;

import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ParticipantState;
import com.tetris.mab.balance.MabBalanceProfile;
import com.tetris.mab.balance.MabBalanceProfiles;
import com.tetris.mab.decoy.DecoyResolutionResult;
import com.tetris.mab.decoy.DecoyType;
import com.tetris.mab.intel.RadarScanResult;
import com.tetris.mab.upgrade.UpgradeApplicationResult;
import com.tetris.mab.upgrade.UpgradeChoiceSet;
import com.tetris.mab.upgrade.UpgradeDefinition;
import com.tetris.mab.upgrade.UpgradeType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Step 13 — offline PvE driver that ticks the {@link MabAiPolicy}
 * against a single hidden participant. Local-only: no networking, no
 * threading, no rollback. Driven by the debug HUD's refresh timer or a
 * manual "AI Tick Once" button on the Swing event-dispatch thread.
 */
public final class MabAiDriver {

    private final MutuallyAssuredBlocksMatch match;
    private final ParticipantId aiId;
    private final MabAiState state;
    private final MabAiPolicy policy;

    /** Whether the AI also advances its own hidden strategic clock per tick. */
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

    /**
     * Advance the AI by one tick. No-op when disabled. Always safe to
     * call from the Swing EDT — does not block.
     */
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
                state.addChargeBudget(
                        MabAiPolicy.chargeBudgetGainPerSimulatedPiece(state));
            }
        }

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

    // ─────────────── decision execution ───────────────────────

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
                case RADAR_SCAN -> {
                    RadarScanResult r = match.debugRadarScan(aiId);
                    boolean ok = r != null && r.success();
                    if (ok) {
                        state.recordRadarScan();
                        state.setRadarCooldownPieces(radarCooldownFor());
                        yield d;
                    }
                    yield MabAiDecision.skipped(d.type(), aiId, d.detail(),
                            "radar scan rejected");
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
                    yield MabAiDecision.skipped(d.type(), aiId, d.detail(),
                            "launches fire from simplified clear routes");
                }
                case DECOY -> {
                    DecoyType type;
                    try { type = DecoyType.valueOf(d.detail()); }
                    catch (Exception ex) { type = MabAiPolicy.decoyChoice(state.getArchetype()); }
                    DecoyResolutionResult r = match.debugActivateDecoy(aiId, type);
                    boolean ok = r != null && r.success();
                    if (ok) {
                        state.recordDecoy();
                        state.setDecoyCooldownPieces(decoyCooldownFor());
                        yield d;
                    }
                    yield MabAiDecision.skipped(d.type(), aiId, d.detail(),
                            "decoy rejected");
                }
                case OPEN_UPGRADE_PAUSE -> attemptUpgradeSequence(d);
                case APPLY_UPGRADE, CLOSE_UPGRADE_PAUSE ->
                        // The driver folds these into OPEN_UPGRADE_PAUSE.
                        d;
            };
        } catch (RuntimeException ex) {
            return MabAiDecision.skipped(d.type(), aiId, d.detail(),
                    "exception: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    /**
     * Open the upgrade pause, attempt to apply one archetype-preferred
     * upgrade, then close the pause. Logs intermediate AI_UPGRADE_*
     * events. All inside the AI driver so the HUD only sees a single
     * decision per tick.
     */
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
     * Pick an upgrade from the available choice set, preferring the
     * archetype's priority list, then falling back to the first
     * available choice.
     */
    private UpgradeType pickUpgrade(UpgradeChoiceSet choices) {
        if (choices == null || choices.choices() == null || choices.choices().isEmpty()) {
            return null;
        }
        List<UpgradeDefinition> defs = choices.choices();
        for (UpgradeType pref : preferredUpgrades(state.getArchetype())) {
            for (UpgradeDefinition def : defs) {
                if (def.type() == pref) return pref;
            }
        }
        return defs.get(0).type();
    }

    /**
     * Archetype-flavoured preference list. References only enum
     * constants verified to exist in {@link UpgradeType}.
     */
    private static UpgradeType[] preferredUpgrades(MabAiArchetype a) {
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
            case MIRV_CONTROLLER -> new UpgradeType[] {
                    UpgradeType.SIGNAL_ANALYSIS,
                    UpgradeType.THREAT_TRACKING,
                    UpgradeType.PENETRATION_PACKAGE
            };
            case DOOMSDAY_HOARDER -> new UpgradeType[] {
                    UpgradeType.WARHEAD_REFINEMENT,
                    UpgradeType.DEEP_BUNKER,
                    UpgradeType.DEAD_HAND_PROTOCOL
            };
            case BALANCED -> new UpgradeType[] {
                    UpgradeType.EARLY_WARNING_RADAR,
                    UpgradeType.BUILD_EFFICIENCY,
                    UpgradeType.SHELTERS
            };
        };
    }

    // ─────────────── cooldown defaults via balance profile ────

    private int launchCooldownFor() {
        return state.getBalanceProfile().launchCooldownFor(state.getDifficulty());
    }
    private int radarCooldownFor() {
        return state.getBalanceProfile().radarCooldownFor(state.getDifficulty());
    }
    private int decoyCooldownFor() {
        return state.getBalanceProfile().decoyCooldownFor(state.getDifficulty());
    }
    private int defenseCooldownFor() {
        return state.getBalanceProfile().defenseCooldownFor(state.getDifficulty());
    }
    private int upgradeCooldownFor() {
        return state.getBalanceProfile().upgradeCooldownFor(state.getDifficulty());
    }

    // ─────────────── helpers ──────────────────────────────────

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
