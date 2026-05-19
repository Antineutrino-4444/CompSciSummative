package com.tetris.mab.sim;

import com.tetris.controller.GameController;
import com.tetris.controller.GameLaunchMode;
import com.tetris.controller.LocalInputRouter;
import com.tetris.mab.MatchMode;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.nuke.NukeDesignFactory;
import com.tetris.mab.ui.MabBattleShellPanel;
import com.tetris.mab.ui.MabLocalPvpConfig;
import com.tetris.mab.ui.MabNukeDesignSelection;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.lang.reflect.Field;

/**
 * Starts the actual same-keyboard local PvP controller path. This
 * complements the model-level local PvP probe by checking that setup
 * designs, shell mounting, local input wiring, and menu callbacks are
 * present through the UI controller lifecycle.
 */
public final class MabLocalPvpStartupProbe {

    private MabLocalPvpStartupProbe() {}

    public static void main(String[] args) throws Exception {
        System.out.println("=== MAB Local PvP Startup Probe ===");

        MabNukeDesignSelection p1Design = MabNukeDesignSelection.fromMabDesign(
                NukeDesignFactory.createDefaultEmp());
        MabNukeDesignSelection p2Design = MabNukeDesignSelection.fromMabDesign(
                NukeDesignFactory.createDefaultHeavyBlast());
        MabLocalPvpConfig config = new MabLocalPvpConfig(
                1, "Alpha", "Beta",
                com.tetris.mab.balance.MabBalanceProfiles.STANDARD_PVE,
                p1Design, p2Design);
        GameController controller = new GameController(config);
        int[] callbacks = {0, 0, 0};
        controller.setMabPveCallbacks(
                () -> callbacks[0]++,
                () -> callbacks[1]++,
                () -> callbacks[2]++);

        JComponent[] root = new JComponent[1];
        boolean ok = true;
        try {
            SwingUtilities.invokeAndWait(() -> root[0] = controller.startEmbedded(() -> {}));
            ok &= report("launchModeLocalPvp",
                    controller.getLaunchMode() == GameLaunchMode.MAB_LOCAL_PVP);
            ok &= report("configP1Name", "Alpha".equals(controller.getMabLocalPvpConfig().getPlayerAName()));
            ok &= report("configP2Name", "Beta".equals(controller.getMabLocalPvpConfig().getPlayerBName()));
            ok &= report("rootIsBattleShell", root[0] instanceof MabBattleShellPanel);

            if (root[0] instanceof MabBattleShellPanel shell) {
                ok &= report("opsDeckMounted", shell.getOpsDeck() != null);
                ok &= report("playerBoardMounted", shell.getPlayerGamePanel() != null);
                ok &= report("opponentBoardMounted", shell.getOpponentBoardPanel() != null);
                ok &= report("localPvpInputAdapterPresent", shell.getLocalPvpInputAdapter() != null);
            }

            MutuallyAssuredBlocksMatch match = mabMatch(controller);
            ok &= report("matchCreated", match != null);
            if (match != null) {
                ok &= report("modePvpLocal", match.getMatchMode() == MatchMode.PVP_LOCAL);
                ok &= report("sharedPieceSequence", match.hasSharedPieceSequence());
                ok &= report("p1DesignApplied",
                        match.getParticipant(ParticipantId.PLAYER_A)
                                .getNukeBuildState().getCurrentDesign() == p1Design.getDesign());
                ok &= report("p2DesignApplied",
                        match.getParticipant(ParticipantId.PLAYER_B)
                                .getNukeBuildState().getCurrentDesign() == p2Design.getDesign());
            }

            ok &= report("localInputRouterPresent", localInputRouter(controller) != null);
            ok &= report("callbacksNotInvokedAtStart",
                    callbacks[0] == 0 && callbacks[1] == 0 && callbacks[2] == 0);

            SwingUtilities.invokeAndWait(controller::restart);
            SwingUtilities.invokeAndWait(() -> {});
            ok &= report("restartCallbackOnce", callbacks[0] == 1);
            ok &= report("setupCallbackStillZero", callbacks[2] == 0);

            SwingUtilities.invokeAndWait(controller::exitStage);
            SwingUtilities.invokeAndWait(() -> {});
            ok &= report("mainMenuCallbackOnce", callbacks[1] == 1);
        } finally {
            controller.stop();
        }

        report("success", ok);
        if (!ok) System.exit(1);
    }

    private static MutuallyAssuredBlocksMatch mabMatch(GameController controller)
            throws ReflectiveOperationException {
        Field field = GameController.class.getDeclaredField("mabMatch");
        field.setAccessible(true);
        return (MutuallyAssuredBlocksMatch) field.get(controller);
    }

    private static LocalInputRouter localInputRouter(GameController controller)
            throws ReflectiveOperationException {
        Field field = GameController.class.getDeclaredField("localPvpInputRouter");
        field.setAccessible(true);
        return (LocalInputRouter) field.get(controller);
    }

    private static boolean report(String name, boolean value) {
        System.out.println(name + "=" + value);
        return value;
    }
}
