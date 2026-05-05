package com.tetris.controller;

import com.tetris.model.Settings;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.HashSet;
import java.util.Set;

/**
 * InputHandler.java
 * =================
 * jstris-style frame-counting DAS / ARR.
 *
 * ─────────────────────────────────────────────────────────────────
 * THE BUG WE FIX HERE
 * ─────────────────────────────────────────────────────────────────
 * Swing's game-loop {@link javax.swing.Timer} and AWT key events all
 * dispatch on the EDT. When the user releases a key just before a
 * timer tick, the event order on the EDT queue can be:
 *
 *      [timer-tick → processInput()] → [keyReleased]
 *
 * `processInput` then runs against stale {@code pressedKeys} that
 * still contains the key, fires one more ARR shift, *then* the
 * release event finally executes. That's the classic "piece moves
 * one extra cell after I let go" feel.
 *
 * Fix: at the top of {@link #processInput()} we drain every pending
 * {@link KeyEvent} from the AWT {@link EventQueue} synchronously, so
 * the input snapshot is always current as of the moment the game
 * tick decides what to do.
 *
 * ─────────────────────────────────────────────────────────────────
 * FRAME-COUNTING DAS / ARR (jstris's "FPS-based DAS")
 * ─────────────────────────────────────────────────────────────────
 * The Jstris settings dialog has a "FPS-based DAS — Evaluate DAS on
 * fixed intervals" toggle that is ON by default. Counting DAS in
 * frames rather than wall-clock ms gives perfectly stable handling
 * even when {@code System.nanoTime()} jitter or GC pauses would
 * otherwise produce the occasional double-shift on a single frame.
 *
 * We translate Settings DAS/ARR (ms) into frame counts using the
 * controller's frame interval:
 *
 *      dasFrames = ceil(DAS_ms / FRAME_INTERVAL_MS)
 *      arrFrames = max(1, round(ARR_ms / FRAME_INTERVAL_MS))   if ARR > 0
 *      arrFrames = 0                                            if ARR == 0 (instant)
 *
 * One shift fires per frame at most (except ARR=0 → entire row
 * teleport), exactly like jstris.
 *
 * ─────────────────────────────────────────────────────────────────
 * WINDOWS AUTO-REPEAT
 * ─────────────────────────────────────────────────────────────────
 * On Windows + Swing, holding a key fires repeated {@code keyPressed}
 * events with NO interleaved {@code keyReleased}. We just ignore
 * presses for keys that are already in {@code pressedKeys} — no
 * heuristics needed.
 */
public class InputHandler implements KeyListener {

    /** Game-loop frame interval — must match GameController's value. */
    private static final int FRAME_INTERVAL_MS = 16;

    // ─────────────────────── State ──────────────────────────────

    private final Set<Integer> pressedKeys  = new HashSet<>();
    private final Set<Integer> consumedKeys = new HashSet<>();

    // Frame counters (incremented once per processInput call while held).
    private int leftFramesHeld;
    private int rightFramesHeld;
    private boolean leftDASCharged;
    private boolean rightDASCharged;
    /** Frames since the last ARR shift fired (so ARR>1 paces correctly). */
    private int leftFramesSinceShift;
    private int rightFramesSinceShift;

    // Soft drop uses ms-paced gravity scaling, kept on nanoTime for accuracy.
    private long lastDownRepeatNs;

    /** Set in keyPressed, consumed once on the next processInput tick. */
    private boolean leftJustPressed;
    private boolean rightJustPressed;
    private boolean downJustPressed;

    private final GameController controller;

    public InputHandler(GameController controller) {
        this.controller = controller;
    }

    // ─────────────────────── KeyListener ────────────────────────

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        // Windows fires repeated keyPressed for held keys without a
        // matching release. The first one adds the key; subsequent
        // ones are no-ops so DAS isn't restarted.
        if (!pressedKeys.add(code)) return;

        Settings s = Settings.get();
        if (s.isMoveLeft(code)) {
            leftFramesHeld = 0;
            leftDASCharged = false;
            leftFramesSinceShift = 0;
            leftJustPressed = true;
        } else if (s.isMoveRight(code)) {
            rightFramesHeld = 0;
            rightDASCharged = false;
            rightFramesSinceShift = 0;
            rightJustPressed = true;
        } else if (s.isSoftDrop(code)) {
            lastDownRepeatNs = System.nanoTime();
            downJustPressed = true;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        pressedKeys.remove(code);
        consumedKeys.remove(code);

        Settings s = Settings.get();
        if (s.isMoveLeft(code)) {
            leftDASCharged = false;
            leftFramesHeld = 0;
            leftFramesSinceShift = 0;
        }
        if (s.isMoveRight(code)) {
            rightDASCharged = false;
            rightFramesHeld = 0;
            rightFramesSinceShift = 0;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) { /* unused */ }

    // ─────────────────────── EDT race fix ──────────────────────

    /**
     * Drain every pending {@link KeyEvent} from the AWT event queue
     * synchronously so that {@link #pressedKeys} reflects exactly
     * what the user is doing at this instant in time, not what they
     * were doing a frame ago. This eliminates the "one extra cell
     * after release" race entirely.
     */
    private void drainPendingKeyEvents() {
        EventQueue queue = Toolkit.getDefaultToolkit().getSystemEventQueue();
        while (true) {
            AWTEvent peek = queue.peekEvent(KeyEvent.KEY_PRESSED);
            AWTEvent peekRel = queue.peekEvent(KeyEvent.KEY_RELEASED);
            if (peek == null && peekRel == null) return;
            try {
                AWTEvent ev = queue.getNextEvent();
                if (ev instanceof KeyEvent ke) {
                    int id = ke.getID();
                    if (id == KeyEvent.KEY_PRESSED)        keyPressed(ke);
                    else if (id == KeyEvent.KEY_RELEASED)  keyReleased(ke);
                    else if (id == KeyEvent.KEY_TYPED)     keyTyped(ke);
                }
                // Non-KeyEvents from the peek (shouldn't happen with the
                // ID filter, but be safe) are simply discarded; they'd be
                // processed on the next normal EDT pump otherwise. Since
                // we're already on the EDT, dispatch them so we don't
                // starve other components.
                else if (ev != null) {
                    Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(ev);
                    return;
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    // ─────────────────────── Input Processing ───────────────────

    public void processInput() {
        drainPendingKeyEvents();

        Settings s = Settings.get();

        // ──── Single-fire actions ────
        if (isNewPress(s.getKeyHardDrop()))  controller.hardDrop();
        if (isNewPress(s.getKeyRotateCW()))  controller.rotateCW();
        if (isNewPress(s.getKeyRotateCCW())) controller.rotateCCW();
        if (isNewPress(s.getKeyRotate180())) controller.rotate180();
        if (isNewPress(s.getKeyHold())  || isNewPress(s.getKeyHoldAlt()))  controller.hold();
        if (isNewPress(s.getKeyPause()) || isNewPress(s.getKeyPauseAlt())) controller.togglePause();
        if (isNewPress(s.getKeyReset()))    controller.restart();
        if (isNewPress(s.getKeySettings())) controller.openSettings();

        // ──── Frame-counting DAS / ARR ────
        int dasFrames = Math.max(1, (s.getDasDelay() + FRAME_INTERVAL_MS - 1) / FRAME_INTERVAL_MS);
        int arrFrames = s.getArrInterval() == 0
                ? 0
                : Math.max(1, (s.getArrInterval() + FRAME_INTERVAL_MS / 2) / FRAME_INTERVAL_MS);

        // Move left
        if (pressedKeys.contains(s.getKeyMoveLeft())) {
            if (leftJustPressed) {
                controller.moveLeft();
                leftJustPressed = false;
            } else {
                leftFramesHeld++;
                if (!leftDASCharged) {
                    if (leftFramesHeld >= dasFrames) {
                        leftDASCharged = true;
                        leftFramesSinceShift = 0;
                        controller.moveLeft();
                    }
                } else if (arrFrames == 0) {
                    // Instant: travel the whole row this frame.
                    for (int i = 0; i < 10; i++) controller.moveLeft();
                } else {
                    leftFramesSinceShift++;
                    if (leftFramesSinceShift >= arrFrames) {
                        controller.moveLeft();
                        leftFramesSinceShift = 0;
                    }
                }
            }
        }

        // Move right
        if (pressedKeys.contains(s.getKeyMoveRight())) {
            if (rightJustPressed) {
                controller.moveRight();
                rightJustPressed = false;
            } else {
                rightFramesHeld++;
                if (!rightDASCharged) {
                    if (rightFramesHeld >= dasFrames) {
                        rightDASCharged = true;
                        rightFramesSinceShift = 0;
                        controller.moveRight();
                    }
                } else if (arrFrames == 0) {
                    for (int i = 0; i < 10; i++) controller.moveRight();
                } else {
                    rightFramesSinceShift++;
                    if (rightFramesSinceShift >= arrFrames) {
                        controller.moveRight();
                        rightFramesSinceShift = 0;
                    }
                }
            }
        }

        // ──── Soft drop (kept ms-based — needs to scale with gravity) ────
        if (pressedKeys.contains(s.getKeySoftDrop())) {
            long now = System.nanoTime();
            int sdf = s.getSoftDropFactor();
            if (downJustPressed) {
                controller.softDrop();
                downJustPressed = false;
                lastDownRepeatNs = now;
            } else if (sdf == 0) {
                for (int i = 0; i < 40; i++) controller.softDrop();
                lastDownRepeatNs = now;
            } else {
                long gravityIntervalNs  = controller.getGravityInterval() * 1_000_000L;
                long softDropIntervalNs = Math.max(1L, gravityIntervalNs / sdf);
                int maxDropsPerFrame = 20;
                int drops = 0;
                while (now - lastDownRepeatNs >= softDropIntervalNs && drops < maxDropsPerFrame) {
                    controller.softDrop();
                    lastDownRepeatNs += softDropIntervalNs;
                    drops++;
                }
                if (drops >= maxDropsPerFrame) lastDownRepeatNs = now;
            }
        }
    }

    private boolean isNewPress(int keyCode) {
        if (keyCode == 0) return false;
        if (pressedKeys.contains(keyCode) && !consumedKeys.contains(keyCode)) {
            consumedKeys.add(keyCode);
            return true;
        }
        return false;
    }

    // ─────────────────────── IRS / IHS ─────────────────────────

    public boolean isKeyHeld(int keyCode) {
        return pressedKeys.contains(keyCode);
    }

    public boolean hasUnconsumedKey(int keyCode) {
        return keyCode != 0 && pressedKeys.contains(keyCode) && !consumedKeys.contains(keyCode);
    }

    public void consumeKey(int keyCode) {
        consumedKeys.add(keyCode);
    }
}
