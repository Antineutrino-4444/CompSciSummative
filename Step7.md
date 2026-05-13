# Step 7 — Mutually Assured Blocks: Interception System and Threat Defense Resolution

This step turns the existing intercept action codes into real defensive resolutions. Completed `EMERGENCY_INTERCEPT`, `STANDARD_INTERCEPT`, and `FULL_INTERCEPT` actions now run against an incoming `WARNING_ACTIVE` threat, deterministically producing either a full intercept (threat cancelled) or a partial intercept (threat continues but Step 6 impact damage is reduced).

---

## 1. Files added/modified

**Added** (under `com.tetris.mab.intercept`):

- [src/main/java/com/tetris/mab/intercept/InterceptType.java](src/main/java/com/tetris/mab/intercept/InterceptType.java)
- [src/main/java/com/tetris/mab/intercept/InterceptOutcome.java](src/main/java/com/tetris/mab/intercept/InterceptOutcome.java)
- [src/main/java/com/tetris/mab/intercept/InterceptDefinition.java](src/main/java/com/tetris/mab/intercept/InterceptDefinition.java)
- [src/main/java/com/tetris/mab/intercept/InterceptRegistry.java](src/main/java/com/tetris/mab/intercept/InterceptRegistry.java)
- [src/main/java/com/tetris/mab/intercept/InterceptResult.java](src/main/java/com/tetris/mab/intercept/InterceptResult.java)
- [src/main/java/com/tetris/mab/intercept/InterceptMitigationState.java](src/main/java/com/tetris/mab/intercept/InterceptMitigationState.java)
- [src/main/java/com/tetris/mab/intercept/InterceptResolver.java](src/main/java/com/tetris/mab/intercept/InterceptResolver.java)

**Modified**:

- [src/main/java/com/tetris/mab/IncomingThreatState.java](src/main/java/com/tetris/mab/IncomingThreatState.java) — added `InterceptMitigationState`, `applyPartialIntercept`, `markFullyIntercepted` overload.
- [src/main/java/com/tetris/mab/ActiveLaunchState.java](src/main/java/com/tetris/mab/ActiveLaunchState.java) — added `InterceptMitigationState`, `applyPartialIntercept`, `markFullyIntercepted` (sets phase `CANCELLED`).
- [src/main/java/com/tetris/mab/ParticipantState.java](src/main/java/com/tetris/mab/ParticipantState.java) — added `selectedInterceptThreatId` + getters/setters/clearer; updated debug string to show intercept counts.
- [src/main/java/com/tetris/mab/MatchDebugSnapshot.java](src/main/java/com/tetris/mab/MatchDebugSnapshot.java) — added `selectedInterceptThreatId`, `interceptableThreatCount`, `fullyInterceptedThreatCount`, `partiallyMitigatedThreatCount`.
- [src/main/java/com/tetris/mab/impact/ImpactResolver.java](src/main/java/com/tetris/mab/impact/ImpactResolver.java) — reads `InterceptMitigationState` multipliers and applies them to a local copy of blast/radiation/disarm/silo damage. `NukeDesign` is never mutated. Skips with `SKIPPED_CANCELLED` when threat status is `INTERCEPTED` or mitigation is fully intercepted.
- [src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java](src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java) — added intercept registry/resolver, `dispatchCompletedAction(...)`, `selectInterceptTarget(...)`, `clearInterceptTarget(...)`, `resolveInterceptForSelectedThreat(...)`, `resolveInterceptFromAction(...)`, target picker, and the `ACTION_START_REJECTED_NO_ACTIVE_THREAT` refinement.

## 2. Interception lifecycle overview

```
LAUNCH_AUTHORIZED → COUNTDOWN → IN_FLIGHT → (warning ticking)
                                              │
                  ┌── intercept action completes ──┐
                  │                                │
            FULLY_INTERCEPTED                PARTIALLY_INTERCEPTED
            launch=CANCELLED                 launch stays IN_FLIGHT
            threat=INTERCEPTED               threat stays WARNING_ACTIVE
            timer cancelled                   timer keeps ticking
            no impact                         eventually IMPACT_READY
                                              → reduced impact damage
```

Intercepts only operate on `WARNING_ACTIVE` threats. Once a threat reaches `IMPACT_READY` it is too late to intercept (`FAILED_TOO_LATE`).

## 3. `InterceptType` / `InterceptOutcome`

`InterceptType`: `EMERGENCY`, `STANDARD`, `FULL` — speed-vs-strength tiers.

`InterceptOutcome`: `FULLY_INTERCEPTED`, `PARTIALLY_INTERCEPTED`, plus failure reasons `FAILED_NO_THREAT`, `FAILED_THREAT_NOT_ACTIVE`, `FAILED_TOO_LATE`, `FAILED_ALREADY_INTERCEPTED`, `FAILED_INVALID_TARGET`, `FAILED_ERROR`.

## 4. `InterceptDefinition` defaults

| Type      | Action               | Power | Blast↓ | Rad↓ | Disarm↓ | Silo↓ | Can fully intercept? |
|-----------|----------------------|-------|--------|------|---------|-------|-----------------------|
| EMERGENCY | EMERGENCY_INTERCEPT  | 2     | 0.35   | 0.25 | 0.20    | 0.20  | no                    |
| STANDARD  | STANDARD_INTERCEPT   | 4     | 0.60   | 0.50 | 0.40    | 0.40  | yes                   |
| FULL      | FULL_INTERCEPT       | 8     | 0.90   | 0.80 | 0.75    | 0.75  | yes                   |

Reduction ratios are clamped to `[0.0, 1.0]` in the record's compact constructor.

## 5. Threat resistance calculation

Pure deterministic function — no RNG.

Base by `NukeSizeCategory`: `MICRO=1, TACTICAL=2, THEATER=3, STRATEGIC=4, SUPERHEAVY=6, DOOMSDAY_SCALE=8`.

Modifiers:
- `+2` if doctrine is `MIRV`
- `+1` if doctrine is `DECOY_PACKAGE`
- `+2` if doctrine is `DOOMSDAY`
- `+1` if `nukeDesign.detectionProfile >= 4`
- `+1` if `threat.warningPiecesRemaining <= 1` (very late intercept)

Result is at least `1`.

## 6. Full vs partial intercept rules

```
canFull = definition.canFullyIntercept && interceptPower >= threatResistance
```

- `canFull == true` → `FULLY_INTERCEPTED`. `launch.markFullyIntercepted(def)` (phase → `CANCELLED`) and `threat.markFullyIntercepted(def)` (status → `INTERCEPTED`). The mitigation state collapses all multipliers to `0.0`.
- `canFull == false` → `PARTIALLY_INTERCEPTED`. Both launch and threat stack the def's reduction ratios via `addPartialMitigation(def)`. Stacking is additive but capped at `0.95` per damage category (so something always gets through, preventing back-door full intercepts via spam).
- `EMERGENCY` always partial (its `canFullyIntercept = false`).

## 7. Target selection rules

`MutuallyAssuredBlocksMatch.selectInterceptTarget(defenderId, threatId)`:

1. Verify the threat exists in the defender's `getIncomingThreats()`.
2. Verify its status is `WARNING_ACTIVE`.
3. Set `participant.selectedInterceptThreatId` and log `INTERCEPT_TARGET_SELECTED`.

When an intercept action completes, the resolver looks up the target as:

1. The selected threat, if it is still `WARNING_ACTIVE`.
2. Otherwise, the first `WARNING_ACTIVE` threat in `getIncomingThreats()`.
3. Otherwise, return `FAILED_NO_THREAT`.

After resolution, the selected target is cleared if it matches the resolved threat.

## 8. Timer cancellation for full intercepts

Full intercept calls `pieceTimerManager.cancelTimer(threat.getFlightTimerId())`, which force-removes the warning timer so it never enters `consumeCompletedTimers()`. The match logs `INTERCEPT_TIMER_CANCELLED`. As an additional safety net, `handleFlightWarningCompleted(...)` already early-returns when the launch phase is `CANCELLED`, logging `FLIGHT_TIMER_IGNORED_PHASE`.

## 9. How partial mitigation affects Step 6 impact resolution

`ImpactResolver.resolveImpact(...)` now:

1. Skips with `SKIPPED_CANCELLED` if the threat status is `INTERCEPTED` or its `InterceptMitigationState.isFullyIntercepted()` is true. **No garbage, no disarm, no silo damage.**
2. For partial mitigation, computes effective values without mutating the `NukeDesign`:
   ```
   effectiveBlast      = round(design.blastRating      * mit.effectiveBlastMultiplier())
   effectiveRadiation  = round(design.radiationRating  * mit.effectiveRadiationMultiplier())
   effectiveDisarm     = round(design.disarmRating     * mit.effectiveDisarmMultiplier())
   effectiveSiloDamage = round(design.siloDamageRating * mit.effectiveSiloDamageMultiplier())
   ```
3. Garbage plan is built from `effectiveBlast` (the immediate cap shrinks proportionally so small impacts don't all become "immediate").
4. Returned `ImpactResult` carries the **effective** ratings in its `blastRating` / `radiationRating` / `disarmRating` / `siloDamageRating` fields, and the `message` includes a compact mitigation summary.

## 10. Public APIs added

| API | Purpose |
|---|---|
| `match.selectInterceptTarget(ParticipantId, String)` | Choose which incoming threat to intercept next. |
| `match.clearInterceptTarget(ParticipantId)` | Forget the previously-selected target. |
| `match.resolveInterceptForSelectedThreat(ParticipantId, ActionType)` | Test/debug entry point — runs the resolver without going through the action-code system. |
| `IncomingThreatState.getInterceptMitigation()`, `applyPartialIntercept(def)`, `markFullyIntercepted(def)` | Threat-side mitigation. |
| `ActiveLaunchState.getInterceptMitigation()`, `applyPartialIntercept(def)`, `markFullyIntercepted(def)` | Launch-side mitigation. |
| `ParticipantState.getSelectedInterceptThreatId()`, `setSelectedInterceptThreatId(id)`, `clearSelectedInterceptThreatId()` | Target-selection state. |
| `InterceptRegistry.createDefault()`, `getByActionType(...)`, `getByInterceptType(...)`, `getAll()` | Definition lookup. |
| `InterceptResolver.resolveIntercept(def, launch, threat, defender, attacker)` | Stateless resolver. |

Log events emitted: `INTERCEPT_TARGET_SELECTED`, `INTERCEPT_TARGET_CLEARED`, `INTERCEPT_TARGET_SELECT_FAILED`, `INTERCEPT_STARTED`, `INTERCEPT_RESOLVED`, `INTERCEPT_FAILED`, `THREAT_PARTIALLY_INTERCEPTED`, `THREAT_FULLY_INTERCEPTED`, `INTERCEPT_TIMER_CANCELLED`, plus the new `ACTION_START_REJECTED_NO_ACTIVE_THREAT`.

## 11. Debug snapshot changes

`MatchDebugSnapshot.ParticipantSummary` gained:

- `String selectedInterceptThreatId`
- `int interceptableThreatCount` — threats with status `WARNING_ACTIVE`
- `int fullyInterceptedThreatCount` — threats with status `INTERCEPTED`
- `int partiallyMitigatedThreatCount` — threats with `totalInterceptPowerApplied > 0` and not fully intercepted

`ParticipantState.toDebugString()` now prints `interceptable`, `fullyIntercepted`, `partialMitig`, and `target` in the threats summary.

## 12. Determinism

Interception in this step is **fully deterministic**. There is no `Random` source anywhere in the intercept resolver or in the threat-resistance formula. The only sources of failure are:

- timing (threat already `IMPACT_READY` → `FAILED_TOO_LATE`)
- wrong action code (e.g. emergency vs a high-resistance strategic strike → `PARTIALLY_INTERCEPTED`)
- insufficient intercept power (`interceptPower < threatResistance` → partial only)
- wrong target (no warning-active threat → `FAILED_NO_THREAT`)

DEFCON does NOT modify damage ratings (Step 6 rule preserved).

## 13. Not implemented (intentionally deferred)

- Civil defense mitigation
- Grace buffer / delayed overflow protection
- Second-strike retaliation triggers
- Radar scan effects on warning length / detection profile
- Decoy effects
- Upgrade-effect application to intercept power, threat resistance, mitigation caps
- AI behavior (the AI never calls `selectInterceptTarget` / starts intercept actions)
- (project is offline-only: local 1v1 + PvE + practice/debug; networking / online multiplayer are permanently out of scope.)

## 14. Acceptance verification

Manual checks against the new code paths:

- `startActionAttempt` rejects an intercept attempt with `ACTION_START_REJECTED_NO_THREAT` (no incoming threats) or `ACTION_START_REJECTED_NO_ACTIVE_THREAT` (only resolved/intercepted/impact-ready threats remain).
- A defender can call `selectInterceptTarget(defenderId, threatId)` for any `WARNING_ACTIVE` threat and the snapshot reports the selection.
- Without a selection, an intercept resolves against the first `WARNING_ACTIVE` threat.
- `EMERGENCY_INTERCEPT` against any threat results in `PARTIALLY_INTERCEPTED` (`canFullyIntercept=false`).
- `STANDARD_INTERCEPT` against `MICRO`/`TACTICAL`/`THEATER`/`STRATEGIC` threats produces `FULLY_INTERCEPTED` (4 ≥ resistance) provided the threat is plain doctrine and warning has not collapsed; against larger / heavily-modifier-stacked threats it falls back to partial.
- `FULL_INTERCEPT` (power 8) reaches every base size including `DOOMSDAY_SCALE` until doctrine + late-warning modifiers push resistance above 8.
- Fully intercepted threats: warning timer is cancelled (`INTERCEPT_TIMER_CANCELLED`), launch phase = `CANCELLED`, threat status = `INTERCEPTED`. They never reach `IMPACT_READY`, and `ImpactResolver` returns `SKIPPED_CANCELLED`.
- Partially intercepted threats remain `WARNING_ACTIVE`, eventually reach `IMPACT_READY`, and the impact resolver applies reduced effective values (`blastRating`, `radiationRating`, `disarmRating`, `siloDamageRating`) inside the returned `ImpactResult`. The `NukeDesign` is unchanged.
- Existing single-player Tetris behaviour is untouched (Step 7 does not touch the engine).

## 15. Build

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
