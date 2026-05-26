package com.tetris;

import com.tetris.audio.AudioHealth;
import com.tetris.audio.SoundEffectManager;
import com.tetris.controller.GameController;
import com.tetris.controller.GameLaunchMode;
import com.tetris.model.Settings;
import com.tetris.system.AppPaths;
import com.tetris.system.RuntimeBootstrap;
import com.tetris.view.DebugOverlay;
import com.tetris.view.StartMenu;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Main.java
 * =========
 * Entry point for the MAB application.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * APPLICATION STARTUP SEQUENCE
 * ═══════════════════════════════════════════════════════════════════════
 *
 *   1. Set the system Look & Feel (native appearance).
 *   2. Create a GameController with the starting level.
 *   3. Schedule the controller start on the Swing EDT (Event Dispatch Thread).
 *   4. GameController creates the GameState, MainFrame, and starts the game loop.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * WHY SWING EDT?
 * ═══════════════════════════════════════════════════════════════════════
 * All Swing GUI creation and manipulation MUST happen on the Event Dispatch
 * Thread to avoid threading issues. SwingUtilities.invokeLater() ensures
 * the game window is created on the correct thread.
 *
 * The game loop (Timer) also fires on the EDT, so all game logic and
 * rendering happen on the same thread — no synchronization needed.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * ARCHITECTURE OVERVIEW
 * ═══════════════════════════════════════════════════════════════════════
 *
 *   Main (entry point)
 *     └── GameController (game loop, input routing)
 *           ├── GameState (game logic, board, scoring)
 *           │     ├── Board (10×24 grid, collision, line clearing)
 *           │     ├── Tetromino (immutable piece with position + rotation)
 *           │     ├── TetrominoType (7 pieces with SRS rotation data)
 *           │     ├── SRSData (wall kick offset tables)
 *           │     ├── BagRandomizer (7-bag piece generation)
 *           │     ├── ScoreSystem (scoring, levels, gravity)
 *           │     └── Position (immutable 2D coordinate)
 *           ├── InputHandler (keyboard input, DAS/ARR)
 *           └── MainFrame (window)
 *                 ├── GamePanel (playfield rendering)
 *                 └── SidePanel (hold, next, score, controls)
 *
 * ═══════════════════════════════════════════════════════════════════════
 * MODERN TETRIS FEATURES IMPLEMENTED
 * ═══════════════════════════════════════════════════════════════════════
 *
 *   ✓ Standard 10×20 playfield with 4-row buffer zone
 *   ✓ All 7 tetrominoes (I, O, T, S, Z, J, L) with guideline colors
 *   ✓ Super Rotation System (SRS) with full wall kick tables
 *   ✓ 7-bag randomizer (fair piece distribution)
 *   ✓ Ghost piece (drop shadow preview)
 *   ✓ Hold piece (swap current piece to storage)
 *   ✓ Next piece preview (5 upcoming pieces)
 *   ✓ Hard drop (instant placement)
 *   ✓ Soft drop (accelerated gravity)
 *   ✓ Lock delay (500ms) with move reset (up to 15 resets)
 *   ✓ T-Spin detection (full and mini)
 *   ✓ Modern scoring: line clears, T-spins, combos, back-to-back
 *   ✓ Level progression (every 10 lines)
 *   ✓ Gravity curve (speeds up with level)
 *   ✓ DAS (Delayed Auto Shift, 133ms) and ARR (Auto Repeat Rate, 10ms)
 *   ✓ Pause/resume
 *   ✓ Game over detection (block out / lock out)
 *   ✓ Restart capability
 *   ✓ 3D beveled block rendering
 *   ✓ Clean MVC architecture
 */
public class Main {

    /** Default starting level. */
    private static final int DEFAULT_START_LEVEL = 1;
    private static JDialog audioFailureDialog;

    /**
     * Application entry point.
     *
     * @param args command-line arguments (optional: first arg = start level)
     */
    public static void main(String[] args) {
        RuntimeBootstrap.run();
        if (hasArg(args, "--smoke-test")) {
            runSmokeTest();
            return;
        }

        // Make the dev console + F3 overlay available on every screen
        // (menu, settings, key bindings, in-game), and show a closeable
        // popup the first time audio is disabled after repeated failures.
        DebugOverlay.shared().install();
        AudioHealth.shared().setOnTrip(Main::showAudioFailurePopup);

        // Parse optional start level from command line
        int startLevel = DEFAULT_START_LEVEL;
        if (args.length > 0) {
            try {
                startLevel = Integer.parseInt(args[0]);
                if (startLevel < 1 || startLevel > 20) {
                    System.err.println("Start level must be between 1 and 20. Using default.");
                    startLevel = DEFAULT_START_LEVEL;
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid start level. Using default level " + DEFAULT_START_LEVEL + ".");
            }
        }

        // Set native Look & Feel for better appearance
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Fall back to default L&F — not critical
        }

        // Create the controller and start on the EDT
        final int level = startLevel;
        SwingUtilities.invokeLater(() -> {
            if (Boolean.getBoolean("tetris.controls.reset")) {
                Settings.get().resetControlMappingsToDefaults();
                Settings.get().setControlsWizardCompleted(false);
                Settings.get().save();
            }
            // SFX volume + mute mirror the persisted Settings on startup so
            // the first menu hover plays at the right level immediately.
            SoundEffectManager.shared().setMasterSfxVolume(
                    (float) Settings.get().getSfxVolume());
            SoundEffectManager.shared().setMuted(Settings.get().isSfxMuted());
            // Continuous menu: the StartMenu hosts every screen (settings,
            // nuke builder, the actual game) inside a CardLayout, so the
            // launcher window stays visible the whole session.
            StartMenu menu = new StartMenu(
                    level,
                    (lvl, mode) -> new GameController(lvl, mode));
            // Step 18 — wire the MAB PvE config-aware factory so the
            // setup dialog can launch a configured PvE controller.
            menu.setMabPveFactory(com.tetris.controller.GameController::new);
            menu.setMabLocalPvpFactory(com.tetris.controller.GameController::new);
            menu.setMabAiVsAiFactory(com.tetris.controller.GameController::new);
            menu.setVisible(true);
        });
    }

    private static boolean hasArg(String[] args, String wanted) {
        for (String arg : args) {
            if (wanted.equalsIgnoreCase(arg)) return true;
        }
        return false;
    }

    private static void runSmokeTest() {
        Settings.get();
        System.out.println("[smoke] MAB startup complete.");
        System.out.println("[smoke] Data dir: " + AppPaths.dataDir());
        System.out.println("[smoke] Settings file: " + AppPaths.settingsFile());
        System.out.println("[smoke] Music root: " + AppPaths.musicDir());
    }

    private static void showAudioFailurePopup(String message) {
        SwingUtilities.invokeLater(() -> {
            if (audioFailureDialog != null && audioFailureDialog.isDisplayable()) {
                audioFailureDialog.toFront();
                audioFailureDialog.requestFocus();
                return;
            }

            Window owner = resolveActiveWindow();
            JDialog dialog = owner == null
                    ? new JDialog((Frame) null, "Audio disabled", false)
                    : new JDialog(owner, "Audio disabled", Dialog.ModalityType.MODELESS);
            dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

            JPanel body = new JPanel(new BorderLayout(0, 12));
            body.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));

            JLabel title = new JLabel("Audio disabled after repeated playback failures");
            body.add(title, BorderLayout.NORTH);

            JTextArea text = new JTextArea(message == null ? "" : message);
            text.setEditable(false);
            text.setOpaque(false);
            text.setLineWrap(true);
            text.setWrapStyleWord(true);
            text.setColumns(52);
            text.setRows(5);
            body.add(text, BorderLayout.CENTER);

            JButton close = new JButton("Close");
            close.addActionListener(e -> dialog.dispose());
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            actions.add(close);
            body.add(actions, BorderLayout.SOUTH);

            dialog.setContentPane(body);
            dialog.pack();
            dialog.setLocationRelativeTo(owner);
            dialog.addWindowListener(new WindowAdapter() {
                @Override public void windowClosed(WindowEvent e) {
                    if (audioFailureDialog == dialog) {
                        audioFailureDialog = null;
                    }
                }
            });
            audioFailureDialog = dialog;
            dialog.setVisible(true);
        });
    }

    private static Window resolveActiveWindow() {
        KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        Window win = kfm.getActiveWindow();
        if (win == null) win = kfm.getFocusedWindow();
        if (win != null) return win;
        for (Window candidate : Window.getWindows()) {
            if (candidate != null && candidate.isShowing()) {
                return candidate;
            }
        }
        return null;
    }
}
