package com.tetris.mab.ui;

import com.tetris.mab.upgrade.draft.MabActiveDoctrineAvailability;
import com.tetris.mab.upgrade.draft.MabActiveDoctrineType;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Step 25 - modal selector for active doctrine cards. No text entry and no
 * default Swing white panels.
 */
public final class MabActiveDoctrineOverlayPanel extends JPanel {

    private final JPanel optionRow = new JPanel(new GridLayout(1, 2, 16, 0));
    private final List<MabActiveDoctrineAvailability> options = new ArrayList<>();
    private Consumer<MabActiveDoctrineType> useCallback;
    private Runnable closeCallback;
    private JLabel titleLabel;

    public MabActiveDoctrineOverlayPanel() {
        super(new GridBagLayout());
        setOpaque(false);
        setFocusable(true);
        optionRow.setOpaque(false);

        JPanel modal = new JPanel(new BorderLayout(0, 14));
        modal.setOpaque(true);
        modal.setBackground(MabUiTheme.SHELL_BG);
        modal.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(MabUiTheme.C_AMBER, 2),
                new EmptyBorder(22, 30, 18, 30)));
        modal.setPreferredSize(new Dimension(720, 360));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        titleLabel = new JLabel("ACTIVE COMMANDS", SwingConstants.CENTER);
        titleLabel.setFont(MabUiTheme.STENCIL_HEADLINE);
        titleLabel.setForeground(MabUiTheme.C_AMBER);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel sub = new JLabel("SELECT AVAILABLE COMMAND", SwingConstants.CENTER);
        sub.setFont(MabUiTheme.STENCIL_SMALL);
        sub.setForeground(MabUiTheme.TEXT_FAINT);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.add(titleLabel);
        header.add(Box.createVerticalStrut(5));
        header.add(sub);
        modal.add(header, BorderLayout.NORTH);

        modal.add(optionRow, BorderLayout.CENTER);

        JButton cancel = styleButton("CANCEL", false);
        cancel.addActionListener(e -> close());
        JPanel footer = new JPanel();
        footer.setOpaque(false);
        footer.add(cancel);
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

    public void showOptions(String title,
                            List<MabActiveDoctrineAvailability> rows,
                            Consumer<MabActiveDoctrineType> onUse,
                            Runnable onClose) {
        titleLabel.setText(title == null ? "ACTIVE COMMANDS" : title);
        showOptions(rows, onUse, onClose);
    }

    public void showOptions(List<MabActiveDoctrineAvailability> rows,
                            Consumer<MabActiveDoctrineType> onUse,
                            Runnable onClose) {
        options.clear();
        if (rows != null) options.addAll(rows);
        useCallback = onUse;
        closeCallback = onClose;
        optionRow.removeAll();
        for (MabActiveDoctrineAvailability row : options) {
            optionRow.add(buildOption(row));
        }
        while (optionRow.getComponentCount() < 2) {
            optionRow.add(buildEmptyOption());
        }
        setVisible(true);
        revalidate();
        repaint();
        SwingUtilities.invokeLater(this::requestFocusInWindow);
    }

    public void dismiss() {
        setVisible(false);
        optionRow.removeAll();
        options.clear();
        useCallback = null;
        closeCallback = null;
    }

    public int getOptionCount() {
        return options.size();
    }

    public List<MabActiveDoctrineAvailability> getOptionsSnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(options));
    }

    public boolean requiresTextInput() {
        return false;
    }

    private JPanel buildOption(MabActiveDoctrineAvailability row) {
        boolean ready = row != null && row.available();
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(true);
        p.setBackground(ready ? MabUiTheme.CARD_BG : MabUiTheme.SHELL_PANEL_BG);
        p.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(ready ? MabUiTheme.C_AMBER : MabUiTheme.GRID_LINE_HI, 1),
                new EmptyBorder(14, 16, 14, 16)));

        JLabel name = new JLabel(row == null ? "EMPTY" : row.displayName());
        name.setFont(MabUiTheme.STENCIL_MID);
        name.setForeground(ready ? MabUiTheme.C_AMBER : MabUiTheme.TEXT_FAINT);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(name);
        p.add(Box.createVerticalStrut(12));

        JLabel status = new JLabel(row == null ? "NOT LOADED" : row.status());
        status.setFont(MabUiTheme.TERM_MED);
        status.setForeground(ready ? MabUiTheme.C_GREEN : MabUiTheme.C_RED);
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(status);

        JLabel reason = new JLabel(row == null ? "NO ACTIVE CARD" : row.reason());
        reason.setFont(MabUiTheme.TERM_SMALL);
        reason.setForeground(MabUiTheme.TEXT_FAINT);
        reason.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(reason);
        p.add(Box.createVerticalGlue());

        String cost = row == null || row.chargeCost() <= 0 ? "NO CHARGE COST"
                : "COST " + row.chargeCost() + " CHARGE";
        JLabel costLabel = new JLabel(cost);
        costLabel.setFont(MabUiTheme.TERM_TINY);
        costLabel.setForeground(MabUiTheme.TEXT_GHOST);
        costLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(costLabel);
        p.add(Box.createVerticalStrut(10));

        JButton use = styleButton(ready ? "EXECUTE" : "LOCKED", ready);
        use.setEnabled(ready);
        use.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (row != null) {
            use.addActionListener(e -> {
                if (useCallback != null) useCallback.accept(row.type());
            });
        }
        p.add(use);
        return p;
    }

    private JPanel buildEmptyOption() {
        return buildOption(null);
    }

    private JButton styleButton(String text, boolean primary) {
        JButton b = new JButton(text);
        b.setFont(MabUiTheme.TERM_SMALL);
        b.setForeground(primary ? MabUiTheme.SHELL_DEEP_BG : MabUiTheme.TEXT_FAINT);
        b.setBackground(primary ? MabUiTheme.C_AMBER : MabUiTheme.SHELL_PANEL_BG);
        b.setOpaque(true);
        b.setBorder(new LineBorder(primary ? MabUiTheme.C_AMBER : MabUiTheme.GRID_LINE_HI, 1));
        b.setFocusPainted(false);
        b.setFocusable(false);
        return b;
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
}
