package com.tetris.mab.sim;

import com.tetris.events.GameEventListener;
import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ai.MabAiArchetype;
import com.tetris.mab.ai.MabAiDesignPicker;
import com.tetris.mab.ai.MabAiDifficulty;
import com.tetris.mab.ai.MabAiDriver;
import com.tetris.mab.ai.MabBoardAiDriver;
import com.tetris.mab.balance.MabBalanceProfiles;
import com.tetris.mab.nuke.NukeDesign;
import com.tetris.model.Board;
import com.tetris.model.GameState;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Head-to-head probe — pits MASTER against each lower tier on a shared
 * deterministic 7-bag PvE match. Each side plays until a fixed piece
 * budget is reached (or one tops out). Winner is decided by:
 * <ol>
 *   <li>If one side topped out, the other wins.</li>
 *   <li>Otherwise the side with more lines cleared.</li>
 *   <li>Tied lines: lower hole count wins.</li>
 * </ol>
 *
 * <p>Run:
 * <pre>
 *   java -cp target\classes com.tetris.mab.sim.MabAiHeadToHeadProbe
 * </pre>
 *
 * <p>Offline-only.
 */
public final class MabAiHeadToHeadProbe {

    public static void main(String[] args) {
        int piecesPerMatch = 30;
        int gamesPerPair = 5;
        if (args.length > 0) try { piecesPerMatch = Math.max(20, Integer.parseInt(args[0])); }
            catch (NumberFormatException ignored) {}
        if (args.length > 1) try { gamesPerPair = Math.max(1, Integer.parseInt(args[1])); }
            catch (NumberFormatException ignored) {}

        System.out.println("MAB AI head-to-head probe (fixed piece budget)");
        System.out.println("  master vs each lower tier, pieces/match=" + piecesPerMatch
                + " games/pair=" + gamesPerPair);
        System.out.printf("%-10s  %-7s  %-7s  %-7s  %-9s  %-9s  %-7s  %-7s%n",
                "opponent", "wins", "losses", "ties", "masterLn", "oppLn",
                "masterH", "oppH");

        MabAiDifficulty[] opponents = {
                MabAiDifficulty.EASY, MabAiDifficulty.MEDIUM,
                MabAiDifficulty.HARD, MabAiDifficulty.EXPERT };

        int totalWins = 0, totalGames = 0;
        for (MabAiDifficulty opp : opponents) {
            int wins = 0, losses = 0, ties = 0;
            int masterLines = 0, oppLines = 0;
            int masterHoles = 0, oppHoles = 0;
            for (int g = 0; g < gamesPerPair; g++) {
                Outcome o = runMatch(MabAiDifficulty.MASTER, opp,
                        /*seed*/ 42L + g * 17L, piecesPerMatch);
                if (o.winner == ParticipantId.PLAYER_A) wins++;
                else if (o.winner == ParticipantId.PLAYER_B) losses++;
                else ties++;
                masterLines += o.aLines;
                oppLines += o.bLines;
                masterHoles += o.aHoles;
                oppHoles += o.bHoles;
            }
            totalGames += gamesPerPair;
            totalWins += wins;
            System.out.printf("%-10s  %-7d  %-7d  %-7d  %-9d  %-9d  %-7d  %-7d%n",
                    opp.name(), wins, losses, ties, masterLines, oppLines,
                    masterHoles, oppHoles);
        }
        double winRate = totalGames > 0 ? (totalWins * 100.0 / totalGames) : 0;
        System.out.println();
        System.out.printf("master overall winRate=%.1f%%  (%d/%d)%n",
                winRate, totalWins, totalGames);
        boolean ok = winRate >= 55.0;
        System.out.println("acceptance: master winRate >= 55% : " + ok);
        if (!ok) System.exit(1);
    }

    private static Outcome runMatch(MabAiDifficulty a, MabAiDifficulty b,
                                     long seed, int piecesPerMatch) {
        GameState gsA = new GameState(0);
        GameState gsB = new GameState(0);
        MutuallyAssuredBlocksMatch match = MutuallyAssuredBlocksMatch.createPveShared(
                gsA, gsB, MatchDifficulty.NORMAL, seed);
        match.startMatch();

        // Pick archetype-flavoured designs for both sides so each AI
        // plays toward its own warhead identity.
        NukeDesign designA = MabAiDesignPicker.pick(MabAiArchetype.BALANCED, a);
        NukeDesign designB = MabAiDesignPicker.pick(MabAiArchetype.BALANCED, b);
        match.applyWarheadDesign(ParticipantId.PLAYER_A, designA);
        match.applyWarheadDesign(ParticipantId.PLAYER_B, designB);

        MabBoardAiDriver boardA = new MabBoardAiDriver(gsA);
        boardA.setDifficulty(a);
        boardA.setSeed(seed ^ 0x1111L);
        boardA.attachStrategicContext(match, ParticipantId.PLAYER_A);
        MabBoardAiDriver boardB = new MabBoardAiDriver(gsB);
        boardB.setDifficulty(b);
        boardB.setSeed(seed ^ 0x2222L);
        boardB.attachStrategicContext(match, ParticipantId.PLAYER_B);

        MabAiDriver stratA = new MabAiDriver(match, ParticipantId.PLAYER_A,
                MabAiArchetype.BALANCED, a, MabBalanceProfiles.standardPve());
        MabAiDriver stratB = new MabAiDriver(match, ParticipantId.PLAYER_B,
                MabAiArchetype.BALANCED, b, MabBalanceProfiles.standardPve());
        stratA.setEnabled(true);
        stratB.setEnabled(true);
        stratA.setAdvanceHiddenClock(false);
        stratB.setAdvanceHiddenClock(false);

        AtomicInteger aPieces = new AtomicInteger();
        AtomicInteger bPieces = new AtomicInteger();
        gsA.addListener(new GameEventListener() {
            @Override public void onPieceLocked(PieceLockedEvent e) { aPieces.incrementAndGet(); }
        });
        gsB.addListener(new GameEventListener() {
            @Override public void onPieceLocked(PieceLockedEvent e) { bPieces.incrementAndGet(); }
        });

        ParticipantId winner = null;
        for (int i = 0; i < 8000; i++) {
            boardA.tick();
            boardB.tick();
            if (i % 6 == 0) {
                try { stratA.tick(); } catch (RuntimeException ignored) {}
                try { stratB.tick(); } catch (RuntimeException ignored) {}
            }
            if (gsA.isGameOver() && winner == null) winner = ParticipantId.PLAYER_B;
            if (gsB.isGameOver() && winner == null) winner = ParticipantId.PLAYER_A;
            if (winner != null) break;
            if (aPieces.get() >= piecesPerMatch && bPieces.get() >= piecesPerMatch) break;
            try { Thread.sleep(2); } catch (InterruptedException ex) {
                Thread.currentThread().interrupt(); break;
            }
        }
        int aLines = boardA.getLinesClearedTotal();
        int bLines = boardB.getLinesClearedTotal();
        int aHoles = analyseHoles(gsA.getBoard());
        int bHoles = analyseHoles(gsB.getBoard());
        if (winner == null) {
            if (aLines != bLines) winner = (aLines > bLines)
                    ? ParticipantId.PLAYER_A : ParticipantId.PLAYER_B;
            else if (aHoles != bHoles) winner = (aHoles < bHoles)
                    ? ParticipantId.PLAYER_A : ParticipantId.PLAYER_B;
        }
        return new Outcome(winner, aLines, bLines, aHoles, bHoles);
    }

    private static int analyseHoles(Board board) {
        int w = Board.WIDTH;
        int h = Board.TOTAL_HEIGHT;
        int[] colH = new int[w];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                Color c = board.getCell(x, y);
                if (c != null) { colH[x] = h - y; break; }
            }
        }
        int holes = 0;
        for (int x = 0; x < w; x++) {
            int top = h - colH[x];
            for (int y = top + 1; y < h; y++) {
                if (board.getCell(x, y) == null) holes++;
            }
        }
        return holes;
    }

    private static final class Outcome {
        final ParticipantId winner;
        final int aLines;
        final int bLines;
        final int aHoles;
        final int bHoles;
        Outcome(ParticipantId w, int aL, int bL, int aH, int bH) {
            this.winner = w; this.aLines = aL; this.bLines = bL;
            this.aHoles = aH; this.bHoles = bH;
        }
    }

    private MabAiHeadToHeadProbe() {}
}
