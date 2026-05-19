package com.tetris.mab.sim;

import com.tetris.controller.GameController;
import com.tetris.controller.GameLaunchMode;
import com.tetris.mab.ui.MabBattleShellPanel;
import com.tetris.mab.ui.MabCompactHudPanel;
import com.tetris.mab.ui.MabOpponentBoardPanel;
import com.tetris.mab.ui.MabOpsDeckPanel;
import com.tetris.model.GameState;
import com.tetris.view.GamePanel;
import com.tetris.view.GameView;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;

/**
 * Verifies the ordinary Tetris embedded path remains independent from
 * MAB: it mounts the regular GameView, contains no MAB battle chrome,
 * restarts the single board in place, keeps the MAB gravity multiplier
 * neutral, and routes Back/Main Menu through the normal exit callback.
 */
public final class MabNormalTetrisModeProbe {

    private MabNormalTetrisModeProbe() {}

    public static void main(String[] args) throws Exception {
        System.out.println("=== Normal Tetris Mode Probe ===");

        GameController controller = new GameController(1, GameLaunchMode.NORMAL_TETRIS);
        int[] exits = {0};
        JComponent[] viewRef = new JComponent[1];
        boolean ok = true;

        try {
            SwingUtilities.invokeAndWait(() ->
                    viewRef[0] = controller.startEmbedded(() -> exits[0]++));

            JComponent view = viewRef[0];
            ok &= report("launchModeNormal",
                    controller.getLaunchMode() == GameLaunchMode.NORMAL_TETRIS);
            ok &= report("embeddedViewIsGameView", view instanceof GameView);
            ok &= report("noMabBattleShell", !containsType(view, MabBattleShellPanel.class));
            ok &= report("noMabOpsDeck", !containsType(view, MabOpsDeckPanel.class));
            ok &= report("noMabOpponentBoard", !containsType(view, MabOpponentBoardPanel.class));
            ok &= report("noLegacyMabHud", !containsType(view, MabCompactHudPanel.class));

            GameState firstState = controllerState(controller);
            GamePanel gamePanel = ((GameView) view).getGamePanel();
            ok &= report("panelUsesInitialState", panelState(gamePanel) == firstState);
            ok &= report("normalGravityMultiplierNeutralBeforeRestart",
                    nearOne(firstState.getMabGravityMultiplier()));

            SwingUtilities.invokeAndWait(controller::restart);
            GameState restartedState = controllerState(controller);
            ok &= report("restartCreatesFreshState", restartedState != firstState);
            ok &= report("panelUsesRestartedState", panelState(gamePanel) == restartedState);
            ok &= report("normalGravityMultiplierNeutralAfterRestart",
                    nearOne(restartedState.getMabGravityMultiplier()));

            SwingUtilities.invokeAndWait(controller::exitStage);
            SwingUtilities.invokeAndWait(() -> {});
            ok &= report("normalExitCallbackOnce", exits[0] == 1);
        } finally {
            controller.stop();
        }

        report("success", ok);
        if (!ok) System.exit(1);
    }

    private static boolean containsType(Component root, Class<?> type) {
        if (root == null) return false;
        if (type.isInstance(root)) return true;
        if (root instanceof Container c) {
            for (Component child : c.getComponents()) {
                if (containsType(child, type)) return true;
            }
        }
        return false;
    }

    private static GameState controllerState(GameController controller)
            throws ReflectiveOperationException {
        Field field = GameController.class.getDeclaredField("gameState");
        field.setAccessible(true);
        return (GameState) field.get(controller);
    }

    private static GameState panelState(GamePanel panel)
            throws ReflectiveOperationException {
        Field field = GamePanel.class.getDeclaredField("gameState");
        field.setAccessible(true);
        return (GameState) field.get(panel);
    }

    private static boolean nearOne(double value) {
        return Math.abs(value - 1.0) < 0.0001;
    }

    private static boolean report(String name, boolean value) {
        System.out.println(name + "=" + value);
        return value;
    }
}
