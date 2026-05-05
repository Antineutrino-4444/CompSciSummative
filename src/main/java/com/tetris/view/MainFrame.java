package com.tetris.view;

import com.tetris.controller.InputHandler;
import com.tetris.model.GameState;
import com.tetris.view.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * MainFrame.java — top-level game window.
 *
 * Hosts the playfield ({@link GamePanel}) in the center and the HUD
 * ({@link SidePanel}) on the left. Provides:
 *
 *   • A single key listener on the playfield so input never gets
 *     dropped after focus shuffles.
 *   • A Tools menu with Settings / Nuke Builder / Restart / Quit.
 *   • A timer that drives ~60fps repaint of both panels.
 *   • Confirm-on-close so a stray Alt-F4 doesn't kill the game.
 */
public class MainFrame extends JFrame {

    private final GamePanel gamePanel;
    private final SidePanel sidePanel;
    private final Timer repaintTimer;

    /** Allows the controller to swap state on restart. */
    public void setGameState(GameState gameState) {
        gamePanel.setGameState(gameState);
        sidePanel.setGameState(gameState);
        gamePanel.repaint();
        sidePanel.repaint();
    }

    /** Returns focus to the playfield (called after dialogs close). */
    public void requestGameFocus() {
        gamePanel.requestFocusInWindow();
    }

    public MainFrame(GameState gameState, InputHandler inputHandler) {
        super("Modern Tetris");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        // Fullscreen-only: borderless, maximized to fill the screen.
        setUndecorated(true);
        setResizable(false);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(screen);
        setLocation(0, 0);
        getContentPane().setBackground(Theme.BG_0);

        // ── Layout ──
        JPanel root = new JPanel(new BorderLayout(Theme.SPACE_M, 0));
        root.setBackground(Theme.BG_0);
        root.setBorder(new EmptyBorder(Theme.SPACE_M, Theme.SPACE_M, Theme.SPACE_M, Theme.SPACE_M));
        setContentPane(root);

        sidePanel = new SidePanel(gameState);
        gamePanel = new GamePanel(gameState);

        root.add(sidePanel, BorderLayout.WEST);
        root.add(gamePanel, BorderLayout.CENTER);

        // ── Input: single listener on the playfield ──
        gamePanel.setFocusable(true);
        gamePanel.addKeyListener(inputHandler);
        SwingUtilities.invokeLater(gamePanel::requestFocusInWindow);
        // Re-request focus whenever the panel becomes visible / gains the window.
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowActivated(java.awt.event.WindowEvent e) {
                gamePanel.requestFocusInWindow();
            }
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                if (confirmExit()) {
                    repaintTimer.stop();
                    dispose();
                }
            }
        });

        // ── Menu bar ──
        setJMenuBar(buildMenuBar());

        // ── Repaint pump ──
        repaintTimer = new Timer(16, e -> {
            gamePanel.repaint();
            sidePanel.repaint();
        });
        repaintTimer.start();
    }

    private boolean confirmExit() {
        // No prompt — exit immediately.
        return true;
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        bar.setBackground(Theme.BG_1);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.DIVIDER));

        JMenu game = new JMenu("Game");
        game.setForeground(Theme.TEXT_PRIMARY);
        game.setFont(Theme.FONT_BODY);

        JMenuItem quit = new JMenuItem("Return to menu");
        quit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
        quit.addActionListener(e -> {
            if (confirmExit()) { repaintTimer.stop(); dispose(); }
        });
        game.add(quit);

        JMenu tools = new JMenu("Tools");
        tools.setForeground(Theme.TEXT_PRIMARY);
        tools.setFont(Theme.FONT_BODY);

        JMenuItem settings = new JMenuItem("Settings...");
        settings.addActionListener(e -> {
            SettingsPanel.showDialog(this);
            gamePanel.requestFocusInWindow();
        });

        JMenuItem nuke = new JMenuItem("Nuke Builder...");
        nuke.addActionListener(e -> {
            NukeBuilderDialog.showDialog(this);
            gamePanel.requestFocusInWindow();
        });

        tools.add(settings);
        tools.add(nuke);

        bar.add(game);
        bar.add(tools);
        return bar;
    }
}
