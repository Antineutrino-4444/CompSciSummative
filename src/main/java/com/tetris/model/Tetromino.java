package com.tetris.model;

/**
 * Tetromino.java
 * ==============
 * Represents a single active tetromino piece on the board.
 *
 * A Tetromino has:
 *   - type:          which of the 7 pieces it is (I, O, T, S, Z, J, L)
 *   - boardPosition: the (x, y) position of the bounding-box top-left corner on the board
 *   - rotationState: current rotation (0 = spawn, 1 = CW, 2 = 180°, 3 = CCW)
 *
 * IMMUTABILITY:
 *   Tetromino instances are immutable. All movement/rotation methods return
 *   a NEW Tetromino with the updated state. This makes it safe to test
 *   hypothetical positions (e.g., for wall kicks) without mutating the original.
 *
 * ABSOLUTE CELL POSITIONS:
 *   Call getAbsoluteCells() to get the 4 board-space coordinates of the
 *   filled cells, computed as: boardPosition + each cell offset from the
 *   rotation state.
 *
 * SPAWN POSITION:
 *   Per Tetris Guideline, pieces spawn at row 0 (above visible area if
 *   the board has buffer rows), centered horizontally:
 *     - 10-wide board: x = 3 for all pieces
 */
public class Tetromino {

    /** Which piece this is. */
    private final TetrominoType type;

    /**
     * Top-left corner of the bounding box on the board.
     * This is NOT the pivot — it's the offset applied to local cell coordinates.
     */
    private final Position boardPosition;

    /** Current rotation state index (0–3). */
    private final int rotationState;

    // ─────────────────────────── Constructor ──────────────────────

    /**
     * Creates a Tetromino with explicit state.
     *
     * @param type          the piece type
     * @param boardPosition top-left of bounding box on board
     * @param rotationState rotation index (0–3)
     */
    public Tetromino(TetrominoType type, Position boardPosition, int rotationState) {
        this.type = type;
        this.boardPosition = boardPosition;
        this.rotationState = rotationState;
    }

    // ─────────────────────────── Factory ─────────────────────────

    /**
     * Creates a new Tetromino in its spawn position.
     *
     * Spawn rules (Tetris Guideline):
     *   - Rotation state 0
     *   - Centered horizontally: column 3 on a 10-wide board
     *   - Row 0 (may be in the buffer zone above the visible playfield)
     *
     * @param type       the piece type to spawn
     * @param boardWidth the width of the board (typically 10)
     * @return a new Tetromino at spawn position
     */
    public static Tetromino spawn(TetrominoType type, int boardWidth) {
        // Center the bounding box: (boardWidth - boundingBoxSize) / 2
        // For a 10-wide board with 4-wide box: (10-4)/2 = 3
        // For a 10-wide board with 3-wide box: (10-3)/2 = 3 (integer division)
        int spawnX = (boardWidth - type.getBoundingBoxSize()) / 2;
        int spawnY = 0;
        return new Tetromino(type, new Position(spawnX, spawnY), 0);
    }

    // ─────────────────────────── Accessors ───────────────────────

    public TetrominoType getType() {
        return type;
    }

    public Position getBoardPosition() {
        return boardPosition;
    }

    public int getRotationState() {
        return rotationState;
    }

    // ─────────────────────────── Cell Queries ────────────────────

    /**
     * Returns the 4 absolute board positions of this piece's filled cells.
     *
     * Computed as: boardPosition + cellOffset for each cell in the current
     * rotation state.
     *
     * @return array of 4 Positions in board coordinates
     */
    public Position[] getAbsoluteCells() {
        Position[] localCells = type.getCells(rotationState);
        Position[] absoluteCells = new Position[localCells.length];
        for (int i = 0; i < localCells.length; i++) {
            absoluteCells[i] = boardPosition.add(localCells[i]);
        }
        return absoluteCells;
    }

    // ─────────────────────────── Movement ────────────────────────

    /**
     * Returns a new Tetromino moved left by 1 column.
     */
    public Tetromino moveLeft() {
        return new Tetromino(type, boardPosition.translate(-1, 0), rotationState);
    }

    /**
     * Returns a new Tetromino moved right by 1 column.
     */
    public Tetromino moveRight() {
        return new Tetromino(type, boardPosition.translate(1, 0), rotationState);
    }

    /**
     * Returns a new Tetromino moved down by 1 row (soft drop / gravity).
     */
    public Tetromino moveDown() {
        return new Tetromino(type, boardPosition.translate(0, 1), rotationState);
    }

    /**
     * Returns a new Tetromino translated by an arbitrary offset.
     * Used for wall kick adjustments.
     *
     * @param dx horizontal offset
     * @param dy vertical offset
     * @return new Tetromino at the offset position
     */
    public Tetromino translate(int dx, int dy) {
        return new Tetromino(type, boardPosition.translate(dx, dy), rotationState);
    }

    // ─────────────────────────── Rotation ────────────────────────

    /**
     * Returns a new Tetromino rotated 90° clockwise.
     * Does NOT apply wall kicks — the caller must handle that.
     */
    public Tetromino rotateCW() {
        int newState = (rotationState + 1) % 4;
        return new Tetromino(type, boardPosition, newState);
    }

    /**
     * Returns a new Tetromino rotated 90° counter-clockwise.
     * Does NOT apply wall kicks — the caller must handle that.
     */
    public Tetromino rotateCCW() {
        int newState = (rotationState + 3) % 4;   // +3 mod 4 == −1 mod 4
        return new Tetromino(type, boardPosition, newState);
    }

    /**
     * Returns a new Tetromino rotated 180°.
     */
    public Tetromino rotate180() {
        int newState = (rotationState + 2) % 4;
        return new Tetromino(type, boardPosition, newState);
    }

    /**
     * Returns a new Tetromino with a specific rotation state.
     * Used internally for wall kick testing.
     *
     * @param state the target rotation state (0–3)
     */
    public Tetromino withRotation(int state) {
        return new Tetromino(type, boardPosition, state);
    }

    @Override
    public String toString() {
        return type.name() + " at " + boardPosition + " rot=" + rotationState;
    }
}
