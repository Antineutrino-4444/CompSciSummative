package com.tetris.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The core game state and logic engine for Particle Tetris.
 *
 * <p>Manages piece spawning, movement, rotation, locking, gravity,
 * DAS/ARR, lock delay, hold piece, ghost piece, line clearing,
 * hadron detection, and game over detection.</p>
 */
public class GameState {

    public static final double LOCK_DELAY = 0.5;
    public static final int MAX_LOCK_RESETS = 15;
    public static final double DAS = 0.167;
    public static final double ARR = 0.033;
    public static final int PREVIEW_COUNT = 5;
    private static final int LINES_PER_LEVEL = 10;

    private final Board board;
    private final BagRandomizer bag;
    private final HadronDetector hadronDetector;

    private Piece currentPiece;
    private int currentRotation;
    private int currentCol;
    private int currentRow;

    private Piece holdPiece;
    private boolean holdUsed;

    private double lockTimer;
    private boolean onSurface;
    private int lockResets;
    private int lowestRow;

    private double gravityAccumulator;

    private int dasDirection;
    private double dasTimer;
    private double arrTimer;
    private boolean dasCharged;

    private boolean gameOver;
    private boolean paused;

    private int totalLinesCleared;
    private final List<Hadron> discoveredHadrons = new ArrayList<>();
    private List<Hadron> lastDiscoveredHadrons = Collections.emptyList();
    private int lastLinesCleared;
    private String actionText = "";
    private double actionTextTimer;

    public GameState() {
        this(1);
    }

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

    public boolean moveLeft() {
        if (gameOver || paused || currentPiece == null) return false;
        if (!board.collides(currentPiece, currentRotation, currentCol - 1, currentRow)) {
            currentCol--;
            onLockReset();
            return true;
        }
        return false;
    }

    public boolean moveRight() {
        if (gameOver || paused || currentPiece == null) return false;
        if (!board.collides(currentPiece, currentRotation, currentCol + 1, currentRow)) {
            currentCol++;
            onLockReset();
            return true;
        }
        return false;
    }

    public boolean softDrop() {
        if (gameOver || paused || currentPiece == null) return false;
        if (!board.collides(currentPiece, currentRotation, currentCol, currentRow - 1)) {
            currentRow--;
            gravityAccumulator = 0;
            return true;
        }
        return false;
    }

    public void hardDrop() {
        if (gameOver || paused || currentPiece == null) return;
        while (!board.collides(currentPiece, currentRotation, currentCol, currentRow - 1)) {
            currentRow--;
        }
        lockPiece();
    }

    // ==================== ROTATION ====================

    public boolean rotateCW() {
        return rotate((currentRotation + 1) & 3);
    }

    public boolean rotateCCW() {
        return rotate((currentRotation + 3) & 3);
    }

    public boolean rotate180() {
        return rotate((currentRotation + 2) & 3);
    }

    private boolean rotate(int newRotation) {
        if (gameOver || paused || currentPiece == null) return false;
        if (currentPiece == Piece.GLUON) return false;

        int[][] kicks = WallKickData.getKicks(currentPiece, currentRotation, newRotation);
        if (kicks == null) return false;

        for (int[] kick : kicks) {
            int testCol = currentCol + kick[0];
            int testRow = currentRow + kick[1];
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

    private void lockPiece() {
        if (currentPiece == null) return;

        board.placePiece(currentPiece, currentRotation, currentCol, currentRow);

        // Check lock out
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

        // Detect hadrons
        List<Hadron> hadrons = hadronDetector.detect(board, currentPiece, currentRotation,
                currentCol, currentRow);
        lastDiscoveredHadrons = hadrons;
        discoveredHadrons.addAll(hadrons);

        // Clear lines
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
                case 4 -> "Quad!";
                default -> linesCleared + " Lines!";
            });
        }
        if (sb.length() > 0) {
            actionText = sb.toString();
            actionTextTimer = 2.5;
        }

        spawnNextPiece();
        holdUsed = false;
    }

    // ==================== GHOST PIECE ====================

    public int getGhostRow() {
        if (currentPiece == null) return 0;
        int ghostRow = currentRow;
        while (!board.collides(currentPiece, currentRotation, currentCol, ghostRow - 1)) {
            ghostRow--;
        }
        return ghostRow;
    }

    // ==================== GAME LOOP ====================

    public void update(double deltaTime) {
        if (gameOver || paused || currentPiece == null) return;

        if (actionTextTimer > 0) {
            actionTextTimer -= deltaTime;
            if (actionTextTimer <= 0) actionText = "";
        }

        updateDAS(deltaTime);

        double gravityInterval = getGravityInterval();
        gravityAccumulator += deltaTime;
        while (gravityAccumulator >= gravityInterval) {
            gravityAccumulator -= gravityInterval;
            if (!board.collides(currentPiece, currentRotation, currentCol, currentRow - 1)) {
                currentRow--;
            }
        }

        boolean nowOnSurface = board.collides(currentPiece, currentRotation, currentCol,
                currentRow - 1);
        if (nowOnSurface) {
            onSurface = true;
            lockTimer += deltaTime;
            if (lockTimer >= LOCK_DELAY) lockPiece();
        } else {
            onSurface = false;
            lockTimer = 0;
        }
    }

    public double getGravityInterval() {
        int level = (totalLinesCleared / LINES_PER_LEVEL) + 1;
        return Math.pow(0.8 - (level - 1) * 0.007, level - 1);
    }

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

    // ==================== INPUT ====================

    public void startDAS(int direction) {
        if (direction == dasDirection) return;
        dasDirection = direction;
        dasTimer = 0;
        arrTimer = 0;
        dasCharged = false;
        if (direction < 0) moveLeft();
        else if (direction > 0) moveRight();
    }

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

    public List<Piece> getPreviewPieces() {
        return bag.peekNext(PREVIEW_COUNT);
    }

    public void togglePause() {
        paused = !paused;
    }
}
