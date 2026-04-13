package com.tetris.controller;

import com.tetris.model.Settings;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.HashSet;
import java.util.Set;

/**
 * InputHandler.java
 * =================
 * Handles keyboard input for the Tetris game, implementing DAS (Delayed Auto Shift)
 * and ARR (Auto Repeat Rate) for responsive, modern-feeling controls.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * CONTROL SCHEME
 * ═══════════════════════════════════════════════════════════════════════
 * All key bindings are configurable via the Settings dialog (F1).
 * Default bindings:
 *
 *   Key              Action
 *   ───              ──────
 *   Left Arrow       Move piece left
 *   Right Arrow      Move piece right
 *   Down Arrow       Soft drop (move down faster)
 *   Up Arrow         Rotate clockwise
 *   Z                Rotate counter-clockwise
 *   A                Rotate 180°
 *   Space            Hard drop (instant drop & lock)
 *   C / Shift        Hold piece
 *   P / Escape       Pause / Resume
 *   R                Reset
 *   F1               Open Settings
 *
 * ═══════════════════════════════════════════════════════════════════════
 * DAS & ARR (Delayed Auto Shift & Auto Repeat Rate)
 * ═══════════════════════════════════════════════════════════════════════
 * All timing values are read from Settings at runtime, so changes in the
 * Settings dialog take effect immediately without restarting.
 *
 *   1. First press: move once immediately.
 *   2. Wait DAS (configurable, default 167ms).
 *   3. After DAS triggers, repeat every ARR (configurable, default 33ms).
 *
 * SOFT DROP (SDF):
 *   - SDF (Soft Drop Factor): piece drops at SDF× gravity speed.
 *   - A value of 0 means instant soft drop (teleport to bottom).
 *
 * ═══════════════════════════════════════════════════════════════════════
 * IMPLEMENTATION NOTES
 * ═══════════════════════════════════════════════════════════════════════
 * - All key codes are looked up from Settings.get() at runtime.
 * - DAS/ARR/SDF values are also read from Settings each frame.
 * - This allows hot-reloading of settings without restarting the game.
 * - We track pressed keys in a Set to support simultaneous key presses.
 * - Rotation, hard drop, hold, etc. are single-fire (don't repeat on hold).
 */
public class InputHandler implements KeyListener {

    // ─────────────────────── State ──────────────────────────────

    /** Set of currently pressed key codes. */
    private final Set<Integer> pressedKeys = new HashSet<>();

    /** Set of keys that have been consumed (for single-fire actions). */
    private final Set<Integer> consumedKeys = new HashSet<>();

    /** Timestamps for DAS tracking per direction. */
    private long leftPressTime;
    private long rightPressTime;
    private boolean leftDASActive;
    private boolean rightDASActive;
    private long lastLeftRepeat;
    private long lastRightRepeat;
    private long lastDownRepeat;

    /**
     * Flags set in keyPressed() and consumed in processInput() to guarantee
     * the initial move fires exactly once, regardless of timing jitter
     * between the key event and the next game loop frame.
     */
    private volatile boolean leftJustPressed;
    private volatile boolean rightJustPressed;
    private volatile boolean downJustPressed;

    /** Reference to the game controller for triggering actions. */
    private final GameController controller;

    // ─────────────────────── Constructor ─────────────────────────

    public InputHandler(GameController controller) {
        this.controller = controller;
    }

    // ─────────────────────── KeyListener ────────────────────────

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        Settings s = Settings.get();
        if (!pressedKeys.contains(code)) {
            pressedKeys.add(code);
            long now = System.currentTimeMillis();

            // Track DAS start time for movement keys (compared against settings)
            if (s.isMoveLeft(code)) {
                leftPressTime = now;
                leftDASActive = false;
                lastLeftRepeat = now;
                leftJustPressed = true;
            } else if (s.isMoveRight(code)) {
                rightPressTime = now;
                rightDASActive = false;
                lastRightRepeat = now;
                rightJustPressed = true;
            } else if (s.isSoftDrop(code)) {
                lastDownRepeat = now;
                downJustPressed = true;
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        pressedKeys.remove(code);
        consumedKeys.remove(code);

        Settings s = Settings.get();
        // Reset DAS state
        if (s.isMoveLeft(code))  leftDASActive = false;
        if (s.isMoveRight(code)) rightDASActive = false;
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // Not used — we use keyPressed/keyReleased for precise control
    }

    // ─────────────────────── Input Processing ───────────────────

    /**
     * Called every frame from the game loop. Processes all currently pressed
     * keys and triggers the appropriate game actions.
     *
     * All key codes and timing values are read from Settings.get() each frame,
     * so rebinding keys or changing DAS/ARR takes effect immediately.
     */
    public void processInput() {
        long now = System.currentTimeMillis();
        Settings s = Settings.get();

        // ──── Single-fire actions (don't repeat on hold) ────

        // Hard drop
        if (isNewPress(s.getKeyHardDrop())) {
            controller.hardDrop();
        }

        // Rotate CW
        if (isNewPress(s.getKeyRotateCW())) {
            controller.rotateCW();
        }

        // Rotate CCW
        if (isNewPress(s.getKeyRotateCCW())) {
            controller.rotateCCW();
        }

        // Rotate 180°
        if (isNewPress(s.getKeyRotate180())) {
            controller.rotate180();
        }

        // Hold (primary or alt)
        if (isNewPress(s.getKeyHold()) || isNewPress(s.getKeyHoldAlt())) {
            controller.hold();
        }

        // Pause (primary or alt)
        if (isNewPress(s.getKeyPause()) || isNewPress(s.getKeyPauseAlt())) {
            controller.togglePause();
        }

        // Reset
        if (isNewPress(s.getKeyReset())) {
            controller.restart();
        }

        // Settings
        if (isNewPress(s.getKeySettings())) {
            controller.openSettings();
        }

        // ──── DAS/ARR movement (left/right) ────
        long dasDelay = s.getDasDelay();
        long arrInterval = Math.max(1, s.getArrInterval());

        // Move left
        if (pressedKeys.contains(s.getKeyMoveLeft())) {
            if (leftJustPressed) {
                controller.moveLeft();
                leftJustPressed = false;
            } else if (!leftDASActive) {
                if (now - leftPressTime >= dasDelay) {
                    leftDASActive = true;
                    lastLeftRepeat = now;
                    controller.moveLeft();
                }
            } else {
                if (s.getArrInterval() == 0) {
                    // ARR=0: instant — move all the way in one frame
                    for (int i = 0; i < 10; i++) controller.moveLeft();
                } else if (now - lastLeftRepeat >= arrInterval) {
                    controller.moveLeft();
                    lastLeftRepeat = now;
                }
            }
        }

        // Move right
        if (pressedKeys.contains(s.getKeyMoveRight())) {
            if (rightJustPressed) {
                controller.moveRight();
                rightJustPressed = false;
            } else if (!rightDASActive) {
                if (now - rightPressTime >= dasDelay) {
                    rightDASActive = true;
                    lastRightRepeat = now;
                    controller.moveRight();
                }
            } else {
                if (s.getArrInterval() == 0) {
                    for (int i = 0; i < 10; i++) controller.moveRight();
                } else if (now - lastRightRepeat >= arrInterval) {
                    controller.moveRight();
                    lastRightRepeat = now;
                }
            }
        }

        // ──── Soft drop (SDF-based) ────
        if (pressedKeys.contains(s.getKeySoftDrop())) {
            int sdf = s.getSoftDropFactor();
            if (downJustPressed) {
                controller.softDrop();
                downJustPressed = false;
                lastDownRepeat = now;
            } else if (sdf == 0) {
                // SDF=0: instant drop (teleport to ghost position)
                for (int i = 0; i < 40; i++) controller.softDrop();
                lastDownRepeat = now;
            } else {
                long softDropInterval = Math.max(1, controller.getGravityInterval() / sdf);
                int maxDropsPerFrame = 20;
                int drops = 0;
                while (now - lastDownRepeat >= softDropInterval && drops < maxDropsPerFrame) {
                    controller.softDrop();
                    lastDownRepeat += softDropInterval;
                    drops++;
                }
                if (drops >= maxDropsPerFrame) {
                    lastDownRepeat = now;
                }
            }
        }
    }

    /**
     * Checks if a key is freshly pressed (not yet consumed for single-fire actions).
     * Marks it as consumed after returning true.
     */
    private boolean isNewPress(int keyCode) {
        if (keyCode == 0) return false; // unbound
        if (pressedKeys.contains(keyCode) && !consumedKeys.contains(keyCode)) {
            consumedKeys.add(keyCode);
            return true;
        }
        return false;
    }

    // ─────────────────────── IRS/IHS Support ────────────────────

    /**
     * Checks if a key is currently held down (regardless of consumed state).
     */
    public boolean isKeyHeld(int keyCode) {
        return pressedKeys.contains(keyCode);
    }

    /**
     * Checks if a key is pressed but not yet consumed.
     * Used for IRS/IHS "tap" mode.
     */
    public boolean hasUnconsumedKey(int keyCode) {
        return keyCode != 0 && pressedKeys.contains(keyCode) && !consumedKeys.contains(keyCode);
    }

    /**
     * Marks a key as consumed (won't fire again until released and re-pressed).
     */
    public void consumeKey(int keyCode) {
        consumedKeys.add(keyCode);
    }
}
