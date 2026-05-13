# Step 2 — Mutually Assured Blocks: Match-State Layer

This step builds the **strategy layer** that wraps the existing
single-player Tetris engine. The Tetris engine itself was not touched;
the new layer subscribes through the Step-1 `GameEventListener` facade
and aggregates events into per-participant strategic state. No nuke
logic, no AI driver — just the structural foundation. The game is
offline-only (local 1v1 / PvE / practice/debug); networking and online
multiplayer are permanently out of scope.

---

## 1. Files added / modified

All new code lives in a single new package, `com.tetris.mab`. **No
existing file was modified.**

| Status | File |
|---|---|
| Added | [src/main/java/com/tetris/mab/MatchMode.java](src/main/java/com/tetris/mab/MatchMode.java) |
| Added | [src/main/java/com/tetris/mab/MatchDifficulty.java](src/main/java/com/tetris/mab/MatchDifficulty.java) |
| Added | [src/main/java/com/tetris/mab/MatchPhase.java](src/main/java/com/tetris/mab/MatchPhase.java) |
| Added | [src/main/java/com/tetris/mab/ParticipantId.java](src/main/java/com/tetris/mab/ParticipantId.java) |
| Added | [src/main/java/com/tetris/mab/TimerAdvanceMode.java](src/main/java/com/tetris/mab/TimerAdvanceMode.java) |
| Added | [src/main/java/com/tetris/mab/SiloDamageState.java](src/main/java/com/tetris/mab/SiloDamageState.java) |
| Added | [src/main/java/com/tetris/mab/PieceCountdownTimer.java](src/main/java/com/tetris/mab/PieceCountdownTimer.java) |
| Added | [src/main/java/com/tetris/mab/PieceTimerManager.java](src/main/java/com/tetris/mab/PieceTimerManager.java) |
| Added | [src/main/java/com/tetris/mab/NukeBuildState.java](src/main/java/com/tetris/mab/NukeBuildState.java) |
| Added | [src/main/java/com/tetris/mab/SiloState.java](src/main/java/com/tetris/mab/SiloState.java) |
| Added | [src/main/java/com/tetris/mab/RadarIntelState.java](src/main/java/com/tetris/mab/RadarIntelState.java) |
| Added | [src/main/java/com/tetris/mab/UpgradeState.java](src/main/java/com/tetris/mab/UpgradeState.java) |
| Added | [src/main/java/com/tetris/mab/ActionCodeProgressState.java](src/main/java/com/tetris/mab/ActionCodeProgressState.java) |
| Added | [src/main/java/com/tetris/mab/ActiveLaunchState.java](src/main/java/com/tetris/mab/ActiveLaunchState.java) |
| Added | [src/main/java/com/tetris/mab/IncomingThreatState.java](src/main/java/com/tetris/mab/IncomingThreatState.java) |
| Added | [src/main/java/com/tetris/mab/RestraintState.java](src/main/java/com/tetris/mab/RestraintState.java) |
| Added | [src/main/java/com/tetris/mab/SecondStrikeState.java](src/main/java/com/tetris/mab/SecondStrikeState.java) |
| Added | [src/main/java/com/tetris/mab/CivilDefenseState.java](src/main/java/com/tetris/mab/CivilDefenseState.java) |
| Added | [src/main/java/com/tetris/mab/DefconState.java](src/main/java/com/tetris/mab/DefconState.java) |
| Added | [src/main/java/com/tetris/mab/ParticipantState.java](src/main/java/com/tetris/mab/ParticipantState.java) |
| Added | [src/main/java/com/tetris/mab/MatchEventLogEntry.java](src/main/java/com/tetris/mab/MatchEventLogEntry.java) |
| Added | [src/main/java/com/tetris/mab/MatchDebugSnapshot.java](src/main/java/com/tetris/mab/MatchDebugSnapshot.java) |
| Added | [src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java](src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java) |
| Added | this document (Step2.md) |

---

## 2. Match-state architecture

```
┌─ existing engine (untouched) ──────────────────────────────┐
│   GameState A             GameState B                       │
│      │  fires events         │  fires events                │
└──────┼────────────────────── ┼─────────────────────────────┘
       │                       │
       │  GameEventListener    │  GameEventListener
       ▼                       ▼
┌──────────────────────────────────────────────────────────────┐
│  MutuallyAssuredBlocksMatch  (com.tetris.mab)                │
│  ┌─ ParticipantState A ─┐    ┌─ ParticipantState B ─┐        │
│  │  GameState ref       │    │  GameState ref       │        │
│  │  NukeBuildState      │    │  NukeBuildState      │        │
│  │  SiloState           │    │  SiloState           │        │
│  │  Radar / Upgrade /   │    │  Radar / Upgrade /   │        │
│  │  ActionCode / Launch │    │  ActionCode / Launch │        │
│  │  IncomingThreats /   │    │  IncomingThreats /   │        │
│  │  Restraint / 2nd-    │    │  Restraint / 2nd-    │        │
│  │  Strike / CivilDef   │    │  Strike / CivilDef   │        │
│  │  pieces/lines/garb.  │    │  pieces/lines/garb.  │        │
│  └──────────────────────┘    └──────────────────────┘        │
│  DefconState (shared)         PieceTimerManager (shared)     │
│  MatchPhase, paused, winner, eventLog                        │
└──────────────────────────────────────────────────────────────┘
```

### Key design choices

- **The engine has zero compile-time dependency on the match layer.**
  All coupling goes one way: `mab` imports `model.GameState` and
  `events.GameEventListener`; the engine has no idea the match layer
  exists.
- **No render payloads cross the boundary.** `Color[][]` board snapshots
  stay inside the engine and the existing `GameEventListener` payloads.
  `MatchDebugSnapshot` is purely numeric/textual — safe to log,
  serialise, or feed into a future debug overlay.
- **Listeners are detached on shutdown.** `MutuallyAssuredBlocksMatch.shutdown()`
  removes both listeners so a finished match never leaks subscribers.
- **Bounded log.** The match keeps the most recent 200 log entries to
  avoid unbounded growth in long matches.

---

## 3. Connecting `GameState` to `ParticipantState`

The match coordinator constructor:

1. Wraps each `GameState` in a `ParticipantState` (`PLAYER_A`, `PLAYER_B`).
2. Calls `gameState.setPlayerId(id.name())` so any `TopOutEvent` payload
   coming back from the engine carries a meaningful player id.
3. Builds one `GameEventListener` per participant via
   `buildListener(participant)` and registers it with that participant's
   `GameState`.

The listener is a closure over the specific `ParticipantState`, so
events are routed to the correct side without any per-event lookup.

| Engine event | What the match does |
|---|---|
| `onPieceLocked` | `participant.incrementPiecesLocked()`; `pieceTimerManager.advanceForPieceLocked(participant.id)`; consumes completed timers and logs each as `TIMER_COMPLETED`; logs `PIECE_LOCKED` |
| `onLinesCleared` | `participant.addLinesCleared(count)`; `nukeBuildState.addCharge(...)` (provisional); `defconState.addEscalation(...)` (provisional); logs `LINES_CLEARED` with full metadata |
| `onGarbageInserted` | `participant.addGarbageReceived(rows)`; `defconState.addEscalation(rows*2, "garbage")`; logs `GARBAGE_INSERTED` including the full per-row hole layout from Step-1's refined event payload |
| `onTopOut` | `participant.markToppedOut()`; sets `winner = participant.id.opponent()`; sets phase to `GAME_OVER`; logs `TOP_OUT` |
| `onPauseChanged` | Logged as `BOARD_PAUSE_CHANGED`. Per-board pauses do **not** toggle the match-level `paused` flag — only `pauseMatch / resumeMatch` do that. |

Other engine events (`onPieceSpawned`, `onPieceMoved`, `onHoldUsed`,
`onPreviewAdvanced`, `onScoreUpdated`) are intentionally **not**
consumed in Step 2; the listener interface's `default` no-ops mean the
engine still fires them but the match silently ignores them. They will
become useful when later steps wire AI / radar / second-strike systems.

---

## 4. Piece-count timers

`PieceTimerManager` and `PieceCountdownTimer` provide a deterministic
countdown mechanism that ticks **once per piece lock**, never on
real-time clocks. This is critical because:

- Pausing the match must freeze every strategic timer.
- Different gravity speeds between participants must not unfairly
  advance shared timers.
- Replays / save-states need a discrete, reproducible time axis.

Each timer carries:

| Field | Purpose |
|---|---|
| `id` | caller-defined identifier (used to cancel / look up) |
| `owner` | which participant created the timer |
| `advanceMode` | which piece-lock events count: `OWNER_PIECES`, `OPPONENT_PIECES`, `EITHER_PLAYER_PIECES`, `PLAYER_A_PIECES`, `PLAYER_B_PIECES` |
| `remainingPieces` | counts down to zero |
| `completed` | true once `remainingPieces == 0` |
| `reason` | free-form tag, used in the log |

Manager API:

```java
PieceTimerManager mgr = match.getPieceTimerManager();
mgr.addTimer(new PieceCountdownTimer(
        "launch-1", ParticipantId.PLAYER_A,
        TimerAdvanceMode.OWNER_PIECES, 5, "missile-prep"));
// each onPieceLocked() routes through advanceForPieceLocked(...)
List<PieceCountdownTimer> done = mgr.consumeCompletedTimers();
mgr.cancelTimer("launch-1");
mgr.clear();
```

`consumeCompletedTimers()` is drained by the listener after each piece
lock and each completed timer is also written to the event log as
`TIMER_COMPLETED`.

---

## 5. Provisional nuke charge from line clears

These values are intentionally provisional; the real nuke design schema
will replace them. They live in `MutuallyAssuredBlocksMatch.lineClearChargeFor(...)`.

| Clear | Charge |
|---|---|
| Single | 1 |
| Double | 3 |
| Triple | 5 |
| Tetris | 8 |
| Back-to-back Tetris | 10 (overrides 8) |
| Perfect Clear | 12 (overrides everything else) |

Charge is added via `participant.getNukeBuildState().addCharge(n)`. The
`NukeBuildState` flips `armed = true` once the cumulative charge meets
its `effectiveBuildChargeRequired` (default 20 for the placeholder
design). Excess charge is recorded in `overbuiltCharge` for the future
"nuke yield bonus" mechanic.

---

## 6. Provisional DEFCON escalation

`DefconState` is shared across both participants. It tracks readiness
(5 → 1), not damage. Crossing a threshold lowers the level.

Default thresholds:

| Escalation reached | DEFCON level |
|---|---|
| 0 | 5 |
| 100 | 4 |
| 250 | 3 |
| 500 | 2 |
| 850 | 1 |

Provisional escalation sources (no other side effects yet — DEFCON does
not modify gameplay in Step 2):

| Source | Escalation |
|---|---|
| Single | +1 |
| Double | +2 |
| Triple | +3 |
| Tetris | +5 |
| Back-to-back Tetris | +7 |
| Perfect Clear | +10 |
| Garbage inserted | +2 per row |
| Top-out | none (match ends) |

`DefconState.addEscalation(amount, reason)` exposes the same call shape
that future systems (e.g. nuke launches) will use — only the numbers
will change.

---

## 7. Current placeholder systems

Every class below has fields and getters/setters but **no behavior** in
Step 2:

- `NukeBuildState` — accepts charge and flips `armed`, but doesn't
  expose a real design or actually launch anything.
- `SiloState` — integrity / hardening / etc. fields, never modified by
  the match yet.
- `RadarIntelState` — `lastKnownEnemyDoctrine`, `lastKnownEnemySize`,
  `stale` flag; no scan logic.
- `UpgradeState` — pending choices counter and an empty unlocked list;
  no upgrade picker.
- `ActionCodeProgressState` — required + completed sequence lists;
  nothing matches against them yet.
- `ActiveLaunchState` / `IncomingThreatState` — record-shaped placeholder
  data carriers; no in-flight motion or impact.
- `RestraintState` / `SecondStrikeState` / `CivilDefenseState` — flags
  and counters reserved for future doctrine systems.

---

## 8. Intentionally NOT implemented yet

- Real `NukeDesign` schema (parts, slots, doctrines).
- Action-code / launch-code matching.
- Actual missile launches and impacts.
- Radiation / messy-garbage generation logic (the patterned-garbage
  *plumbing* is already in place from the Step-1 refinement).
- Upgrade-selection UI and gameplay gating.
- (project is offline-only: local 1v1 + PvE + practice/debug; networking / online multiplayer are permanently out of scope.)
- Final PvE AI driver (the match accepts a second `GameState` but
  nothing drives it for you).
- DEFCON gameplay modifiers (damage, charge multipliers, gating).

---

## 9. Acceptance-criteria verification

| Criterion | Status |
|---|---|
| Project compiles | ✓ — language server reports no errors across the new `com.tetris.mab` package or any pre-existing file |
| Existing single-player Tetris still works | ✓ — no engine file was modified in Step 2; the new package is purely additive and only activates when a match is constructed |
| `MutuallyAssuredBlocksMatch` can be created from two `GameState`s | ✓ — `createLocalPvp(...)` and `createPve(...)` factories |
| Each participant receives events from the correct `GameState` | ✓ — `buildListener(participant)` closes over the right `ParticipantState`; both listeners register independently |
| Piece locks increment participant piece counters | ✓ — `onPieceLocked` calls `participant.incrementPiecesLocked()` |
| Line clears add provisional nuke build charge | ✓ — see `lineClearChargeFor(...)` and table in §5 |
| Line clears update provisional DEFCON escalation | ✓ — see `lineClearEscalationFor(...)` and table in §6 |
| Garbage insertion updates `garbageReceivedTotal` and DEFCON escalation | ✓ — `onGarbageInserted` adds rows and `rows*2` escalation |
| Top-out on one participant sets the other as winner | ✓ — `onTopOut` sets `winner = participant.id.opponent()` and phase `GAME_OVER` |
| Piece-count timers can be added / advanced / completed / consumed | ✓ — `PieceTimerManager.addTimer / advanceForPieceLocked / consumeCompletedTimers / cancelTimer / clear` |
| Match state can produce a readable debug snapshot | ✓ — `toDebugSnapshot()` returns a numeric-only `MatchDebugSnapshot`; `toDebugString()` prints a human-readable summary |
| Phase-safe event routing (Step 2 refinement) | ✓ — see §11; gameplay events outside `ACTIVE` / while paused / after `GAME_OVER` no longer mutate strategic state |
| Step2.md documents the architecture and confirms what was implemented | ✓ — this document |

---

## 10. Example — creating a local PvP match

```java
import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.model.GameState;

GameState gameA = new GameState();
GameState gameB = new GameState();

MutuallyAssuredBlocksMatch match =
    MutuallyAssuredBlocksMatch.createLocalPvp(
        gameA,
        gameB,
        MatchDifficulty.NORMAL
    );

match.startMatch();

// ... two GameControllers (or two InputHandlers, or one human + one AI)
// drive gameA and gameB normally. Every line clear, garbage insert,
// top-out, etc. is automatically routed into the match through the
// Step-1 GameEventListener layer.

System.out.println(match.toDebugString());
// Example output:
// === Mutually Assured Blocks ===
// mode=PVP_LOCAL difficulty=NORMAL phase=ACTIVE
// DEFCON 5 (escalation=0, next at 100)
// Participant{PLAYER_A pieces=0 lines=0 garbage=0 NukeBuild{0/20 design=placeholder_tactical doctrine=placeholder} Silo{HP=100 STABLE} incoming=0 launches=0}
// Participant{PLAYER_B pieces=0 lines=0 garbage=0 NukeBuild{0/20 design=placeholder_tactical doctrine=placeholder} Silo{HP=100 STABLE} incoming=0 launches=0}
// activeTimers=0 loggedEvents=1
```

To pause and resume the entire match (both engines together):

```java
match.pauseMatch("upgrade-screen");
// ... show upgrade UI ...
match.resumeMatch("upgrade-done");
```

When the match is finished:

```java
match.shutdown();   // detaches both GameEventListeners
```

---

## 11. Step 2 refinement — phase-safe event routing

After the initial Step 2 wiring it was possible for engine events to
mutate strategic state at moments where they should not — e.g. lines
cleared during the SETUP phase before `startMatch()`, garbage inserted
while the match was paused for an upgrade screen, or a stray top-out
arriving after a winner had already been decided. This refinement closes
those holes without altering the engine.

### Guard

A single private predicate gates every gameplay-mutating event:

```java
private boolean isGameplayMutationAllowed() {
    return currentPhase == MatchPhase.ACTIVE && !paused && winner == null;
}
```

When the guard returns false, the event is still **logged** (so the
match log remains a faithful trace of everything the engine reported),
but no participant counters, nuke charge, DEFCON escalation, or piece
timers are changed.

### Behavior matrix

| Event | Guard true (ACTIVE, unpaused, no winner) | Guard false |
|---|---|---|
| `onPieceLocked` | increments `piecesLocked`, advances piece timers, drains completed timers, logs `PIECE_LOCKED` (+ `TIMER_COMPLETED` per drained timer) | logs `IGNORED_PIECE_LOCKED` only |
| `onLinesCleared` | increments `linesClearedTotal`, adds provisional nuke charge, adds provisional DEFCON escalation, logs `LINES_CLEARED` | logs `IGNORED_LINES_CLEARED` only |
| `onGarbageInserted` | increments `garbageReceivedTotal`, adds `rows*2` DEFCON escalation, logs `GARBAGE_INSERTED` (with `holeColumnsByRow`) | logs `IGNORED_GARBAGE_INSERTED` (still records `rows`, `source`, `holeColumnsByRow`) |
| `onTopOut` (ACTIVE, no winner) | marks participant toppedOut, sets `winner = opponent`, sets phase `GAME_OVER`, logs `TOP_OUT` | — |
| `onTopOut` (`GAME_OVER` or winner already set) | — | logs `IGNORED_TOP_OUT_AFTER_GAME_OVER`; winner not overwritten |
| `onTopOut` (`SETUP` / `UPGRADE_PAUSE`) | — | logs `TOP_OUT_OUTSIDE_ACTIVE`; no winner decided |
| `onPauseChanged` | always logged as `BOARD_PAUSE_CHANGED` regardless of phase | does **not** flip match-level `paused` |

Every "ignored" log entry includes the standard guard context
(`phase`, `paused`, `winner`, `participant`) plus event-specific data
(line count, row count, hole columns, top-out reason, etc.).

### Match-level pause is the only thing that controls `match.paused`

`onPauseChanged` events from individual `GameState` instances are
recorded but never mutate `match.paused`. The flag is only changed by
`pauseMatch(reason)` / `resumeMatch(reason)` (and the new
`enterUpgradePause` / `exitUpgradePause`).

### Piece timers

`PieceTimerManager` itself is unchanged — keeping it free of match-phase
knowledge. The phase guard is enforced at the call site in
`MutuallyAssuredBlocksMatch.onPieceLocked`: when the guard returns
false, `pieceTimerManager.advanceForPieceLocked(...)` is **not**
called, so timers neither tick nor complete during pause / SETUP /
GAME_OVER.

### Idempotent lifecycle methods

`startMatch()`, `pauseMatch()`, and `resumeMatch()` are now phase-aware
and idempotent:

| Method | SETUP | ACTIVE (unpaused) | ACTIVE (paused) | UPGRADE_PAUSE | GAME_OVER |
|---|---|---|---|---|---|
| `startMatch()` | → ACTIVE, `MATCH_STARTED` | `START_IGNORED_ALREADY_ACTIVE` | `START_IGNORED_ALREADY_ACTIVE` | `START_IGNORED_UPGRADE_PAUSE` | `START_IGNORED_GAME_OVER` |
| `pauseMatch(r)` | pauses | pauses, `MATCH_PAUSED` | `PAUSE_IGNORED_ALREADY_PAUSED` | `PAUSE_IGNORED_ALREADY_PAUSED` | `PAUSE_IGNORED_GAME_OVER` |
| `resumeMatch(r)` | `RESUME_IGNORED_NOT_PAUSED` (no-op) | `RESUME_IGNORED_NOT_PAUSED` | resumes, `MATCH_RESUMED` | resumes, `MATCH_RESUMED` | `RESUME_IGNORED_GAME_OVER` |

### Optional upgrade-pause helpers (added)

Two convenience methods were added since the existing field model made
them trivial:

- `enterUpgradePause(String reason)` — allowed only from `ACTIVE`;
  sets phase to `UPGRADE_PAUSE`, sets `paused = true`, pauses both
  engines, logs `UPGRADE_PAUSE_ENTERED`.
- `exitUpgradePause(String reason)` — allowed only from
  `UPGRADE_PAUSE`; sets phase back to `ACTIVE`, sets `paused = false`,
  resumes both engines, logs `UPGRADE_PAUSE_EXITED`.

Calls from any other phase log `UPGRADE_PAUSE_IGNORED` /
`UPGRADE_PAUSE_EXIT_IGNORED` and do nothing else. The selection UI
itself is still out of scope; this is just the lifecycle scaffolding.

### What this guarantees

- Gameplay-mutating events are ignored outside `ACTIVE`.
- Piece timers do not advance while paused.
- Line clears do not add nuke charge while paused or outside `ACTIVE`.
- Garbage events do not escalate DEFCON while paused or outside `ACTIVE`.
- A top-out during `ACTIVE` ends the match and decides the winner.
- A top-out arriving after `GAME_OVER` does not overwrite the winner.
- Pause events from individual boards are always logged.
- Match-level pause is controlled only by `pauseMatch` / `resumeMatch`
  (and the new upgrade-pause helpers).

### Build verification

```
$env:Path = "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin;$env:Path"
javac -d target\classes -encoding UTF-8 @<all .java under src\main\java>
EXITCODE=0
```

The full project compiles cleanly with the real `javac` build (Eclipse
Adoptium JDK 25). No engine source file was modified by this
refinement.
