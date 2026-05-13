package com.tetris.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.tetris.events.GarbageRowPattern;

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

    /**
     * Snapshot of the rows cleared by the most recent {@link #clearLines()}
     * call. Each entry is a defensive copy of one full row, in the same
     * top-to-bottom order they originally appeared on the board. Empty
     * after a {@code clearLines()} that cleared nothing. Used by the view
     * to animate the line-clear effect (flash + shatter particles) using
     * the original cell colors.
     */
    private List<Color[]> lastClearedRowColors = Collections.emptyList();

    /**
     * Y indices of the cleared rows from the most recent {@link #clearLines()}
     * call (in the original grid coordinate system, top-to-bottom).
     */
    private List<Integer> lastClearedRowIndices = Collections.emptyList();

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

        // Snapshot every cleared row first so the view can animate them.
        List<Color[]> clearedColors = new ArrayList<>(4);
        List<Integer> clearedIndices = new ArrayList<>(4);
        for (int row = 0; row < TOTAL_HEIGHT; row++) {
            if (isRowFull(row)) {
                Color[] copy = new Color[WIDTH];
                System.arraycopy(grid[row], 0, copy, 0, WIDTH);
                clearedColors.add(copy);
                clearedIndices.add(row);
            }
        }
        lastClearedRowColors = clearedColors;
        lastClearedRowIndices = clearedIndices;

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
     * Returns true if there are no locked cells anywhere on the board
     * (visible play area or buffer). Used by the score system to detect
     * an "All Clear" / Perfect Clear bonus immediately after line clears.
     */
    public boolean isCompletelyEmpty() {
        for (int y = 0; y < TOTAL_HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (grid[y][x] != null) return false;
            }
        }
        return true;
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

    /**
     * Y indices (in board coords, top = 0) of the rows cleared by the most
     * recent {@link #clearLines()} call. Top-to-bottom order. Empty if no
     * lines were cleared.
     */
    public List<Integer> getLastClearedRowIndices() {
        return lastClearedRowIndices;
    }

    /**
     * Defensive snapshot of the row contents that were just cleared by
     * the most recent {@link #clearLines()} call, in the same order as
     * {@link #getLastClearedRowIndices()}. Each entry is a {@code WIDTH}-
     * wide array of the original cell colors. Empty if no lines were
     * cleared.
     */
    public List<Color[]> getLastClearedRowColors() {
        return lastClearedRowColors;
    }

    // ─────────────────────────── External Integration ────────────
    // The methods below exist so that external systems (e.g. the
    // "Mutually Assured Blocks" strategy layer) can read the board
    // and inject garbage without reaching into the grid directly.

    /**
     * Returns the height of the locked stack, measured from the bottom of
     * the board. 0 means the playfield is empty; {@link #TOTAL_HEIGHT}
     * means the topmost buffer row contains a locked cell.
     */
    public int getStackHeight() {
        for (int y = 0; y < TOTAL_HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (grid[y][x] != null) return TOTAL_HEIGHT - y;
            }
        }
        return 0;
    }

    /**
     * Pushes {@code rows} garbage lines into the bottom of the playfield,
     * shifting the existing stack upward by the same amount. Each garbage
     * row is filled with {@code garbageColor} except for column
     * {@code holeColumn}, which stays empty.
     *
     * @param rows           number of garbage rows to insert (no-op if &le; 0)
     * @param holeColumn     column index for the hole (clamped to [0, WIDTH-1])
     * @param garbageColor   color used to render the garbage cells
     * @return {@code true} if any locked cell was pushed off the top of
     *         the buffer (caller may treat this as a top-out condition)
     */
    public boolean insertGarbageRows(int rows, int holeColumn, Color garbageColor) {
        if (rows <= 0) return false;
        int hole = Math.max(0, Math.min(WIDTH - 1, holeColumn));
        boolean overflow = false;

        for (int i = 0; i < rows; i++) {
            // Detect overflow: anything in the top row will be lost on shift.
            for (int x = 0; x < WIDTH; x++) {
                if (grid[0][x] != null) { overflow = true; break; }
            }
            // Shift everything up by 1 row.
            for (int y = 0; y < TOTAL_HEIGHT - 1; y++) {
                System.arraycopy(grid[y + 1], 0, grid[y], 0, WIDTH);
            }
            // Write the new garbage row at the bottom.
            for (int x = 0; x < WIDTH; x++) {
                grid[TOTAL_HEIGHT - 1][x] = (x == hole) ? null : garbageColor;
            }
        }
        return overflow;
    }

    /**
     * Pushes a sequence of patterned garbage rows into the bottom of the
     * playfield, supporting per-row hole layouts (e.g. radiation /
     * messy garbage with multiple holes per row).
     *
     * <p>The first element of {@code patterns} is inserted first, then
     * shifted up as later rows are inserted — so the LAST element of
     * {@code patterns} ends up on the BOTTOM-most row of the playfield.
     * If a row pattern's {@link GarbageRowPattern#color()} is {@code null},
     * {@code defaultColor} is used for its filled cells. Hole indices
     * outside {@code [0, WIDTH-1]} are silently ignored.
     *
     * @param patterns      rows to insert; null/empty means no-op
     * @param defaultColor  fallback color for rows without an explicit color
     * @return {@code true} if any locked cell was pushed off the top of
     *         the buffer (caller may treat this as a top-out condition)
     */
    public boolean insertGarbageRows(List<GarbageRowPattern> patterns, Color defaultColor) {
        if (patterns == null || patterns.isEmpty()) return false;
        boolean overflow = false;

        for (GarbageRowPattern pattern : patterns) {
            // Detect overflow before shifting: anything in the top row will be lost.
            for (int x = 0; x < WIDTH; x++) {
                if (grid[0][x] != null) { overflow = true; break; }
            }
            // Shift everything up by 1 row.
            for (int y = 0; y < TOTAL_HEIGHT - 1; y++) {
                System.arraycopy(grid[y + 1], 0, grid[y], 0, WIDTH);
            }
            // Compute the per-column "is hole" flag for this row.
            boolean[] hole = new boolean[WIDTH];
            if (pattern != null) {
                for (Integer h : pattern.holeColumns()) {
                    if (h != null && h >= 0 && h < WIDTH) hole[h] = true;
                }
            }
            Color color = (pattern != null && pattern.color() != null) ? pattern.color() : defaultColor;
            for (int x = 0; x < WIDTH; x++) {
                grid[TOTAL_HEIGHT - 1][x] = hole[x] ? null : color;
            }
        }
        return overflow;
    }
}
