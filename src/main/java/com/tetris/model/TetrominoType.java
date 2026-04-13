package com.tetris.model;

import java.awt.Color;

/**
 * TetrominoType.java
 * ==================
 * Enumerates the seven standard Tetris pieces (tetrominoes) following the
 * Tetris Guideline specification.
 *
 * PIECE SHAPES (rotation state 0 — spawn orientation):
 * ─────────────────────────────────────────────────────
 *
 *  I-piece (cyan):        O-piece (yellow):     T-piece (purple):
 *  . . . .                . X X .               . X . .
 *  X X X X                . X X .               X X X .
 *  . . . .                . . . .               . . . .
 *  . . . .                . . . .               . . . .
 *
 *  S-piece (green):       Z-piece (red):        J-piece (blue):
 *  . X X .                X X . .               X . . .
 *  X X . .                . X X .               X X X .
 *  . . . .                . . . .               . . . .
 *
 *  L-piece (orange):
 *  . . X .
 *  X X X .
 *  . . . .
 *
 * ROTATION STATES:
 *   Each piece stores 4 rotation states (0, 1, 2, 3) corresponding to
 *   0°, 90° clockwise, 180°, and 270° clockwise rotations.
 *
 *   The cell coordinates are stored as (col, row) offsets relative to the
 *   top-left corner of the bounding box.
 *
 * COLORS:
 *   Follow the Tetris Guideline standard color assignments.
 *
 * BOUNDING BOX:
 *   I and O pieces use a 4×4 box; all others use a 3×3 box.
 *   This affects SRS wall kick table selection.
 */
public enum TetrominoType {

    /*
     * Each piece defines its 4 rotation states.
     * rotationStates[state] = array of 4 Position offsets (the 4 filled cells).
     *
     * Rotation state indices:
     *   0 = spawn / 0°
     *   1 = 90° clockwise  (R)
     *   2 = 180°           (2)
     *   3 = 270° clockwise (L)
     */

    /**
     * I-piece — the long bar. Unique 4×4 bounding box.
     * Cyan color (#00F0F0).
     */
    I(new Position[][]{
        // State 0: horizontal bar in row 1
        {new Position(0, 1), new Position(1, 1), new Position(2, 1), new Position(3, 1)},
        // State 1 (R): vertical bar in col 2
        {new Position(2, 0), new Position(2, 1), new Position(2, 2), new Position(2, 3)},
        // State 2: horizontal bar in row 2
        {new Position(0, 2), new Position(1, 2), new Position(2, 2), new Position(3, 2)},
        // State 3 (L): vertical bar in col 1
        {new Position(1, 0), new Position(1, 1), new Position(1, 2), new Position(1, 3)}
    }, new Color(0, 240, 240), 4),

    /**
     * O-piece — the 2×2 square. Does not change shape on rotation.
     * Yellow color (#F0F000).
     */
    O(new Position[][]{
        {new Position(1, 0), new Position(2, 0), new Position(1, 1), new Position(2, 1)},
        {new Position(1, 0), new Position(2, 0), new Position(1, 1), new Position(2, 1)},
        {new Position(1, 0), new Position(2, 0), new Position(1, 1), new Position(2, 1)},
        {new Position(1, 0), new Position(2, 0), new Position(1, 1), new Position(2, 1)}
    }, new Color(240, 240, 0), 4),

    /**
     * T-piece — T-shape. Center of many advanced techniques (T-spins).
     * Purple color (#A000F0).
     */
    T(new Position[][]{
        {new Position(1, 0), new Position(0, 1), new Position(1, 1), new Position(2, 1)},
        {new Position(1, 0), new Position(1, 1), new Position(2, 1), new Position(1, 2)},
        {new Position(0, 1), new Position(1, 1), new Position(2, 1), new Position(1, 2)},
        {new Position(1, 0), new Position(0, 1), new Position(1, 1), new Position(1, 2)}
    }, new Color(160, 0, 240), 3),

    /**
     * S-piece — S/skew shape.
     * Green color (#00F000).
     */
    S(new Position[][]{
        {new Position(1, 0), new Position(2, 0), new Position(0, 1), new Position(1, 1)},
        {new Position(1, 0), new Position(1, 1), new Position(2, 1), new Position(2, 2)},
        {new Position(1, 1), new Position(2, 1), new Position(0, 2), new Position(1, 2)},
        {new Position(0, 0), new Position(0, 1), new Position(1, 1), new Position(1, 2)}
    }, new Color(0, 240, 0), 3),

    /**
     * Z-piece — Z/skew shape (mirror of S).
     * Red color (#F00000).
     */
    Z(new Position[][]{
        {new Position(0, 0), new Position(1, 0), new Position(1, 1), new Position(2, 1)},
        {new Position(2, 0), new Position(1, 1), new Position(2, 1), new Position(1, 2)},
        {new Position(0, 1), new Position(1, 1), new Position(1, 2), new Position(2, 2)},
        {new Position(1, 0), new Position(0, 1), new Position(1, 1), new Position(0, 2)}
    }, new Color(240, 0, 0), 3),

    /**
     * J-piece — J-shape.
     * Blue color (#0000F0).
     */
    J(new Position[][]{
        {new Position(0, 0), new Position(0, 1), new Position(1, 1), new Position(2, 1)},
        {new Position(1, 0), new Position(2, 0), new Position(1, 1), new Position(1, 2)},
        {new Position(0, 1), new Position(1, 1), new Position(2, 1), new Position(2, 2)},
        {new Position(1, 0), new Position(1, 1), new Position(0, 2), new Position(1, 2)}
    }, new Color(0, 0, 240), 3),

    /**
     * L-piece — L-shape (mirror of J).
     * Orange color (#F0A000).
     */
    L(new Position[][]{
        {new Position(2, 0), new Position(0, 1), new Position(1, 1), new Position(2, 1)},
        {new Position(1, 0), new Position(1, 1), new Position(1, 2), new Position(2, 2)},
        {new Position(0, 1), new Position(1, 1), new Position(2, 1), new Position(0, 2)},
        {new Position(0, 0), new Position(1, 0), new Position(1, 1), new Position(1, 2)}
    }, new Color(240, 160, 0), 3);

    // ─────────────────────────── Fields ───────────────────────────

    /**
     * The cell offsets for each of the 4 rotation states.
     * rotationStates[stateIndex] = Position[4] (the 4 cells).
     */
    private final Position[][] rotationStates;

    /** The guideline color for this piece. */
    private final Color color;

    /** Bounding box size: 4 for I and O, 3 for all others. */
    private final int boundingBoxSize;

    // ─────────────────────────── Constructor ──────────────────────

    TetrominoType(Position[][] rotationStates, Color color, int boundingBoxSize) {
        this.rotationStates = rotationStates;
        this.color = color;
        this.boundingBoxSize = boundingBoxSize;
    }

    // ─────────────────────────── Accessors ───────────────────────

    /**
     * Returns the 4 cell offsets for a given rotation state.
     *
     * @param state rotation state index (0–3)
     * @return array of 4 Position offsets within the bounding box
     */
    public Position[] getCells(int state) {
        return rotationStates[state];
    }

    /** Returns the guideline color. */
    public Color getColor() {
        return color;
    }

    /**
     * Returns the bounding box size (3 or 4).
     * This determines which SRS wall kick table to use.
     */
    public int getBoundingBoxSize() {
        return boundingBoxSize;
    }

    /** Total number of rotation states (always 4). */
    public int getRotationStateCount() {
        return rotationStates.length;
    }
}
