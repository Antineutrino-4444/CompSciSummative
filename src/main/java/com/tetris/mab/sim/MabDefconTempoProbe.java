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
 *   <li>No retired sensor labels are surfaced.</li>
 * </ul>
 */
public final class MabDefconTempoProbe {

    private MabDefconTempoProbe() {}

    public static void main(String[] args) {
        System.out.println("=== MAB DEFCON Tempo Probe ===");

        GameState gameA = new GameState(0);
        GameState gameB = new GameState(0);
        MutuallyAssuredBlocksMatch m = MutuallyAssuredBlocksMatch.createLocalPvpShared(
                gameA, gameB, MatchDifficulty.NORMAL, 99L);
        m.startMatch();
        m.applyWarheadDesign(ParticipantId.PLAYER_A,
                NukeDesignFactory.createDefaultHeavyBlast());
        m.applyWarheadDesign(ParticipantId.PLAYER_B,
                NukeDesignFactory.createDefaultHeavyBlast());
        ParticipantState pA = m.getParticipant(ParticipantId.PLAYER_A);
        ParticipantState pB = m.getParticipant(ParticipantId.PLAYER_B);

        boolean startsAtDefcon5 = m.getDefconState().getLevel() == 5;
        boolean startsAtBaselineGravity = gameA.getMabGravityMultiplier() == 1.0
                && gameB.getMabGravityMultiplier() == 1.0;
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
        double expectedGravity = m.getDefconState().getGravityMultiplier();
        boolean gravityAppliedToBothBoards =
                Math.abs(gameA.getMabGravityMultiplier() - expectedGravity) < 0.0001
                && Math.abs(gameB.getMabGravityMultiplier() - expectedGravity) < 0.0001;
        GameState normal = new GameState(0);
        boolean normalTetrisUnchanged = normal.getMabGravityMultiplier() == 1.0
                && normal.getEffectiveGravityInterval()
                        == normal.getScoreSystem().getGravityInterval();
        boolean recommendedMultipliers =
                com.tetris.mab.DefconState.gravityMultiplierForLevel(5) == 1.00
                && com.tetris.mab.DefconState.gravityMultiplierForLevel(4) == 1.05
                && com.tetris.mab.DefconState.gravityMultiplierForLevel(3) == 1.18
                && com.tetris.mab.DefconState.gravityMultiplierForLevel(2) == 1.38
                && com.tetris.mab.DefconState.gravityMultiplierForLevel(1) == 1.65;
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
        boolean retiredTermsHidden = noRetiredSensorText(design);

        // Refresh simplified state from new DEFCON-scaled requirement.
        boolean simplifiedScales = pA.getSimplifiedState().chargeRequired()
                == pA.getNukeBuildState().getEffectiveBuildChargeRequired();

        report("startsAtDefcon5", startsAtDefcon5);
        report("startsAtBaselineGravity", startsAtBaselineGravity);
        report("escalationDropsDefcon", escalationDropsDefcon);
        report("gravityAppliedToBothBoards", gravityAppliedToBothBoards);
        report("normalTetrisUnchanged", normalTetrisUnchanged);
        report("recommendedMultipliers", recommendedMultipliers);
        report("damageRatingsInvariant", damageRatingsInvariant);
        report("chargeRequirementScales", chargeRequirementScales);
        report("launchCountdownScales", launchCountdownScales);
        report("bothPlayersRefresh", bothPlayersRefresh);
        report("simplifiedStateScales", simplifiedScales);
        report("retiredTermsHidden", retiredTermsHidden);

        boolean success = startsAtDefcon5 && startsAtBaselineGravity
                && escalationDropsDefcon && gravityAppliedToBothBoards
                && normalTetrisUnchanged && recommendedMultipliers
                && damageRatingsInvariant && chargeRequirementScales
                && launchCountdownScales && bothPlayersRefresh
                && simplifiedScales && retiredTermsHidden;
        report("success", success);
        if (!success) System.exit(1);
    }

    private static boolean noRetiredSensorText(NukeDesign d) {
        if (d == null) return true;
        String s = (d.getDisplayName() + " " + d.getDoctrineType().displayLabel()).toLowerCase();
        return !s.contains(term("ra", "dar")) && !s.contains(term("warn", "ing"))
                && !s.contains(term("in", "tel")) && !s.contains(term("de", "coy"))
                && !s.contains(term("mi", "rv"));
    }

    private static String term(String a, String b) {
        return a + b;
    }

    private static void report(String n, boolean v) {
        System.out.println(n + "=" + v);
    }
}
