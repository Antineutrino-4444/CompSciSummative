package com.tetris.model;

import java.util.List;

/**
 * GameState.java
 * ==============
 * The central game state manager. Coordinates all game logic: piece spawning,
 * movement, rotation (with SRS wall kicks), locking, line clearing, hold,
 * T-spin detection, scoring, and game-over conditions.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * GAME FLOW OVERVIEW
 * ═══════════════════════════════════════════════════════════════════════
 *
 *   ┌──────────┐
 *   │  SPAWN   │ ← Pull next piece from bag, set as current piece
 *   └────┬─────┘
 *        │
 *        ▼
 *   ┌──────────┐    ┌──────────┐
 *   │  FALLING │◄───│  INPUT   │ ← Player moves/rotates/drops
 *   └────┬─────┘    └──────────┘
 *        │ gravity tick or hard drop
 *        ▼
 *   ┌──────────┐
 *   │  LOCK    │ ← Piece can't move down; lock delay starts
 *   └────┬─────┘
 *        │ lock delay expires (or hard drop)
 *        ▼
 *   ┌──────────┐
 *   │  CLEAR   │ ← Check for completed lines, award score
 *   └────┬─────┘
 *        │
 *        ▼
 *   ┌──────────┐
 *   │  CHECK   │ ← Game over? (block out / lock out)
 *   └────┬─────┘
 *        │ no
 *        ▼
 *     (back to SPAWN)
 *
 * ═══════════════════════════════════════════════════════════════════════
 * LOCK DELAY
 * ═══════════════════════════════════════════════════════════════════════
 * When a piece reaches its lowest valid position:
 *   - A 500ms lock delay timer starts.
 *   - If the player moves/rotates the piece during this time, the timer
 *     resets (up to a maximum of 15 resets).
 *   - After the delay expires (or max resets), the piece locks.
 *   - Hard drop always locks instantly (no delay).
 *
 * ═══════════════════════════════════════════════════════════════════════
 * HOLD SYSTEM
 * ═══════════════════════════════════════════════════════════════════════
 * The player can press HOLD to:
 *   - Store the current piece in the hold slot.
 *   - If the hold slot was empty: spawn the next piece from the bag.
 *   - If the hold slot had a piece: swap it with the current piece.
 *   - Hold can only be used ONCE per piece (flag resets on lock).
 *
 * ═══════════════════════════════════════════════════════════════════════
 * T-SPIN DETECTION
 * ═══════════════════════════════════════════════════════════════════════
 * A T-spin is detected when ALL of the following are true:
 *   1. The piece is a T-piece.
 *   2. The last successful movement was a rotation (not translation).
 *   3. At least 3 of the 4 diagonal corners around the T-piece center
 *      are occupied (wall or locked block).
 *
 * A T-spin Mini is detected when:
 *   - It's a T-spin, but only 2 specific front corners are filled
 *     (and the rotation kick used was NOT the last test).
 *   - Or: it qualifies as a T-spin but the kick offset was trivial.
 *
 * Simplified approach used here:
 *   - Full T-Spin: 3+ corners occupied AND last move was rotation
 *   - T-Spin Mini: exactly 3 corners AND the kick used was test 0/1/2
 *     (i.e., not the 4th wall kick test which usually indicates a "real" T-spin slot)
 */
public class GameState {

    // ─────────────────────── Configuration ───────────────────────

    // Lock delay, max resets, and preview count are now read from Settings.get()
    // at runtime, so changes take effect immediately without restarting.

    // ─────────────────────── Components ─────────────────────────

    private final Board board;
    private final BagRandomizer bag;
    private final ScoreSystem scoreSystem;

    // ─────────────────────── Piece State ────────────────────────

    /** The currently falling piece (null if between spawns). */
    private Tetromino currentPiece;

    /** The piece stored in the hold slot (null if empty). */
    private TetrominoType holdPiece;

    /** Whether hold has been used for the current piece (resets on lock). */
    private boolean holdUsed;

    // ─────────────────────── Lock Delay State ───────────────────

    /** Whether the current piece is touching the ground (lock delay active). */
    private boolean lockDelayActive;

    /** Timestamp (millis) when the lock delay started. */
    private long lockDelayStart;

    /** Number of lock resets used for the current piece. */
    private int lockResets;

    /** The lowest Y position reached by the current piece (for lock reset logic). */
    private int lowestY;

    // ─────────────────────── T-Spin Detection ───────────────────

    /** Whether the last successful move on the current piece was a rotation. */
    private boolean lastMoveWasRotation;

    /** The wall kick test index used for the last rotation (0 = no kick, 1–4 = kick tests). */
    private int lastKickIndex;

    // ─────────────────────── Game State ─────────────────────────

    private boolean gameOver;
    private boolean paused;

    /** Flag indicating a new piece was just spawned (for IRS/IHS). */
    private boolean justSpawned;

    /** Timestamp of the last gravity drop. */
    private long lastGravityDrop;

    // ─────────────────────── Constructor ─────────────────────────

    /**
     * Creates a new game at the specified starting level.
     *
     * @param startLevel the starting level (1+)
     */
    public GameState(int startLevel) {
        board = new Board();
        bag = new BagRandomizer();
        scoreSystem = new ScoreSystem(startLevel);
        holdPiece = null;
        holdUsed = false;
        gameOver = false;
        paused = false;
        lastGravityDrop = System.currentTimeMillis();
        spawnNextPiece();
    }

    /** Creates a new game at level 1. */
    public GameState() {
        this(1);
    }

    // ═══════════════════════════════════════════════════════════════
    // PIECE SPAWNING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Spawns the next piece from the bag.
     *
     * If the new piece immediately collides with the board (can't spawn),
     * the game is over ("block out").
     */
    private void spawnNextPiece() {
        TetrominoType nextType = bag.next();
        currentPiece = Tetromino.spawn(nextType, Board.WIDTH);

        // Check for block-out (can't place the spawned piece)
        if (!board.isValidPosition(currentPiece)) {
            gameOver = true;
            return;
        }

        // Reset lock delay state for the new piece
        lockDelayActive = false;
        lockResets = 0;
        lowestY = currentPiece.getBoardPosition().getY();
        lastMoveWasRotation = false;
        lastKickIndex = 0;
        holdUsed = false; // Reset hold flag only here (once per lock, not per hold)
        justSpawned = true; // Flag for IRS/IHS detection
    }

    // ═══════════════════════════════════════════════════════════════
    // MOVEMENT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Moves the current piece left by 1 column.
     *
     * @return true if the move was successful
     */
    public boolean moveLeft() {
        if (!canAct()) return false;
        Tetromino moved = currentPiece.moveLeft();
        if (board.isValidPosition(moved)) {
            currentPiece = moved;
            lastMoveWasRotation = false;
            onSuccessfulMove();
            return true;
        }
        return false;
    }

    /**
     * Moves the current piece right by 1 column.
     *
     * @return true if the move was successful
     */
    public boolean moveRight() {
        if (!canAct()) return false;
        Tetromino moved = currentPiece.moveRight();
        if (board.isValidPosition(moved)) {
            currentPiece = moved;
            lastMoveWasRotation = false;
            onSuccessfulMove();
            return true;
        }
        return false;
    }

    /**
     * Soft drop: moves the piece down by 1 row.
     * Awards 1 point per row.
     *
     * @return true if the piece moved down
     */
    public boolean softDrop() {
        if (!canAct()) return false;
        Tetromino moved = currentPiece.moveDown();
        if (board.isValidPosition(moved)) {
            currentPiece = moved;
            lastMoveWasRotation = false;
            scoreSystem.addSoftDrop(1);
            // Update lowest Y
            int currentY = currentPiece.getBoardPosition().getY();
            if (currentY > lowestY) {
                lowestY = currentY;
            }
            updateLockDelay();
            return true;
        }
        return false;
    }

    /**
     * Hard drop: instantly drops the piece to the ghost position and locks it.
     * Awards 2 points per row dropped. No lock delay.
     */
    public void hardDrop() {
        if (!canAct()) return;

        // Calculate distance to ghost position
        Tetromino ghost = board.getGhostPosition(currentPiece);
        int distance = ghost.getBoardPosition().getY() - currentPiece.getBoardPosition().getY();
        scoreSystem.addHardDrop(distance);

        currentPiece = ghost;
        lastMoveWasRotation = false;  // Hard drop is a translation
        lockPiece();
    }

    // ═══════════════════════════════════════════════════════════════
    // ROTATION (with SRS wall kicks)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Rotates the current piece 90° clockwise with SRS wall kicks.
     *
     * @return true if the rotation was successful
     */
    public boolean rotateCW() {
        if (!canAct()) return false;
        return tryRotation(currentPiece.rotateCW(), currentPiece.getRotationState(),
                (currentPiece.getRotationState() + 1) % 4);
    }

    /**
     * Rotates the current piece 90° counter-clockwise with SRS wall kicks.
     *
     * @return true if the rotation was successful
     */
    public boolean rotateCCW() {
        if (!canAct()) return false;
        return tryRotation(currentPiece.rotateCCW(), currentPiece.getRotationState(),
                (currentPiece.getRotationState() + 3) % 4);
    }

    /**
     * Rotates the current piece 180° (if supported).
     *
     * @return true if the rotation was successful
     */
    public boolean rotate180() {
        if (!canAct()) return false;
        Tetromino rotated = currentPiece.rotate180();
        if (board.isValidPosition(rotated)) {
            currentPiece = rotated;
            lastMoveWasRotation = true;
            lastKickIndex = 0;
            onSuccessfulMove();
            return true;
        }
        return false;
    }

    /**
     * Attempts a rotation with SRS wall kick testing.
     *
     * Steps:
     *   1. Try the basic rotation (offset 0,0).
     *   2. If that fails, try each wall kick offset in order.
     *   3. First valid position wins.
     *   4. If all fail, rotation is rejected.
     *
     * @param rotated   the piece after basic rotation (no kick)
     * @param fromState rotation state before
     * @param toState   rotation state after
     * @return true if a valid position was found
     */
    private boolean tryRotation(Tetromino rotated, int fromState, int toState) {
        // Test 0: basic rotation, no offset
        if (board.isValidPosition(rotated)) {
            currentPiece = rotated;
            lastMoveWasRotation = true;
            lastKickIndex = 0;
            onSuccessfulMove();
            return true;
        }

        // Tests 1–4: wall kick offsets
        Position[] kicks = SRSData.getKicks(currentPiece.getType(), fromState, toState);
        for (int i = 0; i < kicks.length; i++) {
            Tetromino kicked = rotated.translate(kicks[i].getX(), kicks[i].getY());
            if (board.isValidPosition(kicked)) {
                currentPiece = kicked;
                lastMoveWasRotation = true;
                lastKickIndex = i + 1;  // 1-indexed (0 = no kick)
                onSuccessfulMove();
                return true;
            }
        }

        return false;  // All tests failed
    }

    // ═══════════════════════════════════════════════════════════════
    // HOLD
    // ═══════════════════════════════════════════════════════════════

    /**
     * Swaps the current piece with the hold piece.
     *
     * Rules:
     *   - Can only be used once per piece (resets when next piece spawns).
     *   - Current piece goes to hold.
     *   - If hold was empty, spawn next piece from bag.
     *   - If hold had a piece, spawn that piece instead.
     *   - Held piece resets to rotation state 0.
     *
     * @return true if hold was successful
     */
    public boolean hold() {
        if (!canAct()) return false;
        if (holdUsed) return false;  // Already used hold this piece

        TetrominoType currentType = currentPiece.getType();

        if (holdPiece == null) {
            // Hold slot was empty → store current, spawn next from bag
            holdPiece = currentType;
            spawnNextPiece();
        } else {
            // Swap: store current type, spawn the held type
            TetrominoType swapType = holdPiece;
            holdPiece = currentType;
            currentPiece = Tetromino.spawn(swapType, Board.WIDTH);
            if (!board.isValidPosition(currentPiece)) {
                gameOver = true;
                return false;
            }
            // Reset lock state for the swapped-in piece
            lockDelayActive = false;
            lockResets = 0;
            lowestY = currentPiece.getBoardPosition().getY();
            lastMoveWasRotation = false;
            lastKickIndex = 0;
        }

        holdUsed = true;
        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    // LOCK DELAY
    // ═══════════════════════════════════════════════════════════════

    /**
     * Called after any successful move (translation or rotation) to handle
     * lock delay resets.
     *
     * Lock delay rules:
     *   - If the piece is on the ground and we haven't exceeded max resets,
     *     reset the lock delay timer.
     *   - If the piece moves to a new lowest Y, reset the counter entirely.
     */
    private void onSuccessfulMove() {
        int currentY = currentPiece.getBoardPosition().getY();

        // If piece reached a new lowest position, reset lock resets entirely
        if (currentY > lowestY) {
            lowestY = currentY;
            lockResets = 0;
        }

        updateLockDelay();
    }

    /**
     * Updates the lock delay state based on whether the piece is resting
     * on the ground (can't move down).
     */
    private void updateLockDelay() {
        Tetromino below = currentPiece.moveDown();
        boolean onGround = !board.isValidPosition(below);

        if (onGround) {
            if (!lockDelayActive) {
                // Start a new lock delay
                lockDelayActive = true;
                lockDelayStart = System.currentTimeMillis();
            } else if (lockResets < Settings.get().getMaxLockResets()) {
                // Reset the lock timer (move/rotate on ground)
                lockDelayStart = System.currentTimeMillis();
                lockResets++;
            }
            // If max resets reached, timer continues from when it was last set
        } else {
            // Piece is no longer on the ground → deactivate lock delay
            lockDelayActive = false;
        }
    }

    /**
     * Checks if the lock delay has expired and the piece should lock.
     * Called from the game loop.
     *
     * @return true if the piece should be locked
     */
    public boolean isLockDelayExpired() {
        if (!lockDelayActive) return false;
        return (System.currentTimeMillis() - lockDelayStart) >= Settings.get().getLockDelay();
    }

    // ═══════════════════════════════════════════════════════════════
    // PIECE LOCKING & LINE CLEARING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Locks the current piece onto the board, checks for T-spins,
     * clears lines, awards score, and spawns the next piece.
     */
    public void lockPiece() {
        if (currentPiece == null) return;

        // ──── T-Spin detection (before locking) ────
        boolean isTSpin = false;
        boolean isTSpinMini = false;

        if (currentPiece.getType() == TetrominoType.T && lastMoveWasRotation) {
            int cornerCount = countTCorners();
            if (cornerCount >= 3) {
                // Determine full vs mini based on front corners
                int frontCorners = countTFrontCorners();
                if (frontCorners >= 2) {
                    isTSpin = true;  // Full T-Spin
                } else {
                    // Mini T-spin, UNLESS the 4th wall kick test was used (which makes it full)
                    if (lastKickIndex == 4) {
                        isTSpin = true;
                    } else {
                        isTSpinMini = true;
                    }
                }
            }
        }

        // ──── Lock the piece ────
        board.lockPiece(currentPiece);

        // ──── Check for lock-out (piece locked entirely in buffer zone) ────
        boolean allInBuffer = true;
        for (Position cell : currentPiece.getAbsoluteCells()) {
            if (cell.getY() >= Board.BUFFER_HEIGHT) {
                allInBuffer = false;
                break;
            }
        }
        if (allInBuffer) {
            gameOver = true;
            return;
        }

        // ──── Clear lines ────
        int linesCleared = board.clearLines();

        // ──── Award score ────
        scoreSystem.onLineClear(linesCleared, isTSpin, isTSpinMini);

        // ──── Spawn next piece ────
        currentPiece = null;
        holdUsed = false;
        spawnNextPiece();
    }

    /**
     * Counts how many of the 4 diagonal corners around the T-piece center
     * are occupied (wall or locked block).
     *
     * The T-piece center is at bounding-box (1, 1) relative to boardPosition.
     *
     * Corners are: (center-1, center-1), (center+1, center-1),
     *              (center-1, center+1), (center+1, center+1)
     *
     * @return number of occupied corners (0–4)
     */
    private int countTCorners() {
        Position pos = currentPiece.getBoardPosition();
        int cx = pos.getX() + 1;  // center column
        int cy = pos.getY() + 1;  // center row

        int count = 0;
        // Top-left corner
        if (isOccupied(cx - 1, cy - 1)) count++;
        // Top-right corner
        if (isOccupied(cx + 1, cy - 1)) count++;
        // Bottom-left corner
        if (isOccupied(cx - 1, cy + 1)) count++;
        // Bottom-right corner
        if (isOccupied(cx + 1, cy + 1)) count++;

        return count;
    }

    /**
     * Counts the "front" corners of the T-piece based on its current rotation.
     *
     * "Front" means the two corners in the direction the T is pointing.
     *   State 0: pointing up    → top-left, top-right
     *   State 1: pointing right → top-right, bottom-right
     *   State 2: pointing down  → bottom-left, bottom-right
     *   State 3: pointing left  → top-left, bottom-left
     *
     * @return number of occupied front corners (0–2)
     */
    private int countTFrontCorners() {
        Position pos = currentPiece.getBoardPosition();
        int cx = pos.getX() + 1;
        int cy = pos.getY() + 1;

        int count = 0;
        switch (currentPiece.getRotationState()) {
            case 0 -> { // pointing up
                if (isOccupied(cx - 1, cy - 1)) count++;
                if (isOccupied(cx + 1, cy - 1)) count++;
            }
            case 1 -> { // pointing right
                if (isOccupied(cx + 1, cy - 1)) count++;
                if (isOccupied(cx + 1, cy + 1)) count++;
            }
            case 2 -> { // pointing down
                if (isOccupied(cx - 1, cy + 1)) count++;
                if (isOccupied(cx + 1, cy + 1)) count++;
            }
            case 3 -> { // pointing left
                if (isOccupied(cx - 1, cy - 1)) count++;
                if (isOccupied(cx - 1, cy + 1)) count++;
            }
        }
        return count;
    }

    /**
     * Checks if a cell is occupied (out of bounds counts as occupied for corner checks).
     */
    private boolean isOccupied(int x, int y) {
        if (!board.isInBounds(x, y)) return true;  // walls count as occupied
        return board.getCell(x, y) != null;
    }

    // ═══════════════════════════════════════════════════════════════
    // GRAVITY / GAME LOOP
    // ═══════════════════════════════════════════════════════════════

    /**
     * Called every frame from the game loop. Handles:
     *   1. Gravity: drops the piece at the current level's speed.
     *   2. Lock delay: locks the piece if the delay has expired.
     *
     * @return true if the game state changed (needs repaint)
     */
    public boolean update() {
        if (gameOver || paused || currentPiece == null) return false;

        boolean changed = false;
        long now = System.currentTimeMillis();

        // Gravity: automatically drop the piece
        int interval = scoreSystem.getGravityInterval();
        if (now - lastGravityDrop >= interval) {
            Tetromino moved = currentPiece.moveDown();
            if (board.isValidPosition(moved)) {
                currentPiece = moved;
                lastMoveWasRotation = false;
                // Update lowest Y
                int currentY = currentPiece.getBoardPosition().getY();
                if (currentY > lowestY) {
                    lowestY = currentY;
                    lockResets = 0;
                }
                changed = true;
            }
            updateLockDelay();
            lastGravityDrop = now;
        }

        // Lock delay check
        if (isLockDelayExpired()) {
            lockPiece();
            changed = true;
        }

        return changed;
    }

    // ═══════════════════════════════════════════════════════════════
    // PAUSE / GAME STATE
    // ═══════════════════════════════════════════════════════════════

    public void togglePause() {
        paused = !paused;
    }

    public void restart() {
        // Create a fresh game state (handled by the controller creating a new GameState)
    }

    // ═══════════════════════════════════════════════════════════════
    // ACCESSORS
    // ═══════════════════════════════════════════════════════════════

    public Board getBoard() { return board; }
    public Tetromino getCurrentPiece() { return currentPiece; }
    public TetrominoType getHoldPiece() { return holdPiece; }
    public boolean isHoldUsed() { return holdUsed; }
    public boolean isGameOver() { return gameOver; }
    public boolean isPaused() { return paused; }
    public ScoreSystem getScoreSystem() { return scoreSystem; }
    public boolean wasJustSpawned() { return justSpawned; }
    public void clearJustSpawned() { justSpawned = false; }

    /**
     * Returns the ghost piece (shadow showing where the piece will land).
     * Returns null if there's no active piece.
     */
    public Tetromino getGhostPiece() {
        if (currentPiece == null) return null;
        return board.getGhostPosition(currentPiece);
    }

    /**
     * Returns the list of upcoming pieces for the preview panel.
     */
    public List<TetrominoType> getPreviewPieces() {
        return bag.peek(Settings.get().getPreviewCount());
    }

    /**
     * Helper: can the player act? (not game over, not paused, piece exists)
     */
    private boolean canAct() {
        return !gameOver && !paused && currentPiece != null;
    }
}
