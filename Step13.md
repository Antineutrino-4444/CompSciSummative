# Step 13 — Mutually Assured Blocks: Offline PvE Strategic AI Driver and Practice Opponent

> **Permanent scope rule:** This step (and every following one) remains
> strictly **offline-only**. No networking, no sockets, no
> matchmaking, no rollback, no online-prep architecture. The PvE AI
> introduced here runs in-process on the Swing event-dispatch thread.

## 1. Purpose

Step 12 produced a visible debug HUD with manual buttons for both
participants. Step 13 plugs an **offline strategic AI** into the
hidden Player-B participant so the lone human player has a
deterministic practice opponent that:

- charges, arms, and launches its own nukes,
- scans with radar, deploys decoys, raises civil defence,
- spends earned upgrade points,
- advances its own MAB-side strategic clock without ever moving a
  piece on the visible board.

It is **not** a substitute for a finished AI; it is the offline
practice driver for the strategic layer.

## 2. Scope

| In scope                                                                                               | Out of scope                                                                  |
| ------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------- |
| New `com.tetris.mab.ai` package: archetype, difficulty, decision, state, policy, driver.               | Online play, rollback, prediction, network sync.                              |
| Public `debugAdvanceStrategicClockOnly(pid, pieces)` helper on the match.                              | Visible second board, AI-driven Tetris piece movement.                        |
| HUD wiring: `Toggle AI B` + `AI Tick Once` buttons; auto-tick from the refresh timer; AI-state panel.  | A polished Player-B board renderer.                                           |
| `AI_*` event log types via a new `debugLogEvent(...)` hook on the match.                               | Persisted AI state across sessions.                                           |

## 3. Files added

```
src/main/java/com/tetris/mab/ai/
    MabAiArchetype.java        Strategic flavour enum.
    MabAiDifficulty.java       EASY / NORMAL / HARD / DEBUG.
    MabAiDecisionType.java     Decision categories.
    MabAiDecision.java         Immutable decision record.
    MabAiState.java            Mutable per-AI state with cooldowns.
    MabAiPolicy.java           Pure decision function.
    MabAiDriver.java           Owns state, ticks the policy, calls match debug API.
```

## 4. Files modified

| File                                                                                       | Change                                                                                                                              |
| ------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------- |
| [src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java](src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java) | Extracted `advanceStrategicPieceClockFor(...)` from `onPieceLocked`; added `debugAdvanceStrategicClockOnly(...)` and `debugLogEvent(...)`. |
| [src/main/java/com/tetris/mab/debugui/MabDebugController.java](src/main/java/com/tetris/mab/debugui/MabDebugController.java) | Added optional `MabAiDriver`, `attachAiB`, `toggleAi`, `tickAiOnce`, `tickAiIfEnabled`, `formattedSnapshotWithAi`.                  |
| [src/main/java/com/tetris/mab/debugui/MabDebugFrame.java](src/main/java/com/tetris/mab/debugui/MabDebugFrame.java)           | Added `Toggle AI B` and `AI Tick Once` buttons; refresh timer auto-ticks the AI; uses AI-enriched snapshot.                         |

## 5. Strategic-clock helper extraction

The side-effects previously inlined in
`buildListener.onPieceLocked` were extracted into a private helper:

```java
private void advanceStrategicPieceClockFor(ParticipantState participant, String source) {
    participant.incrementPiecesLocked();
    // radar staleness ticks (silent)
    participant.getRadarIntel().tickScannerPiece();
    ParticipantState opp = getOpponent(participant.getId());
    if (opp != null) opp.getRadarIntel().tickTargetPiece();
    // decoy expirations
    tickAndExpireDecoys(participant);
    // civil-defence shield decay + log
    // WARNING_ACTIVE warning ticks
    // pieceTimerManager.advanceForPieceLocked + routeCompletedTimer
}
```

The real `onPieceLocked` now reduces to a phase guard, a call to the
helper, and the existing `PIECE_LOCKED` log. The helper takes a
`source` string so logs distinguish engine ticks (`"engine"`) from
hidden AI ticks (`"debug-strategic-clock"`).

## 6. `debugAdvanceStrategicClockOnly`

```java
public int debugAdvanceStrategicClockOnly(ParticipantId participantId, int pieces);
```

- Phase-guarded: only runs while `ACTIVE && !paused && winner == null`.
- Never touches `GameState`. Visible pieces, score, line clears stay
  untouched.
- Calls `advanceStrategicPieceClockFor` `pieces` times.
- Emits a single `AI_STRATEGIC_CLOCK_ADVANCED` log with the count.
- Rejected calls emit `DEBUG_STRATEGIC_CLOCK_REJECTED`.

This is exactly the safer "Preferred" alternative noted in the Step 12
deferral: synthesising `pieceLocked` events from outside the engine
would have desynced scoring; this helper only ticks the strategic
side.

## 7. AI package

### Archetypes (`MabAiArchetype`)

`TACTICAL_SPAMMER, DIRTY_BOMBER, CONCRETE_STRATEGIST, MAD_DEFENDER,
MIRV_CONTROLLER, DOOMSDAY_HOARDER, BALANCED`. Each archetype tunes the
decoy pick and upgrade-preference list (see §10/§11).

### Difficulty (`MabAiDifficulty`)

`EASY, NORMAL, HARD, DEBUG`. Difficulty controls:

- charge-budget gain per simulated piece,
- size of an `ADD_CHARGE` step,
- launch / radar / decoy / defence / upgrade cooldown lengths.

### Decision (`MabAiDecision`)

Record of `(type, participantId, detail, executed, message)` with
`none`, `executed`, `skipped` factories.

### State (`MabAiState`)

Mutable, single-thread access only. Tracks ticks, simulated pieces,
charge budget, five cooldown counters (clamped at 0), and per-action
counters. `setLastDecision` formats a one-line summary for the HUD.

### Policy (`MabAiPolicy`)

Pure function `chooseDecision(ai, match, self, opponent)` with a
fixed priority order:

1. If disabled or match not `ACTIVE` / paused / decided → `NONE`.
2. Any impact-ready launch or threat → `RESOLVE_IMPACT`.
3. `WARNING_ACTIVE` incoming threat + defence cooldown ready +
   no shield up → `CIVIL_DEFENSE`.
4. Opponent has decoys or our intel is stale + radar cooldown
   ready → `RADAR_SCAN`.
5. Armed nuke + launch cooldown ready → `LAUNCH`.
6. Not armed but budget covers what is needed → `ARM_NUKE`
   (drains budget, then arms).
7. Budget covers a step → `ADD_CHARGE`.
8. Decoy cooldown ready and archetype favours deception → `DECOY`.
9. Have upgrade points + cooldown ready → `OPEN_UPGRADE_PAUSE`
   (folded into a single sequence by the driver, see §11).
10. Otherwise top off charge or `NONE`.

### Driver (`MabAiDriver`)

Owns the state, holds the policy, talks to the match through the
`debug*` API. `tick()` performs:

1. `state.incrementAiTicks()`, `state.tickCooldowns()`.
2. If `advanceHiddenClock` (default `true`):
   `match.debugAdvanceStrategicClockOnly(aiId, 1)` and credit the
   per-difficulty charge gain to the budget.
3. Ask the policy for a decision.
4. Execute it via the corresponding `debug*` method on the match.
5. Set the appropriate cooldown and counter.
6. Emit `AI_DECISION_EXECUTED` or `AI_DECISION_SKIPPED`.

`tick()` is reentrancy-safe and never throws (a failed decision is
captured and logged as skipped).

## 8. New `AI_*` log types

| Type                            | When                                                                                             |
| ------------------------------- | ------------------------------------------------------------------------------------------------ |
| `AI_ENABLED` / `AI_DISABLED`    | Driver toggled.                                                                                  |
| `AI_DECISION_EXECUTED`          | Policy returned a decision and the match executed it.                                            |
| `AI_DECISION_SKIPPED`           | Policy chose `NONE`, or execution was rejected by the match.                                     |
| `AI_STRATEGIC_CLOCK_ADVANCED`   | After every successful `debugAdvanceStrategicClockOnly` call (the AI's own piece simulation).    |
| `AI_UPGRADE_ATTEMPTED`          | Inside the upgrade sequence, after picking a candidate.                                          |
| `AI_UPGRADE_APPLIED`            | `applyUpgrade` returned success.                                                                 |
| `AI_UPGRADE_SKIPPED`            | `applyUpgrade` was rejected or no candidate was available.                                       |

All AI logs are emitted via a new public hook on the match:

```java
public void debugLogEvent(String eventType, ParticipantId participantId,
                          String message, Map<String, Object> metadata);
```

The hook delegates to the existing private `log(...)` writer so AI
events share the same ring buffer the HUD already displays.

## 9. HUD wiring

- `MabDebugController` gains an optional `MabAiDriver`, plus
  `attachAiB`, `toggleAi`, `tickAiOnce`, `tickAiIfEnabled`, and
  `formattedSnapshotWithAi`.
- `MabDebugFrame` adds two buttons (`Toggle AI B`, `AI Tick Once`)
  and the existing 400 ms refresh timer now calls
  `controller.tickAiIfEnabled()` before redrawing the snapshot.
- The snapshot panel appends an `── AI ──` block with the
  `MabAiState` debug string when an AI is attached.

The AI is **off by default**. The user must press *Toggle AI B*
(or *AI Tick Once*) to start it.

## 10. Decoy choices by archetype

| Archetype              | Decoy chosen by policy   |
| ---------------------- | ------------------------ |
| TACTICAL_SPAMMER       | `DECOY_LAUNCH`           |
| DIRTY_BOMBER           | `FALSE_DOCTRINE_SIGNAL`  |
| CONCRETE_STRATEGIST    | `DUMMY_SILO_HEAT`        |
| MAD_DEFENDER           | `DECOY_LAUNCH`           |
| MIRV_CONTROLLER        | `GHOST_MIRV`             |
| DOOMSDAY_HOARDER       | `FALSE_DOCTRINE_SIGNAL`  |
| BALANCED               | `DECOY_LAUNCH`           |

`MASKED_LAUNCH` is intentionally never chosen: the manual decoy API
rejects it (per Step 11).

## 11. Upgrade-by-archetype map

The driver opens an upgrade pause, picks one upgrade matching the
archetype's preference list (falling back to the first available
choice), applies it, and closes the pause. Preferences only reference
enum constants verified to exist in
[UpgradeType.java](src/main/java/com/tetris/mab/upgrade/UpgradeType.java).

| Archetype              | Preferred upgrades (first match wins)                                                |
| ---------------------- | ------------------------------------------------------------------------------------ |
| TACTICAL_SPAMMER       | `RAPID_LAUNCH_DRILLS`, `BUILD_EFFICIENCY`, `RAPID_ASSEMBLY_LINE`                     |
| DIRTY_BOMBER           | `DIRTY_PAYLOAD_ENGINEERING`, `WARHEAD_REFINEMENT`, `PENETRATION_PACKAGE`             |
| CONCRETE_STRATEGIST    | `HARDENED_SILO`, `BLAST_DOORS`, `SECURE_LAUNCH_CHAIN`                                |
| MAD_DEFENDER           | `SHELTERS`, `GARBAGE_CONTROL`, `SECOND_STRIKE_DOCTRINE_I`, `ASSURED_RETALIATION`     |
| MIRV_CONTROLLER        | `SIGNAL_ANALYSIS`, `THREAT_TRACKING`, `PENETRATION_PACKAGE`                          |
| DOOMSDAY_HOARDER       | `WARHEAD_REFINEMENT`, `DEEP_BUNKER`, `DEAD_HAND_PROTOCOL`                            |
| BALANCED               | `EARLY_WARNING_RADAR`, `BUILD_EFFICIENCY`, `SHELTERS`                                |

If none of the preferences are available the first available choice
is taken; if no choices exist the sequence logs
`AI_UPGRADE_ATTEMPTED` with `pick=null` and exits cleanly.

## 12. Cooldown defaults

| Difficulty | Launch | Radar | Decoy | Defence | Upgrade | ADD_CHARGE step | Budget gain / sim piece |
| ---------- | -----: | ----: | ----: | ------: | ------: | --------------: | ----------------------: |
| EASY       | 12     | 16    | 20    | 10      | 30      | 10              | 2                       |
| NORMAL     | 8      | 10    | 14    | 6       | 20      | 20              | 4                       |
| HARD       | 5      | 6     | 9     | 4       | 12      | 30              | 6                       |
| DEBUG      | 2      | 2     | 3     | 2       | 4       | 50              | 12                      |

All cooldowns are measured in AI ticks and decremented by
`MabAiState.tickCooldowns`.

## 13. Determinism + threading

- The driver only runs on the Swing EDT (refresh timer or button
  click).
- No randomness anywhere in the policy: the same sequence of state
  inputs always yields the same decision.
- No new threads, executors, locks, or sockets.

## 14. Failure handling

- Any `RuntimeException` thrown by the match while executing a
  decision is caught inside `MabAiDriver.execute(...)` and converted
  into a skipped decision with the exception class + message in the
  log entry.
- `RESOLVE_IMPACT`, `RADAR_SCAN`, `DECOY` all check the underlying
  result object's `success()` (or list size) before recording the
  action and applying its cooldown.
- The upgrade sequence always closes the upgrade pause via a
  `finally` block.

## 15. Compatibility

- Existing manual buttons keep working unchanged. The AI never moves
  Player A.
- The visible game board is still owned exclusively by
  `GameController` / `GameState`. The AI's "pieces" are strategic
  ticks only.
- Step 11 (decoys) and Step 12 (HUD + manual debug) APIs are reused
  verbatim; no signatures changed.

## 16. Manual verification (suggested)

1. Launch with `mvn` or `run.bat`. The MAB Debug HUD appears.
2. Click **Toggle AI B**. The HUD's `── AI ──` block flips to
   `enabled=true`.
3. Watch the events panel: `AI_ENABLED`,
   `AI_STRATEGIC_CLOCK_ADVANCED`, `AI_DECISION_EXECUTED` /
   `AI_DECISION_SKIPPED` lines appear roughly every 400 ms.
4. After the AI charges enough to launch, observe a
   `LAUNCH_AUTHORIZED` followed by an eventual incoming threat on
   Player A and the AI's `CIVIL_DEFENSE` reaction when it later sees
   one against itself.
5. Click **Toggle AI B** again. `AI_DISABLED` is logged and no further
   AI events appear.
6. Use **AI Tick Once** to step the AI a single decision at a time.

## 17. Build verification

```
javac -d target\classes -encoding UTF-8 "@$env:TEMP\tetris_sources.txt"
EXITCODE=0
```

## 18. What Step 14 will need

- A second visible HUD pane with a richer per-participant view
  (currently the AI block is plain text).
- A small set of unit tests around `MabAiPolicy.chooseDecision` and
  `MabAiDriver.execute` to lock in the priority order.
- An optional headless `MabAiHarness` driver to fast-forward a
  large number of AI ticks for balance tuning.
