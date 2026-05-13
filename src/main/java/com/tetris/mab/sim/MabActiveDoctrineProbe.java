package com.tetris.mab.sim;

import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.upgrade.draft.MabActiveDoctrineType;
import com.tetris.mab.upgrade.draft.MabUpgradeDraftRegistry;
import com.tetris.model.GameState;
import com.tetris.model.TetrominoType;

import java.util.List;

/**
 * Step 25 - verifies active doctrine runtime behavior against live match state.
 */
public final class MabActiveDoctrineProbe {

    public static void main(String[] args) {
        System.out.println("=== MAB Active Doctrine Probe ===");
        boolean ok = true;

        MabUpgradeDraftRegistry reg = new MabUpgradeDraftRegistry();

        MutuallyAssuredBlocksMatch manualMatch = match();
        var p = manualMatch.getParticipant(ParticipantId.PLAYER_A);
        boolean manualUnavailableWithoutCard = !manualMatch
                .getActiveDoctrineAvailability(ParticipantId.PLAYER_A,
                        MabActiveDoctrineType.MANUAL_OVERRIDE)
                .available();
        p.getUpgradeInventory().addUpgrade(reg.getById("manual_override"));
        boolean manualUnavailableNotReady = !manualMatch
                .getActiveDoctrineAvailability(ParticipantId.PLAYER_A,
                        MabActiveDoctrineType.MANUAL_OVERRIDE)
                .available();
        TetrominoType manualPieceBefore = p.getGameState().getCurrentPiece().getType();
        List<TetrominoType> manualPreviewBefore = p.getGameState().getPreviewPieces();
        manualMatch.debugArmCurrentNuke(ParticipantId.PLAYER_A);
        boolean manualAvailableWhenReady = manualMatch
                .getActiveDoctrineAvailability(ParticipantId.PLAYER_A,
                        MabActiveDoctrineType.MANUAL_OVERRIDE)
                .available();
        var manualResult = manualMatch.useActiveDoctrine(
                ParticipantId.PLAYER_A, MabActiveDoctrineType.MANUAL_OVERRIDE);
        boolean manualFiresLaunch = manualResult.success()
                && manualMatch.countActiveOutgoingLaunches(ParticipantId.PLAYER_A) == 1;
        boolean manualConsumesCycle = !p.getSimplifiedState().nukeReady()
                && p.getNukeBuildState().getCurrentBuildCharge() == 0;
        boolean manualNoDoubleUse = !manualMatch
                .getActiveDoctrineAvailability(ParticipantId.PLAYER_A,
                        MabActiveDoctrineType.MANUAL_OVERRIDE)
                .available();
        boolean manualSequenceUnchanged = manualPieceBefore == p.getGameState().getCurrentPiece().getType()
                && manualPreviewBefore.equals(p.getGameState().getPreviewPieces());

        MutuallyAssuredBlocksMatch empNoCard = match();
        boolean empUnavailableWithoutCard = !empNoCard
                .getActiveDoctrineAvailability(ParticipantId.PLAYER_A, MabActiveDoctrineType.EMP)
                .available();

        MutuallyAssuredBlocksMatch empMatch = match();
        var empP = empMatch.getParticipant(ParticipantId.PLAYER_A);
        empP.getUpgradeInventory().addUpgrade(reg.getById("emp"));
        boolean empUnavailableNoCharge = !empMatch
                .getActiveDoctrineAvailability(ParticipantId.PLAYER_A, MabActiveDoctrineType.EMP)
                .available();
        empMatch.debugAddNukeCharge(ParticipantId.PLAYER_A, 40);
        boolean empUnavailableNoTarget = !empMatch
                .getActiveDoctrineAvailability(ParticipantId.PLAYER_A, MabActiveDoctrineType.EMP)
                .available();
        TetrominoType empPieceBefore = empP.getGameState().getCurrentPiece().getType();
        List<TetrominoType> empPreviewBefore = empP.getGameState().getPreviewPieces();
        empMatch.debugInjectIncomingThreatForTesting(ParticipantId.PLAYER_A);
        boolean empAvailableWithTarget = empMatch
                .getActiveDoctrineAvailability(ParticipantId.PLAYER_A, MabActiveDoctrineType.EMP)
                .available();
        int chargeBefore = empP.getNukeBuildState().getCurrentBuildCharge();
        var empResult = empMatch.useActiveDoctrine(ParticipantId.PLAYER_A, MabActiveDoctrineType.EMP);
        boolean empSpendsCharge = chargeBefore - empP.getNukeBuildState().getCurrentBuildCharge() == 40;
        boolean empCancelsOrWeakens = empResult.success()
                && empMatch.countLiveIncomingThreats(ParticipantId.PLAYER_A) == 0
                && empMatch.countActiveOutgoingLaunches(ParticipantId.PLAYER_B) == 0;
        boolean empSequenceUnchanged = empPieceBefore == empP.getGameState().getCurrentPiece().getType()
                && empPreviewBefore.equals(empP.getGameState().getPreviewPieces());

        boolean sharedSequenceUnaffected = manualSequenceUnchanged && empSequenceUnchanged;

        ok &= report("manualUnavailableWithoutCard", manualUnavailableWithoutCard);
        ok &= report("manualUnavailableNotReady", manualUnavailableNotReady);
        ok &= report("manualAvailableWhenReady", manualAvailableWhenReady);
        ok &= report("manualFiresLaunch", manualFiresLaunch);
        ok &= report("manualConsumesCycle", manualConsumesCycle);
        ok &= report("manualNoDoubleUse", manualNoDoubleUse);
        ok &= report("empUnavailableWithoutCard", empUnavailableWithoutCard);
        ok &= report("empUnavailableNoCharge", empUnavailableNoCharge);
        ok &= report("empUnavailableNoTarget", empUnavailableNoTarget);
        ok &= report("empAvailableWithTarget", empAvailableWithTarget);
        ok &= report("empSpendsCharge", empSpendsCharge);
        ok &= report("empCancelsOrWeakens", empCancelsOrWeakens);
        ok &= report("sharedSequenceUnaffected", sharedSequenceUnaffected);
        System.out.println("success=" + ok);
        if (!ok) System.exit(1);
    }

    private static MutuallyAssuredBlocksMatch match() {
        MutuallyAssuredBlocksMatch m = MutuallyAssuredBlocksMatch.createPveShared(
                new GameState(0), new GameState(0), MatchDifficulty.NORMAL, 2525L);
        m.startMatch();
        return m;
    }

    private static boolean report(String name, boolean value) {
        System.out.println(name + "=" + value);
        return value;
    }

    private MabActiveDoctrineProbe() {}
}
