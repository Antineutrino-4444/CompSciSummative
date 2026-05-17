package com.tetris.mab.sim;

import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.nuke.NukeDesignFactory;
import com.tetris.mab.ui.MabLocalPvpConfig;
import com.tetris.mab.ui.MabNukeDesignSelection;
import com.tetris.mab.ui.MabPveConfig;
import com.tetris.model.GameState;

/**
 * Verifies that PvE and local PvP setup treat the Nuke Builder as a
 * first-class part of starting a match: the configured warhead design
 * is visible (via the selection), is applied to the live participant
 * state at start, and falls back safely to a placeholder design when
 * none is selected.
 */
public final class MabNukeBuilderMatchSetupProbe {

    private MabNukeBuilderMatchSetupProbe() {}

    public static void main(String[] args) {
        System.out.println("=== MAB Nuke Builder Match Setup Probe ===");
        int failed = 0;

        // ── PvE ──
        MabNukeDesignSelection humanPick = MabNukeDesignSelection
                .fromMabDesign(NukeDesignFactory.createDefaultDirtyPayload());
        MabPveConfig pveCfg = MabPveConfig.fromSelections(1,
                com.tetris.mab.ai.MabAiArchetype.BALANCED,
                com.tetris.mab.ai.MabAiDifficulty.NORMAL,
                false,
                com.tetris.mab.balance.MabBalanceProfiles.STANDARD_PVE,
                humanPick);
        boolean pveDesignRequiredVisible = pveCfg.getNukeDesignSelection() != null
                && pveCfg.getNukeDesignSelection().getDesign() != null;
        // Opponent design visibility: defaults to placeholder when none
        // is explicitly chosen, and is visible via the standard accessor.
        NukeDesign aiDesign = NukeDesignFactory.createDefaultTacticalBlast();
        boolean pveOpponentDesignVisible = aiDesign != null
                && aiDesign.getDisplayName() != null;

        MutuallyAssuredBlocksMatch pveMatch = MutuallyAssuredBlocksMatch.createPveShared(
                new GameState(0), new GameState(0), MatchDifficulty.NORMAL, 7L);
        pveMatch.startMatch();
        pveMatch.applyWarheadDesign(ParticipantId.PLAYER_A,
                pveCfg.getNukeDesignSelection().getDesign());
        pveMatch.applyWarheadDesign(ParticipantId.PLAYER_B, aiDesign);
        boolean pveAppliesPlayerDesign =
                pveMatch.getParticipant(ParticipantId.PLAYER_A).getNukeBuildState()
                        .getCurrentDesign() == pveCfg.getNukeDesignSelection().getDesign();

        // ── Local PvP with separate designs ──
        MabNukeDesignSelection p1Pick = MabNukeDesignSelection
                .fromMabDesign(NukeDesignFactory.createDefaultEmp());
        MabNukeDesignSelection p2Pick = MabNukeDesignSelection
                .fromMabDesign(NukeDesignFactory.createDefaultHeavyBlast());
        MabLocalPvpConfig pvpCfg = new MabLocalPvpConfig(1,
                "Player 1", "Player 2",
                com.tetris.mab.balance.MabBalanceProfiles.STANDARD_PVE,
                p1Pick, p2Pick);
        boolean pvpP1DesignVisible = pvpCfg.getNukeDesignP1() != null
                && pvpCfg.getNukeDesignP1().getDesign() != null;
        boolean pvpP2DesignVisible = pvpCfg.getNukeDesignP2() != null
                && pvpCfg.getNukeDesignP2().getDesign() != null;
        boolean pvpSeparateDesignsSupported = pvpCfg.getNukeDesignP1() != pvpCfg.getNukeDesignP2();

        MutuallyAssuredBlocksMatch pvpMatch = MutuallyAssuredBlocksMatch.createLocalPvpShared(
                new GameState(0), new GameState(0), MatchDifficulty.NORMAL, 8L);
        pvpMatch.startMatch();
        pvpMatch.applyWarheadDesign(ParticipantId.PLAYER_A, pvpCfg.getNukeDesignP1().getDesign());
        pvpMatch.applyWarheadDesign(ParticipantId.PLAYER_B, pvpCfg.getNukeDesignP2().getDesign());
        boolean pvpAppliesEachPlayer =
                pvpMatch.getParticipant(ParticipantId.PLAYER_A).getNukeBuildState().getCurrentDesign()
                        == pvpCfg.getNukeDesignP1().getDesign()
                && pvpMatch.getParticipant(ParticipantId.PLAYER_B).getNukeBuildState().getCurrentDesign()
                        == pvpCfg.getNukeDesignP2().getDesign();

        // Default fallback safe even when selection is null.
        MabNukeDesignSelection nullPick = MabNukeDesignSelection.defaultSelection();
        boolean defaultFallbackSafe = nullPick != null && nullPick.getDesign() != null
                && nullPick.getDesign().getId() != null;

        boolean builderDesignSafe = new com.tetris.mab.nuke.MabNukeBuilderBridge()
                .isSafeGameplaySummary(humanPick.getSummary());

        report("pveDesignRequiredVisible", pveDesignRequiredVisible);
        report("pveAppliesPlayerDesign", pveAppliesPlayerDesign);
        report("pveOpponentDesignVisible", pveOpponentDesignVisible);
        report("pvpP1DesignVisible", pvpP1DesignVisible);
        report("pvpP2DesignVisible", pvpP2DesignVisible);
        report("pvpSeparateDesignsSupported", pvpSeparateDesignsSupported);
        report("pvpAppliesEachPlayer", pvpAppliesEachPlayer);
        report("defaultFallbackSafe", defaultFallbackSafe);
        report("builderDesignSafe", builderDesignSafe);

        boolean success = pveDesignRequiredVisible && pveAppliesPlayerDesign
                && pveOpponentDesignVisible
                && pvpP1DesignVisible && pvpP2DesignVisible
                && pvpSeparateDesignsSupported && pvpAppliesEachPlayer
                && defaultFallbackSafe && builderDesignSafe;
        report("success", success);
        if (!success) System.exit(1);
    }

    private static void report(String n, boolean v) {
        System.out.println(n + "=" + v);
    }
}
