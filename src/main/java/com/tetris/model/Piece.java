package com.tetris.model;

import java.util.Map;

/**
 * Represents the 7 standard Tetris pieces (tetrominoes) as defined by the
 * Tetris Guideline.
 *
 * <p>Each piece is defined by its shape in all 4 rotation states (0, R, 2, L),
 * using a coordinate system where (0,0) is the top-left of the bounding box.
 * The I and O pieces use a 4×4 and 3×3 bounding box respectively for SRS
 * rotation. All other pieces use a 3×3 bounding box.</p>
 *
 * <p>Colors follow the standard Tetris Guideline:</p>
 * <ul>
 *   <li>I - Cyan</li>
 *   <li>O - Yellow</li>
 *   <li>T - Purple</li>
 *   <li>S - Green</li>
 *   <li>Z - Red</li>
 *   <li>J - Blue</li>
 *   <li>L - Orange</li>
 * </ul>
 */
public enum Piece {

    /**
     * The I-piece (straight line). Uses a 4×4 bounding box.
     * Cyan color.
     */
    I(new int[][][] {
        // Rotation 0 (spawn)
        {{0,1},{1,1},{2,1},{3,1}},
        // Rotation R (clockwise)
        {{2,0},{2,1},{2,2},{2,3}},
        // Rotation 2 (180°)
        {{0,2},{1,2},{2,2},{3,2}},
        // Rotation L (counter-clockwise)
        {{1,0},{1,1},{1,2},{1,3}}
    }, 0x00FFFF, 4),

    /**
     * The O-piece (square). Uses a 3×3 bounding box.
     * Yellow color. Does not visibly rotate.
     */
    O(new int[][][] {
        {{1,0},{2,0},{1,1},{2,1}},
        {{1,0},{2,0},{1,1},{2,1}},
        {{1,0},{2,0},{1,1},{2,1}},
        {{1,0},{2,0},{1,1},{2,1}}
    }, 0xFFFF00, 3),

    /**
     * The T-piece. Uses a 3×3 bounding box.
     * Purple color. Central to T-spin mechanics.
     */
    T(new int[][][] {
        {{1,0},{0,1},{1,1},{2,1}},
        {{1,0},{1,1},{2,1},{1,2}},
        {{0,1},{1,1},{2,1},{1,2}},
        {{1,0},{0,1},{1,1},{1,2}}
    }, 0xAA00FF, 3),

    /**
     * The S-piece (S-skew). Uses a 3×3 bounding box.
     * Green color.
     */
    S(new int[][][] {
        {{1,0},{2,0},{0,1},{1,1}},
        {{1,0},{1,1},{2,1},{2,2}},
        {{1,1},{2,1},{0,2},{1,2}},
        {{0,0},{0,1},{1,1},{1,2}}
    }, 0x00FF00, 3),

    /**
     * The Z-piece (Z-skew). Uses a 3×3 bounding box.
     * Red color.
     */
    Z(new int[][][] {
        {{0,0},{1,0},{1,1},{2,1}},
        {{2,0},{1,1},{2,1},{1,2}},
        {{0,1},{1,1},{1,2},{2,2}},
        {{1,0},{0,1},{1,1},{0,2}}
    }, 0xFF0000, 3),

    /**
     * The J-piece. Uses a 3×3 bounding box.
     * Blue color.
     */
    J(new int[][][] {
        {{0,0},{0,1},{1,1},{2,1}},
        {{1,0},{2,0},{1,1},{1,2}},
        {{0,1},{1,1},{2,1},{2,2}},
        {{1,0},{1,1},{0,2},{1,2}}
    }, 0x0000FF, 3),

    /**
     * The L-piece. Uses a 3×3 bounding box.
     * Orange color.
     */
    L(new int[][][] {
        {{2,0},{0,1},{1,1},{2,1}},
        {{1,0},{1,1},{1,2},{2,2}},
        {{0,1},{1,1},{2,1},{0,2}},
        {{0,0},{1,0},{1,1},{1,2}}
    }, 0xFF8800, 3);

    /**
     * The shape data for all 4 rotation states.
     * {@code cells[rotation][cellIndex]} gives {col, row} offsets within bounding box.
     */
    private final int[][][] cells;

    /** The color of this piece as a 24-bit RGB value. */
    private final int color;

    /** The size of the bounding box (3 for most pieces, 4 for I). */
    private final int boundingBox;

    Piece(int[][][] cells, int color, int boundingBox) {
        this.cells = cells;
        this.color = color;
        this.boundingBox = boundingBox;
    }

    /**
     * Returns the cell positions for the given rotation state.
     *
     * @param rotation the rotation state (0=spawn, 1=R/CW, 2=180, 3=L/CCW)
     * @return array of {col, row} pairs relative to the piece's bounding box origin
     */
    public int[][] getCells(int rotation) {
        return cells[rotation & 3];
    }

    /**
     * Returns the color of this piece as a 24-bit RGB integer.
     *
     * @return the color value (e.g. 0x00FFFF for cyan)
     */
    public int getColor() {
        return color;
    }

    /**
     * Returns the bounding box size for this piece.
     *
     * @return 4 for I-piece, 3 for all others
     */
    public int getBoundingBox() {
        return boundingBox;
    }

    /**
     * Returns the color as a JavaFX-compatible hex string (e.g. "#00FFFF").
     *
     * @return hex color string
     */
    public String getColorHex() {
        return String.format("#%06X", color);
    }

    /**
     * Map from piece type to standard Tetris Guideline spawn column offset.
     * Pieces spawn horizontally centered in the playfield.
     * For a 10-wide field: most pieces at column 3, I at column 3, O at column 3.
     */
    private static final Map<Piece, Integer> SPAWN_COLUMNS = Map.of(
        I, 3, O, 3, T, 3, S, 3, Z, 3, J, 3, L, 3
    );

    /**
     * Returns the column at which this piece spawns (left edge of bounding box).
     * Per Tetris Guideline, pieces are centered in the 10-wide playfield.
     *
     * @return the spawn column offset
     */
    public int getSpawnColumn() {
        return SPAWN_COLUMNS.get(this);
    }

    /**
     * Returns the row at which this piece spawns (top edge of bounding box).
     * Per Tetris Guideline, pieces spawn with their lowest visible row
     * at the top of the visible playfield (row 20 in a 0-indexed 40-row field,
     * where rows 0-19 are visible and rows 20-39 are the buffer zone).
     *
     * @return the spawn row offset (in the 40-row field)
     */
    public int getSpawnRow() {
        return 19;
    }
}
