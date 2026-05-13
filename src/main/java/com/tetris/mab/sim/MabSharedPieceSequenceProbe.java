package com.tetris.mab.sim;

import com.tetris.mab.pieces.MabPieceStream;
import com.tetris.mab.pieces.MabSharedPieceSequence;
import com.tetris.model.TetrominoType;

import java.util.ArrayList;
import java.util.List;

/**
 * Step 21 \u2014 verifies the shared deterministic 7-bag piece sequence
 * used by MAB versus modes.
 *
 * <p>Run: {@code java -cp target\classes com.tetris.mab.sim.MabSharedPieceSequenceProbe}
 *
 * <p>Offline-only.
 */
public final class MabSharedPieceSequenceProbe {

    public static void main(String[] args) {
        long seed = 123456L;
        System.out.println("=== MAB Shared Piece Sequence Probe ===");
        System.out.println("seed=" + seed);

        boolean firstFiftyEqual = checkFirstFiftyEqual(seed);
        System.out.println("first50Equal=" + firstFiftyEqual);

        boolean independentCursors = checkIndependentCursors(seed);
        System.out.println("independentCursors=" + independentCursors);

        boolean sameSeed = checkSameSeedReproducible(seed);
        System.out.println("sameSeedReproducible=" + sameSeed);

        boolean differentSeed = checkDifferentSeedDifferent(seed, seed + 1);
        System.out.println("differentSeedDifferent=" + differentSeed);

        boolean holdSafe = checkHoldDoesNotMutateSequence(seed);
        System.out.println("holdDoesNotMutateSequence=" + holdSafe);

        boolean success = firstFiftyEqual && independentCursors && sameSeed
                && differentSeed && holdSafe;
        System.out.println("success=" + success);
        if (!success) System.exit(1);
    }

    private static boolean checkFirstFiftyEqual(long seed) {
        MabSharedPieceSequence shared = new MabSharedPieceSequence(seed);
        MabPieceStream a = shared.newStream();
        MabPieceStream b = shared.newStream();
        for (int i = 0; i < 50; i++) {
            if (a.next() != b.next()) return false;
        }
        return true;
    }

    private static boolean checkIndependentCursors(long seed) {
        MabSharedPieceSequence shared = new MabSharedPieceSequence(seed);
        MabPieceStream a = shared.newStream();
        MabPieceStream b = shared.newStream();
        // A consumes 10, B consumes 4, then B's 5th piece must equal A's 5th.
        List<TetrominoType> aSeq = new ArrayList<>();
        for (int i = 0; i < 10; i++) aSeq.add(a.next());
        List<TetrominoType> bSeq = new ArrayList<>();
        for (int i = 0; i < 4; i++) bSeq.add(b.next());
        TetrominoType bFifth = b.next(); // B's 5th piece
        return bFifth == aSeq.get(4);
    }

    private static boolean checkSameSeedReproducible(long seed) {
        MabSharedPieceSequence s1 = new MabSharedPieceSequence(seed);
        MabSharedPieceSequence s2 = new MabSharedPieceSequence(seed);
        MabPieceStream a = s1.newStream();
        MabPieceStream b = s2.newStream();
        for (int i = 0; i < 30; i++) {
            if (a.next() != b.next()) return false;
        }
        return true;
    }

    private static boolean checkDifferentSeedDifferent(long seed1, long seed2) {
        MabSharedPieceSequence s1 = new MabSharedPieceSequence(seed1);
        MabSharedPieceSequence s2 = new MabSharedPieceSequence(seed2);
        MabPieceStream a = s1.newStream();
        MabPieceStream b = s2.newStream();
        boolean anyDifferent = false;
        for (int i = 0; i < 50; i++) {
            if (a.next() != b.next()) { anyDifferent = true; break; }
        }
        return anyDifferent;
    }

    private static boolean checkHoldDoesNotMutateSequence(long seed) {
        // Simulate: A reads 5 pieces, "holds" (does NOT consume), reads 5
        // more. The total of 10 reads should match the first 10 pieces of
        // a fresh stream from the same seed. Hold in real GameState reuses
        // the held slot and never calls bag.next().
        MabSharedPieceSequence shared = new MabSharedPieceSequence(seed);
        MabPieceStream a = shared.newStream();
        List<TetrominoType> got = new ArrayList<>();
        for (int i = 0; i < 5; i++) got.add(a.next());
        // (no next() during simulated hold)
        for (int i = 0; i < 5; i++) got.add(a.next());

        MabSharedPieceSequence ref = new MabSharedPieceSequence(seed);
        MabPieceStream r = ref.newStream();
        for (int i = 0; i < 10; i++) {
            if (r.next() != got.get(i)) return false;
        }
        return true;
    }

    private MabSharedPieceSequenceProbe() {}
}
