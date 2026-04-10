package com.tetris.model;

import java.util.List;

/**
 * The core game state and logic engine for modern Tetris.
 *
 * <p>This class manages all aspects of the Tetris game loop including:</p>
 * <ul>
 *   <li>Piece spawning, movement, rotation, and locking</li>
 *   <li>Gravity and soft/hard drop mechanics</li>
 *   <li>DAS (Delayed Auto-Shift) and ARR (Auto-Repeat Rate)</li>
 *   <li>Lock delay with move/rotation reset</li>
 *   <li>Hold piece functionality</li>
 *   <li>Ghost piece calculation</li>
 *   <li>T-Spin detection</li>
 *   <li>Line clearing and scoring integration</li>
 *   <li>Game over detection</li>
 * </ul>
 *
 * <h3>Timing Constants (Tetris Guideline)</h3>
 * <ul>
 *   <li>Lock delay: 500ms (piece locks after sitting on surface)</li>
 *   <li>Max lock resets: 15 (moves/rotations that restart lock timer)</li>
 *   <li>DAS: 167ms (delay before auto-repeat starts)</li>
 *   <li>ARR: 33ms (speed of auto-repeat movement)</li>
 * </ul>
 */
public class GameState {

    // --- Timing constants (in seconds) ---

    /** Lock delay in seconds. Piece locks after resting on a surface this long. */
    public static final double LOCK_DELAY = 0.5;

    /** Maximum number of times lock delay can be reset by movement/rotation. */
    public static final int MAX_LOCK_RESETS = 15;

    /** Delayed Auto-Shift: initial delay before auto-repeat (seconds). */
    public static final double DAS = 0.167;

    /** Auto-Repeat Rate: interval between repeated movements (seconds). */
    public static final double ARR = 0.033;

    /** Number of preview pieces shown. */
    public static final int PREVIEW_COUNT = 5;

    // --- Game components ---

    private final Board board;
    private final BagRandomizer bag;
    private final ScoreSystem scoring;

    // --- Current piece state ---

    /** The currently active (falling) piece, or null if none. */
    private Piece currentPiece;

    /** Current rotation state of the active piece (0-3). */
    private int currentRotation;

    /** Column of the active piece's bounding box left edge. */
    private int currentCol;

    /** Row of the active piece's bounding box (using Board coordinates, 0=bottom). */
    private int currentRow;

    // --- Hold piece ---

    /** The piece currently in hold, or null if empty. */
    private Piece holdPiece;

    /** Whether hold has been used for the current piece (can only hold once per piece). */
    private boolean holdUsed;

    // --- Lock delay state ---

    /** Timer for lock delay (counts up when piece is on surface). */
    private double lockTimer;

    /** Whether the piece is currently on a surface (eligible for locking). */
    private boolean onSurface;

    /** Number of times the lock timer has been reset for the current piece. */
    private int lockResets;

    /** The lowest row the current piece has reached (for lock reset tracking). */
    private int lowestRow;

    // --- Gravity ---

    /** Gravity accumulator (counts up to trigger downward movement). */
    private double gravityAccumulator;

    // --- DAS/ARR state ---

    /** Current DAS direction: -1 = left, 0 = none, 1 = right. */
    private int dasDirection;

    /** DAS timer (counts up from 0 to DAS threshold). */
    private double dasTimer;

    /** ARR timer (counts up from 0 to ARR threshold). */
    private double arrTimer;

    /** Whether DAS has charged (initial delay passed). */
    private boolean dasCharged;

    // --- Game state flags ---

    /** Whether the game is over. */
    private boolean gameOver;

    /** Whether the game is paused. */
    private boolean paused;

    /** Whether the last rotation was a T-Spin. */
    private boolean lastMoveWasRotation;

    /** The last wall kick index used (for T-Spin mini detection). */
    private int lastKickIndex;

    /** Lines cleared in the most recent lock. */
    private int lastLinesCleared;

    /** Whether to show the action text (e.g. "Tetris", "T-Spin Double"). */
    private String actionText = "";

    /** Timer for how long to show the action text. */
    private double actionTextTimer;

    /**
     * Creates a new game with default settings (level 1, 5 preview pieces).
     */
    public GameState() {
        this(1);
    }

    /**
     * Creates a new game starting at the specified level.
     *
     * @param startLevel the starting level (1+)
     */
    public GameState(int startLevel) {
        this.board = new Board();
        this.bag = new BagRandomizer(PREVIEW_COUNT);
        this.scoring = new ScoreSystem(startLevel);
        this.gameOver = false;
        this.paused = false;
        this.holdPiece = null;
        this.holdUsed = false;
        this.dasDirection = 0;
        this.dasTimer = 0;
        this.arrTimer = 0;
        this.dasCharged = false;

        spawnNextPiece();
    }

    // ==================== PIECE SPAWNING ====================

    /**
     * Spawns the next piece from the bag randomizer.
     *
     * <p>Per Tetris Guideline, the piece spawns horizontally centered at the
     * top of the visible playfield. If the piece immediately collides upon
     * spawning, the game is over (block out).</p>
     *
     * <p>Note: {@code holdUsed} is NOT reset here. It is reset in
     * {@link #lockPiece()} when a new piece spawns after locking, but not
     * when spawning from {@link #hold()} (since hold was already used).</p>
     */
    private void spawnNextPiece() {
        currentPiece = bag.next();
        currentRotation = 0;
        currentCol = currentPiece.getSpawnColumn();
        currentRow = currentPiece.getSpawnRow();
        lockTimer = 0;
        lockResets = 0;
        onSurface = false;
        lastMoveWasRotation = false;
        lastKickIndex = -1;
        gravityAccumulator = 0;

        // Check for block out (immediate collision on spawn)
        if (board.collides(currentPiece, currentRotation, currentCol, currentRow)) {
            // Try one row higher
            currentRow++;
            if (board.collides(currentPiece, currentRotation, currentCol, currentRow)) {
                gameOver = true;
            }
        }

        // Calculate lowest row for lock reset tracking
        lowestRow = getGhostRow();
    }

    // ==================== MOVEMENT ====================

    /**
     * Attempts to move the current piece left.
     *
     * @return true if the move succeeded
     */
    public boolean moveLeft() {
        if (gameOver || paused || currentPiece == null) return false;
        if (!board.collides(currentPiece, currentRotation, currentCol - 1, currentRow)) {
            currentCol--;
            lastMoveWasRotation = false;
            onLockReset();
            return true;
        }
        return false;
    }

    /**
     * Attempts to move the current piece right.
     *
     * @return true if the move succeeded
     */
    public boolean moveRight() {
        if (gameOver || paused || currentPiece == null) return false;
        if (!board.collides(currentPiece, currentRotation, currentCol + 1, currentRow)) {
            currentCol++;
            lastMoveWasRotation = false;
            onLockReset();
            return true;
        }
        return false;
    }

    /**
     * Performs a soft drop (moves piece down one row).
     * Awards 1 point per cell.
     *
     * @return true if the piece moved down
     */
    public boolean softDrop() {
        if (gameOver || paused || currentPiece == null) return false;
        if (!board.collides(currentPiece, currentRotation, currentCol, currentRow - 1)) {
            currentRow--;
            scoring.onSoftDrop(1);
            gravityAccumulator = 0;
            lastMoveWasRotation = false;
            return true;
        }
        return false;
    }

    /**
     * Performs a hard drop (instantly drops piece to lowest valid position).
     * Awards 2 points per cell dropped. Locks the piece immediately.
     */
    public void hardDrop() {
        if (gameOver || paused || currentPiece == null) return;
        int dropDistance = 0;
        while (!board.collides(currentPiece, currentRotation, currentCol, currentRow - 1)) {
            currentRow--;
            dropDistance++;
        }
        scoring.onHardDrop(dropDistance);
        lockPiece();
    }

    // ==================== ROTATION (SRS) ====================

    /**
     * Attempts to rotate the piece clockwise using SRS wall kicks.
     *
     * @return true if rotation succeeded (possibly with a wall kick)
     */
    public boolean rotateCW() {
        return rotate((currentRotation + 1) & 3);
    }

    /**
     * Attempts to rotate the piece counter-clockwise using SRS wall kicks.
     *
     * @return true if rotation succeeded (possibly with a wall kick)
     */
    public boolean rotateCCW() {
        return rotate((currentRotation + 3) & 3);
    }

    /**
     * Attempts to rotate the piece 180° using SRS-like kicks.
     *
     * @return true if rotation succeeded
     */
    public boolean rotate180() {
        return rotate((currentRotation + 2) & 3);
    }

    /**
     * Internal rotation logic with SRS wall kick testing.
     *
     * <p>Tries each wall kick offset in order. If the rotated piece with the
     * applied offset doesn't collide, the rotation succeeds.</p>
     *
     * @param newRotation the target rotation state
     * @return true if rotation succeeded
     */
    private boolean rotate(int newRotation) {
        if (gameOver || paused || currentPiece == null) return false;
        if (currentPiece == Piece.O) return false; // O doesn't rotate

        int[][] kicks = WallKickData.getKicks(currentPiece, currentRotation, newRotation);
        if (kicks == null) return false;

        for (int i = 0; i < kicks.length; i++) {
            int testCol = currentCol + kicks[i][0];
            int testRow = currentRow + kicks[i][1];
            if (!board.collides(currentPiece, newRotation, testCol, testRow)) {
                currentCol = testCol;
                currentRow = testRow;
                currentRotation = newRotation;
                lastMoveWasRotation = true;
                lastKickIndex = i;
                onLockReset();
                return true;
            }
        }
        return false;
    }

    // ==================== HOLD ====================

    /**
     * Swaps the current piece with the hold piece.
     *
     * <p>Per Tetris Guideline, hold can only be used once per piece. If hold
     * is empty, the current piece goes to hold and a new piece is spawned
     * from the bag.</p>
     */
    public void hold() {
        if (gameOver || paused || currentPiece == null || holdUsed) return;

        holdUsed = true;
        Piece temp = holdPiece;
        holdPiece = currentPiece;

        if (temp != null) {
            // Swap with existing hold piece
            currentPiece = temp;
            currentRotation = 0;
            currentCol = currentPiece.getSpawnColumn();
            currentRow = currentPiece.getSpawnRow();
            lockTimer = 0;
            lockResets = 0;
            onSurface = false;
            lastMoveWasRotation = false;
            gravityAccumulator = 0;
            lowestRow = getGhostRow();

            if (board.collides(currentPiece, currentRotation, currentCol, currentRow)) {
                gameOver = true;
            }
        } else {
            // Hold was empty, spawn new piece
            spawnNextPiece();
        }
    }

    // ==================== LOCK DELAY ====================

    /**
     * Handles lock delay reset when a successful move or rotation occurs.
     *
     * <p>Per Tetris Guideline, the lock timer resets when the piece moves or
     * rotates while on a surface, up to {@link #MAX_LOCK_RESETS} times.
     * Additionally, if the piece reaches a new lowest row, the reset counter
     * itself resets.</p>
     */
    private void onLockReset() {
        if (onSurface && lockResets < MAX_LOCK_RESETS) {
            lockTimer = 0;
            lockResets++;
        }
        // If piece reached a new lowest position, reset the counter
        int ghostRow = getGhostRow();
        if (ghostRow < lowestRow) {
            lowestRow = ghostRow;
            lockResets = 0;
            lockTimer = 0;
        }
    }

    // ==================== PIECE LOCKING ====================

    /**
     * Locks the current piece to the board and processes the result.
     *
     * <p>This handles:</p>
     * <ol>
     *   <li>Placing the piece cells on the board</li>
     *   <li>T-Spin detection</li>
     *   <li>Line clear checking</li>
     *   <li>Score calculation</li>
     *   <li>Perfect clear detection</li>
     *   <li>Lock out detection (game over if piece locks entirely above visible area)</li>
     *   <li>Spawning the next piece</li>
     * </ol>
     */
    private void lockPiece() {
        if (currentPiece == null) return;

        // Check for T-Spin before placing
        boolean isTSpin = false;
        boolean isMiniTSpin = false;
        if (currentPiece == Piece.T && lastMoveWasRotation) {
            isTSpin = detectTSpin();
            if (isTSpin && lastKickIndex != 4) {
                // Could be a mini T-Spin if not kick test 5
                isMiniTSpin = detectMiniTSpin();
                if (isMiniTSpin) {
                    isTSpin = false; // It's a mini, not a full T-Spin
                }
            }
        }

        // Place the piece on the board
        board.placePiece(currentPiece, currentRotation, currentCol, currentRow);

        // Check for lock out (piece entirely above visible area)
        boolean allAboveVisible = true;
        int[][] cells = currentPiece.getCells(currentRotation);
        for (int[] cell : cells) {
            int cy = currentRow - cell[1];
            if (cy < Board.VISIBLE_HEIGHT) {
                allAboveVisible = false;
                break;
            }
        }
        if (allAboveVisible) {
            gameOver = true;
            return;
        }

        // Clear lines
        int linesCleared = board.clearLines();
        lastLinesCleared = linesCleared;
        boolean isPerfectClear = board.isPerfectClear();

        // Update scoring
        if (linesCleared > 0 || isTSpin || isMiniTSpin) {
            scoring.onLineClear(linesCleared, isTSpin, isMiniTSpin, isPerfectClear);
            actionText = scoring.getLastAction();
            actionTextTimer = 2.0; // Show for 2 seconds
        } else {
            scoring.onPieceLockNoLines();
        }

        // Spawn next piece
        spawnNextPiece();
        holdUsed = false; // Reset hold availability for the new piece
    }

    // ==================== T-SPIN DETECTION ====================

    /**
     * Detects a T-Spin using the 3-corner rule.
     *
     * <p>A T-Spin occurs when:</p>
     * <ol>
     *   <li>The last move was a rotation</li>
     *   <li>The piece is a T-piece</li>
     *   <li>At least 3 of the 4 corners of the T-piece's 3×3 bounding box
     *       are occupied (by walls or existing blocks)</li>
     * </ol>
     *
     * @return true if a T-Spin is detected
     */
    private boolean detectTSpin() {
        // Check 4 corners of the T-piece's 3×3 bounding box
        int corners = 0;
        int[][] cornerPositions = {
            {currentCol, currentRow},
            {currentCol + 2, currentRow},
            {currentCol, currentRow - 2},
            {currentCol + 2, currentRow - 2}
        };

        for (int[] pos : cornerPositions) {
            int cx = pos[0];
            int cy = pos[1];
            if (cx < 0 || cx >= Board.WIDTH || cy < 0 || cy >= Board.HEIGHT) {
                corners++;
            } else if (board.getCell(cx, cy) != null) {
                corners++;
            }
        }

        return corners >= 3;
    }

    /**
     * Detects a Mini T-Spin.
     *
     * <p>A Mini T-Spin occurs when the T-Spin conditions are met but the
     * two corners in front of the T (the "pointing" direction) are not both
     * occupied. If both front corners are occupied, it's a full T-Spin.</p>
     *
     * @return true if this is a Mini T-Spin (not a full T-Spin)
     */
    private boolean detectMiniTSpin() {
        // The "front" of the T depends on rotation state
        int[][] frontCorners = getFrontCorners();
        int frontOccupied = 0;
        for (int[] pos : frontCorners) {
            int cx = pos[0];
            int cy = pos[1];
            if (cx < 0 || cx >= Board.WIDTH || cy < 0 || cy >= Board.HEIGHT) {
                frontOccupied++;
            } else if (board.getCell(cx, cy) != null) {
                frontOccupied++;
            }
        }
        // If both front corners occupied, it's a full T-Spin, not mini
        return frontOccupied < 2;
    }

    /**
     * Gets the two "front" corner positions of the T-piece based on current rotation.
     * The front is the direction the T is pointing.
     *
     * @return array of 2 corner positions as {col, row}
     */
    private int[][] getFrontCorners() {
        return switch (currentRotation) {
            case 0 -> new int[][] { // T points up
                {currentCol, currentRow},
                {currentCol + 2, currentRow}
            };
            case 1 -> new int[][] { // T points right
                {currentCol + 2, currentRow},
                {currentCol + 2, currentRow - 2}
            };
            case 2 -> new int[][] { // T points down
                {currentCol, currentRow - 2},
                {currentCol + 2, currentRow - 2}
            };
            case 3 -> new int[][] { // T points left
                {currentCol, currentRow},
                {currentCol, currentRow - 2}
            };
            default -> new int[][] {{0, 0}, {0, 0}};
        };
    }

    // ==================== GHOST PIECE ====================

    /**
     * Calculates the row where the ghost piece would land (hard drop position).
     *
     * @return the row of the ghost piece, or the current row if no piece is active
     */
    public int getGhostRow() {
        if (currentPiece == null) return 0;
        int ghostRow = currentRow;
        while (!board.collides(currentPiece, currentRotation, currentCol, ghostRow - 1)) {
            ghostRow--;
        }
        return ghostRow;
    }

    // ==================== GAME LOOP UPDATE ====================

    /**
     * Updates the game state by the given time delta.
     *
     * <p>This method should be called every frame (e.g. 60 times per second).
     * It handles:</p>
     * <ul>
     *   <li>Gravity (automatic downward movement)</li>
     *   <li>DAS/ARR (auto-repeat for held movement keys)</li>
     *   <li>Lock delay countdown</li>
     *   <li>Action text fadeout</li>
     * </ul>
     *
     * @param deltaTime time elapsed since last update, in seconds
     */
    public void update(double deltaTime) {
        if (gameOver || paused || currentPiece == null) return;

        // Update action text timer
        if (actionTextTimer > 0) {
            actionTextTimer -= deltaTime;
            if (actionTextTimer <= 0) {
                actionText = "";
            }
        }

        // DAS/ARR processing
        updateDAS(deltaTime);

        // Gravity
        double gravityInterval = scoring.getGravityInterval();
        gravityAccumulator += deltaTime;
        while (gravityAccumulator >= gravityInterval) {
            gravityAccumulator -= gravityInterval;
            if (!board.collides(currentPiece, currentRotation, currentCol, currentRow - 1)) {
                currentRow--;
                lastMoveWasRotation = false;
            }
        }

        // Lock delay
        boolean nowOnSurface = board.collides(currentPiece, currentRotation, currentCol, currentRow - 1);
        if (nowOnSurface) {
            onSurface = true;
            lockTimer += deltaTime;
            if (lockTimer >= LOCK_DELAY) {
                lockPiece();
            }
        } else {
            onSurface = false;
            lockTimer = 0;
        }
    }

    /**
     * Processes DAS (Delayed Auto-Shift) and ARR (Auto-Repeat Rate).
     *
     * <p>When a directional key is held:</p>
     * <ol>
     *   <li>Initial press: immediate move</li>
     *   <li>Wait for DAS charge (167ms)</li>
     *   <li>Then repeat moves at ARR interval (33ms)</li>
     * </ol>
     *
     * @param deltaTime time elapsed since last update
     */
    private void updateDAS(double deltaTime) {
        if (dasDirection == 0) return;

        dasTimer += deltaTime;

        if (!dasCharged) {
            if (dasTimer >= DAS) {
                dasCharged = true;
                arrTimer = 0;
                // First auto-repeat move
                if (dasDirection < 0) moveLeft();
                else moveRight();
            }
        } else {
            arrTimer += deltaTime;
            while (arrTimer >= ARR) {
                arrTimer -= ARR;
                if (dasDirection < 0) moveLeft();
                else moveRight();
            }
        }
    }

    // ==================== INPUT HANDLING ====================

    /**
     * Starts DAS charging for the given direction.
     * Called when a movement key is pressed.
     *
     * @param direction -1 for left, 1 for right
     */
    public void startDAS(int direction) {
        if (direction == dasDirection) return;
        dasDirection = direction;
        dasTimer = 0;
        arrTimer = 0;
        dasCharged = false;
        // Immediate first move
        if (direction < 0) moveLeft();
        else if (direction > 0) moveRight();
    }

    /**
     * Stops DAS for the given direction.
     * Called when a movement key is released.
     *
     * @param direction -1 for left, 1 for right
     */
    public void stopDAS(int direction) {
        if (dasDirection == direction) {
            dasDirection = 0;
            dasTimer = 0;
            arrTimer = 0;
            dasCharged = false;
        }
    }

    // ==================== GETTERS ====================

    public Board getBoard() { return board; }
    public Piece getCurrentPiece() { return currentPiece; }
    public int getCurrentRotation() { return currentRotation; }
    public int getCurrentCol() { return currentCol; }
    public int getCurrentRow() { return currentRow; }
    public Piece getHoldPiece() { return holdPiece; }
    public boolean isHoldUsed() { return holdUsed; }
    public boolean isGameOver() { return gameOver; }
    public boolean isPaused() { return paused; }
    public ScoreSystem getScoring() { return scoring; }
    public String getActionText() { return actionText; }
    public int getLastLinesCleared() { return lastLinesCleared; }

    /**
     * Returns the list of upcoming preview pieces.
     *
     * @return list of next pieces in the queue
     */
    public List<Piece> getPreviewPieces() {
        return bag.peekNext(PREVIEW_COUNT);
    }

    /**
     * Toggles the pause state.
     */
    public void togglePause() {
        paused = !paused;
    }

    /**
     * Sets the pause state.
     *
     * @param paused true to pause, false to unpause
     */
    public void setPaused(boolean paused) {
        this.paused = paused;
    }
}
