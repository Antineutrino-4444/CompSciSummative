# Step 8 — Mutually Assured Blocks: Civil Defense, Impact Grace Buffer, and Safer Garbage Application

This step turns the existing `CIVIL_DEFENSE` action code into real defensive mitigation and adds an impact-grace buffer that protects defenders from instant unavoidable top-outs by shifting overflow garbage into delayed waves.

---

## 1. Files added/modified

**Added** (under `com.tetris.mab.defense`):

- [src/main/java/com/tetris/mab/defense/CivilDefenseMitigation.java](src/main/java/com/tetris/mab/defense/CivilDefenseMitigation.java)
- [src/main/java/com/tetris/mab/defense/ImpactGracePolicy.java](src/main/java/com/tetris/mab/defense/ImpactGracePolicy.java)
- [src/main/java/com/tetris/mab/defense/ImpactGraceDecision.java](src/main/java/com/tetris/mab/defense/ImpactGraceDecision.java)

**Modified**:

- [src/main/java/com/tetris/mab/CivilDefenseState.java](src/main/java/com/tetris/mab/CivilDefenseState.java) — replaced placeholder; new fields, activation/consume/tick semantics.
- [src/main/java/com/tetris/mab/ParticipantState.java](src/main/java/com/tetris/mab/ParticipantState.java) — debug string includes `CivilDefense{...}`.
- [src/main/java/com/tetris/mab/MatchDebugSnapshot.java](src/main/java/com/tetris/mab/MatchDebugSnapshot.java) — added `civilDefenseCharges`, `civilDefenseShieldPiecesRemaining`, `civilDefenseEmergencyActive`, `totalCivilDefenseActivations`.
- [src/main/java/com/tetris/mab/impact/ImpactResult.java](src/main/java/com/tetris/mab/impact/ImpactResult.java) — added 6 fields: `civilDefenseApplied`, `civilDefenseChargesConsumed`, `civilDefenseImmediateRowsReduced`, `requestedImmediateRowsBeforeGrace`, `allowedImmediateRowsAfterGrace`, `graceDeferredRows`.
- [src/main/java/com/tetris/mab/impact/ImpactResolver.java](src/main/java/com/tetris/mab/impact/ImpactResolver.java) — new overload accepting `CivilDefenseMitigation` + `ImpactGracePolicy`; applies CD after intercept; routes overflow into grace-deferred waves.
- [src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java](src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java) — `dispatchCompletedAction` handles `CIVIL_DEFENSE`; CD shield ticks in `onPieceLocked`; `resolveImpact` consumes CD only when ready and passes it + `ImpactGracePolicy` to the resolver; emits all new logs; `activateCivilDefenseManually`/`getCivilDefenseState` public APIs.

## 2. Civil-defense concept

Civil defense is a deterministic per-defender protection layer that **reduces** an incoming impact's effective damage. It does not erase the threat — interception is still the only way to fully stop a launch. CD is most effective against small/medium attacks and softens the immediate damage of large attacks; large attacks remain dangerous.

## 3. `CivilDefenseState` fields and behavior

| Field | Purpose |
|---|---|
| `activeCharges` (cap `maxCharges=3`) | Stored basic-protection charges. One is consumed per real impact. |
| `shieldPiecesRemaining` | Piece-locks the shield is still active for. Ticks down in `onPieceLocked`. |
| `emergencyProtocolActive` | True after an emergency activation; clears when the shield expires. |
| `totalActivations` | Cumulative `activateBasic` / `activateEmergency` calls. |
| `totalBlastReduced/Radiation/Disarm/SiloDamage` | Cumulative reduction stats. |

Methods:
- `activateBasic(shieldPieces)` — increments `activeCharges` (capped), extends shield duration with `max(existing, shieldPieces)`, increments `totalActivations`.
- `activateEmergency(shieldPieces)` — same, plus sets `emergencyProtocolActive = true`.
- `hasProtection()` — true if `activeCharges > 0` or `shieldPiecesRemaining > 0`.
- `consumeForImpact()` — returns `CivilDefenseMitigation.none()` if no protection; otherwise consumes one active charge (if any) and returns `basic(...)` or `emergency(...)` based on the emergency flag. Shield duration is **not** cleared here.
- `tickPiece()` — decrements `shieldPiecesRemaining`; clears `emergencyProtocolActive` when it reaches zero.
- `addReductionStats(...)` — accumulates reduction totals.

## 4. `CivilDefenseMitigation` ratios

Ratios are clamped to `[0.0, 0.9]` so CD alone can never reduce damage to zero.

| Profile | Blast↓ | Rad↓ | Disarm↓ | Silo↓ | Immediate−rows | Extra grace rows |
|---|---|---|---|---|---|---|
| `basic`     | 0.25 | 0.30 | 0.15 | 0.15 | 1 | 2 |
| `emergency` | 0.40 | 0.45 | 0.25 | 0.25 | 2 | 4 |
| `none`      | 0.00 | 0.00 | 0.00 | 0.00 | 0 | 0 |

`emergency` factories exist on the type; in this step the `CIVIL_DEFENSE` action code only triggers `activateBasic`. The emergency profile is wired in for later (e.g. upgrade/policy effects).

## 5. `CIVIL_DEFENSE` activation flow

When the action-code system marks a `CIVIL_DEFENSE` attempt complete, `MutuallyAssuredBlocksMatch.dispatchCompletedAction` routes it to:

```
activateCivilDefense(participant, "action:" + def.getId())
  → participant.getCivilDefenseState().activateBasic(CIVIL_DEFENSE_SHIELD_PIECES)
  → log("CIVIL_DEFENSE_ACTIVATED", participant, source, {...})
```

Default `CIVIL_DEFENSE_SHIELD_PIECES = 12`. Activation works regardless of whether the participant currently has any incoming threat. There is no target selection.

`activateCivilDefenseManually(ParticipantId)` is a debug/test entry point that runs the same path under the standard phase guard (ACTIVE, not paused, no winner). On rejection it logs `CIVIL_DEFENSE_MANUAL_ACTIVATION_IGNORED`.

## 6. Shield duration ticking

In the `GameEventListener.onPieceLocked` handler installed for each participant, after the gameplay-mutation guard:

```java
int before = cds.getShieldPiecesRemaining();
cds.tickPiece();
if (before > 0 && cds.getShieldPiecesRemaining() == 0) {
    log("CIVIL_DEFENSE_EXPIRED", ...);
}
```

Per-piece logs are deliberately suppressed — only the expiration moment is logged.

## 7. Civil-defense consumption during impact resolution

`MutuallyAssuredBlocksMatch.resolveImpact(launchId)` performs a readiness check **before** consuming protection:

```java
boolean readyForImpact = defender != null
        && launch.getPhase() == LaunchPhase.IMPACT_READY
        && threat != null
        && threat.getStatus() == ThreatStatus.IMPACT_READY;
if (readyForImpact && defender.getCivilDefenseState().hasProtection()) {
    cdMitigation = defender.getCivilDefenseState().consumeForImpact();
    log("CIVIL_DEFENSE_CONSUMED", ...);
}
```

If the resolver later returns `SKIPPED_*`, no protection has been spent because consumption only happens once readiness is established. After a `RESOLVED` impact, the match coordinator computes the per-category reduction deltas (`originalRating − effectiveRating`), feeds them into `cds.addReductionStats(...)`, and emits `CIVIL_DEFENSE_MITIGATION_APPLIED`.

Inside `ImpactResolver`, the order is:

```
post-intercept = original × interceptMultiplier
effective       = post-intercept × civilDefenseMultiplier      ← Step 8
plannedImmediateRows = build garbage plan from effectiveBlast
afterCdImmediate     = max(0, plannedImmediateRows − cd.immediateGarbageReduction)
graceDecision        = ImpactGracePolicy.decide(defender, afterCdImmediate, ...)
allowedImmediateRows = graceDecision.allowedImmediateRows
deferredRows         = graceDecision.deferredRows
```

The `NukeDesign` is never mutated.

## 8. `ImpactGracePolicy`

Purpose: decide deterministically how many of the requested immediate garbage rows are safe to insert this tick, and how many should be deferred.

```
boardHeight     = Board.VISIBLE_HEIGHT (= 20)
stackHeight     = defender.gameState.getBoardHeight()
                  // engine helper that returns CURRENT stack height;
                  // falls back to a snapshot scan if it returns 0.
emergencyHeadroom = 2 + extraGraceRows           // CD adds 2 (basic) / 4 (emergency)
safeRows          = max(0, boardHeight − stackHeight − emergencyHeadroom)
allowedImmediate  = min(requestedImmediateRows, safeRows)
deferredRows      = requestedImmediateRows − allowedImmediate
```

Returned in an `ImpactGraceDecision` along with the inputs for debug visibility.

## 9. Safe immediate garbage calculation

The chosen calculation is the conservative `boardHeight − stackHeight − headroom`. The engine's `GameState.getBoardHeight()` is mis-named — it actually returns the current stack height — so it serves directly as `stackHeight`. When that helper returns `0` we cross-check against `getBoardSnapshot()` to find the topmost occupied row (counted from the bottom, capped at `Board.VISIBLE_HEIGHT`). If no game state is available we treat the board as empty (`stackHeight = 0`) and fall back to the default `boardHeight = Board.VISIBLE_HEIGHT`.

Headroom is always at least `2`, plus any `extraGraceRows` supplied by civil defense (`+2` basic, `+4` emergency). This guarantees a small buffer even on a clean board, so a single huge impact still leaves the player a couple of free rows to recover from after garbage insertion.

## 10. Deferred garbage waves

When `deferredRows > 0`:

- The resolver schedules **one row per deferred wave** so the damage is delivered gently rather than as a single chunk.
- Each wave uses an `OWNER_PIECES` `PieceCountdownTimer` with interval `2 × (i + 1)` — the first deferred wave fires 2 defender pieces later, the next 4 pieces later, etc. (sequential scheduling matches the existing Step-6 designed-wave layout.)
- Wave indices continue after the design's existing delayed waves so the `(i+1)/N` debug labels stay monotonic.
- Timer reason tag: `nuke:<launchId>:grace-wave:<i>:<radiation>`.
- Each wave is registered through the same `waveSink` callback as designed waves and routed back through the existing `pendingImpactWaves`/`handleImpactGarbageWaveTimer` path. They use the same `ImpactWaveState` / `RadiationGarbagePatternGenerator` machinery, so radiation messiness is preserved on deferred rows.
- Deferred rows are reflected in `result.garbageLinesDelayed()` and additionally exposed via the new `result.graceDeferredRows()` field.
- Logged as `IMPACT_GRACE_DEFERRED_GARBAGE` (and `IMPACT_GARBAGE_IMMEDIATE_SKIPPED_BY_GRACE` if the entire immediate slice was deferred).

Deferred rows are **never discarded**.

## 11. Grace buffer is delay, not erasure

The grace buffer only changes _when_ overflow garbage arrives — it does not remove rows. A defender pinned at top-of-stack will still receive every requested row across the next few piece locks; the engine just gets a chance to clear lines first.

## 12. DEFCON does not modify damage

DEFCON values continue to influence build/launch readiness only. No part of impact resolution reads DEFCON to scale blast, radiation, disarm, or silo damage.

## 13. Public APIs added

| API | Purpose |
|---|---|
| `match.activateCivilDefenseManually(ParticipantId)` | Debug/test activation outside the action-code path; obeys phase guard. |
| `match.getCivilDefenseState(ParticipantId)` | Read-only access for tests/UI. |
| `CivilDefenseState.activateBasic / activateEmergency / consumeForImpact / tickPiece / hasProtection / addReductionStats / toDebugString` | State management. |
| `CivilDefenseMitigation.none / basic / emergency` + multiplier accessors | Mitigation profile factories. |
| `ImpactGracePolicy.decide(defender, requested, alreadyDelayed, extraGrace, reason)` | Returns an `ImpactGraceDecision`. |
| `ImpactResolver.resolveImpact(...)` (new overload) | Adds `CivilDefenseMitigation` + `ImpactGracePolicy` parameters. |

New log events: `CIVIL_DEFENSE_ACTIVATED`, `CIVIL_DEFENSE_MANUAL_ACTIVATION_IGNORED`, `CIVIL_DEFENSE_CONSUMED`, `CIVIL_DEFENSE_MITIGATION_APPLIED`, `CIVIL_DEFENSE_EXPIRED`, `IMPACT_GRACE_APPLIED`, `IMPACT_GRACE_DEFERRED_GARBAGE`, `IMPACT_GARBAGE_IMMEDIATE_SKIPPED_BY_GRACE`.

## 14. Debug snapshot changes

`MatchDebugSnapshot.ParticipantSummary` now ends with:

- `int civilDefenseCharges`
- `int civilDefenseShieldPiecesRemaining`
- `boolean civilDefenseEmergencyActive`
- `int totalCivilDefenseActivations`

`pendingImpactWaveCount` was already present and now includes grace-deferred waves automatically.

`ParticipantState.toDebugString()` now appends `CivilDefense{charges=… shield=… emergency=… activations=…}`.

## 15. Not implemented (intentionally deferred)

- Upgrade UI and silo upgrades (no upgrade modifiers on CD ratios or shield duration).
- Radar scan effects on CD activation.
- Decoy effects.
- Second-strike retaliation triggers.
- Treaty/restraint bonuses.
- AI behavior (the AI never calls `activateCivilDefenseManually` and currently does not pursue `CIVIL_DEFENSE` action codes).
- (project is offline-only: local 1v1 + PvE + practice/debug; networking / online multiplayer are permanently out of scope.)
- Emergency activation is wired in `CivilDefenseState` and `CivilDefenseMitigation` but no current trigger calls `activateEmergency`. It is reserved for a later step (e.g. low-health / DEFCON-1 panic).

## 16. Acceptance verification

Manual check against the new code paths:

- Project compiles with the real javac command (see §17).
- Step 1–7 paths are unchanged. The single-player engine is untouched. Existing intercept tests still take the new resolver overload through the no-CD path (`CivilDefenseMitigation.none()` + default `ImpactGracePolicy`) via the legacy resolver overload, which delegates to the new one with neutral arguments.
- Completing a `CIVIL_DEFENSE` action: `dispatchCompletedAction` routes to `activateCivilDefense(...)`, which calls `activateBasic(12)` and emits `CIVIL_DEFENSE_ACTIVATED`. No launch is authorized, no intercept is run.
- `activateCivilDefenseManually` succeeds during ACTIVE/unpaused/no-winner, otherwise logs `CIVIL_DEFENSE_MANUAL_ACTIVATION_IGNORED`.
- `shieldPiecesRemaining` decrements once per defender piece-lock (in `onPieceLocked`); `CIVIL_DEFENSE_EXPIRED` fires only on the transition from positive to zero.
- `CivilDefenseState.consumeForImpact()` is only called once `resolveImpact` confirms IMPACT_READY for both launch and threat → SKIPPED resolutions never spend protection.
- For a basic CD profile, the resolver computes effective ratings as `original × interceptMul × cdMul`, so blast/radiation/disarm/silo damage values returned in `ImpactResult` are reduced. With basic ratios capped at 0.9 and clamped, no single CD application can zero out damage.
- `ImpactGracePolicy.decide` caps immediate rows by `boardHeight − stackHeight − headroom`. Deferred rows show up in `result.graceDeferredRows()` and as new pending impact waves, scheduled at 2-piece intervals. They eventually apply through the existing `handleImpactGarbageWaveTimer` path.
- Radiation messiness is preserved: grace-deferred waves are produced with `RadiationGarbagePatternGenerator.generate(1, width, rad, ...)`.
- DEFCON state is not read by `ImpactResolver` or by the CD multipliers.
- `MatchDebugSnapshot.ParticipantSummary` exposes the four new CD fields and `pendingImpactWaveCount`.

## 17. Build

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
