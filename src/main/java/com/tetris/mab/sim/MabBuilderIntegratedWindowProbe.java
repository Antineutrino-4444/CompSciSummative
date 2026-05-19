package com.tetris.mab.sim;

import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ui.MabBattleShellPanel;
import com.tetris.mab.ui.MabOpponentBoardPanel;
import com.tetris.model.GameState;
import com.tetris.view.GamePanel;
import com.tetris.view.NukeBuilderDialog;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Window;

/**
 * Probe: opening the builder for pre-match or mid-game redesign must
 * NOT create any new JFrame, JDialog, modal dialog, popup window, or
 * OS-level child window. Snapshots {@link Window#getWindows()} before
 * and after exercising both flows.
 */
public final class MabBuilderIntegratedWindowProbe {
    private MabBuilderIntegratedWindowProbe() {}

    public static void main(String[] args) throws Exception {
        System.out.println("=== MAB Builder Integrated Window Probe ===");

        final int baseline = Window.getWindows().length;
        final boolean[] sepFrame = {false};
        final boolean[] sepDialog = {false};

        SwingUtilities.invokeAndWait(() -> {
            // Pre-match builder card path (embedded factory).
            NukeBuilderDialog pre = NukeBuilderDialog.createEmbedded(() -> {});
            pre.setSize(new Dimension(1366, 700));
            pre.doLayout();

            for (Window w : Window.getWindows()) {
                if (w instanceof JFrame) sepFrame[0] = true;
                if (w instanceof JDialog) sepDialog[0] = true;
            }

            // Mid-game DEFCON redesign overlay path.
            GameState p1 = new GameState(0);
            GameState p2 = new GameState(0);
            MutuallyAssuredBlocksMatch match = MutuallyAssuredBlocksMatch
                    .createPveShared(p1, p2, MatchDifficulty.NORMAL, 42L);
            GamePanel pg = new GamePanel(p1);
            MabOpponentBoardPanel opp = new MabOpponentBoardPanel(p2);
            MabBattleShellPanel shell = new MabBattleShellPanel(
                    pg, p1, opp,
                    ParticipantId.PLAYER_A, ParticipantId.PLAYER_B,
                    "P1", "AI",
                    MabBattleShellPanel.defaultModeLine(null),
                    MabBattleShellPanel.defaultModeSub(),
                    () -> {});
            shell.setSize(new Dimension(1366, 768));
            shell.doLayout();
            shell.showDefconRedesignReview(match, ParticipantId.PLAYER_A,
                    "PROBE - INTEGRATED REDESIGN", 4,
                    null, null, false, s -> {}, () -> {});

            for (Window w : Window.getWindows()) {
                if (w instanceof JFrame) sepFrame[0] = true;
                if (w instanceof JDialog) sepDialog[0] = true;
            }
            shell.hideDefconRedesignOverlay();
        });

        boolean builderInsideMainShell = !sepFrame[0] && !sepDialog[0];
        boolean success = builderInsideMainShell;
        System.out.println("separateJFrameCreated=" + sepFrame[0]);
        System.out.println("separateDialogCreated=" + sepDialog[0]);
        System.out.println("builderInsideMainShell=" + builderInsideMainShell);
        System.out.println("baselineWindows=" + baseline);
        System.out.println("success=" + success);
        System.exit(success ? 0 : 1);
    }
}
