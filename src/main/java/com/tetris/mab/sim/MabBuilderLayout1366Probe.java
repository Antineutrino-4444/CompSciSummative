package com.tetris.mab.sim;

import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ui.MabBattleShellPanel;
import com.tetris.mab.ui.MabDefconRedesignOverlayPanel;
import com.tetris.mab.ui.MabOpponentBoardPanel;
import com.tetris.model.GameState;
import com.tetris.view.GamePanel;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;

/**
 * Probe: at 1366×768 the integrated builder overlay fits the
 * viewport with the CANCEL button visible and the embedded builder's
 * key hint (top-right) showing the configured hard-drop key as the
 * confirm trigger.
 */
public final class MabBuilderLayout1366Probe {
    private MabBuilderLayout1366Probe() {}

    public static void main(String[] args) throws Exception {
        System.out.println("=== MAB Builder Layout 1366x768 Probe ===");
        final boolean[] fits        = {false};
        final boolean[] confirmHint = {false};
        final boolean[] cancelIn    = {false};

        GameState p1s = new GameState(0);
        GameState p2s = new GameState(0);
        MutuallyAssuredBlocksMatch match = MutuallyAssuredBlocksMatch
                .createPveShared(p1s, p2s, MatchDifficulty.NORMAL, 42L);
        SwingUtilities.invokeAndWait(() -> {
            GamePanel pg = new GamePanel(p1s);
            MabOpponentBoardPanel opp = new MabOpponentBoardPanel(p2s);
            MabBattleShellPanel shell = new MabBattleShellPanel(
                    pg, p1s, opp,
                    ParticipantId.PLAYER_A, ParticipantId.PLAYER_B,
                    "P1", "AI",
                    MabBattleShellPanel.defaultModeLine(null),
                    MabBattleShellPanel.defaultModeSub(),
                    () -> {});
            shell.setSize(new Dimension(1366, 768));
            shell.doLayout();

            shell.showDefconRedesignReview(match, ParticipantId.PLAYER_A,
                    "PROBE - LAYOUT", 4,
                    null, null, false, s -> {}, () -> {});
            shell.doLayout();

            MabDefconRedesignOverlayPanel overlay = shell.getDefconRedesignOverlay();
            overlay.setSize(new Dimension(1366, 768));
            overlay.doLayout();
            Dimension pref = overlay.getPreferredSize();
            fits[0] = pref.width <= 1366 && pref.height <= 768;

            // Confirm is shown as a hint in the embedded builder's
            // top-right key-hint label. Walk the label tree for a
            // visible label that contains "confirm".
            confirmHint[0] = findLabelContaining(overlay, "confirm");
            JButton cancel = findButton(overlay, "CANCEL");
            cancelIn[0] = cancel != null && cancel.isVisible();
            shell.hideDefconRedesignOverlay();
        });

        boolean success = fits[0] && confirmHint[0] && cancelIn[0];
        System.out.println("overlayPreferredFits1366x768=" + fits[0]);
        System.out.println("confirmKeyHintVisible=" + confirmHint[0]);
        System.out.println("cancelButtonVisibleNoScroll=" + cancelIn[0]);
        System.out.println("success=" + success);
        System.exit(success ? 0 : 1);
    }

    private static boolean findLabelContaining(Container root, String needle) {
        if (root == null) return false;
        String n = needle.toLowerCase();
        for (Component c : root.getComponents()) {
            if (c instanceof JLabel l && c.isVisible()) {
                String t = l.getText();
                if (t != null && t.toLowerCase().contains(n)) return true;
            }
            if (c instanceof Container cc) {
                if (findLabelContaining(cc, needle)) return true;
            }
        }
        return false;
    }

    private static JButton findButton(Container root, String text) {
        if (root == null) return null;
        for (Component c : root.getComponents()) {
            if (c instanceof JButton b && text.equals(b.getText())) return b;
            if (c instanceof Container cc) {
                JButton hit = findButton(cc, text);
                if (hit != null) return hit;
            }
        }
        return null;
    }

}
