# Step 22 \u2014 All-Spin Recognition and Dramatic MAB Stage UI

## Scope (per spec, 18 parts)

This step extends the engine and the MAB UI so that:

1. The game now recognizes *all* qualifying spins, including 0-line spins
   detected via the **immobile rule**, for T / J / L / S / Z / I (O is
   excluded \u2014 see Part 19).
2. Spin events feed the simplified MAB charge / launch / intercept
   pipeline through a new unified per-lock event.
3. 0-line spins now grant +4 charge, advance launch progress (spin
   route), and trigger spin-intercept against incoming threats.
4. The MAB PvE main gameplay screen has a **dramatic, large stage strip**
   above everything else so the player can read the current MAB stage
   from across the room.

Offline-only. Single-player NORMAL_TETRIS scoring/behaviour is
unchanged. UI must remain readable at 1366\u00d7768 with no clipped core
information.

## Files added

- `src/main/java/com/tetris/events/PieceLockResult.java` \u2014 unified
  per-lock event payload (line count + spin flags + label + B2B + combo
  + hard-drop flag + piece type + total pieces).
- `src/main/java/com/tetris/model/SpinDetector.java` \u2014 stateless
  immobile-rule helper. `isImmobile(Board, Tetromino)` returns
  `true` iff none of `(-1,0)`, `(+1,0)`, `(0,-1)` translations are
  valid against the board (the piece is NOT yet locked when this is
  called \u2014 the board does not contain its cells).
- `src/main/java/com/tetris/mab/ui/MabStage.java` \u2014 enum of the nine
  dramatic stages.
- `src/main/java/com/tetris/mab/ui/MabStagePresenter.java` \u2014 pure
  view-model: folds match state into one `Snapshot` per refresh tick.
- `src/main/java/com/tetris/mab/ui/MabStageStripPanel.java` \u2014 large
  three-row strip rendered above the alert banner. Background flashes
  for 4 refresh ticks on every stage transition.
- `src/main/java/com/tetris/mab/sim/MabSpinDetectionProbe.java` \u2014
  deterministic offline probe (10 checks) covering geometry,
  movement-cancel, hard-drop preserve, soft-drop preserve, 0-line
  intercept, and 0-line launch progress.

## Files modified

- `src/main/java/com/tetris/events/GameEventListener.java` \u2014 added
  default `onPieceLockedDetailed(PieceLockResult)` listener method.
- `src/main/java/com/tetris/model/GameState.java`:
  - Added Step 22 spin tracking fields: `lastPlacementInput` (enum),
    `spinCandidate`, `successfulRotationCountThisPiece`,
    `movedHorizontallyAfterLastRotation`, `hardDroppedThisLock`.
  - Reset of those fields in `spawnNextPiece()` and `hold()`.
  - `moveLeft`/`moveRight` now clear `spinCandidate` and set
    `movedHorizontallyAfterLastRotation = true`.
  - `softDrop` and `hardDrop` set `lastPlacementInput` but **do not**
    clear `spinCandidate` (per the immobile rule).
  - `tryRotation` (90\u00b0 paths) and `rotate180` set `spinCandidate =
    true` and increment the per-piece rotation counter when the
    rotation actually succeeds.
  - `lockPiece` now computes the all-spin classification *before* the
    board write (so the immobile probe does not see the piece's own
    cells), keeps existing T-spin scoring untouched, and fires the new
    `onPieceLockedDetailed(...)` event after the line-clear and
    score-update events.
  - Added probe-only getter `isSpinCandidatePending()`.
- `src/main/java/com/tetris/mab/clear/MabClearResult.java` \u2014 added
  `SpinKind.IMMOBILE`, added optional `pieceType` field, kept legacy
  9-arg constructor.
- `src/main/java/com/tetris/mab/clear/MabChargeCalculator.java` \u2014
  added `fromLockResult(ParticipantId, PieceLockResult)` factory and a
  richer `renderDisplay` that emits labels like
  `"J Spin no-line +4"` and `"B2B T Spin Triple x3 +25"`.
- `src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java`:
  - Added `onPieceLockedDetailed(...)` listener override that calls
    new `processSimplifiedClearForLockResult`.
  - Removed the previous duplicate call from `onLinesCleared` so a
    line-clearing spin is no longer routed twice.
  - Added `processSimplifiedClearForLockResult(ParticipantState,
    PieceLockResult)` \u2014 spin-intercept priority, then charge gain,
    then launch-route progress (spin OR Tetris), then launch fire.
  - Added `debugSimplifiedFeedLockResult(...)` for the new spin probe.
- `src/main/java/com/tetris/mab/sim/MabChargeCalculatorProbe.java` \u2014
  added `spinNoLine = +4` check.
- `src/main/java/com/tetris/mab/ui/MabPveGamePanel.java` \u2014 inserts the
  new `MabStageStripPanel` above the alert banner; exposes
  `refreshStageStrip(MutuallyAssuredBlocksMatch, ParticipantId)`.
- `src/main/java/com/tetris/controller/GameController.java` \u2014 the
  existing 10 Hz embedded-refresh timer now also calls
  `refreshStageStrip(...)`.

## Online research summary (offline-implemented)

The all-spin (immobile) rule is the modern guideline rule used by
TETR.IO, Jstris (in spin modes), and the official Guideline successor
specs. It awards spin credit to any rotation-locked piece that, at the
moment of lock, cannot move *up*, *left*, or *right* by one cell. A
prior horizontal move after the last rotation cancels the credit.
Hard drop, soft drop, and gravity preserve the candidate. T-spin
scoring still uses the older 3-corner rule for legacy compatibility;
both rules co-exist in `lockPiece` so single-player scoring is
unaffected.

## MAB spin rule (this implementation)

A piece counts as a spin for MAB when **all** of:

- A successful rotation occurred since spawn / last horizontal move /
  last hold.
- No horizontal move has happened since that last rotation.
- The piece type is not O (rotation may be identity).
- `SpinDetector.isImmobile(board, piece)` is true (with the piece in
  its final position but *before* board writes its cells).

OR the existing T-spin 3-corner rule fires (mini or full).

## 0-line spin processing

The new `onPieceLockedDetailed` event always carries the spin label and
charge-relevant fields. The MAB router (`processSimplifiedClearForLockResult`):

1. **Intercept priority.** If a spin is detected and the participant
   has any `WARNING_ACTIVE` incoming threat, the legacy intercept
   resolver fires (mapped to FULL/STANDARD/EMERGENCY by spin lines),
   the simplified state's `onSpinIntercept()` is called, and the
   line-clear / charge code is skipped for this lock.
2. **Charge.** Otherwise charge is added per the (now spin-aware)
   `MabChargeCalculator.compute(...)` formula. A 0-line spin grants
   +4. State is synced from the nuke build state. The first lock that
   completes the bar logs `MAB_NUKE_READY`.
3. **Launch route progress.** When the bar is full, Tetrises advance
   the Tetris route and any spin (0-line included) advances the spin
   route. Whichever route reaches its goal fires the launch via the
   existing `fireSimplifiedLaunch` path.

## Stage UI

`MabStage` enum (priority highest \u2192 lowest):
`MATCH_OVER \u2192 INCOMING_THREAT \u2192 LAUNCH_FIRED \u2192 NUKE_READY (split into
LAUNCH_TETRIS_ROUTE / LAUNCH_SPIN_ROUTE) \u2192 BUILD_CHARGE`.

`MabStagePresenter.present(...)` returns an immutable `Snapshot`
{stage, subtitle, progressText, ctaText, fraction}. `MabStageStripPanel`
paints three rows (tiny mode title, **32-pt bold** stage headline
coloured by severity, subtitle + thin progress bar + CTA). On stage
transition the strip background flashes a darkened stage colour for 4
refresh ticks (~400 ms at the 10 Hz refresh).

## Probes

| Probe | Result |
|---|---|
| `MabChargeCalculatorProbe` (now includes `spinNoLine=+4`) | `success=true` |
| `MabSimplifiedCoreProbe` | `success=true` |
| `MabSpinDetectionProbe` (10 checks) | `success=true` |
| `MabSharedPieceSequenceProbe` | `success=true` |

## Simulations

| Mode | Result |
|---|---|
| `MabSimulationRunner smoke` | `ok=true invariantFailures=0` |
| `MabSimulationRunner ai-vs-dummy 160` | `ok=true invariantFailures=0` |
| `MabSimulationRunner ai-vs-ai 160`   | `ok=true invariantFailures=0` |

## Manual UI checklist

- The MAB PvE window now shows a 110-px tall dramatic stage strip
  spanning the full width above the existing alert banner.
- Stage headline is 32-pt bold, colour-coded by severity (info/blue,
  success/green, warning/amber, critical/red).
- Charge progress bar fills smoothly during play; on transition into
  `NUKE_READY` the strip flashes green for ~400 ms.
- Incoming threat instantly switches the strip to red `INCOMING THREAT`
  with the CTA "Land any spin (any piece) to shoot down a launch".
- Successful spin during a threat shows the intercept stage briefly,
  then returns to charge build.
- Match-over swaps the strip to grey `MATCH OVER` and shows the
  embedded result panel below as before.
- Layout fits 1366\u00d7768 without clipping the existing alert banner,
  command notification, boards, or the south compact dashboard.

## Limitations

- The dramatic strip currently lives only inside the embedded MAB PvE
  layout (the path also used by the local PvP shared-sequence flow when
  routed through the same panel). Standalone PvP windows that do not
  build `MabPveGamePanel` are not affected.
- O-piece is excluded from immobile-spin credit because the engine's
  rotation states for O are effectively identity in geometry, which
  would otherwise produce false positives in tight pockets.
- The "spin label" carried in `MabClearResult.displayText()` uses the
  TETR.IO style ("T Spin", "T Spin Mini", "J Spin", \u2026) regardless of
  locale.
