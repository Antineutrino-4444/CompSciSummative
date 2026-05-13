package com.tetris.mab.debugui;

import com.tetris.mab.MatchDebugSnapshot;
import com.tetris.mab.MatchEventLogEntry;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ai.MabAiArchetype;
import com.tetris.mab.ai.MabAiDecision;
import com.tetris.mab.ai.MabAiDifficulty;
import com.tetris.mab.ai.MabAiDriver;
import com.tetris.mab.decoy.DecoyType;

import java.util.List;

/**
 * Step 12 — thin controller that mediates between the debug UI panel
 * and the underlying {@link MutuallyAssuredBlocksMatch}.
 *
 * <p>All UI-facing actions are short delegates so the panel stays
 * passive. Methods are safe to call from the Swing EDT.
 */
public final class MabDebugController {

    /** Default charge increment per "Add Charge" click. */
    public static final int DEFAULT_CHARGE_INCREMENT = 25;

    private final MutuallyAssuredBlocksMatch match;
    private MabAiDriver aiDriver;
    /** Auto-tick AI from the HUD refresh timer when enabled. */
    private boolean autoTickAi = true;

    public MabDebugController(MutuallyAssuredBlocksMatch match) {
        if (match == null) throw new IllegalArgumentException("match");
        this.match = match;
    }

    public MutuallyAssuredBlocksMatch getMatch() { return match; }

    public MatchDebugSnapshot snapshot() { return match.toDebugSnapshot(); }

    public List<MatchEventLogEntry> recentEvents() { return match.getRecentEvents(20); }

    public String formattedSnapshot() { return MabDebugFormatter.formatSnapshot(snapshot()); }

    public List<String> formattedEvents() {
        return MabDebugFormatter.formatRecentEvents(recentEvents(), 20);
    }

    public String helpText() { return MabDebugFormatter.formatHelpText(); }

    // ── per-action delegates ───────────────────────────────

    public void addChargeA() { match.debugAddNukeCharge(ParticipantId.PLAYER_A, DEFAULT_CHARGE_INCREMENT); }
    public void addChargeB() { match.debugAddNukeCharge(ParticipantId.PLAYER_B, DEFAULT_CHARGE_INCREMENT); }

    public void armA() { match.debugArmCurrentNuke(ParticipantId.PLAYER_A); }
    public void armB() { match.debugArmCurrentNuke(ParticipantId.PLAYER_B); }

    public void launchA() { match.debugStartLaunch(ParticipantId.PLAYER_A); }
    public void launchB() { match.debugStartLaunch(ParticipantId.PLAYER_B); }

    public void radarA() { match.debugRadarScan(ParticipantId.PLAYER_A); }
    public void radarB() { match.debugRadarScan(ParticipantId.PLAYER_B); }

    public void civilDefenseA() { match.debugActivateCivilDefense(ParticipantId.PLAYER_A); }
    public void civilDefenseB() { match.debugActivateCivilDefense(ParticipantId.PLAYER_B); }

    public void decoyA(DecoyType type) { match.debugActivateDecoy(ParticipantId.PLAYER_A, type); }
    public void decoyB(DecoyType type) { match.debugActivateDecoy(ParticipantId.PLAYER_B, type); }

    public void resolveImpacts() { match.debugResolveAllImpacts(); }

    public void openUpgradePause() { match.enterUpgradePause("debug-hud"); }
    public void closeUpgradePause() { match.exitUpgradePause("debug-hud"); }

    // ── Step 13: PvE AI driver ───────────────────────────────

    /** Lazily attaches a PvE AI to {@link ParticipantId#PLAYER_B}. */
    public MabAiDriver attachAiB(MabAiArchetype archetype, MabAiDifficulty difficulty) {
        if (aiDriver != null) return aiDriver;
        aiDriver = new MabAiDriver(match, ParticipantId.PLAYER_B,
                archetype == null ? MabAiArchetype.BALANCED : archetype,
                difficulty == null ? MabAiDifficulty.NORMAL : difficulty);
        return aiDriver;
    }

    public MabAiDriver getAiDriver() { return aiDriver; }
    public boolean isAiAttached() { return aiDriver != null; }
    public boolean isAiEnabled() { return aiDriver != null && aiDriver.isEnabled(); }

    public void setAutoTickAi(boolean v) { this.autoTickAi = v; }
    public boolean isAutoTickAi() { return autoTickAi; }

    /** Toggle the AI driver on/off; auto-attaches with sensible defaults. */
    public void toggleAi() {
        if (aiDriver == null) {
            attachAiB(MabAiArchetype.BALANCED, MabAiDifficulty.NORMAL);
        }
        aiDriver.setEnabled(!aiDriver.isEnabled());
    }

    /** Single manual tick. Returns the resulting decision (never null). */
    public MabAiDecision tickAiOnce() {
        if (aiDriver == null) {
            attachAiB(MabAiArchetype.BALANCED, MabAiDifficulty.NORMAL);
        }
        if (!aiDriver.isEnabled()) aiDriver.setEnabled(true);
        return aiDriver.tick();
    }

    /** Auto-tick called from the HUD refresh timer. */
    public MabAiDecision tickAiIfEnabled() {
        if (!autoTickAi) return null;
        if (aiDriver == null || !aiDriver.isEnabled()) return null;
        return aiDriver.tick();
    }

    /** Snapshot text including AI state when attached. */
    public String formattedSnapshotWithAi() {
        String base = formattedSnapshot();
        if (aiDriver == null) return base;
        return base + System.lineSeparator()
                + System.lineSeparator()
                + "── AI ──"
                + System.lineSeparator()
                + aiDriver.toDebugString();
    }

    // ── Step 14: headless smoke simulation hook ──────────────

    /**
     * Runs a headless smoke simulation in a fresh, isolated
     * {@link com.tetris.mab.MutuallyAssuredBlocksMatch} (the
     * currently displayed match is NOT mutated). Returns a short
     * human-readable summary suitable for showing in the HUD.
     */
    public String runHeadlessSmokeSimulation() {
        com.tetris.mab.sim.MabSimulationResult r =
                new com.tetris.mab.sim.MabHeadlessSimulation()
                        .run(com.tetris.mab.sim.MabSimulationConfig.smoke());
        StringBuilder sb = new StringBuilder();
        sb.append("Smoke sim: ").append(r.success() ? "OK" : "FAIL").append('\n');
        sb.append(r.summary()).append('\n');
        if (!r.invariantFailures().isEmpty()) {
            sb.append("invariantFailures:\n");
            for (String f : r.invariantFailures()) sb.append("  - ").append(f).append('\n');
        }
        return sb.toString();
    }
}
