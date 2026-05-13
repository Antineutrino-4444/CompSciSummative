# Step 16 — Player-Facing Upgrade Pause UI and Nuke Redesign Panel

## 1. Goal

Replace the Step-15 placeholder "UPGRADE PAUSE — selection UI not implemented yet" with a real player-facing UI that lets the human player:

1. Open and close `UPGRADE_PAUSE` from the HUD (no debug mutators).
2. Browse available upgrades, see cost / DEFCON / prerequisite gating, and apply one.
3. Pick a different nuke design preset and apply it during `UPGRADE_PAUSE` with a visible retention preview.

## 2. Constraints (carried forward)

* **Strict offline-only.** No networking abstractions, no online API stubs.
* The player-facing UI may **not** call any `debug*` mutator on `MutuallyAssuredBlocksMatch`.
* The only mutators called from the player-facing UI in this step are:
  * `openUpgradePause(reason)`
  * `closeUpgradePause(reason)`
  * `applyUpgrade(participantId, upgradeType)`
  * `redesignNukeDuringUpgradePause(participantId, newDesign, retainedRatio)`
* All Swing work happens on the EDT. Refresh runs on the existing 400 ms `javax.swing.Timer`.

## 3. Public APIs used (read-only or sanctioned mutators)

| API | Purpose |
| --- | --- |
| `MutuallyAssuredBlocksMatch.getCurrentPhase()` | Gate UI buttons on `UPGRADE_PAUSE`. |
| `MutuallyAssuredBlocksMatch.getParticipant(ParticipantId)` | Read player `UpgradeState` and `NukeBuildState`. |
| `MutuallyAssuredBlocksMatch.getUpgradeChoices(ParticipantId)` | Source of truth for selectable upgrades. |
| `MutuallyAssuredBlocksMatch.applyUpgrade(...)` | Sanctioned mutator. |
| `MutuallyAssuredBlocksMatch.openUpgradePause(reason)` | Sanctioned mutator. |
| `MutuallyAssuredBlocksMatch.closeUpgradePause(reason)` | Sanctioned mutator. |
| `MutuallyAssuredBlocksMatch.redesignNukeDuringUpgradePause(...)` | Sanctioned mutator. |
| `UpgradeState.canAfford / hasPrerequisites / isMaxed / costForNextLevel / getLevel / getUpgradePoints / getLevels` | Display gating. |
| `NukeRedesignRetentionRules.suggestedRetentionRatio / explain` | Retention preview. |
| `NukeBuildState.getCurrentDesign / getCurrentBuildCharge` | Show what is currently armed. |

## 4. New files

* [src/main/java/com/tetris/mab/ui/MabUpgradeFormatter.java](src/main/java/com/tetris/mab/ui/MabUpgradeFormatter.java) — formats list rows, detail blocks, owned-upgrade summary, apply-result lines, retention preview. Stateless.
* [src/main/java/com/tetris/mab/ui/MabNukePresetDefinition.java](src/main/java/com/tetris/mab/ui/MabNukePresetDefinition.java) — wraps a `Supplier<NukeDesign>` plus display name and description. Static `defaults()` returns 8 presets backed by `NukeDesignFactory` factory methods.
* [src/main/java/com/tetris/mab/ui/MabUpgradePanel.java](src/main/java/com/tetris/mab/ui/MabUpgradePanel.java) — `JList<UpgradeDefinition>` + detail area + Apply button + status label. Refresh keeps selection stable across applies.
* [src/main/java/com/tetris/mab/ui/MabNukeRedesignPanel.java](src/main/java/com/tetris/mab/ui/MabNukeRedesignPanel.java) — preset combo, retention preview text, Apply Redesign button. Disabled outside `UPGRADE_PAUSE`.
* [src/main/java/com/tetris/mab/ui/MabUpgradeWindow.java](src/main/java/com/tetris/mab/ui/MabUpgradeWindow.java) — `JFrame` (`HIDE_ON_CLOSE`) hosting both panels in a `JTabbedPane`, plus a footer "Close Upgrade Pause" button.

## 5. Modified files

* [src/main/java/com/tetris/mab/ui/MabHudPanel.java](src/main/java/com/tetris/mab/ui/MabHudPanel.java#L31) — added "Open Upgrades" / "Close Upgrade Pause" buttons; updated phase banner to say "open the upgrade window"; added `setOpenUpgradeAction` / `setCloseUpgradeAction` callbacks. Buttons enable/disable based on phase.
* [src/main/java/com/tetris/mab/ui/MabHudFormatter.java](src/main/java/com/tetris/mab/ui/MabHudFormatter.java#L57) — `PLAYER_FACING_EVENTS` extended with `UPGRADE_SIDE_EFFECT_APPLIED`, `UPGRADE_APPLY_REJECTED`, `UPGRADE_APPLY_REJECTED_NOT_IN_PAUSE`, `UPGRADE_PAUSE_OPEN_REJECTED`, `UPGRADE_PAUSE_CLOSE_REJECTED`, `NUKE_REDESIGNED`, `NUKE_REDESIGN_REJECTED` so all upgrade-pause activity is visible in the recent-events feed.
* [src/main/java/com/tetris/mab/ui/MabPlayerFacingController.java](src/main/java/com/tetris/mab/ui/MabPlayerFacingController.java) — owns `MabUpgradeWindow`; HUD buttons routed through `requestUpgradePause()` / `requestCloseUpgradePause()`; HUD timer also refreshes the upgrade window when visible; `shutdown()` disposes both frames.

## 6. Lifecycle

```
HUD button "Open Upgrades"
  -> MabPlayerFacingController.requestUpgradePause()
  -> match.openUpgradePause("player-facing-upgrade-ui")
  -> MabUpgradeWindow.setVisible(true)

HUD button "Close Upgrade Pause"  (or window's footer button)
  -> MabPlayerFacingController.requestCloseUpgradePause()
  -> match.closeUpgradePause("player-facing-upgrade-ui")
  -> MabUpgradeWindow.setVisible(false)
```

The 400 ms HUD timer also calls `MabUpgradeWindow.refresh()` while it is visible, so points / DEFCON / charge / current design stay live without manual reloads.

## 7. Upgrade list display

For each `UpgradeDefinition` returned by `getUpgradeChoices(...)`, the list shows a tag of:

* `MAX` — already at max level
* `lock` — prerequisites not met
* `$N` — needs N more upgrade points (unaffordable)
* `ok N` — affordable at cost N

Selecting a row shows category, current level / max level, exact cost, available points, status, DEFCON gate, prerequisites, and the description. The Apply button is enabled only when the selection is in `UPGRADE_PAUSE`, has prereqs, is not maxed, and is affordable. The button's tooltip explains why it is disabled.

## 8. Apply flow

`applyUpgrade(playerId, def.type())` returns `UpgradeApplicationResult`. The status label shows green for `success=true` ("Applied X -> level N (cost C)") or red for `failed()` with the rejection message. The list refreshes immediately so prerequisites of just-purchased upgrades unlock without reopening the window.

## 9. Nuke redesign

`MabNukeRedesignPanel` lists 8 presets backed by `NukeDesignFactory`:

| Preset | Backing factory |
| --- | --- |
| Placeholder Tactical | `createDefaultPlaceholder` |
| Clean Fusion Strategic | `createDefaultCleanFusion` |
| Dirty Tactical | `createDefaultDirtyBomb` |
| Concrete Blaster | `createDefaultConcreteBlaster` |
| MIRV Package | `createDefaultMirv` |
| EMP Special | `createDefaultEmp` |
| Bunker Buster | `createDefaultBunkerBuster` |
| Doomsday Demonstrator | `createDefaultDoomsday` |

Selecting a preset displays the retention preview built from `NukeRedesignRetentionRules.suggestedRetentionRatio(oldDesign, newDesign)` plus `explain(...)`, and shows the floor of `currentCharge * ratio`. Apply Redesign calls `match.redesignNukeDuringUpgradePause(playerId, newDesign, ratio)` and reports success (green) or rejection (red).

## 10. Why factory presets, not custom designs

Per spec, "if conversion is difficult, use existing `NukeDesignFactory` presets and document". `NukeDesign`'s constructor requires per-DEFCON build/code/launch/warning maps that were hand-tuned in Step 3. Building those tables from a small `MabNukePresetDefinition` record would either duplicate Step 3 or break validation. The 8 factory presets cover every doctrine (`PLACEHOLDER`, `CLEAN_FUSION`, `DIRTY_BOMB`, `CONCRETE_BLASTER`, `MIRV`, `EMP`, `BUNKER_BUSTER`, `DOOMSDAY`) and every size category, so every retention rule branch is reachable from this UI. Custom point-and-click design construction is intentionally deferred.

## 11. UI tree

```
JFrame "Mutually Assured Blocks — PvE HUD"
  └── MabHudPanel
        ├── header / phase banner
        ├── [ Open Upgrades ] [ Close Upgrade Pause ]   <-- NEW
        └── arsenal / threats / opponent / actions / events

JFrame "Mutually Assured Blocks — Upgrade Pause"        <-- NEW
  └── JTabbedPane
        ├── Upgrades         -> MabUpgradePanel
        └── Nuke Redesign    -> MabNukeRedesignPanel
        + footer [ Close Upgrade Pause ]
```

## 12. Edge cases handled

* `getUpgradeChoices` throws → header shows "(unable to read choices: ...)" and list stays empty.
* `applyUpgrade` throws → red status label, no further action.
* `redesignNukeDuringUpgradePause` returns false → red "Redesign rejected." label.
* Match is in `ACTIVE` (not `UPGRADE_PAUSE`) → Apply Upgrade and Apply Redesign are disabled with explanatory tooltip.
* Upgrade window closed via window decoration → `HIDE_ON_CLOSE`; the underlying match remains in `UPGRADE_PAUSE` until the user clicks Close Upgrade Pause.
* On `shutdown()`, both windows are disposed and timers stopped.

## 13. Events surfaced

All of these now appear in the HUD's recent-events feed when produced by the match:

* `UPGRADE_PAUSE_OPENED` / `UPGRADE_PAUSE_CLOSED`
* `UPGRADE_PAUSE_OPEN_REJECTED` / `UPGRADE_PAUSE_CLOSE_REJECTED`
* `UPGRADE_APPLIED` / `UPGRADE_SIDE_EFFECT_APPLIED`
* `UPGRADE_APPLY_REJECTED` / `UPGRADE_APPLY_REJECTED_NOT_IN_PAUSE`
* `NUKE_REDESIGNED` / `NUKE_REDESIGN_REJECTED`

## 14. What is NOT in this step

* No custom point-and-click `NukeDesign` builder. Presets only.
* No keyboard shortcut for opening upgrades — strictly button-driven.
* No persistence of upgrade choices across matches (matches are still in-memory).
* No animations or audio.
* No new event types — only existing match events are consumed.
* No changes to AI behaviour.
* No networking, no online hooks, no future-online API reservations.

## 15. Files touched

```
NEW:
  src/main/java/com/tetris/mab/ui/MabUpgradeFormatter.java
  src/main/java/com/tetris/mab/ui/MabNukePresetDefinition.java
  src/main/java/com/tetris/mab/ui/MabUpgradePanel.java
  src/main/java/com/tetris/mab/ui/MabNukeRedesignPanel.java
  src/main/java/com/tetris/mab/ui/MabUpgradeWindow.java

MODIFIED:
  src/main/java/com/tetris/mab/ui/MabHudPanel.java
  src/main/java/com/tetris/mab/ui/MabHudFormatter.java
  src/main/java/com/tetris/mab/ui/MabPlayerFacingController.java
```

## 16. Build & verify

```powershell
$env:Path = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin;' + $env:Path
if (Test-Path target\classes) { Remove-Item -Recurse -Force target\classes }
New-Item -ItemType Directory -Force target\classes | Out-Null
Get-ChildItem -Recurse -Filter *.java src\main\java |
    ForEach-Object { $_.FullName } |
    Set-Content -Encoding ASCII "$env:TEMP\tetris_sources.txt"
javac -d target\classes -encoding UTF-8 "@$env:TEMP\tetris_sources.txt"
java -cp target\classes com.tetris.mab.sim.MabSimulationRunner smoke
```

Result: `EXITCODE=0`, smoke `success=true`, 0 invariant failures.

## 17. Manual smoke checklist (when launched as a real PvE game)

1. Start MAB PvE — companion HUD frame appears top-right.
2. Click "Open Upgrades" — match enters `UPGRADE_PAUSE`, upgrade window opens, list populated.
3. Select an entry — detail pane fills, Apply button enables/disables based on cost / prereqs / DEFCON / max level.
4. Click Apply Upgrade — green status; list refreshes; owned-summary updates.
5. Switch to "Nuke Redesign" tab — current design + charge shown.
6. Pick a different preset — retention preview updates with `suggestedRetentionRatio` and a textual explanation.
7. Click Apply Redesign — green "Redesigned -> X (ratio Y)"; current label updates.
8. Click "Close Upgrade Pause" — match returns to `ACTIVE`, window hides, both Apply buttons disable.

## 18. Invariants

* No call to any `debug*` method from `com.tetris.mab.ui`.
* `com.tetris.mab.ui` does not import anything from `com.tetris.mab.debugui`.
* All UI mutations happen on the EDT through Swing event listeners or the existing 400 ms `javax.swing.Timer`.
* `MutuallyAssuredBlocksMatch` is unchanged.

## 19. Status

Step 16 complete. Compile clean. Smoke `success=true`. Player-facing upgrade pause and nuke redesign UI are wired into the existing PvE flow without touching debug mutators or breaking Step 15 behaviour.
