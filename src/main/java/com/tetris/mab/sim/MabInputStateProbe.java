package com.tetris.mab.sim;

import com.tetris.controller.GameController;
import com.tetris.controller.GameLaunchMode;
import com.tetris.controller.InputHandler;
import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ui.MabBattleShellPanel;
import com.tetris.mab.ui.MabOpponentBoardPanel;
import com.tetris.model.GameState;
import com.tetris.model.Settings;
import com.tetris.view.GamePanel;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import javax.swing.SwingUtilities;

/**
 * Step 23 Control Refinement \u2014 verifies the held-key state model
 * used by the MAB battle shell input adapter. Tests {@link InputHandler}
 * directly because the adapter is a thin Swing wrapper around it.
 *
 * <p>Headless-friendly: builds an {@link InputHandler} attached to a
 * lightweight {@link GameController} and synthesises {@link KeyEvent}s
 * via a stub {@link Component}. Reads the package-private
 * {@code pressedKeys} set via reflection because the public surface
 * doesn't otherwise expose its emptiness in one call.
 */
public final class MabInputStateProbe {

    public static void main(String[] args) {
        boolean ok = run();
        System.out.println("success=" + ok);
        if (!ok) System.exit(1);
        System.exit(0);
    }

    private static boolean run() {
        System.out.println("=== MAB Input State Probe ===");
        boolean ok = true;
        InputHandler in = new InputHandler(new GameController(1, GameLaunchMode.NORMAL_TETRIS));
        Settings s = Settings.get();
        int LEFT  = s.getKeyMoveLeft();
        int RIGHT = s.getKeyMoveRight();
        int DOWN  = s.getKeyMoveDown();
        int DROP  = s.getKeyHardDrop();
        int HOLD  = s.getKeyHold();

        // 1. Press / release LEFT
        press(in, LEFT);
        boolean leftHeld1 = in.isKeyHeld(LEFT);
        release(in, LEFT);
        boolean leftHeld2 = in.isKeyHeld(LEFT);
        ok &= report("leftPressRelease", leftHeld1 && !leftHeld2);

        // 2. focus-lost / releaseAll clears LEFT
        press(in, LEFT);
        in.releaseAll();
        ok &= report("focusLostClears", !in.isKeyHeld(LEFT) && pressedKeysSize(in) == 0);

        // 3. press RIGHT works
        press(in, RIGHT);
        boolean rightHeld = in.isKeyHeld(RIGHT);
        in.releaseAll();
        ok &= report("rightPressRelease", rightHeld && !in.isKeyHeld(RIGHT));

        // 4. releaseAll clears every key + counters
        press(in, LEFT); press(in, RIGHT); press(in, DOWN);
        in.releaseAll();
        ok &= report("releaseAllClears",
                !in.isKeyHeld(LEFT) && !in.isKeyHeld(RIGHT) && !in.isKeyHeld(DOWN)
                && pressedKeysSize(in) == 0);

        // 5. hardDrop is edge-triggered (hasUnconsumedKey true once, then consumed)
        press(in, DROP);
        boolean dropFirst = in.hasUnconsumedKey(DROP);
        in.consumeKey(DROP);
        boolean dropSecond = in.hasUnconsumedKey(DROP);
        release(in, DROP);
        ok &= report("hardDropEdgeTriggered", dropFirst && !dropSecond);

        // 6. hold edge-triggered
        press(in, HOLD);
        boolean holdFirst = in.hasUnconsumedKey(HOLD);
        in.consumeKey(HOLD);
        boolean holdSecond = in.hasUnconsumedKey(HOLD);
        release(in, HOLD);
        ok &= report("holdEdgeTriggered", holdFirst && !holdSecond);

        // 7. opposite directions: press LEFT, then press RIGHT, then
        //    release both \u2014 nothing must remain held.
        press(in, LEFT);
        press(in, RIGHT);
        release(in, LEFT);
        release(in, RIGHT);
        ok &= report("oppositeDirectionSafe",
                !in.isKeyHeld(LEFT) && !in.isKeyHeld(RIGHT));

        // 8. Held LEFT then releaseAll (simulating shutdown / focus loss)
        press(in, LEFT);
        in.releaseAll();
        ok &= report("shutdownClears",
                !in.isKeyHeld(LEFT) && pressedKeysSize(in) == 0);

        ok &= report("qDoesNotConflictWithDefaultControls", qDoesNotConflict(s));
        ok &= report("overlayOpenClearsHeldKeys", overlayOpenClearsHeldKeys(LEFT));

        return ok;
    }

    private static boolean report(String name, boolean v) {
        System.out.println(name + "=" + v);
        return v;
    }

    private static void press(InputHandler in, int code) {
        in.keyPressed(synthesize(KeyEvent.KEY_PRESSED, code));
    }

    private static void release(InputHandler in, int code) {
        in.keyReleased(synthesize(KeyEvent.KEY_RELEASED, code));
    }

    private static KeyEvent synthesize(int id, int code) {
        Component src = new Component() {};
        return new KeyEvent(src, id, System.currentTimeMillis(), 0, code,
                KeyEvent.CHAR_UNDEFINED);
    }

    private static int pressedKeysSize(InputHandler in) {
        try {
            Field f = InputHandler.class.getDeclaredField("pressedKeys");
            f.setAccessible(true);
            return ((java.util.Set<?>) f.get(in)).size();
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }

    private static boolean qDoesNotConflict(Settings s) {
        int q = KeyEvent.VK_Q;
        return q != s.getKeyMoveLeft()
                && q != s.getKeyMoveRight()
                && q != s.getKeyMoveDown()
                && q != s.getKeyHardDrop()
                && q != s.getKeyRotateCW()
                && q != s.getKeyRotateCCW()
                && q != s.getKeyHold()
                && q != s.getKeyHoldAlt()
                && q != s.getKeyPause()
                && q != s.getKeyPauseAlt()
                && q != s.getKeyExitStage()
                && q != s.getKeySettings();
    }

    private static boolean overlayOpenClearsHeldKeys(int leftKey) {
        final boolean[] out = {false};
        try {
            SwingUtilities.invokeAndWait(() -> {
                GameController controller = new GameController(1, GameLaunchMode.NORMAL_TETRIS);
                InputHandler handler = new InputHandler(controller);
                GameState a = new GameState(0);
                GameState b = new GameState(0);
                MutuallyAssuredBlocksMatch match = MutuallyAssuredBlocksMatch.createPveShared(
                        a, b, MatchDifficulty.NORMAL, 8080L);
                match.startMatch();
                MabBattleShellPanel shell = new MabBattleShellPanel(
                        new GamePanel(a), a, new MabOpponentBoardPanel(b),
                        ParticipantId.PLAYER_A, ParticipantId.PLAYER_B,
                        "P1", "AI",
                        MabBattleShellPanel.defaultModeLine(null),
                        MabBattleShellPanel.defaultModeSub(),
                        () -> {});
                shell.setInputHandler(handler);
                shell.refreshAll(match);
                press(handler, leftKey);
                shell.showDoctrineStatusOverlay();
                out[0] = !handler.isKeyHeld(leftKey) && pressedKeysSize(handler) == 0;
                shell.hideDoctrineStatusOverlay();
            });
        } catch (Exception e) {
            return false;
        }
        return out[0];
    }
}
