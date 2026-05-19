package com.tetris.mab.sim;

import com.tetris.controller.GameController;
import com.tetris.controller.GameLaunchMode;
import com.tetris.mab.MatchEventLogEntry;
import com.tetris.mab.MatchPhase;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.NukeBuildState;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ai.MabAiArchetype;
import com.tetris.mab.ai.MabAiDifficulty;
import com.tetris.mab.balance.MabBalanceProfiles;
import com.tetris.mab.nuke.NukeDesignFactory;
import com.tetris.mab.ui.MabAiVsAiConfig;
import com.tetris.mab.ui.MabBattleShellPanel;
import com.tetris.mab.ui.MabLocalPvpConfig;
import com.tetris.mab.ui.MabNukeDesignSelection;
import com.tetris.mab.ui.MabPveConfig;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Verifies that a DEFCON escalation through the real embedded controller
 * path gives both participants a redesign step, including AI-controlled
 * participants in PvE and AI-vs-AI.
 */
public final class MabDefconAllParticipantsRedesignProbe {
    private MabDefconAllParticipantsRedesignProbe() {}

    public static void main(String[] args) throws Exception {
        System.out.println("=== MAB DEFCON All Participants Redesign Probe ===");
        boolean ok = true;
        ok &= runPve();
        ok &= runLocalPvp();
        ok &= runAiVsAi();
        report("success", ok);
        if (!ok) System.exit(1);
    }

    private static boolean runPve() throws Exception {
        MabNukeDesignSelection design = MabNukeDesignSelection.fromMabDesign(
                NukeDesignFactory.createDefaultTacticalBlast());
        MabPveConfig config = MabPveConfig.fromSelections(
                1,
                MabAiArchetype.BALANCED,
                MabAiDifficulty.HARD,
                false,
                MabBalanceProfiles.STANDARD_PVE,
                design);
        return runScenario("pve", new GameController(config), 1);
    }

    private static boolean runLocalPvp() throws Exception {
        MabLocalPvpConfig config = new MabLocalPvpConfig(
                1,
                "Alpha",
                "Beta",
                MabBalanceProfiles.STANDARD_PVE,
                MabNukeDesignSelection.fromMabDesign(NukeDesignFactory.createDefaultEmp()),
                MabNukeDesignSelection.fromMabDesign(NukeDesignFactory.createDefaultHeavyBlast()));
        return runScenario("localPvp", new GameController(config), 2);
    }

    private static boolean runAiVsAi() throws Exception {
        MabAiVsAiConfig config = new MabAiVsAiConfig(
                1,
                MabAiArchetype.TACTICAL_SPAMMER,
                MabAiDifficulty.EXPERT,
                MabAiArchetype.DOOMSDAY_HOARDER,
                MabAiDifficulty.HARD,
                MabBalanceProfiles.STANDARD_PVE);
        return runScenario("aiVsAi", new GameController(config), 0);
    }

    private static boolean runScenario(String label,
                                       GameController controller,
                                       int humanConfirmCount) throws Exception {
        JComponent[] root = new JComponent[1];
        try {
            SwingUtilities.invokeAndWait(() -> root[0] = controller.startEmbedded(() -> {}));
            boolean ok = true;
            ok &= report(label + ".launchMode", controller.getLaunchMode() != GameLaunchMode.NORMAL_TETRIS);
            ok &= report(label + ".rootIsBattleShell", root[0] instanceof MabBattleShellPanel);
            MutuallyAssuredBlocksMatch match = mabMatch(controller);
            MabBattleShellPanel shell = mabBattleShell(controller);
            ok &= report(label + ".matchCreated", match != null);
            ok &= report(label + ".shellCreated", shell != null);
            if (match == null || shell == null) return false;

            int beforeDefcon = match.getDefconState().getLevel();
            int beforeA = historySize(match, ParticipantId.PLAYER_A);
            int beforeB = historySize(match, ParticipantId.PLAYER_B);

            SwingUtilities.invokeAndWait(() ->
                    match.addEscalationAndRefresh(100, label + "-redesign-probe"));
            int afterDefcon = match.getDefconState().getLevel();
            ok &= report(label + ".defconEscalated", afterDefcon < beforeDefcon);

            ok &= report(label + ".reviewReached",
                    waitFor(() -> shell.isDefconRedesignOverlayVisible()
                            && shell.getDefconRedesignOverlay().getEmbeddedBuilderForProbe() != null,
                            6000));

            for (int i = 0; i < humanConfirmCount; i++) {
                final int participantIndex = i;
                SwingUtilities.invokeAndWait(() ->
                        shell.getDefconRedesignOverlay().triggerConfirmForProbe());
                ParticipantId expected = participantIndex == 0
                        ? ParticipantId.PLAYER_A : ParticipantId.PLAYER_B;
                int expectedHistory = expected == ParticipantId.PLAYER_A
                        ? beforeA + 1 : beforeB + 1;
                ok &= report(label + ".humanConfirm" + (i + 1),
                        waitFor(() -> historySize(match, expected) >= expectedHistory,
                                2000));
                if (i + 1 < humanConfirmCount) {
                    ok &= report(label + ".nextHumanReview" + (i + 2),
                            waitFor(() -> shell.isDefconRedesignOverlayVisible()
                                    && shell.getDefconRedesignOverlay().getEmbeddedBuilderForProbe() != null,
                                    2000));
                }
            }

            ok &= report(label + ".bothParticipantsRedesigned",
                    waitFor(() -> redesignedExactlyOnce(match, ParticipantId.PLAYER_A, beforeA, afterDefcon)
                                    && redesignedExactlyOnce(match, ParticipantId.PLAYER_B, beforeB, afterDefcon)
                                    && !shell.isDefconRedesignOverlayVisible()
                                    && match.getCurrentPhase() != MatchPhase.UPGRADE_PAUSE,
                            8000));
            ok &= report(label + ".startedEvent",
                    eventCount(match, "DEFCON_REDESIGN_STARTED") >= 1);
            ok &= report(label + ".finishedEvent",
                    eventCount(match, "DEFCON_REDESIGN_FINISHED") >= 1);
            return ok;
        } finally {
            controller.stop();
        }
    }

    private static boolean redesignedExactlyOnce(MutuallyAssuredBlocksMatch match,
                                                 ParticipantId participantId,
                                                 int beforeSize,
                                                 int defconLevel) {
        List<NukeBuildState.DesignHistoryEntry> history = match.getParticipant(participantId)
                .getNukeBuildState()
                .getDesignHistory();
        if (history.size() != beforeSize + 1) return false;
        NukeBuildState.DesignHistoryEntry last =
                history.get(history.size() - 1);
        return "redesign".equals(last.source()) && last.defconLevel() == defconLevel;
    }

    private static int historySize(MutuallyAssuredBlocksMatch match,
                                   ParticipantId participantId) {
        return match.getParticipant(participantId)
                .getNukeBuildState()
                .getDesignHistory()
                .size();
    }

    private static int eventCount(MutuallyAssuredBlocksMatch match, String eventType) {
        int count = 0;
        for (MatchEventLogEntry e : match.getEventLog()) {
            if (eventType.equals(e.eventType())) count++;
        }
        return count;
    }

    private static boolean waitFor(BooleanSupplier condition, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            final boolean[] ok = { false };
            SwingUtilities.invokeAndWait(() -> ok[0] = condition.getAsBoolean());
            if (ok[0]) return true;
            Thread.sleep(50);
        }
        return false;
    }

    private static MutuallyAssuredBlocksMatch mabMatch(GameController controller)
            throws ReflectiveOperationException {
        Field field = GameController.class.getDeclaredField("mabMatch");
        field.setAccessible(true);
        return (MutuallyAssuredBlocksMatch) field.get(controller);
    }

    private static MabBattleShellPanel mabBattleShell(GameController controller)
            throws ReflectiveOperationException {
        Field field = GameController.class.getDeclaredField("mabBattleShell");
        field.setAccessible(true);
        return (MabBattleShellPanel) field.get(controller);
    }

    private static boolean report(String name, boolean value) {
        System.out.println(name + "=" + value);
        return value;
    }
}
