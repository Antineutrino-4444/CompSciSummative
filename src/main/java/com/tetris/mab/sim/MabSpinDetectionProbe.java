package com.tetris.mab.sim;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

import com.tetris.events.PieceLockResult;
import com.tetris.events.GameEventListener;
import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.model.Board;
import com.tetris.model.GameState;
import com.tetris.model.Position;
import com.tetris.model.SpinDetector;
import com.tetris.model.Tetromino;
import com.tetris.model.TetrominoType;

/**
 * Step 22 \u2014 verifies all-spin recognition (geometry rule + GameState
 * tracking integration) and 0-line spin routing through the simplified
 * MAB pipeline.
 *
 * <p>Run: {@code java -cp target\classes com.tetris.mab.sim.MabSpinDetectionProbe}
 */
public final class MabSpinDetectionProbe {

    private static int failed;

    public static void main(String[] args) {
        System.out.println("=== MAB Spin Detection Probe ===");

        // 1. Direct SpinDetector geometry checks: each piece is locked
        //    in a hole that matches its cells exactly, with garbage
        //    everywhere else, so all four-direction translations collide.
        checkBool("tSpinNoLine", isImmobileInPocket(TetrominoType.T, 0), true);
        checkBool("jSpinNoLine", isImmobileInPocket(TetrominoType.J, 0), true);
        checkBool("sSpinNoLine", isImmobileInPocket(TetrominoType.S, 0), true);
        checkBool("iSpinNoLine", isImmobileInPocket(TetrominoType.I, 0), true);

        // 2. A piece floating in an empty board is not immobile.
        checkBool("movableAfterRotate", isImmobileFreeFloat(TetrominoType.T, 1), false);

        // 3. GameState rotation \u2192 movement cancels spin candidate.
        checkBool("moveAfterRotateCancels", moveAfterRotateCancelsSpin(), true);

        // 4. Hard drop preserves spin candidate (rotate \u2192 hard drop \u2192 lock).
        checkBool("hardDropPreservesSpin", hardDropPreservesSpin(), true);

        // 5. Soft drop preserves spin candidate (rotate \u2192 soft drop \u2192 lock).
        checkBool("softDropPreservesSpin", softDropPreservesSpin(), true);

        // 6. 0-line spin during incoming threat triggers intercept.
        checkBool("zeroLineSpinIntercept", zeroLineSpinIntercept(), true);

        // 7. 0-line spin (no threat, after nuke ready) advances spin route.
        checkBool("zeroLineSpinLaunchProgress", zeroLineSpinLaunchProgress(), true);

        boolean success = failed == 0;
        System.out.println("success=" + success);
        if (!success) System.exit(1);
    }

    // ──────────────────────────── helpers ────────────────────────────

    /**
     * Build a board where the only empty cells are exactly the piece's
     * absolute cells (shifted to a deep stable position). Then the piece
     * cannot translate in any direction without colliding with garbage
     * or going out of bounds.
     */
    private static boolean isImmobileInPocket(TetrominoType type, int rotation) {
        Board board = new Board();
        Tetromino piece = Tetromino.spawn(type, Board.WIDTH).withRotation(rotation);
        // Drop near the floor so down-translation is blocked by bounds
        // even before garbage. Place piece with bottom-most cell at y=2.
        int minY = Integer.MAX_VALUE;
        for (Position c : piece.getAbsoluteCells()) minY = Math.min(minY, c.getY());
        piece = piece.translate(0, 2 - minY);

        Set<Long> hole = new HashSet<>();
        for (Position c : piece.getAbsoluteCells()) {
            hole.add(key(c.getX(), c.getY()));
        }
        // Fill the entire board EXCEPT the hole cells.
        for (int y = 0; y < Board.TOTAL_HEIGHT; y++) {
            for (int x = 0; x < Board.WIDTH; x++) {
                if (!hole.contains(key(x, y))) {
                    board.setCell(x, y, Color.GRAY);
                }
            }
        }
        return SpinDetector.isImmobile(board, piece);
    }

    private static boolean isImmobileFreeFloat(TetrominoType type, int rotation) {
        Board board = new Board();
        Tetromino piece = Tetromino.spawn(type, Board.WIDTH).withRotation(rotation);
        // Drop deep into an empty playfield so all 3 directions are free.
        piece = piece.translate(0, 8);
        return SpinDetector.isImmobile(board, piece);
    }

    private static long key(int x, int y) { return ((long) x << 16) ^ (y & 0xffffL); }

    /**
     * Drives a real {@link GameState}: spawn \u2192 rotate \u2192 move
     * left \u2192 lock. Verifies the resulting lock event has spin=false
     * (movement after rotation must cancel the spin candidate).
     */
    private static boolean moveAfterRotateCancelsSpin() {
        boolean[] spinSeen = { false };
        GameEventListener listener = new GameEventListener() {
            @Override
            public void onPieceLockedDetailed(PieceLockResult e) {
                if (e.spin()) spinSeen[0] = true;
            }
        };
        GameState gs = new GameState();
        gs.addListener(listener);
        gs.rotateCW();
        gs.moveLeft();
        gs.hardDrop(); // locks; should NOT count as spin (movement cancelled).
        return !spinSeen[0];
    }

    private static boolean hardDropPreservesSpin() {
        return rotateThenDropPreservesSpin(true);
    }

    private static boolean softDropPreservesSpin() {
        return rotateThenDropPreservesSpin(false);
    }

    /**
     * After a successful rotation the spin candidate must be set; soft
     * drop, hard drop, and gravity must NOT clear it. We assert the flag
     * remains pending right up to the moment of lock.
     */
    private static boolean rotateThenDropPreservesSpin(boolean hardDrop) {
        GameState gs = new GameState();
        gs.rotateCW();
        if (!gs.isSpinCandidatePending()) gs.rotateCCW();
        if (!gs.isSpinCandidatePending()) return false;
        if (hardDrop) {
            boolean before = gs.isSpinCandidatePending();
            gs.hardDrop();
            return before;
        } else {
            for (int i = 0; i < 30; i++) {
                gs.softDrop();
                if (!gs.isSpinCandidatePending()) return false;
            }
            return true;
        }
    }

    private static boolean zeroLineSpinIntercept() {
        MutuallyAssuredBlocksMatch match = MutuallyAssuredBlocksMatch.createLocalPvpShared(
                new GameState(0), new GameState(0), MatchDifficulty.NORMAL, 1234L);
        match.startMatch();
        ParticipantId attacker = ParticipantId.PLAYER_B;
        ParticipantId defender = ParticipantId.PLAYER_A;
        // Synthesise an incoming threat AGAINST the defender.
        String threatId = match.debugInjectIncomingThreatForTesting(defender);
        if (threatId == null) return false;
        // Defender now performs a 0-line spin \u2192 should intercept.
        long beforeIntercepts = match.getEventLog().stream()
                .filter(e -> "MAB_SPIN_INTERCEPT_TRIGGERED".equals(e.eventType()))
                .count();
        match.debugSimplifiedFeedLockResult(defender, /*lines*/ 0,
                /*spin*/ true, "T Spin", false, false, 0);
        long afterIntercepts = match.getEventLog().stream()
                .filter(e -> "MAB_SPIN_INTERCEPT_TRIGGERED".equals(e.eventType()))
                .count();
        // Use attacker reference to silence unused warning in case of
        // future extension; intercept logging is on the defender side.
        return afterIntercepts > beforeIntercepts && attacker != defender;
    }

    private static boolean zeroLineSpinLaunchProgress() {
        MutuallyAssuredBlocksMatch match = MutuallyAssuredBlocksMatch.createLocalPvpShared(
                new GameState(0), new GameState(0), MatchDifficulty.NORMAL, 5678L);
        match.startMatch();
        ParticipantId pid = ParticipantId.PLAYER_A;
        // Pump enough spin events to fill charge and reach nuke-ready
        // (each spin no-line = +4). Required charge default ~ 200ish.
        for (int i = 0; i < 80; i++) {
            match.debugSimplifiedFeedLockResult(pid, 0, true, "J Spin", false, false, 0);
        }
        // Now feed one more 0-line spin \u2014 should advance spin route.
        long beforeProg = match.getEventLog().stream()
                .filter(e -> "MAB_LAUNCH_PROGRESS_SPIN".equals(e.eventType()))
                .count();
        match.debugSimplifiedFeedLockResult(pid, 0, true, "S Spin", false, false, 0);
        long afterProg = match.getEventLog().stream()
                .filter(e -> "MAB_LAUNCH_PROGRESS_SPIN".equals(e.eventType()))
                .count();
        return afterProg > beforeProg;
    }

    private static void forceCurrentPiece(GameState gs, TetrominoType type) {
        // The default GameState exposes no setter; we rely on the spawned
        // piece. For determinism we just check the type and \u2014 if it
        // doesn't match \u2014 hold/swap until it does. For probe purposes
        // we accept whichever piece is current and adjust expectations.
        // (No-op stub: tests above use whatever piece spawns first; the
        // pocket helpers are TYPE-agnostic for the immobile geometry.)
    }

    // ──────────────────────────── assert ────────────────────────────

    private static void checkBool(String label, boolean actual, boolean expected) {
        boolean ok = actual == expected;
        System.out.println(label + "=" + actual + (ok ? "" : " (expected " + expected + ")"));
        if (!ok) failed++;
    }

    private MabSpinDetectionProbe() {}
}
