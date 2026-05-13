package com.tetris.events;

import com.tetris.model.Position;
import com.tetris.model.TetrominoType;

import java.awt.Color;
import java.util.List;

/**
 * GameEventListener.java
 * ======================
 * Integration point for the "Mutually Assured Blocks" strategy layer
 * (and any future external system) to observe gameplay events without
 * being tightly coupled to the core Tetris engine.
 *
 * <p>Listeners are registered with
 * {@link com.tetris.model.GameState#addListener(GameEventListener)} and
 * are notified on the same thread that runs the game loop (the Swing EDT).
 * Implementations <b>must not</b> block or directly mutate engine state
 * from inside a callback — instead, call back into the public API on
 * {@link com.tetris.model.GameState} (e.g. {@code insertGarbage},
 * {@code pause}, {@code resume}).
 *
 * <p>All callback methods are {@code default} no-ops so a listener only
 * needs to implement the events it actually cares about.
 */
public interface GameEventListener {

    // ───── Events ──────────────────────────────────────────────

    /** Fired immediately after a new piece is placed at the spawn position. */
    default void onPieceSpawned(PieceSpawnedEvent e) {}

    /** Fired after any successful translation or rotation of the active piece. */
    default void onPieceMoved(PieceMovedEvent e) {}

    /** Fired immediately after a piece is written into the board (before line clears). */
    default void onPieceLocked(PieceLockedEvent e) {}

    /**
     * Step 22 \u2014 fired exactly once per locked piece, after line clears
     * have been resolved. Carries the unified spin / line-clear / B2B /
     * combo / hard-drop summary used by Mutually Assured Blocks. Default
     * implementation is a no-op so single-player Tetris is unaffected.
     */
    default void onPieceLockedDetailed(PieceLockResult e) {}

    /** Fired only when at least one line is cleared by a lock. */
    default void onLinesCleared(LinesClearedEvent e) {}

    /** Fired after garbage rows have been pushed into the board. */
    default void onGarbageInserted(GarbageInsertedEvent e) {}

    /** Fired after a successful hold swap. */
    default void onHoldUsed(HoldUsedEvent e) {}

    /** Fired whenever the bag deals a piece (i.e. the preview queue advances). */
    default void onPreviewAdvanced(PreviewAdvancedEvent e) {}

    /** Fired when the player tops out (block out, lock out, or garbage overflow). */
    default void onTopOut(TopOutEvent e) {}

    /** Fired whenever the paused flag changes. */
    default void onPauseChanged(PauseChangedEvent e) {}

    /** Fired whenever score / combo / back-to-back state may have changed. */
    default void onScoreUpdated(ScoreUpdatedEvent e) {}

    // ───── Enums ───────────────────────────────────────────────

    enum MoveKind { LEFT, RIGHT, SOFT_DROP, HARD_DROP, GRAVITY, ROTATE_CW, ROTATE_CCW, ROTATE_180 }

    enum TopOutReason { BLOCK_OUT, LOCK_OUT, GARBAGE_OVERFLOW }

    // ───── Payload records ─────────────────────────────────────

    /**
     * @param type            the spawned tetromino type
     * @param queueIndex      monotonic spawn counter (0 for the first piece of the game)
     * @param boardSnapshot   defensive deep-copy of the board grid at spawn time
     */
    record PieceSpawnedEvent(TetrominoType type, int queueIndex, Color[][] boardSnapshot) {}

    /**
     * @param type        the moving piece's type
     * @param kind        which kind of movement occurred
     * @param kickIndex   SRS kick test index used (0 = no kick / N/A)
     */
    record PieceMovedEvent(TetrominoType type, MoveKind kind, int kickIndex) {}

    /**
     * @param type        the type of the piece that just locked
     * @param finalCells  the four absolute board cells the piece occupied at lock
     * @param boardAfter  defensive deep-copy of the board immediately after lock,
     *                    BEFORE any line-clear shifting
     */
    record PieceLockedEvent(TetrominoType type, List<Position> finalCells, Color[][] boardAfter) {}

    /**
     * @param count          number of lines cleared by this lock (1–4)
     * @param rowIndices     y-indices of the cleared rows in the original grid (top→bottom)
     * @param tetris         true if exactly 4 lines were cleared (a "Tetris")
     * @param tSpin          true if the lock was a full T-spin
     * @param tSpinMini      true if the lock was a T-spin mini
     * @param backToBack     true if this clear continued a back-to-back chain
     * @param perfectClear   true if the board is completely empty after the clear
     * @param combo          combo counter AFTER this clear (0 = first of combo)
     */
    record LinesClearedEvent(int count, List<Integer> rowIndices,
                             boolean tetris, boolean tSpin, boolean tSpinMini,
                             boolean backToBack, boolean perfectClear, int combo) {}

    /**
     * Garbage was just inserted at the bottom of the playfield. The event
     * carries the full per-row hole layout so external systems (debug UI,
     * replays, the upcoming Mutually Assured Blocks layer) can inspect
     * exactly what was inserted, including patterned / radiation garbage
     * with multiple holes per row.
     *
     * @param rows               number of rows actually inserted
     * @param holeColumnsByRow   for each inserted row (in insertion order,
     *                           bottom-most row last) the columns that
     *                           were left empty. Always non-null and the
     *                           outer list has size {@code rows}.
     * @param source             free-form tag identifying the originator
     *                           (e.g. "test", "nuke", "external")
     */
    record GarbageInsertedEvent(int rows, List<List<Integer>> holeColumnsByRow, String source) {}

    /**
     * @param swappedOut   the type that was put INTO the hold slot
     * @param swappedIn    the type that was placed onto the board (null on first hold)
     */
    record HoldUsedEvent(TetrominoType swappedOut, TetrominoType swappedIn) {}

    /**
     * @param justDealt   the type the bag just produced
     * @param upcoming    snapshot of the next preview pieces after this deal
     */
    record PreviewAdvancedEvent(TetrominoType justDealt, List<TetrominoType> upcoming) {}

    /**
     * @param playerId    identifier for the player ("p1" in single player)
     * @param reason      why the top-out happened
     * @param finalBoard  defensive deep-copy of the final board state
     */
    record TopOutEvent(String playerId, TopOutReason reason, Color[][] finalBoard) {}

    /**
     * @param paused   the new paused state
     * @param reason   free-form description of why pause changed (e.g. "user", "settings", "external")
     */
    record PauseChangedEvent(boolean paused, String reason) {}

    /**
     * @param score        total score after the update
     * @param combo        current combo counter (-1 = inactive)
     * @param b2bChain     length of current back-to-back chain (0 = inactive)
     * @param backToBack   whether B2B is currently armed
     * @param lastAction   human-readable description of the most recent scoring action
     */
    record ScoreUpdatedEvent(int score, int combo, int b2bChain,
                             boolean backToBack, String lastAction) {}
}
