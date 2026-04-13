package com.tetris.model;

/**
 * Position.java
 * =============
 * Represents an (x, y) coordinate on the Tetris board or within a tetromino's local space.
 *
 * COORDINATE SYSTEM:
 *   - x: column index (0 = leftmost, increases rightward)
 *   - y: row index (0 = topmost / ceiling, increases downward)
 *
 * This is an immutable value object. Every mutation returns a new Position.
 *
 * Used for:
 *   - Storing the position of a tetromino's pivot on the board
 *   - Representing individual cell offsets within a tetromino shape
 *   - Wall kick offset calculations
 */
public class Position {

    /** Column index (0-based, left to right). */
    private final int x;

    /** Row index (0-based, top to bottom). */
    private final int y;

    /**
     * Creates a new Position.
     *
     * @param x column index
     * @param y row index
     */
    public Position(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    /**
     * Returns a new Position offset by (dx, dy).
     *
     * @param dx horizontal offset (positive = right)
     * @param dy vertical offset   (positive = down)
     * @return new translated Position
     */
    public Position translate(int dx, int dy) {
        return new Position(x + dx, y + dy);
    }

    /**
     * Adds another Position's coordinates to this one (vector addition).
     *
     * @param other the other position
     * @return new Position representing the sum
     */
    public Position add(Position other) {
        return new Position(x + other.x, y + other.y);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Position other)) return false;
        return x == other.x && y == other.y;
    }

    @Override
    public int hashCode() {
        return 31 * x + y;
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}
