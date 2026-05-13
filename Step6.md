# Step 6 — Mutually Assured Blocks: Impact Resolver, Radiation Garbage, Disarm Damage, and Silo Damage

This step turns the Step-5 launch lifecycle into a real impact: when a launch reaches `IMPACT_READY`, the match coordinator can resolve it into garbage on the defender's board, charge loss on the defender's nuke build, and integrity loss on the defender's silo.

---

## 1. Files added

New impact package under `com.tetris.mab.impact`:

- [src/main/java/com/tetris/mab/impact/ImpactResolutionStatus.java](src/main/java/com/tetris/mab/impact/ImpactResolutionStatus.java) — outcome enum (`RESOLVED`, `SKIPPED_NOT_READY`, `SKIPPED_ALREADY_RESOLVED`, `SKIPPED_CANCELLED`, `LAUNCH_NOT_FOUND`, `THREAT_NOT_FOUND`, `DEFENDER_NOT_FOUND`, `ERROR`).
- [src/main/java/com/tetris/mab/impact/RadiationLevel.java](src/main/java/com/tetris/mab/impact/RadiationLevel.java) — `CLEAN, LIGHT, DIRTY, HOT, SEVERE, SALTED` + `fromRating(int)` + `messinessScore()`.
- [src/main/java/com/tetris/mab/impact/ImpactGarbagePlan.java](src/main/java/com/tetris/mab/impact/ImpactGarbagePlan.java) — immutable plan (totals, immediate vs delayed, wave count, pieces between waves, generated patterns).
- [src/main/java/com/tetris/mab/impact/ImpactResult.java](src/main/java/com/tetris/mab/impact/ImpactResult.java) — immutable record returned by the resolver.
- [src/main/java/com/tetris/mab/impact/RadiationGarbagePatternGenerator.java](src/main/java/com/tetris/mab/impact/RadiationGarbagePatternGenerator.java) — deterministic per-row hole pattern generator.
- [src/main/java/com/tetris/mab/impact/ImpactWaveState.java](src/main/java/com/tetris/mab/impact/ImpactWaveState.java) — mutable scheduled-wave holder.
- [src/main/java/com/tetris/mab/impact/ImpactResolver.java](src/main/java/com/tetris/mab/impact/ImpactResolver.java) — stateless resolver implementing the resolution rules.

## 2. Files modified

- [src/main/java/com/tetris/mab/ActiveLaunchState.java](src/main/java/com/tetris/mab/ActiveLaunchState.java) — added `NukeDesign` reference + getter (immutable; carried through resolution).
- [src/main/java/com/tetris/mab/NukeBuildState.java](src/main/java/com/tetris/mab/NukeBuildState.java) — added `reduceCharge(int)`.
- [src/main/java/com/tetris/mab/SiloState.java](src/main/java/com/tetris/mab/SiloState.java) — added `applyDamage(int)`, `repair(int)`, threshold-driven `refreshDamageState()`.
- [src/main/java/com/tetris/mab/MatchDebugSnapshot.java](src/main/java/com/tetris/mab/MatchDebugSnapshot.java) — added `siloIntegrity` and `pendingImpactWaveCount` to `ParticipantSummary`; `of(...)` now takes the wave count.
- [src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java](src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java) — pendingImpactWaves list, public `resolveImpact(launchId)` and `resolveAllImpactReady()`, wave-timer routing, all impact log events, snapshot update, passes `NukeDesign` into `ActiveLaunchState`.

## 3. Lifecycle

```
LAUNCH_AUTHORIZED → COUNTDOWN → IN_FLIGHT → IMPACT_READY → (explicit) → RESOLVED
```

`handleFlightWarningCompleted(...)` still ONLY marks the launch + threat as `IMPACT_READY`. It does NOT call `resolveImpact`. Resolution is explicit-only via:

- `match.resolveImpact(launchId)` — resolve a single launch.
- `match.resolveAllImpactReady()` — resolve every currently `IMPACT_READY` launch.

## 4. RadiationLevel mapping

`RadiationLevel.fromRating(int)`:

| `getRadiationRating()` | RadiationLevel | messinessScore |
|---|---|---|
| ≤ 0 | CLEAN  | 0 |
| 1   | LIGHT  | 1 |
| 2   | DIRTY  | 2 |
| 3   | HOT    | 3 |
| 4   | SEVERE | 4 |
| ≥ 5 | SALTED | 5 |

## 5. Radiation pattern rules

`RadiationGarbagePatternGenerator.generate(rows, boardWidth, level, tag)`:

- Each row has exactly one hole.
- `boardWidth = 10` (engine constant `Board.WIDTH`).
- The hole column is derived deterministically from row index, level messiness, and tag hash — no `Random`.
- `CLEAN` keeps the same hole column for every row (clean/standard garbage).
- Higher messiness shifts the hole more often:
  - `LIGHT` shifts roughly every 4 rows, `DIRTY` every 3, `HOT` every 2, `SEVERE` every row.
  - `SALTED` additionally jumps far across the board on a deterministic schedule.

Same `(rows, level, tag)` always produces the same pattern, which makes Step 6 trivial to test and replay.

## 6. ImpactGarbagePlan

Built per impact from the attacker's `NukeDesign.getGarbageProfile()`:

- `totalLines = baseGarbageLines`
- `immediateLines = min(totalLines, maxImmediateLines)`
- `delayedLines = totalLines - immediateLines`
- `waveCount` and `piecesBetweenWaves` come straight from the `GarbageProfile`. If `usesWaves()` is false but there are delayed lines, they collapse into a single wave.

## 7. Immediate vs delayed garbage

- **Immediate rows** are inserted via `defender.getGameState().insertGarbagePattern(patterns, "nuke:<launchId>:immediate:<radiation>")` during `resolveImpact`.
- **Delayed waves** are scheduled as `PieceCountdownTimer` instances on the existing `PieceTimerManager`, with `TimerAdvanceMode.OWNER_PIECES` (the defender's own pieces tick the timer down). Wave `i` fires after `piecesBetweenWaves * (i + 1)` defender pieces. Each wave's patterns are generated lazily inside `handleImpactGarbageWaveTimer(...)` from the stored `(rows, RadiationLevel, tag)` so the `ImpactWaveState` itself stays small and serializable.

## 8. Disarm damage

- Pulled from `NukeDesign.getDisarmRating()` (DEFCON-independent).
- Reduces `defender.getNukeBuildState().getCurrentBuildCharge()` by that amount, clamped at 0, via the new `reduceCharge(int)`.
- After reduction, `recomputeArmedAndOverbuilt()` runs, so a defender mid-arm can lose `armed=true`.
- `defenderNukeFullyDisarmed` is set when `chargeBefore > 0 && chargeAfter == 0` — matches the spec "fully disarmed" indicator.

## 9. Silo damage

- Pulled from `NukeDesign.getSiloDamageRating()` (DEFCON-independent).
- Reduces `defender.getSiloState().getIntegrity()` by that amount, clamped to `[0, 100]`, via the new `applyDamage(int)`.
- `refreshDamageState()` then maps integrity to `SiloDamageState`:
  - 75–100 → `STABLE`
  - 50–74  → `DAMAGED`
  - 25–49  → `COMPROMISED`
  - 0–24   → `CRITICAL`

Hardening / launch-security / mitigation upgrade effects are intentionally **not** consulted yet.

## 10. Pending waves & timers

- `match.getPendingImpactWaves()` — read-only snapshot.
- `routeCompletedTimer(...)` now has an `impact_garbage_wave:<launchId>:<waveIndex>` branch that calls `handleImpactGarbageWaveTimer(timerId)`.
- A wave that is already applied is logged as `IMPACT_WAVE_ALREADY_APPLIED`; a timer with no matching wave logs `IMPACT_WAVE_TIMER_ORPHANED`.
- The debug snapshot exposes `pendingImpactWaveCount` per participant.

## 11. Public APIs added

| API | Purpose |
|---|---|
| `match.resolveImpact(String launchId)` | Resolve one IMPACT_READY launch. |
| `match.resolveAllImpactReady()` | Resolve every IMPACT_READY launch in flight. |
| `match.getPendingImpactWaves()` | Inspect pending delayed waves. |
| `ActiveLaunchState.getNukeDesign()` | Immutable reference to the design used at launch. |
| `NukeBuildState.reduceCharge(int)` | Disarm-damage entry point. |
| `SiloState.applyDamage(int)` / `repair(int)` | Silo HP mutators. |
| `RadiationLevel.fromRating(int)` | Map design rating → level. |
| `RadiationGarbagePatternGenerator.generate(...)` | Deterministic patterns. |

Log events emitted: `IMPACT_RESOLUTION_STARTED`, `IMPACT_RESOLVED`, `IMPACT_SKIPPED`, `IMPACT_GARBAGE_IMMEDIATE_APPLIED`, `IMPACT_GARBAGE_WAVE_SCHEDULED`, `IMPACT_GARBAGE_WAVE_APPLIED`, `IMPACT_WAVE_TIMER_ORPHANED`, `IMPACT_WAVE_ALREADY_APPLIED`, `IMPACT_DISARM_APPLIED`, `IMPACT_SILO_DAMAGE_APPLIED`.

## 12. Explicit no auto-resolve

`handleFlightWarningCompleted(...)` only marks `IMPACT_READY`. There is no code path inside the match that calls `resolveImpact` automatically. Callers (UI, AI, tests) MUST invoke `resolveImpact(launchId)` or `resolveAllImpactReady()` explicitly.

## 13. Explicit DEFCON-no-damage

DEFCON does **not** modify any damage value. Blast, radiation, disarm, and silo-damage ratings are read directly from the attacker's `NukeDesign` at resolution time. Bigger bombs always hit harder regardless of readiness.

## 14. Not implemented (intentionally deferred)

- Interception / shoot-down windows.
- Civil defense and grace-buffer mitigations.
- Second-strike retaliation triggers.
- Decoy / radar effects on warning length.
- Upgrade-effect application to disarm / silo damage (hardening, distributed stockpile, launch security).
- AI driver decisions about when to call `resolveImpact`.
- (project is offline-only: local 1v1 + PvE + practice/debug; networking / online multiplayer are permanently out of scope.)

## 15. Acceptance verification

Manual checks performed against the resolver:

- A launch in `COUNTDOWN`, `IN_FLIGHT`, `RESOLVED`, or `CANCELLED` returns `SKIPPED_*` and does not mutate any state.
- An `IMPACT_READY` launch with a missing threat returns `THREAT_NOT_FOUND`.
- A resolved launch:
  - inserts exactly `min(baseGarbage, maxImmediate)` rows immediately,
  - schedules the rest as `waveCount` `OWNER_PIECES` timers on the defender,
  - reduces defender charge by `min(charge, disarmRating)`,
  - reduces defender integrity by `siloDamageRating` (clamped at 0),
  - marks both the launch and the threat as `RESOLVED`,
  - emits the full set of log events listed in §11.
- Wave timers fire through the existing `routeCompletedTimer(...)` path and call `handleImpactGarbageWaveTimer(...)`, which inserts deterministic patterns and emits `IMPACT_GARBAGE_WAVE_APPLIED`.

## 16. Build

```powershell
$env:Path = "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin;$env:Path"
cd 'c:\Users\leo.li2026\1\Desktop\CompSciSummative'
if (Test-Path target\classes) { Remove-Item -Recurse -Force target\classes }
New-Item -ItemType Directory -Force target\classes | Out-Null
$srcs = Get-ChildItem -Recurse -Filter *.java src\main\java | ForEach-Object { $_.FullName }
$srcs | Set-Content -Encoding ASCII $env:TEMP\tetris_sources.txt
javac -d target\classes -encoding UTF-8 "@$env:TEMP\tetris_sources.txt"
```

Result: `EXITCODE=0`.
