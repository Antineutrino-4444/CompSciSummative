# Step 4 — Spin-Aware Action Code System with Launch Confirmation

This step adds the data + state-machine layer for the "Action Code"
system: a player matches a sequence of line-clear tokens to trigger a
strategic action. T-spin / T-spin mini line clears can act as
wildcards. Strategic / launch actions require explicit confirmation
either via API call or a final non-replaceable hard 4.

This step does not yet execute action effects. Completed actions are
logged. Launch authorization is a placeholder log entry.

## 1. Files added / modified

### Added (all under `src/main/java/com/tetris/mab/action/`):
- [SpinKind.java](src/main/java/com/tetris/mab/action/SpinKind.java)
- [ActionClearToken.java](src/main/java/com/tetris/mab/action/ActionClearToken.java)
- [ActionCodeTokenRequirement.java](src/main/java/com/tetris/mab/action/ActionCodeTokenRequirement.java)
- [ActionConfirmationMode.java](src/main/java/com/tetris/mab/action/ActionConfirmationMode.java)
- [ActionType.java](src/main/java/com/tetris/mab/action/ActionType.java)
- [ActionCategory.java](src/main/java/com/tetris/mab/action/ActionCategory.java)
- [ActionCodeResult.java](src/main/java/com/tetris/mab/action/ActionCodeResult.java)
- [ActionCodeMatchMode.java](src/main/java/com/tetris/mab/action/ActionCodeMatchMode.java)
- [ActionCodeMatcher.java](src/main/java/com/tetris/mab/action/ActionCodeMatcher.java)
- [ActionCodeDifficultyRules.java](src/main/java/com/tetris/mab/action/ActionCodeDifficultyRules.java)
- [ActionCodeDefinition.java](src/main/java/com/tetris/mab/action/ActionCodeDefinition.java)
- [ActionCodeAttempt.java](src/main/java/com/tetris/mab/action/ActionCodeAttempt.java)
- [ActionCodeManager.java](src/main/java/com/tetris/mab/action/ActionCodeManager.java)
- [ActionCodeAmbiguityChecker.java](src/main/java/com/tetris/mab/action/ActionCodeAmbiguityChecker.java)
- [ActionCodeRegistry.java](src/main/java/com/tetris/mab/action/ActionCodeRegistry.java)
- [Step4.md](Step4.md)

### Modified:
- [ActionCodeProgressState.java](src/main/java/com/tetris/mab/ActionCodeProgressState.java) — rewritten to wrap `ActionCodeManager`.
- [ParticipantState.java](src/main/java/com/tetris/mab/ParticipantState.java) — added `getActionCodeManager()` helper, surfaced action state in `toDebugString()`.
- [MatchDebugSnapshot.java](src/main/java/com/tetris/mab/MatchDebugSnapshot.java) — `ParticipantSummary` extended with active and pending action-code fields plus completed action count.
- [MutuallyAssuredBlocksMatch.java](src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java) — added `actionCodeRegistry`, integrated action progression into `onLinesCleared`, added 6 new public methods (`startActionAttempt`, `startLaunchAttemptForCurrentNuke`, `startLaunchAttemptForCurrentNukeWithHardFourConfirm`, `confirmActionAttempt`, `cancelActionAttempt`, `getActionCodeRegistry`).

The Tetris engine itself (`com.tetris.controller`, `com.tetris.model`,
`com.tetris.view`, `com.tetris.events`) was not modified.

## 2. Action Code concept

An Action Code is an ordered sequence of line-clear tokens. The player
performs the action by clearing exactly that pattern of lines, in
order, while the attempt is active. For example, `[1, 2, 3]` requires
a single, then a double, then a triple.

A wrong line-clear immediately fails the attempt
(`ActionCodeResult.FAILED_RESET`).

## 3. Spin wildcard rule

A line-clear that is a recognised spin (currently T-spin or T-spin
mini, see §4) can act as a wildcard token that satisfies any normal
replaceable required token from 1 to 4. Examples for required code
`[1, 2, 1]`:

- `[SPIN, 2, 1]` ✓
- `[1, SPIN, 1]` ✓
- `[1, 2, SPIN]` ✓ (any spin line clear)

A `[1, 4, 1]` requirement is also satisfied by `[1, SPIN, 1]`, which
is exactly why those two patterns become ambiguous when used in the
same context (see §9, §10).

Implementation: `ActionCodeMatcher.matches(...)`. After the null and
zero-line guards, if the required token has `spinMayReplace == true`
and the actual token's `isSpinWildcard()` is true, the match
short-circuits true regardless of `lineCount`.

## 4. T-spin / T-spin mini support status

The engine event `LinesClearedEvent` exposes `tSpin` and `tSpinMini`
flags. Step 4 maps these to `SpinKind.T_SPIN` and `SpinKind.T_SPIN_MINI`
in `ActionClearToken.fromLinesClearedEvent(...)`. Both are eligible
wildcards (`SpinKind.isEligibleActionWildcard()` returns `true`).

`SpinKind.I_SPIN`, `J_SPIN`, `L_SPIN`, `S_SPIN`, `Z_SPIN` are present
in the enum for forward compatibility, but `isEligibleActionWildcard()`
returns `false` for them: the engine does not yet flag them. No new
spin physics or rotation work was added in this step.

## 5. O-spins do not count

`SpinKind` deliberately has no `O_SPIN` value. Even if a future engine
update ever flagged one, there is no enum constant to receive it.

## 6. Zero-line spins do not advance codes

`ActionClearToken.isLineClear()` and `ActionClearToken.isSpinWildcard()`
both require `lineCount > 0`. `ActionCodeManager.processLineClear(...)`
returns `IGNORED` for tokens with `lineCount <= 0`. A T-spin that
clears no lines does not advance any active attempt.

## 7. Confirmation modes

`ActionConfirmationMode`:

- `NONE` — sequence completion = action completion. Manager returns
  `COMPLETED` directly.
- `KEYBOARD_CONFIRM` — sequence completion moves the attempt to the
  pending-confirmation slot (`PENDING_CONFIRMATION`). The UI/input
  layer must call `MutuallyAssuredBlocksMatch.confirmActionAttempt(...)`
  to finish.
- `HARD_FOUR_CONFIRM` — the final required token is itself a hard
  4-line confirmation token. When that token is satisfied, the action
  completes immediately (`COMPLETED`); no separate confirmation call
  is needed.

Default for launches is `KEYBOARD_CONFIRM`. Most utility actions use
`NONE`. Strategic utility actions (`Concrete Blaster Arm`, `Treaty /
Restraint Lock`, `Full Intercept`) use `KEYBOARD_CONFIRM`.

## 8. Hard confirm 4 cannot be replaced by a spin

`ActionCodeTokenRequirement.hardConfirmFour()` produces
`(lineCount=4, spinMayReplace=false, confirmationToken=true)`. The
record's compact constructor rejects any attempt to construct a
confirmation token with `spinMayReplace=true`. In
`ActionCodeMatcher.matches(...)`, when `required.confirmationToken()`
is true the match function:

- rejects any `actual.spinKind() != NONE`
- requires `actual.lineCount() == required.requiredLineCount()`

so a T-spin Tetris (or any spin) cannot satisfy the final hard 4 of
a `HARD_FOUR_CONFIRM` launch. Only an ordinary 4-line clear works.

## 9. Ambiguity caused by spin wildcards

Because a spin can replace any normal replaceable token from 1..4, two
different sequences become indistinguishable from the player's input
side as soon as their differing positions are all spin-replaceable.
For example:

- `[1, 2, 1]` vs `[1, 4, 1]` — both are completed by `[1, SPIN, 1]`.
- `[2, 3, 2]` vs `[2, 1, 2]` — both are completed by `[2, SPIN, 2]`.

`ActionCodeAmbiguityChecker.findAmbiguities(definitions)` reports any
pair within the same category (or both launches) that has the same
length, the same confirmation mode, and only differs at positions
where both required tokens are normal replaceable tokens.

## 10. How default codes handle ambiguity

The default registry intentionally puts each action in its own
category whenever possible to avoid in-context collision. The
checker still reports cross-launch overlaps because all default
launch actions live in `LAUNCH` and use `KEYBOARD_CONFIRM`, so any two
length-matched launches with all-normal tokens trigger the rule.

These are surfaced (not silenced) so the future UI can make the
player pick which action they intended when multiple launch sequences
match. Warnings are stored on the registry and exposed via
`ActionCodeRegistry.getAmbiguityWarnings()`. The 23 default warnings
produced by `createDefault()` on this build are:

```
AMBIGUOUS: micro_launch [1,1] vs strategic_launch [4,4]
AMBIGUOUS: tactical_launch [1,2,1] vs theater_launch [2,3,2]
AMBIGUOUS: tactical_launch [1,2,1] vs dirty_launch [1,3,1]
AMBIGUOUS: tactical_launch [1,2,1] vs mirv_launch [3,2,3]
AMBIGUOUS: tactical_launch [1,2,1] vs concrete_blaster_launch [2,4,2]
AMBIGUOUS: tactical_launch [1,2,1] vs superheavy_launch [4,4,4]
AMBIGUOUS: theater_launch [2,3,2] vs dirty_launch [1,3,1]
AMBIGUOUS: theater_launch [2,3,2] vs mirv_launch [3,2,3]
AMBIGUOUS: theater_launch [2,3,2] vs concrete_blaster_launch [2,4,2]
AMBIGUOUS: theater_launch [2,3,2] vs superheavy_launch [4,4,4]
AMBIGUOUS: dirty_launch [1,3,1] vs mirv_launch [3,2,3]
AMBIGUOUS: dirty_launch [1,3,1] vs concrete_blaster_launch [2,4,2]
AMBIGUOUS: dirty_launch [1,3,1] vs superheavy_launch [4,4,4]
AMBIGUOUS: mirv_launch [3,2,3] vs concrete_blaster_launch [2,4,2]
AMBIGUOUS: mirv_launch [3,2,3] vs superheavy_launch [4,4,4]
AMBIGUOUS: concrete_blaster_launch [2,4,2] vs superheavy_launch [4,4,4]
AMBIGUOUS: doomsday_launch [4,4,4,4] vs masked_launch [1,2,1,3]
AMBIGUOUS: emergency_intercept [1,2,1] vs standard_intercept [1,2,3]
AMBIGUOUS: emergency_intercept [1,2,1] vs civil_defense [1,1,2]
AMBIGUOUS: standard_intercept [1,2,3] vs civil_defense [1,1,2]
AMBIGUOUS: silo_harden [2,2,2] vs counterlaunch_prep [3,1,3]
AMBIGUOUS: ghost_mirv [2,1,2,1] vs masked_launch [1,2,1,3]
AMBIGUOUS: false_doctrine_signal [3,1,1] vs dummy_silo_heat [2,2,1]
```

These warnings are intentionally **non-fatal** — the system is
designed so the player picks which action they're attempting via
`startActionAttempt(...)` *before* clearing lines. Mid-sequence the
manager only matches against the pre-selected definition, so spin
ambiguity cannot misroute the active attempt at runtime. The warnings
exist to inform the UI layer when offering an "auto-detect from line
clears" feature, which is intentionally not implemented in Step 4.
The `HARD_FOUR_CONFIRM` launch variants also disambiguate any pair in
that mode because the trailing hard 4 cannot be replaced by a spin.

## 11. ActionType and ActionCategory overview

`ActionType` (concrete identity) groups into roles via its
`isLaunch / isDefense / isIntel / isDecoy / isUtility` helpers.

`ActionCategory` (broad bucket): `LAUNCH`, `DEFENSE`, `INTEL`,
`DECOY`, `UTILITY`, `RESTRAINT`, `CUSTOM`.

The two enums are deliberately separate so e.g. `MASKED_LAUNCH` can
be a launch *type* (`isLaunch() == true`) but live in the `DECOY`
category for ambiguity grouping.

## 12. Default action-code table

| id | type | category | sequence | mode | armed? | threat? | charge |
|---|---|---|---|---|---|---|---|
| micro_launch | MICRO_LAUNCH | LAUNCH | [1,1] | KEYBOARD | yes | no | yes |
| tactical_launch | TACTICAL_LAUNCH | LAUNCH | [1,2,1] | KEYBOARD | yes | no | yes |
| theater_launch | THEATER_LAUNCH | LAUNCH | [2,3,2] | KEYBOARD | yes | no | yes |
| strategic_launch | STRATEGIC_LAUNCH | LAUNCH | [4,4] | KEYBOARD | yes | no | yes |
| dirty_launch | DIRTY_LAUNCH | LAUNCH | [1,3,1] | KEYBOARD | yes | no | yes |
| mirv_launch | MIRV_LAUNCH | LAUNCH | [3,2,3] | KEYBOARD | yes | no | yes |
| concrete_blaster_launch | CONCRETE_BLASTER_LAUNCH | LAUNCH | [2,4,2] | KEYBOARD | yes | no | yes |
| superheavy_launch | SUPERHEAVY_LAUNCH | LAUNCH | [4,4,4] | KEYBOARD | yes | no | yes |
| doomsday_launch | DOOMSDAY_LAUNCH | LAUNCH | [4,4,4,4] | KEYBOARD | yes | no | yes |
| emergency_intercept | EMERGENCY_INTERCEPT | DEFENSE | [1,2,1] | NONE | no | yes | no |
| standard_intercept | STANDARD_INTERCEPT | DEFENSE | [1,2,3] | NONE | no | yes | no |
| full_intercept | FULL_INTERCEPT | DEFENSE | [1,2,3,2,1] | KEYBOARD | no | yes | no |
| civil_defense | CIVIL_DEFENSE | DEFENSE | [1,1,2] | NONE | no | no | no |
| radar_scan | RADAR_SCAN | INTEL | [2,1,2] | NONE | no | no | no |
| silo_harden | SILO_HARDEN | UTILITY | [2,2,2] | NONE | no | no | no |
| counterlaunch_prep | COUNTERLAUNCH_PREP | UTILITY | [3,1,3] | NONE | no | no | no |
| emp_pulse | EMP_PULSE | UTILITY | [1,2,2,1] | NONE | no | no | no |
| concrete_blaster_arm | CONCRETE_BLASTER_ARM | UTILITY | [2,4,2] | KEYBOARD | no | no | no |
| treaty_restraint_lock | TREATY_RESTRAINT_LOCK | RESTRAINT | [1,1,1,1] | KEYBOARD | no | no | no |
| decoy_launch | DECOY_LAUNCH | DECOY | [1,3,1] | KEYBOARD | no | no | no |
| ghost_mirv | GHOST_MIRV | DECOY | [2,1,2,1] | KEYBOARD | no | no | no |
| false_doctrine_signal | FALSE_DOCTRINE_SIGNAL | DECOY | [3,1,1] | NONE | no | no | no |
| dummy_silo_heat | DUMMY_SILO_HEAT | DECOY | [2,2,1] | NONE | no | no | no |
| masked_launch | MASKED_LAUNCH | DECOY | [1,2,1,3] | KEYBOARD | yes | no | yes |

Per-nuke launch definitions are built dynamically by
`ActionCodeRegistry.launchDefinitionForNuke(design, defcon)` using
`design.effectiveLaunchCode(defcon)`. The hard-four variant appends
a non-replaceable hard 4 token via
`ActionCodeRegistry.launchDefinitionForNukeWithHardFourConfirm(...)`.

## 13. Difficulty matching rules

`ActionCodeDifficultyRules.matchModeFor(MatchDifficulty)`:

| Difficulty | Match mode |
|---|---|
| EASY | FLEXIBLE |
| NORMAL | MOSTLY_FLEXIBLE |
| HARD | STRICT |
| DOOMSDAY | STRICT |

Mode meanings (ordinary tokens only — spin wildcards are unaffected):

- `FLEXIBLE`: `actual.lineCount() >= required.requiredLineCount()`.
- `MOSTLY_FLEXIBLE`: strategic actions require exact match; non-strategic
  actions are flexible.
- `STRICT`: ordinary clears must match exactly.

Spin wildcard behavior is preserved in every mode (including
`STRICT`) unless the specific required token sets `spinMayReplace =
false`. This deliberately keeps spins valuable on Hard / Doomsday
while still preventing spin abuse on confirmation 4s.

## 14. Line clears keep building nuke charge

`MutuallyAssuredBlocksMatch.onLinesCleared` was not refactored; the
existing `participant.getNukeBuildState().addCharge(...)` call and
DEFCON escalation are unchanged. The new
`processActionForLineClear(...)` helper runs *after* charge and DEFCON
updates and only when `event.count() > 0`. The same line clear can
both build nuke charge and advance an active action code.

## 15. Completed actions are logged but not executed

When an action reaches `COMPLETED`, the match coordinator only logs:

- `ACTION_COMPLETED` with id/name/type/category metadata.
- For launch actions only, an additional `LAUNCH_AUTHORIZED_PLACEHOLDER`.

It does **not** create an `ActiveLaunchState`, consume nuke charge,
start a launch timer, or create incoming threats. Those belong to a
later step.

## 16. Public APIs added to `MutuallyAssuredBlocksMatch`

```java
public ActionCodeRegistry getActionCodeRegistry();

public boolean startActionAttempt(ParticipantId, ActionType);
public boolean startLaunchAttemptForCurrentNuke(ParticipantId);
public boolean startLaunchAttemptForCurrentNukeWithHardFourConfirm(ParticipantId);

public boolean confirmActionAttempt(ParticipantId);
public boolean cancelActionAttempt(ParticipantId, String reason);
```

All start-* methods return `false` and emit either
`ACTION_START_IGNORED`, `ACTION_START_REJECTED_NOT_ARMED`, or
`ACTION_START_REJECTED_NO_THREAT` when their preconditions fail.
`confirmActionAttempt` emits `ACTION_CONFIRM_IGNORED`,
`ACTION_CONFIRM_IGNORED_NONE_PENDING`, or `ACTION_CONFIRM_FAILED` when
appropriate. `cancelActionAttempt` emits
`ACTION_CANCEL_IGNORED_NONE_ACTIVE` if neither slot is occupied.

## 17. Launch confirmation placeholder behavior

- `KEYBOARD_CONFIRM` launch reaches sequence end →
  `ACTION_PENDING_CONFIRMATION` is logged. The launch is **not**
  authorised. Calling `confirmActionAttempt(participantId)` later logs
  `ACTION_CONFIRMED`, `ACTION_COMPLETED`, and
  `LAUNCH_AUTHORIZED_PLACEHOLDER`.
- `HARD_FOUR_CONFIRM` launch's final hard 4 is the confirmation token
  itself, so on completion it directly logs `ACTION_COMPLETED` and
  `LAUNCH_AUTHORIZED_PLACEHOLDER` without a separate API call.
- No `ActiveLaunchState`, no nuke-charge consumption, no launch
  timer, no incoming threat are created in either path. These are
  Step 5+ work.

## 18. Debug snapshot changes

`MatchDebugSnapshot.ParticipantSummary` adds:

```
activeActionId, activeActionName,
activeActionProgress, activeActionRequiredLength,
activeActionExpectedNextClear,
pendingConfirmationActionId, pendingConfirmationActionName,
completedActionCount
```

When no active action exists the action fields are `null` / `0`. When
no pending action exists the pending fields are `null`.

`ParticipantState.toDebugString()` now shows an `Action{...}` block.
Examples:

```
Action{none}
Action{radar_scan 1/3 next=2}
Action{pending tactical_launch}
```

## 19. Intentionally NOT implemented in this step

- Keyboard input wiring for action codes / confirmation.
- Actual missile launch execution.
- Incoming threats and missile impacts.
- Radiation / patterned garbage generation from impacts.
- Disarm resolution.
- Silo damage resolution.
- Upgrade UI.
- Radar scan effects.
- Decoy effects.
- Civil defense effects.
- Second-strike behavior.
- AI behavior.
- (project is offline-only: local 1v1 + PvE + practice/debug; networking / online multiplayer are permanently out of scope.)
- I/J/L/S/Z spin detection in the engine. The enum reserves the
  values; physics is not added.

## 20. Acceptance-criteria verification

| Criterion | Status |
|---|---|
| Compiles with the real javac build | ✓ `EXITCODE=0` |
| Single-player Tetris behavior unchanged | ✓ engine untouched |
| Step 2 match creation still works | ✓ `MutuallyAssuredBlocksMatch.createLocalPvp/createPve` API preserved |
| Step 3 NukeDesign defaults still work | ✓ `NukeDesignFactory.createAllDefaults()` untouched, used by launch builders |
| `ActionCodeDefinition` represents a token sequence | ✓ |
| Manager: start / advance / pending / confirm / complete / fail / cancel | ✓ `ActionCodeManager.processLineClear` + `confirmPending` + `cancelAttempt` |
| Spin wildcard replaces normal token when `spinMayReplace` | ✓ `ActionCodeMatcher` |
| Spin cannot replace hard confirm 4 | ✓ matcher rejects `actual.spinKind() != NONE` for confirmation tokens |
| Zero-line spins do not advance | ✓ `processLineClear` returns `IGNORED` for `lineCount <= 0` |
| O-spins do not count | ✓ `SpinKind` has no `O_SPIN` |
| EASY = flexible | ✓ |
| NORMAL = mostly flexible | ✓ |
| HARD / DOOMSDAY = strict | ✓ |
| Spin wildcard valuable in all modes | ✓ matcher checks wildcard before mode-specific logic |
| `startActionAttempt` starts utility actions | ✓ |
| `startLaunchAttemptForCurrentNuke` uses effective launch code | ✓ via `ActionCodeRegistry.launchDefinitionForNuke(design, defcon)` |
| Hard-four launch variant appends non-replaceable 4 | ✓ via `launchDefinitionForNukeWithHardFourConfirm` |
| Launch attempts require armed nuke | ✓ guarded in both private start helpers + per-definition `requiresArmedNuke` check |
| Intercept rejected if no incoming threat | ✓ `ACTION_START_REJECTED_NO_THREAT` |
| Line clears still build nuke charge | ✓ existing `addCharge` call preserved |
| Line clears advance active action codes | ✓ `processActionForLineClear` |
| KEYBOARD_CONFIRM launch enters pending | ✓ `ACTION_PENDING_CONFIRMATION` logged |
| `confirmActionAttempt` placeholder-authorises a launch | ✓ logs `LAUNCH_AUTHORIZED_PLACEHOLDER` |
| HARD_FOUR_CONFIRM authorises only on final hard 4 | ✓ matcher + manager flow |
| Completed actions logged but not executed | ✓ no launch state created |
| Debug snapshot includes active and pending action state | ✓ §18 |
| Step4.md documents the implementation | ✓ this document |

## 21. Build verification

```powershell
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

Maven is not on PATH on this machine; the javac command above is the
same form used by `run.bat` and is the authoritative compile.


---

## Step 4 refinement � ambiguity reduction

### Why the first implementation produced 23 warnings

The Step-4 base implementation gave every action a sequence of *normal
replaceable* tokens (`spinMayReplace = true`). Because a single spin
can satisfy any normal token from 1..4 lines, two same-length sequences
in the same category whose tokens are all replaceable collapse to the
same player input. Example: `tactical_launch [1,2,1]` and
`dirty_launch [1,3,1]` are both completed by `[1, SPIN, 1]`. With nine
launches all in `LAUNCH` + `KEYBOARD_CONFIRM`, almost every same-length
launch pair collided, and several defense / decoy pairs collided too �
giving 23 default warnings.

### The new anchored-token concept

`ActionCodeTokenRequirement` now supports three flavors:

| Notation | Constructor | spinMayReplace | confirmationToken | Meaning |
|---|---|---|---|---|
| `n`  | `normal(n)`         | true  | false | Normal replaceable token; an ordinary clear of `n` lines (or any spin) satisfies it. Subject to `MatchMode`. |
| `An` | `anchored(n)`       | false | false | Anchored token; **only** an exact ordinary `n`-line clear satisfies it. Spins do **not** apply, and higher line counts do not substitute even under `FLEXIBLE`. |
| `C4` | `hardConfirmFour()` | false | true  | Hard confirmation token: only an ordinary 4-line clear satisfies it. Spins do not apply. |

Anchors are the disambiguation primitive: two same-position tokens
`Aa` vs `Ab` with `a != b` cannot be jointly satisfied by any single
`ActionClearToken`, so the position is "incompatible" and the
ambiguity check rejects the pair. Spins remain useful at every
non-anchored position, so the anchored-token feature does **not**
remove spin value � it just pins down enough positions to make each
default sequence unique.

### Improved ambiguity checker

`ActionCodeAmbiguityChecker.positionsCompatible(...)` now applies
proper joint-satisfiability rules:

- both anchored ? compatible iff same line count;
- anchored vs normal ? compatible iff `anchorVal >= normalVal`
  (an ordinary `anchorVal` satisfies the normal side under `FLEXIBLE`);
- normal vs normal ? always compatible (a spin or a sufficient
  ordinary clear satisfies both);
- confirmation vs confirmation ? compatible iff same line count;
- confirmation vs anchored ? compatible iff anchored value == 4;
- confirmation vs normal ? compatible iff normalVal = 4 (always true
  in practice).

A pair is flagged only if every position is compatible AND they share
the same length, the same `ActionConfirmationMode`, and either the
same category or both are launches. The classic spin-collapse case
`[1,2,1] vs [1,4,1]` is still flagged (verified by direct test in the
build harness: 1 warning produced).

### Revised default action codes

All nine launches use `HARD_FOUR_CONFIRM`, ending in a non-replaceable
hard 4. Anchors are used sparingly to break remaining collisions.

| id | category | sequence | mode |
|---|---|---|---|
| micro_launch              | LAUNCH    | `[1, C4]`              | HARD_FOUR_CONFIRM |
| tactical_launch           | LAUNCH    | `[1, A2, C4]`          | HARD_FOUR_CONFIRM |
| theater_launch            | LAUNCH    | `[A2, 3, C4]`          | HARD_FOUR_CONFIRM |
| strategic_launch          | LAUNCH    | `[A4, 3, C4]`          | HARD_FOUR_CONFIRM |
| dirty_launch              | LAUNCH    | `[A1, 3, 2, C4]`       | HARD_FOUR_CONFIRM |
| mirv_launch               | LAUNCH    | `[A3, A2, 3, C4]`      | HARD_FOUR_CONFIRM |
| concrete_blaster_launch   | LAUNCH    | `[2, A4, 2, C4]`       | HARD_FOUR_CONFIRM |
| superheavy_launch         | LAUNCH    | `[4, A3, 4, C4]`       | HARD_FOUR_CONFIRM |
| doomsday_launch           | LAUNCH    | `[4, A4, 4, A4, C4]`   | HARD_FOUR_CONFIRM |
| emergency_intercept       | DEFENSE   | `[1, A2, A1]`          | NONE |
| standard_intercept        | DEFENSE   | `[1, 2, A3]`           | NONE |
| full_intercept            | DEFENSE   | `[1, A2, 3, A2, 1]`    | KEYBOARD_CONFIRM |
| civil_defense             | DEFENSE   | `[1, A1, 2]`           | NONE |
| radar_scan                | INTEL     | `[2, 1, A2]`           | NONE |
| silo_harden               | UTILITY   | `[A2, 2, A2]`          | NONE |
| counterlaunch_prep        | UTILITY   | `[A3, 1, A3]`          | NONE |
| emp_pulse                 | UTILITY   | `[1, 2, A2, 1]`        | NONE |
| concrete_blaster_arm      | UTILITY   | `[2, A4, 2]`           | KEYBOARD_CONFIRM |
| treaty_restraint_lock     | RESTRAINT | `[1, A1, 1, A1]`       | KEYBOARD_CONFIRM |
| decoy_launch              | DECOY     | `[1, A3, 1]`           | KEYBOARD_CONFIRM |
| ghost_mirv                | DECOY     | `[2, A1, 2, 1]`        | KEYBOARD_CONFIRM |
| false_doctrine_signal     | DECOY     | `[A3, A1, 1]`          | NONE |
| dummy_silo_heat           | DECOY     | `[2, A2, 1]`           | NONE |
| masked_launch             | DECOY     | `[1, A2, 1, A3]`       | KEYBOARD_CONFIRM |

### Final ambiguity warning count

`ActionCodeRegistry.createDefault().getAmbiguityWarnings().size() == 0`.

Verified by harness:

```
$ java -cp target\classes _AmbCheck
count=0
sanity_121_vs_141_warnings=1
  AMBIGUOUS: test_a [1,2,1] vs test_b [1,4,1] (spin wildcard could collapse them)
```

No warnings need per-pair justification because the list is empty.

### Notes on dynamic per-nuke launches

`ActionCodeRegistry.launchDefinitionForNuke(design, defcon)` and
`launchDefinitionForNukeWithHardFourConfirm(design, defcon)` are
**unchanged**. The first still uses `KEYBOARD_CONFIRM` (intentional �
custom per-nuke codes are user-authored and the explicit confirm is
the safety net), and the second still appends a hard 4 to the
user-authored code. Only the static default library in
`createDefault()` was redesigned.
