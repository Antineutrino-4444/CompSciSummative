package com.tetris;

import com.tetris.controller.GameController;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Main.java
 * =========
 * Entry point for the Modern Tetris application.
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

    /**
     * Application entry point.
     *
     * @param args command-line arguments (optional: first arg = start level)
     */
    public static void main(String[] args) {
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
            GameController controller = new GameController(level);
            controller.start();
        });
    }
}
