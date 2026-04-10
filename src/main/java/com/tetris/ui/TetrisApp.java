package com.tetris.ui;

import com.tetris.model.GameState;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.util.HashSet;
import java.util.Set;

/**
 * The main JavaFX application entry point for Modern Tetris.
 *
 * <p>This class sets up the game window, handles keyboard input, and drives
 * the game loop using a JavaFX {@link AnimationTimer}.</p>
 *
 * <h3>Controls</h3>
 * <ul>
 *   <li><b>Left Arrow / A</b> — Move left</li>
 *   <li><b>Right Arrow / D</b> — Move right</li>
 *   <li><b>Down Arrow / S</b> — Soft drop</li>
 *   <li><b>Space</b> — Hard drop</li>
 *   <li><b>Up Arrow / X</b> — Rotate clockwise</li>
 *   <li><b>Z / Control</b> — Rotate counter-clockwise</li>
 *   <li><b>C / Shift</b> — Hold piece</li>
 *   <li><b>Escape / F1</b> — Pause</li>
 *   <li><b>R</b> — Restart (when game over)</li>
 * </ul>
 */
public class TetrisApp extends Application {

    private GameState gameState;
    private GameRenderer renderer;
    private AnimationTimer gameLoop;

    /** Set of currently pressed keys (for held-key detection). */
    private final Set<KeyCode> pressedKeys = new HashSet<>();

    /** Soft drop repeat timer (for held down key). */
    private double softDropTimer;
    private static final double SOFT_DROP_RATE = 0.05; // 20 cells/sec

    @Override
    public void start(Stage primaryStage) {
        gameState = new GameState();
        renderer = new GameRenderer();

        StackPane root = new StackPane(renderer.getCanvas());
        root.setStyle("-fx-background-color: #14141E;");
        Scene scene = new Scene(root, GameRenderer.CANVAS_WIDTH, GameRenderer.CANVAS_HEIGHT);

        // Key press handler
        scene.setOnKeyPressed(e -> {
            KeyCode code = e.getCode();
            boolean wasPressed = pressedKeys.contains(code);
            pressedKeys.add(code);

            if (!wasPressed) {
                handleKeyPress(code);
            }
        });

        // Key release handler
        scene.setOnKeyReleased(e -> {
            KeyCode code = e.getCode();
            pressedKeys.remove(code);
            handleKeyRelease(code);
        });

        // Game loop
        gameLoop = new AnimationTimer() {
            private long lastTime = -1;

            @Override
            public void handle(long now) {
                if (lastTime < 0) {
                    lastTime = now;
                    return;
                }

                double deltaTime = (now - lastTime) / 1_000_000_000.0;
                lastTime = now;

                // Clamp delta to avoid huge jumps (e.g. when window was minimized)
                deltaTime = Math.min(deltaTime, 0.1);

                // Handle held soft drop
                if (pressedKeys.contains(KeyCode.DOWN) || pressedKeys.contains(KeyCode.S)) {
                    softDropTimer += deltaTime;
                    while (softDropTimer >= SOFT_DROP_RATE) {
                        softDropTimer -= SOFT_DROP_RATE;
                        gameState.softDrop();
                    }
                }

                gameState.update(deltaTime);
                renderer.render(gameState);
            }
        };
        gameLoop.start();

        primaryStage.setTitle("Particle Tetris — Quark Forge");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();

        // Request focus for key events
        renderer.getCanvas().requestFocus();
    }

    /**
     * Handles a key press event (first press only, not repeats).
     *
     * @param code the key code that was pressed
     */
    private void handleKeyPress(KeyCode code) {
        // Restart
        if (code == KeyCode.R && gameState.isGameOver()) {
            gameState = new GameState(1);
            return;
        }

        // Pause
        if (code == KeyCode.ESCAPE || code == KeyCode.F1) {
            gameState.togglePause();
            return;
        }

        if (gameState.isGameOver() || gameState.isPaused()) return;

        switch (code) {
            // Movement
            case LEFT, A -> gameState.startDAS(-1);
            case RIGHT, D -> gameState.startDAS(1);
            case DOWN, S -> {
                softDropTimer = 0;
                gameState.softDrop();
            }

            // Hard drop
            case SPACE -> gameState.hardDrop();

            // Rotation
            case UP, X -> gameState.rotateCW();
            case Z, CONTROL -> gameState.rotateCCW();

            // Hold
            case C, SHIFT -> gameState.hold();

            default -> { /* Ignore other keys */ }
        }
    }

    /**
     * Handles a key release event.
     *
     * @param code the key code that was released
     */
    private void handleKeyRelease(KeyCode code) {
        switch (code) {
            case LEFT, A -> gameState.stopDAS(-1);
            case RIGHT, D -> gameState.stopDAS(1);
            case DOWN, S -> softDropTimer = 0;
            default -> { /* Ignore */ }
        }
    }

    /**
     * Application entry point.
     *
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) {
        launch(args);
    }
}
