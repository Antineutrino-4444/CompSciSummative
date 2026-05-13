package com.tetris.mab.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Step 18 — player-facing post-match result window.
 *
 * <p>Display + lifecycle only. The dialog never mutates the match.
 * The Restart and Back-to-Menu buttons run callbacks supplied by the
 * controller; either may be {@code null}, in which case the matching
 * button is disabled.
 *
 * <p><b>Offline-only.</b>
 */
public class MabMatchResultDialog extends JFrame {

    private final JTextArea bodyArea;
    private final JButton restartButton = new JButton("Restart MAB PvE");
    private final JButton backButton = new JButton("Back to Menu");
    private final JButton closeButton = new JButton("Close Result");

    private Runnable onRestart;
    private Runnable onBackToMenu;

    public MabMatchResultDialog() {
        super("Mutually Assured Blocks — Match Result");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        bodyArea = new JTextArea(28, 64);
        bodyArea.setEditable(false);
        bodyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        bodyArea.setBorder(new EmptyBorder(8, 8, 8, 8));

        JScrollPane scroll = new JScrollPane(bodyArea);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        restartButton.addActionListener(e -> {
            if (onRestart != null) onRestart.run();
        });
        backButton.addActionListener(e -> {
            if (onBackToMenu != null) onBackToMenu.run();
        });
        closeButton.addActionListener(e -> setVisible(false));
        buttons.add(closeButton);
        buttons.add(backButton);
        buttons.add(restartButton);

        setLayout(new BorderLayout());
        add(scroll, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        pack();
    }

    public void show(MabMatchResultSummary summary) {
        setTitle(MabMatchResultFormatter.formatTitle(summary));
        bodyArea.setText(MabMatchResultFormatter.formatBody(summary));
        bodyArea.setCaretPosition(0);
        restartButton.setEnabled(onRestart != null);
        backButton.setEnabled(onBackToMenu != null);
        setLocationRelativeTo(null);
        setVisible(true);
        toFront();
    }

    public void setRestartCallback(Runnable r) { this.onRestart = r; }
    public void setBackToMenuCallback(Runnable r) { this.onBackToMenu = r; }
}
