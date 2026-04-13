package com.tetris.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The core game state and logic engine for Particle Tetris.
 *
 * <p>Manages piece spawning, movement, rotation, locking, gravity,
 * DAS/ARR, lock delay, hold piece, ghost piece, hadron detection
 * with cascade combos, scoring, and game over detection.</p>
 *
 * <p>Line clears are removed — the only way to clear cells from the board
 * is by forming hadrons. This unifies the game's core loop around particle
 * physics rather than having two competing objectives.</p>
 */
public class GameState {

    public static final double LOCK_DELAY = 0.5;
    public static final int MAX_LOCK_RESETS = 8;
    public static final double DAS = 0.167;
    public static final double ARR = 0.033;
    public static final int PREVIEW_COUNT = 3;
    private static final int PARTICLES_PER_LEVEL = 5;
    public static final int MAX_UNDOS = 2;

    private final Board board;
    private final BagRandomizer bag;
    private final HadronDetector hadronDetector;
    private final ScoreSystem scoreSystem;

    private Piece currentPiece;
    private int currentRotation;
    private int currentCol;
    private int currentRow;

    private Piece holdPiece;

    private double lockTimer;
    private boolean onSurface;
    private int lockResets;

    private double gravityAccumulator;

    private int dasDirection;
    private double dasTimer;
    private double arrTimer;
    private boolean dasCharged;

    private boolean gameOver;
    private boolean paused;

    private final List<Hadron> discoveredHadrons = new ArrayList<>();
    private List<Hadron> lastDiscoveredHadrons = Collections.emptyList();
    private List<HadronFormation> lastFormations = Collections.emptyList();
    private double hadronAnimTimer;
    public static final double HADRON_ANIM_DURATION = 0.6;
    private String actionText = "";
    private double actionTextTimer;
    private int lastComboCount;

    // Undo system: snapshots of board state, capped at MAX_UNDOS per game
    private final List<Board> undoSnapshots = new ArrayList<>();
    private int undosRemaining;

    public GameState() {
        this(1);
    }

    public GameState(int startLevel) {
        this.board = new Board();
        this.bag = new BagRandomizer(PREVIEW_COUNT);
        this.hadronDetector = new HadronDetector();
        this.scoreSystem = new ScoreSystem();
        this.gameOver = false;
        this.paused = false;
        this.holdPiece = null;
        this.dasDirection = 0;
        this.dasTimer = 0;
        this.arrTimer = 0;
        this.dasCharged = false;
        this.undosRemaining = MAX_UNDOS;

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

    /**
     * Swaps the current piece with the hold piece. Unlike standard Tetris,
     * hold is freely reusable — no once-per-piece restriction.
     */
    public void hold() {
        if (gameOver || paused || currentPiece == null) return;

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

            if (board.collides(currentPiece, currentRotation, currentCol, currentRow)) {
                gameOver = true;
            }
        } else {
            spawnNextPiece();
        }
    }

    // ==================== LOCK DELAY ====================

    /**
     * Resets the lock timer when the piece moves/rotates on a surface.
     * Limited to MAX_LOCK_RESETS — no infinite-manipulation exception.
     */
    private void onLockReset() {
        if (onSurface && lockResets < MAX_LOCK_RESETS) {
            lockTimer = 0;
            lockResets++;
        }
    }

    // ==================== PIECE LOCKING ====================

    /**
     * Locks the current piece and triggers the cascade detection loop.
     *
     * <p>After locking, hadron detection runs in a loop: detect → consume →
     * sticky gravity → detect again, until no more hadrons form. Each iteration
     * increments the combo counter, multiplying the score.</p>
     *
     * <p>Line clears are removed — hadrons are the only way to remove cells.</p>
     */
    private void lockPiece() {
        if (currentPiece == null) return;

<<<<<<< HEAD
        // Snapshot board state for undo (before placing)
        if (undosRemaining > 0) {
            if (undoSnapshots.size() >= MAX_UNDOS) {
                undoSnapshots.remove(0);
=======
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
>>>>>>> parent of b338db7 (Add .gitignore and clarify T-spin detection comment)
            }
            undoSnapshots.add(board.copy());
        }

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

        // Cascade detection loop: detect hadrons → sticky gravity → repeat
        List<HadronFormation> allFormations = new ArrayList<>();
        List<Hadron> allHadrons = new ArrayList<>();
        int comboCount = 0;

        // First detection uses the placed piece info
        Piece detectPiece = currentPiece;
        int detectRotation = currentRotation;
        int detectCol = currentCol;
        int detectRow = currentRow;

        while (true) {
            List<HadronFormation> formations = hadronDetector.detect(board, detectPiece,
                    detectRotation, detectCol, detectRow);

            if (formations.isEmpty()) break;

            for (HadronFormation f : formations) {
                scoreSystem.award(f.getHadron(), comboCount);
                allFormations.add(f);
                allHadrons.add(f.getHadron());
            }
            comboCount++;

            // For subsequent cascade iterations, we need to re-scan the whole board.
            // Use a dummy scan from (0,0) — detect() only looks at seed cells near the
            // piece, so we need to re-trigger detection broadly. Since sticky gravity
            // may have moved cells, use a full-board scan approach.
            // Actually, detect() already applies gravity internally, so after the first
            // call the board has changed. We break and let the outer loop re-detect
            // from any new gluon cluster.
            detectPiece = Piece.GLUON; // dummy — we'll scan near existing gluon clusters
            detectCol = 0;
            detectRow = 0;
            detectRotation = 0;

            // Re-scan: find any gluon on the board to seed from
            boolean foundGluon = false;
            for (int r = 0; r < Board.HEIGHT && !foundGluon; r++) {
                for (int c = 0; c < Board.WIDTH && !foundGluon; c++) {
                    Piece p = board.getCell(c, r);
                    if (p != null && p.isGluon()) {
                        detectCol = c;
                        detectRow = r;
                        foundGluon = true;
                    }
                }
            }
            if (!foundGluon) break;
        }

        lastDiscoveredHadrons = allHadrons;
        lastFormations = allFormations;
        lastComboCount = comboCount;
        discoveredHadrons.addAll(allHadrons);
        if (!allFormations.isEmpty()) {
            hadronAnimTimer = HADRON_ANIM_DURATION;
        }

        // Build action text
        StringBuilder sb = new StringBuilder();
        if (!allHadrons.isEmpty()) {
            for (Hadron h : allHadrons) {
                if (sb.length() > 0) sb.append(" + ");
                sb.append(h.getDisplayName());
            }
            sb.append("!");
            if (comboCount > 1) {
                sb.append(" ×").append(comboCount).append(" Combo!");
            }
        }
        if (sb.length() > 0) {
            actionText = sb.toString();
            actionTextTimer = 2.5;
        }

        spawnNextPiece();
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

        if (hadronAnimTimer > 0) {
            hadronAnimTimer -= deltaTime;
            if (hadronAnimTimer <= 0) {
                hadronAnimTimer = 0;
                lastFormations = Collections.emptyList();
            }
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
        int level = getLevel();
        return Math.pow(0.8 - (level - 1) * 0.007, level - 1);
    }

    public int getLevel() {
        return (scoreSystem.getTotalParticlesContained() / PARTICLES_PER_LEVEL) + 1;
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
    public boolean isGameOver() { return gameOver; }
    public boolean isPaused() { return paused; }
    public String getActionText() { return actionText; }
    public int getScore() { return scoreSystem.getScore(); }
    public int getTotalParticlesContained() { return scoreSystem.getTotalParticlesContained(); }
    public ScoreSystem getScoreSystem() { return scoreSystem; }
    public int getLastComboCount() { return lastComboCount; }
    public List<Hadron> getDiscoveredHadrons() { return Collections.unmodifiableList(discoveredHadrons); }
    public List<Hadron> getLastDiscoveredHadrons() { return lastDiscoveredHadrons; }
    public List<HadronFormation> getLastFormations() { return lastFormations; }
    public double getHadronAnimTimer() { return hadronAnimTimer; }
    public int getUndosRemaining() { return undosRemaining; }

    public List<Piece> getPreviewPieces() {
        return bag.peekNext(PREVIEW_COUNT);
    }

    // ==================== UNDO ====================

    /**
     * Undoes the last piece placement by restoring the board snapshot.
     * Limited to MAX_UNDOS per game. Returns true if undo was performed.
     */
    public boolean undo() {
        if (gameOver || paused || undoSnapshots.isEmpty() || undosRemaining <= 0) {
            return false;
        }
        Board snapshot = undoSnapshots.remove(undoSnapshots.size() - 1);
        // Restore board state
        for (int r = 0; r < Board.HEIGHT; r++) {
            for (int c = 0; c < Board.WIDTH; c++) {
                board.setCell(c, r, snapshot.getCell(c, r));
            }
        }
        undosRemaining--;
        // Re-spawn current piece at spawn position
        currentRotation = 0;
        currentCol = currentPiece.getSpawnColumn();
        currentRow = currentPiece.getSpawnRow();
        lockTimer = 0;
        lockResets = 0;
        onSurface = false;
        gravityAccumulator = 0;
        return true;
    }

    public void togglePause() {
        paused = !paused;
    }
}
