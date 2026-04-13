package com.tetris.model;

/**
 * SRSData.java
 * ============
 * Contains the complete Super Rotation System (SRS) wall kick offset data
 * as defined in the Tetris Guideline.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * WHAT IS SRS?
 * ═══════════════════════════════════════════════════════════════════════
 * The Super Rotation System is the standard rotation system used in modern
 * Tetris games (since ~2001, Tetris Worlds onward). It defines:
 *
 *   1. How each piece's cells are arranged in each of 4 rotation states
 *      (handled in TetrominoType).
 *
 *   2. A set of "wall kick" offsets tested when a rotation would otherwise
 *      fail due to collision (handled HERE).
 *
 * ═══════════════════════════════════════════════════════════════════════
 * HOW WALL KICKS WORK
 * ═══════════════════════════════════════════════════════════════════════
 * When the player presses rotate:
 *   1. Try the basic rotation (no translation). If it fits → done.
 *   2. If it doesn't fit, try each of the wall kick offsets for this
 *      particular rotation transition (from-state → to-state).
 *   3. For each offset, translate the rotated piece by that offset and
 *      check if it fits.
 *   4. The first offset that results in a valid position is used.
 *   5. If none of the offsets work, the rotation fails entirely.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * KICK TABLES
 * ═══════════════════════════════════════════════════════════════════════
 * There are TWO separate tables:
 *
 *   - JLSTZ kicks: used for J, L, S, T, Z pieces (3×3 bounding box)
 *   - I kicks:     used for the I piece (4×4 bounding box)
 *
 * The O piece does not rotate visually, so no kicks are needed.
 *
 * Each table maps a rotation transition (e.g., 0→1, 1→2, etc.) to
 * a sequence of 4 kick offsets (tested in order after the basic rotation).
 *
 * The offsets below are computed as: offset[fromState][testIndex] − offset[toState][testIndex]
 * from the Tetris Guideline's base offset tables.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * ROTATION TRANSITIONS
 * ═══════════════════════════════════════════════════════════════════════
 *   Index 0: 0→R  (spawn → clockwise)
 *   Index 1: R→2  (clockwise → 180°)
 *   Index 2: 2→L  (180° → counter-clockwise)
 *   Index 3: L→0  (counter-clockwise → spawn)
 *   Index 4: R→0  (reverse of 0→R)
 *   Index 5: 2→R  (reverse of R→2)
 *   Index 6: L→2  (reverse of 2→L)
 *   Index 7: 0→L  (reverse of L→0)
 */
public final class SRSData {

    private SRSData() {
        // Utility class — not instantiable
    }

    // ─────────────────────────────────────────────────────────────
    // JLSTZ WALL KICK DATA
    // ─────────────────────────────────────────────────────────────
    // 8 transitions × 4 kick tests each.
    // Offsets are (dx, dy) where +x = right, +y = down.

    /**
     * Wall kick offsets for J, L, S, T, Z pieces.
     *
     * Indexed by transition:
     *   [0] 0→R   [1] R→2   [2] 2→L   [3] L→0
     *   [4] R→0   [5] 2→R   [6] L→2   [7] 0→L
     *
     * Each sub-array has 4 offsets (test 1–4; test 0 is always (0,0) i.e. no kick).
     */
    public static final Position[][] JLSTZ_KICKS = {
        // 0→R: (-1,0), (-1,-1), (0,+2), (-1,+2)
        {new Position(-1, 0), new Position(-1, -1), new Position(0, 2), new Position(-1, 2)},
        // R→2: (1,0), (1,1), (0,-2), (1,-2)
        {new Position(1, 0), new Position(1, 1), new Position(0, -2), new Position(1, -2)},
        // 2→L: (1,0), (1,-1), (0,2), (1,2)
        {new Position(1, 0), new Position(1, -1), new Position(0, 2), new Position(1, 2)},
        // L→0: (-1,0), (-1,1), (0,-2), (-1,-2)
        {new Position(-1, 0), new Position(-1, 1), new Position(0, -2), new Position(-1, -2)},
        // R→0: (1,0), (1,1), (0,-2), (1,-2)
        {new Position(1, 0), new Position(1, 1), new Position(0, -2), new Position(1, -2)},
        // 2→R: (-1,0), (-1,-1), (0,2), (-1,2)
        {new Position(-1, 0), new Position(-1, -1), new Position(0, 2), new Position(-1, 2)},
        // L→2: (-1,0), (-1,1), (0,-2), (-1,-2)
        {new Position(-1, 0), new Position(-1, 1), new Position(0, -2), new Position(-1, -2)},
        // 0→L: (1,0), (1,-1), (0,2), (1,2)
        {new Position(1, 0), new Position(1, -1), new Position(0, 2), new Position(1, 2)}
    };

    // ─────────────────────────────────────────────────────────────
    // I-PIECE WALL KICK DATA
    // ─────────────────────────────────────────────────────────────

    /**
     * Wall kick offsets for the I piece.
     * Same indexing as JLSTZ_KICKS.
     */
    public static final Position[][] I_KICKS = {
        // 0→R: (-2,0), (1,0), (-2,1), (1,-2)
        {new Position(-2, 0), new Position(1, 0), new Position(-2, 1), new Position(1, -2)},
        // R→2: (-1,0), (2,0), (-1,-2), (2,1)
        {new Position(-1, 0), new Position(2, 0), new Position(-1, -2), new Position(2, 1)},
        // 2→L: (2,0), (-1,0), (2,-1), (-1,2)
        {new Position(2, 0), new Position(-1, 0), new Position(2, -1), new Position(-1, 2)},
        // L→0: (1,0), (-2,0), (1,2), (-2,-1)
        {new Position(1, 0), new Position(-2, 0), new Position(1, 2), new Position(-2, -1)},
        // R→0: (2,0), (-1,0), (2,-1), (-1,2)
        {new Position(2, 0), new Position(-1, 0), new Position(2, -1), new Position(-1, 2)},
        // 2→R: (1,0), (-2,0), (1,2), (-2,-1)
        {new Position(1, 0), new Position(-2, 0), new Position(1, 2), new Position(-2, -1)},
        // L→2: (-2,0), (1,0), (-2,1), (1,-2)
        {new Position(-2, 0), new Position(1, 0), new Position(-2, 1), new Position(1, -2)},
        // 0→L: (-1,0), (2,0), (-1,-2), (2,1)
        {new Position(-1, 0), new Position(2, 0), new Position(-1, -2), new Position(2, 1)}
    };

    /**
     * Returns the appropriate wall kick offsets for a given piece type and
     * rotation transition.
     *
     * @param type      the tetromino type
     * @param fromState the rotation state before rotation (0–3)
     * @param toState   the rotation state after rotation (0–3)
     * @return array of kick offsets to test (may be empty for O piece), or null if invalid
     */
    public static Position[] getKicks(TetrominoType type, int fromState, int toState) {
        // O piece never needs wall kicks
        if (type == TetrominoType.O) {
            return new Position[0];
        }

        int transitionIndex = getTransitionIndex(fromState, toState);
        if (transitionIndex < 0) {
            return new Position[0];
        }

        if (type == TetrominoType.I) {
            return I_KICKS[transitionIndex];
        } else {
            return JLSTZ_KICKS[transitionIndex];
        }
    }

    /**
     * Maps a (fromState, toState) pair to a kick table index.
     *
     * @return index 0–7, or -1 if invalid
     */
    private static int getTransitionIndex(int from, int to) {
        // CW transitions
        if (from == 0 && to == 1) return 0;
        if (from == 1 && to == 2) return 1;
        if (from == 2 && to == 3) return 2;
        if (from == 3 && to == 0) return 3;
        // CCW transitions
        if (from == 1 && to == 0) return 4;
        if (from == 2 && to == 1) return 5;
        if (from == 3 && to == 2) return 6;
        if (from == 0 && to == 3) return 7;
        return -1;
    }
}
