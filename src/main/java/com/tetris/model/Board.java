package com.tetris.model;

/**
 * Manages the Tetris playfield (board/matrix).
 *
 * <p>The board is a 10-column × 40-row grid as specified by the Tetris Guideline.
 * Rows 0-19 are the visible playfield (row 0 = bottom). Rows 20-39 are the
 * buffer/vanish zone above the visible area where pieces spawn.</p>
 *
 * <p>Each cell is either empty (null) or contains a {@link Piece} reference
 * indicating which piece type occupies that cell (used for coloring).</p>
 *
 * <h3>Coordinate System</h3>
 * <ul>
 *   <li>Column (x): 0 = leftmost, 9 = rightmost</li>
 *   <li>Row (y): 0 = bottom, 39 = top</li>
 * </ul>
 */
public class Board {

    /** Standard playfield width (columns). */
    public static final int WIDTH = 10;

    /** Total playfield height including buffer zone (rows). */
    public static final int HEIGHT = 40;

    /** Number of visible rows (bottom portion of the playfield). */
    public static final int VISIBLE_HEIGHT = 20;

    /**
     * The grid of cells. {@code grid[row][col]} where row 0 is the bottom.
     * A null value means the cell is empty.
     */
    private final Piece[][] grid;

    /**
     * Creates a new empty board.
     */
    public Board() {
        grid = new Piece[HEIGHT][WIDTH];
    }

    /**
     * Creates a deep copy of this board.
     *
     * @return a new Board with the same cell contents
     */
    public Board copy() {
        Board copy = new Board();
        for (int r = 0; r < HEIGHT; r++) {
            System.arraycopy(grid[r], 0, copy.grid[r], 0, WIDTH);
        }
        return copy;
    }

    /**
     * Gets the piece type at the specified cell.
     *
     * @param col the column (0-9)
     * @param row the row (0 = bottom, 39 = top)
     * @return the Piece occupying the cell, or null if empty
     */
    public Piece getCell(int col, int row) {
        if (col < 0 || col >= WIDTH || row < 0 || row >= HEIGHT) {
            return null;
        }
        return grid[row][col];
    }

    /**
     * Sets a cell to the specified piece type.
     *
     * @param col   the column (0-9)
     * @param row   the row (0 = bottom, 39 = top)
     * @param piece the piece type to place, or null to clear the cell
     */
    public void setCell(int col, int row, Piece piece) {
        if (col >= 0 && col < WIDTH && row >= 0 && row < HEIGHT) {
            grid[row][col] = piece;
        }
    }

    /**
     * Checks whether a cell is empty (unoccupied).
     *
     * @param col the column
     * @param row the row
     * @return true if the cell is within bounds and empty
     */
    public boolean isEmpty(int col, int row) {
        if (col < 0 || col >= WIDTH || row < 0 || row >= HEIGHT) {
            return false;
        }
        return grid[row][col] == null;
    }

    /**
     * Checks whether a piece at the given position would collide with
     * existing blocks or the walls.
     *
     * @param piece    the piece type
     * @param rotation the rotation state (0-3)
     * @param col      the column of the bounding box's left edge
     * @param row      the row of the bounding box's bottom edge
     * @return true if any cell of the piece overlaps with a wall or existing block
     */
    public boolean collides(Piece piece, int rotation, int col, int row) {
        int[][] cells = piece.getCells(rotation);
        for (int[] cell : cells) {
            int cx = col + cell[0];
            int cy = row - cell[1]; // cell[1] is offset from top of bounding box, so subtract
            if (cx < 0 || cx >= WIDTH || cy < 0 || cy >= HEIGHT) {
                return true;
            }
            if (grid[cy][cx] != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Places a piece on the board permanently (locks it down).
     *
     * @param piece    the piece type
     * @param rotation the rotation state
     * @param col      the column of the bounding box's left edge
     * @param row      the row of the bounding box's bottom edge
     */
    public void placePiece(Piece piece, int rotation, int col, int row) {
        int[][] cells = piece.getCells(rotation);
        for (int[] cell : cells) {
            int cx = col + cell[0];
            int cy = row - cell[1];
            if (cx >= 0 && cx < WIDTH && cy >= 0 && cy < HEIGHT) {
                grid[cy][cx] = piece;
            }
        }
    }

    /**
     * Clears all completed (full) lines and returns the number cleared.
     *
     * <p>Lines above cleared rows drop down to fill the gaps. This is the
     * "naive gravity" line clear method used in standard Tetris.</p>
     *
     * @return the number of lines cleared (0-4)
     */
    public int clearLines() {
        int linesCleared = 0;
        int writeRow = 0;

        for (int readRow = 0; readRow < HEIGHT; readRow++) {
            if (isLineFull(readRow)) {
                linesCleared++;
            } else {
                if (writeRow != readRow) {
                    System.arraycopy(grid[readRow], 0, grid[writeRow], 0, WIDTH);
                }
                writeRow++;
            }
        }

        // Clear the top rows that are now empty
        for (int r = writeRow; r < HEIGHT; r++) {
            for (int c = 0; c < WIDTH; c++) {
                grid[r][c] = null;
            }
        }

        return linesCleared;
    }

    /**
     * Checks whether a row is completely filled (all cells occupied).
     *
     * @param row the row to check
     * @return true if every cell in the row is non-null
     */
    public boolean isLineFull(int row) {
        if (row < 0 || row >= HEIGHT) return false;
        for (int c = 0; c < WIDTH; c++) {
            if (grid[row][c] == null) return false;
        }
        return true;
    }

    /**
     * Checks whether the board is completely empty (a "perfect clear").
     *
     * @return true if every cell on the board is empty
     */
    public boolean isPerfectClear() {
        for (int r = 0; r < HEIGHT; r++) {
            for (int c = 0; c < WIDTH; c++) {
                if (grid[r][c] != null) return false;
            }
        }
        return true;
    }

    /**
     * Checks if any block exists above the visible playfield,
     * indicating a potential top-out condition.
     *
     * @return true if any cell in the buffer zone (rows 20-39) is occupied
     */
    public boolean hasBlocksAboveVisible() {
        for (int r = VISIBLE_HEIGHT; r < HEIGHT; r++) {
            for (int c = 0; c < WIDTH; c++) {
                if (grid[r][c] != null) return true;
            }
        }
        return false;
    }

    /**
     * Returns the highest occupied row on the board.
     *
     * @return the highest row with a block, or -1 if the board is empty
     */
    public int getHighestRow() {
        for (int r = HEIGHT - 1; r >= 0; r--) {
            for (int c = 0; c < WIDTH; c++) {
                if (grid[r][c] != null) return r;
            }
        }
        return -1;
    }

    /**
     * Provides read-only access to the internal grid for rendering.
     *
     * @return the grid array (do not modify)
     */
    public Piece[][] getGrid() {
        return grid;
    }
}
