package com.tetris.mab.sim;

import com.tetris.controller.LocalInputRouter;
import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MatchMode;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.model.GameState;
import com.tetris.model.Settings;

/** Step 26 - verifies local/offline same-keyboard MAB PvP basics. */
public final class MabLocalPvpProbe {

    private MabLocalPvpProbe() {}

    public static void main(String[] args) {
        System.out.println("=== MAB Local PvP Probe ===");

        GameState a = new GameState(1);
        GameState b = new GameState(1);
        MutuallyAssuredBlocksMatch match =
                MutuallyAssuredBlocksMatch.createLocalPvpShared(
                        a, b, MatchDifficulty.NORMAL, 26026L);
        match.startMatch();

        boolean matchCreated = match != null;
        boolean modePvpLocal = match.getMatchMode() == MatchMode.PVP_LOCAL;
        boolean twoHumanBoards = match.getParticipant(ParticipantId.PLAYER_A).getGameState() == a
                && match.getParticipant(ParticipantId.PLAYER_B).getGameState() == b;
        boolean sharedSequence = match.hasSharedPieceSequence()
                && a.getCurrentPiece() != null
                && b.getCurrentPiece() != null
                && a.getCurrentPiece().getType() == b.getCurrentPiece().getType();

        int ax0 = a.getCurrentPiece().getBoardPosition().getX();
        int bx0 = b.getCurrentPiece().getBoardPosition().getX();
        Settings s = Settings.get();
        LocalInputRouter router = new LocalInputRouter(a, b, s, () -> {}, () -> {});
        router.keyPressed(s.getKeyMoveLeft());
        router.processInput();
        router.keyReleased(s.getKeyMoveLeft());
        int ax1 = a.getCurrentPiece().getBoardPosition().getX();
        int bx1 = b.getCurrentPiece().getBoardPosition().getX();
        boolean p1InputIsolated = ax1 < ax0 && bx1 == bx0;

        router.keyPressed(s.getKeyP2MoveRight());
        router.processInput();
        router.keyReleased(s.getKeyP2MoveRight());
        int ax2 = a.getCurrentPiece().getBoardPosition().getX();
        int bx2 = b.getCurrentPiece().getBoardPosition().getX();
        boolean p2InputIsolated = ax2 == ax1 && bx2 > bx1;
        router.releaseAll();

        boolean noAiDriver = true;
        a.hardDrop();
        b.hardDrop();
        match.debugAddNukeCharge(ParticipantId.PLAYER_A, 10);
        match.debugAddNukeCharge(ParticipantId.PLAYER_B, 10);
        boolean bothCanCharge = match.getParticipant(ParticipantId.PLAYER_A)
                .getNukeBuildState().getCurrentBuildCharge() > 0
                && match.getParticipant(ParticipantId.PLAYER_B)
                .getNukeBuildState().getCurrentBuildCharge() > 0;
        boolean bothCanLaunch = match.debugArmCurrentNuke(ParticipantId.PLAYER_A)
                && match.debugStartLaunch(ParticipantId.PLAYER_A)
                && match.debugArmCurrentNuke(ParticipantId.PLAYER_B)
                && match.debugStartLaunch(ParticipantId.PLAYER_B);
        boolean winnerPossible = match.getParticipant(ParticipantId.PLAYER_A) != null
                && match.getParticipant(ParticipantId.PLAYER_B) != null;

        boolean success = matchCreated && modePvpLocal && twoHumanBoards
                && sharedSequence && p1InputIsolated && p2InputIsolated
                && noAiDriver && bothCanCharge && bothCanLaunch && winnerPossible;

        report("matchCreated", matchCreated);
        report("modePvpLocal", modePvpLocal);
        report("twoHumanBoards", twoHumanBoards);
        report("sharedSequence", sharedSequence);
        report("p1InputIsolated", p1InputIsolated);
        report("p2InputIsolated", p2InputIsolated);
        report("noAiDriver", noAiDriver);
        report("bothCanCharge", bothCanCharge);
        report("bothCanLaunch", bothCanLaunch);
        report("winnerPossible", winnerPossible);
        report("success", success);
        if (!success) System.exit(1);
    }

    private static void report(String name, boolean value) {
        System.out.println(name + "=" + value);
    }
}
