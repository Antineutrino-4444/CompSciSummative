package com.tetris.mab.sim;

import com.tetris.mab.ParticipantId;
import com.tetris.mab.ui.MabUpgradeDraftOverlayPanel;
import com.tetris.mab.upgrade.draft.MabUpgradeCard;
import com.tetris.mab.upgrade.draft.MabUpgradeCategory;
import com.tetris.mab.upgrade.draft.MabUpgradeDraft;
import com.tetris.mab.upgrade.draft.MabUpgradeRarity;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

/**
 * Step 24 Refinement — verifies that the upgrade overlay is readable,
 * fits inside 1366x768, and does not use white backgrounds or scroll panes
 * for the 3-card area.
 *
 * <p>Three representative cards are used:
 * <ol>
 *   <li>Short title, short description.</li>
 *   <li>Longer title, medium description.</li>
 *   <li>Near-maximum description (~120 chars) to validate future safety.</li>
 * </ol>
 */
public final class MabUpgradeOverlayLayoutProbe {

    public static void main(String[] args) {
        System.out.println("=== MAB Upgrade Overlay Layout Probe ===");
        boolean ok;
        try {
            ok = run();
        } catch (Throwable t) {
            System.out.println("EXCEPTION " + t);
            t.printStackTrace(System.out);
            ok = false;
        }
        System.out.println("success=" + ok);
        if (!ok) System.exit(1);
    }

    private static boolean run() throws Exception {
        final boolean[] holder = {true};
        SwingUtilities.invokeAndWait(() -> {
            try {
                // ── Build 3 representative test cards ──
                MabUpgradeCard card1 = new MabUpgradeCard(
                        "probe_short", "Shelters", "SHELTERS", null,
                        MabUpgradeCategory.DEFENSE, MabUpgradeRarity.STANDARD,
                        "Impact garbage is reduced by 1 line.",
                        null, 1, false, List.of("defense_shelters"));

                MabUpgradeCard card2 = new MabUpgradeCard(
                        "probe_medium", "Reactive Plating", "REACT", null,
                        MabUpgradeCategory.DEFENSE, MabUpgradeRarity.ADVANCED,
                        "After taking an impact, intercept strength doubles for 6 seconds.",
                        null, 1, false, List.of("defense_reactive_plating"));

                // Near-future-limit description (~120 chars).
                MabUpgradeCard card3 = new MabUpgradeCard(
                        "probe_long", "Ablative Spin Protocol", "ABLATIVE", null,
                        MabUpgradeCategory.DEFENSE, MabUpgradeRarity.CRITICAL,
                        "If two incoming threats arrive within 2 ticks of each other, "
                        + "a single spin intercept neutralises both simultaneously.",
                        null, 1, false, List.of("defense_ablative_spin"));

                MabUpgradeDraft draft = new MabUpgradeDraft(
                        ParticipantId.PLAYER_A, 3,
                        Arrays.asList(card1, card2, card3));

                // ── Show the overlay ──
                MabUpgradeDraftOverlayPanel overlay = new MabUpgradeDraftOverlayPanel();
                overlay.show(draft, null, c -> {});

                // Force preferred-size layout.
                Dimension ps = overlay.getPreferredSize();
                overlay.setSize(ps);
                forceLayoutTree(overlay);

                // ── Checks ──

                boolean fits = ps.width <= 1366 && ps.height <= 768;
                holder[0] &= check("overlayFits=" + fits
                        + " (" + ps.width + "x" + ps.height + ")", fits);

                // Card row must have 3 children.
                JPanel cardRow = findCardRow(overlay);
                boolean threeCards = cardRow != null && cardRow.getComponentCount() == 3;
                holder[0] &= check("threeCardsVisible=" + threeCards, threeCards);

                // Each card must have non-zero preferred size >= 270x300.
                boolean cardBoundsOk = true;
                if (cardRow != null) {
                    for (int i = 0; i < cardRow.getComponentCount(); i++) {
                        Component card = cardRow.getComponent(i);
                        Dimension cp = card.getPreferredSize();
                        if (cp.width < 270 || cp.height < 300) {
                            System.out.println("  card[" + i + "] too small: "
                                    + cp.width + "x" + cp.height);
                            cardBoundsOk = false;
                        }
                    }
                } else {
                    cardBoundsOk = false;
                }
                holder[0] &= check("cardBoundsNonZero=" + cardBoundsOk, cardBoundsOk);

                // Description JLabel must exist inside each card.
                boolean descsOk = true;
                if (cardRow != null) {
                    for (int i = 0; i < cardRow.getComponentCount(); i++) {
                        Component card = cardRow.getComponent(i);
                        if (!(card instanceof Container)) { descsOk = false; break; }
                        boolean foundDesc = findDescLabel((Container) card);
                        if (!foundDesc) {
                            System.out.println("  card[" + i + "] missing desc label");
                            descsOk = false;
                        }
                    }
                } else {
                    descsOk = false;
                }
                holder[0] &= check("descriptionsInsideCards=" + descsOk, descsOk);

                // Title JLabel must exist inside each card.
                boolean titlesOk = true;
                if (cardRow != null) {
                    for (int i = 0; i < cardRow.getComponentCount(); i++) {
                        Component card = cardRow.getComponent(i);
                        if (!(card instanceof Container)) { titlesOk = false; break; }
                        boolean foundTitle = findTitleLabel((Container) card, i == 0 ? "SHELTERS" : (i == 1 ? "REACTIVE" : "ABLATIVE"));
                        if (!foundTitle) {
                            System.out.println("  card[" + i + "] title label not found");
                            titlesOk = false;
                        }
                    }
                } else {
                    titlesOk = false;
                }
                holder[0] &= check("titlesInsideCards=" + titlesOk, titlesOk);

                // No JScrollPane in the card area.
                boolean noScroll = cardRow == null || !findDescendantOfType(cardRow, JScrollPane.class);
                holder[0] &= check("noScrollPane=" + noScroll, noScroll);

                // No component with default white (255,255,255) background.
                boolean noWhite = !findWhiteBackground(overlay);
                holder[0] &= check("noWhitePanels=" + noWhite, noWhite);

                // Future-text safety: overlay still fits with the longest card.
                boolean futureOk = fits && cardBoundsOk && descsOk;
                holder[0] &= check("futureTextSafe=" + futureOk, futureOk);

            } catch (Throwable t) {
                System.out.println("EDT EXCEPTION " + t);
                t.printStackTrace(System.out);
                holder[0] = false;
            }
        });
        return holder[0];
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static boolean check(String msg, boolean v) {
        System.out.println((v ? "PASS  " : "FAIL  ") + msg);
        return v;
    }

    private static void forceLayoutTree(Container c) {
        if (c == null) return;
        c.doLayout();
        for (int i = 0; i < c.getComponentCount(); i++) {
            Component child = c.getComponent(i);
            if (child instanceof Container) forceLayoutTree((Container) child);
        }
    }

    /** Find the GridLayout panel holding the 3 cards (direct child of modal). */
    private static JPanel findCardRow(Container root) {
        for (int i = 0; i < root.getComponentCount(); i++) {
            Component c = root.getComponent(i);
            if (c instanceof JPanel jp && jp.getLayout() instanceof GridLayout gl
                    && gl.getColumns() == 3) {
                return jp;
            }
            if (c instanceof Container sub) {
                JPanel found = findCardRow(sub);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Returns true if the container has a JLabel whose text contains
     * "width:" (HTML wrap marker used by desc labels) and whose font
     * is TERM_MED (13pt).
     */
    private static boolean findDescLabel(Container card) {
        for (int i = 0; i < card.getComponentCount(); i++) {
            Component c = card.getComponent(i);
            if (c instanceof JLabel lbl) {
                String t = lbl.getText();
                if (t != null && t.contains("width:")
                        && lbl.getFont() != null
                        && lbl.getFont().getSize() == 13) {
                    return true;
                }
            }
            if (c instanceof Container sub && findDescLabel(sub)) return true;
        }
        return false;
    }

    /**
     * Returns true if the container has a JLabel whose text contains a
     * fragment of the expected title.
     */
    private static boolean findTitleLabel(Container card, String fragment) {
        String upper = fragment.toUpperCase();
        for (int i = 0; i < card.getComponentCount(); i++) {
            Component c = card.getComponent(i);
            if (c instanceof JLabel lbl) {
                String t = lbl.getText();
                if (t != null && t.toUpperCase().contains(upper)) return true;
            }
            if (c instanceof Container sub && findTitleLabel(sub, fragment)) return true;
        }
        return false;
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

    private static boolean findWhiteBackground(Container root) {
        for (int i = 0; i < root.getComponentCount(); i++) {
            Component c = root.getComponent(i);
            if (c.isOpaque()) {
                Color bg = c.getBackground();
                if (bg != null && bg.getRed() > 230 && bg.getGreen() > 230 && bg.getBlue() > 230) {
                    System.out.println("  white-ish bg found on: " + c.getClass().getSimpleName()
                            + " bg=" + bg);
                    return true;
                }
            }
            if (c instanceof Container && findWhiteBackground((Container) c)) return true;
        }
        return false;
    }
}
