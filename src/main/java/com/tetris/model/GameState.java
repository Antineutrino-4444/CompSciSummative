package com.tetris.model;

import com.tetris.events.GameEventListener;
import com.tetris.events.GameEventListener.GarbageInsertedEvent;
import com.tetris.events.GameEventListener.HoldUsedEvent;
import com.tetris.events.GameEventListener.LinesClearedEvent;
import com.tetris.events.GameEventListener.MoveKind;
import com.tetris.events.GameEventListener.PauseChangedEvent;
import com.tetris.events.GameEventListener.PieceLockedEvent;
import com.tetris.events.GameEventListener.PieceMovedEvent;
import com.tetris.events.GameEventListener.PieceSpawnedEvent;
import com.tetris.events.GameEventListener.PreviewAdvancedEvent;
import com.tetris.events.GameEventListener.ScoreUpdatedEvent;
import com.tetris.events.GameEventListener.TopOutEvent;
import com.tetris.events.GameEventListener.TopOutReason;
import com.tetris.events.GarbageRowPattern;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

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
    private BagRandomizer bag;
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

    // ─────────────────────── Step 22: All-Spin Tracking ──────────

    /**
     * Step 22 \u2014 placement-input categories used by the MAB all-spin
     * rule. Distinct from {@link #lastMoveWasRotation} (which the legacy
     * T-spin scoring relies on) so we can apply modern rules without
     * changing single-player scoring.
     */
    public enum LastPlacementInput { NONE, ROTATE, MOVE, SOFT_DROP, GRAVITY, HARD_DROP }

    private LastPlacementInput lastPlacementInput = LastPlacementInput.NONE;
    /**
     * Step 22 \u2014 true while the current piece is still eligible for
     * an immobile spin: a rotation has happened since spawn (or since the
     * last horizontal move / hold), and no horizontal translation has
     * happened since that last rotation. Soft drop, hard drop, and
     * gravity do not clear this flag.
     */
    private boolean spinCandidate = false;
    private int successfulRotationCountThisPiece = 0;
    private boolean movedHorizontallyAfterLastRotation = false;
    /** Step 22 \u2014 whether the most recent lock was a hard drop. */
    private boolean hardDroppedThisLock = false;

    // ─────────────────────── Game State ─────────────────────────

    private boolean gameOver;
    private boolean paused;
    private boolean gravityFrozen;
    private double mabGravityMultiplier = 1.0;

    /** Flag indicating a new piece was just spawned (for IRS/IHS). */
    private boolean justSpawned;

    /** Timestamp of the last gravity drop. */
    private long lastGravityDrop;

    // ─────────────────────── External integration ──────────────

    /** Default color used when external systems push garbage rows. */
    private static final Color GARBAGE_COLOR = new Color(120, 120, 120);

    /** Listeners observing gameplay events (Mutually Assured Blocks, etc.). */
    private final List<GameEventListener> listeners = new CopyOnWriteArrayList<>();

    /** Monotonic counter incremented each time a piece is spawned (queue index). */
    private int pieceCounter = 0;

    /** Identifier reported in TopOutEvent — single-player default. */
    private String playerId = "p1";

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

    /**
     * Step 21 — MAB-only hook to swap the piece source after construction.
     *
     * <p>Replaces the bag, clears the hold slot, and re-spawns the current
     * piece from the new source so the player starts from the deterministic
     * shared sequence. Must be called BEFORE the player ever sees the board
     * (typically immediately after construction, before any input).
     *
     * <p>This is offline-only and must NOT be used by normal single-player
     * Tetris; doing so would change observable randomization behavior.
     */
    public void replacePieceSourceForMabSharedSequence(BagRandomizer newBag) {
        if (newBag == null) throw new IllegalArgumentException("newBag");
        this.bag = newBag;
        this.holdPiece = null;
        this.holdUsed = false;
        // pieceCounter increments inside spawnNextPiece(); rewind so the
        // new sequence's piece #1 is reported as piece index 0 to listeners.
        this.pieceCounter = 0;
        spawnNextPiece();
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
        // Notify listeners that the preview queue advanced.
        fire(l -> l.onPreviewAdvanced(new PreviewAdvancedEvent(
                nextType,
                bag.peek(Settings.get().getPreviewCount()))));

        currentPiece = Tetromino.spawn(nextType, Board.WIDTH);

        // Check for block-out (can't place the spawned piece)
        if (!board.isValidPosition(currentPiece)) {
            gameOver = true;
            fire(l -> l.onTopOut(new TopOutEvent(
                    playerId, TopOutReason.BLOCK_OUT, board.getGridCopy())));
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
        // Step 22 — reset all-spin tracking for the new piece.
        lastPlacementInput = LastPlacementInput.NONE;
        spinCandidate = false;
        successfulRotationCountThisPiece = 0;
        movedHorizontallyAfterLastRotation = false;
        hardDroppedThisLock = false;
        pieceCounter++;

        final int idx = pieceCounter - 1;
        final TetrominoType spawnedType = nextType;
        fire(l -> l.onPieceSpawned(new PieceSpawnedEvent(
                spawnedType, idx, board.getGridCopy())));
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
            // Step 22 — horizontal movement cancels spin candidacy.
            lastPlacementInput = LastPlacementInput.MOVE;
            spinCandidate = false;
            movedHorizontallyAfterLastRotation = true;
            onSuccessfulMove();
            firePieceMoved(MoveKind.LEFT, 0);
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
            // Step 22 — horizontal movement cancels spin candidacy.
            lastPlacementInput = LastPlacementInput.MOVE;
            spinCandidate = false;
            movedHorizontallyAfterLastRotation = true;
            onSuccessfulMove();
            firePieceMoved(MoveKind.RIGHT, 0);
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
            // Step 22 — vertical motion does NOT cancel spin candidacy.
            lastPlacementInput = LastPlacementInput.SOFT_DROP;
            scoreSystem.addSoftDrop(1);
            // Update lowest Y
            int currentY = currentPiece.getBoardPosition().getY();
            if (currentY > lowestY) {
                lowestY = currentY;
                lockResets = 0;
            }
            Tetromino below = currentPiece.moveDown();
            if (board.isValidPosition(below)) {
                lockDelayActive = false;
            } else if (!lockDelayActive) {
                lockDelayActive = true;
                lockDelayStart = System.currentTimeMillis();
            }
            firePieceMoved(MoveKind.SOFT_DROP, 0);
            return true;
        }
        if (!board.isValidPosition(currentPiece.moveDown()) && !lockDelayActive) {
            lockDelayActive = true;
            lockDelayStart = System.currentTimeMillis();
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
        // Step 22 — hard drop is a locking command, not a spin-canceling
        // placement move. Preserve spin candidacy so the player can rotate
        // into a slot and hard-drop for spin credit.
        lastPlacementInput = LastPlacementInput.HARD_DROP;
        hardDroppedThisLock = true;
        firePieceMoved(MoveKind.HARD_DROP, 0);
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
        boolean ok = tryRotation(currentPiece.rotateCW(), currentPiece.getRotationState(),
                (currentPiece.getRotationState() + 1) % 4);
        if (ok) firePieceMoved(MoveKind.ROTATE_CW, lastKickIndex);
        return ok;
    }

    /**
     * Rotates the current piece 90° counter-clockwise with SRS wall kicks.
     *
     * @return true if the rotation was successful
     */
    public boolean rotateCCW() {
        if (!canAct()) return false;
        boolean ok = tryRotation(currentPiece.rotateCCW(), currentPiece.getRotationState(),
                (currentPiece.getRotationState() + 3) % 4);
        if (ok) firePieceMoved(MoveKind.ROTATE_CCW, lastKickIndex);
        return ok;
    }

    /**
     * Rotates the current piece 180° with SRS+ wall kicks (TETR.IO / Jstris).
     *
     * Same algorithm as 90° rotation: try the basic rotation, then each
     * 180° kick offset in order. First valid position wins.
     *
     * @return true if the rotation was successful
     */
    public boolean rotate180() {
        if (!canAct()) return false;
        Tetromino rotated = currentPiece.rotate180();
        int fromState = currentPiece.getRotationState();
        int toState = (fromState + 2) % 4;

        // Test 0: basic rotation, no offset
        if (board.isValidPosition(rotated)) {
            currentPiece = rotated;
            lastMoveWasRotation = true;
            lastKickIndex = 0;
            // Step 22 — spin candidacy from a 180° rotation as well.
            lastPlacementInput = LastPlacementInput.ROTATE;
            spinCandidate = true;
            successfulRotationCountThisPiece++;
            movedHorizontallyAfterLastRotation = false;
            onSuccessfulMove();
            firePieceMoved(MoveKind.ROTATE_180, 0);
            return true;
        }

        // Tests 1–N: SRS+ 180° kick offsets
        Position[] kicks = SRSData.getKicks180(currentPiece.getType(), fromState, toState);
        for (int i = 0; i < kicks.length; i++) {
            Tetromino kicked = rotated.translate(kicks[i].getX(), kicks[i].getY());
            if (board.isValidPosition(kicked)) {
                currentPiece = kicked;
                lastMoveWasRotation = true;
                // Last kick index of 4 is the "TST-equivalent" promotion slot
                // for 90° kicks; 180° kicks don't have an equivalent so we
                // keep the index for stat tracking but don't promote.
                lastKickIndex = i + 1;
                lastPlacementInput = LastPlacementInput.ROTATE;
                spinCandidate = true;
                successfulRotationCountThisPiece++;
                movedHorizontallyAfterLastRotation = false;
                onSuccessfulMove();
                firePieceMoved(MoveKind.ROTATE_180, lastKickIndex);
                return true;
            }
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
            // Step 22 — mark spin candidate; cleared by horizontal move/hold.
            lastPlacementInput = LastPlacementInput.ROTATE;
            spinCandidate = true;
            successfulRotationCountThisPiece++;
            movedHorizontallyAfterLastRotation = false;
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
                lastPlacementInput = LastPlacementInput.ROTATE;
                spinCandidate = true;
                successfulRotationCountThisPiece++;
                movedHorizontallyAfterLastRotation = false;
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
        TetrominoType incoming;

        if (holdPiece == null) {
            // Hold slot was empty → store current, spawn next from bag
            holdPiece = currentType;
            spawnNextPiece();
            incoming = currentPiece == null ? null : currentPiece.getType();
        } else {
            // Swap: store current type, spawn the held type
            TetrominoType swapType = holdPiece;
            holdPiece = currentType;
            currentPiece = Tetromino.spawn(swapType, Board.WIDTH);
            if (!board.isValidPosition(currentPiece)) {
                gameOver = true;
                fire(l -> l.onTopOut(new TopOutEvent(
                        playerId, TopOutReason.BLOCK_OUT, board.getGridCopy())));
                return false;
            }
            // Reset lock state for the swapped-in piece
            lockDelayActive = false;
            lockResets = 0;
            lowestY = currentPiece.getBoardPosition().getY();
            lastMoveWasRotation = false;
            lastKickIndex = 0;
            // Step 22 — hold clears spin candidacy for both the swapped-out
            // and swapped-in piece.
            lastPlacementInput = LastPlacementInput.NONE;
            spinCandidate = false;
            successfulRotationCountThisPiece = 0;
            movedHorizontallyAfterLastRotation = false;
            hardDroppedThisLock = false;
            incoming = swapType;
        }

        holdUsed = true;
        final TetrominoType outType = currentType;
        final TetrominoType inType = incoming;
        fire(l -> l.onHoldUsed(new HoldUsedEvent(outType, inType)));
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

        updateLockDelay(true);
    }

    /**
     * Updates the lock delay state based on whether the piece is resting
     * on the ground (can't move down).
     */
    private void updateLockDelay(boolean allowReset) {
        Tetromino below = currentPiece.moveDown();
        boolean onGround = !board.isValidPosition(below);

        if (onGround) {
            if (!lockDelayActive) {
                // Start a new lock delay
                lockDelayActive = true;
                lockDelayStart = System.currentTimeMillis();
            } else if (allowReset && lockResets < Settings.get().getMaxLockResets()) {
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

        // ──── Step 22: All-spin immobile detection (before board write) ────
        // The board does not yet contain this piece's cells, so testing
        // translations on `currentPiece` against the board is correct.
        boolean immobileSpin = false;
        if (spinCandidate
                && !movedHorizontallyAfterLastRotation
                && successfulRotationCountThisPiece > 0
                && currentPiece.getType() != TetrominoType.O
                && SpinDetector.isImmobile(board, currentPiece)) {
            immobileSpin = true;
        }
        boolean anySpin = isTSpin || isTSpinMini || immobileSpin;
        // Build the human-readable spin label up-front (used by the
        // detailed lock event below, regardless of line count).
        final String spinLabel = !anySpin ? ""
                : (isTSpin ? "T Spin"
                        : (isTSpinMini ? "T Spin Mini"
                                : (currentPiece.getType().name() + " Spin")));
        final TetrominoType lockedTypeForSpin = currentPiece.getType();
        final boolean tSpinFinal = isTSpin;
        final boolean tSpinMiniFinal = isTSpinMini;
        final boolean spinFinal = anySpin;
        final boolean hardDroppedFinal = hardDroppedThisLock;

        // Snapshot final cells + type before locking writes into the board.
        final TetrominoType lockedType = currentPiece.getType();
        final List<Position> finalCells = new ArrayList<>(Arrays.asList(currentPiece.getAbsoluteCells()));

        // ──── Lock the piece ────
        board.lockPiece(currentPiece);

        // Notify listeners of the lock BEFORE line-clear shifting.
        final Color[][] boardAfterLock = board.getGridCopy();
        fire(l -> l.onPieceLocked(new PieceLockedEvent(lockedType, finalCells, boardAfterLock)));

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
            fire(l -> l.onTopOut(new TopOutEvent(
                    playerId, TopOutReason.LOCK_OUT, board.getGridCopy())));
            return;
        }

        // ──── Clear lines ────
        final List<Integer> rowIndices = new ArrayList<>(board.getLastClearedRowIndices());
        int linesCleared = board.clearLines();
        // clearLines() refreshes the snapshot — read it AFTER the call.
        rowIndices.clear();
        rowIndices.addAll(board.getLastClearedRowIndices());

        // ──── All Clear / Perfect Clear detection ────
        boolean perfectClear = linesCleared > 0 && board.isCompletelyEmpty();

        // ──── Award score ────
        scoreSystem.onLineClear(linesCleared, isTSpin, isTSpinMini, perfectClear);
        scoreSystem.onPieceLocked();

        // ──── Fire line-clear + score events ────
        if (linesCleared > 0) {
            final int cleared = linesCleared;
            final boolean tSpin = isTSpin;
            final boolean tSpinMini = isTSpinMini;
            final boolean pc = perfectClear;
            final boolean b2b = scoreSystem.isBackToBack();
            final int combo = scoreSystem.getCombo();
            fire(l -> l.onLinesCleared(new LinesClearedEvent(
                    cleared, rowIndices,
                    cleared == 4, tSpin, tSpinMini,
                    b2b, pc, combo)));
        }

        // ──── Step 22: detailed unified lock result (always fires) ────
        final int detailedLines = linesCleared;
        final boolean detailedPC = perfectClear;
        final boolean detailedB2B = scoreSystem.isBackToBack();
        final int detailedCombo = scoreSystem.getCombo();
        final int detailedPieces = pieceCounter;
        fire(l -> l.onPieceLockedDetailed(new com.tetris.events.PieceLockResult(
                lockedTypeForSpin,
                detailedLines,
                spinFinal,
                tSpinFinal,
                tSpinMiniFinal,
                spinLabel,
                detailedPC,
                detailedB2B,
                detailedCombo,
                hardDroppedFinal,
                detailedPieces)));

        fire(l -> l.onScoreUpdated(new ScoreUpdatedEvent(
                scoreSystem.getScore(),
                scoreSystem.getCombo(),
                scoreSystem.getB2bChain(),
                scoreSystem.isBackToBack(),
                scoreSystem.getLastAction())));

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
        int interval = getEffectiveGravityInterval();
        if (gravityFrozen) { lastGravityDrop = now; }
        else if (now - lastGravityDrop >= interval) {
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
                firePieceMoved(MoveKind.GRAVITY, 0);
            }
            updateLockDelay(false);
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

    public boolean isGravityFrozen() { return gravityFrozen; }

    public void setGravityFrozen(boolean frozen) {
        this.gravityFrozen = frozen;
        if (!frozen) lastGravityDrop = System.currentTimeMillis();
    }

    public void setMabGravityMultiplier(double multiplier) {
        double next = multiplier;
        if (!Double.isFinite(next) || next <= 0.0) next = 1.0;
        if (Math.abs(mabGravityMultiplier - next) < 0.0001) return;
        mabGravityMultiplier = next;
        lastGravityDrop = System.currentTimeMillis();
    }

    public double getMabGravityMultiplier() {
        return mabGravityMultiplier;
    }

    public int getEffectiveGravityInterval() {
        int base = scoreSystem.getGravityInterval();
        return Math.max(1, (int) Math.round(base / mabGravityMultiplier));
    }

    public void togglePause() {
        setPaused(!paused, "toggle");
    }

    /** Pauses the game and notifies listeners with the supplied reason tag. */
    public void pause(String reason) {
        if (!paused) setPaused(true, reason);
    }

    /** Resumes the game and notifies listeners with the supplied reason tag. */
    public void resume(String reason) {
        if (paused) setPaused(false, reason);
    }

    private void setPaused(boolean newState, String reason) {
        if (paused == newState) return;
        paused = newState;
        final String r = reason == null ? "" : reason;
        fire(l -> l.onPauseChanged(new PauseChangedEvent(newState, r)));
    }

    public void restart() {
        // Create a fresh game state (handled by the controller creating a new GameState)
    }

    // ═══════════════════════════════════════════════════════════════
    // ACCESSORS
    // ═══════════════════════════════════════════════════════════════

    public Board getBoard() { return board; }

    /** Step 22 \u2014 probe-only: true if a spin would credit on lock right now. */
    public boolean isSpinCandidatePending() {
        return spinCandidate
                && !movedHorizontallyAfterLastRotation
                && successfulRotationCountThisPiece > 0;
    }
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

    // ═══════════════════════════════════════════════════════════════
    // EXTERNAL INTEGRATION API (Mutually Assured Blocks etc.)
    // ═══════════════════════════════════════════════════════════════
    // Everything in this section is purely additive: existing gameplay
    // is unchanged whether or not any listeners are registered.

    /** Registers a listener. Safe to call from any thread. */
    public void addListener(GameEventListener listener) {
        if (listener != null) listeners.add(listener);
    }

    /** Removes a previously registered listener. */
    public void removeListener(GameEventListener listener) {
        if (listener != null) listeners.remove(listener);
    }

    /** Number of currently registered listeners (mainly for tests). */
    public int getListenerCount() {
        return listeners.size();
    }

    /** Sets the player id reported in {@link TopOutEvent}. */
    public void setPlayerId(String id) {
        if (id != null && !id.isEmpty()) this.playerId = id;
    }

    /** Returns the player id used in events. */
    public String getPlayerId() {
        return playerId;
    }

    /**
     * Pushes garbage rows into the bottom of the board. Each row is
     * filled with a uniform garbage color except for {@code holeColumn}.
     * Convenience overload — internally builds a repeated clean
     * {@link GarbageRowPattern} and calls
     * {@link #insertGarbagePattern(List, String)}.
     *
     * @param rows         number of garbage rows (no-op if &le; 0)
     * @param holeColumn   column for the hole, clamped to [0, WIDTH-1]
     * @return {@code true} if any rows were inserted
     */
    public boolean insertGarbage(int rows, int holeColumn) {
        return insertGarbage(rows, holeColumn, "external");
    }

    /**
     * As {@link #insertGarbage(int, int)} but tags the originating system
     * (e.g. "nuke", "test") in the dispatched event.
     */
    public boolean insertGarbage(int rows, int holeColumn, String source) {
        if (rows <= 0) return false;
        int clamped = Math.max(0, Math.min(Board.WIDTH - 1, holeColumn));
        return insertGarbagePattern(GarbageRowPattern.cleanRepeated(rows, clamped), source);
    }

    /**
     * General patterned-garbage entry point. Each {@link GarbageRowPattern}
     * specifies the hole columns (zero or more) and optional color/tag for
     * one garbage row. The first element is inserted first and is shifted
     * up as later rows are inserted, so the LAST element of {@code rows}
     * ends up on the bottom-most row of the playfield.
     *
     * <p>Fires {@link GameEventListener#onGarbageInserted}, and — if the
     * shift overflows the buffer or buries the active piece — also fires
     * {@link GameEventListener#onTopOut} with reason
     * {@link TopOutReason#GARBAGE_OVERFLOW}.
     *
     * @param rows    rows to insert (null/empty = no-op)
     * @param source  free-form tag identifying the originator, e.g.
     *                "nuke", "radiation", "test"
     * @return {@code true} if any rows were inserted
     */
    public boolean insertGarbagePattern(List<GarbageRowPattern> rows, String source) {
        if (rows == null || rows.isEmpty() || gameOver) return false;

        boolean overflow = board.insertGarbageRows(rows, GARBAGE_COLOR);
        int rowCount = rows.size();

        // Lift the active piece by the same number of rows so it doesn't
        // get clipped through the rising stack. If that's impossible the
        // game tops out.
        if (currentPiece != null) {
            Tetromino lifted = currentPiece.translate(0, -rowCount);
            if (board.isValidPosition(lifted)) {
                currentPiece = lifted;
                int y = currentPiece.getBoardPosition().getY();
                if (y < lowestY) lowestY = y;
            } else {
                overflow = true;
            }
        }

        // Build the per-row hole list snapshot for the event in the same
        // top-to-bottom order they were inserted (i.e. matching the
        // `rows` argument's iteration order).
        List<List<Integer>> holesByRow = new ArrayList<>(rowCount);
        for (GarbageRowPattern p : rows) {
            holesByRow.add(p == null ? List.of() : List.copyOf(p.holeColumns()));
        }
        final int rCount = rowCount;
        final List<List<Integer>> holesSnapshot = List.copyOf(holesByRow);
        final String src = source == null ? "external" : source;
        fire(l -> l.onGarbageInserted(new GarbageInsertedEvent(rCount, holesSnapshot, src)));

        if (overflow) {
            gameOver = true;
            fire(l -> l.onTopOut(new TopOutEvent(
                    playerId, TopOutReason.GARBAGE_OVERFLOW, board.getGridCopy())));
        }
        return true;
    }

    /**
     * Returns the current stack height (number of rows from the bottom
     * up to the highest occupied cell).
     */
    public int getBoardHeight() {
        return board.getStackHeight();
    }

    /**
     * Returns a defensive deep-copy of the current board grid. Indexed as
     * {@code [row][col]}, with row 0 at the top of the buffer zone.
     */
    public Color[][] getBoardSnapshot() {
        return board.getGridCopy();
    }

    // ─────────────────── private dispatch helpers ────────────────

    private void fire(Consumer<GameEventListener> action) {
        if (listeners.isEmpty()) return;
        for (GameEventListener l : listeners) {
            try { action.accept(l); }
            catch (RuntimeException ignored) { /* a buggy listener must not break the engine */ }
        }
    }

    private void firePieceMoved(MoveKind kind, int kickIndex) {
        if (currentPiece == null || listeners.isEmpty()) return;
        final TetrominoType type = currentPiece.getType();
        fire(l -> l.onPieceMoved(new PieceMovedEvent(type, kind, kickIndex)));
    }
}
