package com.tetris.mab.sim;

import com.tetris.controller.GameController;
import com.tetris.controller.GameLaunchMode;
import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.model.GameState;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Headless smoke probe for the developer console DEFCON priming command. */
public final class DevConsoleDefconMaxProbe {
    private DevConsoleDefconMaxProbe() {}

    public static void main(String[] args) throws Exception {
        GameController controller = new GameController(1, GameLaunchMode.NORMAL_TETRIS);
        MutuallyAssuredBlocksMatch match = MutuallyAssuredBlocksMatch.createLocalPvp(
                new GameState(1), new GameState(1), MatchDifficulty.NORMAL);
        match.startMatch();

        Field mabMatch = GameController.class.getDeclaredField("mabMatch");
        mabMatch.setAccessible(true);
        mabMatch.set(controller, match);

        Method handle = GameController.class.getDeclaredMethod(
                "handleConsoleCommand", String.class);
        handle.setAccessible(true);

        int beforeLevel = match.getDefconState().getLevel();
        String out = String.valueOf(handle.invoke(controller, "defcon max"));
        int primedMeter = match.getDefconState().getEscalationMeter();
        int nextThreshold = match.getDefconState().getNextThreshold();

        var change = match.addEscalationAndRefresh(1, "probe-single-line-clear");
        int afterLevel = match.getDefconState().getLevel();

        boolean ok = out.toLowerCase().contains("primed")
                && beforeLevel == 5
                && primedMeter == 99
                && nextThreshold == 100
                && change.changed()
                && afterLevel == 4;

        System.out.println("out=" + out);
        System.out.println("beforeLevel=" + beforeLevel);
        System.out.println("primedMeter=" + primedMeter);
        System.out.println("nextThreshold=" + nextThreshold);
        System.out.println("afterSingleLineLevel=" + afterLevel);
        System.out.println("success=" + ok);
        match.shutdown();
        if (!ok) System.exit(1);
    }
}
