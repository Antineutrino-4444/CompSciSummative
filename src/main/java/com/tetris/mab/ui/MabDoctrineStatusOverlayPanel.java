package com.tetris.mab.ui;

import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ParticipantState;
import com.tetris.mab.upgrade.draft.MabActiveDoctrineAvailability;
import com.tetris.mab.upgrade.draft.MabUpgradeCard;
import com.tetris.mab.upgrade.draft.MabUpgradeCategory;
import com.tetris.mab.upgrade.draft.MabUpgradeInventory;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Step 25 - read-only doctrine inventory/status overlay. The active section
 * is fixed above the scrollable owned-list area.
 */
public final class MabDoctrineStatusOverlayPanel extends JPanel {

    private final JPanel activePanel = new JPanel(new GridLayout(0, 1, 0, 6));
    private final JPanel listPanel = new JPanel();
    private final JScrollPane listScroll;
    private final JLabel emptyLabel = new JLabel("NO DOCTRINES LOADED", SwingConstants.CENTER);
    private Runnable closeCallback;

    public MabDoctrineStatusOverlayPanel() {
        super(new GridBagLayout());
        setOpaque(false);
        setFocusable(true);

        JPanel modal = new JPanel(new BorderLayout(0, 12));
        modal.setOpaque(true);
        modal.setBackground(MabUiTheme.SHELL_BG);
        modal.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(MabUiTheme.C_CYAN, 2),
                new EmptyBorder(20, 26, 18, 26)));
        modal.setPreferredSize(new Dimension(780, 620));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        JLabel title = new JLabel("LOADED DOCTRINES", SwingConstants.CENTER);
        title.setFont(MabUiTheme.STENCIL_HEADLINE);
        title.setForeground(MabUiTheme.C_CYAN);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel sub = new JLabel("CURRENT MATCH INVENTORY", SwingConstants.CENTER);
        sub.setFont(MabUiTheme.STENCIL_SMALL);
        sub.setForeground(MabUiTheme.TEXT_FAINT);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.add(title);
        header.add(sub);
        modal.add(header, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        activePanel.setOpaque(false);
        body.add(section("ACTIVE DOCTRINES", activePanel), BorderLayout.NORTH);

        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setOpaque(true);
        listPanel.setBackground(MabUiTheme.SHELL_DEEP_BG);
        listScroll = new JScrollPane(listPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        listScroll.setBorder(new LineBorder(MabUiTheme.GRID_LINE_HI, 1));
        listScroll.setBackground(MabUiTheme.SHELL_DEEP_BG);
        listScroll.getViewport().setBackground(MabUiTheme.SHELL_DEEP_BG);
        listScroll.getVerticalScrollBar().setBackground(MabUiTheme.SHELL_PANEL_BG);
        listScroll.getHorizontalScrollBar().setBackground(MabUiTheme.SHELL_PANEL_BG);
        styleScrollBar(listScroll);
        listScroll.setPreferredSize(new Dimension(720, 320));
        body.add(section("OWNED INVENTORY", listScroll), BorderLayout.CENTER);
        modal.add(body, BorderLayout.CENTER);

        JButton close = new JButton("CLOSE");
        close.setFont(MabUiTheme.TERM_SMALL);
        close.setForeground(MabUiTheme.TEXT_FAINT);
        close.setBackground(MabUiTheme.SHELL_PANEL_BG);
        close.setOpaque(true);
        close.setBorder(new LineBorder(MabUiTheme.GRID_LINE_HI, 1));
        close.setFocusPainted(false);
        close.setFocusable(false);
        close.addActionListener(e -> close());
        JPanel footer = new JPanel();
        footer.setOpaque(false);
        footer.add(close);
        modal.add(footer, BorderLayout.SOUTH);

        add(modal, new GridBagConstraints());
        installKeyboardActions();
        setVisible(false);
    }

    @Override
    protected void paintComponent(java.awt.Graphics g) {
        g.setColor(new Color(2, 6, 11, 190));
        g.fillRect(0, 0, getWidth(), getHeight());
        super.paintComponent(g);
    }

    public void showStatus(MutuallyAssuredBlocksMatch match,
                           ParticipantId participantId,
                           Runnable onClose) {
        closeCallback = onClose;
        rebuild(match, participantId);
        setVisible(true);
        revalidate();
        repaint();
        SwingUtilities.invokeLater(this::requestFocusInWindow);
    }

    public void dismiss() {
        setVisible(false);
        activePanel.removeAll();
        listPanel.removeAll();
        closeCallback = null;
    }

    public JPanel getActivePanel() {
        return activePanel;
    }

    public JScrollPane getListScrollPane() {
        return listScroll;
    }

    public boolean requiresTextInput() {
        return false;
    }

    private void rebuild(MutuallyAssuredBlocksMatch match, ParticipantId participantId) {
        activePanel.removeAll();
        listPanel.removeAll();
        ParticipantState p = match == null ? null : match.getParticipant(participantId);
        MabUpgradeInventory inv = p == null ? null : p.getUpgradeInventory();

        List<MabActiveDoctrineAvailability> activeRows = match == null
                ? List.of() : match.getActiveDoctrineOptions(participantId);
        for (MabActiveDoctrineAvailability row : activeRows) {
            activePanel.add(activeRow(row));
        }

        Map<String, List<MabUpgradeCard>> groups = grouped(inv);
        boolean any = inv != null && inv.getTotalSelectedCards() > 0;
        if (!any) {
            emptyLabel.setFont(MabUiTheme.STENCIL_SMALL);
            emptyLabel.setForeground(MabUiTheme.TEXT_FAINT);
            emptyLabel.setBorder(new EmptyBorder(20, 0, 20, 0));
            listPanel.add(emptyLabel);
        } else {
            for (Map.Entry<String, List<MabUpgradeCard>> e : groups.entrySet()) {
                listPanel.add(groupHeader(e.getKey()));
                if (e.getValue().isEmpty()) {
                    listPanel.add(mutedRow("NONE", "", ""));
                } else {
                    for (MabUpgradeCard card : e.getValue()) {
                        listPanel.add(upgradeRow(inv, card, p));
                    }
                }
            }
        }
        listPanel.revalidate();
    }

    private JPanel activeRow(MabActiveDoctrineAvailability row) {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setOpaque(true);
        p.setBackground(MabUiTheme.SHELL_DEEP_BG);
        p.setBorder(new EmptyBorder(6, 10, 6, 10));
        JLabel name = label(row.displayName(), MabUiTheme.TEXT_BRIGHT, MabUiTheme.TERM_MED);
        JLabel status = label(row.owned() ? row.status() : "NOT LOADED",
                row.available() ? MabUiTheme.C_GREEN : MabUiTheme.TEXT_FAINT,
                MabUiTheme.TERM_SMALL);
        JLabel reason = label(row.reason(), MabUiTheme.TEXT_GHOST, MabUiTheme.TERM_TINY);
        p.add(name, BorderLayout.WEST);
        p.add(status, BorderLayout.CENTER);
        p.add(reason, BorderLayout.EAST);
        return p;
    }

    private JPanel upgradeRow(MabUpgradeInventory inv, MabUpgradeCard card, ParticipantState pState) {
        String stacks = "x" + (inv == null ? 0 : inv.getStacks(card.getId()));
        String status = card.isActiveCard() ? "ACTIVE" : "PASSIVE";
        if ("hardened_silos".equals(card.getId()) && pState != null && pState.isHardenedSilosUsed()) {
            status = "ONCE PER MATCH USED";
        }
        if ("dead_hand_protocol".equals(card.getId()) && pState != null && pState.isDeadHandUsed()) {
            status = "ONCE PER MATCH USED";
        }
        return mutedRow(card.getDisplayName() + " " + stacks,
                card.getOneLineDescription(), status);
    }

    private JPanel mutedRow(String left, String mid, String right) {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setOpaque(true);
        p.setBackground(MabUiTheme.SHELL_DEEP_BG);
        p.setBorder(new EmptyBorder(5, 12, 5, 12));
        p.add(label(left, MabUiTheme.TEXT, MabUiTheme.TERM_SMALL), BorderLayout.WEST);
        p.add(label(mid, MabUiTheme.TEXT_FAINT, MabUiTheme.TERM_TINY), BorderLayout.CENTER);
        p.add(label(right, MabUiTheme.C_CYAN_DIM, MabUiTheme.TERM_TINY), BorderLayout.EAST);
        return p;
    }

    private JLabel groupHeader(String text) {
        JLabel l = label(text, MabUiTheme.C_CYAN, MabUiTheme.STENCIL_SMALL);
        l.setBorder(new EmptyBorder(10, 10, 4, 10));
        l.setOpaque(true);
        l.setBackground(MabUiTheme.SHELL_DEEP_BG);
        return l;
    }

    private Map<String, List<MabUpgradeCard>> grouped(MabUpgradeInventory inv) {
        Map<String, List<MabUpgradeCard>> groups = new LinkedHashMap<>();
        for (String g : List.of("CHARGE", "TETRIS", "SPIN", "DEFENSE", "POWER", "INTEL", "TEMPO")) {
            groups.put(g, new ArrayList<>());
        }
        if (inv != null) {
            for (MabUpgradeCard card : inv.getAllSelectedCards()) {
                groups.get(groupName(card.getCategory())).add(card);
            }
        }
        return groups;
    }

    private static String groupName(MabUpgradeCategory category) {
        if (category == null) return "TEMPO";
        return switch (category) {
            case TETRIS_ROUTE -> "TETRIS";
            case SPIN_ROUTE -> "SPIN";
            case POWER -> "POWER";
            case CHARGE -> "CHARGE";
            case DEFENSE -> "DEFENSE";
            case TEMPO -> "TEMPO";
        };
    }

    private JPanel section(String title, Component body) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        JLabel h = label(title, MabUiTheme.TEXT_FAINT, MabUiTheme.TERM_TINY);
        p.add(h, BorderLayout.NORTH);
        p.add(body, BorderLayout.CENTER);
        return p;
    }

    private JLabel label(String text, Color color, java.awt.Font font) {
        JLabel l = new JLabel(text == null ? "" : text);
        l.setFont(font);
        l.setForeground(color == null ? MabUiTheme.TEXT : color);
        return l;
    }

    private void close() {
        if (closeCallback != null) closeCallback.run();
    }

    private void installKeyboardActions() {
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        getActionMap().put("close", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                close();
            }
        });
    }

    private static void styleScrollBar(JScrollPane scroll) {
        if (scroll == null) return;
        scroll.getVerticalScrollBar().setUI(darkScrollBarUi());
        scroll.getVerticalScrollBar().setOpaque(true);
        scroll.getHorizontalScrollBar().setUI(darkScrollBarUi());
        scroll.getHorizontalScrollBar().setOpaque(true);
    }

    private static BasicScrollBarUI darkScrollBarUi() {
        return new BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                this.thumbColor = MabUiTheme.GRID_LINE_HI;
                this.trackColor = MabUiTheme.SHELL_PANEL_BG;
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return scrollButton();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return scrollButton();
            }
        };
    }

    private static JButton scrollButton() {
        JButton b = new JButton();
        b.setPreferredSize(new Dimension(0, 0));
        b.setMinimumSize(new Dimension(0, 0));
        b.setMaximumSize(new Dimension(0, 0));
        b.setOpaque(true);
        b.setBackground(MabUiTheme.SHELL_PANEL_BG);
        b.setBorder(null);
        b.setFocusable(false);
        return b;
    }
}
