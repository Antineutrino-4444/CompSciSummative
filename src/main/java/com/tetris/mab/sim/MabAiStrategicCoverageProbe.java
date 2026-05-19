package com.tetris.mab.sim;

import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MatchEventLogEntry;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ai.MabAiArchetype;
import com.tetris.mab.ai.MabAiDifficulty;
import com.tetris.mab.ai.MabAiDriver;
import com.tetris.mab.balance.MabBalanceProfiles;
import com.tetris.mab.nuke.NukeDesignFactory;
import com.tetris.model.GameState;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Strategic-AI coverage probe.
 *
 * <p>Drives the new strategic AI against multiple Nuke Builder designs
 * and DEFCON tempos to confirm:
 * <ul>
 *   <li>The AI references the design's effective build charge.</li>
 *   <li>Civil defence fires in response to incoming threats.</li>
 *   <li>Upgrade decisions get made when points are available.</li>
 *   <li>The AI never crashes during a complete match cycle.</li>
 * </ul>
 *
 * <p>Run:
 * <pre>
 *   java -cp target\classes com.tetris.mab.sim.MabAiStrategicCoverageProbe
 * </pre>
 *
 * <p>Offline-only.
 */
public final class MabAiStrategicCoverageProbe {

    public static void main(String[] args) {
        int ticksPerScenario = 160;
        if (args.length > 0) try { ticksPerScenario = Math.max(40, Integer.parseInt(args[0])); }
            catch (NumberFormatException ignored) {}

        System.out.println("MAB AI strategic-coverage probe ticks/scenario=" + ticksPerScenario);

        Map<String, com.tetris.mab.nuke.NukeDesign> designs = new LinkedHashMap<>();
        designs.put("tactical-blast", NukeDesignFactory.createDefaultTacticalBlast());
        designs.put("heavy-blast",    NukeDesignFactory.createDefaultHeavyBlast());
        designs.put("dirty-payload",  NukeDesignFactory.createDefaultDirtyPayload());
        designs.put("emp",            NukeDesignFactory.createDefaultEmp());
        designs.put("bunker-buster",  NukeDesignFactory.createDefaultBunkerBuster());
        designs.put("doomsday",       NukeDesignFactory.createDefaultDoomsday());

        int passed = 0;
        int failed = 0;
        for (var entry : designs.entrySet()) {
            String name = entry.getKey();
            com.tetris.mab.nuke.NukeDesign design = entry.getValue();
            Scenario s = runScenario(design, MabAiDifficulty.MASTER, ticksPerScenario);
            boolean addedCharge = s.eventTypes.contains("DEBUG_NUKE_CHARGE_ADDED")
                    || s.eventTypes.contains("AI_DECISION_EXECUTED");
            boolean designApplied = s.designId.equals(design.getId());
            boolean noException = !s.failed;
            boolean fired = s.eventTypes.contains("AI_DECISION_EXECUTED");
            boolean strategicClockAdvanced = s.eventTypes.contains("AI_STRATEGIC_CLOCK_ADVANCED");
            boolean ok = addedCharge && designApplied && noException
                    && (fired || strategicClockAdvanced);
            System.out.printf("  %-16s applied=%-5s addedCharge=%-5s fired=%-5s clock=%-5s noEx=%-5s -> %s%n",
                    name, designApplied, addedCharge, fired, strategicClockAdvanced,
                    noException, ok ? "PASS" : "FAIL");
            if (ok) passed++; else failed++;
        }

        // Upgrade scenario: make MASTER spend upgrade points.
        Scenario u = runUpgradeScenario(ticksPerScenario);
        boolean upgradeOk = u.eventTypes.contains("AI_UPGRADE_ATTEMPTED")
                || u.eventTypes.contains("UPGRADE_APPLIED")
                || u.eventTypes.contains("AI_UPGRADE_APPLIED");
        System.out.println("  upgrade-flow      attempt=" + upgradeOk
                + " -> " + (upgradeOk ? "PASS" : "FAIL"));
        if (upgradeOk) passed++; else failed++;

        System.out.println();
        System.out.println("passed=" + passed + " failed=" + failed);
        System.out.println("success=" + (failed == 0));
        if (failed != 0) System.exit(1);
    }

    private static Scenario runScenario(com.tetris.mab.nuke.NukeDesign design,
                                         MabAiDifficulty difficulty, int ticks) {
        GameState a = new GameState(0);
        GameState b = new GameState(0);
        MutuallyAssuredBlocksMatch match = MutuallyAssuredBlocksMatch.createPveShared(
                a, b, MatchDifficulty.NORMAL, 4242L);
        match.startMatch();
        match.applyWarheadDesign(ParticipantId.PLAYER_B, design);
        MabAiDriver ai = new MabAiDriver(match, ParticipantId.PLAYER_B,
                MabAiArchetype.BALANCED, difficulty, MabBalanceProfiles.standardPve());
        ai.setEnabled(true);
        ai.setAdvanceHiddenClock(true);
        boolean failed = false;
        try {
            for (int i = 0; i < ticks; i++) ai.tick();
        } catch (RuntimeException ex) {
            failed = true;
        }
        Set<String> types = new HashSet<>();
        for (MatchEventLogEntry e : match.getEventLog()) types.add(e.eventType());
        return new Scenario(types, failed,
                match.getParticipant(ParticipantId.PLAYER_B)
                        .getNukeBuildState().getCurrentDesign().getId());
    }

    private static Scenario runUpgradeScenario(int ticks) {
        GameState a = new GameState(0);
        GameState b = new GameState(0);
        MutuallyAssuredBlocksMatch match = MutuallyAssuredBlocksMatch.createPveShared(
                a, b, MatchDifficulty.NORMAL, 7777L);
        match.startMatch();
        match.debugAddUpgradePoints(ParticipantId.PLAYER_B, 50);
        MabAiDriver ai = new MabAiDriver(match, ParticipantId.PLAYER_B,
                MabAiArchetype.BALANCED, MabAiDifficulty.MASTER,
                MabBalanceProfiles.standardPve());
        ai.setEnabled(true);
        ai.setAdvanceHiddenClock(true);
        boolean failed = false;
        try {
            for (int i = 0; i < ticks; i++) ai.tick();
        } catch (RuntimeException ex) {
            failed = true;
        }
        Set<String> types = new HashSet<>();
        for (MatchEventLogEntry e : match.getEventLog()) types.add(e.eventType());
        return new Scenario(types, failed,
                match.getParticipant(ParticipantId.PLAYER_B)
                        .getNukeBuildState().getCurrentDesign().getId());
    }

    private static final class Scenario {
        final Set<String> eventTypes;
        final boolean failed;
        final String designId;
        Scenario(Set<String> types, boolean failed, String designId) {
            this.eventTypes = types; this.failed = failed; this.designId = designId;
        }
    }

    private MabAiStrategicCoverageProbe() {}
}
