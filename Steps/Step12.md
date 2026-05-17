# Step 12 — Mutually Assured Blocks: Visible Offline Debug HUD, Local Match Harness, and Manual Strategic Controls

This step makes every backend system added across Steps 1–11 *visibly
testable* from the running game. Until now the Mutually Assured Blocks
(MAB) work was pure model/API plumbing: the player launching the
existing single-player Tetris saw an unchanged board because none of the
strategic state was wired to a renderer. Step 12 adds a clearly-labelled
**MAB Debug / Vertical Slice** window that shows live match state and
exposes manual buttons to drive every strategic system.

The game remains **offline-only**. No networking, sockets, client/server
abstractions, matchmaking, rollback, or online-prep architecture is
introduced. Supported modes are local 1v1, PvE, and practice/debug.

---

## 1. Files added

```
src/main/java/com/tetris/mab/debugui/MabDebugFormatter.java
src/main/java/com/tetris/mab/debugui/MabDebugController.java
src/main/java/com/tetris/mab/debugui/MabDebugFrame.java
Step12.md
```

## 2. Files modified

```
src/main/java/com/tetris/controller/GameController.java
src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java
```

`MutuallyAssuredBlocksMatch` gained `getRecentEvents`, the
`debugXxx(...)` family, and an extracted `authorizeLaunchInternal`
helper now shared by the action-code and debug launch paths.
`GameController` opens/closes the debug window from `start()` and
`startEmbedded()`; the existing single-player code paths are otherwise
untouched.

## 3. UI inspection findings

| Question | Answer |
|----------|--------|
| UI toolkit | **Swing** (`javax.swing.*`, `java.awt.*`) |
| Main entry point | [src/main/java/com/tetris/Main.java](src/main/java/com/tetris/Main.java) |
| Launcher | `com.tetris.view.StartMenu` (CardLayout host) |
| Main view/controller | `com.tetris.controller.GameController`, `com.tetris.view.MainFrame`, `com.tetris.view.GameView` |
| Render loop | `javax.swing.Timer` at ~60 fps inside both `GameController` and `MainFrame` |
| Existing side panel | `com.tetris.view.SidePanel` (already wide; modifying it for MAB risks layout regressions) |
| Two-board rendering today | **Not** practical — the existing window is built around a single visible `GameState` |
| Chosen integration | A separate, non-modal `JFrame` (`MabDebugFrame`) opened from `GameController.start()` / `startEmbedded()` |

The separate-window approach was chosen so the existing fullscreen game
window is not disturbed and the HUD can be closed/disabled without
risking regressions.

## 4. Why the running game previously looked unchanged

Steps 1–11 added the entire MAB strategy backend (DEFCON, nuke designs,
launches, threats, impacts, civil defence, upgrades, radar, decoys) but
deliberately did not wire any of it into the existing Tetris renderer or
controllers. That kept regular single-player Tetris stable while the
match coordinator grew. The debug HUD added in this step is the first
visible surface for any of that state — the normal in-game playfield
still looks the same.

## 5. Match instance creation

In `GameController.openMabDebugHud()`:

* Player A uses the **same `GameState`** that the visible game already
  owns. Every piece lock, line clear, top-out, and pause/resume on the
  visible board now also flows into the MAB match through the existing
  Step-1 `GameEventListener` plumbing.
* Player B is a fresh, **hidden, model-only** `GameState(startLevel)`.
  It has no view, never receives input, and is never ticked by the game
  loop. Its only purpose is to give the MAB match a valid second
  participant so the debug HUD can issue cross-player actions (radar
  scans, decoys, manual launches against a live opponent).
* The match is created with `MutuallyAssuredBlocksMatch.createLocalPvp(...,
  MatchDifficulty.NORMAL)` and immediately moved to `ACTIVE` via
  `startMatch()`.

The HUD opens once per game session. Closing it stops its refresh timer
and detaches the listeners on Player B's hidden `GameState`. The HUD
window itself is closed by `stop()` (called when the main game window
closes) so nothing leaks across sessions.

You can disable the HUD entirely by passing `-Dmab.debug.hud=false` at
launch.

## 6. What the debug HUD shows

Every refresh (default 400 ms) the HUD pulls a fresh
`MatchDebugSnapshot` and the 20 most-recent `MatchEventLogEntry` rows,
then formats them with `MabDebugFormatter`. Visible fields:

* match phase, paused flag, winner;
* DEFCON level + escalation meter, active timer count, total logged events;
* per-participant: pieces locked, lines cleared, garbage received,
  topped-out flag;
* current nuke design (id, doctrine, size), build charge / required,
  armed flag;
* silo damage state + integrity + silo upgrade levels;
* active launches, impact-ready launch count, incoming threats,
  impact-ready threats, pending impact-wave count;
* first active launch + phase; first incoming threat + warning pieces;
* civil defence charges, shield pieces remaining, emergency-protocol
  flag, total activations, civ-def upgrade levels;
* upgrade points (current + lifetime), upgrade count, recent upgrades;
* radar totals (scans / ok / fail), best intel rank, last intel level +
  confidence + stale flag;
* active decoy count, false-launch / false-threat signature counts,
  active confidence penalty, masked-launch decoy count, first decoy id
  + type;
* in-progress action-code attempt (id, progress, expected next clear).

The bottom panel always lists every recent event row, oldest first,
truncated to 200 chars per row to keep the panel scannable.

## 7. Manual debug controls

Buttons (in `MabDebugFrame`):

| Button | Calls | Effect |
|--------|-------|--------|
| Add Charge A / B | `debugAddNukeCharge(..., 25)` | adds 25 nuke charge |
| Arm A / B | `debugArmCurrentNuke(...)` | tops off to armed |
| Launch A / B | `debugStartLaunch(...)` | auto-arms + authorizes a launch |
| Radar A / B | `debugRadarScan(...)` | runs a `DEBUG`-level radar scan |
| CivDef A / B | `debugActivateCivilDefense(...)` | manual civil-defence activation |
| Decoy A / B | `debugActivateDecoy(..., DECOY_LAUNCH)` | activates a `DECOY_LAUNCH` decoy |
| Resolve Impacts | `debugResolveAllImpacts()` | applies every `IMPACT_READY` launch/threat |
| Open Upgrade Pause | `enterUpgradePause("debug-hud")` | enters upgrade pause |
| Close Upgrade Pause | `exitUpgradePause("debug-hud")` | leaves upgrade pause |
| Refresh now | manual snapshot refresh | (read-only) |

Keyboard shortcuts (window-scoped, **only fire while the debug window
has focus**, so they cannot conflict with in-game keys):

| Key | Action |
|-----|--------|
| `F5` | manual refresh |
| `F6` | Add Charge to Player A |
| `F7` | Arm Player A nuke |
| `F8` | Debug-launch Player A |
| `F9` | Radar scan from Player A |
| `F10` | Resolve all impacts |
| `Esc` | Hide the HUD (game keeps running) |

All debug methods log under the `DEBUG_*` event-type prefix
(`DEBUG_NUKE_CHARGE_ADDED`, `DEBUG_NUKE_ARMED`, `DEBUG_LAUNCH_STARTED`,
`DEBUG_IMPACTS_RESOLVED`, `DEBUG_RADAR_SCAN`,
`DEBUG_CIVIL_DEFENSE_ACTIVATED`, `DEBUG_DECOY_ACTIVATED`, plus the
explicit `*_REJECTED` variants when guards fail) so they are easy to
identify in the event log and won't be confused with action-code traffic.

## 8. New public API on `MutuallyAssuredBlocksMatch`

```java
List<MatchEventLogEntry> getRecentEvents(int maxEntries);

boolean debugAddNukeCharge(ParticipantId id, int amount);
boolean debugArmCurrentNuke(ParticipantId id);
boolean debugStartLaunch(ParticipantId id);
List<ImpactResult> debugResolveAllImpacts();
RadarScanResult debugRadarScan(ParticipantId scannerId);
boolean debugActivateCivilDefense(ParticipantId id);
DecoyResolutionResult debugActivateDecoy(ParticipantId id, DecoyType type);
```

`debugAddNukeCharge` and `debugArmCurrentNuke` allow `ACTIVE` and
`UPGRADE_PAUSE`. `debugStartLaunch` requires `ACTIVE`, not paused, no
winner — same guard as the action-code launch path. All emit
`DEBUG_*` log entries.

`authorizeLaunchInternal(attacker, actionId, source)` is a private
helper that both `authorizeLaunchFromAction(...)` and
`debugStartLaunch(...)` call so the launch-construction logic exists in
exactly one place.

## 9. `debugAdvanceOwnPieceClock` — intentionally not implemented

The spec said this was optional and to skip it if risky. Strategic
piece-clock advancement is currently driven by the engine's
`pieceLocked` event, which carries scoring data and goes through Step
1's `GameEventListener` plumbing. Synthesising that event from the HUD
would require either fabricating a `PieceLockEvent` or reaching into
`PieceTimerManager` to tick timers without the upstream side effects
(active actions, civil-defence shield decay, decoy expiration ticking,
radar staleness, etc.). That risks subtly desynchronising the pieces
counter from real engine state, so the method was skipped for this
slice. The "Resolve Impacts" button covers the practical use case: it
turns any `IMPACT_READY` flight into damage immediately without faking
clock advancement. Real timer advancement happens naturally as the
player locks pieces in the visible game.

## 10. Manual test script

1. Build the project (see § 14) and launch `com.tetris.Main` (or use the
   normal `run.bat`/`run.sh` entry).
2. Pick "Play" from the start menu — the regular Tetris board appears as
   before.
3. The "MAB Debug / Vertical Slice — NOT final UI" window appears
   alongside the game window. (If it doesn't, ensure
   `-Dmab.debug.hud=false` is *not* set.)
4. Click **Add Charge A** several times and watch Player A's
   `charge=…/…` field climb in the snapshot panel.
5. Click **Arm A** — the snapshot now shows `ARMED` for Player A and
   `DEBUG_NUKE_ARMED` appears in the event log.
6. Click **Launch A** — Player A's `launches=` increments, a
   `LAUNCH_AUTHORIZED` and `LAUNCH_COUNTDOWN_STARTED` row appears, and
   Player B's `threats=` will increment after the engine ticks.
7. Lock pieces in the visible game until the launch countdown / warning
   timers expire; the snapshot will show `impactReady=` rising and the
   event log will show `LAUNCH_IMPACT_READY` / `THREAT_IMPACT_READY`.
8. Click **Radar A** — `RADAR_SCAN_STARTED`, `RADAR_SCAN_COMPLETED`, and
   (if applicable) `RADAR_DECOY_EFFECT_APPLIED` appear in the event log,
   and Player A's `radar scans=` totals increment.
9. Click **Decoy B** — Player B activates a `DECOY_LAUNCH`; subsequent
   radar scans against Player B see false signatures until pierced or
   expired.
10. Click **CivDef A** to activate civil defence; the snapshot's
    `civDef charges=` line updates.
11. Click **Resolve Impacts** to drain any `IMPACT_READY` flights;
    `IMPACT_RESOLVED` and (if any) `GARBAGE_INSERTED` rows appear.
12. Press `Esc` in the debug window to hide it; the game continues
    running normally. Closing the main game window calls
    `GameController.stop()`, which disposes the HUD.

## 11. Offline-only confirmation

* No `java.net`, `java.nio.channels`, `Socket`, `ServerSocket`,
  HTTP/REST/JSON-RPC client, or serialisation transport is added in this
  step.
* `MabDebugFrame` is a local Swing window that polls the in-process
  match instance — it does not communicate with anything outside the
  JVM.
* The MAB match still uses the same in-process listener wiring added in
  Step 2.

The project remains an offline single-process Tetris game.

## 12. NOT final UI

The window title is `"MAB Debug / Vertical Slice — NOT final UI"` and
the panel banner reads `"MAB Debug / Vertical Slice — these controls
are debug-only and not final game UX."` This is intentional: the goal
of Step 12 is to make the backend visibly testable, not to ship player-
facing UX.

## 13. Intentionally not implemented (this step)

* Final two-board competitive layout — the visible game still renders
  exactly one board.
* Final polished MAB HUD inside the main window (the SidePanel is left
  alone; integration would risk regressions).
* PvE AI driver — `MatchMode.PVE` is selectable but no AI ticks
  Player B in this step.
* Campaign / missions / progression metagame.
* `debugAdvanceOwnPieceClock(...)` — see § 9.
* Sample-upgrade buttons — without an established way to award debug
  upgrade points safely, the HUD exposes only the upgrade-pause
  enter/exit controls; upgrade application can still be exercised via
  the existing `applyUpgrade(...)` API from tests.
* Networking, online multiplayer, matchmaking, sockets, rollback —
  permanently out of scope.

## 14. Build verification

```powershell
$env:Path = "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin;$env:Path"
cd 'c:\Users\leo.li2026\1\Desktop\CompSciSummative'
if (Test-Path target\classes) { Remove-Item -Recurse -Force target\classes }
New-Item -ItemType Directory -Force target\classes | Out-Null
$srcs = Get-ChildItem -Recurse -Filter *.java src\main\java | ForEach-Object { $_.FullName }
$srcs | Set-Content -Encoding ASCII $env:TEMP\tetris_sources.txt
javac -d target\classes -encoding UTF-8 "@$env:TEMP\tetris_sources.txt"
Write-Host "EXITCODE=$LASTEXITCODE"
```

Result:

```
EXITCODE=0
```

## 15. Acceptance-criteria verification

* ✅ Project compiles with `EXITCODE=0`.
* ✅ Existing game still starts (the StartMenu / MainFrame / GameView
  paths are unchanged; the HUD is opened in addition to, not instead of,
  the game window).
* ✅ Existing single-player controls still work — `MabDebugFrame` is a
  separate `JFrame` and its key bindings are scoped
  `WHEN_IN_FOCUSED_WINDOW` so they only fire while the debug window has
  focus.
* ✅ A visible MAB debug HUD/window exists (`MabDebugFrame`).
* ✅ The HUD shows live match state via `MatchDebugSnapshot` formatted
  through `MabDebugFormatter`.
* ✅ The HUD shows recent strategic event logs
  (`getRecentEvents(20)`).
* ✅ The HUD exposes manual controls for charge, arm, launch, radar,
  civil defence, decoys, impact resolution, and upgrade pause.
* ✅ The HUD allows visibly observing nuke charge changing, launch
  creation, incoming threats, radar results/events, decoy counts/events,
  impact-ready and resolved impact state, and civil-defence state.
* ✅ Implementation is offline-only — no networking added.
* ✅ `Step12.md` documents the integration and how to test it.
