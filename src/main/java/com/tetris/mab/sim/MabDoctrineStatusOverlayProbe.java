package com.tetris.mab.sim;

import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ui.MabBattleShellPanel;
import com.tetris.mab.ui.MabDoctrineStatusOverlayPanel;
import com.tetris.mab.ui.MabOpponentBoardPanel;
import com.tetris.mab.upgrade.draft.MabUpgradeDraftRegistry;
import com.tetris.model.GameState;
import com.tetris.view.GamePanel;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;

/**
 * Step 25 - verifies the owned-doctrine status overlay layout.
 */
public final class MabDoctrineStatusOverlayProbe {

    public static void main(String[] args) {
        System.out.println("=== MAB Doctrine Status Overlay Probe ===");
        boolean ok;
        try {
            final boolean[] holder = {true};
            SwingUtilities.invokeAndWait(() -> holder[0] = runOnEdt());
            ok = holder[0];
        } catch (Throwable t) {
            t.printStackTrace(System.out);
            ok = false;
        }
        System.out.println("success=" + ok);
        if (!ok) System.exit(1);
        System.exit(0);
    }

    private static boolean runOnEdt() {
        boolean ok = true;
        GameState a = new GameState(0);
        GameState b = new GameState(0);
        MutuallyAssuredBlocksMatch match = MutuallyAssuredBlocksMatch.createPveShared(
                a, b, MatchDifficulty.NORMAL, 2525L);
        match.startMatch();
        MabUpgradeDraftRegistry reg = new MabUpgradeDraftRegistry();
        var inv = match.getParticipant(ParticipantId.PLAYER_A).getUpgradeInventory();
        inv.addUpgrade(reg.getById("manual_override"));
        inv.addUpgrade(reg.getById("emp"));
        inv.addUpgrade(reg.getById("fast_fuse"));
        inv.addUpgrade(reg.getById("spin_launch_crew"));
        inv.addUpgrade(reg.getById("shelters"));

        MabDoctrineStatusOverlayPanel overlay = new MabDoctrineStatusOverlayPanel();
        overlay.showStatus(match, ParticipantId.PLAYER_A, () -> {});
        Dimension pref = overlay.getPreferredSize();
        overlay.setSize(1366, 768);
        forceLayoutTree(overlay);

        ok &= report("overlayInstantiates", overlay != null);
        ok &= report("overlayFits1366x768", pref.width <= 1366 && pref.height <= 768);
        ok &= report("ownedUpgradesGrouped", containsText(overlay, "CHARGE")
                && containsText(overlay, "TETRIS")
                && containsText(overlay, "SPIN")
                && containsText(overlay, "DEFENSE")
                && containsText(overlay, "POWER")
                && containsText(overlay, "INTEL")
                && containsText(overlay, "TEMPO"));
        ok &= report("activeDoctrinesShown", containsText(overlay, "MANUAL OVERRIDE")
                && containsText(overlay, "EMP"));
        ok &= report("multipleOwnedListScrollsOnlyListArea",
                overlay.getListScrollPane() instanceof JScrollPane
                        && overlay.getActivePanel().getComponentCount() >= 2);
        ok &= report("noDefaultWhitePanels", !findWhiteBackground(overlay));

        MabDoctrineStatusOverlayPanel empty = new MabDoctrineStatusOverlayPanel();
        MutuallyAssuredBlocksMatch emptyMatch = MutuallyAssuredBlocksMatch.createPveShared(
                new GameState(0), new GameState(0), MatchDifficulty.NORMAL, 99L);
        emptyMatch.startMatch();
        empty.showStatus(emptyMatch, ParticipantId.PLAYER_A, () -> {});
        ok &= report("emptyInventoryReadable", containsText(empty, "NO DOCTRINES LOADED"));

        GamePanel pg = new GamePanel(a);
        MabOpponentBoardPanel opp = new MabOpponentBoardPanel(b);
        MabBattleShellPanel shell = new MabBattleShellPanel(
                pg, a, opp, ParticipantId.PLAYER_A, ParticipantId.PLAYER_B,
                "STATION P1", "STATION AI",
                MabBattleShellPanel.defaultModeLine(null),
                MabBattleShellPanel.defaultModeSub(),
                () -> {});
        shell.setSize(1366, 768);
        shell.refreshAll(match);
        shell.doLayout();
        shell.showDoctrineStatusOverlay();
        boolean boardsPresentOpen = contains(shell.getPlayerBoardHost(), pg)
                && contains(shell.getOpponentBoardHost(), opp);
        shell.hideDoctrineStatusOverlay();
        boolean boardsPresentClosed = contains(shell.getPlayerBoardHost(), pg)
                && contains(shell.getOpponentBoardHost(), opp);
        ok &= report("openingClosingKeepsBoards", boardsPresentOpen && boardsPresentClosed);

        return ok;
    }

    private static boolean report(String name, boolean value) {
        System.out.println(name + "=" + value);
        return value;
    }

    private static boolean contains(Container parent, Component target) {
        if (parent == null || target == null) return false;
        for (int i = 0; i < parent.getComponentCount(); i++) {
            Component c = parent.getComponent(i);
            if (c == target) return true;
            if (c instanceof Container sub && contains(sub, target)) return true;
        }
        return false;
    }

    private static boolean containsText(Container root, String needle) {
        if (root == null || needle == null) return false;
        String n = needle.toUpperCase();
        for (int i = 0; i < root.getComponentCount(); i++) {
            Component c = root.getComponent(i);
            if (c instanceof javax.swing.JLabel lbl) {
                String t = lbl.getText();
                if (t != null && t.toUpperCase().contains(n)) return true;
            }
            if (c instanceof Container sub && containsText(sub, needle)) return true;
        }
        return false;
    }

    private static boolean findWhiteBackground(Container root) {
        if (root == null) return false;
        for (int i = 0; i < root.getComponentCount(); i++) {
            Component c = root.getComponent(i);
            if (c.isOpaque()) {
                Color bg = c.getBackground();
                if (bg != null && bg.getRed() > 230 && bg.getGreen() > 230 && bg.getBlue() > 230) {
                    System.out.println("whiteBackground=" + c.getClass().getName() + " " + bg);
                    return true;
                }
            }
            if (c instanceof Container sub && findWhiteBackground(sub)) return true;
        }
        return false;
    }

    private static void forceLayoutTree(Container c) {
        if (c == null) return;
        c.doLayout();
        for (int i = 0; i < c.getComponentCount(); i++) {
            Component child = c.getComponent(i);
            if (child instanceof Container sub) forceLayoutTree(sub);
        }
    }

    private MabDoctrineStatusOverlayProbe() {}
}
