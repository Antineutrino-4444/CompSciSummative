package com.tetris.mab.sim;

import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ui.MabBattleShellPanel;
import com.tetris.mab.ui.MabDefconRedesignOverlayPanel;
import com.tetris.mab.ui.MabOpponentBoardPanel;
import com.tetris.model.GameState;
import com.tetris.view.GamePanel;

import java.awt.Dimension;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Probe: the player-facing Nuke Builder paths must NOT present a
 * complete preset nuke list. Walks the DEFCON redesign overlay
 * component tree and asserts no {@link JComboBox} is reachable —
 * the integrated builder edits slot/part controls instead.
 */
public final class MabNoPresetNukeListProbe {
    private MabNoPresetNukeListProbe() {}

    public static void main(String[] args) throws Exception {
        System.out.println("=== MAB No Preset Nuke List Probe ===");
        AtomicBoolean noCombo = new AtomicBoolean(true);
        AtomicBoolean templatesEditable = new AtomicBoolean(true);
        GameState p1 = new GameState(0);
        GameState p2 = new GameState(0);
        MutuallyAssuredBlocksMatch match = MutuallyAssuredBlocksMatch
                .createPveShared(p1, p2, MatchDifficulty.NORMAL, 42L);
        SwingUtilities.invokeAndWait(() -> {
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
                    "PROBE - PRESET-LESS BUILDER", 4,
                    null, null, false, s -> {}, () -> {});
            MabDefconRedesignOverlayPanel overlay = shell.getDefconRedesignOverlay();
            if (containsJComboBox(overlay)) noCombo.set(false);
            // Builder seed defaults — the embedded editable builder
            // is the only design entry. No "complete nuke" list.
            if (overlay.getEmbeddedBuilderForProbe() == null) templatesEditable.set(false);
            shell.hideDefconRedesignOverlay();
        });
        boolean success = noCombo.get() && templatesEditable.get();
        System.out.println("playerFacingCompletePresetList=" + (!noCombo.get()));
        System.out.println("templatesOnlyAsEditableStartingPoints=" + templatesEditable.get());
        System.out.println("success=" + success);
        System.exit(success ? 0 : 1);
    }

    private static boolean containsJComboBox(JComponent root) {
        if (root instanceof JComboBox) return true;
        for (Component c : root.getComponents()) {
            if (c instanceof JComboBox) return true;
            if (c instanceof Container container) {
                for (Component cc : container.getComponents()) {
                    if (cc instanceof JComponent jc && containsJComboBox(jc)) return true;
                }
            }
        }
        return false;
    }
}
