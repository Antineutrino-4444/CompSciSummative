package com.tetris.mab.sim;

import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ParticipantState;
import com.tetris.mab.NukeBuildState;
import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.nuke.NukeDesignFactory;
import com.tetris.model.GameState;

/**
 * Verifies that a builder-derived warhead design becomes the live MAB
 * strategic loadout — design-specific charge requirement, design-specific
 * launch route, design-specific tempo. Replaces nothing; it is a NEW
 * probe added for the Nuke Builder integration work.
 */
public final class MabNukeBuilderLiveIntegrationProbe {

    private MabNukeBuilderLiveIntegrationProbe() {}

    public static void main(String[] args) {
        System.out.println("=== MAB Nuke Builder Live Integration Probe ===");
        int failed = 0;

        // Build matches under different designs and assert that the live
        // simplified state reflects the design's charge/route values.
        MutuallyAssuredBlocksMatch matchLight = newMatch();
        matchLight.applyWarheadDesign(ParticipantId.PLAYER_A,
                NukeDesignFactory.createDefaultTacticalBlast());
        ParticipantState pLight = matchLight.getParticipant(ParticipantId.PLAYER_A);
        boolean lightChargeFromDesign = pLight.getSimplifiedState().chargeRequired()
                == pLight.getNukeBuildState().getEffectiveBuildChargeRequired();
        boolean lightTetrisGoalFromDesign = pLight.getSimplifiedState().launchTetrisGoal() == 3;
        boolean lightSpinGoalFromDesign = pLight.getSimplifiedState().launchSpinGoal() == 2;

        MutuallyAssuredBlocksMatch matchHeavy = newMatch();
        matchHeavy.applyWarheadDesign(ParticipantId.PLAYER_A,
                NukeDesignFactory.createDefaultHeavyBlast());
        ParticipantState pHeavy = matchHeavy.getParticipant(ParticipantId.PLAYER_A);
        boolean heavyChargeFromDesign = pHeavy.getSimplifiedState().chargeRequired()
                == pHeavy.getNukeBuildState().getEffectiveBuildChargeRequired();
        boolean heavyTetrisGoalFromDesign = pHeavy.getSimplifiedState().launchTetrisGoal() == 5;
        boolean heavySpinGoalFromDesign = pHeavy.getSimplifiedState().launchSpinGoal() == 3;

        MutuallyAssuredBlocksMatch matchDoomsday = newMatch();
        matchDoomsday.applyWarheadDesign(ParticipantId.PLAYER_A,
                NukeDesignFactory.createDefaultDoomsday());
        ParticipantState pDoomsday = matchDoomsday.getParticipant(ParticipantId.PLAYER_A);
        boolean doomsdayChargeBig = pDoomsday.getSimplifiedState().chargeRequired() >= 100;
        boolean doomsdayTetrisGoal = pDoomsday.getSimplifiedState().launchTetrisGoal() >= 5;

        // Charge requirement must differ across designs (not hardcoded).
        boolean designsHaveDifferentCharge =
                pLight.getSimplifiedState().chargeRequired()
                        != pHeavy.getSimplifiedState().chargeRequired()
                && pHeavy.getSimplifiedState().chargeRequired()
                        != pDoomsday.getSimplifiedState().chargeRequired();

        // Different launch countdowns by design.
        NukeBuildState nbLight = pLight.getNukeBuildState();
        NukeBuildState nbHeavy = pHeavy.getNukeBuildState();
        NukeBuildState nbDoomsday = pDoomsday.getNukeBuildState();
        boolean differentCountdowns =
                nbLight.getEffectiveLaunchCountdownPieces() < nbHeavy.getEffectiveLaunchCountdownPieces()
                && nbHeavy.getEffectiveLaunchCountdownPieces() < nbDoomsday.getEffectiveLaunchCountdownPieces();

        // Different impact-profile ratings by design.
        NukeDesign tactical = NukeDesignFactory.createDefaultTacticalBlast();
        NukeDesign emp = NukeDesignFactory.createDefaultEmp();
        NukeDesign dirty = NukeDesignFactory.createDefaultDirtyPayload();
        NukeDesign concrete = NukeDesignFactory.createDefaultConcreteBlaster();
        boolean profilesAreDistinct =
                tactical.getRadiationRating() < dirty.getRadiationRating()
                && emp.getEmpRating() > dirty.getEmpRating()
                && concrete.getSiloDamageRating() > tactical.getSiloDamageRating()
                && concrete.getDisarmRating() > emp.getDisarmRating();

        report("lightChargeFromDesign", lightChargeFromDesign);
        report("lightTetrisGoalFromDesign", lightTetrisGoalFromDesign);
        report("lightSpinGoalFromDesign", lightSpinGoalFromDesign);
        report("heavyChargeFromDesign", heavyChargeFromDesign);
        report("heavyTetrisGoalFromDesign", heavyTetrisGoalFromDesign);
        report("heavySpinGoalFromDesign", heavySpinGoalFromDesign);
        report("doomsdayChargeBig", doomsdayChargeBig);
        report("doomsdayTetrisGoal", doomsdayTetrisGoal);
        report("designsHaveDifferentCharge", designsHaveDifferentCharge);
        report("differentCountdowns", differentCountdowns);
        report("profilesAreDistinct", profilesAreDistinct);

        boolean success = lightChargeFromDesign
                && lightTetrisGoalFromDesign
                && lightSpinGoalFromDesign
                && heavyChargeFromDesign
                && heavyTetrisGoalFromDesign
                && heavySpinGoalFromDesign
                && doomsdayChargeBig
                && doomsdayTetrisGoal
                && designsHaveDifferentCharge
                && differentCountdowns
                && profilesAreDistinct;
        report("success", success);
        if (!success) System.exit(1);
    }

    private static MutuallyAssuredBlocksMatch newMatch() {
        MutuallyAssuredBlocksMatch m = MutuallyAssuredBlocksMatch.createLocalPvpShared(
                new GameState(0), new GameState(0), MatchDifficulty.NORMAL, 1234L);
        m.startMatch();
        return m;
    }

    private static void report(String n, boolean v) {
        System.out.println(n + "=" + v);
    }
}
