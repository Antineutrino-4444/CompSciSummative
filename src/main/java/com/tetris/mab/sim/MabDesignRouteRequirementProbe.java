package com.tetris.mab.sim;

import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.nuke.NukeDesignFactory;
import com.tetris.model.GameState;

/**
 * Verifies that launch-route requirements (Tetris pips / spin pips) are
 * design-aware:
 *
 * <ul>
 *   <li>Light tactical: 3 Tetris / 2 spin.</li>
 *   <li>Default (training, dirty, EMP): 4 Tetris / 2 spin.</li>
 *   <li>Heavy / clean / bunker / concrete / salted: 5 Tetris / 3 spin.</li>
 *   <li>Doomsday: 6 Tetris / 4 spin.</li>
 * </ul>
 */
public final class MabDesignRouteRequirementProbe {

    private MabDesignRouteRequirementProbe() {}

    public static void main(String[] args) {
        System.out.println("=== MAB Design Route Requirement Probe ===");

        NukeDesign light = NukeDesignFactory.createDefaultTacticalBlast();
        NukeDesign heavy = NukeDesignFactory.createDefaultHeavyBlast();
        NukeDesign emp = NukeDesignFactory.createDefaultEmp();
        NukeDesign doomsday = NukeDesignFactory.createDefaultDoomsday();

        boolean lightRouteFast = light.effectiveLaunchTetrisGoal(3) == 3
                && light.effectiveLaunchSpinGoal(3) == 2;
        boolean heavyRouteSlower = heavy.effectiveLaunchTetrisGoal(3) == 5
                && heavy.effectiveLaunchSpinGoal(3) == 3;
        boolean empRouteMedium = emp.effectiveLaunchTetrisGoal(3) == 4
                && emp.effectiveLaunchSpinGoal(3) == 2;
        boolean doomsdayRouteHard = doomsday.effectiveLaunchTetrisGoal(3) == 6
                && doomsday.effectiveLaunchSpinGoal(3) == 4;

        // UI visibility: simplified state must surface design-specific goals.
        MutuallyAssuredBlocksMatch m = MutuallyAssuredBlocksMatch.createLocalPvpShared(
                new GameState(0), new GameState(0), MatchDifficulty.NORMAL, 1L);
        m.startMatch();
        m.applyWarheadDesign(ParticipantId.PLAYER_A, heavy);
        boolean uiRouteRequirementVisible =
                m.getParticipant(ParticipantId.PLAYER_A).getSimplifiedState().launchTetrisGoal() == 5
                && m.getParticipant(ParticipantId.PLAYER_A).getSimplifiedState().launchSpinGoal() == 3;

        // Upgrades may still re-target route goals (Fast Fuse), so we
        // verify the simplified state accepts a manual override too.
        m.getParticipant(ParticipantId.PLAYER_A).getSimplifiedState().setLaunchTetrisGoal(2);
        boolean upgradesStillApply =
                m.getParticipant(ParticipantId.PLAYER_A).getSimplifiedState().launchTetrisGoal() == 2;

        report("lightRouteFast", lightRouteFast);
        report("heavyRouteSlower", heavyRouteSlower);
        report("empRouteMedium", empRouteMedium);
        report("doomsdayRouteHard", doomsdayRouteHard);
        report("uiRouteRequirementVisible", uiRouteRequirementVisible);
        report("upgradesStillApply", upgradesStillApply);

        boolean success = lightRouteFast && heavyRouteSlower && empRouteMedium
                && doomsdayRouteHard && uiRouteRequirementVisible && upgradesStillApply;
        report("success", success);
        if (!success) System.exit(1);
    }

    private static void report(String n, boolean v) {
        System.out.println(n + "=" + v);
    }
}
