package com.tetris.controller;

import com.tetris.model.GameState;
import com.tetris.model.Settings;

import java.awt.event.KeyEvent;
import java.util.EnumSet;
import java.util.Set;

/**
 * Routes local keyboard events to two independent {@link GameState}
 * instances for same-keyboard MAB local PvP.
 *
 * <p>It intentionally mirrors the existing {@link InputHandler} behavior
 * without replacing it. Normal Tetris and MAB PvE keep using the original
 * one-player handler; local PvP gets this local-only router.</p>
 */
public final class LocalInputRouter {

    private final GameState playerA;
    private final GameState playerB;
    private final Settings settings;
    private final Runnable onPause;
    private final Runnable onExit;
    private final Runnable onReset;

    private final PlayerKeys p1 = new PlayerKeys();
    private final PlayerKeys p2 = new PlayerKeys();

    public LocalInputRouter(GameState playerA,
                            GameState playerB,
                            Settings settings,
                            Runnable onPause,
                            Runnable onReset) {
        this(playerA, playerB, settings, onPause, onReset, onReset);
    }

    public LocalInputRouter(GameState playerA,
                            GameState playerB,
                            Settings settings,
                            Runnable onPause,
                            Runnable onExit,
                            Runnable onReset) {
        if (playerA == null || playerB == null) throw new IllegalArgumentException("gameState");
        this.playerA = playerA;
        this.playerB = playerB;
        this.settings = settings == null ? Settings.get() : settings;
        this.onPause = onPause;
        this.onExit = onExit;
        this.onReset = onReset;
    }

    public void keyPressed(KeyEvent e) {
        if (e != null) keyPressed(e.getKeyCode());
    }

    public void keyReleased(KeyEvent e) {
        if (e != null) keyReleased(e.getKeyCode());
    }

    public void keyPressed(int code) {
        if (code == 0) return;
        p1.press(actionForPlayer1(code));
        p2.press(actionForPlayer2(code));
    }

    public void keyReleased(int code) {
        if (code == 0) return;
        p1.release(actionForPlayer1(code));
        p2.release(actionForPlayer2(code));
    }

    public void processInput() {
        processSharedActions();
        processPlayer(p1, playerA);
        processPlayer(p2, playerB);
    }

    public void releaseAll() {
        p1.releaseAll();
        p2.releaseAll();
    }

    public boolean isHeld(int playerNumber, LocalPlayerAction action) {
        return (playerNumber == 2 ? p2 : p1).held.contains(action);
    }

    private void processSharedActions() {
        if (p1.isNewPress(LocalPlayerAction.PAUSE) && onPause != null) onPause.run();
        boolean exitPressed = p1.isNewPress(LocalPlayerAction.EXIT_STAGE);
        exitPressed = p2.isNewPress(LocalPlayerAction.EXIT_STAGE) || exitPressed;
        if (exitPressed && onExit != null) {
            onExit.run();
        }
        boolean resetPressed = p1.isNewPress(LocalPlayerAction.RESET);
        resetPressed = p2.isNewPress(LocalPlayerAction.RESET) || resetPressed;
        if (resetPressed && onReset != null) {
            onReset.run();
        }
    }

    private void processPlayer(PlayerKeys keys, GameState state) {
        if (keys.isNewPress(LocalPlayerAction.HARD_DROP)) state.hardDrop();
        if (keys.isNewPress(LocalPlayerAction.ROTATE_CW)) state.rotateCW();
        if (keys.isNewPress(LocalPlayerAction.ROTATE_CCW)) state.rotateCCW();
        if (keys.isNewPress(LocalPlayerAction.HOLD)) state.hold();

        int dasFrames = FrameRate.framesForMillisCeil(settings.getDasDelay());
        int arrFrames = settings.getArrInterval() == 0
                ? 0
                : FrameRate.framesForMillisRounded(settings.getArrInterval());

        processHorizontal(keys, state, LocalPlayerAction.MOVE_LEFT, dasFrames, arrFrames);
        processHorizontal(keys, state, LocalPlayerAction.MOVE_RIGHT, dasFrames, arrFrames);
        processSoftDrop(keys, state);
    }

    private void processHorizontal(PlayerKeys keys, GameState state,
                                   LocalPlayerAction action,
                                   int dasFrames, int arrFrames) {
        if (!keys.held.contains(action)) return;
        MovementCounter counter = action == LocalPlayerAction.MOVE_LEFT
                ? keys.leftCounter : keys.rightCounter;
        Runnable move = action == LocalPlayerAction.MOVE_LEFT
                ? state::moveLeft : state::moveRight;
        if (counter.justPressed) {
            move.run();
            counter.justPressed = false;
            return;
        }
        counter.framesHeld++;
        if (!counter.dasCharged) {
            if (counter.framesHeld >= dasFrames) {
                counter.dasCharged = true;
                counter.framesSinceShift = 0;
                move.run();
            }
            return;
        }
        if (arrFrames == 0) {
            for (int i = 0; i < 10; i++) move.run();
        } else {
            counter.framesSinceShift++;
            if (counter.framesSinceShift >= arrFrames) {
                move.run();
                counter.framesSinceShift = 0;
            }
        }
    }

    private void processSoftDrop(PlayerKeys keys, GameState state) {
        if (!keys.held.contains(LocalPlayerAction.MOVE_DOWN)) return;
        long now = System.nanoTime();
        if (keys.downJustPressed) {
            state.softDrop();
            keys.downJustPressed = false;
            keys.lastDownRepeatNs = now;
            return;
        }
        int sdf = settings.getSoftDropFactor();
        if (sdf == 0) {
            for (int i = 0; i < 40; i++) state.softDrop();
            keys.lastDownRepeatNs = now;
            return;
        }
        long gravityIntervalNs = state.getScoreSystem().getGravityInterval() * 1_000_000L;
        long softDropIntervalNs = Math.max(1L, gravityIntervalNs / Math.max(1, sdf));
        int drops = 0;
        while (now - keys.lastDownRepeatNs >= softDropIntervalNs && drops < 20) {
            state.softDrop();
            keys.lastDownRepeatNs += softDropIntervalNs;
            drops++;
        }
        if (drops >= 20) keys.lastDownRepeatNs = now;
    }

    private LocalPlayerAction actionForPlayer1(int code) {
        if (settings.isMoveLeft(code))      return LocalPlayerAction.MOVE_LEFT;
        if (settings.isMoveRight(code))     return LocalPlayerAction.MOVE_RIGHT;
        if (settings.isMoveDown(code))      return LocalPlayerAction.MOVE_DOWN;
        if (settings.isMoveUp(code))        return LocalPlayerAction.MOVE_UP;
        if (settings.isHardDrop(code))      return LocalPlayerAction.HARD_DROP;
        if (settings.isRotateCW(code))      return LocalPlayerAction.ROTATE_CW;
        if (settings.isRotateCCW(code))     return LocalPlayerAction.ROTATE_CCW;
        if (settings.isHold(code))          return LocalPlayerAction.HOLD;
        if (settings.isPause(code))         return LocalPlayerAction.PAUSE;
        if (settings.isExitStage(code))     return LocalPlayerAction.EXIT_STAGE;
        if (settings.isReset(code))         return LocalPlayerAction.RESET;
        return null;
    }

    private LocalPlayerAction actionForPlayer2(int code) {
        if (settings.isP2MoveLeft(code))      return LocalPlayerAction.MOVE_LEFT;
        if (settings.isP2MoveRight(code))     return LocalPlayerAction.MOVE_RIGHT;
        if (settings.isP2MoveDown(code))      return LocalPlayerAction.MOVE_DOWN;
        if (settings.isP2MoveUp(code))        return LocalPlayerAction.MOVE_UP;
        if (settings.isP2HardDrop(code))      return LocalPlayerAction.HARD_DROP;
        if (settings.isP2RotateCW(code))      return LocalPlayerAction.ROTATE_CW;
        if (settings.isP2RotateCCW(code))     return LocalPlayerAction.ROTATE_CCW;
        if (settings.isP2Hold(code))          return LocalPlayerAction.HOLD;
        if (settings.isP2ExitStage(code))     return LocalPlayerAction.EXIT_STAGE;
        if (settings.isP2Reset(code))         return LocalPlayerAction.RESET;
        return null;
    }

    private static final class PlayerKeys {
        final Set<LocalPlayerAction> held = EnumSet.noneOf(LocalPlayerAction.class);
        final Set<LocalPlayerAction> consumed = EnumSet.noneOf(LocalPlayerAction.class);
        final MovementCounter leftCounter = new MovementCounter();
        final MovementCounter rightCounter = new MovementCounter();
        boolean downJustPressed;
        long lastDownRepeatNs;

        void press(LocalPlayerAction action) {
            if (action == null) return;
            if (!held.add(action)) return;
            if (action == LocalPlayerAction.MOVE_LEFT) leftCounter.resetForPress();
            else if (action == LocalPlayerAction.MOVE_RIGHT) rightCounter.resetForPress();
            else if (action == LocalPlayerAction.MOVE_DOWN) {
                downJustPressed = true;
                lastDownRepeatNs = System.nanoTime();
            }
        }

        void release(LocalPlayerAction action) {
            if (action == null) return;
            held.remove(action);
            consumed.remove(action);
            if (action == LocalPlayerAction.MOVE_LEFT) leftCounter.clear();
            else if (action == LocalPlayerAction.MOVE_RIGHT) rightCounter.clear();
            else if (action == LocalPlayerAction.MOVE_DOWN) {
                downJustPressed = false;
                lastDownRepeatNs = 0L;
            }
        }

        boolean isNewPress(LocalPlayerAction action) {
            if (action == null) return false;
            if (held.contains(action) && !consumed.contains(action)) {
                consumed.add(action);
                return true;
            }
            return false;
        }

        void releaseAll() {
            held.clear();
            consumed.clear();
            leftCounter.clear();
            rightCounter.clear();
            downJustPressed = false;
            lastDownRepeatNs = 0L;
        }
    }

    private static final class MovementCounter {
        int framesHeld;
        int framesSinceShift;
        boolean dasCharged;
        boolean justPressed;

        void resetForPress() {
            framesHeld = 0;
            framesSinceShift = 0;
            dasCharged = false;
            justPressed = true;
        }

        void clear() {
            framesHeld = 0;
            framesSinceShift = 0;
            dasCharged = false;
            justPressed = false;
        }
    }
}
