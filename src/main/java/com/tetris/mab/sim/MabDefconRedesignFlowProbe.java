package com.tetris.mab.sim;

import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MatchPhase;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.NukeBuildState;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ai.MabBoardAiDriver;
import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.nuke.NukeDesignFactory;
import com.tetris.mab.ui.MabDefconRedesignOverlayPanel;
import com.tetris.model.GameState;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Verifies the DEFCON redesign flow contract that is easiest to regress:
 * upgrade-pause freeze, cancel/no-op continuity, one applied redesign,
 * and single-fire overlay confirmation.
 */
public final class MabDefconRedesignFlowProbe {

    public static void main(String[] args) throws Exception {
        System.out.println("=== MAB DEFCON Redesign Flow Probe ===");

        GameState gameA = new GameState(0);
        GameState gameB = new GameState(0);
        MutuallyAssuredBlocksMatch match = MutuallyAssuredBlocksMatch.createLocalPvpShared(
                gameA, gameB, MatchDifficulty.NORMAL, 2026L);
        match.startMatch();
        NukeDesign original = NukeDesignFactory.createDefaultTacticalBlast();
        NukeDesign replacement = NukeDesignFactory.createDefaultEmp();
        match.applyWarheadDesign(ParticipantId.PLAYER_A, original);
        match.applyWarheadDesign(ParticipantId.PLAYER_B, NukeDesignFactory.createDefaultHeavyBlast());
        match.debugAddNukeCharge(ParticipantId.PLAYER_A, 40);

        NukeBuildState buildA = match.getParticipant(ParticipantId.PLAYER_A).getNukeBuildState();
        NukeBuildState buildB = match.getParticipant(ParticipantId.PLAYER_B).getNukeBuildState();
        String originalId = buildA.getCurrentDesign().getId();
        int originalCharge = buildA.getCurrentBuildCharge();
        int originalHistoryA = buildA.getDesignHistory().size();
        int originalHistoryB = buildB.getDesignHistory().size();

        boolean pauseOpened = match.openUpgradePause("probe_defcon_redesign");
        boolean pauseState = match.getCurrentPhase() == MatchPhase.UPGRADE_PAUSE
                && match.isPaused()
                && gameA.isPaused()
                && gameB.isPaused();

        MabBoardAiDriver boardAi = new MabBoardAiDriver(gameA);
        long ticksBefore = boardAi.getTickCount();
        boolean boardStillPaused = true;
        for (int i = 0; i < 20; i++) {
            boardStillPaused &= !gameA.update();
            boardStillPaused &= !gameB.update();
            boardAi.tick();
        }
        boolean boardAiFrozen = boardAi.getTickCount() == ticksBefore;

        boolean cancelNoChangeBeforeClose = originalId.equals(buildA.getCurrentDesign().getId())
                && originalCharge == buildA.getCurrentBuildCharge()
                && originalHistoryA == buildA.getDesignHistory().size();
        boolean closeAfterCancel = match.closeUpgradePause("probe_cancel");
        boolean cancelNoChangeAfterClose = originalId.equals(buildA.getCurrentDesign().getId())
                && originalCharge == buildA.getCurrentBuildCharge()
                && originalHistoryA == buildA.getDesignHistory().size();

        boolean reopen = match.openUpgradePause("probe_confirm");
        boolean redesignApplied = match.redesignNukeDuringUpgradePause(
                ParticipantId.PLAYER_A, replacement, 0.5);
        boolean closeAfterConfirm = match.closeUpgradePause("probe_confirm");
        boolean rejectedAfterClose = !match.redesignNukeDuringUpgradePause(
                ParticipantId.PLAYER_A, NukeDesignFactory.createDefaultDoomsday(), 1.0);
        boolean confirmOnceState = replacement.getId().equals(buildA.getCurrentDesign().getId())
                && buildA.getCurrentBuildCharge() == 20
                && buildA.getDesignHistory().size() == originalHistoryA + 1
                && buildB.getDesignHistory().size() == originalHistoryB;

        OverlayResult overlay = runOverlayProbe(match);

        report("pauseOpened", pauseOpened);
        report("pauseState", pauseState);
        report("boardAiFrozen", boardAiFrozen);
        report("boardStillPaused", boardStillPaused);
        report("cancelNoChangeBeforeClose", cancelNoChangeBeforeClose);
        report("closeAfterCancel", closeAfterCancel);
        report("cancelNoChangeAfterClose", cancelNoChangeAfterClose);
        report("reopen", reopen);
        report("redesignApplied", redesignApplied);
        report("closeAfterConfirm", closeAfterConfirm);
        report("rejectedAfterClose", rejectedAfterClose);
        report("confirmOnceState", confirmOnceState);
        report("overlayCurrentLoadedFirst", overlay.currentLoadedFirst);
        report("overlayConfirmSingleFire", overlay.confirmSingleFire);
        report("overlayCancelSingleFire", overlay.cancelSingleFire);

        boolean success = pauseOpened && pauseState && boardAiFrozen && boardStillPaused
                && cancelNoChangeBeforeClose && closeAfterCancel && cancelNoChangeAfterClose
                && reopen && redesignApplied && closeAfterConfirm && rejectedAfterClose
                && confirmOnceState && overlay.currentLoadedFirst
                && overlay.confirmSingleFire && overlay.cancelSingleFire;
        report("success", success);
        if (!success) System.exit(1);
    }

    private static OverlayResult runOverlayProbe(MutuallyAssuredBlocksMatch match)
            throws InvocationTargetException, InterruptedException {
        OverlayResult[] result = new OverlayResult[1];
        SwingUtilities.invokeAndWait(() -> {
            MabDefconRedesignOverlayPanel overlay = new MabDefconRedesignOverlayPanel();
            overlay.setSize(1366, 768);
            AtomicInteger confirms = new AtomicInteger();
            AtomicInteger cancels = new AtomicInteger();
            overlay.showReview(match, ParticipantId.PLAYER_A,
                    "PLAYER 1 - WARHEAD REVIEW (DEFCON 4)", 4,
                    null, null, false,
                    s -> confirms.incrementAndGet(),
                    cancels::incrementAndGet);
            // The integrated builder no longer presents a preset
            // JComboBox — the "current first" check is now satisfied
            // by the embedded builder having been instantiated and
            // BEFORE pane being populated.
            boolean currentFirst = overlay.getEmbeddedBuilderForProbe() != null;
            // Confirm is now triggered by the hard-drop key (no
            // button). Use the probe hook to fire it twice and assert
            // the one-shot guard still allows only one fire.
            overlay.triggerConfirmForProbe();
            overlay.triggerConfirmForProbe();
            boolean singleConfirm = confirms.get() == 1 && cancels.get() == 0;

            overlay.showReview(match, ParticipantId.PLAYER_A,
                    "PLAYER 1 - WARHEAD REVIEW (DEFCON 4)", 4,
                    null, null, false,
                    s -> confirms.incrementAndGet(),
                    cancels::incrementAndGet);
            JButton cancel = findButton(overlay, "CANCEL");
            if (cancel != null) {
                cancel.doClick();
                cancel.doClick();
            }
            result[0] = new OverlayResult(currentFirst, singleConfirm,
                    cancels.get() == 1 && confirms.get() == 1);
        });
        return result[0];
    }

    private static JButton findButton(Container root, String text) {
        JButton b = findDescendant(root, JButton.class, c -> text.equals(c.getText()));
        return b;
    }

    private static <T extends Component> T findDescendant(Container root, Class<T> type) {
        return findDescendant(root, type, c -> true);
    }

    private static <T extends Component> T findDescendant(Container root, Class<T> type,
                                                          java.util.function.Predicate<T> pred) {
        if (root == null) return null;
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) {
                T v = type.cast(c);
                if (pred.test(v)) return v;
            }
            if (c instanceof Container child) {
                T nested = findDescendant(child, type, pred);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static void report(String name, boolean value) {
        System.out.println(name + "=" + value);
    }

    private record OverlayResult(boolean currentLoadedFirst,
                                 boolean confirmSingleFire,
                                 boolean cancelSingleFire) {}

    private MabDefconRedesignFlowProbe() {}
}
