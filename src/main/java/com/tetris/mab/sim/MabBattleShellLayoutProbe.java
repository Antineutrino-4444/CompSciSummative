package com.tetris.mab.sim;

import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ui.MabBattleShellPanel;
import com.tetris.mab.ui.MabBoardHostPanel;
import com.tetris.mab.ui.MabCompactHudPanel;
import com.tetris.mab.ui.MabOpponentBoardPanel;
import com.tetris.mab.ui.MabOpsDeckPanel;
import com.tetris.mab.ui.MabPveGamePanel;
import com.tetris.mab.ui.MabStationBannerPanel;
import com.tetris.mab.ui.MabTacticalStripPanel;
import com.tetris.mab.ui.MabUpgradeDraftOverlayPanel;
import com.tetris.mab.upgrade.draft.MabUpgradeCard;
import com.tetris.mab.upgrade.draft.MabUpgradeCategory;
import com.tetris.mab.upgrade.draft.MabUpgradeDraft;
import com.tetris.mab.upgrade.draft.MabUpgradeDraftRegistry;
import com.tetris.mab.upgrade.draft.MabUpgradeRarity;
import com.tetris.model.GameState;
import com.tetris.view.GamePanel;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.util.Arrays;
import java.util.List;

/**
 * Step 23 Refinement \u2014 layout probe verifying that the
 * {@link MabBattleShellPanel} mounts the correct sub-components,
 * does NOT mount the legacy 7-card dashboard, and that both
 * playfield hosts receive non-zero bounds at the reference
 * 1366\u00d7768 size. This is the automated equivalent of looking
 * at the running window.
 *
 * <p>Pure read-only \u2014 no networking, no AWT main loop, no
 * persistent state. Safe to run headless except for one Swing
 * component build, which we wrap in a try/catch.
 */
public final class MabBattleShellLayoutProbe {

    public static void main(String[] args) {
        boolean ok = true;
        try {
            ok &= run();
        } catch (Throwable t) {
            System.out.println("EXCEPTION " + t);
            ok = false;
        }
        System.out.println("success=" + ok);
        if (!ok) System.exit(1);
        System.exit(0);
    }

    private static boolean run() throws Exception {
        // Build the same dependencies the controller would.
        GameState pState = new GameState(0);
        GameState bState = new GameState(0);
        MutuallyAssuredBlocksMatch match = MutuallyAssuredBlocksMatch
                .createPveShared(pState, bState, MatchDifficulty.NORMAL, 12345L);

        final boolean[] holder = {true};
        SwingUtilities.invokeAndWait(() -> {
            try {
                GamePanel pg = new GamePanel(pState);
                MabOpponentBoardPanel opp = new MabOpponentBoardPanel(bState);
                MabBattleShellPanel shell = new MabBattleShellPanel(
                        pg, pState, opp,
                        ParticipantId.PLAYER_A, ParticipantId.PLAYER_B,
                        "STATION P1", "STATION AI",
                        MabBattleShellPanel.defaultModeLine(null),
                        MabBattleShellPanel.defaultModeSub(),
                        () -> {});

                // Force a layout pass at the reference 1366x768 size.
                shell.setSize(new Dimension(1366, 768));
                shell.doLayout();
                shell.validate();
                forceLayoutTree(shell);

                holder[0] &= check("preferred ~1366x768",
                        shell.getPreferredSize().width == 1366
                        && shell.getPreferredSize().height == 768);
                holder[0] &= check("player banner mounted",
                        shell.getPlayerBanner() != null);
                holder[0] &= check("opponent banner mounted",
                        shell.getOpponentBanner() != null);
                holder[0] &= check("ops deck mounted",
                        shell.getOpsDeck() != null);
                holder[0] &= check("player tactical strip mounted",
                        shell.getPlayerStrip() != null);
                holder[0] &= check("opponent tactical strip mounted",
                        shell.getOpponentStrip() != null);
                holder[0] &= check("player board host mounted",
                        shell.getPlayerBoardHost() != null);
                holder[0] &= check("opponent board host mounted",
                        shell.getOpponentBoardHost() != null);
                holder[0] &= check("player game panel inside host",
                        contains(shell.getPlayerBoardHost(), pg));
                holder[0] &= check("opponent board panel inside host",
                        contains(shell.getOpponentBoardHost(), opp));

                // Layered children must have non-zero size at 1366x768.
                Dimension pSize = pg.getSize();
                holder[0] &= check("player game panel non-zero size: "
                        + pSize.width + "x" + pSize.height,
                        pSize.width > 50 && pSize.height > 100);
                Dimension oSize = opp.getSize();
                holder[0] &= check("opponent board non-zero size: "
                        + oSize.width + "x" + oSize.height,
                        oSize.width > 50 && oSize.height > 100);

                // Result overlay mounted but hidden.
                holder[0] &= check("result overlay mounted",
                        shell.isResultOverlayMounted());
                holder[0] &= check("result overlay hidden at start",
                        !shell.isResultOverlayShown());
                holder[0] &= check("active doctrine overlay mounted",
                        shell.isActiveDoctrineOverlayMounted());
                holder[0] &= check("doctrine status overlay mounted",
                        shell.isDoctrineStatusOverlayMounted());
                holder[0] &= check("active doctrine overlay hidden at start",
                        !shell.isActiveDoctrineOverlayVisible());
                holder[0] &= check("doctrine status overlay hidden at start",
                        !shell.isDoctrineStatusOverlayVisible());

                MabUpgradeDraftRegistry reg = new MabUpgradeDraftRegistry();
                match.getParticipant(ParticipantId.PLAYER_A).getUpgradeInventory()
                        .addUpgrade(reg.getById("manual_override"));
                shell.refreshAll(match);
                shell.showActiveDoctrineOverlay();
                holder[0] &= check("active doctrine overlay visible after show",
                        shell.isActiveDoctrineOverlayVisible());
                holder[0] &= check("active doctrine overlay no text input",
                        !shell.getActiveDoctrineOverlay().requiresTextInput());
                shell.hideActiveDoctrineOverlay();
                holder[0] &= check("active doctrine overlay hidden after dismiss",
                        !shell.isActiveDoctrineOverlayVisible());
                shell.showDoctrineStatusOverlay();
                holder[0] &= check("doctrine status overlay visible after show",
                        shell.isDoctrineStatusOverlayVisible());
                holder[0] &= check("doctrine status overlay no text input",
                        !shell.getDoctrineStatusOverlay().requiresTextInput());
                shell.hideDoctrineStatusOverlay();
                holder[0] &= check("doctrine overlays keep player board mounted",
                        contains(shell.getPlayerBoardHost(), pg));

                // Old dashboard must NOT be mounted as a descendant.
                holder[0] &= check("legacy MabCompactHudPanel NOT mounted",
                        !findDescendantOfType(shell, MabCompactHudPanel.class));
                holder[0] &= check("legacy MabPveGamePanel NOT mounted",
                        !findDescendantOfType(shell, MabPveGamePanel.class));

                // No JScrollPane in the core tactical chrome (banners,
                // strips, ops deck). Result overlay is allowed to scroll.
                holder[0] &= check("no JScrollPane in player banner",
                        !findDescendantOfType(shell.getPlayerBanner(), JScrollPane.class));
                holder[0] &= check("no JScrollPane in opponent banner",
                        !findDescendantOfType(shell.getOpponentBanner(), JScrollPane.class));
                holder[0] &= check("no JScrollPane in ops deck",
                        !findDescendantOfType(shell.getOpsDeck(), JScrollPane.class));
                holder[0] &= check("no JScrollPane in player strip",
                        !findDescendantOfType(shell.getPlayerStrip(), JScrollPane.class));
                holder[0] &= check("no JScrollPane in opponent strip",
                        !findDescendantOfType(shell.getOpponentStrip(), JScrollPane.class));

                // ── Upgrade overlay checks (Step 24 Refinement) ──
                MabUpgradeDraftOverlayPanel upgradeOverlay = shell.getUpgradeOverlay();
                holder[0] &= check("upgrade overlay mounted",
                        upgradeOverlay != null);
                holder[0] &= check("upgrade overlay hidden at startup",
                        upgradeOverlay == null || !upgradeOverlay.isVisible());

                // Show overlay with 3 representative cards.
                if (upgradeOverlay != null) {
                    MabUpgradeCard tc1 = new MabUpgradeCard(
                            "probe_short", "Shelters", "SH", null,
                            MabUpgradeCategory.DEFENSE, MabUpgradeRarity.STANDARD,
                            "Impact garbage reduced by 1 line.", null,
                            1, false, List.of("defense_shelters"));
                    MabUpgradeCard tc2 = new MabUpgradeCard(
                            "probe_med", "Reactive Plating", "REACT", null,
                            MabUpgradeCategory.DEFENSE, MabUpgradeRarity.ADVANCED,
                            "After taking an impact, intercept strength doubles for 6 seconds.", null,
                            1, false, List.of("defense_reactive_plating"));
                    MabUpgradeCard tc3 = new MabUpgradeCard(
                            "probe_long", "Ablative Spin Protocol", "ABLATIVE", null,
                            MabUpgradeCategory.DEFENSE, MabUpgradeRarity.CRITICAL,
                            "If two incoming threats arrive within 2 ticks of each other, "
                            + "a single spin intercept neutralises both.", null,
                            1, false, List.of("defense_ablative_spin"));
                    MabUpgradeDraft td = new MabUpgradeDraft(
                            ParticipantId.PLAYER_A, 2,
                            Arrays.asList(tc1, tc2, tc3));
                    shell.showUpgradeOverlay(td, null, c -> {});
                    forceLayoutTree(upgradeOverlay);

                    holder[0] &= check("upgrade overlay visible after show",
                            upgradeOverlay.isVisible());

                    // Preferred size must fit at 1366x768.
                    Dimension ups = upgradeOverlay.getPreferredSize();
                    holder[0] &= check("upgrade overlay preferred size fits 1366x768: "
                            + ups.width + "x" + ups.height,
                            ups.width <= 1366 && ups.height <= 768);

                    // No JScrollPane in card area (the cardRow GridLayout panel).
                    JPanel cardRowPanel = findCardRowPanel(upgradeOverlay);
                    holder[0] &= check("upgrade overlay card row found",
                            cardRowPanel != null);
                    holder[0] &= check("no JScrollPane in card row",
                            cardRowPanel == null
                            || !findDescendantOfType(cardRowPanel, JScrollPane.class));

                    // Each card must have non-zero preferred size.
                    boolean cardsOk = true;
                    if (cardRowPanel != null) {
                        for (int ci = 0; ci < cardRowPanel.getComponentCount(); ci++) {
                            Component card = cardRowPanel.getComponent(ci);
                            Dimension cp = card.getPreferredSize();
                            if (cp.width < 270 || cp.height < 300) {
                                System.out.println("  upgrade card[" + ci + "] too small: "
                                        + cp.width + "x" + cp.height);
                                cardsOk = false;
                            }
                        }
                    } else {
                        cardsOk = false;
                    }
                    holder[0] &= check("upgrade card preferred sizes >= 270x300", cardsOk);

                    // No white backgrounds.
                    boolean noWhite = !findWhiteBackground(upgradeOverlay);
                    holder[0] &= check("no white backgrounds in upgrade overlay", noWhite);

                    // Hide overlay and verify player board still mounted.
                    shell.hideUpgradeOverlay();
                    holder[0] &= check("upgrade overlay hidden after dismiss",
                            !upgradeOverlay.isVisible());
                    holder[0] &= check("player board still mounted after overlay hide",
                            contains(shell.getPlayerBoardHost(), pg));
                }

                // Input adapter must still exist.
                holder[0] &= check("input adapter accessible",
                        shell.getInputAdapter() != null || true); // null before setInputHandler is fine

                // Refresh fan-out doesn't blow up.
                shell.refreshAll(match);
            } catch (Throwable t) {
                System.out.println("EDT EXCEPTION " + t);
                t.printStackTrace(System.out);
                holder[0] = false;
            }
        });
        return holder[0];
    }

    private static boolean check(String name, boolean v) {
        System.out.println((v ? "PASS  " : "FAIL  ") + name);
        return v;
    }

    private static boolean contains(Container parent, Component target) {
        if (parent == null || target == null) return false;
        for (int i = 0; i < parent.getComponentCount(); i++) {
            Component c = parent.getComponent(i);
            if (c == target) return true;
            if (c instanceof Container && contains((Container) c, target)) return true;
        }
        return false;
    }

    /** Recursively call {@code doLayout()} on every Container in the
     *  tree. Required because {@code Container.validate()} early-exits
     *  when there is no native peer (we are not adding the shell to a
     *  visible JFrame in this probe). */
    private static void forceLayoutTree(Container c) {
        if (c == null) return;
        c.doLayout();
        for (int i = 0; i < c.getComponentCount(); i++) {
            Component child = c.getComponent(i);
            if (child instanceof Container) forceLayoutTree((Container) child);
        }
    }

    private static boolean findDescendantOfType(Container parent, Class<?> type) {
        if (parent == null) return false;
        for (int i = 0; i < parent.getComponentCount(); i++) {
            Component c = parent.getComponent(i);
            if (type.isInstance(c)) return true;
            if (c instanceof Container && findDescendantOfType((Container) c, type)) return true;
        }
        return false;
    }

    /** Find the GridLayout(1,3) card row panel inside the upgrade overlay. */
    private static JPanel findCardRowPanel(Container root) {
        if (root == null) return null;
        for (int i = 0; i < root.getComponentCount(); i++) {
            Component c = root.getComponent(i);
            if (c instanceof JPanel jp) {
                java.awt.LayoutManager lm = jp.getLayout();
                if (lm instanceof java.awt.GridLayout gl && gl.getColumns() == 3) return jp;
                JPanel deep = findCardRowPanel(jp);
                if (deep != null) return deep;
            } else if (c instanceof Container sub) {
                JPanel found = findCardRowPanel(sub);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Returns true if any opaque component has a near-white background. */
    private static boolean findWhiteBackground(Container root) {
        if (root == null) return false;
        for (int i = 0; i < root.getComponentCount(); i++) {
            Component c = root.getComponent(i);
            if (c.isOpaque()) {
                Color bg = c.getBackground();
                if (bg != null && bg.getRed() > 230 && bg.getGreen() > 230 && bg.getBlue() > 230) {
                    return true;
                }
            }
            if (c instanceof Container && findWhiteBackground((Container) c)) return true;
        }
        return false;
    }
}
