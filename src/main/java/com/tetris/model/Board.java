package com.tetris.model;

import java.awt.Color;

/**
 * Board.java
 * ==========
 * Represents the Tetris playfield — a grid of cells that can be empty or
 * filled with a specific color (from a locked piece).
 *
 * ═══════════════════════════════════════════════════════════════════════
 * BOARD DIMENSIONS
 * ═══════════════════════════════════════════════════════════════════════
 * Standard Tetris Guideline:
 *   - Visible area:  10 columns × 20 rows
 *   - Buffer zone:   10 columns × 4 rows above the visible area
 *   - Total:         10 columns × 24 rows (rows 0–3 are buffer/hidden)
 *
 * The buffer rows allow pieces to spawn partially above the visible area.
 * When the buffer rows are occupied and the next piece cannot spawn,
 * the game is over ("block out" / "lock out").
 *
 * ═══════════════════════════════════════════════════════════════════════
 * COORDINATE SYSTEM
 * ═══════════════════════════════════════════════════════════════════════
 *   - Column (x): 0 = leftmost, 9 = rightmost
 *   - Row    (y): 0 = topmost buffer row, 23 = bottom visible row
 *   - Visible rows: 4..23 (indices), displayed as rows 1..20 to the player
 *
 * ═══════════════════════════════════════════════════════════════════════
 * CELL STORAGE
 * ═══════════════════════════════════════════════════════════════════════
 *   grid[y][x] = null  → empty cell
 *   grid[y][x] = Color → filled cell (locked piece fragment)
 *
 * ═══════════════════════════════════════════════════════════════════════
 * LINE CLEARING
 * ═══════════════════════════════════════════════════════════════════════
 * When a row is completely filled, it is cleared:
 *   1. Identify all full rows.
 *   2. Remove them from the grid.
 *   3. Shift all rows above downward to fill the gaps.
 *   4. Insert empty rows at the top.
 *   5. Return the number of lines cleared (used for scoring).
 */
public class Board {

    /** Standard visible width (columns). */
    public static final int WIDTH = 10;

    /** Standard visible height (rows). */
    public static final int VISIBLE_HEIGHT = 20;

    /** Buffer rows above the visible area for piece spawning. */
    public static final int BUFFER_HEIGHT = 4;

    /** Total height including buffer. */
    public static final int TOTAL_HEIGHT = VISIBLE_HEIGHT + BUFFER_HEIGHT;

    /**
     * The grid. grid[row][col].
     * null = empty, non-null = color of the locked piece occupying that cell.
     */
    private final Color[][] grid;

    // ─────────────────────────── Constructor ──────────────────────

    /**
     * Creates an empty board.
     */
    public Board() {
        grid = new Color[TOTAL_HEIGHT][WIDTH];
    }

    // ─────────────────────────── Cell Access ─────────────────────

    /**
     * Returns the color at (x, y), or null if empty.
     *
     * @param x column (0-based)
     * @param y row (0-based, includes buffer)
     * @return the cell color, or null
     */
    public Color getCell(int x, int y) {
        if (!isInBounds(x, y)) return null;
        return grid[y][x];
    }

    /**
     * Sets the color at (x, y). Used when locking a piece.
     *
     * @param x     column
     * @param y     row
     * @param color the color to set (null to clear)
     */
    public void setCell(int x, int y, Color color) {
        if (isInBounds(x, y)) {
            grid[y][x] = color;
        }
    }

    /**
     * Checks if the cell at (x, y) is empty (null).
     */
    public boolean isEmpty(int x, int y) {
        return isInBounds(x, y) && grid[y][x] == null;
    }

    // ─────────────────────────── Bounds ──────────────────────────

    /**
     * Checks if (x, y) is within the board boundaries.
     */
    public boolean isInBounds(int x, int y) {
        return x >= 0 && x < WIDTH && y >= 0 && y < TOTAL_HEIGHT;
    }

    // ─────────────────────────── Collision Detection ─────────────

    /**
     * Checks if a tetromino can occupy its current position without
     * overlapping walls or locked cells.
     *
     * Tests each of the tetromino's 4 absolute cell positions:
     *   - Must be within board bounds
     *   - Must not overlap an existing (non-null) cell
     *
     * @param tetromino the piece to test
     * @return true if the position is valid (no collision)
     */
    public boolean isValidPosition(Tetromino tetromino) {
        for (Position cell : tetromino.getAbsoluteCells()) {
            int x = cell.getX();
            int y = cell.getY();
            if (!isInBounds(x, y)) return false;
            if (grid[y][x] != null) return false;
        }
        return true;
    }

    // ─────────────────────────── Locking ─────────────────────────

    /**
     * Locks a tetromino onto the board by writing its color into the grid
     * at each of its 4 cell positions.
     *
     * After calling this, the piece becomes part of the board and the
     * Tetromino object is no longer needed.
     *
     * @param tetromino the piece to lock
     */
    public void lockPiece(Tetromino tetromino) {
        Color color = tetromino.getType().getColor();
        for (Position cell : tetromino.getAbsoluteCells()) {
            setCell(cell.getX(), cell.getY(), color);
        }
    }

    // ─────────────────────────── Line Clearing ───────────────────

    /**
     * Clears all completed lines and returns the count.
     *
     * Algorithm:
     *   1. Scan from bottom to top.
     *   2. For each complete row, mark it.
     *   3. Compact: shift non-complete rows down.
     *   4. Fill newly exposed top rows with nulls (empty).
     *
     * @return number of lines cleared (0–4)
     */
    public int clearLines() {
        int linesCleared = 0;

        // writeRow is where the next non-cleared row will be placed (bottom-up)
        int writeRow = TOTAL_HEIGHT - 1;

        // Scan from bottom to top
        for (int readRow = TOTAL_HEIGHT - 1; readRow >= 0; readRow--) {
            if (!isRowFull(readRow)) {
                // Copy this row to the write position
                if (writeRow != readRow) {
                    System.arraycopy(grid[readRow], 0, grid[writeRow], 0, WIDTH);
                }
                writeRow--;
            } else {
                linesCleared++;
            }
        }

        // Fill the remaining top rows with empty cells
        for (int row = writeRow; row >= 0; row--) {
            for (int col = 0; col < WIDTH; col++) {
                grid[row][col] = null;
            }
        }

        return linesCleared;
    }

    /**
     * Checks if a row is completely filled (no null cells).
     *
     * @param row the row index to check
     * @return true if every cell in the row is non-null
     */
    public boolean isRowFull(int row) {
        for (int col = 0; col < WIDTH; col++) {
            if (grid[row][col] == null) return false;
        }
        return true;
    }

    // ─────────────────────────── Ghost Piece ─────────────────────

    /**
     * Computes the ghost (shadow) piece position — the lowest valid position
     * directly below the current piece.
     *
     * The ghost piece shows where the current piece will land if hard-dropped.
     *
     * Algorithm: move the piece down one row at a time until it's no longer valid,
     * then return the last valid position.
     *
     * @param tetromino the current active piece
     * @return a new Tetromino at the ghost position
     */
    public Tetromino getGhostPosition(Tetromino tetromino) {
        Tetromino ghost = tetromino;
        while (true) {
            Tetromino next = ghost.moveDown();
            if (!isValidPosition(next)) {
                return ghost;
            }
            ghost = next;
        }
    }

    // ─────────────────────────── Queries ─────────────────────────

    /**
     * Returns true if any buffer row (y < BUFFER_HEIGHT) contains a locked cell.
     * This is one condition for game over ("lock out").
     */
    public boolean hasBlocksInBufferZone() {
        for (int y = 0; y < BUFFER_HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (grid[y][x] != null) return true;
            }
        }
        return false;
    }

    /**
     * Returns a deep copy of the grid for read-only rendering.
     */
    public Color[][] getGridCopy() {
        Color[][] copy = new Color[TOTAL_HEIGHT][WIDTH];
        for (int y = 0; y < TOTAL_HEIGHT; y++) {
            System.arraycopy(grid[y], 0, copy[y], 0, WIDTH);
        }
        return copy;
    }
}
