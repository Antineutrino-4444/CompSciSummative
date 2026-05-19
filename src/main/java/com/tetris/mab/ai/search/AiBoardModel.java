package com.tetris.mab.ai.search;

import com.tetris.model.Board;
import com.tetris.model.Position;
import com.tetris.model.Tetromino;
import com.tetris.model.TetrominoType;

import java.awt.Color;

/**
 * Internal mutable bitboard used by the AI's search.
 *
 * <p>Mirrors the playable area of {@link com.tetris.model.Board} (10 columns
 * × 24 rows including the 4-row buffer) as a primitive {@code int[][]} where
 * non-zero entries are filled cells.
 *
 * <p>The model is deliberately self-contained: nothing here mutates the
 * real {@link com.tetris.model.Board}. The AI uses it to enumerate legal
 * placements without touching the game state.
 */
public final class AiBoardModel {

    public static final int WIDTH = Board.WIDTH;
    public static final int VISIBLE_HEIGHT = Board.VISIBLE_HEIGHT;
    public static final int BUFFER_HEIGHT = Board.BUFFER_HEIGHT;
    public static final int TOTAL_HEIGHT = Board.TOTAL_HEIGHT;

    private final int[][] grid;

    public AiBoardModel() {
        this.grid = new int[TOTAL_HEIGHT][WIDTH];
    }

    public AiBoardModel(int[][] src) {
        this();
        for (int y = 0; y < TOTAL_HEIGHT; y++) {
            System.arraycopy(src[y], 0, grid[y], 0, WIDTH);
        }
    }

    /** Copy of the internal grid. */
    public int[][] snapshot() {
        int[][] copy = new int[TOTAL_HEIGHT][];
        for (int y = 0; y < TOTAL_HEIGHT; y++) copy[y] = grid[y].clone();
        return copy;
    }

    public AiBoardModel deepCopy() { return new AiBoardModel(grid); }

    public int cell(int x, int y) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= TOTAL_HEIGHT) return 1;
        return grid[y][x];
    }

    public boolean isEmpty(int x, int y) {
        if (x < 0 || x >= WIDTH) return false;
        if (y >= TOTAL_HEIGHT) return false;
        if (y < 0) return true;
        return grid[y][x] == 0;
    }

    public void setCell(int x, int y, int v) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= TOTAL_HEIGHT) return;
        grid[y][x] = v;
    }

    /** Loads from the real {@link Board}. Treats any non-null cell as filled. */
    public static AiBoardModel fromBoard(Board board) {
        AiBoardModel m = new AiBoardModel();
        for (int y = 0; y < TOTAL_HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                Color c = board.getCell(x, y);
                m.grid[y][x] = (c == null) ? 0 : 1;
            }
        }
        return m;
    }

    public boolean fits(Tetromino piece) {
        for (Position c : piece.getAbsoluteCells()) {
            int x = c.getX(), y = c.getY();
            if (x < 0 || x >= WIDTH || y >= TOTAL_HEIGHT) return false;
            if (y < 0) continue;
            if (grid[y][x] != 0) return false;
        }
        return true;
    }

    /** Returns piece settled at its drop-resting position, or null if the spawn already collides. */
    public Tetromino drop(Tetromino piece) {
        if (!fits(piece)) return null;
        Tetromino cur = piece;
        while (true) {
            Tetromino next = cur.moveDown();
            if (!fits(next)) return cur;
            cur = next;
        }
    }

    /** Locks the piece into the grid in place. */
    public void lock(Tetromino piece) {
        for (Position c : piece.getAbsoluteCells()) {
            int x = c.getX(), y = c.getY();
            if (x >= 0 && x < WIDTH && y >= 0 && y < TOTAL_HEIGHT) {
                grid[y][x] = 1;
            }
        }
    }

    /** Returns the number of cleared lines (and shifts rows down). */
    public int clearLines() {
        int cleared = 0;
        for (int y = TOTAL_HEIGHT - 1; y >= 0; y--) {
            boolean full = true;
            for (int x = 0; x < WIDTH; x++) {
                if (grid[y][x] == 0) { full = false; break; }
            }
            if (full) {
                cleared++;
                for (int yy = y; yy > 0; yy--) {
                    System.arraycopy(grid[yy - 1], 0, grid[yy], 0, WIDTH);
                }
                java.util.Arrays.fill(grid[0], 0);
                y++;
            }
        }
        return cleared;
    }

    public boolean topRowsClear(int rows) {
        for (int y = 0; y < rows && y < TOTAL_HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (grid[y][x] != 0) return false;
            }
        }
        return true;
    }

    /** Used by the T-spin 3-corner rule. */
    public boolean isOccupiedOrWall(int x, int y) {
        if (x < 0 || x >= WIDTH) return true;
        if (y >= TOTAL_HEIGHT) return true;
        if (y < 0) return false;
        return grid[y][x] != 0;
    }

    public int[] columnHeights() {
        int[] h = new int[WIDTH];
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < TOTAL_HEIGHT; y++) {
                if (grid[y][x] != 0) { h[x] = TOTAL_HEIGHT - y; break; }
            }
        }
        return h;
    }

    /** Spawn a fresh piece of the given type centered at the top. */
    public static Tetromino spawn(TetrominoType type) {
        return Tetromino.spawn(type, WIDTH);
    }
}
