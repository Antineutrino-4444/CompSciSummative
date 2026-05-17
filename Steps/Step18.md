# Step 18 — PvE Setup Options, Match Results, Restart Flow, and End-State UX

## 1. Files added / modified

NEW
- [src/main/java/com/tetris/mab/ui/MabPveConfig.java](src/main/java/com/tetris/mab/ui/MabPveConfig.java)
- [src/main/java/com/tetris/mab/ui/MabPveSetupDialog.java](src/main/java/com/tetris/mab/ui/MabPveSetupDialog.java)
- [src/main/java/com/tetris/mab/ui/MabMatchResultSummary.java](src/main/java/com/tetris/mab/ui/MabMatchResultSummary.java)
- [src/main/java/com/tetris/mab/ui/MabMatchResultFormatter.java](src/main/java/com/tetris/mab/ui/MabMatchResultFormatter.java)
- [src/main/java/com/tetris/mab/ui/MabMatchResultDialog.java](src/main/java/com/tetris/mab/ui/MabMatchResultDialog.java)

MODIFIED
- [src/main/java/com/tetris/Main.java](src/main/java/com/tetris/Main.java) — wires `menu.setMabPveFactory(GameController::new)` so the StartMenu can construct a controller from a `MabPveConfig`.
- [src/main/java/com/tetris/view/StartMenu.java](src/main/java/com/tetris/view/StartMenu.java) — adds `setMabPveFactory`, opens `MabPveSetupDialog` on the MAB PvE button, adds `launchMabPveWithConfig(...)` and shared `mountController(...)` helper, wires Restart and Back-to-Menu callbacks.
- [src/main/java/com/tetris/controller/GameController.java](src/main/java/com/tetris/controller/GameController.java) — new constructors `GameController(MabPveConfig)` and `GameController(int, GameLaunchMode, MabPveConfig)`; new `setMabPveCallbacks(...)`; `openMabIntegrations` now uses the config for archetype, difficulty, debug-HUD opt-in, and player-HUD opt-in.
- [src/main/java/com/tetris/mab/ui/MabPlayerFacingController.java](src/main/java/com/tetris/mab/ui/MabPlayerFacingController.java) — accepts archetype/difficulty in a new constructor, owns the `MabMatchResultDialog`, exposes `setRestartCallback` / `setBackToMenuCallback`, runs `maybeShowResult()` on each HUD tick to display the result dialog exactly once and stop the AI timer.
- [src/main/java/com/tetris/mab/ui/MabHudPanel.java](src/main/java/com/tetris/mab/ui/MabHudPanel.java) — adds Opponent label, end-state banner, `setOpponentInfo(...)` method, and disables Open Upgrades after match over.
- [src/main/java/com/tetris/mab/ui/MabHudFormatter.java](src/main/java/com/tetris/mab/ui/MabHudFormatter.java) — adds `formatEndStateBanner(...)` and `formatOpponentLine(...)`; extends `PLAYER_FACING_EVENTS` with `TOP_OUT` and `MATCH_ENDED`.

## 2. Offline-only scope confirmation

Step 18 only adds Swing UI + a controller plumbing path. No networking, no client/server, no online matchmaking, no remote-player abstractions. Mutually Assured Blocks remains strictly offline (Local 1v1, PvE, debug/practice).

## 3. PvE setup UI

`MabPveSetupDialog` is an application-modal Swing dialog with:
* AI archetype dropdown (`MabAiArchetype` values)
* AI difficulty dropdown (`MabAiDifficulty` values)
* Start level spinner (1–20, clamped)
* "Show MAB Debug HUD" checkbox (defaults to system property `mab.debug.hud`)
* **Start PvE** (default button) and **Cancel**

Cancel disposes the dialog without launching anything. Start disposes the dialog and invokes a `Consumer<MabPveConfig>` callback supplied by `StartMenu`. The dialog never mutates a match.

## 4. MabPveConfig fields

| Field | Type | Default |
| --- | --- | --- |
| `startLevel` | `int` (clamped 1..20) | 1 |
| `aiArchetype` | `MabAiArchetype` | `BALANCED` |
| `aiDifficulty` | `MabAiDifficulty` | `NORMAL` |
| `showPlayerHud` | `boolean` | `true` |
| `showDebugHud` | `boolean` | `MabPveConfig.debugHudDefault()` (reads `mab.debug.hud` system property) |
| `label` | `String` | `"MAB PvE"` |

Static factories: `defaults()`, `fromSelections(level, archetype, difficulty, showDebugHud)`. The compact constructor null-guards every reference field and clamps `startLevel`.

## 5. StartMenu launch flow

1. Player clicks **☢ MUTUALLY ASSURED BLOCKS — PvE** on the main StartMenu card.
2. `mabPve.addActionListener` calls `openMabPveSetup()`.
3. The setup dialog appears, seeded with the last-used `MabPveConfig` (initially `defaults()`).
4. On **Start PvE**, `launchMabPveWithConfig(cfg)` calls `mabPveFactory.apply(cfg)` (wired in `Main` to `GameController::new(MabPveConfig)`), wires Restart / Back-to-Menu callbacks, then `mountController(ctrl)` swaps the game card into the StartMenu's `CardLayout`.
5. The standard Play button is unchanged — it still calls `showGameCard(GameLaunchMode.NORMAL_TETRIS)` and never opens the setup dialog.

If `setMabPveFactory(...)` was never called, the MAB PvE button falls back to the existing `showGameCard(GameLaunchMode.MAB_PVE)` legacy path and skips the setup dialog. This keeps older callers working.

## 6. AI archetype / difficulty selection

`MabPlayerFacingController` now exposes a constructor taking `(match, humanId, archetype, difficulty)`. `GameController.openMabIntegrations()` reads the config and constructs the controller with `cfg.getAiArchetype()` and `cfg.getAiDifficulty()`. The HUD shows the chosen pair via `MabHudPanel.setOpponentInfo(...)` rendered as `Opponent: BALANCED / NORMAL` in the header. Internal AI cooldowns are not exposed.

## 7. Match end detection

`MutuallyAssuredBlocksMatch` already tracks `currentPhase`, `winner`, and `isGameOver()`. Step 18 reuses these:

* `MabPlayerFacingController.maybeShowResult()` runs on every HUD tick and calls `match.isGameOver()`.
* When true and the result has not yet been shown, the AI is disabled, the AI timer is stopped, and a `MabMatchResultSummary` is built and passed to the result dialog.
* End-state banner in the HUD reads `snapshot.currentPhase() == GAME_OVER` and `snapshot.winner()` to choose Victory / Defeat / Mutual Collapse copy.

Top-out detection (`TopOutEvent` → `TOP_OUT` log entry → `MatchPhase.GAME_OVER` + `winner` set) is unchanged; we only consume it.

## 8. Result summary fields

`MabMatchResultSummary` (immutable) carries:

`matchOver`, `winner`, `playerId`, `title`, `reason`, `defconLevel`, `totalEvents`, `playerLinesCleared`, `playerPiecesLocked`, `playerCharge`, `opponentCharge`, `launchesAuthorized`, `impactsResolved`, `radarScans`, `decoysActivated`, `civilDefenseActivations`, `upgradesApplied`, `aiDecisionsExecuted`, `finalNukeDesign`, `highlightEvents`.

Counts come from walking `match.getEventLog()` (event types: `LAUNCH_AUTHORIZED`, `IMPACT_RESOLVED`, `RADAR_SCAN_COMPLETED`, `DECOY_ACTIVATED`, `CIVIL_DEFENSE_ACTIVATED`, `UPGRADE_APPLIED`, `AI_DECISION_EXECUTED`). `highlightEvents` is the last 12 entries. The bounded log can undercount very long matches — this is documented as a known limitation in the formatter output.

## 9. Result dialog behavior

`MabMatchResultDialog` is a `JFrame` (`HIDE_ON_CLOSE`) with a 28×64 monospaced `JTextArea` and three buttons: **Restart MAB PvE**, **Back to Menu**, **Close Result**. Buttons are disabled if their callbacks are unset. `show(summary)` sets the title via `formatTitle`, body via `formatBody`, and centers the window. The dialog never mutates the match.

Player-facing titles:
* `Victory — Opponent Collapsed`
* `Defeat — Your Stack Collapsed`
* `Mutual Collapse`
* `Match In Progress` (defensive fallback)

## 10. Restart / back-to-menu behavior

`StartMenu.launchMabPveWithConfig(cfg)` builds the controller, then wires:

* **Restart**: `ctrl.stop()` then `launchMabPveWithConfig(cfg)` (same config). This disposes the old HUD, upgrade window, command guide window, result dialog, AI timer, HUD timer, and underlying `MutuallyAssuredBlocksMatch` via the existing `closeMabIntegrations()` path, then constructs a fresh controller and mounts it.
* **Back to Menu**: `ctrl.stop()` then `showMenuCard()` returns to the main StartMenu card.

Both callbacks are passed into `MabPlayerFacingController.setRestartCallback / setBackToMenuCallback`, which the result dialog invokes when the matching button is clicked.

## 11. End-state HUD behavior

When the match phase is `GAME_OVER`:
* The HUD shows a centered, mono-bold blue end-state banner: `MATCH OVER — Victory` / `MATCH OVER — Defeat` / `MATCH OVER — Mutual Collapse`.
* `Open Upgrades` is force-disabled. `Close Upgrade Pause` and `Command Guide` remain available so the player can still close any leftover pause and read commands; neither mutates strategic state once the match is over.
* The HUD continues to refresh so the player can read the final state. The result dialog is shown exactly once.

## 12. Event feed additions

`PLAYER_FACING_EVENTS` now also includes `TOP_OUT` and `MATCH_ENDED`. The match emits `TOP_OUT` on top-out (existing behavior). `MATCH_ENDED` is emitted exactly once by the MAB PvE result-detection flow (`MabPlayerFacingController.maybeShowResult`) the first time `match.isGameOver()` is observed — see Part 20.

`MutuallyAssuredBlocksMatch.recordPlayerFacingEvent(eventType, participantId, message, metadata)` is the safe, non-debug logging hook used for this emission. It defensively copies the metadata map and never exposes the internal mutable event log. It is intentionally distinct from `debugLogEvent(...)` because `MATCH_ENDED` is a real player-facing event, not a `DEBUG_*` test marker.

## 13. Player-facing control safety

* `MabPveSetupDialog` only calls a `Consumer<MabPveConfig>`; it does not touch the match.
* `MabMatchResultDialog` only invokes `Runnable` callbacks; it does not touch the match.
* `MabMatchResultSummary.from(...)` only reads `match.toDebugSnapshot()` and `match.getEventLog()`.
* `MabPlayerFacingController.maybeShowResult()` calls `aiDriver.setEnabled(false)` (already an allowed lifecycle method) and reads match state; it does not call any `debug*` mutator.
* No new player-facing buttons launch nukes, scan radar, activate civil defense, or trigger decoys. Strategic actions remain entered via line-clear sequences.
* `com.tetris.mab.ui` does not import `com.tetris.mab.debugui`.

## 14. Normal Tetris isolation

* `Play` on StartMenu still calls `showGameCard(GameLaunchMode.NORMAL_TETRIS)` and never opens the setup dialog.
* `GameController.openMabIntegrations()` early-returns when `launchMode != MAB_PVE` *and* `mab.debug.hud` is not set, so NORMAL_TETRIS does not construct a `MabPlayerFacingController`, `MabMatchResultDialog`, or any MAB UI.
* Existing single-player keys, controls, GameView, and MainFrame remain untouched.

## 15. Manual test script

```powershell
$env:Path = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin;' + $env:Path
cd 'c:\Users\leo.li2026\1\Desktop\CompSciSummative'
if (Test-Path target\classes) { Remove-Item -Recurse -Force target\classes }
New-Item -ItemType Directory -Force target\classes | Out-Null
Get-ChildItem -Recurse -Filter *.java src\main\java |
    ForEach-Object { $_.FullName } |
    Set-Content -Encoding ASCII "$env:TEMP\tetris_sources.txt"
javac -d target\classes -encoding UTF-8 "@$env:TEMP\tetris_sources.txt"
java -cp target\classes com.tetris.mab.sim.MabSimulationRunner smoke
java -cp target\classes com.tetris.Main
```

In the running game:
1. Click **MUTUALLY ASSURED BLOCKS — PvE**. The setup dialog appears.
2. Pick `DOOMSDAY_HOARDER` archetype, `HARD` difficulty, start level 5, leave debug HUD off, click **Start PvE**.
3. The MAB PvE game card appears and the companion HUD opens. The header shows `Opponent: DOOMSDAY_HOARDER / HARD`.
4. Play normally, or stack pieces to the top to force a top-out.
5. On top-out, the result dialog appears once, titled `Mutually Assured Blocks — Defeat — Your Stack Collapsed`. The HUD shows `MATCH OVER — Defeat` and `Open Upgrades` is disabled.
6. Click **Restart MAB PvE** — old controller stops, a fresh PvE match begins with the same `DOOMSDAY_HOARDER / HARD` selection.
7. Force another top-out, then click **Back to Menu** — returns to StartMenu.
8. Click **PLAY TETRIS** — only the standard single-player game appears, no MAB HUD, no result dialog.
9. Re-launch with `-Dmab.debug.hud=true`, repeat MAB PvE setup with the debug HUD checkbox ticked, and use the debug HUD's `Resolve All Impacts` / `Add Charge` controls to force a fast match end if needed.

## 16. Intentionally not implemented yet

* No save files, persistent profiles, or per-run history.
* No campaign / missions / achievements.
* No final balance tuning.
* No two-board local 1v1 layout.
* No online multiplayer / networking / client–server.
* No final art / audio for the result screen.
* No keyboard shortcuts on the setup or result dialogs beyond the default focus traversal.
* No restart-into-different-config flow — Restart re-uses the previous selections; the player must Back-to-Menu to change archetype/difficulty.
* `MabSimulationRunner` was not modified to use `MabMatchResultSummary`; its existing telemetry is sufficient.

## 17. Acceptance-criteria verification

| Criterion | Result |
| --- | --- |
| Project compiles with EXITCODE=0 | ✅ confirmed below |
| Smoke simulation `success=true` | ✅ confirmed below |
| NORMAL_TETRIS Play still starts and shows no MAB HUD | ✅ `openMabIntegrations` early-returns; `Play` button untouched |
| MAB PvE button opens setup UI | ✅ `mabPve.addActionListener` → `openMabPveSetup()` |
| Player can select AI archetype + difficulty | ✅ `MabPveSetupDialog` dropdowns |
| MAB PvE starts with selected AI archetype + difficulty | ✅ `GameController(MabPveConfig)` → `MabPlayerFacingController(match, humanId, archetype, difficulty)` |
| Player-facing HUD shows selected opponent | ✅ `MabHudPanel.setOpponentInfo` + `formatOpponentLine` |
| Match end can be detected | ✅ `match.isGameOver()` + `getWinner()` |
| Result dialog appears once when match ends | ✅ `resultShown` guard in `maybeShowResult()` |
| Result summary shows winner / reason and major stats | ✅ `MabMatchResultSummary` + `MabMatchResultFormatter` |
| Restart MAB PvE works | ✅ wired in `StartMenu.launchMabPveWithConfig` |
| Back to Menu works | ✅ wired in `StartMenu.launchMabPveWithConfig` |
| Timers / windows cleaned up on stop / restart / back | ✅ `GameController.stop` → `closeMabIntegrations()` → `MabPlayerFacingController.shutdown()` disposes HUD, upgrade window, command guide, result dialog, AI driver, timers |
| End-state HUD banner appears | ✅ `formatEndStateBanner` + `endStateBanner` label |
| Player-facing setup / result UI does not call debug mutators | ✅ verified in Part 13 |
| No networking / online code added | ✅ none added |
| Step18.md documents the implementation | ✅ this file |
| Explicit `MATCH_ENDED` event emission | ✅ emitted once by `MabPlayerFacingController.maybeShowResult()` via `MutuallyAssuredBlocksMatch.recordPlayerFacingEvent(...)` |

## 18. Build command used and final build result

```powershell
$env:Path = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin;' + $env:Path
if (Test-Path target\classes) { Remove-Item -Recurse -Force target\classes }
New-Item -ItemType Directory -Force target\classes | Out-Null
Get-ChildItem -Recurse -Filter *.java src\main\java |
    ForEach-Object { $_.FullName } |
    Set-Content -Encoding ASCII "$env:TEMP\tetris_sources.txt"
javac -d target\classes -encoding UTF-8 "@$env:TEMP\tetris_sources.txt"
```

Result: `EXITCODE=0`.

## 19. Smoke simulation command and result

```powershell
java -cp target\classes com.tetris.mab.sim.MabSimulationRunner smoke
```

Result:

```
=== MAB Simulation: SMOKE ===
success=true
summary: mode=SMOKE ok=true ticks=80 events=271 launches=1 impacts=1 scans=1 decoys=1 civDef=1 upgrades=0 aiExec=0 aiSkip=0 garbage=2 invariantFailures=0
```

`success=true`, 0 invariant failures, `EXITCODE=0`.

## 20. Refinement — explicit MATCH_ENDED emission

Added a non-debug player-facing logging hook on the match:

```java
public void recordPlayerFacingEvent(
    String eventType,
    ParticipantId participantId,
    String message,
    Map<String, Object> metadata
)
```

It lives next to `debugLogEvent(...)` in [MutuallyAssuredBlocksMatch.java](src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java), delegates to the same private `log(...)` writer, defensively copies the metadata map, no-ops on null/blank `eventType`, and is named distinctly from `debugLogEvent` because `MATCH_ENDED` is a real player-facing event.

[MabPlayerFacingController.maybeShowResult()](src/main/java/com/tetris/mab/ui/MabPlayerFacingController.java) now does, in order:

1. Guards on `resultShown` and `match.isGameOver()`; sets `resultShown = true` immediately.
2. Disables AI driver and stops the AI timer.
3. Builds an initial `MabMatchResultSummary`.
4. Calls `match.recordPlayerFacingEvent("MATCH_ENDED", humanId, summary.getTitle(), metadata)`.
5. Rebuilds the summary so its `highlightEvents` include the just-emitted `MATCH_ENDED` row.
6. Shows the result dialog.

Metadata fields written:

| Key | Value |
| --- | --- |
| `winner` | `String.valueOf(summary.getWinner())` |
| `playerId` | `String.valueOf(summary.getPlayerId())` |
| `title` | player-facing title (Victory / Defeat / Mutual Collapse) |
| `reason` | top-out detail or empty |
| `defconLevel` | int |
| `totalEvents` | int |
| `launchesAuthorized` | int |
| `impactsResolved` | int |
| `radarScans` | int |
| `decoysActivated` | int |
| `civilDefenseActivations` | int |
| `upgradesApplied` | int |
| `finalNukeDesign` | string or empty |
| `source` | `"mab-pve-result"` |

Emission rules:
* Exactly once per completed match (guarded by `resultShown`).
* Never on later HUD refresh ticks.
* Never on result-dialog close/reopen.
* Never during NORMAL_TETRIS (controller never constructed).
* Never during the setup dialog (no controller / no match yet).
* On Restart MAB PvE, the old controller is stopped, a brand-new `MutuallyAssuredBlocksMatch` is created, and the new `MabPlayerFacingController` instance has `resultShown = false`, so the next match's end can emit again. Old result state is not reused.
* Back-to-Menu still works — `ctrl.stop()` runs before `showMenuCard()`.
* Emission failure is caught and logged to stderr; the result dialog still appears.
* `recordPlayerFacingEvent(...)` is not a debug mutator, does not modify game state, and does not introduce networking.
