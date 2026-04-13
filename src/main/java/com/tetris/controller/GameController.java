package com.tetris.controller;

import com.tetris.model.GameState;
import com.tetris.model.Settings;
import com.tetris.view.MainFrame;
import com.tetris.view.SettingsPanel;

import javax.swing.Timer;

/**
 * GameController.java
 * ====================
 * The central controller that connects the game model (GameState) with
 * the view (MainFrame). Runs the game loop and dispatches input actions.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * GAME LOOP ARCHITECTURE
 * ═══════════════════════════════════════════════════════════════════════
 * Uses a Swing Timer running at ~60fps (16ms interval) to:
 *   1. Process input (DAS/ARR via InputHandler).
 *   2. Update game state (gravity, lock delay).
 *   3. Repaint the view.
 *
 * Why Swing Timer instead of a Thread.sleep loop?
 *   - Swing Timer fires on the Event Dispatch Thread (EDT), so we don't
 *     need to worry about thread-safety when updating Swing components.
 *   - It's the standard approach for Swing-based games.
 *
 * FRAME RATE:
 *   Target: 60fps (16ms per frame).
 *   The game logic (gravity, lock delay) uses real timestamps, not frame
 *   counting, so it behaves correctly even if frames are dropped.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * RESPONSIBILITY SEPARATION
 * ═══════════════════════════════════════════════════════════════════════
 *   - GameController: owns the game loop timer, routes input → model
 *   - GameState: all game logic (movement validation, scoring, etc.)
 *   - InputHandler: keyboard event capture + DAS/ARR processing
 *   - MainFrame/Panels: pure rendering (reads GameState, draws pixels)
 *
 * The controller never directly modifies the board or score — it only
 * calls methods on GameState, which encapsulates all rules.
 */
public class GameController {

    /** Target frame interval in milliseconds (~60fps). */
    private static final int FRAME_INTERVAL_MS = 16;

    // ─────────────────────── Components ─────────────────────────

    private GameState gameState;
    private MainFrame mainFrame;
    private InputHandler inputHandler;
    private Timer gameLoopTimer;

    /** Starting level (remembered for restarts). */
    private final int startLevel;

    // ─────────────────────── Constructor ─────────────────────────

    /**
     * Creates a new GameController.
     *
     * @param startLevel the level to start at (1+)
     */
    public GameController(int startLevel) {
        this.startLevel = Math.max(1, startLevel);
        this.gameState = new GameState(this.startLevel);
        this.inputHandler = new InputHandler(this);
    }

    // ─────────────────────── Lifecycle ───────────────────────────

    /**
     * Initializes the view and starts the game loop.
     * Called once from Main.
     */
    public void start() {
        // Create the main window, passing this controller's input handler
        mainFrame = new MainFrame(gameState, inputHandler);
        mainFrame.setVisible(true);

        // Start the game loop timer
        gameLoopTimer = new Timer(FRAME_INTERVAL_MS, e -> gameLoop());
        gameLoopTimer.setRepeats(true);
        gameLoopTimer.start();
    }

    /**
     * The main game loop — called every frame (~60fps).
     *
     * Order of operations:
     *   1. Process input (handles DAS/ARR for held keys)
     *   2. Update game state (gravity tick, lock delay check)
     *   3. Repaint the view
     */
    private void gameLoop() {
        // 1. Process input
        inputHandler.processInput();

        // 2. Update game state
        gameState.update();

        // 3. IRS/IHS: apply queued rotation/hold on freshly spawned pieces
        //    Mode is read from Settings (off/tap/hold).
        if (gameState.wasJustSpawned()) {
            Settings s = Settings.get();
            String irsMode = s.getIrsMode();
            String ihsMode = s.getIhsMode();

            // IRS (Initial Rotation System)
            if (!"off".equals(irsMode)) {
                boolean useTap = "tap".equals(irsMode);
                if (useTap ? inputHandler.hasUnconsumedKey(s.getKeyRotateCW())
                           : inputHandler.isKeyHeld(s.getKeyRotateCW())) {
                    gameState.rotateCW();
                    inputHandler.consumeKey(s.getKeyRotateCW());
                } else if (useTap ? inputHandler.hasUnconsumedKey(s.getKeyRotateCCW())
                                  : inputHandler.isKeyHeld(s.getKeyRotateCCW())) {
                    gameState.rotateCCW();
                    inputHandler.consumeKey(s.getKeyRotateCCW());
                } else if (useTap ? inputHandler.hasUnconsumedKey(s.getKeyRotate180())
                                  : inputHandler.isKeyHeld(s.getKeyRotate180())) {
                    gameState.rotate180();
                    inputHandler.consumeKey(s.getKeyRotate180());
                }
            }

            // IHS (Initial Hold System)
            if (!"off".equals(ihsMode)) {
                boolean useTap = "tap".equals(ihsMode);
                if (useTap ? inputHandler.hasUnconsumedKey(s.getKeyHold())
                           : inputHandler.isKeyHeld(s.getKeyHold())) {
                    gameState.hold();
                    inputHandler.consumeKey(s.getKeyHold());
                } else if (useTap ? inputHandler.hasUnconsumedKey(s.getKeyHoldAlt())
                                  : inputHandler.isKeyHeld(s.getKeyHoldAlt())) {
                    gameState.hold();
                    inputHandler.consumeKey(s.getKeyHoldAlt());
                }
            }

            gameState.clearJustSpawned();
        }

        // 4. Repaint
        mainFrame.repaint();
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT ACTION METHODS
    // ═══════════════════════════════════════════════════════════════
    // Called by InputHandler when keys are pressed.
    // These simply delegate to GameState.

    public void moveLeft() {
        gameState.moveLeft();
    }

    public void moveRight() {
        gameState.moveRight();
    }

    public void softDrop() {
        gameState.softDrop();
    }

    public void hardDrop() {
        gameState.hardDrop();
    }

    public void rotateCW() {
        gameState.rotateCW();
    }

    public void rotateCCW() {
        gameState.rotateCCW();
    }

    public void rotate180() {
        gameState.rotate180();
    }

    public void hold() {
        gameState.hold();
    }

    public void togglePause() {
        gameState.togglePause();
    }

    /**
     * Restarts the game with a fresh state.
     * Can be triggered at any time — during gameplay, pause, or game over.
     */
    public void restart() {
        gameState = new GameState(startLevel);
        mainFrame.setGameState(gameState);
    }

    /**
     * Opens the settings dialog. Pauses the game while the dialog is open.
     * After the dialog closes, focus returns to the game panel.
     */
    public void openSettings() {
        boolean wasPaused = gameState.isPaused();
        if (!wasPaused && !gameState.isGameOver()) {
            gameState.togglePause();
        }
        SettingsPanel.showDialog(mainFrame);
        mainFrame.requestGameFocus();
        if (!wasPaused && !gameState.isGameOver()) {
            gameState.togglePause();
        }
    }

    /**
     * Returns the current gravity interval in milliseconds.
     * Used by InputHandler to calculate SDF-based soft drop speed.
     *
     * @return gravity interval in ms
     */
    public int getGravityInterval() {
        return gameState.getScoreSystem().getGravityInterval();
    }
}
