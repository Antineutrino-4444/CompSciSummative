package com.tetris.model;

/**
 * Provides wall kick offset data for piece rotations.
 *
 * <p>Since the new particle pieces are smaller than standard tetrominoes
 * (3-cell trominoes and 1-cell gluons), the wall kicks are simplified.
 * Trominoes use a small set of kick tests within a 2×2 or 3×1 bounding box.
 * The gluon (single cell) doesn't rotate.</p>
 */
public final class WallKickData {

    private WallKickData() {}

    /**
     * Kick data for L/J-tromino pieces (2×2 bounding box).
     * Small pieces need fewer kicks — just try offsets to keep the piece in bounds.
     */
    private static final int[][][][] TROMINO_LJ_KICKS = new int[4][4][][];

    /**
     * Kick data for line-tromino pieces (3×1 or 1×3 bounding box).
     */
    private static final int[][][][] TROMINO_LINE_KICKS = new int[4][4][][];

    static {
        // L/J tromino kicks (2×2 bounding box)
        // These are lightweight — 3 tests each
        for (int from = 0; from < 4; from++) {
            for (int to = 0; to < 4; to++) {
                if (from != to) {
                    TROMINO_LJ_KICKS[from][to] = new int[][] {
                        {0, 0}, {-1, 0}, {1, 0}, {0, 1}, {0, -1}
                    };
                }
            }
        }

        // Line tromino kicks (3×1 ↔ 1×3)
        // Horizontal to vertical: shift to keep in play area
        for (int from = 0; from < 4; from++) {
            for (int to = 0; to < 4; to++) {
                if (from != to) {
                    TROMINO_LINE_KICKS[from][to] = new int[][] {
                        {0, 0}, {-1, 0}, {1, 0}, {-1, 1}, {1, 1}, {0, -1}
                    };
                }
            }
        }
    }

    /**
     * Gets the wall kick offsets for a rotation transition.
     *
     * @param piece        the piece being rotated
     * @param fromRotation the current rotation state (0-3)
     * @param toRotation   the target rotation state (0-3)
     * @return array of kick tests, each as {dx, dy}, or null if no data
     */
    public static int[][] getKicks(Piece piece, int fromRotation, int toRotation) {
        fromRotation &= 3;
        toRotation &= 3;
        if (fromRotation == toRotation) {
            return new int[][] {{0, 0}};
        }

        // Gluon (single cell) doesn't need rotation
        if (piece == Piece.GLUON) {
            return new int[][] {{0, 0}};
        }

        // Line trominoes (TOP_QUARK_B, BOTTOM_QUARK_B) — 3×1 / 1×3
        if (piece == Piece.TOP_QUARK_B || piece == Piece.BOTTOM_QUARK_B) {
            return TROMINO_LINE_KICKS[fromRotation][toRotation];
        }

        // L/J trominoes (TOP_QUARK_A, BOTTOM_QUARK_A) — 2×2
        return TROMINO_LJ_KICKS[fromRotation][toRotation];
    }
}
