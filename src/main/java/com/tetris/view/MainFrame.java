package com.tetris.view;

import com.tetris.controller.InputHandler;
import com.tetris.model.GameState;

import javax.swing.*;
import java.awt.*;

/**
 * MainFrame.java
 * ==============
 * The top-level JFrame window for the Tetris game.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * WINDOW LAYOUT
 * ═══════════════════════════════════════════════════════════════════════
 *
 *   ┌──────────────────────────────────────────┐
 *   │  Modern Tetris                       [─□X] │
 *   ├────────────────┬────────────────────────┤
 *   │                │                          │
 *   │   SIDE PANEL   │      GAME PANEL          │
 *   │   (Hold, Next, │      (10×20 playfield)   │
 *   │    Score, etc.) │                          │
 *   │                │                          │
 *   │                │                          │
 *   │                │                          │
 *   │                │                          │
 *   │                │                          │
 *   │                │                          │
 *   │                │                          │
 *   └────────────────┴────────────────────────┘
 *
 * Uses BorderLayout:
 *   - WEST: SidePanel (hold, next, score, controls)
 *   - CENTER: GamePanel (the playfield)
 *
 * ═══════════════════════════════════════════════════════════════════════
 * FOCUS & INPUT
 * ═══════════════════════════════════════════════════════════════════════
 * The GamePanel is set as the focusable component and has the KeyListener
 * attached. This ensures keyboard input is captured even after clicking
 * other UI elements.
 *
 * The frame is resizable — all panels adapt to the available space.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * DOUBLE BUFFERING
 * ═══════════════════════════════════════════════════════════════════════
 * Swing's default double-buffering is enabled to prevent flickering
 * during the 60fps repaint cycle.
 */
public class MainFrame extends JFrame {

    /** Reference to the game panel for repainting. */
    private final GamePanel gamePanel;

    /** Reference to the side panel for repainting. */
    private final SidePanel sidePanel;

    // ─────────────────────── Constructor ─────────────────────────

    /**
     * Creates the main window and lays out the panels.
     *
     * @param gameState    the initial game state to render
     * @param inputHandler the keyboard input handler to attach
     */
    public MainFrame(GameState gameState, InputHandler inputHandler) {
        super("LHC // TETRIS");

        // ──── Create panels ────
        gamePanel = new GamePanel(gameState);
        sidePanel = new SidePanel(gameState);

        // ──── Layout ────
        setLayout(new BorderLayout(5, 0));
        add(sidePanel, BorderLayout.WEST);
        add(gamePanel, BorderLayout.CENTER);

        // ──── Window properties ────
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(true);
        setMinimumSize(new Dimension(420, 400));
        getContentPane().setBackground(new Color(4, 6, 14));
        pack();
        setLocationRelativeTo(null);  // Center on screen

        // ──── Input handling ────
        // Attach key listener to both the game panel and the frame
        gamePanel.setFocusable(true);
        gamePanel.addKeyListener(inputHandler);
        gamePanel.requestFocusInWindow();

        // Also listen on the frame for safety
        addKeyListener(inputHandler);

        // Ensure focus returns to game panel when clicked
        gamePanel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                gamePanel.requestFocusInWindow();
            }
        });
    }

    // ─────────────────────── State Update ───────────────────────

    /**
     * Updates the game state reference when the game restarts.
     * Both panels need the new state to render correctly.
     *
     * @param gameState the new game state
     */
    public void setGameState(GameState gameState) {
        gamePanel.setGameState(gameState);
        sidePanel.setGameState(gameState);
    }

    /**
     * Override repaint to ensure both panels are repainted.
     */
    @Override
    public void repaint() {
        super.repaint();
        if (gamePanel != null) gamePanel.repaint();
        if (sidePanel != null) sidePanel.repaint();
    }

    /**
     * Returns focus to the game panel (e.g., after a settings dialog closes).
     */
    public void requestGameFocus() {
        gamePanel.requestFocusInWindow();
    }
}
