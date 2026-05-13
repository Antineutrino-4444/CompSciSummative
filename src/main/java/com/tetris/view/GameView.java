package com.tetris.view;

import com.tetris.controller.InputHandler;
import com.tetris.model.GameState;
import com.tetris.view.theme.Components;
import com.tetris.view.theme.Components.ButtonStyle;
import com.tetris.view.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * GameView.java — embeddable Tetris play view.
 *
 * Bundles the {@link SidePanel} HUD on the left, the {@link GamePanel}
 * playfield on the right, and a thin top toolbar (Back / Settings /
 * Restart). It owns its own ~60fps repaint timer; callers must invoke
 * {@link #shutdown()} when removing the view from the UI so the timer
 * stops cleanly.
 *
 * Designed to live inside {@link StartMenu}'s CardLayout so the player
 * never leaves the launcher window when starting a match. {@link MainFrame}
 * still exists as a thin {@code JFrame} wrapper for tooling that prefers
 * a separate window.
 */
public class GameView extends JPanel {

    private final GamePanel gamePanel;
    private final SidePanel sidePanel;
    private final NextPanel nextPanel;
    private final Timer repaintTimer;
    private final Runnable onBack;

    /** True once {@link #shutdown()} has run, so we don't double-stop. */
    private boolean disposed = false;

    public GameView(GameState gameState, InputHandler inputHandler, Runnable onBack) {
        super(new BorderLayout(0, 0));
        this.onBack = onBack != null ? onBack : () -> {};

        setBackground(Theme.BG_0);
        setBorder(new EmptyBorder(Theme.SPACE_M, Theme.SPACE_M, Theme.SPACE_M, Theme.SPACE_M));

        gamePanel = new GamePanel(gameState);
        sidePanel = new SidePanel(gameState);
        nextPanel = new NextPanel(gameState);

        add(buildToolbar(), BorderLayout.NORTH);
        add(sidePanel, BorderLayout.WEST);
        add(gamePanel, BorderLayout.CENTER);
        add(nextPanel, BorderLayout.EAST);

        // Single key listener on the playfield so input is never dropped
        // when focus shuffles between the side panel and the playfield.
        gamePanel.setFocusable(true);
        gamePanel.addKeyListener(inputHandler);
        SwingUtilities.invokeLater(gamePanel::requestFocusInWindow);

        repaintTimer = new Timer(16, e -> {
            gamePanel.repaint();
            sidePanel.repaint();
            nextPanel.repaint();
        });
        repaintTimer.start();
    }

    /** Lets the controller swap state on restart. */
    public void setGameState(GameState gameState) {
        gamePanel.setGameState(gameState);
        sidePanel.setGameState(gameState);
        nextPanel.setGameState(gameState);
        gamePanel.repaint();
        sidePanel.repaint();
        nextPanel.repaint();
        gamePanel.requestFocusInWindow();
    }

    /** Returns focus to the playfield (called after sub-dialogs close). */
    public void requestGameFocus() {
        gamePanel.requestFocusInWindow();
    }

    /** Step 23 \u2014 expose sub-panels so the MAB battle shell can
     *  re-host them in a different layout while still piggy-backing on
     *  this view's repaint timer and key listener wiring. */
    public GamePanel getGamePanel() { return gamePanel; }
    public NextPanel getNextPanel() { return nextPanel; }
    public SidePanel getSidePanel() { return sidePanel; }

    /** Stops the repaint timer. Idempotent. */
    public void shutdown() {
        if (disposed) return;
        disposed = true;
        repaintTimer.stop();
    }

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.BG_1);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.DIVIDER),
                new EmptyBorder(Theme.SPACE_S, Theme.SPACE_M, Theme.SPACE_S, Theme.SPACE_M)));

        JButton back = Components.button("\u25C2  BACK", ButtonStyle.SECONDARY);
        back.addActionListener(e -> {
            shutdown();
            onBack.run();
        });
        bar.add(back, BorderLayout.WEST);

        JLabel title = Components.label("MODERN TETRIS",
                Theme.FONT_MONO_BOLD, Theme.ACCENT);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        bar.add(title, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_S, 0));
        right.setOpaque(false);

        JButton settings = Components.button("\u2699  SETTINGS", ButtonStyle.TEXT);
        settings.addActionListener(e -> {
            SettingsPanel.showDialog(SwingUtilities.getWindowAncestor(this) instanceof JFrame f ? f : null);
            requestGameFocus();
        });
        right.add(settings);

        bar.add(right, BorderLayout.EAST);
        return bar;
    }
}
