package com.tetris.mab.sim;

import com.tetris.controller.GameController;
import com.tetris.controller.GameLaunchMode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Headless smoke probe for the developer console AI pause command. */
public final class DevConsoleAiPauseProbe {
    private DevConsoleAiPauseProbe() {}

    public static void main(String[] args) throws Exception {
        GameController controller = new GameController(1, GameLaunchMode.NORMAL_TETRIS);

        Method handle = GameController.class.getDeclaredMethod(
                "handleConsoleCommand", String.class);
        handle.setAccessible(true);
        Field paused = GameController.class.getDeclaredField("aiPausedByConsole");
        paused.setAccessible(true);

        String pauseOut = String.valueOf(handle.invoke(controller, "ai pause"));
        boolean pausedAfterPause = paused.getBoolean(controller);

        String statusOut = String.valueOf(handle.invoke(controller, "ai status"));

        String resumeOut = String.valueOf(handle.invoke(controller, "ai resume"));
        boolean pausedAfterResume = paused.getBoolean(controller);

        boolean ok = pauseOut.toLowerCase().contains("paused")
                && pausedAfterPause
                && statusOut.toLowerCase().contains("paused")
                && resumeOut.toLowerCase().contains("resumed")
                && !pausedAfterResume;

        System.out.println("pauseOut=" + pauseOut);
        System.out.println("pausedAfterPause=" + pausedAfterPause);
        System.out.println("statusOut=" + statusOut);
        System.out.println("resumeOut=" + resumeOut);
        System.out.println("pausedAfterResume=" + pausedAfterResume);
        System.out.println("success=" + ok);
        if (!ok) System.exit(1);
    }
}
