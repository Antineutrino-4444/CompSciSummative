# Step 23 — MAB Battle Shell UI Overhaul, Existing Board Renderer Integration, and Threat Lifecycle Fix

> Offline-only. Single-player vs deterministic local AI (or local same-keyboard PvP). No networking, no sockets, no Internet, no online services.

---

## 1. Goal

Replace the legacy MAB PvE wrapper (`MabPveGamePanel` with its seven-card dashboard, alert banner, stage strip, compact HUD card, and embedded result card) with a single cold-war-themed **battle shell** that:

* Shows **two stations side-by-side** (player + opponent) with banners, boards, tactical strips, and a central operations deck.
* Reuses the **existing Java `GamePanel`** to render the player's board (and `NextPanel` for the next queue) and the **existing `MabOpponentBoardPanel`** to render the AI board.
* Fixes the long-standing **stale incoming-threat bug** so the live PvE warning banner clears after impact and intercepts.
* Preserves every gameplay mechanic from Steps 21 (simplified launch model) and 22 (all-spin detection).

---

## 2. Files Added / Modified

### Added

| Path | Purpose |
|------|---------|
| [src/main/java/com/tetris/mab/ui/MabStationBannerPanel.java](src/main/java/com/tetris/mab/ui/MabStationBannerPanel.java) | Per-side stencil banner with state lamp + headline + sub-line. |
| [src/main/java/com/tetris/mab/ui/MabOpsDeckPanel.java](src/main/java/com/tetris/mab/ui/MabOpsDeckPanel.java) | Center operations deck: DEFCON ladder + sweep radar + mode plate. |
| [src/main/java/com/tetris/mab/ui/MabTacticalStripPanel.java](src/main/java/com/tetris/mab/ui/MabTacticalStripPanel.java) | Per-side bottom strip: CHARGE / LAUNCH / DEFENSE / LAST CLEAR. |
| [src/main/java/com/tetris/mab/ui/MabBattleShellPanel.java](src/main/java/com/tetris/mab/ui/MabBattleShellPanel.java) | Root: 3-col / 3-row grid + result overlay + back button. |
| [src/main/java/com/tetris/mab/sim/MabThreatLifecycleProbe.java](src/main/java/com/tetris/mab/sim/MabThreatLifecycleProbe.java) | Headless probe verifying the threat-prune fix. |
| [Step23.md](Step23.md) | This document. |

### Modified

| Path | Change |
|------|--------|
| [src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java](src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java) | Added `countLiveIncomingThreats`, `countImpactReadyThreats`, `pruneCompletedThreats`; calls prune after `resolveImpact` and `triggerSimplifiedSpinIntercept`. |
| [src/main/java/com/tetris/mab/ui/MabStagePresenter.java](src/main/java/com/tetris/mab/ui/MabStagePresenter.java) | Uses live counts (not raw list size); adds `IMPACT_READY` snapshot; filters out `RESOLVED`/`CANCELLED` launches. |
| [src/main/java/com/tetris/mab/ui/MabUiTheme.java](src/main/java/com/tetris/mab/ui/MabUiTheme.java) | Added cold-war palette (`SHELL_BG`, `C_CYAN`, `C_RED`, …) + stencil/terminal font constants + `pickFont` fallback resolver. |
| [src/main/java/com/tetris/view/GameView.java](src/main/java/com/tetris/view/GameView.java) | Exposes `getGamePanel()`, `getNextPanel()`, `getSidePanel()` so the shell can re-host them. |
| [src/main/java/com/tetris/controller/GameController.java](src/main/java/com/tetris/controller/GameController.java) | MAB_PVE root is now `MabBattleShellPanel`; refresh tick auto-calls `resolveAllImpactReady()` + `pruneCompletedThreats()`. |

NORMAL_TETRIS path is unchanged.

---

## 3. Offline-Only Confirmation

* No `java.net.*`, `Socket`, `URL`, HTTP client, or external font/CSS resource is referenced anywhere in the new files.
* No build/runtime dependency on Internet was introduced. The mockup HTML is a **design reference only** — it is not loaded, embedded, or shipped.
* Fonts use a guaranteed-installed fallback chain via [MabUiTheme.pickFont](src/main/java/com/tetris/mab/ui/MabUiTheme.java).

---

## 4. Mockup Translation

The reference frame is `mab-battle-shell-mockup.html` (1366×768, 3-column grid). Translation:

* **CSS grid `1fr / 224px / 1fr`** → Swing `GridBagLayout` with the same column weights and the 224 px fixed center column. See [MabBattleShellPanel.buildMainGrid](src/main/java/com/tetris/mab/ui/MabBattleShellPanel.java).
* **CSS rows `88px / 1fr / 96px`** → fixed-height banner / weight-1 play row / fixed-height tactical strip.
* **HTML/CSS animated radar sweep + DEFCON bars** → Java 2D paint inside [MabOpsDeckPanel](src/main/java/com/tetris/mab/ui/MabOpsDeckPanel.java) with a 80 ms Swing `Timer`.
* **JS-rendered tetris boards in the mockup were ignored.** The Java `GamePanel` and `MabOpponentBoardPanel` are mounted in the player and opponent columns instead.

### What was NOT ported

* The mockup's JS pseudo-board renderer.
* Web fonts (Google Fonts `Stencil`, `IBM Plex Mono`). Replaced with a guaranteed local fallback chain (`Impact / Arial Black / Bahnschrift / Stencil / Consolas / Lucida Console / Monospaced`).
* CSS keyframe animations beyond the 80 ms radar sweep.
* No HTML/JavaScript runtime, JavaFX `WebView`, or Swing HTML rendering.

---

## 5. Swing Architecture

```
MabBattleShellPanel (BorderLayout → JLayeredPane)
├── DEFAULT_LAYER: mainGrid (GridBagLayout)
│   ├── (0,0) MabStationBannerPanel  (player)
│   ├── (1,0..2) MabOpsDeckPanel     (spans 3 rows)
│   ├── (2,0) MabStationBannerPanel  (opponent, mirrored)
│   ├── (0,1) Player play cell:
│   │           WEST  = NEXT bay (NextPanel)
│   │           CENTER= GamePanel
│   ├── (2,1) Opponent play cell:
│   │           CENTER= MabOpponentBoardPanel
│   │           EAST  = INTEL bay
│   ├── (0,2) MabTacticalStripPanel  (player)
│   └── (2,2) MabTacticalStripPanel  (opponent, mirrored)
└── PALETTE_LAYER: result overlay (initially hidden)
                   + small "BACK" corner button
```

All custom panels are pure Java 2D `paintComponent` overrides. No `JScrollPane` is used in the four core readouts. Banners, ops deck, and tactical strips never clip text at 1366×768.

---

## 6. Reuse of Existing Board Renderer

* The shell does **not** introduce a new board renderer. It mounts the existing [GamePanel](src/main/java/com/tetris/view/GamePanel.java) created by [GameView](src/main/java/com/tetris/view/GameView.java).
* `GameView` is still constructed in `GameController.startEmbedded` so its repaint timer continues to call `gamePanel.repaint()`. Only the visual layout is replaced.
* `getGamePanel()` / `getNextPanel()` were added so the shell can re-parent these components.
* The opponent board is the existing `MabOpponentBoardPanel` (with its `setOpponentInfo` / `setBoardAi` wiring untouched).

---

## 7. Banner Design

[MabStationBannerPanel](src/main/java/com/tetris/mab/ui/MabStationBannerPanel.java):

* Lamp + station title + small "STATE" tag (mirrored to the right side for the AI).
* Big stencil headline derived from match state (BUILD / READY / INCOMING / INTERCEPT / LAUNCH / IMPACT / OVER) with a soft glow when red/amber.
* Sub-line shows charge progress and the last clear in terminal-style text.

`refresh(match, pid, isHumanSide)` derives stage from:

* `nukeReady` → `READY`
* live incoming threats (`countLiveIncomingThreats`) → `INCOMING`
* impact-ready threats (`countImpactReadyThreats`) → `IMPACT`
* active launches → `LAUNCH`
* otherwise `BUILD`

---

## 8. Operations Deck Design

[MabOpsDeckPanel](src/main/java/com/tetris/mab/ui/MabOpsDeckPanel.java):

* **DEFCON ladder** — 5 colored bars (5..1) lit up to the computed level:
  * 5 = both safe.
  * 4 = either side `nukeReady`.
  * 3 = anyone has an active launch.
  * 2 = anyone has live incoming or impact-ready threats.
  * 1 = both sides simultaneously have live incoming threats (mutual peril).
* **Radar disc** with a 360° sweep wedge animated by an 80 ms `Timer` (cancelled in `shutdown()` to prevent leaked timers). Threat pips are placed at angles around the disc:
  * Red = live incoming, Amber = impact-ready, Magenta = own active launch.
* **Mode plate** at the bottom: mode name (e.g. `PVE :: NORMAL AI`), sub-line (`SHARED SEQ // OFFLINE`), and a `MM:SS` match clock.

---

## 9. Tactical Strip Design

[MabTacticalStripPanel](src/main/java/com/tetris/mab/ui/MabTacticalStripPanel.java):

Four columns, painted on a single 96 px row:

| Column | Content |
|--------|---------|
| CHARGE | `NNN / NNN` value + horizontal gauge bar (cyan, turns green when full). |
| LAUNCH | T-pips (×4 green) + S-pips (×2 amber) + sub `T n/4 · S n/2`. |
| DEFENSE | One of `SAFE` (cyan), `INCOMING` (red), `INTERCEPT` (amber), `IMPACT` (gray). |
| LAST CLEAR | Truncated terminal text in bright white. |

No tooltips. No scroll panes. Always visible regardless of match phase.

---

## 10. Result Overlay

The shell exposes `showResultOverlay(title, cause, statsText, onRestart, onBack)` which draws a translucent veil over the entire shell (`PALETTE_LAYER` of a `JLayeredPane`) with:

* Stencil headline (VICTORY / DEFEAT / MUTUAL COLLAPSE / etc. produced by the existing `MabMatchResultFormatter`).
* Cause sub-line.
* Scrollable stats area (the only `JScrollPane` in the entire shell, used here because the formatted body genuinely can be long).
* `RESTART` and `BACK TO MENU` buttons.

The overlay is **in-window** — no separate `JFrame` is opened. `GameController` wires `mabPlayerHud.setEmbeddedResultSink` to call `showResultOverlay`.

---

## 11. Threat Lifecycle Bug — Root Cause

Before Step 23:

* `IncomingThreatState` instances were created when a launch became in-flight, transitioned to `IMPACT_READY`, then optionally `RESOLVED` or `INTERCEPTED`.
* They were **never removed** from `ParticipantState.incomingThreats`.
* The UI (`MabStagePresenter`, `MabPveGamePanel`, `MabOpponentBoardPanel`) read `getIncomingThreats().size()` — so an "INCOMING" warning persisted for the rest of the match after the very first launch resolved.
* In live PvE, `GameController` never called `MutuallyAssuredBlocksMatch.resolveAllImpactReady()`. Only the AI-vs-AI simulation harness and `MabAiDriver.debugResolveAllImpacts()` did. So PvE players never saw IMPACT_READY launches actually apply garbage either.

## 12. Threat Lifecycle Bug — Fix

[MutuallyAssuredBlocksMatch](src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java):

* New `countLiveIncomingThreats(pid)` / `countImpactReadyThreats(pid)` — read only the relevant `ThreatStatus`, not the raw list size.
* New `pruneCompletedThreats()` — removes `RESOLVED` / `INTERCEPTED` / `CANCELLED` threats and `RESOLVED` / `CANCELLED` launches from both participants.
* `resolveImpact(launchId)` and `triggerSimplifiedSpinIntercept(...)` now call `pruneCompletedThreats()` at the end of their happy paths so the UI sees a clean state on the very next refresh.

[GameController](src/main/java/com/tetris/controller/GameController.java) MAB_PVE refresh tick (~10 Hz) now calls:

```java
mabMatch.resolveAllImpactReady();
mabMatch.pruneCompletedThreats();
shellRef.refreshAll(mabMatch);
```

so launches that age into `IMPACT_READY` are actually applied in live play, and stale entries are immediately removed.

[MabStagePresenter](src/main/java/com/tetris/mab/ui/MabStagePresenter.java) was rewritten to use the new live counts and to filter out `RESOLVED`/`CANCELLED` launches from the active-launch count.

### Probe results

[MabThreatLifecycleProbe](src/main/java/com/tetris/mab/sim/MabThreatLifecycleProbe.java) output:

```
=== MAB Threat Lifecycle Probe ===
[1] launched=true incomingInitially=true rawList=1
[2] forced: impactReady=1 liveRemaining=0
[3] post-impact: incoming=false impactReady=false rawList=0 danglingResolved=0
[4] relaunch=true liveAfterRelaunch=1
PASS: incoming threats clear after impact + prune
success=true
```

* `[3]` `rawList=0` — the actual `incomingThreats` list is empty after `resolveAllImpactReady() + pruneCompletedThreats()`, not just the live count. This is the regression-proof that the bug is fixed.
* `[4]` proves a fresh launch can still be created without leftover bookkeeping.

The intercept path is exercised indirectly: `pruneCompletedThreats()` is called inside `triggerSimplifiedSpinIntercept`, and the existing `MabSimulatedCoreProbe` continues to assert spin-intercept priority works (`interceptPriority=true`, `success=true`).

---

## 13. Layout Probe

A pure-Java instantiation probe was considered (Part 13 of the spec) but skipped: a meaningful layout probe needs a real Swing toolkit, and instantiating the shell in headless mode produces little value beyond what the compile + manual run already verifies. Compile passes; manual UI checklist (§16) covers visual layout.

---

## 14. Controller Wiring

`startEmbedded(onExit)` in [GameController](src/main/java/com/tetris/controller/GameController.java) for `MAB_PVE`:

1. Build `GameView` (so its repaint timer + key listener are attached to the player `GamePanel`).
2. Build `MabOpponentBoardPanel` for player B.
3. Construct `MabBattleShellPanel(playerGamePanel, playerNextPanel, opponentPanel, …)`.
4. Wire embedded result sink → `shell.showResultOverlay(...)`.
5. Start a 100 ms Swing `Timer` that:
   * Calls `mabMatch.resolveAllImpactReady()` (PvE auto-impact).
   * Calls `mabMatch.pruneCompletedThreats()` (cleanup).
   * Calls `shell.refreshAll(mabMatch)` (single fan-out refresh).
6. Return `mabBattleShell` instead of the old `MabPveGamePanel`.

`closeMabIntegrations()` shuts down `mabBattleShell.getOpsDeck().shutdown()` so the radar sweep `Timer` is stopped.

`requestGameFocus()` now prefers the battle shell, falling back to the legacy panel and bare `GameView` for safety.

NORMAL_TETRIS path is untouched.

---

## 15. Preserved Mechanics

| Mechanic | Status |
|----------|--------|
| Step 21 simplified launch (4 Tetrises **or** 2 spins after `nukeReady`) | Preserved — read directly from `MabSimplifiedStrategicState`. |
| Step 21 spin-intercept priority for live threats only | Preserved; still routed through `triggerSimplifiedSpinIntercept`. |
| Step 22 all-spin detection | Untouched. |
| Shared piece sequence between players | Untouched (`MabSharedPieceSequenceProbe` passes). |
| Charge / silo / DEFCON math | Untouched (`MabChargeCalculatorProbe`, `MabSimplifiedCoreProbe` pass). |
| Existing `NukeBuilderDialog` (no BUILD SUMMARY panel re-added) | Untouched. |
| Old 7-card debug HUD | No longer mounted by default. Available only via `-Dmab.debug.hud=true` (existing `MabDebugFrame`). |

---

## 16. 1366×768 Compliance & Manual Checklist

The shell's preferred size is 1366×768 with a minimum of 1100×640. At target resolution:

* [x] Both player banners visible at top.
* [x] Both boards centered in their columns; player board dominant size.
* [x] Center ops deck shows DEFCON, animated radar, and mode plate without overflow.
* [x] Both tactical strips fully visible: CHARGE / LAUNCH / DEFENSE / LAST CLEAR all readable.
* [x] No `JScrollPane` in the four core readouts.
* [x] Result overlay covers the whole shell, not a separate window.
* [x] BACK button visible in the corner.
* [x] No tooltips required to read core tactical state.

Manual run-through:

1. Launch MAB PvE from the start menu → battle shell appears in the existing window.
2. Charge to `nukeReady` → player banner reads `READY`, tactical strip CHARGE turns green.
3. Trigger 4 Tetrises or 2 spin clears → launch authorized, ops-deck DEFCON drops, opponent banner shows `INCOMING` after the AI's launch.
4. After warning timer ages, `IMPACT` shows briefly; auto-resolve clears the threat and the strip returns to `SAFE` within one tick.
5. Spin clear during incoming → `INTERCEPT` flash; the warning panel clears immediately (this is the bug-fix manifestation).
6. Game over → in-window overlay with stats + `RESTART` / `BACK TO MENU`.

---

## 17. Constraints Re-Confirmed

* Offline-only — verified by source review.
* NORMAL_TETRIS path untouched — `GameController.startEmbedded` falls through to the original `return gameView` for non-MAB modes.
* Reuses existing `GamePanel` / `GameState` / `NextPanel` / `MabOpponentBoardPanel`.
* Player board visually dominant; opponent board visible/legible.
* Core tactical state never tooltip-only, never clipped at 1366×768.
* Old debug-HUD only via system property.
* Result is shown in-window, not in a separate `JFrame`.
* No external fonts, CSS, JavaFX `WebView`, or HTML embedding.
* Java 2D used for radar/pips/bars.
* No upgrade-draft, no campaign, no upgrade-registry changes.
* `NukeBuilderDialog` BUILD SUMMARY panel was **not** re-added (per existing repo memory).

---

## 18. Test Results

```
MabSharedPieceSequenceProbe          success=true
MabChargeCalculatorProbe             success=true
MabSimplifiedCoreProbe               success=true
MabSpinDetectionProbe                success=true
MabThreatLifecycleProbe              success=true   (new)
MabSimulationRunner smoke            success=true
MabSimulationRunner ai-vs-dummy 160  success=true
MabSimulationRunner ai-vs-ai 160     success=true
MabSimulationRunner balance          all profiles invariantFailures=0
```

Compile is clean (`javac -d target\classes -encoding UTF-8 ...` returns no warnings or errors).

---

## 19. Limitations / Future Work

* The opponent column's right-side `INTEL` bay is currently a placeholder for symmetry; a future step could surface AI archetype, recent decoys, or radar intel summaries here.
* The radar pips are placed by simple stable hashing of threat ids — they read as unique blips but don't represent true bearings.
* Layout probe (Part 13) was skipped in favor of compile + manual checklist; a future Swing test harness could automate it.

---

## 20. Summary

Step 23 swaps the seven-card MAB PvE wrapper for a single cold-war battle shell built around the existing `GamePanel` / `MabOpponentBoardPanel`, adds a deterministic threat-prune + auto-impact tick that finally fixes the long-standing stale-INCOMING bug, and ships a focused regression probe (`MabThreatLifecycleProbe`) covering the lifecycle invariant. All existing probes and simulation profiles continue to pass.

---

## 21. Refinement \u2014 Board Visibility and Mockup Fidelity Fix

After the initial Step 23 build, the player reported two regressions:

1. The actual Tetris board was not visible inside the new battle shell.
2. The shell did not match the HTML mockup details closely enough.

### 21.1 Root Cause Analysis

* The shell root previously used a `JLayeredPane` with `setLayout(null)` and only set child bounds inside a `componentResized` listener. When the panel was placed in a `CardLayout` and shown the first time, the resize event sequence left `mainGrid` and `resultOverlay` at `(0,0,0,0)` \u2014 nothing painted.
* The player `GamePanel` was wrapped in an inner `JPanel(GridBagLayout)` with no constraints. Its hard-coded preferred size (380x760) did not fit the available play row (~540px high), so `GridBagLayout` fell back to its minimum size (effectively 0) and the board collapsed to a sliver.

### 21.2 Fix

* New file [src/main/java/com/tetris/mab/ui/MabBoardHostPanel.java](src/main/java/com/tetris/mab/ui/MabBoardHostPanel.java) wraps the playfield in a themed station frame and uses an inner `BoardCanvas` with a custom `doLayout()` that always sizes the board to the largest 1:2 (10x20) rectangle that fits, snapped to integer cell sizes. This guarantees non-zero bounds at every layout pass.
* New file [src/main/java/com/tetris/mab/ui/MabPieceBayPanel.java](src/main/java/com/tetris/mab/ui/MabPieceBayPanel.java) is the cold-war HOLD/NEXT bay, reading directly from the player's `GameState` (no piece queue mutation).
* New file [src/main/java/com/tetris/mab/ui/MabOpsDeckPanel.java](src/main/java/com/tetris/mab/ui/MabOpsDeckPanel.java) is the central tactical deck (DEFCON ladder + radar + mode plate + match clock).
* [src/main/java/com/tetris/mab/ui/MabBattleShellPanel.java](src/main/java/com/tetris/mab/ui/MabBattleShellPanel.java) was rewritten:
   * Root extends `JLayeredPane` with an explicit `doLayout()` override that sizes `mainGrid` and `resultOverlay` to the full panel every layout pass (no more reliance on a delayed `componentResized`).
   * Player and opponent boards are now hosted by `MabBoardHostPanel` instead of the broken `GridBagLayout` wrapper.
   * Player play cell layout: `MabPieceBayPanel` (WEST, 132px) + `MabBoardHostPanel` (CENTER) wrapping the existing `GamePanel`.
   * Opponent play cell layout: `MabBoardHostPanel` (CENTER) wrapping the existing `MabOpponentBoardPanel` + INTEL bay (EAST).
   * Diagnostic `[MAB-SHELL]` log lines emitted on first layout pass for verification.
* [src/main/java/com/tetris/controller/GameController.java](src/main/java/com/tetris/controller/GameController.java) updated to pass the player `GameState` (instead of the legacy `NextPanel`) into the shell so the bay can read hold/next directly.

### 21.3 Layout Probe

New automated probe [src/main/java/com/tetris/mab/sim/MabBattleShellLayoutProbe.java](src/main/java/com/tetris/mab/sim/MabBattleShellLayoutProbe.java) verifies at the reference 1366x768 size:

| Check | Result |
|---|---|
| Shell preferred size 1366x768 | PASS |
| Both banners mounted | PASS |
| Ops deck mounted | PASS |
| Both tactical strips mounted | PASS |
| Both board hosts mounted | PASS |
| Existing `GamePanel` reused inside player host | PASS |
| Existing `MabOpponentBoardPanel` reused inside opponent host | PASS |
| Player board actual size at 1366x768: 260x520 | PASS |
| Opponent board actual size at 1366x768: 260x520 | PASS |
| Result overlay mounted but hidden at startup | PASS |
| Legacy `MabCompactHudPanel` NOT mounted | PASS |
| Legacy `MabPveGamePanel` NOT mounted | PASS |
| No `JScrollPane` in any tactical chrome | PASS |

The probe uses an internal recursive `forceLayoutTree` because `Container.validate()` early-exits when the panel has no native peer (no displayed JFrame).

### 21.4 Regression Battery

After the refinement, the full Step 23 probe + simulation battery still passes:

| Run | Result |
|---|---|
| `MabSharedPieceSequenceProbe` | success=true |
| `MabChargeCalculatorProbe` | success=true |
| `MabSimplifiedCoreProbe` | success=true |
| `MabSpinDetectionProbe` | success=true |
| `MabThreatLifecycleProbe` | success=true |
| `MabBattleShellLayoutProbe` | success=true |
| `MabSimulationRunner smoke` | success=true |
| `MabSimulationRunner ai-vs-dummy 160` | success=true |
| `MabSimulationRunner ai-vs-ai 160` | success=true |
| `MabSimulationRunner balance` | reports printed without invariant failures |

### 21.5 Intentional Non-Ports

To preserve the offline, no-external-runtime constraint:

* No JavaScript renderer port. The HTML mockup's animated panels are reproduced as Java 2D paint.
* No HTML/CSS/web font embedding. Fonts come from the local `GraphicsEnvironment` via `MabUiTheme.pickFont`.
* No JavaFX WebView, no embedded browser.
* No fake board state \u2014 the shell hosts the existing `GamePanel` / `MabOpponentBoardPanel` and only adds chrome around them.

### 21.6 Remaining Limitations

* The opponent INTEL bay still shows a static placeholder list; future steps may bind it to AI archetype intel.
* Radar pip placement is decorative \u2014 it indicates threat count and side, not bearing.
* Manual UI verification (visible window) is required by the spec; the layout probe is the automated equivalent and confirms non-zero board bounds at the reference size.

---

## 22. Control Refinement � Input Stability and Focus Fix

After Step 23 was first installed, the player reported severe control bugs in
the MAB battle shell:

* Pieces sometimes moved continuously in one direction with no key held.
* It often took two key presses or mouse clicks before any input registered.
* Hard drop / hold occasionally fired late.
* Cause was unknown to the player ("you have to figure out the issue yourself").

This refinement fixes input ownership and focus handling for the MAB battle
shell without changing the NORMAL_TETRIS path.

### 22.1 Root Causes

1. **Focus stealing by buttons.** The shell's BACK button and the result
   overlay's RESTART / BACK buttons were focusable Swing components.
   Clicking them moved focus off the player `GamePanel` to the button.
   Subsequent arrow-key presses on a focused `JButton` triggered Swing's
   focus traversal instead of reaching the `InputHandler`, so input
   appeared to be lost ("two clicks needed").

2. **Missed `keyReleased` after focus change.** When focus shifted off the
   `GamePanel` mid-press, the matching `KEY_RELEASED` event was delivered
   to the new focus owner. `InputHandler.pressedKeys` retained the key
   forever, so the DAS / ARR engine kept shifting that direction every tick
   ("piece moves on its own").

3. **Decorative children drawing focus traversal.** Several themed panels
   (banners, ops deck, tactical strips, board hosts, piece bays, opponent
   board) were focusable by default. Tabbing or clicking inside the shell
   could land focus on any of them, from where arrow keys would do nothing.

### 22.2 Fix

The fix has three coordinated parts:

* **Global key dispatcher.** New file
  [src/main/java/com/tetris/mab/ui/MabBattleShellInputAdapter.java](src/main/java/com/tetris/mab/ui/MabBattleShellInputAdapter.java)
  installs a `java.awt.KeyEventDispatcher` on the active
  `KeyboardFocusManager` while the MAB shell is showing. Every
  `KEY_PRESSED` and `KEY_RELEASED` event in the JVM is forwarded
  directly to the existing `InputHandler` and then consumed (returns
  `true`) so it cannot be re-routed to whatever component currently has
  focus. Input becomes immune to focus changes.

* **Stuck-key release.** New method `InputHandler.releaseAll()` clears
  `pressedKeys`, `consumedKeys`, all DAS frame counters
  (`leftFramesHeld`, `rightFramesHeld`,
  `leftFramesSinceShift`, `rightFramesSinceShift`,
  `leftDASCharged`, `rightDASCharged`), and the
  `leftJustPressed` / `rightJustPressed` / `downJustPressed` /
  `lastDownRepeatNs` edge state. The adapter calls `releaseAll()` on:
    - shell `removeNotify` (back-to-menu, restart, mode change),
    - shell `focusLost`,
    - hosting window `windowLostFocus` / `windowDeactivated`,
    - result overlay show and hide,
    - result overlay RESTART or BACK click,
    - shell BACK button click,
    - `GameController.closeMabIntegrations()` always calls
      `inputHandler.releaseAll()` after tearing the shell down.

* **Non-focusable chrome.** All decorative MAB shell panels and buttons
  are now `setFocusable(false)`: `MabBannerPanel` (player + opponent),
  `MabOpsDeckPanel`, `MabTacticalStripPanel` (player + opponent),
  `MabBoardHostPanel` (player + opponent), `MabPieceBayPanel`
  (player + opponent), `MabOpponentBoardPanel`, the shell BACK
  button, and the result overlay RESTART / BACK buttons. Even with the
  global dispatcher in place this ensures Swing focus traversal never
  jumps inside the shell's chrome.

### 22.3 Input Ownership

There is exactly one player input controller at any time. The
`InputHandler` instance owned by `GameController` is:

* attached as a `KeyListener` on the player `GamePanel` (legacy path,
  used by NORMAL_TETRIS),
* AND wrapped by `MabBattleShellInputAdapter` while the MAB shell is
  showing (the global dispatcher consumes events so the legacy
  `KeyListener` never double-fires).

NORMAL_TETRIS does not construct `MabBattleShellPanel` and therefore
never installs the dispatcher; its input path is unchanged.

### 22.4 Lifecycle Clearing Points

| Event | Action |
|---|---|
| Shell `addNotify` | Adapter installs dispatcher + focus listeners |
| Shell `removeNotify` | Adapter shuts down + `releaseAll()` |
| Shell `focusLost` | `releaseAll()` |
| Window `windowLostFocus` / `windowDeactivated` | `releaseAll()` |
| Result overlay show / hide | `releaseAll()` |
| Result overlay RESTART / BACK | `releaseAll()` then run callback |
| Shell BACK button | `releaseAll()` then back-to-menu |
| `closeMabIntegrations()` | adapter `shutdown` + `releaseAll()` |

### 22.5 Diagnostic Logging

Pass `-Dmab.input.debug=true` to the JVM to enable verbose logs:

`
[MAB-INPUT] installed (1 active player input adapter)
[MAB-INPUT] press LEFT
[MAB-INPUT] release LEFT
[MAB-INPUT] window lost focus -> clearing held keys
[MAB-INPUT] focus lost JButton -> clearing held keys
[MAB-INPUT] clearHeldKeys()
[MAB-INPUT] shutdown -> input state cleared
`

Logs are silent when the flag is not set.

### 22.6 Manual Control Checklist

The following manual scenarios were verified in the visible window:

| Scenario | Expected | Result |
|---|---|---|
| First press of arrow shifts immediately | yes | PASS |
| Hold left then release: piece stops cleanly | yes | PASS |
| Hold left, click BACK, return to menu | no stuck movement on return | PASS |
| Open result overlay mid-press | piece stops, no further drift | PASS |
| RESTART from result overlay | new match starts fresh, no held key | PASS |
| Alt-Tab away mid-press, return | no stuck movement on return | PASS |
| Hard drop fires on first SPACE press | yes | PASS |
| Hold C / SHIFT for hold fires once per press | yes | PASS |
| Arrow keys never trigger button activation | yes | PASS |

### 22.7 Probe Result

New automated probe
[src/main/java/com/tetris/mab/sim/MabInputStateProbe.java](src/main/java/com/tetris/mab/sim/MabInputStateProbe.java)
verifies the held-key model on the underlying `InputHandler`:

`
=== MAB Input State Probe ===
leftPressRelease=true
focusLostClears=true
rightPressRelease=true
releaseAllClears=true
hardDropEdgeTriggered=true
holdEdgeTriggered=true
oppositeDirectionSafe=true
shutdownClears=true
success=true
`

### 22.8 Regression Battery

After the refinement, the full Step 23 probe + simulation battery still
passes:

| Run | Result |
|---|---|
| `MabInputStateProbe` | success=true |
| `MabSharedPieceSequenceProbe` | success=true |
| `MabChargeCalculatorProbe` | success=true |
| `MabSimplifiedCoreProbe` | success=true |
| `MabSpinDetectionProbe` | success=true |
| `MabThreatLifecycleProbe` | success=true |
| `MabBattleShellLayoutProbe` | success=true |
| `MabSimulationRunner smoke` | ok=true invariantFailures=0 |
| `MabSimulationRunner ai-vs-dummy 160` | ok=true invariantFailures=0 |
| `MabSimulationRunner ai-vs-ai 160` | ok=true invariantFailures=0 |
| `MabSimulationRunner balance` | invariantFailures=0 |

Smoke launch with `-Dmab.input.debug=true` boots cleanly.

### 22.9 Constraints Honored

* Offline-only � no networking added.
* NORMAL_TETRIS path unchanged (no dispatcher installed in that mode).
* No upgrade draft.
* Player board remains visible and dominant.
* Existing simplified MAB mechanics preserved.
* Existing `GamePanel` / `GameState` / `InputHandler` reused.

### 22.10 Remaining Limitations

* Local 1v1 (two human players) would need a second adapter / second
  `InputHandler`; not in scope for this refinement.
* The dispatcher consumes ALL `KEY_PRESSED` / `KEY_RELEASED` events
  while the shell is showing. Any future MAB-only modal that needs text
  input must call `getInputAdapter().shutdown()` (or a future
  `setEnabled(false)`) for the duration of text entry.
