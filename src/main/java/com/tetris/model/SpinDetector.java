package com.tetris.model;

/**
 * Step 22 \u2014 generic immobile-spin geometry helper.
 *
 * <p>A locked tetromino is "immobile" when, at its final resting position,
 * it cannot translate one cell to the left, right, or up without colliding
 * with the board contents or the playfield walls. This is the geometric
 * basis of the all-spin rule used by Mutually Assured Blocks: a rotation
 * that lands a piece in a slot it cannot escape counts as a spin
 * regardless of which piece type was used or whether any lines cleared.
 *
 * <p>Engine-level only \u2014 callers (e.g. {@link GameState}) layer the
 * additional gameplay rules (rotation was the final placement-changing
 * input, no horizontal move after rotation, etc.) on top.
 *
 * <p><b>Offline-only.</b>
 */
public final class SpinDetector {

    private SpinDetector() {}

    /**
     * Returns {@code true} when {@code piece} cannot move left, right, or
     * up by one cell on {@code board}. Hard-drop and gravity are
     * irrelevant; this purely tests spatial immobility.
     */
    public static boolean isImmobile(Board board, Tetromino piece) {
        if (board == null || piece == null) return false;
        if (board.isValidPosition(piece.translate(-1, 0))) return false;
        if (board.isValidPosition(piece.translate( 1, 0))) return false;
        if (board.isValidPosition(piece.translate( 0, -1))) return false;
        return true;
    }
}
