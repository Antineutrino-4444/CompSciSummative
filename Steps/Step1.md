# Step 1 — Mutually Assured Blocks: Architecture & Integration Points

Audit of the existing single-player Tetris engine and the additive
integration layer added in this step. The Tetris engine itself was **not
rewritten**. Only a thin event/observer layer plus a small public API
(`insertGarbage`, `pause(reason)`, `resume(reason)`, `getBoardHeight()`,
`getBoardSnapshot()`) was added so that the upcoming "Mutually Assured
Blocks" strategy layer can hook in without touching engine code.

---

## 1. Audit — where each gameplay event happens

All file/line references are 1-based and relative to the workspace root.

| # | Event | Source location | Notes |
|---|---|---|---|
| 1 | **Piece spawned** | [src/main/java/com/tetris/model/GameState.java](src/main/java/com/tetris/model/GameState.java#L171) — `spawnNextPiece()` | Called from the constructor, after each lock, and after a first-time hold. Block-out is detected here. |
| 2a | **Piece moved** (left/right) | [GameState.java](src/main/java/com/tetris/model/GameState.java#L210) `moveLeft()`, [L227](src/main/java/com/tetris/model/GameState.java#L227) `moveRight()` | Validated against `Board.isValidPosition`. |
| 2b | **Piece moved** (soft / hard drop) | [GameState.java](src/main/java/com/tetris/model/GameState.java#L246) `softDrop()`, [L283](src/main/java/com/tetris/model/GameState.java#L283) `hardDrop()` | Hard drop computes ghost via `Board.getGhostPosition` and immediately calls `lockPiece()`. |
| 2c | **Piece moved** (gravity) | [GameState.java](src/main/java/com/tetris/model/GameState.java#L660) `update()` — gravity tick branch | Driven by `ScoreSystem.getGravityInterval()`. |
| 2d | **Piece rotated** | [GameState.java](src/main/java/com/tetris/model/GameState.java#L304) `rotateCW`, [L312](src/main/java/com/tetris/model/GameState.java#L312) `rotateCCW`, [L327](src/main/java/com/tetris/model/GameState.java#L327) `rotate180`, [L383](src/main/java/com/tetris/model/GameState.java#L383) `tryRotation` | SRS / SRS+ wall kicks tested in order; `lastKickIndex` records which kick succeeded. |
| 3 | **Piece locked** | [GameState.java](src/main/java/com/tetris/model/GameState.java#L494) `lockPiece()` | Performs T-spin detection, calls `Board.lockPiece`, checks lock-out, calls `Board.clearLines`, awards score, then `spawnNextPiece`. |
| 4 | **Line clear resolved** | [Board.java](src/main/java/com/tetris/model/Board.java#L196) `clearLines()`; consumed by [GameState.java](src/main/java/com/tetris/model/GameState.java#L546) inside `lockPiece()` | `Board` snapshots `lastClearedRowIndices` / `lastClearedRowColors` before compacting so the view (and now listeners) can read them. |
| 5 | **Garbage inserted** | *Did not exist before this step.* New: [Board.java](src/main/java/com/tetris/model/Board.java#L322) `insertGarbageRows(...)` and [GameState.java](src/main/java/com/tetris/model/GameState.java#L803) `insertGarbage(...)`. | Adds rows at the bottom, shifts the stack up, lifts the active piece. Overflow at the top of the buffer flags a top-out. |
| 6 | **Hold used** | [GameState.java](src/main/java/com/tetris/model/GameState.java#L405) `hold()` | First hold pulls the next bag piece and triggers a normal spawn; subsequent holds swap directly without using the bag. |
| 7 | **Preview queue advanced** | [BagRandomizer.java](src/main/java/com/tetris/model/BagRandomizer.java#L75) `next()`; called from `spawnNextPiece()` and indirectly through `hold()` | Refills with a new shuffled 7-bag whenever the queue length drops to `MIN_QUEUE_SIZE`. |
| 8 | **Player tops out** | Three sites in [GameState.java](src/main/java/com/tetris/model/GameState.java): block-out in `spawnNextPiece()` (~L182), lock-out in `lockPiece()` (~L530), block-out on hold-swap in `hold()` (~L420). New: garbage-overflow path in `insertGarbage`. | Centralised through new `TopOutEvent` payload with a `TopOutReason` enum. |
| 9 | **Game pauses or resumes** | [GameState.java](src/main/java/com/tetris/model/GameState.java#L693) `togglePause()`, plus new `pause(reason)` / `resume(reason)`. Triggers from [InputHandler.java](src/main/java/com/tetris/controller/InputHandler.java#L198) and [GameController.java](src/main/java/com/tetris/controller/GameController.java#L255) when opening Settings. | All three now route through `setPaused(boolean, String reason)` so listeners always see one canonical event. |
| 10 | **Score / combo / B2B updates** | [ScoreSystem.java](src/main/java/com/tetris/model/ScoreSystem.java#L200) `onLineClear(...)` (combo, B2B chain, perfect clear, level), [L155](src/main/java/com/tetris/model/ScoreSystem.java#L155) `onPieceLocked()` (PPS), [L165](src/main/java/com/tetris/model/ScoreSystem.java#L165) `addSoftDrop`, [L173](src/main/java/com/tetris/model/ScoreSystem.java#L173) `addHardDrop` | A consolidated `ScoreUpdatedEvent` is fired once per lock from `lockPiece()` after both `onLineClear` and `onPieceLocked` have run, so listeners observe a consistent snapshot. |

---

## 2. Integration layer (added)

### 2.1 `GameEventListener` interface

New file: [src/main/java/com/tetris/events/GameEventListener.java](src/main/java/com/tetris/events/GameEventListener.java).

- One `default` no-op method per event so subscribers only override what
  they need.
- All payloads are immutable Java `record`s declared inside the
  interface, so the engine never hands out mutable internal state.
- Two enums included: `MoveKind` (LEFT / RIGHT / SOFT_DROP / HARD_DROP /
  GRAVITY / ROTATE_CW / ROTATE_CCW / ROTATE_180) and `TopOutReason`
  (BLOCK_OUT / LOCK_OUT / GARBAGE_OVERFLOW).

Payloads (exactly the fields requested in the task plus what was needed
to make them useful):

| Event | Payload record |
|---|---|
| Piece spawned | `PieceSpawnedEvent(TetrominoType type, int queueIndex, Color[][] boardSnapshot)` |
| Piece moved/rotated | `PieceMovedEvent(TetrominoType type, MoveKind kind, int kickIndex)` |
| Piece locked | `PieceLockedEvent(TetrominoType type, List<Position> finalCells, Color[][] boardAfter)` |
| Lines cleared | `LinesClearedEvent(int count, List<Integer> rowIndices, boolean tetris, boolean tSpin, boolean tSpinMini, boolean backToBack, boolean perfectClear, int combo)` |
| Garbage inserted | `GarbageInsertedEvent(int rows, int holeColumn, String source)` |
| Hold used | `HoldUsedEvent(TetrominoType swappedOut, TetrominoType swappedIn)` |
| Preview advanced | `PreviewAdvancedEvent(TetrominoType justDealt, List<TetrominoType> upcoming)` |
| Top out | `TopOutEvent(String playerId, TopOutReason reason, Color[][] finalBoard)` |
| Pause changed | `PauseChangedEvent(boolean paused, String reason)` |
| Score updated | `ScoreUpdatedEvent(int score, int combo, int b2bChain, boolean backToBack, String lastAction)` |

`queueIndex` is interpreted as the 0-based monotonic spawn counter for
the current game (i.e. the piece's index in the lifetime spawn order),
which is the most useful identifier for an external strategy layer.

### 2.2 Listener registry on `GameState`

Added fields and methods (all additive — nothing in the existing API
changed):

- `addListener(GameEventListener)`, `removeListener(GameEventListener)`,
  `getListenerCount()`.
- Internal `List<GameEventListener>` is a `CopyOnWriteArrayList` so
  listeners can register/unregister at any time without locking and
  without `ConcurrentModificationException`.
- A private `fire(Consumer<GameEventListener>)` swallows
  `RuntimeException` from buggy listeners so the engine cannot be
  destabilised by external code.
- `setPlayerId(String)` / `getPlayerId()` for use in `TopOutEvent`
  (defaults to `"p1"`).

### 2.3 Public methods external systems can call

| Method (on `GameState`) | Purpose |
|---|---|
| `addListener(GameEventListener)` / `removeListener(...)` | Subscribe to any of the 10 events listed above, including line-clear events. |
| `insertGarbage(int rows, int holeColumn)` and `insertGarbage(int, int, String source)` | Push garbage rows into the bottom of the playfield. Lifts the active piece by the same number of rows; flags `GARBAGE_OVERFLOW` top-out if anything is pushed off the top of the buffer or the active piece can no longer fit. |
| `pause(String reason)` / `resume(String reason)` | Idempotent, fires `PauseChangedEvent` only when the state actually changes. The existing `togglePause()` is preserved and now also routes through this. |
| `getBoardHeight()` | Stack height, measured from the bottom (0 = empty playfield). |
| `getBoardSnapshot()` | Defensive deep-copy `Color[][]` of the entire grid (buffer + visible). |

### 2.4 New helpers on `Board`

- `getStackHeight()` — used by `getBoardHeight()` and available for
  future AI/strategy code.
- `insertGarbageRows(int rows, int holeColumn, Color color)` — does the
  actual row shifting and returns `true` if anything overflowed the top.
- `getGridCopy()` already existed and is reused for snapshots.

---

## 3. Wiring — exactly which call sites fire which event

| Call site | Event(s) fired |
|---|---|
| `spawnNextPiece()` after `bag.next()` | `onPreviewAdvanced` |
| `spawnNextPiece()` after successful spawn | `onPieceSpawned` |
| `spawnNextPiece()` on block-out | `onTopOut(BLOCK_OUT)` |
| `moveLeft / moveRight / softDrop / hardDrop` (success branches) | `onPieceMoved(LEFT / RIGHT / SOFT_DROP / HARD_DROP)` |
| `update()` gravity tick (success) | `onPieceMoved(GRAVITY)` |
| `tryRotation` / `rotate180` (success branches via wrappers) | `onPieceMoved(ROTATE_CW / ROTATE_CCW / ROTATE_180)` |
| `hold()` after a successful swap | `onHoldUsed`. (Also `onPreviewAdvanced` + `onPieceSpawned` when the first hold dips into the bag.) |
| `hold()` when the swapped-in piece can't spawn | `onTopOut(BLOCK_OUT)` |
| `lockPiece()` after `board.lockPiece` | `onPieceLocked` |
| `lockPiece()` if the piece locked entirely in the buffer | `onTopOut(LOCK_OUT)` |
| `lockPiece()` after `clearLines()` if `linesCleared > 0` | `onLinesCleared` |
| `lockPiece()` after `scoreSystem` updates | `onScoreUpdated` |
| `setPaused(boolean, String)` (called by `togglePause`, `pause`, `resume`) | `onPauseChanged` (only on real transitions) |
| `insertGarbage(...)` after the row shift | `onGarbageInserted`, optionally followed by `onTopOut(GARBAGE_OVERFLOW)` |

---

## 4. Acceptance criteria — verification

1. **Tetris game behaves exactly as before.**
   The only behavioural change to existing code paths is that
   `togglePause()` now routes through a private `setPaused` helper that
   sets the same `paused` field; the visible flag toggling is identical.
   Every other engine method retained its original control flow; events
   are emitted at the *end* of the existing success branches. With zero
   listeners registered the dispatch loop short-circuits immediately
   (`if (listeners.isEmpty()) return;`). The IDE language server
   reports no errors in any modified file.

2. **External systems can subscribe to line clear events.**
   `gameState.addListener(new GameEventListener() { @Override public
   void onLinesCleared(LinesClearedEvent e) { ... } });`

3. **External systems can insert garbage through a clean public method.**
   `gameState.insertGarbage(rows, holeColumn)` — or the overload that
   tags the source (`"nuke"`, etc.). Returns `true` if any rows were
   inserted; emits `GarbageInsertedEvent`; emits
   `TopOutEvent(GARBAGE_OVERFLOW)` if the stack overflows or the active
   piece can no longer fit.

4. **External systems can pause and resume the game.**
   `gameState.pause("nuke-charging")` / `gameState.resume("nuke-done")`.
   Both are idempotent and emit `PauseChangedEvent` only when the state
   actually changes. The existing `togglePause()` keyboard binding still
   works and routes through the same code path.

5. **External systems can read current board height and board state.**
   `gameState.getBoardHeight()` returns the stack height; `gameState
   .getBoardSnapshot()` returns a fresh deep-copy `Color[][]`. The
   pre-existing `gameState.getBoard()` accessor is unchanged for any
   read-only callers that prefer to use it directly.

6. **No existing gameplay behavior is broken.**
   - All existing public methods kept their signatures and semantics.
   - No existing field or method was removed.
   - No mutation of existing event ordering inside `lockPiece` —
     scoring still happens before `spawnNextPiece`, line snapshots are
     still captured before compaction, T-spin detection still runs
     before `Board.lockPiece`.
   - The only new mutation outside the additive section is the lifted
     active piece during `insertGarbage`, which is unreachable unless an
     external caller invokes the new method.

---

## 5. Files added / modified

| Status | File |
|---|---|
| **Added** | [src/main/java/com/tetris/events/GameEventListener.java](src/main/java/com/tetris/events/GameEventListener.java) |
| Modified (additive) | [src/main/java/com/tetris/model/GameState.java](src/main/java/com/tetris/model/GameState.java) |
| Modified (additive) | [src/main/java/com/tetris/model/Board.java](src/main/java/com/tetris/model/Board.java) |

No other files were touched. The nuke-system implementation is
intentionally **not** included in this step.

---

## 6. Quick example — what the strategy layer will look like

```java
GameState game = controller.getGameState();
game.setPlayerId("p1");
game.addListener(new GameEventListener() {
    @Override public void onLinesCleared(LinesClearedEvent e) {
        if (e.tetris() && e.backToBack()) {
            // Counter-attack: drop 4 garbage rows on... ourselves, for now.
            game.insertGarbage(4, ThreadLocalRandom.current().nextInt(10), "nuke");
        }
    }
    @Override public void onTopOut(TopOutEvent e) {
        System.out.println(e.playerId() + " topped out via " + e.reason());
    }
});
```

That's the entire surface area the Mutually Assured Blocks layer needs.
The engine has no compile-time dependency on it.

---

## Step 1 refinement — patterned garbage support

The original `insertGarbage(rows, holeColumn[, source])` API only
supports clean garbage with one shared hole column. The Mutually Assured
Blocks layer will eventually need *messy* garbage where each row can
have a different hole pattern (radiation, MIRV split-rows, themed nuke
garbage with custom colors, etc.). This refinement adds a more general
API **without removing or changing the existing convenience overloads**.

### What's new

- **New file** [src/main/java/com/tetris/events/GarbageRowPattern.java](src/main/java/com/tetris/events/GarbageRowPattern.java)
  — immutable `record GarbageRowPattern(List<Integer> holeColumns, Color color, String tag)`.
  Defensively copies the hole list. Provides factories
  `GarbageRowPattern.clean(holeColumn)` and
  `GarbageRowPattern.cleanRepeated(rows, holeColumn)`.

- **`Board`** gains an overload:
  `boolean insertGarbageRows(List<GarbageRowPattern> patterns, Color defaultColor)`.
  Inserts patterns in iteration order — the **last** element ends up on
  the bottom-most playfield row. Per-row color overrides
  `defaultColor`; out-of-range hole indices are silently ignored. The
  original `insertGarbageRows(int, int, Color)` is unchanged.

- **`GameState`** gains:
  `boolean insertGarbagePattern(List<GarbageRowPattern> rows, String source)`.
  This is now the canonical garbage-insertion entry point. It performs
  the same active-piece lift, the same overflow / `GARBAGE_OVERFLOW`
  top-out detection, and dispatches `GarbageInsertedEvent`.

- **`insertGarbage(rows, holeColumn[, source])`** still compiles and
  still works exactly as before — internally it now builds
  `GarbageRowPattern.cleanRepeated(rows, holeColumn)` and delegates to
  `insertGarbagePattern`.

- **`GarbageInsertedEvent` payload was generalised** to
  `GarbageInsertedEvent(int rows, List<List<Integer>> holeColumnsByRow, String source)`.
  For clean garbage the outer list contains `rows` entries, each a
  single-element list `[holeColumn]`. For patterned garbage each inner
  list reflects that row's hole layout exactly. Both inner and outer
  lists are unmodifiable snapshots, suitable for logging / debug UI /
  replays.

### Behavior preservation

- Active-piece lift, overflow detection, `gameOver` semantics, and
  top-out reason (`GARBAGE_OVERFLOW`) are identical to the Step 1
  implementation.
- With no listeners registered and no external caller invoking either
  `insertGarbage` or `insertGarbagePattern`, the game runs exactly as
  before — both APIs are dormant.

### Acceptance check

| Criterion | Status |
|---|---|
| Existing `insertGarbage` overloads still compile and work | ✓ — same signatures, same return value, same observable behavior |
| Patterned garbage can insert rows with different hole columns | ✓ — `insertGarbagePattern(List<GarbageRowPattern>, String)` |
| Garbage overflow / top-out still works | ✓ — single overflow path used by both APIs |
| `GarbageInsertedEvent` carries enough data for debug UI / replays | ✓ — full per-row hole layout plus source tag |
| No nuke logic implemented | ✓ — only the garbage data plumbing |
