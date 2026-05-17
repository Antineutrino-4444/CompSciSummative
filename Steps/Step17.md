# Step 17 — Player-Facing Action Command Guide, Strategic Alerts, and Launch/Defense UX

## 1. Files added / modified

NEW
- [src/main/java/com/tetris/mab/ui/MabCommandGuideEntry.java](src/main/java/com/tetris/mab/ui/MabCommandGuideEntry.java)
- [src/main/java/com/tetris/mab/ui/MabCommandGuideModel.java](src/main/java/com/tetris/mab/ui/MabCommandGuideModel.java)
- [src/main/java/com/tetris/mab/ui/MabCommandGuideFormatter.java](src/main/java/com/tetris/mab/ui/MabCommandGuideFormatter.java)
- [src/main/java/com/tetris/mab/ui/MabCommandGuideWindow.java](src/main/java/com/tetris/mab/ui/MabCommandGuideWindow.java)
- [src/main/java/com/tetris/mab/ui/MabAlertSeverity.java](src/main/java/com/tetris/mab/ui/MabAlertSeverity.java)
- [src/main/java/com/tetris/mab/ui/MabAlert.java](src/main/java/com/tetris/mab/ui/MabAlert.java)
- [src/main/java/com/tetris/mab/ui/MabAlertModel.java](src/main/java/com/tetris/mab/ui/MabAlertModel.java)
- [src/main/java/com/tetris/mab/ui/MabAlertFormatter.java](src/main/java/com/tetris/mab/ui/MabAlertFormatter.java)

MODIFIED
- [src/main/java/com/tetris/mab/ui/MabHudFormatter.java](src/main/java/com/tetris/mab/ui/MabHudFormatter.java) — extended `PLAYER_FACING_EVENTS` allowlist with action / launch / threat / intercept / radar event names; added `formatThreatLaunchBanner(...)` and `formatLaunchReadiness(...)`; expanded `formatActionCodeStatus(...)` with hint text and improved confirmation guidance.
- [src/main/java/com/tetris/mab/ui/MabHudPanel.java](src/main/java/com/tetris/mab/ui/MabHudPanel.java) — added Alerts section, threat/launch banner label, launch-readiness label, "Command Guide" button + `setOpenCommandGuideAction` callback; HUD refresh now also drives `MabAlertModel`.
- [src/main/java/com/tetris/mab/ui/MabPlayerFacingController.java](src/main/java/com/tetris/mab/ui/MabPlayerFacingController.java) — owns `MabCommandGuideWindow`; HUD button routed to `openCommandGuide()`; `shutdown()` disposes the guide window.

## 2. Offline-only scope confirmation

This step adds Swing-only display code under `com.tetris.mab.ui`. No networking, no client/server, no online multiplayer hooks, no abstractions reserved for future online play. Mutually Assured Blocks remains strictly offline (Local 1v1, PvE, debug/practice).

## 3. Command guide model

`MabCommandGuideModel.buildEntries(match, playerId)`:
1. Reads `MutuallyAssuredBlocksMatch.getActionCodeRegistry()` (already exposed publicly at line 294 of `MutuallyAssuredBlocksMatch.java` — no new getter needed).
2. Synthesises one **Current Nuke Launch** entry from the player's `NukeBuildState.getCurrentDesign()` via `ActionCodeRegistry.launchDefinitionForNuke(design, defcon)`.
3. Synthesises one **Confirm Launch** entry describing the hard 4-line confirmation rule.
4. Wraps every non-LAUNCH `ActionCodeDefinition` in a `MabCommandGuideEntry`. Per-nuke launch definitions are skipped because the synthetic Current Nuke Launch entry above already covers the only launch the player can actually fire.

Token sequences are formatted from `ActionCodeTokenRequirement.toDebugString()`:
* `N` — normal N-line clear (spin allowed)
* `AN` — anchored N-line clear (no spin)
* `C4` — hard 4-line confirmation (no spin)

Availability text values used:
* `"Available"`
* `"Requires armed nuke"`
* `"No incoming threat"`
* `"Confirmation pending"`
* `"Idle (no confirmation required right now)"`
* `"Upgrade pause active — gameplay commands paused"`

The model never mutates the match.

## 4. Command guide UI

`MabCommandGuideWindow` is a `JFrame` (`HIDE_ON_CLOSE`) containing a monospaced `JTextArea` with the full grouped guide plus Refresh / Close buttons. Opens via the new "Command Guide" button on the HUD. The header text in the window reads:

> Strategic commands are entered by clearing the listed line counts in order. Spins may replace ordinary line clears where the action-code system permits; anchored tokens (A1..A4) and confirmation tokens (C4) cannot be replaced.

`MabCommandGuideFormatter.formatFullGuide(...)` groups entries by category (Launch, Confirmation, DEFENSE, INTEL, DECOY, UTILITY, RESTRAINT, etc.) and renders each as `displayName / Code / Availability / Notes`.

## 5. Active action progress display

`MabHudFormatter.formatActionCodeStatus(...)` now shows:
* Active command name (or "(none)" with a hint to start one).
* Progress as `currentIndex / requiredLength`.
* Expected next clear (line count) when present.
* Pending confirmation action and instruction:
  > Complete the final 4-line clear (Tetris) to confirm.

The data is read from existing `ParticipantSummary` fields (`activeActionId/Name/Progress/RequiredLength/ExpectedNextClear`, `pendingConfirmationActionId/Name`). No snapshot extension was required.

## 6. Current nuke launch-code display

`MabHudFormatter.formatLaunchReadiness(snapshot, playerId)` prints a single mono-bold line above the HUD body, e.g.:
* `Clean Fusion Strategic: Charging 18/44`
* `Clean Fusion Strategic: Armed — enter launch code`
* `Clean Fusion Strategic: Launch pending confirmation`
* `Clean Fusion Strategic: Launch in flight`
* `Clean Fusion Strategic: Impact ready`

The actual launch-code tokens are shown by the Command Guide entry "Launch: <design name>" so the player can read the required clear pattern at any time.

## 7. Confirmation guidance

* Action-code section repeats the confirmation instruction whenever `pendingConfirmationActionId != null`.
* Command Guide includes a top-level **Confirm Launch** entry under category *Confirmation*.
* The alert model emits `[WARN] Confirmation pending` whenever a pending-confirmation attempt exists.

## 8. Alert model and severity

`MabAlertSeverity` (`INFO`, `SUCCESS`, `WARNING`, `CRITICAL`) — each carries a short tag (`INFO`, `OK`, `WARN`, `CRIT`) used as a bracketed prefix.

`MabAlert` is a record `(eventSequence, severity, title, message)` with `eventSequence == 0` for state-derived alerts.

`MabAlertFormatter.formatTopRows(alerts, max)` renders rows like:

```
[CRIT] Incoming threat — Threats inbound: 1. Warning pieces remaining (first): 6.
[WARN] Confirmation pending — Confirm: Launch: Clean Fusion Strategic — complete the final 4-line clear (Tetris).
[OK]   Civil defense — source=civil_defense
```

## 9. Alert generation rules

State-derived (no event sequence):
* `impactReadyThreatCount > 0` → CRITICAL "Impact ready"
* else `incomingThreatCount > 0` → CRITICAL "Incoming threat" with first warning pieces remaining
* `impactReadyLaunchCount > 0` → WARNING "Your impact ready"
* else `activeLaunchCount > 0` → INFO "Launch in flight"
* `armed` → INFO "Nuke armed"
* `upgradePoints > 0` outside UPGRADE_PAUSE → INFO "Upgrade points available"
* `intelStale` → WARNING "Intel stale"
* `pendingConfirmationActionId != null` → WARNING "Confirmation pending"

Event-derived (newest first; mapped from real emitted event names):
* `DEFCON_CHANGED` → WARNING
* `LAUNCH_AUTHORIZED` → SUCCESS for self / WARNING for opponent
* `INCOMING_THREAT_CREATED`, `IMPACT_READY` → CRITICAL
* `IMPACT_RESOLVED` → WARNING (you were hit) / SUCCESS (your strike landed)
* `CIVIL_DEFENSE_ACTIVATED`, `CIVIL_DEFENSE_MITIGATION_APPLIED` → SUCCESS
* `INTERCEPT_RESOLVED`, `THREAT_FULLY_INTERCEPTED` → SUCCESS
* `INTERCEPT_FAILED`, `THREAT_PARTIALLY_INTERCEPTED` → WARNING
* `RADAR_SCAN_COMPLETED` → INFO; `RADAR_SCAN_REJECTED` → WARNING
* `DECOY_ACTIVATED` → INFO/WARNING (own/opponent)
* `UPGRADE_APPLIED` → SUCCESS; `NUKE_REDESIGNED` → INFO
* `ACTION_COMPLETED`, `ACTION_CONFIRMED` → SUCCESS
* `ACTION_PENDING_CONFIRMATION` → WARNING
* `ACTION_FAILED_RESET`, `ACTION_CONFIRM_FAILED` → WARNING
* `ACTION_START_REJECTED_*`, `LAUNCH_AUTHORIZATION_REJECTED_*` → WARNING

The HUD displays the top 5 rows. No long-term alert history is stored.

## 10. Threat / launch warning banners

`MabHudFormatter.formatThreatLaunchBanner(snapshot, playerId)` returns the most urgent of:
* `IMPACT READY — use intercept / civil defense or resolve impact`
* `INCOMING THREAT — warning pieces remaining: N`
* `YOUR IMPACT IS READY`
* `LAUNCH IN FLIGHT`
* `""` (no banner)

The banner is shown in mono-bold dark red at the top of the HUD, just below the phase banner.

## 11. Event feed changes

`PLAYER_FACING_EVENTS` now also includes:
`ACTION_STARTED`, `ACTION_ADVANCED`, `ACTION_PENDING_CONFIRMATION`, `ACTION_CONFIRMED`, `ACTION_COMPLETED`, `ACTION_CANCELLED`, `ACTION_FAILED_RESET`, `ACTION_CONFIRM_FAILED`, `ACTION_START_REJECTED_NOT_ARMED`, `ACTION_START_REJECTED_NO_THREAT`, `ACTION_START_REJECTED_NO_ACTIVE_THREAT`, `LAUNCH_AUTHORIZED`, `LAUNCH_AUTHORIZATION_REJECTED_PHASE`, `LAUNCH_AUTHORIZATION_REJECTED_NOT_ARMED`, `LAUNCH_COUNTDOWN_STARTED`, `LAUNCH_IN_FLIGHT`, `INCOMING_THREAT_CREATED`, `IMPACT_READY`, `IMPACT_RESOLVED`, `THREAT_FULLY_INTERCEPTED`, `THREAT_PARTIALLY_INTERCEPTED`, `INTERCEPT_STARTED`, `INTERCEPT_FAILED`, `RADAR_SCAN_STARTED`.

Names match the real emitted strings in `MutuallyAssuredBlocksMatch.log(...)` call sites (e.g. the codebase emits `ACTION_ADVANCED` and `ACTION_FAILED_RESET`, not `ACTION_PROGRESS` / `ACTION_FAILED`).

## 12. Player-facing control safety

* `MabCommandGuideWindow` only reads from the registry and snapshot.
* `MabAlertModel.buildAlerts(...)` only reads from snapshot + event log.
* `MabHudPanel.refresh(...)` only calls `match.toDebugSnapshot()`, `match.getRecentEvents(...)`, and the alert/banner formatters.
* The "Command Guide" button calls only `MabCommandGuideWindow.refresh()` + `setVisible(true)`.
* No new player-facing buttons launch nukes, activate radar, civil defense, or decoys. Strategic actions are still entered through line-clear sequences as designed.
* The only player-facing mutators allowed remain the four sanctioned upgrade/redesign methods from Step 16: `openUpgradePause`, `closeUpgradePause`, `applyUpgrade`, `redesignNukeDuringUpgradePause`.
* `com.tetris.mab.ui` does not import `com.tetris.mab.debugui` and calls no `debug*` mutator.

## 13. Normal Tetris isolation

`GameController` was unchanged in this step. The `NORMAL_TETRIS` path still does not construct any `MabHudPanel`, `MabCommandGuideWindow`, `MabAlertModel`, or upgrade UI. The Step-15 launch-mode gate continues to isolate MAB UI. The debug HUD remains opt-in via `-Dmab.debug.hud=true`.

## 14. Manual test script

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
1. From StartMenu, choose **Mutually Assured Blocks (PvE)**.
2. Companion HUD frame appears top-right with sections: Alerts / Your arsenal / Incoming threats / Opponent intel / Action code / Recent events.
3. Click **Command Guide** — a window lists every command grouped by category, including Radar Scan (`2, 1, A2`), Civil Defense (`1, A1, 2`), Emergency Intercept (`1, A2, A1`), Full Intercept, Decoy Launch, Ghost MIRV, and the dynamic Launch entry for the current nuke design.
4. Begin a real action by clearing lines in the listed pattern. Watch the **Action code** section update Active / Progress / Next clear.
5. Wait for the AI Player B to launch — `Incoming threat` row, banner `INCOMING THREAT — warning pieces remaining: N`, and a `[CRIT]` alert appear.
6. When impact lands, observe `IMPACT READY` banner and `[CRIT] Impact ready` alert; complete intercept or civil defense to see `[OK]` follow-ups in the Alerts section.
7. Click **Open Upgrades** to confirm Step 16's upgrade UI still works; close it via **Close Upgrade Pause**.
8. Return to StartMenu and click normal **Play** — only the standard Tetris window appears, no MAB HUD or command guide.

## 15. Intentionally not implemented yet

* No final art / animations — text-only banners and severity tags.
* No tutorial campaign / missions.
* No audio cues for alerts.
* No two-board layout.
* No new debug mutators.
* No keyboard shortcut for opening the Command Guide (button only).
* No long-term alert history — only the most recent 5 are shown.
* No custom action codes — registry contents come straight from `ActionCodeRegistry.createDefault()`.
* No networking, no online multiplayer, no client/server.

## 16. Acceptance-criteria verification

| Criterion | Result |
| --- | --- |
| Project compiles with EXITCODE=0 | ✅ confirmed below |
| Smoke simulation `success=true` | ✅ confirmed below |
| Normal Tetris Play still starts without MAB HUD | ✅ `GameController` NORMAL_TETRIS path untouched |
| MAB PvE still starts from StartMenu | ✅ untouched from Step 15 |
| MAB PvE player-facing HUD still appears | ✅ `MabPlayerFacingController.start()` unchanged in lifecycle |
| Command Guide button/window exists | ✅ `MabCommandGuideWindow` + HUD button |
| Guide lists radar / civil defense / intercept / decoy / launch / confirmation | ✅ via `ActionCodeRegistry` + synthetic launch / confirm entries |
| HUD shows active action progress | ✅ `formatActionCodeStatus` |
| HUD shows pending confirmation guidance | ✅ "Complete the final 4-line clear (Tetris) to confirm." |
| HUD shows current nuke launch readiness/code | ✅ `formatLaunchReadiness` + Command Guide launch entry |
| Alerts section appears in HUD | ✅ `MabHudPanel` Alerts section |
| Incoming threats produce critical alert / banner | ✅ state-derived CRITICAL alert + banner string |
| Launch / impact-ready states produce banners | ✅ `formatThreatLaunchBanner` covers all four cases |
| Event feed includes action-code / strategic events | ✅ allowlist extended |
| Upgrade UI from Step 16 still opens and compiles | ✅ unchanged |
| Player-facing HUD does not call debug mutators | ✅ verified in Part 12 |
| No networking / online code | ✅ none added |
| Step17.md documents the implementation | ✅ this file |

## 17. Build command used and final build result

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

## 18. Smoke simulation command and result

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
