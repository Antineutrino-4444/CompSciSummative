# Step 15 — Mutually Assured Blocks: Playable Offline PvE Mode, Player-Facing Strategic HUD, and First Non-Debug Game Loop

> **Permanent scope rule:** Mutually Assured Blocks remains **offline-only**.
> No networking, no sockets, no matchmaking, no rollback, no online
> synchronisation, no client/server abstractions, and no future-online
> API reservations. The PvE mode introduced here runs entirely
> in-process on the Swing event-dispatch thread.

## 1. Files added / modified

Added:

- [src/main/java/com/tetris/controller/GameLaunchMode.java](src/main/java/com/tetris/controller/GameLaunchMode.java)
- [src/main/java/com/tetris/mab/ui/MabHudFormatter.java](src/main/java/com/tetris/mab/ui/MabHudFormatter.java)
- [src/main/java/com/tetris/mab/ui/MabHudPanel.java](src/main/java/com/tetris/mab/ui/MabHudPanel.java)
- [src/main/java/com/tetris/mab/ui/MabPlayerFacingController.java](src/main/java/com/tetris/mab/ui/MabPlayerFacingController.java)

Modified:

- [src/main/java/com/tetris/Main.java](src/main/java/com/tetris/Main.java) — uses the new `BiFunction`-based StartMenu constructor.
- [src/main/java/com/tetris/view/StartMenu.java](src/main/java/com/tetris/view/StartMenu.java) — adds the *MUTUALLY ASSURED BLOCKS — PvE* button and a moded controller factory.
- [src/main/java/com/tetris/controller/GameController.java](src/main/java/com/tetris/controller/GameController.java) — adds a `GameLaunchMode` constructor parameter, adds `MabPlayerFacingController` lifecycle, and switches the legacy debug-HUD default from on to opt-in.

## 2. Offline-only confirmation

No network classes are used or imported. `MabPlayerFacingController`,
`MabHudPanel`, and `MabHudFormatter` operate purely on
`MutuallyAssuredBlocksMatch.toDebugSnapshot()` and
`MutuallyAssuredBlocksMatch.getRecentEvents(int)`. The hidden Player B
is a local in-process `GameState`. No threads are introduced; both
the HUD refresh (400 ms) and AI tick (500 ms) use Swing `Timer`s.

## 3. Difference between launch paths

| Aspect | NORMAL_TETRIS | MAB_PVE |
| --- | --- | --- |
| MAB match created? | Only if `-Dmab.debug.hud=true` | Always |
| AI driver enabled? | No | Yes (Player B, BALANCED, NORMAL) |
| Player-facing HUD? | No | Yes (companion JFrame) |
| Debug HUD? | Opt-in via `-Dmab.debug.hud=true` | Opt-in via `-Dmab.debug.hud=true` |
| Tetris controls / game loop | Unchanged | Unchanged |

The *PLAY TETRIS* button still launches `NORMAL_TETRIS`; nothing about
the existing single-player experience changes.

## 4. How to start MAB PvE

From the launcher window (StartMenu): click
**☢ MUTUALLY ASSURED BLOCKS — PvE**. The game card appears as
normal, and a separate companion JFrame titled
*"Mutually Assured Blocks — PvE HUD"* opens to the right of the screen.

From the command line, the same flow is reached by running
`java -cp target\classes com.tetris.Main` and clicking the new
button. There is no separate launcher class — adding one was
unnecessary because StartMenu accepted the new mode cleanly.

## 5. Visible Player A / hidden AI Player B

`GameController.openMabIntegrations()` builds the match exactly the
same way Step 12's debug HUD did:

```java
mabPlayerBState = new GameState(startLevel);
mabMatch = MutuallyAssuredBlocksMatch.createLocalPvp(
        gameState, mabPlayerBState, MatchDifficulty.NORMAL);
mabMatch.startMatch();
```

The visible `gameState` is Player A. `mabPlayerBState` is a hidden
model-only board; Player B's playfield is never rendered. The AI
driver uses `MabAiArchetype.BALANCED` and `MabAiDifficulty.NORMAL`
and is enabled by default in MAB_PVE mode.

## 6. How the player-facing HUD is displayed

A **companion `JFrame`** titled *"Mutually Assured Blocks — PvE HUD"*.
Embedding the HUD inside `MainFrame`/`SidePanel` was rejected because
the existing window already uses a tightly-spaced `BorderLayout` plus
a vertical `SidePanel`, and editing it in place risked breaking the
existing single-player layout. The companion frame is positioned to
the right edge of the primary screen and has
`HIDE_ON_CLOSE` semantics; closing it does not stop the game. The
`MabPlayerFacingController.shutdown()` call disposes it cleanly when
the controller stops.

## 7. What the HUD shows

Sections (top to bottom):

- **Header** — DEFCON level, escalation meter, current `MatchPhase`,
  paused / winner indicator.
- **Phase banner** — highlights `UPGRADE_PAUSE` and `paused` states
  in orange.
- **Your arsenal** — current nuke design (display name, doctrine,
  size), current/required charge, armed flag, silo integrity and
  damage state, civil-defence charges/shield/emergency flag, upgrade
  points and lifetime earned, active launch count and first-launch
  phase, active decoy count and first decoy type.
- **Incoming threats** — incoming count, impact-ready count, pending
  impact-wave count, first-threat warning pieces remaining, currently
  selected intercept target, and intercept totals (interceptable,
  fully intercepted, partially mitigated).
- **Opponent intel** — last intel level / confidence / staleness,
  scan totals, last scan summary, best intel rank achieved.
- **Action code** — active attempt name, progress index / required
  length, expected next clear, pending-confirmation banner, lifetime
  completed-attempt count.
- **Recent events** — most recent player-facing event log entries
  (filtered allowlist, see Part 7 of the spec).
- **Help footer** — Tetris controls + the Step 4/8/10 line-clear
  triplets for Radar Scan, Civil Defense, Emergency Intercept, and
  the launch-code reminder.

The HUD never dumps raw metadata maps and never shows internal IDs
unless they are short and meaningful.

## 8. AI lifecycle in MAB PvE

- `MabPlayerFacingController` constructs a `MabAiDriver(match,
  PLAYER_B, BALANCED, NORMAL)` in its constructor.
- `start()` calls `setEnabled(true)` and starts a Swing `Timer` at
  500 ms intervals that calls `aiDriver.tick()`. No background
  threads.
- `shutdown()` stops the timer and calls `setEnabled(false)`,
  emitting an `AI_DISABLED` event.
- The HUD has its own 400 ms refresh timer; it is also a Swing
  `Timer` and is stopped in `shutdown()`.

`GameController.stop()` always calls `closeMabIntegrations()` which
disposes the player-facing controller, debug frame (if present), and
the match.

## 9. Debug HUD opt-in

Behaviour change: previously the debug HUD opened automatically
unless `-Dmab.debug.hud=false`. As of Step 15 the default is **off**.
- `-Dmab.debug.hud=true` opens the Step 12 debug HUD in any launch
  mode (still mutates the same shared match instance).
- Without the property, NORMAL_TETRIS opens nothing MAB-related and
  MAB_PVE opens only the player-facing HUD.

The debug HUD is **not** removed. The simulation harness
(`MabSimulationRunner`) is unaffected.

## 10. Action-code visibility

`MabHudFormatter.formatActionCodeStatus(...)` reads the existing
`MatchDebugSnapshot.ParticipantSummary` action fields:
`activeActionId`, `activeActionName`, `activeActionProgress`,
`activeActionRequiredLength`, `activeActionExpectedNextClear`,
`pendingConfirmationActionId`, `pendingConfirmationActionName`,
`completedActionCount`. No snapshot extension was needed.

The HUD shows the active attempt name, a `progress / required`
counter, the next expected line-clear count, a pending-confirmation
banner ("HARD-DROP to commit"), and the lifetime completed count.

## 11. Upgrade pause visibility

When `MatchPhase` is `UPGRADE_PAUSE`, the HUD's phase banner displays
`UPGRADE PAUSE — selection UI not implemented yet`, and the *Your
arsenal* section continues to show upgrade points. A full upgrade
selection UI is intentionally out of scope; the developer debug HUD
and `applyUpgrade(...)` API still work for now.

## 12. What remains debug-only

- All `match.debug*` mutators (`debugAddNukeCharge`,
  `debugStartLaunch`, `debugActivateDecoy`,
  `debugResolveAllImpacts`, `debugAddUpgradePoints`, etc.) are
  **never** called from `MabHudPanel` or
  `MabPlayerFacingController`. They remain reserved for the Step 12
  debug HUD and the Step 14 simulation harness.
- The Step 14 simulation harness CLI / HUD button still exists and
  is unchanged.
- Direct upgrade selection still goes through the debug HUD or the
  programmatic `applyUpgrade(...)` API.

## 13. Manual test script

```text
1. Build:
     javac -d target\classes ...   (see Part 16 for full command)
2. Launch:
     java -cp target\classes com.tetris.Main
3. In the StartMenu, click "MUTUALLY ASSURED BLOCKS — PvE".
4. Verify a companion window titled
     "Mutually Assured Blocks — PvE HUD"
   appears with the section list above and a header reading
   "DEFCON 5 ESC=0 phase=ACTIVE".
5. Play normally: clear lines, watch the *Your arsenal* "Charge"
   value increase, and the *Recent events* feed populate with
   LINES_CLEARED / NUKE_CHARGE_ADDED / ACTION_PROGRESS rows.
6. Wait ~10–20 seconds and observe AI events appear — by default
   the BALANCED AI for Player B will execute decisions and over
   time will issue LAUNCH_AUTHORIZED, THREAT_WARNING_STARTED, and
   IMPACT_RESOLVED rows in the feed.
7. Optionally enter the Radar Scan triplet (2,1,2) and confirm a
   RADAR_SCAN_COMPLETED row appears and the *Opponent intel*
   section updates.
8. Close the game card (Back) and the companion HUD window — both
   should dispose cleanly with no leftover threads.
9. Re-run Play (NORMAL_TETRIS) to confirm the companion HUD does
   NOT open and normal Tetris is unaffected.
```

## 14. Intentionally not implemented

- Final two-board local 1v1 layout (still single visible board).
- Online multiplayer / networking / matchmaking — permanently out of
  scope.
- Final art, animations, sound design.
- Campaign, missions, tutorial system.
- A full PvE difficulty/archetype menu (BALANCED / NORMAL only).
- Final balance tuning (timer durations, charge thresholds, etc.).
- Upgrade selection UI, nuke builder UI redesign.
- Embedding the HUD inside `MainFrame` (companion window chosen for
  layout safety; Step 16 may revisit).
- Translating the help text / formatter strings.

## 15. Acceptance-criteria verification

| Criterion | Result |
| --- | --- |
| Project compiles with EXITCODE=0 | ✅ EXITCODE=0 (clean rebuild) |
| Smoke simulation returns success=true | ✅ `success=true` |
| Existing normal Play unchanged | ✅ NORMAL_TETRIS path unaltered; debug HUD now opt-in |
| MAB PvE startable from StartMenu | ✅ "MUTUALLY ASSURED BLOCKS — PvE" button |
| MAB PvE creates visible Player A + hidden AI Player B | ✅ via `createLocalPvp(gameState, mabPlayerBState, NORMAL)` |
| Player-facing MAB HUD appears | ✅ companion JFrame |
| Player-facing HUD is display-only | ✅ no `debug*` calls in the `ui` package |
| Debug HUD remains available but optional | ✅ opt-in via `-Dmab.debug.hud=true` |
| Player B AI starts enabled in MAB PvE | ✅ `MabPlayerFacingController.start()` sets enabled=true |
| HUD updates while playing | ✅ 400 ms Swing `Timer` |
| HUD shows DEFCON, charge, armed, threats, launches, civil defence, radar/intel, decoys, upgrades, events | ✅ all sections covered by `MabHudFormatter` |
| Existing single-player controls still work | ✅ `gameState`/`InputHandler` unchanged |
| No networking added | ✅ confirmed |
| Step15.md present | ✅ this file |

## 16. Build commands and final result

```powershell
$env:Path = "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin;$env:Path"
cd 'c:\Users\leo.li2026\1\Desktop\CompSciSummative'
if (Test-Path target\classes) { Remove-Item -Recurse -Force target\classes }
New-Item -ItemType Directory -Force target\classes | Out-Null
$srcs = Get-ChildItem -Recurse -Filter *.java src\main\java | ForEach-Object { $_.FullName }
$srcs | Set-Content -Encoding ASCII $env:TEMP\tetris_sources.txt
javac -d target\classes -encoding UTF-8 "@$env:TEMP\tetris_sources.txt"
Write-Host "EXITCODE=$LASTEXITCODE"

java -cp target\classes com.tetris.mab.sim.MabSimulationRunner smoke
```

Result:

```
EXITCODE=0
=== MAB Simulation: SMOKE ===
success=true
summary: mode=SMOKE ok=true ticks=80 events=271 launches=1 impacts=1 scans=1 decoys=1 civDef=1 upgrades=0 aiExec=0 aiSkip=0 garbage=2 invariantFailures=0
```
