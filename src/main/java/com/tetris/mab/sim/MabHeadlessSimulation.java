package com.tetris.mab.sim;

import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MatchEventLogEntry;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ParticipantState;
import com.tetris.mab.ai.MabAiDriver;
import com.tetris.mab.decoy.DecoyType;
import com.tetris.mab.upgrade.UpgradeType;
import com.tetris.model.GameState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Step 14 — non-Swing simulation harness for the MAB strategic
 * layer. Builds a {@link MutuallyAssuredBlocksMatch} from two model-only
 * {@link GameState} instances and ticks it deterministically through
 * one of several scenarios.
 *
 * <p>Local-only. No threads, no networking, no UI. Never moves any
 * visible piece — everything goes through the {@code debug*} hooks on
 * {@code MutuallyAssuredBlocksMatch}.
 */
public final class MabHeadlessSimulation {

    public MabSimulationResult run(MabSimulationConfig config) {
        if (config == null) config = MabSimulationConfig.smoke();
        try {
            return switch (config.mode()) {
                case SMOKE         -> runSmokeScenario(config);
                case LAUNCH_IMPACT -> runLaunchImpactScenario(config);
                case RADAR_DECOY   -> runRadarDecoyScenario(config);
                case CIVIL_DEFENSE -> runCivilDefenseScenario(config);
                case UPGRADE_FLOW  -> runUpgradeFlowScenario(config);
                case AI_VS_DUMMY   -> runAiVsDummyScenario(config);
                case AI_VS_AI      -> runAiVsAiScenario(config);
                case DEBUG         -> runDebugScenario(config);
            };
        } catch (RuntimeException ex) {
            return failure(config, 0, "exception: "
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                    null, null);
        }
    }

    // ─────────────────────────── Scenarios ─────────────────────

    private MabSimulationResult runSmokeScenario(MabSimulationConfig cfg) {
        Setup s = setup(cfg);
        // Charge → arm → launch on A.
        s.match.debugAddNukeCharge(ParticipantId.PLAYER_A, 1_000);
        s.match.debugArmCurrentNuke(ParticipantId.PLAYER_A);
        boolean launched = s.match.debugStartLaunch(ParticipantId.PLAYER_A);
        // Decoy on B and a radar scan from A.
        s.match.debugActivateDecoy(ParticipantId.PLAYER_B, DecoyType.DECOY_LAUNCH);
        s.match.debugRadarScan(ParticipantId.PLAYER_A);
        // Civil defence on B.
        s.match.debugActivateCivilDefense(ParticipantId.PLAYER_B);
        tally(s);
        // Tick the strategic clocks for both sides so countdown timers
        // can flip into IMPACT_READY.
        for (int i = 0; i < cfg.ticks(); i++) {
            s.match.debugAdvanceStrategicClockOnly(ParticipantId.PLAYER_A, 1);
            s.match.debugAdvanceStrategicClockOnly(ParticipantId.PLAYER_B, 1);
            if (cfg.autoResolveImpacts()) s.match.debugResolveAllImpacts();
            tally(s);
        }
        // Final sweep.
        s.match.debugResolveAllImpacts();
        tally(s);

        Verdict v = collect(cfg, s);
        boolean ok = v.invariantFailures.isEmpty()
                && v.tel.countEvents("LAUNCH_AUTHORIZED") >= 1
                && v.tel.countEvents("RADAR_SCAN_COMPLETED") >= 1
                && v.tel.countEvents("DECOY_ACTIVATED") >= 1;
        if (!launched) ok = false;
        return finish(cfg, s, v, ok,
                ok ? "" : "smoke acceptance not met");
    }

    private MabSimulationResult runLaunchImpactScenario(MabSimulationConfig cfg) {
        Setup s = setup(cfg);
        s.match.debugAddNukeCharge(ParticipantId.PLAYER_A, 2_000);
        s.match.debugArmCurrentNuke(ParticipantId.PLAYER_A);
        s.match.debugStartLaunch(ParticipantId.PLAYER_A);
        tally(s);
        for (int i = 0; i < Math.max(cfg.ticks(), 1); i++) {
            s.match.debugAdvanceStrategicClockOnly(ParticipantId.PLAYER_A, 1);
            s.match.debugAdvanceStrategicClockOnly(ParticipantId.PLAYER_B, 1);
            if (cfg.autoResolveImpacts()) s.match.debugResolveAllImpacts();
            tally(s);
        }
        s.match.debugResolveAllImpacts();
        tally(s);

        Verdict v = collect(cfg, s);
        boolean ok = v.invariantFailures.isEmpty()
                && v.tel.countEvents("LAUNCH_AUTHORIZED") >= 1
                && v.tel.countEvents("IMPACT_RESOLVED") >= 1;
        return finish(cfg, s, v, ok,
                ok ? "" : "launch/impact acceptance not met");
    }

    private MabSimulationResult runRadarDecoyScenario(MabSimulationConfig cfg) {
        Setup s = setup(cfg);
        s.match.debugActivateDecoy(ParticipantId.PLAYER_B, DecoyType.DECOY_LAUNCH);
        tally(s);
        for (int i = 0; i < Math.max(cfg.ticks(), 4); i++) {
            s.match.debugRadarScan(ParticipantId.PLAYER_A);
            s.match.debugAdvanceStrategicClockOnly(ParticipantId.PLAYER_A, 1);
            s.match.debugAdvanceStrategicClockOnly(ParticipantId.PLAYER_B, 1);
            tally(s);
        }
        Verdict v = collect(cfg, s);
        boolean ok = v.invariantFailures.isEmpty()
                && v.tel.countEvents("DECOY_ACTIVATED") >= 1
                && v.tel.countEvents("RADAR_SCAN_COMPLETED") >= 1
                && v.tel.countEvents("RADAR_DECOY_EFFECT_APPLIED") >= 1;
        return finish(cfg, s, v, ok,
                ok ? "" : "radar/decoy acceptance not met");
    }

    private MabSimulationResult runCivilDefenseScenario(MabSimulationConfig cfg) {
        Setup s = setup(cfg);
        s.match.debugActivateCivilDefense(ParticipantId.PLAYER_B);
        s.match.debugAddNukeCharge(ParticipantId.PLAYER_A, 2_000);
        s.match.debugArmCurrentNuke(ParticipantId.PLAYER_A);
        s.match.debugStartLaunch(ParticipantId.PLAYER_A);
        tally(s);
        for (int i = 0; i < Math.max(cfg.ticks(), 1); i++) {
            s.match.debugAdvanceStrategicClockOnly(ParticipantId.PLAYER_A, 1);
            s.match.debugAdvanceStrategicClockOnly(ParticipantId.PLAYER_B, 1);
            if (cfg.autoResolveImpacts()) s.match.debugResolveAllImpacts();
            tally(s);
        }
        s.match.debugResolveAllImpacts();
        tally(s);

        Verdict v = collect(cfg, s);
        int civDefConsumed = v.tel.countEvents("CIVIL_DEFENSE_CONSUMED")
                + v.tel.countEvents("CIVIL_DEFENSE_MITIGATION_APPLIED");
        boolean ok = v.invariantFailures.isEmpty()
                && v.tel.countEvents("CIVIL_DEFENSE_ACTIVATED") >= 1
                && civDefConsumed >= 1
                && v.tel.countEvents("IMPACT_RESOLVED") >= 1;
        return finish(cfg, s, v, ok,
                ok ? "" : "civil-defence acceptance not met");
    }

    private MabSimulationResult runUpgradeFlowScenario(MabSimulationConfig cfg) {
        Setup s = setup(cfg);
        s.match.debugAddUpgradePoints(ParticipantId.PLAYER_A, 50);
        s.match.openUpgradePause("sim:upgrade-flow");
        var result = s.match.applyUpgrade(ParticipantId.PLAYER_A, UpgradeType.HARDENED_SILO);
        s.match.closeUpgradePause("sim:upgrade-flow");
        tally(s);
        for (int i = 0; i < cfg.ticks(); i++) {
            s.match.debugAdvanceStrategicClockOnly(ParticipantId.PLAYER_A, 1);
            tally(s);
        }
        Verdict v = collect(cfg, s);
        int level = s.match.getParticipant(ParticipantId.PLAYER_A)
                .getUpgradeState().getLevel(UpgradeType.HARDENED_SILO);
        boolean ok = v.invariantFailures.isEmpty()
                && v.tel.countEvents("UPGRADE_APPLIED") >= 1
                && result != null && result.success()
                && level >= 1;
        return finish(cfg, s, v, ok,
                ok ? "" : "upgrade-flow acceptance not met");
    }

    private MabSimulationResult runAiVsDummyScenario(MabSimulationConfig cfg) {
        Setup s = setup(cfg);
        MabAiDriver aiB = new MabAiDriver(s.match, ParticipantId.PLAYER_B,
                cfg.playerBArchetype(), cfg.difficulty(), cfg.balanceProfile());
        aiB.setEnabled(true);
        tally(s);
        for (int i = 0; i < cfg.ticks(); i++) {
            aiB.tick();
            if (cfg.autoResolveImpacts()) s.match.debugResolveAllImpacts();
            tally(s);
        }
        Verdict v = collect(cfg, s);
        int decisions = v.tel.countEvents("AI_DECISION_EXECUTED")
                + v.tel.countEvents("AI_DECISION_SKIPPED");
        boolean ok = v.invariantFailures.isEmpty()
                && decisions > 0
                && v.tel.countEvents("AI_STRATEGIC_CLOCK_ADVANCED") > 0;
        return finish(cfg, s, v, ok,
                ok ? "" : "ai-vs-dummy acceptance not met");
    }

    private MabSimulationResult runAiVsAiScenario(MabSimulationConfig cfg) {
        Setup s = setup(cfg);
        MabAiDriver aiA = new MabAiDriver(s.match, ParticipantId.PLAYER_A,
                cfg.playerAArchetype(), cfg.difficulty(), cfg.balanceProfile());
        MabAiDriver aiB = new MabAiDriver(s.match, ParticipantId.PLAYER_B,
                cfg.playerBArchetype(), cfg.difficulty(), cfg.balanceProfile());
        aiA.setEnabled(true);
        aiB.setEnabled(true);
        tally(s);
        for (int i = 0; i < cfg.ticks(); i++) {
            aiA.tick();
            aiB.tick();
            if (cfg.autoResolveImpacts()) s.match.debugResolveAllImpacts();
            tally(s);
        }
        Verdict v = collect(cfg, s);
        boolean ok = v.invariantFailures.isEmpty()
                && v.tel.countEvents("AI_DECISION_EXECUTED") > 0
                && v.tel.countEvents("AI_STRATEGIC_CLOCK_ADVANCED") > 0;
        return finish(cfg, s, v, ok,
                ok ? "" : "ai-vs-ai acceptance not met");
    }

    private MabSimulationResult runDebugScenario(MabSimulationConfig cfg) {
        Setup s = setup(cfg);
        for (int i = 0; i < cfg.ticks(); i++) {
            s.match.debugAdvanceStrategicClockOnly(ParticipantId.PLAYER_A, 1);
            s.match.debugAdvanceStrategicClockOnly(ParticipantId.PLAYER_B, 1);
            tally(s);
        }
        Verdict v = collect(cfg, s);
        boolean ok = v.invariantFailures.isEmpty();
        return finish(cfg, s, v, ok, ok ? "" : "debug invariants failed");
    }

    // ─────────────────────────── Helpers ───────────────────────

    private static Setup setup(MabSimulationConfig cfg) {
        GameState a = new GameState(0);
        GameState b = new GameState(0);
        MutuallyAssuredBlocksMatch match = MutuallyAssuredBlocksMatch.createLocalPvpShared(
                a, b, MatchDifficulty.NORMAL, 14026L);
        match.startMatch();
        // Step 26 — apply per-participant warhead designs if the config
        // specifies them. Charge requirement and launch route are now
        // design-specific, so this is how nuke-builder-balance scenarios
        // pin a particular doctrine for each side.
        if (cfg.playerADesign() != null) {
            match.applyWarheadDesign(ParticipantId.PLAYER_A, cfg.playerADesign());
        }
        if (cfg.playerBDesign() != null) {
            match.applyWarheadDesign(ParticipantId.PLAYER_B, cfg.playerBDesign());
        }
        Setup s = new Setup(a, b, match,
                new LinkedHashMap<>(), new long[]{Long.MIN_VALUE}, new int[]{0});
        tally(s);
        return s;
    }

    /**
     * Step 14 — folds any new entries from the match's bounded event
     * log into our cumulative counts. Safe to call repeatedly.
     */
    private static void tally(Setup s) {
        for (MatchEventLogEntry e : s.match.getEventLog()) {
            if (e.sequenceNumber() <= s.lastSeq[0]) continue;
            s.lastSeq[0] = e.sequenceNumber();
            String t = e.eventType();
            if (t == null || t.isEmpty()) continue;
            s.cumulative.merge(t, 1, Integer::sum);
            s.totalSeen[0]++;
        }
    }

    private static Verdict collect(MabSimulationConfig cfg, Setup s) {
        tally(s);
        MabSimulationTelemetry tel = MabSimulationTelemetry.fromCounts(
                s.cumulative, s.totalSeen[0]);
        List<String> failures = new MabSimulationInvariants().check(s.match);
        List<String> recent = tel.recentEventRows(s.match, cfg.maxEventRows());
        return new Verdict(tel, failures, recent);
    }

    private static MabSimulationResult finish(MabSimulationConfig cfg, Setup s,
                                              Verdict v, boolean ok, String why) {
        ParticipantState a = s.match.getParticipant(ParticipantId.PLAYER_A);
        ParticipantState b = s.match.getParticipant(ParticipantId.PLAYER_B);
        int garbage = v.tel.countEvents("IMPACT_GARBAGE_IMMEDIATE_APPLIED")
                + v.tel.countEvents("GARBAGE_INSERTED");
        String summary = "mode=" + cfg.mode()
                + " ok=" + (ok && v.invariantFailures.isEmpty())
                + " ticks=" + cfg.ticks()
                + " events=" + v.tel.totalEvents()
                + " launches=" + v.tel.countEvents("LAUNCH_AUTHORIZED")
                + " impacts=" + v.tel.countEvents("IMPACT_RESOLVED")
                + " scans=" + v.tel.countEvents("RADAR_SCAN_COMPLETED")
                + " decoys=" + v.tel.countEvents("DECOY_ACTIVATED")
                + " civDef=" + v.tel.countEvents("CIVIL_DEFENSE_ACTIVATED")
                + " upgrades=" + v.tel.countEvents("UPGRADE_APPLIED")
                + " aiExec=" + v.tel.countEvents("AI_DECISION_EXECUTED")
                + " aiSkip=" + v.tel.countEvents("AI_DECISION_SKIPPED")
                + " garbage=" + garbage
                + " invariantFailures=" + v.invariantFailures.size();
        return new MabSimulationResult(
                ok && v.invariantFailures.isEmpty(),
                cfg.label(), cfg.mode(), cfg.ticks(),
                v.tel.totalEvents(),
                v.tel.countEvents("LAUNCH_AUTHORIZED"),
                v.tel.countEvents("IMPACT_RESOLVED"),
                v.tel.countEvents("RADAR_SCAN_COMPLETED"),
                v.tel.countEvents("DECOY_ACTIVATED"),
                v.tel.countEvents("CIVIL_DEFENSE_ACTIVATED"),
                v.tel.countEvents("UPGRADE_APPLIED"),
                v.tel.countEvents("AI_DECISION_EXECUTED"),
                v.tel.countEvents("AI_DECISION_SKIPPED"),
                garbage,
                a == null ? 0 : a.getNukeBuildState().getCurrentBuildCharge(),
                b == null ? 0 : b.getNukeBuildState().getCurrentBuildCharge(),
                a == null ? 0 : a.getSiloState().getIntegrity(),
                b == null ? 0 : b.getSiloState().getIntegrity(),
                ok ? "" : why,
                v.invariantFailures, v.recent, summary);
    }

    private static MabSimulationResult failure(MabSimulationConfig cfg, int ticks,
                                               String why,
                                               List<String> failures,
                                               List<String> recent) {
        return new MabSimulationResult(false,
                cfg.label(), cfg.mode(), ticks,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 100, 100,
                why,
                failures == null ? new ArrayList<>() : failures,
                recent == null ? new ArrayList<>() : recent,
                "FAILED " + cfg.mode() + ": " + why);
    }

    // ─────────────────────────── Plain holders ─────────────────

    private record Setup(GameState a, GameState b, MutuallyAssuredBlocksMatch match,
                         Map<String, Integer> cumulative,
                         long[] lastSeq,
                         int[] totalSeen) {}

    private record Verdict(MabSimulationTelemetry tel,
                           List<String> invariantFailures,
                           List<String> recent) {}
}
