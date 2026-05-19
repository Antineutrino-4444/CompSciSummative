package com.tetris.mab.sim;

import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.nuke.MabNukeBuilderBridge;
import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.nuke.NukeDesignFactory;
import com.tetris.mab.ui.MabLocalPvpConfig;
import com.tetris.mab.ui.MabNukeDesignSelection;
import com.tetris.mab.ui.MabPveConfig;
import com.tetris.model.GameState;
import com.tetris.model.nuke.NukePart;
import com.tetris.model.nuke.NukeSlot;

/** Step 26 - verifies safe MAB setup integration for the Nuke Builder. */
public final class MabNukeBuilderIntegrationProbe {

    private MabNukeBuilderIntegrationProbe() {}

    public static void main(String[] args) {
        System.out.println("=== MAB Nuke Builder Integration Probe ===");

        MabNukeBuilderBridge bridge = new MabNukeBuilderBridge();
        com.tetris.model.nuke.NukeDesign educational = conceptualBuilderDesign();

        boolean builderModelAvailable = educational != null
                && educational.get(NukeSlot.CONFIGURATION) != NukePart.NONE;
        NukeDesign adapted = bridge.toMabDesign(educational);
        String summary = bridge.safeGameplaySummary(adapted);
        boolean adapterAvailable = adapted != null
                && bridge.isSafeGameplaySummary(summary);
        boolean defaultDesignAvailable = NukeDesignFactory.createDefaultPlaceholder() != null;
        boolean invalidDesignSafe = bridge.toMabDesign(new com.tetris.model.nuke.NukeDesign()) != null
                && bridge.fromUnknownBuilderObject("not a builder") != null;

        MabNukeDesignSelection selection =
                MabNukeDesignSelection.fromBuilderDesign(educational);
        boolean setupStoresSelection = selection.isBuilderDerived()
                && selection.getDesign() != null
                && selection.isSummarySafe();

        MabPveConfig pve = MabPveConfig.fromSelections(
                1,
                com.tetris.mab.ai.MabAiArchetype.BALANCED,
                com.tetris.mab.ai.MabAiDifficulty.NORMAL,
                false,
                com.tetris.mab.balance.MabBalanceProfiles.STANDARD_PVE,
                selection);
        GameState pveA = new GameState(1);
        GameState pveB = new GameState(1);
        MutuallyAssuredBlocksMatch pveMatch =
                MutuallyAssuredBlocksMatch.createPveShared(pveA, pveB,
                        MatchDifficulty.NORMAL, 9026L);
        pveMatch.getParticipant(ParticipantId.PLAYER_A)
                .getNukeBuildState()
                .setDesign(pve.getNukeDesignSelection().getDesign(), 5);
        boolean pveAppliesSelection = pveMatch.getParticipant(ParticipantId.PLAYER_A)
                .getNukeBuildState()
                .getCurrentDesign() == selection.getDesign();

        MabLocalPvpConfig pvp = new MabLocalPvpConfig(
                1, "Player 1", "Player 2",
                com.tetris.mab.balance.MabBalanceProfiles.STANDARD_PVE,
                selection, selection);
        GameState pvpA = new GameState(1);
        GameState pvpB = new GameState(1);
        MutuallyAssuredBlocksMatch pvpMatch =
                MutuallyAssuredBlocksMatch.createLocalPvpShared(pvpA, pvpB,
                        MatchDifficulty.NORMAL, 9027L);
        pvpMatch.getParticipant(ParticipantId.PLAYER_A)
                .getNukeBuildState()
                .setDesign(pvp.getNukeDesignP1().getDesign(), 5);
        pvpMatch.getParticipant(ParticipantId.PLAYER_B)
                .getNukeBuildState()
                .setDesign(pvp.getNukeDesignP2().getDesign(), 5);
        boolean pvpAppliesSelection = pvpMatch.getParticipant(ParticipantId.PLAYER_A)
                .getNukeBuildState()
                .getCurrentDesign() == selection.getDesign()
                && pvpMatch.getParticipant(ParticipantId.PLAYER_B)
                .getNukeBuildState()
                .getCurrentDesign() == selection.getDesign();
        boolean summarySafe = bridge.isSafeGameplaySummary(selection.getSummary());

        boolean success = builderModelAvailable && adapterAvailable
                && defaultDesignAvailable && invalidDesignSafe
                && setupStoresSelection && pveAppliesSelection
                && pvpAppliesSelection && summarySafe;

        report("builderModelAvailable", builderModelAvailable);
        report("adapterAvailable", adapterAvailable);
        report("defaultDesignAvailable", defaultDesignAvailable);
        report("invalidDesignSafe", invalidDesignSafe);
        report("setupStoresSelection", setupStoresSelection);
        report("pveAppliesSelection", pveAppliesSelection);
        report("pvpAppliesSelection", pvpAppliesSelection);
        report("summarySafe", summarySafe);
        report("success", success);
        if (!success) System.exit(1);
    }

    private static com.tetris.model.nuke.NukeDesign conceptualBuilderDesign() {
        com.tetris.model.nuke.NukeDesign d = new com.tetris.model.nuke.NukeDesign();
        choose(d, NukeSlot.CONFIGURATION, 3);
        choose(d, NukeSlot.FISSILE, 1);
        choose(d, NukeSlot.TAMPER, 1);
        choose(d, NukeSlot.INITIATOR, 1);
        choose(d, NukeSlot.IMPLOSION, 1);
        choose(d, NukeSlot.BOOST, 1);
        choose(d, NukeSlot.SECONDARY, 1);
        return d;
    }

    private static void choose(com.tetris.model.nuke.NukeDesign d,
                               NukeSlot slot,
                               int preferredIndex) {
        if (slot == null || slot.getOptions().isEmpty()) return;
        int idx = Math.max(0, Math.min(preferredIndex, slot.getOptions().size() - 1));
        d.set(slot, slot.getOptions().get(idx));
    }

    private static void report(String name, boolean value) {
        System.out.println(name + "=" + value);
    }
}
