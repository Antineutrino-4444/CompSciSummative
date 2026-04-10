package com.tetris.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The core game state and logic engine for Particle Tetris.
 *
 * <p>This class manages all aspects of the game loop including:</p>
 * <ul>
 *   <li>Piece spawning, movement, rotation, and locking</li>
 *   <li>Gravity and soft/hard drop mechanics</li>
 *   <li>DAS (Delayed Auto-Shift) and ARR (Auto-Repeat Rate)</li>
 *   <li>Lock delay with move/rotation reset</li>
 *   <li>Hold piece functionality</li>
 *   <li>Ghost piece calculation</li>
 *   <li>Line clearing (standard Tetris mechanic)</li>
 *   <li>Hadron detection (particle combination mechanic)</li>
 *   <li>Game over detection</li>
 * </ul>
 *
 * <p>Note: The traditional scoring system has been removed. Instead, the game
 * tracks discovered hadrons (composite particles created by adjacent quarks
 * and gluons on the board).</p>
 */
public class GameState {

    // --- Timing constants (in seconds) ---

    /** Lock delay in seconds. */
    public static final double LOCK_DELAY = 0.5;

    /** Maximum number of times lock delay can be reset by movement/rotation. */
    public static final int MAX_LOCK_RESETS = 15;

    /** Delayed Auto-Shift: initial delay before auto-repeat (seconds). */
    public static final double DAS = 0.167;

    /** Auto-Repeat Rate: interval between repeated movements (seconds). */
    public static final double ARR = 0.033;

    /** Number of preview pieces shown. */
    public static final int PREVIEW_COUNT = 5;

    /** Default gravity interval in seconds (level 1 speed). */
    private static final double DEFAULT_GRAVITY = 1.0;

    /** Gravity gets faster as more lines are cleared (every 10 lines = level up). */
    private static final int LINES_PER_LEVEL = 10;

    // --- Game components ---

    private final Board board;
    private final BagRandomizer bag;
    private final HadronDetector hadronDetector;

    // --- Current piece state ---

    private Piece currentPiece;
    private int currentRotation;
    private int currentCol;
    private int currentRow;

    // --- Hold piece ---

    private Piece holdPiece;
    private boolean holdUsed;

    // --- Lock delay state ---

    private double lockTimer;
    private boolean onSurface;
    private int lockResets;
    private int lowestRow;

    // --- Gravity ---

    private double gravityAccumulator;

    // --- DAS/ARR state ---

    private int dasDirection;
    private double dasTimer;
    private double arrTimer;
    private boolean dasCharged;

    // --- Game state flags ---

    private boolean gameOver;
    private boolean paused;

    // --- Stats (replaces scoring) ---

    /** Total lines cleared. */
    private int totalLinesCleared;

    /** All hadrons discovered during this game. */
    private final List<Hadron> discoveredHadrons = new ArrayList<>();

    /** Hadrons discovered in the most recent piece lock. */
    private List<Hadron> lastDiscoveredHadrons = Collections.emptyList();

    /** Lines cleared in the most recent lock. */
    private int lastLinesCleared;

    /** Action text to display. */
    private String actionText = "";

    /** Timer for how long to show the action text. */
    private double actionTextTimer;

    /**
     * Creates a new game with default settings.
     */
    public GameState() {
        this(1);
    }

    /**
     * Creates a new game starting at the specified level.
     *
     * @param startLevel the starting level (1+, affects initial gravity speed)
     */
    public GameState(int startLevel) {
        this.board = new Board();
        this.bag = new BagRandomizer(PREVIEW_COUNT);
        this.hadronDetector = new HadronDetector();
        this.gameOver = false;
        this.paused = false;
        this.holdPiece = null;
        this.holdUsed = false;
        this.dasDirection = 0;
        this.dasTimer = 0;
        this.arrTimer = 0;
        this.dasCharged = false;
        this.totalLinesCleared = 0;

        spawnNextPiece();
    }

    // ==================== PIECE SPAWNING ====================

    /**
     * Spawns the next piece from the bag randomizer.
     */
    private void spawnNextPiece() {
        currentPiece = bag.next();
        currentRotation = 0;
        currentCol = currentPiece.getSpawnColumn();
        currentRow = currentPiece.getSpawnRow();
        lockTimer = 0;
        lockResets = 0;
        onSurface = false;
        gravityAccumulator = 0;

        if (board.collides(currentPiece, currentRotation, currentCol, currentRow)) {
            currentRow++;
            if (board.collides(currentPiece, currentRotation, currentCol, currentRow)) {
                gameOver = true;
            }
        }

        lowestRow = getGhostRow();
    }

    // ==================== MOVEMENT ====================

    /**
     * Attempts to move the current piece left.
     */
    public boolean moveLeft() {
        if (gameOver || paused || currentPiece == null) return false;
        if (!board.collides(currentPiece, currentRotation, currentCol - 1, currentRow)) {
            currentCol--;
            onLockReset();
            return true;
        }
        return false;
    }

    /**
     * Attempts to move the current piece right.
     */
    public boolean moveRight() {
        if (gameOver || paused || currentPiece == null) return false;
        if (!board.collides(currentPiece, currentRotation, currentCol + 1, currentRow)) {
            currentCol++;
            onLockReset();
            return true;
        }
        return false;
    }

    /**
     * Performs a soft drop (moves piece down one row).
     */
    public boolean softDrop() {
        if (gameOver || paused || currentPiece == null) return false;
        if (!board.collides(currentPiece, currentRotation, currentCol, currentRow - 1)) {
            currentRow--;
            gravityAccumulator = 0;
            return true;
        }
        return false;
    }

    /**
     * Performs a hard drop. Locks the piece immediately.
     */
    public void hardDrop() {
        if (gameOver || paused || currentPiece == null) return;
        while (!board.collides(currentPiece, currentRotation, currentCol, currentRow - 1)) {
            currentRow--;
        }
        lockPiece();
    }

    // ==================== ROTATION (SRS) ====================

    /**
     * Attempts to rotate the piece clockwise using SRS wall kicks.
     */
    public boolean rotateCW() {
        return rotate((currentRotation + 1) & 3);
    }

    /**
     * Attempts to rotate the piece counter-clockwise using SRS wall kicks.
     */
    public boolean rotateCCW() {
        return rotate((currentRotation + 3) & 3);
    }

    /**
     * Attempts to rotate the piece 180°.
     */
    public boolean rotate180() {
        return rotate((currentRotation + 2) & 3);
    }

    /**
     * Internal rotation logic with SRS wall kick testing.
     */
    private boolean rotate(int newRotation) {
        if (gameOver || paused || currentPiece == null) return false;
        if (currentPiece == Piece.GLUON) return false; // Gluon (O-shape) doesn't rotate

        int[][] kicks = WallKickData.getKicks(currentPiece, currentRotation, newRotation);
        if (kicks == null) return false;

        for (int i = 0; i < kicks.length; i++) {
            int testCol = currentCol + kicks[i][0];
            int testRow = currentRow + kicks[i][1];
            if (!board.collides(currentPiece, newRotation, testCol, testRow)) {
                currentCol = testCol;
                currentRow = testRow;
                currentRotation = newRotation;
                onLockReset();
                return true;
            }
        }
        return false;
    }

    // ==================== HOLD ====================

    /**
     * Swaps the current piece with the hold piece.
     */
    public void hold() {
        if (gameOver || paused || currentPiece == null || holdUsed) return;

        holdUsed = true;
        Piece temp = holdPiece;
        holdPiece = currentPiece;

        if (temp != null) {
            currentPiece = temp;
            currentRotation = 0;
            currentCol = currentPiece.getSpawnColumn();
            currentRow = currentPiece.getSpawnRow();
            lockTimer = 0;
            lockResets = 0;
            onSurface = false;
            gravityAccumulator = 0;
            lowestRow = getGhostRow();

            if (board.collides(currentPiece, currentRotation, currentCol, currentRow)) {
                gameOver = true;
            }
        } else {
            spawnNextPiece();
        }
    }

    // ==================== LOCK DELAY ====================

    private void onLockReset() {
        if (onSurface && lockResets < MAX_LOCK_RESETS) {
            lockTimer = 0;
            lockResets++;
        }
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
     *   <li>Hadron detection (particle combinations)</li>
     *   <li>Line clear checking</li>
     *   <li>Lock out detection</li>
     *   <li>Spawning the next piece</li>
     * </ol>
     */
    private void lockPiece() {
        if (currentPiece == null) return;

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

        // Detect hadrons (particle combinations)
        List<Hadron> hadrons = hadronDetector.detect(board, currentPiece, currentRotation,
                currentCol, currentRow);
        lastDiscoveredHadrons = hadrons;
        discoveredHadrons.addAll(hadrons);

        // Clear lines (standard Tetris mechanic — still works!)
        int linesCleared = board.clearLines();
        lastLinesCleared = linesCleared;
        totalLinesCleared += linesCleared;

        // Build action text
        StringBuilder sb = new StringBuilder();
        if (!hadrons.isEmpty()) {
            for (Hadron h : hadrons) {
                if (sb.length() > 0) sb.append(" + ");
                sb.append(h.getDisplayName());
            }
            sb.append("!");
        }
        if (linesCleared > 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(switch (linesCleared) {
                case 1 -> "Single!";
                case 2 -> "Double!";
                case 3 -> "Triple!";
                case 4 -> "Tetris!";
                default -> linesCleared + " Lines!";
            });
        }
        if (sb.length() > 0) {
            actionText = sb.toString();
            actionTextTimer = 2.5;
        }

        // Spawn next piece
        spawnNextPiece();
        holdUsed = false;
    }

    // ==================== GHOST PIECE ====================

    /**
     * Calculates the row where the ghost piece would land.
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
        double gravityInterval = getGravityInterval();
        gravityAccumulator += deltaTime;
        while (gravityAccumulator >= gravityInterval) {
            gravityAccumulator -= gravityInterval;
            if (!board.collides(currentPiece, currentRotation, currentCol, currentRow - 1)) {
                currentRow--;
            }
        }

        // Lock delay
        boolean nowOnSurface = board.collides(currentPiece, currentRotation, currentCol,
                currentRow - 1);
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
     * Calculates the gravity interval based on lines cleared (pseudo-level).
     * Uses the standard Tetris formula.
     */
    public double getGravityInterval() {
        int level = (totalLinesCleared / LINES_PER_LEVEL) + 1;
        return Math.pow(0.8 - (level - 1) * 0.007, level - 1);
    }

    /**
     * Returns the current level (based on lines cleared).
     */
    public int getLevel() {
        return (totalLinesCleared / LINES_PER_LEVEL) + 1;
    }

    private void updateDAS(double deltaTime) {
        if (dasDirection == 0) return;

        dasTimer += deltaTime;

        if (!dasCharged) {
            if (dasTimer >= DAS) {
                dasCharged = true;
                arrTimer = 0;
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
     */
    public void startDAS(int direction) {
        if (direction == dasDirection) return;
        dasDirection = direction;
        dasTimer = 0;
        arrTimer = 0;
        dasCharged = false;
        if (direction < 0) moveLeft();
        else if (direction > 0) moveRight();
    }

    /**
     * Stops DAS for the given direction.
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
    public String getActionText() { return actionText; }
    public int getLastLinesCleared() { return lastLinesCleared; }
    public int getTotalLinesCleared() { return totalLinesCleared; }
    public List<Hadron> getDiscoveredHadrons() { return Collections.unmodifiableList(discoveredHadrons); }
    public List<Hadron> getLastDiscoveredHadrons() { return lastDiscoveredHadrons; }

    /**
     * Returns the list of upcoming preview pieces.
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
}
