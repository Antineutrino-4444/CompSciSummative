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
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Probe: cancel never invokes the confirm callback; confirm invokes
 * it exactly once even when triggered multiple times (held key,
 * double-click).
 */
public final class MabBuilderCancelConfirmProbe {
    private MabBuilderCancelConfirmProbe() {}

    public static void main(String[] args) throws Exception {
        System.out.println("=== MAB Builder Cancel/Confirm Probe ===");
        AtomicInteger confirms = new AtomicInteger();
        AtomicInteger cancels  = new AtomicInteger();

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

            // Cancel × 2 — only one cancel fires.
            shell.showDefconRedesignReview(match, ParticipantId.PLAYER_A,
                    "PROBE - CANCEL DOUBLE", 4,
                    null, null, false,
                    s -> confirms.incrementAndGet(),
                    cancels::incrementAndGet);
            MabDefconRedesignOverlayPanel overlay = shell.getDefconRedesignOverlay();
            JButton cancel = findButton(overlay, "CANCEL");
            if (cancel != null) { cancel.doClick(); cancel.doClick(); }

            // Re-open. Confirm × 3 — only one confirm fires.
            shell.showDefconRedesignReview(match, ParticipantId.PLAYER_A,
                    "PROBE - CONFIRM TRIPLE", 4,
                    null, null, false,
                    s -> confirms.incrementAndGet(),
                    cancels::incrementAndGet);
            // Hard-drop key now confirms; use probe hook three times
            // to verify the one-shot guard.
            overlay.triggerConfirmForProbe();
            overlay.triggerConfirmForProbe();
            overlay.triggerConfirmForProbe();
            shell.hideDefconRedesignOverlay();
        });

        boolean cancelOnce = cancels.get() == 1;
        boolean confirmOnce = confirms.get() == 1;
        boolean success = cancelOnce && confirmOnce;
        System.out.println("cancels=" + cancels.get() + " (expected 1)");
        System.out.println("confirms=" + confirms.get() + " (expected 1)");
        System.out.println("cancelOnce=" + cancelOnce);
        System.out.println("confirmOnce=" + confirmOnce);
        System.out.println("success=" + success);
        System.exit(success ? 0 : 1);
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
