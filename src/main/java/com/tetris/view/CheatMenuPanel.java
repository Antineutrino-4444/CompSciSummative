package com.tetris.view;

import com.tetris.mab.upgrade.draft.MabUpgradeCard;
import com.tetris.mab.upgrade.draft.MabUpgradeRarity;
import com.tetris.view.theme.Components;
import com.tetris.view.theme.Components.ButtonStyle;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Translucent cheat/debug overlay mounted in the layered pane.
 * Opened by the "debug" console command; shows a gravity freeze toggle
 * and (in MAB modes) one-click upgrade application for Player A.
 */
public final class CheatMenuPanel extends JPanel {

    private static final Color BG     = new Color(0x08, 0x10, 0x12, 250);
    private static final Color BORDER = new Color(0x00, 0xCC, 0x66);
    private static final Color ACCENT = new Color(0x00, 0xFF, 0x80);
    private static final Color TEXT   = new Color(0xB0, 0xD0, 0xC0);
    private static final Font  MONO_B = new Font("Monospaced", Font.BOLD, 12);

    private final BooleanSupplier isGravFrozen;
    private final Consumer<Boolean> setGravFrozen;
    private JButton gravBtn;

    public CheatMenuPanel(BooleanSupplier isGravFrozen,
                          Consumer<Boolean> setGravFrozen,
                          List<MabUpgradeCard> upgradeCards,
                          Consumer<MabUpgradeCard> applyUpgrade,
                          Runnable onClose) {
        this.isGravFrozen  = isGravFrozen;
        this.setGravFrozen = setGravFrozen;

        setOpaque(true);
        setBackground(BG);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                new EmptyBorder(14, 18, 14, 18)));
        setLayout(new BorderLayout(0, 10));

        add(buildHeader(onClose), BorderLayout.NORTH);
        add(buildBody(upgradeCards, applyUpgrade), BorderLayout.CENTER);
    }

    public void refreshGravityButton() {
        if (gravBtn == null) return;
        boolean frozen = isGravFrozen.getAsBoolean();
        gravBtn.setText(frozen ? "FROZEN  ❄   click to resume" : "RUNNING  ▶   click to freeze");
    }

    // ── Layout helpers ────────────────────────────────────────────────────

    private JPanel buildHeader(Runnable onClose) {
        JPanel h = new JPanel(new BorderLayout(10, 0));
        h.setOpaque(false);

        JLabel title = new JLabel("⚡  CHEAT MENU");
        title.setFont(MONO_B.deriveFont(15f));
        title.setForeground(ACCENT);
        h.add(title, BorderLayout.WEST);

        JLabel hint = new JLabel("`  to close   |   gravity is frozen while this panel is open");
        hint.setFont(MONO_B.deriveFont(10f));
        hint.setForeground(new Color(60, 120, 80));
        h.add(hint, BorderLayout.CENTER);

        JButton closeBtn = Components.button("✕  CLOSE", ButtonStyle.SECONDARY);
        closeBtn.addActionListener(e -> onClose.run());
        h.add(closeBtn, BorderLayout.EAST);
        return h;
    }

    private JPanel buildBody(List<MabUpgradeCard> upgradeCards,
                              Consumer<MabUpgradeCard> applyUpgrade) {
        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);

        // ── Gravity toggle ──
        JPanel gravRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        gravRow.setOpaque(false);
        JLabel gl = new JLabel("GRAVITY :");
        gl.setFont(MONO_B);
        gl.setForeground(TEXT);
        gravRow.add(gl);

        boolean frozen = isGravFrozen.getAsBoolean();
        gravBtn = Components.button(
                frozen ? "FROZEN  ❄   click to resume" : "RUNNING  ▶   click to freeze",
                ButtonStyle.PRIMARY);
        gravBtn.addActionListener(e -> {
            boolean now = !isGravFrozen.getAsBoolean();
            setGravFrozen.accept(now);
            refreshGravityButton();
        });
        gravRow.add(gravBtn);
        body.add(gravRow, BorderLayout.NORTH);

        // ── Upgrade grid (MAB modes only) ──
        if (upgradeCards != null && !upgradeCards.isEmpty()) {
            JPanel upgradeSection = new JPanel(new BorderLayout(0, 6));
            upgradeSection.setOpaque(false);

            JLabel secTitle = new JLabel("APPLY UPGRADE → PLAYER A  ("
                    + upgradeCards.size() + " cards)");
            secTitle.setFont(MONO_B);
            secTitle.setForeground(TEXT);
            upgradeSection.add(secTitle, BorderLayout.NORTH);

            int cols = 5;
            JPanel grid = new JPanel(new GridLayout(0, cols, 4, 4));
            grid.setBackground(new Color(0x06, 0x0E, 0x08));
            for (MabUpgradeCard card : upgradeCards) {
                grid.add(makeUpgradeBtn(card, applyUpgrade));
            }
            int rem = (cols - (upgradeCards.size() % cols)) % cols;
            for (int i = 0; i < rem; i++) {
                JPanel pad = new JPanel();
                pad.setBackground(grid.getBackground());
                grid.add(pad);
            }

            JScrollPane scroll = new JScrollPane(grid);
            scroll.setOpaque(false);
            scroll.getViewport().setBackground(grid.getBackground());
            scroll.setBorder(BorderFactory.createLineBorder(new Color(30, 60, 40)));
            scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            upgradeSection.add(scroll, BorderLayout.CENTER);
            body.add(upgradeSection, BorderLayout.CENTER);
        }

        return body;
    }

    private JButton makeUpgradeBtn(MabUpgradeCard card, Consumer<MabUpgradeCard> apply) {
        String label = card.getIconText() + "  " + card.getShortName();
        JButton btn = new JButton(label);
        btn.setFont(MONO_B.deriveFont(11f));
        btn.setForeground(rarityColor(card.getRarity()));
        btn.setBackground(new Color(0x0C, 0x1A, 0x10));
        btn.setOpaque(true);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(30, 65, 40)),
                new EmptyBorder(3, 6, 3, 6)));
        btn.setToolTipText("<html><b>" + card.getDisplayName() + "</b><br>"
                + card.getOneLineDescription() + "</html>");
        btn.addActionListener(e -> {
            apply.accept(card);
            Color orig = btn.getForeground();
            btn.setForeground(ACCENT);
            javax.swing.Timer flash = new javax.swing.Timer(350, ev -> btn.setForeground(orig));
            flash.setRepeats(false);
            flash.start();
        });
        return btn;
    }

    private static Color rarityColor(MabUpgradeRarity r) {
        if (r == null) return new Color(160, 200, 160);
        return switch (r) {
            case CRITICAL -> new Color(255, 185, 50);
            case ADVANCED -> new Color(70, 160, 255);
            default       -> new Color(160, 200, 160);
        };
    }
}
