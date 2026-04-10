package com.tetris.model;

/**
 * Provides the Super Rotation System (SRS) wall kick offset data
 * as defined in the Tetris Guideline.
 *
 * <p>When a piece cannot rotate in place, the game tries up to 5 alternative
 * positions (kick tests) to see if the rotation can succeed. The offsets
 * differ between normal pieces (J, L, S, T, Z) and the I-piece.</p>
 *
 * <p>Each rotation transition (e.g. 0→R, R→2, etc.) has a specific set of
 * 5 offset tests. The game tries them in order; if any position is valid
 * (no collision), the rotation succeeds at that offset.</p>
 *
 * <h3>Rotation State Naming</h3>
 * <ul>
 *   <li>0 = Spawn state</li>
 *   <li>R = Clockwise rotation from spawn</li>
 *   <li>2 = 180° rotation from spawn</li>
 *   <li>L = Counter-clockwise rotation from spawn</li>
 * </ul>
 */
public final class WallKickData {

    private WallKickData() {
        // Utility class
    }

    /**
     * Wall kick offsets for J, L, S, T, Z pieces.
     *
     * <p>Indexed as {@code JLSTZ_KICKS[fromRotation][toRotation][testIndex]},
     * where each entry is {dx, dy} (column offset, row offset).
     * Positive dx = right, positive dy = up.</p>
     *
     * <p>The 8 possible rotation transitions and their 5 tests each:</p>
     */
    private static final int[][][][] JLSTZ_KICKS = new int[4][4][][];

    /**
     * Wall kick offsets for the I-piece.
     *
     * <p>Same indexing as {@link #JLSTZ_KICKS}. The I-piece has different
     * offsets because of its 4×4 bounding box.</p>
     */
    private static final int[][][][] I_KICKS = new int[4][4][][];

    static {
        // JLSTZ wall kick data (from Tetris Guideline / SRS specification)
        // 0→R
        JLSTZ_KICKS[0][1] = new int[][] {{0,0},{-1,0},{-1,1},{0,-2},{-1,-2}};
        // R→0
        JLSTZ_KICKS[1][0] = new int[][] {{0,0},{1,0},{1,-1},{0,2},{1,2}};
        // R→2
        JLSTZ_KICKS[1][2] = new int[][] {{0,0},{1,0},{1,-1},{0,2},{1,2}};
        // 2→R
        JLSTZ_KICKS[2][1] = new int[][] {{0,0},{-1,0},{-1,1},{0,-2},{-1,-2}};
        // 2→L
        JLSTZ_KICKS[2][3] = new int[][] {{0,0},{1,0},{1,1},{0,-2},{1,-2}};
        // L→2
        JLSTZ_KICKS[3][2] = new int[][] {{0,0},{-1,0},{-1,-1},{0,2},{-1,2}};
        // L→0
        JLSTZ_KICKS[3][0] = new int[][] {{0,0},{-1,0},{-1,-1},{0,2},{-1,2}};
        // 0→L
        JLSTZ_KICKS[0][3] = new int[][] {{0,0},{1,0},{1,1},{0,-2},{1,-2}};

        // I-piece wall kick data
        // 0→R
        I_KICKS[0][1] = new int[][] {{0,0},{-2,0},{1,0},{-2,-1},{1,2}};
        // R→0
        I_KICKS[1][0] = new int[][] {{0,0},{2,0},{-1,0},{2,1},{-1,-2}};
        // R→2
        I_KICKS[1][2] = new int[][] {{0,0},{-1,0},{2,0},{-1,2},{2,-1}};
        // 2→R
        I_KICKS[2][1] = new int[][] {{0,0},{1,0},{-2,0},{1,-2},{-2,1}};
        // 2→L
        I_KICKS[2][3] = new int[][] {{0,0},{2,0},{-1,0},{2,1},{-1,-2}};
        // L→2
        I_KICKS[3][2] = new int[][] {{0,0},{-2,0},{1,0},{-2,-1},{1,2}};
        // L→0
        I_KICKS[3][0] = new int[][] {{0,0},{1,0},{-2,0},{1,-2},{-2,1}};
        // 0→L
        I_KICKS[0][3] = new int[][] {{0,0},{-1,0},{2,0},{-1,2},{2,-1}};
    }

    /**
     * Gets the wall kick offsets for a rotation transition.
     *
     * <p>Uses the I-piece kick data for Bottom Quark Blue (the I-shape),
     * and JLSTZ data for all other quarks (T, S, Z, J, L shapes).
     * Gluon (O-shape) doesn't rotate.</p>
     *
     * @param piece        the piece being rotated
     * @param fromRotation the current rotation state (0-3)
     * @param toRotation   the target rotation state (0-3)
     * @return array of 5 kick tests, each as {dx, dy}. Returns null if no data
     *         exists (e.g. same-state rotation).
     */
    public static int[][] getKicks(Piece piece, int fromRotation, int toRotation) {
        fromRotation &= 3;
        toRotation &= 3;
        if (fromRotation == toRotation) {
            return new int[][] {{0, 0}};
        }
        // Bottom Quark Blue uses 4×4 bounding box (I-piece equivalent)
        if (piece == Piece.BOTTOM_QUARK_B) {
            return I_KICKS[fromRotation][toRotation];
        }
        // Gluon (O-shape) doesn't rotate
        if (piece == Piece.GLUON) {
            return new int[][] {{0, 0}};
        }
        // All other quarks use JLSTZ kicks
        return JLSTZ_KICKS[fromRotation][toRotation];
    }
}
