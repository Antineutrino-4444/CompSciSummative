# Step 24 — Level-Up Upgrade Draft v1 for MAB Battle Shell

## 1. Files added
- `src/main/java/com/tetris/mab/upgrade/draft/MabUpgradeRarity.java` — enum `STANDARD / ADVANCED / CRITICAL`.
- `src/main/java/com/tetris/mab/upgrade/draft/MabUpgradeCategory.java` — enum `POWER / CHARGE / TETRIS_ROUTE / SPIN_ROUTE / DEFENSE / INTEL / TEMPO`.
- `src/main/java/com/tetris/mab/upgrade/draft/MabUpgradeCard.java` — immutable card record (id, displayName, shortName, iconText, category, rarity, descriptions, maxStacks, repeatable, effectTags).
- `src/main/java/com/tetris/mab/upgrade/draft/MabUpgradeInventory.java` — per-participant stacks + accessors (`hasUpgrade`, `getStacks`, `addUpgrade`, `isMaxed`, `hasTag`, `countTagStacks`).
- `src/main/java/com/tetris/mab/upgrade/draft/MabUpgradeDraft.java` — immutable draft (participantId, level, exactly 3 choices).
- `src/main/java/com/tetris/mab/upgrade/draft/MabUpgradeDraftRegistry.java` — 24-card seeded registry across all 7 categories.
- `src/main/java/com/tetris/mab/upgrade/draft/MabUpgradeDraftManager.java` — level-up tracker, deterministic 3-card generator, human draft queue, AI-direct apply, simplified-state sync on apply.
- `src/main/java/com/tetris/mab/upgrade/draft/MabUpgradeEffectResolver.java` — stateless modifier calculator (charge bonuses, route targets, refunds, mitigations, intel deltas).
- `src/main/java/com/tetris/mab/upgrade/draft/MabAiUpgradePicker.java` — deterministic AI auto-picker, difficulty-aware (EASY = random; else priority-sorted).
- `src/main/java/com/tetris/mab/ui/MabUpgradeDraftOverlayPanel.java` — DOCTRINE LOADOUT modal panel (3 cards horizontally, click to commit).
- `src/main/java/com/tetris/mab/sim/MabUpgradeDraftProbe.java` — registry / draft / determinism / pause / max-filter probe.
- `src/main/java/com/tetris/mab/sim/MabUpgradeEffectProbe.java` — resolver / sync probe.

## 2. Files modified
- `src/main/java/com/tetris/controller/GameController.java` — `restart()` rewritten so `R` triggers a full-match restart in MAB PvE (via `mabRestartCallback`) and remains a board reset in `NORMAL_TETRIS`. Refresh timer now polls `mabMatch.tickUpgradeDrafts()` and opens the overlay + upgrade pause when a human draft becomes pending.
- `src/main/java/com/tetris/mab/MutuallyAssuredBlocksMatch.java` — added `matchSeed`, lazy `MabUpgradeDraftManager`, public `getUpgradeDraftManager()`, `tickUpgradeDrafts()`, `applyHumanUpgradeChoice(card)`. Both clear handlers (`processSimplifiedClearForLineClear`, `processSimplifiedClearForLockResult`) now run charge through `MabUpgradeEffectResolver.applyChargeModifiers`. `fireSimplifiedLaunch` applies the Rapid Assembly refund and resets the Tetris Doctrine once-per-cycle flag. Tetris Doctrine grants +1 extra route pip post-ready (once per cycle).
- `src/main/java/com/tetris/mab/ParticipantState.java` — added `upgradeInventory` field + accessor and bookkeeping flags `tetrisDoctrineUsedThisCycle`, `emergencyProtocolsUsed`.
- `src/main/java/com/tetris/mab/clear/MabSimplifiedStrategicState.java` — Tetris/Spin route goals are now per-instance (with setters) plus a `spinRouteKeepsOnePip` flag honoured by `onLaunchFired`.
- `src/main/java/com/tetris/mab/ui/MabBattleShellPanel.java` — mounts `MabUpgradeDraftOverlayPanel` on `MODAL_LAYER`, sizes it in `doLayout`, exposes `showUpgradeOverlay / hideUpgradeOverlay / isUpgradeOverlayVisible`. Both transitions call `inputAdapter.clearHeldKeys()` (Step 23 contract).

## 3. Offline-only confirmation
No new networking, no socket / HTTP client, no online-only gating. All upgrade RNG is seeded from the local `matchSeed` already used for the shared 7-bag.

## 4. Design philosophy
Upgrades are picked when a participant levels up (`ScoreSystem.getLevel()`). The match pauses both boards via the existing `MatchPhase.UPGRADE_PAUSE` plumbing, the player picks one of three cards, the AI auto-picks for itself in the same tick, and play resumes. There is no upgrade-points economy, no DEFCON-gated shop, and no separate JFrame. Cards are real gameplay modifiers — not cosmetic filler.

## 5. Old upgrade system status
The legacy `com.tetris.mab.upgrade` action-code system is left untouched. The new draft package lives under `com.tetris.mab.upgrade.draft` and does not depend on it. PvE still functions if the legacy package is bypassed entirely (it is, in this step).

## 6. Level-up trigger logic
Each refresh tick (≈ 100 ms) the controller calls `mabMatch.tickUpgradeDrafts()`. Inside, `MabUpgradeDraftManager.checkLevelUps(playerA, playerB)` walks both participants and compares each one's current `ScoreSystem.getLevel()` against the per-participant `lastDraftedLevel` (initialised to 1). Each missed level produces one draft. Player B (AI) drafts are returned to the manager directly and immediately auto-resolved via `MabAiUpgradePicker`. Player A drafts go onto an internal queue. The first queued human draft is returned to the controller, which opens the overlay and the upgrade pause.

## 7. Draft generation rules
Seed: `matchSeed ^ pidOrdinal*0x9E3779B97F4A7C15L ^ level*0xC6BC279692B5C323L`. Same seed + pid + level always produces the same 3 cards. Maxed cards (non-repeatable already owned, or repeatable at cap) are filtered out before tier sampling. If the resulting pool is too small to fill 3 distinct slots, the manager falls back to non-maxed registry cards (and only allows duplicates as an absolute last resort, to honour the always-3-choices contract). Rarity weights:

| Level | STANDARD | ADVANCED | CRITICAL |
|-------|----------|----------|----------|
| ≤ 3   | 85       | 15       | 0        |
| ≤ 6   | 65       | 30       | 5        |
| ≥ 7   | 50       | 35       | 15       |

## 8. Card registry (24 cards across 7 categories)
- **CHARGE**: efficient_reactor (REPEAT max 3), b2b_amplifier, combo_capacitor, perfect_clear_battery
- **TETRIS_ROUTE**: tetris_doctrine, fast_fuse, clean_well_logistics
- **SPIN_ROUTE**: spin_doctrine, tst_program, counterspin_training, spin_launch_crew
- **DEFENSE**: intercept_crews, shelters, emergency_protocols, ablative_spin
- **POWER**: heavy_warhead, penetrator_package, dirty_payload
- **INTEL**: early_warning_radar, threat_tracking, signal_analysis
- **TEMPO**: rapid_assembly, retaliation_doctrine, dead_hand_drill

## 9. Inventory model
Each `ParticipantState` owns its own `MabUpgradeInventory`. There is no global / static state — inventories are fully isolated between participants and reset every match (because each match constructs new participant states).

## 10. Implemented effect details (selected highlights)
- `efficient_reactor`: +1 charge per stack on every scoring clear.
- `b2b_amplifier`: re-multiplies B2B charge from the calculator's 1.25 baseline up to 1.40 (ratio 1.12 applied on top of the base).
- `combo_capacitor`: +2 charge on combo ≥ 4.
- `perfect_clear_battery`: +8 charge on perfect clear.
- `clean_well_logistics`: +3 charge on non-spin Tetris.
- `spin_doctrine`: +2 charge on any spin.
- `tst_program`: +8 charge on a 3-line spin.
- `tetris_doctrine`: once per launch cycle, a post-ready Tetris counts as +1 extra route pip.
- `fast_fuse`: Tetris route target 4 → 3.
- `spin_launch_crew`: keep 1 spin pip after a spin launch fires.
- `intercept_crews`: +1 intercept strength delta.
- `shelters`: -1 incoming garbage when stack is in upper third.
- `emergency_protocols`: -2 once per match (handled by `emergencyProtocolsUsed` flag).
- `ablative_spin`: marks ablative spin damage reduction active.
- `heavy_warhead`, `dirty_payload`: each adds +1 outgoing garbage on launch.
- `penetrator_package`: +1 penetration delta.
- `rapid_assembly`: +10 charge refund immediately after firing a launch.
- `retaliation_doctrine`: +20 charge on receiving impact.
- `dead_hand_drill`: marks the dead-hand-on-topout flag.
- `early_warning_radar`, `threat_tracking`, `signal_analysis`: extra warning ticks / +1 charge on opponent ready / +1 intercept charge.

## 11. AI picker
`MabAiUpgradePicker.pick(draft, difficulty, seed)`:
- `EASY` → uniform random pick.
- Other difficulties → sort by `(rarity desc, category priority CHARGE > DEFENSE > TETRIS_ROUTE > SPIN_ROUTE > POWER > TEMPO > INTEL, id asc)` and take the first.

## 12. Overlay design
- Translucent dark dim (`Color(2,6,11,200)`) over the entire shell.
- Centered modal with cyan border, "DOCTRINE LOADOUT" title, "LEVEL n — SELECT ONE DOCTRINE — BOTH BOARDS PAUSED" subtitle.
- Three horizontal cards (`260 × 320` each) with rarity-coloured border, large display name, category::rarity tag, one-line description, and a stack indicator for repeatable cards.
- Cards are click-to-commit. Hover highlights. No JScrollPane, no focusable buttons.

## 13. Input / focus handling
- Overlay panel and all card sub-panels are non-focusable.
- `MabBattleShellPanel.showUpgradeOverlay` / `hideUpgradeOverlay` both call `inputAdapter.clearHeldKeys()`, matching the Step 23 contract used by the result overlay.
- Card pick callback is single-shot (`pickCallback = null` immediately) so a fast double-click can't fire twice.

## 14. Pause / resume behaviour
On overlay show the controller calls `mabMatch.openUpgradePause("upgrade_draft")`, which routes to `enterUpgradePause` and pauses both `GameState`s. On pick the controller calls `applyHumanUpgradeChoice(card)`, then `hideUpgradeOverlay()`, then `closeUpgradePause("upgrade_draft")` to resume both boards.

## 15. Event / logging
- `MAB_UPGRADE_SELECTED` — human pick committed.
- `MAB_AI_UPGRADE_SELECTED` — AI auto-pick committed.
- Both include `cardId`, `category`, `rarity` metadata.

## 16. Probe results
- `MabUpgradeDraftProbe` — registry size 24, three-choice, no-duplicates, deterministic seeding (compared by id), AI pick valid, applyDirect stores upgrade, openUpgradePause/closeUpgradePause works, maxed-not-offered, no no-op cards. **success=true**
- `MabUpgradeEffectProbe` — efficient reactor +1/+2 stacks, B2B amplifier 8 → 9, fast fuse target 3, spin launch crew keeps pip, intercept crews delta = 1, shelters mitigation ≥ 1, rapid assembly refund = 10, retaliation doctrine = 20, syncSimplifiedStateConfig pushes targets, empty inventory is a true no-op. **success=true**
- All previously-green probes (`MabSimplifiedCoreProbe`, `MabChargeCalculatorProbe`, `MabSpinDetectionProbe`, `MabSharedPieceSequenceProbe`, `MabThreatLifecycleProbe`, `MabActionCodeInputProbe`, `MabBattleShellLayoutProbe`, `MabInputStateProbe`) still pass.

## 17. Sim results
- `MabSimulationRunner smoke` — exits 0; strategic clock advances normally with the new resolver in the hot path.

## 18. Manual checklist
Launch with `java -Dmab.input.debug=true -cp target\classes com.tetris.Main`, choose MAB PvE, clear lines until level 2 — overlay should appear, both boards visibly paused, click any card → overlay disappears, both boards resume, no stuck movement. Press R during PvE → full match restart. Press R during normal Tetris → board reset (unchanged). Press R during a future MAB PvP launch — currently no-op since GameController has no PvP launch mode.

## 19. Remaining limitations
- AI picker is greedy (priority sort) — no awareness of board state or opponent inventory.
- No upgrade-history HUD: the overlay shows current stacks for repeatable cards, but there's no in-game "upgrades I own" panel between drafts.
- No keyboard navigation for the overlay (mouse-only commit). Held movement is cleared but the overlay doesn't accept arrow keys.
- `dead_hand_drill` and several intel cards are flagged on the inventory but their behavioural hooks are minimal — they'll deepen in a follow-up step.

---

## Step 24 Refinement — Upgrade Overlay Readability and Battle-Shell Styling

### 1. User complaint
- Overlay UI was inconsistent with the cold-war command-room battle shell.
- Text colors were poor: description was TEXT_MUTED (dim gray at #909AAE), effectively unreadable on the dark card.
- Description font was TERM_SMALL (10 pt) — far too small.
- Icon text (⊕ I, ⊿ II, ⊙ III, etc.) was the primary visual identifier — confusing abstract glyphs that convey nothing to new players.
- Category/rarity tag was one dim TEXT_FAINT line with `::` separating enum names — no hierarchy.
- Card was 260×320 — tight for the existing font sizes.
- No matching category color coding.

### 2. Root cause of clipping / unreadability
The original `buildCard` painted the description as a JLabel with:
```
"<html><body style='width:200px'>" + text + "</body></html>"
```
at TERM_SMALL (10 pt) in TEXT_MUTED (`#909AAE`). At 10 pt monospace on a `#181F2E` card the contrast was marginal and the text was too small to read at arm's length. The 200 px HTML body width left unused card space while still potentially clipping if the panel was narrower than expected. There was no visual separation between elements so title and description merged into noise.

### 3. New card layout (top → bottom)
```
[ CHARGE          STANDARD ]   ← header strip: category word (stencil) + rarity badge (right)
  CHG                          ← short badge (dimmed, term-tiny)
 ─────────────────────────     ← thin divider (GRID_LINE_HI)

  Efficient Reactor            ← title (STENCIL_MID 16pt, TEXT_BRIGHT)

  Clears gain +1 charge.       ← description (TERM_MED 13pt, #C4D4E8)
  Stacks with each copy.

  [vertical glue]

  STACK 0 / 3                  ← status tag (TERM_TINY, amber for repeatable)
```

### 4. New text hierarchy
| Region        | Font             | Size | Color              |
|---------------|------------------|------|--------------------|
| Category word | STENCIL_SMALL    | 11pt | Category color     |
| Rarity badge  | TERM_TINY        |  9pt | Rarity color       |
| Short badge   | TERM_TINY        |  9pt | Dimmed cat color   |
| Card title    | STENCIL_MID      | 16pt | TEXT_BRIGHT        |
| Description   | TERM_MED         | 13pt | #C4D4E8 (readable) |
| Status tag    | TERM_TINY        |  9pt | Amber or ghost     |

### 5. New color hierarchy
- STANDARD rarity border: INFO (`#80B8FF`) — soft blue
- ADVANCED rarity border: C_AMBER (`#FFB300`) — amber
- CRITICAL rarity border: C_MAGENTA (`#FF3DC9`) — magenta
- CHARGE category: C_CYAN
- TETRIS_ROUTE category: C_GREEN
- SPIN_ROUTE category: `#70A8FF`
- DEFENSE category: `#60D090`
- POWER category: C_RED
- INTEL category: `#B080FF`
- TEMPO category: C_MAGENTA
- Card BG: CARD_BG (`#181F2E`)
- Hover BG: `#1E2C44`
- Modal BG: SHELL_BG (`#070C14`)
- Modal border: C_CYAN 2px

### 6. New category badge / icon language
Abstract glyphs (⊕ I, ⊿ II, ⊙ III, ◈ II, etc.) are completely removed.
Each card now shows the category as a readable English word **first** (CHARGE, TETRIS, SPIN, DEFENSE, POWER, INTEL, TEMPO) in the stencil font, with a small 3-letter badge below it (CHG, TET, SPN, DEF, PWR, INT, TMP) for quick visual scanning. The player never needs to decode a symbol.

### 7. Wrapping / clipping prevention
Description uses `JLabel` with:
```java
"<html><body style='width:240px'>" + text + "</body></html>"
```
at TERM_MED (13 pt). The 240 px HTML body width is 28 px narrower than the card's 268 px content area, guaranteeing the HTML engine has room to wrap without touching the border. The component's preferred width is determined by the HTML renderer, not by a fixed `setPreferredSize` call, so it shrinks correctly. The title also uses the same `width:240px` HTML wrap for safety.

Card preferred size raised to **300×340** (`setPreferredSize` + `setMinimumSize(270, 300)`). At three cards + 16 px gaps + 30 px side padding the total modal width is **996 px** — well within 1366.

### 8. Future upgrade-list safety
- HTML wrap width (240 px) is hard-coded relative to the card interior, not to today's text lengths.
- Title wraps at the same 240 px limit (max ~2 lines at 16 pt stencil).
- Description wraps naturally at 240 px; TERM_MED (13 pt) at 240 px fits ~3 lines for up to ~130 characters.
- Status tag is a single TERM_TINY line — always fits.
- No fixed Y-offsets anywhere; all positions come from BoxLayout's normal flow.
- The `MabUpgradeOverlayLayoutProbe` validates a 120-character future description and confirms it still passes all layout checks.

### 9. Input / focus handling
Unchanged from original Step 24 contract:
- Overlay panel and all card panels are `setFocusable(false)`.
- `MabBattleShellPanel.showUpgradeOverlay` / `hideUpgradeOverlay` both call `inputAdapter.clearHeldKeys()`.
- Card pick callback is single-shot (nulled immediately on first click).
- Hover/exit events only repaint background + border; no focus grab.

### 10. Probe results

**MabUpgradeOverlayLayoutProbe** (new):
```
=== MAB Upgrade Overlay Layout Probe ===
PASS  overlayFits=true (996x478)
PASS  threeCardsVisible=true
PASS  cardBoundsNonZero=true
PASS  descriptionsInsideCards=true
PASS  titlesInsideCards=true
PASS  noScrollPane=true
PASS  noWhitePanels=true
PASS  futureTextSafe=true
success=true
```

**MabBattleShellLayoutProbe** (updated with upgrade overlay checks):
All 32 checks PASS, including:
```
PASS  upgrade overlay mounted
PASS  upgrade overlay hidden at startup
PASS  upgrade overlay visible after show
PASS  upgrade overlay preferred size fits 1366x768: 996x478
PASS  upgrade overlay card row found
PASS  no JScrollPane in card row
PASS  upgrade card preferred sizes >= 270x300
PASS  no white backgrounds in upgrade overlay
PASS  upgrade overlay hidden after dismiss
PASS  player board still mounted after overlay hide
PASS  input adapter accessible
success=true
```

All previously-green probes remain green:
- `MabUpgradeDraftProbe` — success=true
- `MabUpgradeEffectProbe` — success=true
- `MabInputStateProbe` — success=true
- `MabSharedPieceSequenceProbe` — success=true
- `MabChargeCalculatorProbe` — success=true
- `MabSimplifiedCoreProbe` — success=true
- `MabSpinDetectionProbe` — success=true
- `MabThreatLifecycleProbe` — success=true

### 11. Simulation results
- `MabSimulationRunner smoke` — success=true
- `MabSimulationRunner ai-vs-dummy 160` — success=true
- `MabSimulationRunner ai-vs-ai 160` — success=true
- `MabSimulationRunner balance` — all 4 profiles run, no invariant failures

### 12. Manual UI checklist
Launch with `java -Dmab.input.debug=true -Dmab.debug.upgrades=true -cp target\classes com.tetris.Main`:
1. Start MAB PvE — shell mounts, both boards visible. ✓ (verified by layout probe)
2. Trigger upgrade draft (reach level 2 or use debug flag).
3. Confirm overlay appears over battle shell with dark translucent veil.
4. Confirm "DOCTRINE LOADOUT" title in cyan stencil font.
5. Confirm subtitle shows level number and "BOTH BOARDS PAUSED".
6. Confirm three cards side-by-side, each with category word (CHARGE / DEFENSE / etc.).
7. Confirm card title is readable in bright white stencil font.
8. Confirm rarity badge is color-coded (blue / amber / magenta).
9. Confirm description text wraps inside the card (TERM_MED 13pt).
10. Confirm no description overflows its card border.
11. Confirm hover brightens border and darkens card background.
12. Confirm click selects card and closes overlay.
13. Confirm gameplay resumes after selection.
14. Confirm no stuck movement after close.
15. Confirm no default white Swing panels anywhere.
16. Confirm NORMAL_TETRIS mode is unchanged.

### 13. Remaining limitations
- No keyboard navigation for the overlay (mouse-only). This is intentional — no text fields, no focus issues.
- The "LOADING" flash on click is not implemented; the overlay simply dismisses. Can be added later.
- The battle-shell event log (DOCTRINE LOADED: FAST FUSE) is logged to console but not yet shown as an on-screen event. The ops deck event stream will surface this in a future step.
