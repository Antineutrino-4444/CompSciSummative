package com.tetris.mab.sim;

import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ParticipantState;
import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.nuke.NukeDesignFactory;
import com.tetris.model.GameState;

/**
 * Verifies the DEFCON tempo engine:
 *
 * <ul>
 *   <li>DEFCON starts at 5.</li>
 *   <li>Escalation drives DEFCON down toward 1.</li>
 *   <li>Raw damage ratings (blast / radiation / EMP / disarm / silo)
 *       do NOT change with DEFCON.</li>
 *   <li>Effective charge requirement and launch countdown DO scale
 *       with DEFCON.</li>
 *   <li>Both participants refresh on a DEFCON change.</li>
 *   <li>No radar / warning / intel labels are surfaced.</li>
 * </ul>
 */
public final class MabDefconTempoProbe {

    private MabDefconTempoProbe() {}

    public static void main(String[] args) {
        System.out.println("=== MAB DEFCON Tempo Probe ===");

        MutuallyAssuredBlocksMatch m = MutuallyAssuredBlocksMatch.createLocalPvpShared(
                new GameState(0), new GameState(0), MatchDifficulty.NORMAL, 99L);
        m.startMatch();
        m.applyWarheadDesign(ParticipantId.PLAYER_A,
                NukeDesignFactory.createDefaultHeavyBlast());
        m.applyWarheadDesign(ParticipantId.PLAYER_B,
                NukeDesignFactory.createDefaultHeavyBlast());
        ParticipantState pA = m.getParticipant(ParticipantId.PLAYER_A);
        ParticipantState pB = m.getParticipant(ParticipantId.PLAYER_B);

        boolean startsAtDefcon5 = m.getDefconState().getLevel() == 5;
        int chargeReqAt5 = pA.getNukeBuildState().getEffectiveBuildChargeRequired();
        int launchAt5 = pA.getNukeBuildState().getEffectiveLaunchCountdownPieces();
        NukeDesign design = pA.getNukeBuildState().getCurrentDesign();
        int blastAt5 = design.getBlastRating();
        int radAt5 = design.getRadiationRating();
        int empAt5 = design.getEmpRating();
        int disarmAt5 = design.getDisarmRating();
        int siloAt5 = design.getSiloDamageRating();

        // Apply enough escalation to drop DEFCON. Use the public
        // refresh hook so charge / route values recompute for both
        // participants.
        m.addEscalationAndRefresh(900, "probe");
        boolean defconDropped = m.getDefconState().getLevel() < 5;
        int chargeReqAfter = pA.getNukeBuildState().getEffectiveBuildChargeRequired();
        int launchAfter = pA.getNukeBuildState().getEffectiveLaunchCountdownPieces();
        int blastAfter = design.getBlastRating();
        int radAfter = design.getRadiationRating();
        int empAfter = design.getEmpRating();
        int disarmAfter = design.getDisarmRating();
        int siloAfter = design.getSiloDamageRating();

        boolean escalationDropsDefcon = defconDropped;
        boolean damageRatingsInvariant = blastAt5 == blastAfter
                && radAt5 == radAfter
                && empAt5 == empAfter
                && disarmAt5 == disarmAfter
                && siloAt5 == siloAfter;
        boolean chargeRequirementScales = chargeReqAfter != chargeReqAt5;
        boolean launchCountdownScales = launchAfter != launchAt5;
        boolean bothPlayersRefresh =
                pA.getNukeBuildState().getEffectiveBuildChargeRequired()
                        == pA.getNukeBuildState().getCurrentDesign()
                                .effectiveBuildChargeRequired(m.getDefconState().getLevel())
                && pB.getNukeBuildState().getEffectiveBuildChargeRequired()
                        == pB.getNukeBuildState().getCurrentDesign()
                                .effectiveBuildChargeRequired(m.getDefconState().getLevel());
        boolean noRadarWarningIntel = noRadarText(design);

        // Refresh simplified state from new DEFCON-scaled requirement.
        boolean simplifiedScales = pA.getSimplifiedState().chargeRequired()
                == pA.getNukeBuildState().getEffectiveBuildChargeRequired();

        report("startsAtDefcon5", startsAtDefcon5);
        report("escalationDropsDefcon", escalationDropsDefcon);
        report("damageRatingsInvariant", damageRatingsInvariant);
        report("chargeRequirementScales", chargeRequirementScales);
        report("launchCountdownScales", launchCountdownScales);
        report("bothPlayersRefresh", bothPlayersRefresh);
        report("simplifiedStateScales", simplifiedScales);
        report("noRadarWarningIntel", noRadarWarningIntel);

        boolean success = startsAtDefcon5 && escalationDropsDefcon
                && damageRatingsInvariant && chargeRequirementScales
                && launchCountdownScales && bothPlayersRefresh
                && simplifiedScales && noRadarWarningIntel;
        report("success", success);
        if (!success) System.exit(1);
    }

    private static boolean noRadarText(NukeDesign d) {
        if (d == null) return true;
        String s = (d.getDisplayName() + " " + d.getDoctrineType().displayLabel()).toLowerCase();
        return !s.contains("radar") && !s.contains("warning")
                && !s.contains("intel") && !s.contains("decoy")
                && !s.contains("mirv");
    }

    private static void report(String n, boolean v) {
        System.out.println(n + "=" + v);
    }
}
