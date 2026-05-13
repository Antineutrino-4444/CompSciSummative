# Step 5 — Launch Authorization, Active Launches, Incoming Threats, and Piece Timers

## 1. Files added / modified

**Added**

- [src/main/java/com/tetris/mab/launch/LaunchPhase.java](src/main/java/com/tetris/mab/launch/LaunchPhase.java)
- [src/main/java/com/tetris/mab/launch/ThreatStatus.java](src/main/java/com/tetris/mab/launch/ThreatStatus.java)
- [Step5.md](Step5.md)

**Modified**

- [src/main/java/com/tetris/mab/ActiveLaunchState.java](src/main/java/com/tetris/mab/ActiveLaunchState.java) — placeholder replaced with real mutable lifecycle holder.
- [src/main/java/com/tetris/mab/IncomingThreatState.java](src/main/java/com/tetris/mab/IncomingThreatState.java) — placeholder replaced with real mutable lifecycle holder.
- [src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java](src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java) — real launch authorization, timer routing, warning ticks, public APIs.
- [src/main/java/com/tetris/mab/ParticipantState.java](src/main/java/com/tetris/mab/ParticipantState.java) — debug-string includes launch / threat info.
- [src/main/java/com/tetris/mab/MatchDebugSnapshot.java](src/main/java/com/tetris/mab/MatchDebugSnapshot.java) — `ParticipantSummary` extended with launch / threat counts and first-entry shorthand.

## 2. Launch lifecycle overview

```
ACTION COMPLETED (launch type)
        │
        ▼
authorizeLaunchFromAction()
   • build ActiveLaunchState (phase = COUNTDOWN)
   • register launch_countdown timer (owner=attacker, OWNER_PIECES)
   • resetCharge() on attacker NukeBuildState
   • DEFCON escalation (size-based)
   • LAUNCH_AUTHORIZED + LAUNCH_COUNTDOWN_STARTED logs
        │
        ▼  (attacker locks N pieces, N = effectiveLaunchTimePieces)
launch_countdown timer completes
        │
        ▼
handleLaunchCountdownCompleted()
   • launch.markInFlight(flightTimerId)  → phase = IN_FLIGHT
   • create IncomingThreatState (status = WARNING_ACTIVE) on defender
   • register flight_warning timer (owner=defender, OWNER_PIECES)
   • LAUNCH_IN_FLIGHT + INCOMING_THREAT_CREATED logs
        │
        ▼  (defender locks W pieces, W = effectiveWarningTimePieces; each lock
        │   also decrements threat.warningPiecesRemaining for visibility)
flight_warning timer completes
        │
        ▼
handleFlightWarningCompleted()
   • launch.markImpactReady()  → phase = IMPACT_READY
   • threat.markImpactReady()  → status = IMPACT_READY
   • IMPACT_READY log
        │
        ▼
[Step 6+] impact resolver picks these up; nothing in Step 5
```

## 3. `LaunchPhase` and `ThreatStatus`

`LaunchPhase`: `AUTHORIZED`, `COUNTDOWN`, `IN_FLIGHT`, `IMPACT_READY`,
`RESOLVED`, `CANCELLED`. Step 5 only enters and exits `COUNTDOWN`,
`IN_FLIGHT`, `IMPACT_READY`. `AUTHORIZED` is permitted as a starting
phase (used as an alternate entry point should a future caller want
to delay countdown registration). `RESOLVED` / `CANCELLED` exist for
later steps.

`ThreatStatus`: `WARNING_ACTIVE`, `IMPACT_READY`, `INTERCEPTED`,
`RESOLVED`, `CANCELLED`. Step 5 only enters and exits
`WARNING_ACTIVE` and `IMPACT_READY`.

## 4. How action-code completion authorizes a launch

Both completion paths call `authorizeLaunchFromAction(...)`:

1. `confirmActionAttempt(...)` — KEYBOARD_CONFIRM launches, after the
   pending confirmation succeeds.
2. `processActionForLineClear(...)` — HARD_FOUR_CONFIRM (and any
   future NONE-confirm) launches, when the manager returns
   `COMPLETED`.

Only when `def.getActionType().isLaunch()` is true. The previous
`LAUNCH_AUTHORIZED_PLACEHOLDER` log lines have been removed.

## 5. Nuke charge consumption

`authorizeLaunchFromAction` calls
`attacker.getNukeBuildState().resetCharge()` immediately after
constructing the `ActiveLaunchState`. This zeroes
`currentBuildCharge` and `overbuiltCharge` and clears the `armed`
flag. `setDesign` is **not** called, so the selected `NukeDesign`
(`getCurrentDesign()`) is preserved — the attacker can rebuild charge
for the same design without needing to re-pick.

Charge consumption happens at **authorization**, not at impact, per
the spec.

## 6. Launch countdown timer

- id     = `launch_countdown:<launchId>`
- owner  = attacker
- mode   = `OWNER_PIECES` — ticks only when attacker locks a piece
- pieces = `design.effectiveLaunchTimePieces(currentDefcon)`
- reason = `launch_countdown:<launchId>` (also used for routing on completion)

DEFCON affects the launch time through `effectiveLaunchTimePieces`,
not through any direct multiplier here. DEFCON does not affect
damage (out of scope for Step 5 anyway).

## 7. Incoming threat warning timer

- id     = `flight_warning:<launchId>`
- owner  = defender
- mode   = `OWNER_PIECES` — ticks only when defender locks a piece
- pieces = `design.effectiveWarningTimePieces(currentDefcon at launch time)`
- reason = `flight_warning:<launchId>`

The defcon used for warning pieces is captured at launch
authorization (`design.effectiveWarningTimePieces(defcon)`), not
re-evaluated at handover. This keeps the value the defender sees
stable for the lifetime of the threat.

## 8. Why warning timers tick on defender piece locks

A "5-piece warning" should mean *the defender places 5 of their own
pieces before impact* — i.e. 5 of the defender's stacking moves to
react with intercept actions, civil defense, etc. Using the
attacker's piece pace would make a faster attacker shorten the
defender's warning, which is the wrong invariant. Using
`EITHER_PLAYER_PIECES` would make warning length depend on how
quickly the attacker stacks during flight, also wrong.
`OWNER_PIECES` with `owner = defender` is the correct rule.

The defender-tick rule is mirrored in `onPieceLocked`, which
decrements `warningPiecesRemaining` on every `WARNING_ACTIVE` threat
that the locking participant owns. This keeps
`IncomingThreatState.getWarningPiecesRemaining()` consistent with
the timer's `getRemainingPieces()` for any defender-facing UI.

## 9. Impact-ready placeholder

When the warning timer completes, the launch's phase becomes
`IMPACT_READY` and the threat's status becomes `IMPACT_READY`. Both
remain in their respective participant lists, so a future Step 6
impact resolver can iterate
`getImpactReadyLaunches()` / `getImpactReadyThreats()` and apply
damage / garbage / radiation / civil defense effects. **Step 5
applies no damage of any kind.**

## 10. Public APIs added

On `MutuallyAssuredBlocksMatch`:

- `List<ActiveLaunchState> getActiveLaunches(ParticipantId)`
- `List<IncomingThreatState> getIncomingThreats(ParticipantId)`
- `List<ActiveLaunchState> getImpactReadyLaunches()`
- `List<IncomingThreatState> getImpactReadyThreats()`

All return unmodifiable copies.

On `ActiveLaunchState`: full getters + `markCountdownStarted`,
`markInFlight(flightTimerId)`, `markImpactReady`, `markResolved`,
`markCancelled`, `toDebugString`.

On `IncomingThreatState`: full getters + `decrementWarningPieces`,
`markImpactReady`, `markIntercepted`, `markResolved`, `markCancelled`,
`toDebugString`.

## 11. Debug snapshot changes

`MatchDebugSnapshot.ParticipantSummary` adds:

- `activeLaunchCount`
- `incomingThreatCount` (already present)
- `impactReadyLaunchCount`
- `impactReadyThreatCount`
- `firstActiveLaunchId`
- `firstActiveLaunchPhase` (`LaunchPhase`)
- `firstIncomingThreatId`
- `firstIncomingThreatWarningPiecesRemaining`

`ParticipantState.toDebugString()` now ends with e.g.:

```
Launches{active=1 impactReady=0 first=launch-1 phase=COUNTDOWN} \
Threats{incoming=1 impactReady=0 first=threat-launch-1 warning=4}
```

No `Color[][]` or any rendering payload is exposed.

## 12. Intentionally NOT implemented in Step 5

- Impact damage application
- Garbage generation from impact
- Radiation generation
- Disarm resolution
- Silo damage resolution
- Interception resolution
- Civil defense effects
- Decoy effects
- Radar effects
- Second-strike behaviour
- AI behaviour
- (project is offline-only: local 1v1 + PvE + practice/debug; networking / online multiplayer are permanently out of scope.)

The launch / threat lifecycle stops at **"missile/threat is ready
to resolve impact later."**

## 13. Acceptance-criteria verification

| Criterion | Verified |
|---|---|
| Project compiles with the real compile command | Yes — `EXITCODE=0`. |
| Existing single-player Tetris behavior is unchanged | Yes — only `mab` package modified; engine code untouched. |
| Step 4 action-code APIs still exist | Yes — `startActionAttempt`, `confirmActionAttempt`, `cancelActionAttempt`, dynamic launch starters are unchanged. |
| Completing+confirming a launch creates an `ActiveLaunchState` | Yes — both `confirmActionAttempt` and `processActionForLineClear` route to `authorizeLaunchFromAction`. |
| Authorization consumes/reset nuke charge but preserves `NukeDesign` | Yes — `resetCharge()` only; `setDesign` not called. |
| Authorization adds a launch countdown timer | Yes — `pieceTimerManager.addTimer(...)` with reason `launch_countdown:<id>`. |
| Countdown completion → `IN_FLIGHT` | Yes — `handleLaunchCountdownCompleted` → `launch.markInFlight(...)`. |
| Countdown completion creates `IncomingThreatState` | Yes — appended to defender's `incomingThreats`. |
| Warning timer uses defender-owned piece locks | Yes — `OWNER_PIECES`, `owner = defender`. |
| Defender piece locks decrement `warningPiecesRemaining` | Yes — explicit decrement in `onPieceLocked` for every `WARNING_ACTIVE` threat owned by the locker. |
| Warning completion marks both `IMPACT_READY` | Yes — `handleFlightWarningCompleted`. |
| No impact damage applied | Yes — no damage / garbage / silo code paths invoked. |
| No garbage inserted by nuke impact | Yes — Step 5 never calls any `insertGarbage*` API. |
| No disarm or silo damage applied | Yes — those state classes are untouched. |
| Debug snapshot exposes launch / threat counts | Yes — `ParticipantSummary` extended. |
| Step5.md documents the implementation | This file. |

## 14. Build command and result

```
$env:Path = "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin;$env:Path"
cd 'c:\Users\leo.li2026\1\Desktop\CompSciSummative'
if (Test-Path target\classes) { Remove-Item -Recurse -Force target\classes }
New-Item -ItemType Directory -Force target\classes | Out-Null
$srcs = Get-ChildItem -Recurse -Filter *.java src\main\java | ForEach-Object { $_.FullName }
$srcs | Set-Content -Encoding ASCII $env:TEMP\tetris_sources.txt
javac -d target\classes -encoding UTF-8 "@$env:TEMP\tetris_sources.txt"
Write-Host "EXITCODE=$LASTEXITCODE"
# → EXITCODE=0
```
