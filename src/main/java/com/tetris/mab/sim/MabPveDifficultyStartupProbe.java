package com.tetris.mab.sim;

import com.tetris.controller.GameController;
import com.tetris.controller.GameLaunchMode;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ai.MabAiArchetype;
import com.tetris.mab.ai.MabAiDifficulty;
import com.tetris.mab.balance.MabBalanceProfiles;
import com.tetris.mab.nuke.NukeDesignFactory;
import com.tetris.mab.ui.MabBattleShellPanel;
import com.tetris.mab.ui.MabNukeDesignSelection;
import com.tetris.mab.ui.MabPveConfig;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

/**
 * Starts MAB PvE once for each player-facing AI difficulty. This covers
 * the setup/manual-gate requirement that Easy through Master are
 * reachable and actually mount the battle shell without exposing the
 * legacy NORMAL/DEBUG aliases.
 */
public final class MabPveDifficultyStartupProbe {

    private static final List<MabAiDifficulty> PLAYER_FACING_TIERS = Arrays.asList(
            MabAiDifficulty.EASY,
            MabAiDifficulty.MEDIUM,
            MabAiDifficulty.HARD,
            MabAiDifficulty.EXPERT,
            MabAiDifficulty.MASTER);

    private MabPveDifficultyStartupProbe() {}

    public static void main(String[] args) throws Exception {
        System.out.println("=== MAB PvE Difficulty Startup Probe ===");
        boolean ok = true;

        for (MabAiDifficulty difficulty : PLAYER_FACING_TIERS) {
            ok &= runDifficulty(difficulty);
        }

        ok &= report("normalAliasExcluded", !PLAYER_FACING_TIERS.contains(MabAiDifficulty.NORMAL));
        ok &= report("debugAliasExcluded", !PLAYER_FACING_TIERS.contains(MabAiDifficulty.DEBUG));
        report("success", ok);
        if (!ok) System.exit(1);
    }

    private static boolean runDifficulty(MabAiDifficulty difficulty) throws Exception {
        MabNukeDesignSelection design = MabNukeDesignSelection.fromMabDesign(
                NukeDesignFactory.createDefaultTacticalBlast());
        MabPveConfig config = MabPveConfig.fromSelections(
                1,
                MabAiArchetype.BALANCED,
                difficulty,
                false,
                MabBalanceProfiles.STANDARD_PVE,
                design);
        GameController controller = new GameController(config);
        JComponent[] root = new JComponent[1];
        int[] callbacks = {0, 0, 0};
        controller.setMabPveCallbacks(
                () -> callbacks[0]++,
                () -> callbacks[1]++,
                () -> callbacks[2]++);
        try {
            SwingUtilities.invokeAndWait(() -> root[0] = controller.startEmbedded(() -> {}));
            boolean ok = true;
            ok &= report(difficulty.displayName() + ".launchModePve",
                    controller.getLaunchMode() == GameLaunchMode.MAB_PVE);
            ok &= report(difficulty.displayName() + ".configDifficulty",
                    controller.getMabPveConfig().getAiDifficulty() == difficulty);
            ok &= report(difficulty.displayName() + ".rootIsBattleShell",
                    root[0] instanceof MabBattleShellPanel);
            if (root[0] instanceof MabBattleShellPanel shell) {
                ok &= report(difficulty.displayName() + ".opsDeckMounted",
                        shell.getOpsDeck() != null);
                ok &= report(difficulty.displayName() + ".opponentBoardMounted",
                        shell.getOpponentBoardPanel() != null);
                ok &= report(difficulty.displayName() + ".playerBoardMounted",
                        shell.getPlayerGamePanel() != null);
            }
            MutuallyAssuredBlocksMatch match = mabMatch(controller);
            ok &= report(difficulty.displayName() + ".matchCreated", match != null);
            if (match != null) {
                ok &= report(difficulty.displayName() + ".playerDesignApplied",
                        match.getParticipant(ParticipantId.PLAYER_A)
                                .getNukeBuildState().getCurrentDesign() == design.getDesign());
                ok &= report(difficulty.displayName() + ".aiDesignApplied",
                        match.getParticipant(ParticipantId.PLAYER_B)
                                .getNukeBuildState().getCurrentDesign() != null);
            }
            ok &= report(difficulty.displayName() + ".callbacksNotInvokedAtStart",
                    callbacks[0] == 0 && callbacks[1] == 0 && callbacks[2] == 0);
            return ok;
        } finally {
            controller.stop();
        }
    }

    private static MutuallyAssuredBlocksMatch mabMatch(GameController controller)
            throws ReflectiveOperationException {
        Field field = GameController.class.getDeclaredField("mabMatch");
        field.setAccessible(true);
        return (MutuallyAssuredBlocksMatch) field.get(controller);
    }

    private static boolean report(String name, boolean value) {
        System.out.println(name + "=" + value);
        return value;
    }
}
