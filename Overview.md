# Modern Tetris / Mutually Assured Blocks - Complete Game Overview

This file is a detailed overview of the entire game as it exists in this
repository: the normal modern Tetris game, the Mutually Assured Blocks (MAB)
PvE battle mode, the educational Nuke Builder, the UI layers, AI systems,
upgrade systems, simulation/debug tools, and the code architecture that holds
everything together.

The short version: this project is a Java 17 Swing desktop game built with
Maven. It began as a faithful modern Tetris implementation and has grown into a
hybrid Tetris strategy game called Mutually Assured Blocks, where both sides
play Tetris boards while line clears, spins, Tetrises, upgrades, intercepts,
and launch timers drive an abstract nuclear-strategy battle layer.

## 1. Game Identity

### 1.1 Name and Modes

The project currently contains three major player-facing experiences:

1. Normal Modern Tetris.
   - A single-player modern Tetris game.
   - Uses guideline-style pieces, SRS kicks, hold, ghost piece, next queue,
     lock delay, scoring, combos, back-to-back, perfect clears, and T-spins.

2. Mutually Assured Blocks PvE.
   - A local/offline battle mode.
   - Human Player A plays on the left.
   - Computer Player B plays on a visible opponent board on the right.
   - Both sides use shared deterministic piece generation so the battle feels
     fair and readable.
   - Tetris performance feeds a simplified strategic layer: charge a nuke,
     complete a launch route, launch, intercept incoming threats with spins,
     absorb impacts, draft upgrades, and try to top out the opponent.

3. Nuke Builder.
   - An educational, conceptual builder UI separate from the live MAB strategic
     presets.
   - Lets the user assemble a schematic design from slots and parts.
   - Shows conceptual yield, warnings, historical analogs, and rough effect
     summaries.
   - It is explicitly framed in code comments as public, non-engineering,
     pedagogical material.

### 1.2 What the Game Is Not

- It is not networked.
- It is not a real-time online multiplayer client.
- It does not currently implement public matchmaking, rollback, sockets, or
  remote player synchronization.
- Local PvP names and hooks exist in some MAB APIs, but the current launcher
  path is focused on offline PvE against an AI.
- The old action-code and legacy upgrade systems still exist, but the current
  normal MAB PvE loop uses the simplified clear-to-charge, charge-to-route,
  route-to-launch model instead.

## 2. Project Runtime and Entry Points

### 2.1 Build System

- The project uses Maven.
- Source is under `src/main/java`.
- Java version target is Java 17.
- The `pom.xml` includes:
  - Maven compiler plugin.
  - Maven exec plugin.
  - Maven shade plugin for jar packaging.
  - Maven surefire plugin.
  - JUnit 5 as a test-scope dependency.

### 2.2 Launch Scripts and Main Class

The main entry point is:

- `src/main/java/com/tetris/Main.java`

The `Main` class:

- Parses an optional starting level argument.
- Clamps start level behavior through the controller/config path.
- Sets Swing to the native look and feel when possible.
- Starts the UI on the Swing event-dispatch thread.
- Creates a `StartMenu`.
- Wires normal Tetris and MAB PvE controller factories into the menu.

Top-level helper files include:

- `run.bat`
- `run.sh`
- `pom.xml`
- `README.md`
- `Upgrades.md`
- `Step1.md` through `Step24.md`

The `Step*.md` files document the staged development history of the MAB systems.
The most relevant later steps are:

- `Step20.md`: visible opponent board and strategic HUD work.
- `Step21.md`: simplification pivot away from action-code memorization.
- `Step22.md`: all-spin recognition and dramatic stage UI.
- `Step23.md`: battle shell UI overhaul and threat lifecycle fixes.
- `Step24.md`: level-up upgrade draft system.

## 3. Main Menu and Screen Flow

### 3.1 StartMenu

The main launcher is:

- `com.tetris.view.StartMenu`

It is a decorated, resizable Swing `JFrame` with a `CardLayout`.

Major cards:

- Main menu.
- Settings.
- Nuke Builder.
- MAB selection.
- MAB PvE setup.
- Embedded game card.

Main menu actions include:

- Start MAB.
- Single Player.
- Nuke Builder.
- Settings.
- Quit.

The MAB selection currently presents:

- PvE vs AI.
- PvP marked as coming soon/reserved.
- Back.

The MAB PvE setup screen lets the player choose:

- Starting level, clamped to 1 through 20.
- AI archetype.
- AI difficulty.
- Balance profile.
- Whether to show the MAB debug HUD.

### 3.2 Embedded Game View

Normal Tetris can be hosted inside the launcher through:

- `com.tetris.view.GameView`

`GameView` contains:

- A top toolbar with Back and Settings.
- A left `SidePanel`.
- A central `GamePanel`.
- A right `NextPanel`.
- A repaint timer.
- Focus management for keyboard input.

MAB PvE uses the same underlying `GamePanel` for the player's board, but the
panel is re-hosted inside the MAB battle shell.

### 3.3 Standalone Frame

`com.tetris.view.MainFrame` still exists as a standalone `JFrame` wrapper for
the normal game view. The current launcher path mostly uses embedded cards.

## 4. Normal Modern Tetris

### 4.1 Board Dimensions

The board model is:

- `com.tetris.model.Board`

Board constants:

- Width: 10 columns.
- Visible height: 20 rows.
- Buffer height: 4 rows.
- Total internal height: 24 rows.

The visible board is the standard 10x20 playfield. The extra 4-row buffer above
the visible field allows pieces to spawn and rotate naturally before they enter
the visible area.

Board cells are stored as colors:

- `null` means empty.
- A non-null `Color` means a locked block occupies the cell.

### 4.2 Tetrominoes

Tetromino logic is represented by:

- `com.tetris.model.Tetromino`
- `com.tetris.model.TetrominoType`
- `com.tetris.model.Position`

Piece types:

- I
- O
- T
- S
- Z
- J
- L

Each type owns its rotation-state cell offsets and guideline-style color.
Pieces are immutable: movement and rotation return new `Tetromino` objects
rather than mutating the old one in place.

Spawn behavior:

- Pieces spawn centered near the top.
- Spawn Y is based on the buffer height.
- The game checks for block-out when a new piece cannot spawn validly.

### 4.3 Randomizer

Piece generation uses:

- `com.tetris.model.BagRandomizer`

The game uses modern 7-bag randomization:

- Each bag contains one of each tetromino.
- The bag is shuffled.
- Pieces are drawn from the bag until empty.
- Then a new bag is shuffled.

MAB can replace the normal piece source with shared deterministic streams so
both participants get matching piece sequences.

### 4.4 Movement

Player actions include:

- Move left.
- Move right.
- Soft drop.
- Hard drop.
- Rotate clockwise.
- Rotate counterclockwise.
- Rotate 180 degrees.
- Hold.
- Pause/resume.
- Reset.
- Open settings.
- Toggle developer console.

Default controls from the README/settings path:

| Action | Default key |
| --- | --- |
| Move left | Left arrow |
| Move right | Right arrow |
| Soft drop | Down arrow |
| Hard drop | Space |
| Rotate clockwise | Up arrow |
| Rotate counterclockwise | Z |
| Rotate 180 | A |
| Hold | C / Shift |
| Pause/resume | P / Escape |
| Reset | R |
| Settings | F1 |

### 4.5 Input Handler

Input is managed by:

- `com.tetris.controller.InputHandler`

Important behavior:

- Implements `KeyListener`.
- Drains pending key events before processing movement so old release events do
  not produce an extra movement step.
- Ignores Windows auto-repeat duplicate `keyPressed` events.
- Handles single-fire actions such as hard drop, rotate, hold, pause, reset,
  settings, and console toggle.
- Maintains held-key state for continuous movement and soft drop.
- Provides `releaseAll()` to clear stuck keys after focus loss or overlay
  transitions.

Handling defaults:

- DAS: 167 ms.
- ARR: 33 ms.
- DCD: 0.
- SDF: 6x.
- Lock delay: 500 ms.
- Max lock resets: 15.

The input model uses frame-like timing based around a 16 ms interval for
DAS/ARR/SDF calculations, even though the controller's physics timer runs more
frequently.

Special input cases:

- ARR 0 moves as far as possible in one frame.
- SDF 0 allows very fast downward movement up to a capped number of drops per
  frame.
- IRS/IHS are implemented as tap mode:
  - IRS: rotate input can apply when a new piece spawns.
  - IHS: hold input can apply when a new piece spawns.

### 4.6 Rotation and SRS

Rotation data and kicks are in:

- `com.tetris.model.SRSData`

The game implements Super Rotation System style kick testing:

- Normal tetrominoes use standard kick tables.
- I piece uses its own kick table.
- O piece has no meaningful kick sequence.
- Rotations test kick offsets in order.
- The first valid kicked position is accepted.

`GameState.tryRotation` is responsible for rotation attempts and kick testing.

### 4.7 Hold

Hold behavior:

- One piece can be stored in the hold slot.
- Hold can be used once per active piece.
- If the hold slot is empty, the current piece moves to hold and the next piece
  spawns from the queue.
- If the hold slot is occupied, the current piece swaps with the held piece.
- The hold-used flag resets after the piece locks.

The hold UI dims the held piece when hold has already been used for the current
piece.

### 4.8 Ghost Piece

The board can compute a ghost position:

- `Board.getGhostPosition`

The ghost is rendered under the active piece and shows where the piece will land
if hard dropped.

### 4.9 Lock Delay

Lock delay behavior lives in:

- `com.tetris.model.GameState`

Core rules:

- When a piece touches the stack/floor and can no longer fall, lock delay starts.
- The default delay is 500 ms.
- Movement and rotation can reset lock delay.
- There is a maximum number of lock resets, default 15.
- When delay expires or reset count is exhausted, the piece locks.
- Hard drop locks immediately.

### 4.10 Gravity

Gravity is level-based and follows the formula documented in the README:

```text
(0.8 - (level - 1) * 0.007) ^ (level - 1)
```

The score system increases level by total line clears:

- Level = total lines cleared / 10 + 1.
- The level never drops below the configured starting level.

### 4.11 Scoring

Scoring is implemented by:

- `com.tetris.model.ScoreSystem`

Base line-clear scores, multiplied by level:

| Clear | Base score |
| --- | ---: |
| Single | 100 |
| Double | 300 |
| Triple | 500 |
| Tetris / Quad | 800 |

T-spin scores:

| T-spin type | Lines | Base score |
| --- | ---: | ---: |
| T-spin mini | 0 | 100 |
| T-spin mini single | 1 | 200 |
| T-spin mini double | 2 | 400 |
| Full T-spin no-line | 0 | 400 |
| Full T-spin single | 1 | 800 |
| Full T-spin double | 2 | 1200 |
| Full T-spin triple | 3 | 1600 |

Back-to-back:

- Difficult clears include Tetrises and spins.
- Back-to-back difficult clears receive a 1.5x score multiplier.
- The B2B chain counter is displayed in the HUD.

Combo:

- Combo bonus is `50 * combo * level`.
- Combo resets to -1 on locks that do not clear lines and are not scoring spin
  clears.
- Combo is displayed in the HUD.

Perfect clear:

| Perfect clear type | Bonus |
| --- | ---: |
| Single perfect clear | 800 |
| Double perfect clear | 1200 |
| Triple perfect clear | 1800 |
| Tetris perfect clear | 2000 |
| Back-to-back Tetris perfect clear extra | 3200 |

Drop scoring:

- Soft drop: 1 point per cell.
- Hard drop: 2 points per cell.

Other tracked stats:

- Total score.
- Level.
- Total lines cleared.
- Pieces placed.
- Elapsed time.
- Pieces per second.
- Combo.
- Back-to-back chain.

### 4.12 T-spins and All-spins

Normal scoring has T-spin detection:

- T-spin full.
- T-spin mini.
- Corner checks around the T piece.
- Front-corner logic for distinguishing full vs mini cases.
- Last placement input matters; rotations make a piece spin-eligible.

MAB adds all-spin detection:

- Implemented through `com.tetris.model.SpinDetector`.
- A non-T piece can count as a spin if it satisfies the immobile spin rule.
- The immobile rule checks whether the piece cannot move left, right, or up.
- 0-line spins count for MAB routing and intercept logic.

This means in MAB:

- T-spins count.
- T-spin minis count.
- I/J/L/S/Z immobile spins count.
- Spin triples and 0-line spins can matter strategically.

### 4.13 Line Clearing

`Board.clearLines`:

- Detects full rows.
- Stores cleared row indices.
- Stores cleared row colors for animations.
- Compacts the board downward.
- Inserts empty rows at the top.

`GameState.lockPiece`:

- Locks the current piece into the board.
- Fires piece lock events.
- Checks lock-out.
- Clears lines.
- Checks perfect clear.
- Updates score.
- Fires line clear and detailed lock-result events.
- Spawns the next piece.

### 4.14 Top-out Conditions

The event system distinguishes top-out causes:

- Block-out: a newly spawned piece cannot enter.
- Lock-out: a locked piece ends completely in the buffer zone.
- Garbage overflow: garbage insertion or garbage lifting causes overflow or
  invalid active-piece positioning.

These are represented by:

- `GameEventListener.TopOutReason.BLOCK_OUT`
- `GameEventListener.TopOutReason.LOCK_OUT`
- `GameEventListener.TopOutReason.GARBAGE_OVERFLOW`

### 4.15 Garbage Insertion

Garbage exists for MAB impacts and can also be called directly through the
model:

- `GameState.insertGarbage`
- `GameState.insertGarbagePattern`
- `Board.insertGarbageRows`
- `Board.insertGarbageRows(List<GarbageRowPattern>, Color)`

Garbage can be:

- Single-hole rows.
- Patterned rows with multiple holes.
- Fully solid rows if the hole list is empty.
- Tagged by source, such as nuke, radiation, MIRV, grace wave, or debug source.

When garbage is inserted:

- Existing rows are pushed upward.
- The active piece is lifted by the row count.
- Overflow can top out the player.
- Collision after lift can also top out the player.

## 5. Normal Game UI

### 5.1 GamePanel

The central board renderer is:

- `com.tetris.view.GamePanel`

It renders:

- Board background.
- Grid.
- Locked blocks.
- Active piece.
- Ghost piece.
- 3D/beveled block style through `BlockRenderer`.
- Clear animations.
- Flashing rows.
- Shard particles after clears.
- Center overlays for pause/game over.
- MAB action splash text where relevant.

### 5.2 SidePanel

The left HUD is:

- `com.tetris.view.SidePanel`

It shows:

- Hold label.
- Held piece preview.
- B2B badge.
- Combo badge.
- Score.
- Level.
- Lines.
- Time.
- Pieces.
- PPS.

### 5.3 NextPanel

The right HUD is:

- `com.tetris.view.NextPanel`

It shows:

- Next-piece queue.
- Default control hints.
- Mini tetromino drawings.

The default preview count is 5.

### 5.4 Theme and Components

Shared visual helpers:

- `com.tetris.view.theme.Theme`
- `com.tetris.view.theme.Components`
- `com.tetris.view.theme.BlockRenderer`

The normal UI uses:

- Dark background colors.
- Cyan accent.
- Amber highlight.
- Success/warn/danger colors.
- Sans and monospaced font presets.
- Shared button styles.
- Shared borders and spacing.
- Solid, ghost, and flash block-renderer styles.

### 5.5 SettingsPanel

Settings UI:

- `com.tetris.view.SettingsPanel`

Settings model:

- `com.tetris.model.Settings`

Persistent settings are stored under:

```text
{user.home}/.modern-tetris/settings.properties
```

Settings categories:

- Handling.
- Controls.
- Visual.
- Gameplay.

Default settings include:

- DAS 167 ms.
- ARR 33 ms.
- DCD 0.
- SDF 6.
- Lock delay 500 ms.
- Max lock resets 15.
- Preview count 5.
- IRS/IHS tap mode.
- Grid opacity 0.1.
- Board opacity 0.85.
- Ghost opacity 0.55.
- Default key bindings.

The settings UI supports keyboard navigation and key rebinding.

### 5.6 Developer Console

Developer console:

- `com.tetris.view.DevConsolePanel`

It is a matrix-green overlay panel with:

- Command input.
- History navigation.
- Built-in commands such as clear/close/help behavior.
- Controller-provided command dispatch.

The controller currently exposes a useful `debug` command that reports:

- Timing.
- Game state.
- MAB state if active.
- Memory information.

## 6. Educational Nuke Builder

### 6.1 Purpose

The Nuke Builder is:

- `com.tetris.view.NukeBuilderDialog`
- `com.tetris.model.nuke.NukeDesign`
- `com.tetris.model.nuke.NukeSlot`
- `com.tetris.model.nuke.NukePart`

It is a conceptual, educational builder. It is separate from the live MAB
strategic nuke presets in `com.tetris.mab.nuke`.

### 6.2 UI Structure

The builder is a Swing panel with:

- Slot/stage selector.
- Parts palette filtered by active slot.
- Cross-section schematic.
- Info/stat panel.
- Reset behavior.
- Embedded-host close/back behavior.

The builder can be embedded inside the `StartMenu` card layout.

### 6.3 Slots

The conceptual slot catalog includes:

- Weapon Configuration.
- Fissile Material.
- Tamper / Reflector.
- Neutron Initiator.
- Implosion System.
- Boost Gas.
- Fusion Secondary.
- Casing.
- Safety.
- Delivery.

For two-stage configurations, it also exposes a fusion sub-builder with:

- Stage selection.
- Fuel choice.
- Pusher choice.
- Channel filler choice.
- Spark plug choice.
- Stage count.

### 6.4 Derived Builder Outputs

The builder's model computes:

- A rough conceptual yield estimate.
- A complexity score.
- Compatibility warnings.
- Historical analog text.
- Effects summary.
- Approximate severe-damage radius.

The code comments repeatedly identify these as public, coarse, pedagogical
estimates rather than engineering data.

### 6.5 Relationship to MAB

The Nuke Builder and MAB nuke presets are separate systems:

- Nuke Builder: mutable educational slot/part editor in `com.tetris.model.nuke`.
- MAB nuke system: immutable strategic designs in `com.tetris.mab.nuke`.

There is also a `NukeBuilderAdapter` in the MAB nuke package, which can translate
builder-like specifications into MAB-style strategic design properties. The main
PvE battle path currently relies on the MAB default design factory and match
state rather than forcing the Nuke Builder screen into every match.

## 7. GameController

### 7.1 Role

The central runtime coordinator is:

- `com.tetris.controller.GameController`

It owns:

- A `GameState`.
- An `InputHandler`.
- A normal `GameView` or standalone `MainFrame`.
- Timers.
- Developer console integration.
- MAB match integration.
- MAB visible opponent state.
- MAB battle shell.
- MAB AI drivers and refresh loops.

### 7.2 Timers

The controller uses:

- A physics timer around 8 ms, about 120 Hz.
- A render timer matched to display refresh where possible.
- MAB refresh timers.
- MAB visible-board AI tick timers.

Render loop behavior:

- Processes input.
- Applies IRS/IHS for newly spawned pieces.
- Repaints the view.
- Tracks FPS.

Physics loop behavior:

- Advances the `GameState`.
- Applies gravity.
- Handles lock delay.
- In MAB mode, also keeps battle state refreshed.

### 7.3 Normal vs Embedded Start

Controller start paths:

- `start()`: standalone frame.
- `startEmbedded(onExit)`: embedded view/card mode.

In normal Tetris:

- Reset restarts the single board.
- Pause only affects the single board.

In MAB PvE:

- Reset restarts the full match flow.
- Escape toggles match pause/resume with debounce.
- Pause/resume applies to both participants.
- Result overlay appears at match end.

### 7.4 MAB Integration Setup

When a MAB PvE controller starts:

- It creates a second `GameState` for Player B.
- It constructs a shared MAB match.
- It starts the match.
- It creates the player-facing HUD controller.
- It creates the visible opponent board.
- It creates the visible board AI driver.
- It optionally opens companion/debug HUDs.
- It mounts the `MabBattleShellPanel`.
- It wires the global battle-shell input adapter.

The match seed is derived from `System.nanoTime()` and a constant, then used to
build deterministic match behavior such as shared piece streams and drafts.

## 8. Event System

### 8.1 GameEventListener

The event bridge is:

- `com.tetris.events.GameEventListener`

It defines no-op default listener hooks:

- `onPieceSpawned`
- `onPieceMoved`
- `onPieceLocked`
- `onPieceLockedDetailed`
- `onLinesCleared`
- `onGarbageInserted`
- `onHoldUsed`
- `onPreviewAdvanced`
- `onTopOut`
- `onPauseChanged`
- `onScoreUpdated`

### 8.2 Event Payloads

Events include:

- `PieceSpawnedEvent`
- `PieceMovedEvent`
- `PieceLockedEvent`
- `LinesClearedEvent`
- `GarbageInsertedEvent`
- `HoldUsedEvent`
- `PreviewAdvancedEvent`
- `TopOutEvent`
- `PauseChangedEvent`
- `ScoreUpdatedEvent`

Movement kinds:

- LEFT
- RIGHT
- SOFT_DROP
- HARD_DROP
- GRAVITY
- ROTATE_CW
- ROTATE_CCW
- ROTATE_180

Top-out reasons:

- BLOCK_OUT
- LOCK_OUT
- GARBAGE_OVERFLOW

### 8.3 PieceLockResult

The detailed lock event is:

- `com.tetris.events.PieceLockResult`

It carries the richer information MAB needs:

- Piece type.
- Lines cleared.
- Whether the clear was a Tetris.
- Whether it was a perfect clear.
- Combo count.
- Back-to-back state.
- T-spin full/mini flags.
- Generic spin flag.
- Hard-drop information.
- Final board context.

This object is central to Step 22 all-spin recognition and the simplified MAB
strategic router.

## 9. Mutually Assured Blocks - High-Level Concept

### 9.1 Basic Loop

MAB is a battle mode layered on top of Tetris.

The core player loop:

1. Play Tetris normally.
2. Clear lines and land spins to gain strategic charge.
3. At 100 charge, the nuke becomes ready.
4. After ready, complete either launch route:
   - Tetris route: usually 4 Tetrises.
   - Spin route: usually 2 spins.
5. Launch a strike at the opponent.
6. The strike enters a countdown and then becomes an incoming threat.
7. The defender can land a spin during the warning window to intercept.
8. If not fully intercepted, the threat reaches impact.
9. Impact creates garbage, disarms charge, damages silo state, or produces
   delayed waves depending on the weapon profile.
10. The first side to top out loses.

### 9.2 Participants

Participant IDs:

- `PLAYER_A`
- `PLAYER_B`

Current PvE convention:

- Player A is the human.
- Player B is the AI.

Each participant has:

- A `GameState`.
- A `NukeBuildState`.
- A `SiloState`.
- A simplified strategic state.
- Legacy action-code progress state.
- Legacy upgrade state.
- Draft upgrade inventory.
- Active launches.
- Incoming threats.
- Radar intel.
- Civil defense.
- Decoys.
- Restraint/second-strike-related state.
- Piece/line/garbage/top-out totals.

### 9.3 Match Coordinator

The main MAB coordinator is:

- `com.tetris.mab.MutuallyAssuredBlocksMatch`

It owns and coordinates:

- Participants.
- DEFCON state.
- Piece countdown timers.
- Legacy action-code registry.
- Event log ring.
- Pending impact waves.
- Impact resolver.
- Intercept registry/resolver.
- Radar scan calculator.
- Decoy registry/resolver.
- Upgrade registries.
- Draft manager.
- AI draft picker.
- Launch/threat lifecycle.
- Civil defense.
- Result/winner determination.

### 9.4 Match Modes and Phases

Modes:

- `PVP_LOCAL`
- `PVE`

The launcher currently presents PvE as the working user-facing MAB mode.

Phases:

- `SETUP`
- `ACTIVE`
- `UPGRADE_PAUSE`
- `GAME_OVER`

The match can also be paused. MAB pause/resume pauses both `GameState` instances.

### 9.5 Match Lifecycle

Common lifecycle:

1. Construct match with participants and difficulty.
2. Attach event listeners to both `GameState`s.
3. Start match.
4. Enter `ACTIVE`.
5. Process piece locks, clears, top-outs, launches, threats, impacts, and drafts.
6. Enter `UPGRADE_PAUSE` when a human draft must be chosen.
7. Return to `ACTIVE` after draft pick.
8. Enter `GAME_OVER` when a participant tops out or a winner is determined.
9. Shutdown detaches listeners and stops related UI/AI timers.

## 10. MAB Simplified Strategic Core

### 10.1 Why It Exists

Earlier MAB steps experimented with memorized action-code sequences: players
would perform specific line clear patterns to trigger strategic actions. Step 21
pivots the live PvE/local loop away from that complexity.

The simplified model:

- Keeps the strategic drama.
- Uses normal Tetris skill.
- Makes the route visible.
- Avoids requiring players to memorize long command sequences.
- Keeps the legacy action-code engine available for debug/older systems.

### 10.2 Charge Formula

Charge is computed by:

- `com.tetris.mab.clear.MabChargeCalculator`

Base charge:

| Clear | Charge |
| --- | ---: |
| 0 lines | 0 |
| Single | 1 |
| Double | 3 |
| Triple | 5 |
| Tetris / Quad | 8 |
| 5+ lines | 8 + 2 per extra line |

Spin bonus:

| Spin lines | Bonus |
| --- | ---: |
| 0-line spin | 4 |
| Spin single | 8 |
| Spin double | 14 |
| Spin triple | 20 |
| Spin quad | 24 |

Other bonuses:

- Perfect clear: +12.
- Combo 0-1: +0.
- Combo 2-3: +1.
- Combo 4-6: +2.
- Combo 7+: +4.
- Back-to-back Tetris or spin: total charge multiplied by 1.25 and rounded.

Then the v2 upgrade effect resolver can further modify the charge.

### 10.3 Nuke Ready State

The default ready threshold is:

- 100 charge.

When charge reaches the threshold:

- The simplified state marks the nuke as ready.
- The HUD changes from charge-building to launch-route progress.
- If Manual Override is owned, the participant marks a per-cycle ready indicator
  flag. In the current codebase, the card and indicator state exist, but a full
  dedicated Q-key overlay implementation is not present in the live input path.

### 10.4 Launch Routes

After ready, the player completes one of two routes:

1. Tetris route.
   - Default target: 4 Tetrises.
   - Fast Fuse upgrade reduces this to 3.

2. Spin route.
   - Default target: 2 spins.
   - Any qualifying spin can count.
   - 0-line spins can count.
   - Spin Launch Crew causes a spin-route launch to retain 1 spin pip after
     firing.

When either route reaches its goal:

- The match authorizes and fires a launch.
- Charge is reset for the next cycle.
- Route progress resets, except for Spin Launch Crew retention.
- Rapid Assembly can refund charge after firing.

### 10.5 Intercept Priority

A crucial rule:

- If a participant has an active incoming warning threat and lands a spin, that
  spin is consumed as an intercept attempt instead of adding charge or route
  progress.

This makes spins defensive under pressure.

Spin intercept strength:

- Perfect clear or spin triple or better: full intercept attempt.
- Spin double: standard intercept attempt.
- Other spin: emergency intercept attempt.

Upgrades can improve the tier or modify the result.

### 10.6 Simplified State View

Per participant, `MabSimplifiedStrategicState` tracks:

- Current charge.
- Required charge.
- Nuke-ready flag.
- Tetris route progress.
- Tetris route goal.
- Spin route progress.
- Spin route goal.
- Whether spin route keeps one pip after firing.
- Last clear text.
- Last charge gained.
- Last strategic trigger.
- B2B chain length for display.

Strategic trigger values include:

- NONE.
- CHARGE_GAIN.
- NUKE_READY.
- LAUNCH_PROGRESS_TETRIS.
- LAUNCH_PROGRESS_SPIN.
- LAUNCH_FIRED_TETRIS.
- LAUNCH_FIRED_SPIN.
- SPIN_INTERCEPT.

## 11. MAB Launch Lifecycle

### 11.1 Launch Phases

Launch phase enum:

- AUTHORIZED.
- COUNTDOWN.
- IN_FLIGHT.
- IMPACT_READY.
- RESOLVED.
- CANCELLED.

Launches are represented by:

- `com.tetris.mab.ActiveLaunchState`

### 11.2 Threat Statuses

Incoming threat status enum:

- WARNING_ACTIVE.
- IMPACT_READY.
- INTERCEPTED.
- RESOLVED.
- CANCELLED.

Threats are represented by:

- `com.tetris.mab.IncomingThreatState`

### 11.3 Launch Flow

When a route fires:

1. `fireSimplifiedLaunch` is called.
2. The match calls internal launch authorization.
3. The current nuke design is used to create an `ActiveLaunchState`.
4. A launch countdown timer is created.
5. The attacker spends/reset charge for that cycle.
6. DEFCON can escalate based on launch size.
7. Event log entries are written.
8. Opponent radar intel is marked stale.

When the launch countdown completes:

1. The launch enters `IN_FLIGHT`.
2. The defender receives an `IncomingThreatState`.
3. A warning timer is created for the defender.
4. The defender's UI can show incoming threat state.

When warning completes:

1. The launch enters `IMPACT_READY`.
2. The threat enters `IMPACT_READY`.
3. The controller refresh loop can resolve impact-ready threats.

### 11.4 Piece Timers

Strategic timers use:

- `com.tetris.mab.PieceCountdownTimer`
- `com.tetris.mab.PieceTimerManager`

Timers advance by piece locks rather than real time. Timer advance modes include:

- OWNER_PIECES.
- OPPONENT_PIECES.
- EITHER_PLAYER_PIECES.
- PLAYER_A_PIECES.

This keeps strategic timing tied to Tetris tempo.

## 12. MAB Impact Resolution

### 12.1 ImpactResolver

Impact resolution is implemented by:

- `com.tetris.mab.impact.ImpactResolver`

It translates an impact-ready launch and threat into:

- Immediate garbage.
- Delayed garbage waves.
- Radiation-patterned rows.
- Disarm damage to defender charge.
- Silo damage.
- Civil-defense mitigation.
- Impact-grace deferral.
- Silo-upgrade mitigation.
- Resolved launch/threat state.

### 12.2 Damage Values

The impact resolver uses the attacker's `NukeDesign` for:

- Blast rating.
- Radiation rating.
- Disarm rating.
- Silo damage rating.

Important design rule:

- DEFCON changes readiness/deployment difficulty.
- DEFCON does not directly change damage ratings.

### 12.3 Mitigation Order

The impact logic applies mitigation roughly in this order:

1. Validate launch/threat/defender.
2. Skip if already resolved/cancelled/intercepted.
3. Pull base damage from nuke design.
4. Apply intercept mitigation multipliers.
5. Apply civil-defense mitigation.
6. Build the garbage plan.
7. Apply flat civil-defense immediate-row reduction.
8. Apply impact-grace policy to decide how many rows are safe immediately.
9. Insert allowed immediate garbage.
10. Schedule designed delayed waves.
11. Schedule grace-deferred rows as additional gentle waves.
12. Apply silo-upgrade mitigation to disarm/silo damage.
13. Reduce defender charge.
14. Damage defender silo integrity.
15. Mark launch and threat resolved.
16. Return an `ImpactResult`.

### 12.4 Radiation Garbage

Radiation garbage uses:

- `RadiationGarbagePatternGenerator`
- `RadiationLevel`
- `GarbageRowPattern`

Radiation levels:

- CLEAN.
- LIGHT.
- DIRTY.
- HOT.
- SEVERE.

Radiation patterns affect how holes appear in garbage rows, giving nuke impacts
a different feel from simple one-hole garbage.

### 12.5 Delayed Waves

Nuke designs can use garbage profiles with waves:

- Total garbage lines.
- Maximum immediate lines.
- Whether waves are used.
- Wave count.
- Pieces between waves.
- Messy row behavior.

Delayed waves are scheduled through the piece timer manager and inserted later
as defender-owned timers complete.

### 12.6 Impact Grace

Impact grace is handled by:

- `com.tetris.mab.defense.ImpactGracePolicy`

It prevents some impacts from instantly topping out the defender by deferring
unsafe immediate rows into delayed one-row waves. This makes MAB impacts
dramatic without always being abrupt and unreadable.

### 12.7 Civil Defense

Civil defense is represented by:

- `com.tetris.mab.defense.CivilDefenseState`
- `com.tetris.mab.defense.CivilDefenseMitigation`

Civil defense can:

- Consume active charges.
- Reduce immediate garbage.
- Reduce damage multipliers.
- Add grace rows.
- Create a temporary shield lasting a number of pieces.

The match exposes manual/debug activation APIs and the AI can trigger civil
defense when incoming threats are active.

## 13. MAB Intercepts

### 13.1 Intercept Types

Intercept definitions are registered in:

- `com.tetris.mab.intercept.InterceptRegistry`

Default intercepts:

| Type | Action | Power | Blast mul | Rad mul | Disarm mul | Silo mul | Can full? |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| EMERGENCY | EMERGENCY_INTERCEPT | 2 | 0.35 | 0.25 | 0.20 | 0.20 | No |
| STANDARD | STANDARD_INTERCEPT | 4 | 0.60 | 0.50 | 0.40 | 0.40 | Yes |
| FULL | FULL_INTERCEPT | 8 | 0.90 | 0.80 | 0.75 | 0.75 | Yes |

The multipliers are mitigation power applied to incoming damage components.

### 13.2 Intercept Outcomes

Outcomes:

- FULLY_INTERCEPTED.
- PARTIALLY_INTERCEPTED.
- FAILED_NO_THREAT.
- FAILED_THREAT_NOT_ACTIVE.
- FAILED_TOO_LATE.
- FAILED_ALREADY_INTERCEPTED.
- FAILED_INVALID_TARGET.
- FAILED_ERROR.

### 13.3 Intercept Resolution

The resolver is:

- `com.tetris.mab.intercept.InterceptResolver`

It is deterministic, with no RNG. It checks:

- Definition exists.
- Threat exists.
- Matching launch exists.
- Threat is warning-active.
- Launch is in-flight.
- Threat is not already resolved/cancelled/intercepted.
- It is not too late.

Threat resistance is based on:

- Size category:
  - MICRO = 1.
  - TACTICAL = 2.
  - THEATER = 3.
  - STRATEGIC = 4.
  - SUPERHEAVY = 6.
  - DOOMSDAY_SCALE = 8.
- MIRV doctrine adds 2.
- DECOY_PACKAGE doctrine adds 1.
- DOOMSDAY doctrine adds 2.
- Detection profile >= 4 adds 1.
- Very late warning with 1 or fewer pieces remaining adds 1.

If intercept power meets or exceeds resistance and the definition can fully
intercept, the threat is fully intercepted. Otherwise a partial intercept applies
mitigation multipliers.

## 14. MAB Nuke Designs

### 14.1 Strategic Design Schema

Live MAB nuke designs are immutable objects:

- `com.tetris.mab.nuke.NukeDesign`

Each design includes:

- ID.
- Display name.
- Doctrine type.
- Size category.
- Base build charge required.
- Blast rating.
- Radiation rating.
- Disarm rating.
- Silo damage rating.
- Base launch time in pieces.
- Base warning time in pieces.
- Detection profile.
- Base launch code.
- DEFCON-specific build charge.
- DEFCON-specific launch code.
- DEFCON-specific launch time.
- DEFCON-specific warning time.
- Garbage profile.
- Disarm profile.
- Silo damage profile.
- Optional MIRV profile.
- Optional EMP profile.
- Optional decoy profile.
- Optional full-clear threshold profile.

### 14.2 Doctrine Types

Doctrine types:

- CLEAN_FUSION.
- DIRTY_BOMB.
- SALTED_WARHEAD.
- CONCRETE_BLASTER.
- EMP_PAYLOAD.
- MIRV.
- BUNKER_BUSTER.
- DECOY_PACKAGE.
- DOOMSDAY.
- PLACEHOLDER.

### 14.3 Size Categories

Size categories:

- MICRO.
- TACTICAL.
- THEATER.
- STRATEGIC.
- SUPERHEAVY.
- DOOMSDAY_SCALE.

### 14.4 Default Presets

Default presets are created by:

- `com.tetris.mab.nuke.NukeDesignFactory.createAllDefaults`

| ID | Name | Doctrine | Size | Base build | Blast | Rad | Disarm | Silo | Base launch/warn | Detect |
| --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| placeholder_tactical | Placeholder Tactical | PLACEHOLDER | TACTICAL | 20 | 2 | 1 | 1 | 0 | 4/4 | 1 |
| clean_fusion_strategic | Clean Fusion Strategic | CLEAN_FUSION | STRATEGIC | 44 | 5 | 1 | 3 | 1 | 7/7 | 3 |
| dirty_tactical | Dirty Tactical | DIRTY_BOMB | TACTICAL | 22 | 2 | 4 | 1 | 0 | 5/5 | 2 |
| concrete_blaster | Concrete Blaster | CONCRETE_BLASTER | STRATEGIC | 40 | 2 | 1 | 6 | 5 | 7/7 | 3 |
| mirv_strategic | MIRV Strategic | MIRV | STRATEGIC | 42 | 5 | 2 | 2 | 1 | 7/7 | 4 |
| emp_payload | EMP Payload | EMP_PAYLOAD | THEATER | 30 | 1 | 2 | 3 | 2 | 6/6 | 2 |
| bunker_buster | Bunker Buster | BUNKER_BUSTER | THEATER | 34 | 3 | 0 | 5 | 5 | 6/6 | 3 |
| doomsday_device | Doomsday Device | DOOMSDAY | DOOMSDAY_SCALE | 100 | 8 | 3 | 8 | 8 | 12/12 | 5 |

### 14.5 DEFCON Scaling

Each default nuke has explicit DEFCON maps for:

- Build charge required.
- Launch code.
- Launch time.
- Warning time.

Lower DEFCON values generally make launches faster/easier, but damage ratings
stay constant.

### 14.6 Profiles

Nuke profile classes include:

- `GarbageProfile`.
- `DisarmProfile`.
- `SiloDamageProfile`.
- `MirvProfile`.
- `EmpProfile`.
- `DecoyProfile`.
- `FullClearThresholdProfile`.

These profiles let a design specify not only how many rows it deals, but also
how it interacts with disarm, silo integrity, warning intel, decoys, MIRV-like
multi-wave behavior, and full-clear thresholds.

## 15. DEFCON

### 15.1 Role

DEFCON state is tracked by:

- `com.tetris.mab.DefconState`

The battle shell displays a DEFCON ladder in the central ops deck.

### 15.2 Escalation

The match can escalate DEFCON when launches fire. Larger launches escalate more:

- Micro/tactical launches can push escalation by smaller amounts.
- Theater/strategic launches push more.
- Superheavy/doomsday launches push the most.

DEFCON affects readiness/deployment:

- Build costs.
- Launch code lengths.
- Launch timers.
- Warning timers.

DEFCON does not directly change blast/radiation/disarm/silo damage values.

## 16. Radar, Intel, and Decoys

### 16.1 Intel Levels

Radar intel levels:

- NONE.
- CONTACT.
- SIZE_ESTIMATE.
- DOCTRINE_ESTIMATE.
- PROGRESS_ESTIMATE.
- LAUNCH_WARNING.
- FULL_READOUT.

The radar system can mark enemy intel stale when the enemy launches or redesigns.

### 16.2 Radar Scan Types

Radar scan types:

- BASIC.
- UPGRADED.
- CRISIS.
- FULL_SPECTRUM.

Radar scanning is calculated by:

- `com.tetris.mab.intel.RadarScanCalculator`

The scan result can reveal or misread enemy doctrine, progress, launches, and
decoy effects.

### 16.3 Decoy Types

Decoy types:

- DECOY_LAUNCH.
- GHOST_MIRV.
- FALSE_DOCTRINE_SIGNAL.
- DUMMY_SILO_HEAT.

Decoy visibility states:

- HIDDEN.
- SUSPECTED.
- VISIBLE_AS_REAL.
- IDENTIFIED_AS_DECOY.

Decoys can:

- Create false signatures.
- Reduce confidence.
- Mislead radar about doctrine.
- Make non-threats look like launches.

### 16.4 Decoy Registry and Resolver

Decoy behavior is handled by:

- `com.tetris.mab.decoy.DecoyRegistry`
- `com.tetris.mab.decoy.DecoyResolver`

The strategic AI can deploy decoys depending on archetype, cooldowns, and balance
profile timing.

## 17. Legacy Action-Code System

### 17.1 Purpose

The action-code system predates the simplified MAB loop. It still exists for:

- Debugging.
- Probes.
- Legacy HUDs.
- Possible future/manual strategic controls.

The current live simplified PvE path does not require the player to memorize or
enter these action codes.

### 17.2 Core Classes

Package:

- `com.tetris.mab.action`

Important classes:

- `ActionCodeRegistry`.
- `ActionCodeDefinition`.
- `ActionCodeAttempt`.
- `ActionCodeManager`.
- `ActionCodeTokenRequirement`.
- `ActionClearToken`.
- `ActionCodeResult`.
- `ActionType`.
- `ActionCategory`.
- `ActionConfirmationMode`.
- `ActionCodeMatchMode`.
- `SpinKind`.

### 17.3 Action Categories

Categories:

- LAUNCH.
- DEFENSE.
- INTEL.
- DECOY.
- UTILITY.
- RESTRAINT.
- CUSTOM through action type handling.

### 17.4 Action Types

Action types include:

- MICRO_LAUNCH.
- TACTICAL_LAUNCH.
- THEATER_LAUNCH.
- STRATEGIC_LAUNCH.
- DIRTY_LAUNCH.
- MIRV_LAUNCH.
- CONCRETE_BLASTER_LAUNCH.
- SUPERHEAVY_LAUNCH.
- DOOMSDAY_LAUNCH.
- EMERGENCY_INTERCEPT.
- STANDARD_INTERCEPT.
- FULL_INTERCEPT.
- RADAR_SCAN.
- SILO_HARDEN.
- CIVIL_DEFENSE.
- DECOY_LAUNCH.
- GHOST_MIRV.
- FALSE_DOCTRINE_SIGNAL.
- DUMMY_SILO_HEAT.
- MASKED_LAUNCH.
- COUNTERLAUNCH_PREP.
- TREATY_RESTRAINT_LOCK.
- EMP_PULSE.
- CONCRETE_BLASTER_ARM.
- CUSTOM.

### 17.5 Confirmation Modes

Confirmation modes:

- NONE.
- KEYBOARD_CONFIRM.
- HARD_FOUR_CONFIRM.

Launch definitions often end in a hard 4-line confirmation in the legacy system.

## 18. MAB Upgrade Systems

There are two upgrade systems in the repository:

1. Legacy point-based upgrade registry.
2. New v2 level-up draft card system.

The current visible Step 24 mode uses the draft card system.

## 19. Legacy Upgrade Registry

### 19.1 Legacy Upgrade Types

Legacy upgrades live under:

- `com.tetris.mab.upgrade`

Legacy `UpgradeType` values include:

- HARDENED_SILO.
- DEEP_BUNKER.
- DISTRIBUTED_STOCKPILE.
- RAPID_ASSEMBLY_LINE.
- SECURE_LAUNCH_CHAIN.
- BLAST_DOORS.
- SILO_CAMOUFLAGE.
- SHELTERS.
- GARBAGE_CONTROL.
- EMERGENCY_PROTOCOLS.
- INTERCEPT_CREWS.
- RAPID_LAUNCH_DRILLS.
- SECURE_AUTHORIZATION.
- COUNTDOWN_AUTOMATION.
- EARLY_WARNING_RADAR.
- SIGNAL_ANALYSIS.
- THREAT_TRACKING.
- BUILD_EFFICIENCY.
- LINE_CLEAR_LOGISTICS.
- WARHEAD_REFINEMENT.
- CLEANER_FUSION.
- DIRTY_PAYLOAD_ENGINEERING.
- PENETRATION_PACKAGE.
- SECOND_STRIKE_DOCTRINE_I.
- SECOND_STRIKE_DOCTRINE_II.
- DEAD_HAND_PROTOCOL.
- ASSURED_RETALIATION.

### 19.2 Legacy Upgrade Categories

Legacy categories include:

- NUKE_DESIGN.
- LAUNCH_SYSTEMS.
- MISSILE_SYSTEMS.
- WARNING_RADAR.
- SILO_SYSTEMS.
- DEFENSE.
- MAD_SYSTEMS.
- TEMPO_SYSTEMS.

### 19.3 Legacy Upgrade Behavior

The registry defines:

- Display names.
- IDs.
- Categories.
- Max levels.
- Costs.
- Required DEFCON maximums.
- Prerequisites.
- Descriptions.

The UI classes `MabUpgradePanel` and `MabUpgradeWindow` still support legacy
upgrade pause workflows.

## 20. MAB Upgrade Draft v2

### 20.1 Purpose

The new upgrade draft system lives under:

- `com.tetris.mab.upgrade.draft`

It is documented in:

- `Upgrades.md`
- `Step24.md`

It replaces a point-spend menu with a level-up draft:

- When a participant levels up, they receive a 3-card draft.
- The human's game pauses into an upgrade overlay.
- The AI picks automatically.
- Cards modify the simplified MAB loop.

### 20.2 Draft Trigger

The draft manager watches level changes:

- The first draftable level begins after level 1.
- When a participant reaches a new level, that level can trigger a draft.
- Human drafts are queued and shown as overlays.
- AI drafts are applied automatically when polled.

### 20.3 Draft Generation

Draft generation:

- Deterministic from match seed, participant, and level.
- Selects exactly 3 choices where possible.
- Filters out maxed cards.
- Avoids duplicate cards within a draft.
- Falls back if a tier/pool is empty.

Rarity weights:

| Level range | Standard | Advanced | Critical |
| --- | ---: | ---: | ---: |
| Levels 2-3 | 85% | 15% | 0% |
| Levels 4-6 | 65% | 30% | 5% |
| Level 7+ | 50% | 35% | 15% |

### 20.4 AI Draft Picking

AI draft picker:

- `MabAiUpgradePicker`

Behavior:

- EASY picks randomly.
- Other difficulties sort by higher rarity first.
- Then by category priority.
- Then by card ID.

Category priority:

1. CHARGE.
2. DEFENSE.
3. TETRIS_ROUTE.
4. SPIN_ROUTE.
5. POWER.
6. TEMPO.

### 20.5 Draft Card Schema

Each `MabUpgradeCard` includes:

- ID.
- Display name.
- Short name.
- Icon text.
- Category.
- Rarity.
- One-line description.
- Detailed description.
- Max stacks.
- Repeatable flag.
- Effect tags.
- Sampling weight.
- Archetype membership.
- Active-card flag.

Ownership and stack counts live in:

- `MabUpgradeInventory`

### 20.6 Draft Categories

Draft categories:

- POWER.
- CHARGE.
- TETRIS_ROUTE.
- SPIN_ROUTE.
- DEFENSE.
- TEMPO.

### 20.7 Draft Rarities

Draft rarities:

- STANDARD.
- ADVANCED.
- CRITICAL.

### 20.8 Draft Archetypes

Draft archetypes:

- COMBO_REACTOR.
- SPIN_SPECIALIST.
- TETRIS_STOCKPILER.
- TURTLE.
- RUSHER.
- WILDCARD.

### 20.9 All 40 Draft Cards

#### Combo Reactor

| Card | Rarity | Category | Effect |
| --- | --- | --- | --- |
| Efficient Reactor | Standard | Charge | +1 charge per scoring clear per stack; max 2 stacks. |
| Combo Capacitor | Standard | Charge | Combo 4+ gains +2 extra charge per chained clear. |
| Streak Stoker | Standard | Charge | Nth consecutive clear awards N bonus charge. |
| B2B Amplifier | Advanced | Charge | B2B charge multiplier improves from 1.25 to 1.40. |
| Resonance Chamber | Advanced | Charge | Reaching combo length 6 grants +30 charge instantly. |
| Cascade Reactor | Critical | Tempo | While nuke-ready, every 3rd combo clear advances the relevant launch route by 1 pip. |

#### Spin Specialist

| Card | Rarity | Category | Effect |
| --- | --- | --- | --- |
| Spin Doctrine | Standard | Charge | Any spin clear adds +2 charge. |
| Wrist Drill | Standard | Charge | Zero-line spins award +1 charge. |
| TST Program | Advanced | Charge | Spin triples give +8 extra charge. |
| Counterspin Training | Advanced | Charge | A full intercept refunds +10 charge. Also belongs to Turtle. |
| Spin Network | Advanced | Charge | Spins within a launch cycle chain bonus charge; each spin gains bonus equal to prior spins that cycle. |
| Spin Launch Crew | Critical | Spin Route | A spin launch retains 1 spin route pip. |

#### Tetris Stockpiler

| Card | Rarity | Category | Effect |
| --- | --- | --- | --- |
| Tetris Doctrine | Standard | Charge | Each Tetris awards +6 bonus charge. |
| Clean Well Logistics | Standard | Charge | Non-spin Tetrises give +3 extra charge. |
| Perfect Clear Battery | Standard | Charge | Perfect clears give +8 extra charge. |
| Wellsmith | Advanced | Charge | Maintaining a Tetris-ready well multiplies charge gain by 1.20. |
| B2B Amplifier | Advanced | Charge | Shared with Combo Reactor. |
| Fast Fuse | Critical | Tetris Route | Tetris route target is reduced from 4 to 3. |

#### Turtle

| Card | Rarity | Category | Effect |
| --- | --- | --- | --- |
| Shelters | Standard | Defense | Impact garbage reduced by 1 line, floor 1. |
| Bunker | Standard | Defense | First impact of the match has its line count halved. |
| Cold Steel | Standard | Defense | While stack is low, full intercepts refund +5 extra charge. |
| Intercept Crews | Standard | Defense | Spin intercept tier improved by 1. |
| Emergency Protocols | Standard | Defense | Once per match, a high-stack impact is softened by 2 lines. |
| Reactive Plating | Advanced | Defense | After taking impact, intercept window doubles for 6 seconds. |
| Counterspin Training | Advanced | Charge | Shared with Spin Specialist. |
| Ablative Spin | Critical | Defense | One spin intercept can cover two clustered threats. |
| Hardened Silos | Critical | Defense | Once per match, an impact cannot top you out. |
| Retaliation Doctrine | Critical | Tempo | Taking impact awards +20 charge. |
| Dead Hand Protocol | Critical | Defense | Once per match, prevents fatal impact and auto-fires an uninterceptable counter-launch; half normal draft weight. |

#### Rusher

| Card | Rarity | Category | Effect |
| --- | --- | --- | --- |
| Light the Fuse | Standard | Power | First launch of the match deals +1 garbage line. |
| Heavy Warhead | Advanced | Power | Every launch deals +1 garbage line. |
| Penetrator Package | Advanced | Power | Opponent intercepts are one tier less effective against your launches. |
| Dirty Payload | Advanced | Power | Each impact you cause adds 1 messy garbage line. |
| Rapid Assembly | Advanced | Tempo | Firing a launch refunds +10 charge. |
| Hair Trigger | Advanced | Tempo | Charge gain +25%, but charge bleeds 1 per second when not clearing. |
| Glass Cannon | Advanced | Power | Your launches +2 lines; impacts on you +1 line. |
| Manual Override | Critical | Tempo | Card definition says full charge can fire a half-power launch without route completion; code currently registers the active card and ready indicator but no complete live Q-key overlay path was found. |

#### Wildcards

| Card | Rarity | Category | Effect |
| --- | --- | --- | --- |
| Quiet Storm | Standard | Charge | After 10 seconds without a clear, the next clear gains +20 charge. |
| Stockpile | Advanced | Charge | Charge cap increases from 100 to 130 while the ready threshold remains 100. |
| Adrenaline Sequence | Advanced | Charge | While stack is dangerously high, charge gain is multiplied by 1.5. |
| Jam | Advanced | Tempo | When the opponent reaches ready, one of their launch route targets increases by 1 for that cycle. |
| EMP | Critical | Tempo | Card definition says it can cancel an opponent launch for 40 charge; it is registered as an active card, but no complete live player overlay/input path was found in the current source pass. |

### 20.10 Effect Tags

Charge tags:

- `charge_clear_bonus_1`
- `charge_combo_4plus`
- `charge_streak_stoker`
- `charge_b2b_amplifier`
- `charge_combo_resonance`
- `charge_spin_any`
- `charge_spin_zero`
- `charge_spin_triple`
- `charge_intercept`
- `charge_spin_chain`
- `charge_tetris_flat`
- `charge_clean_tetris`
- `charge_perfect_clear`
- `charge_wellsmith`
- `charge_quiet_storm`
- `charge_stockpile`
- `charge_adrenaline`

Tempo tags:

- `tempo_cascade_reactor`
- `tempo_rapid_assembly`
- `tempo_hair_trigger`
- `tempo_retaliation`
- `tempo_manual_override`
- `tempo_jam`
- `tempo_emp`

Defense tags:

- `defense_shelters`
- `defense_bunker`
- `defense_cold_steel`
- `defense_intercept_str`
- `defense_emergency`
- `defense_reactive_plating`
- `defense_ablative_spin`
- `defense_hardened_silos`
- `defense_dead_hand`

Power tags:

- `power_light_fuse`
- `power_heavy_warhead`
- `power_penetrator`
- `power_dirty_payload`
- `power_glass_cannon`

Route tags:

- `route_tetris_minus1`
- `route_spin_keep1`

### 20.11 Effect Resolver

The effect resolver is:

- `MabUpgradeEffectResolver`

It handles:

- Flat charge bonuses.
- B2B multiplier overrides.
- Combo bonuses.
- Perfect clear bonuses.
- Tetris bonuses.
- Spin bonuses.
- Stateful streak and spin-network bonuses.
- Wellsmith multiplier.
- Adrenaline multiplier.
- Hair Trigger charge multiplier and bleed.
- Quiet Storm arming and bonus.
- Charge cap increase.
- Route target changes.
- Spin pip retention.
- Outgoing garbage additions.
- Incoming garbage additions.
- Impact mitigation.
- Intercept strength deltas.
- Penetrator deltas.
- Launch-fired refunds.
- Impact-taken retaliation.
- Full-intercept charge refunds.

## 21. MAB AI

### 21.1 Visible Board AI

The visible opponent board is driven by:

- `com.tetris.mab.ai.MabBoardAiDriver`

It controls the opponent's actual `GameState`, so the player can watch the AI
placing pieces.

The visible board AI:

- Snapshots the board.
- Tries rotations and target columns.
- Drops each candidate to landing.
- Scores the result.
- Chooses the best deterministic plan.
- Rotates toward the plan.
- Moves toward the target column.
- Dwells briefly.
- Hard drops.
- Replans.

Heuristic terms:

- Lines cleared.
- Aggregate height.
- Holes.
- Bumpiness.
- Max height.
- Top-out risk.

The AI has no T-spin recognition and no deep lookahead in this visible-board
driver.

### 21.2 Visible Board Difficulty

Visible board difficulty affects:

- Action interval.
- Drop dwell time.
- Rotation candidates.
- Line reward weight.
- Aggregate height penalty.
- Hole penalty.
- Bumpiness penalty.
- Max height penalty.
- Top-out penalty.

Difficulty rungs:

- EASY: slower, fewer rotations, lower penalties.
- NORMAL: baseline.
- HARD: faster, stricter.
- DEBUG: very fast for validation.

Approximate target PPS:

- EASY around 0.50 PPS.
- NORMAL around 1.00 PPS.
- HARD around 1.67 PPS.
- DEBUG around 3.00 PPS.

### 21.3 Strategic AI

Strategic AI classes:

- `MabAiDriver`
- `MabAiPolicy`
- `MabAiState`
- `MabAiDecision`
- `MabAiDecisionType`

The strategic AI is deterministic and offline. It can decide to:

- Resolve impact-ready threats.
- Activate civil defense.
- Perform radar scans.
- Add charge in legacy/debug paths.
- Arm current nuke in legacy/debug paths.
- Deploy decoys.
- Open/apply legacy upgrade sequences.

The current live PvE launch rule is important:

- The strategic AI does not simply launch because a nuke is armed.
- Live launches fire through the same simplified clear-route rules as the
  player.

### 21.4 AI Archetypes

AI archetypes:

- TACTICAL_SPAMMER: smaller/frequent launches.
- DIRTY_BOMBER: radiation/messy impacts and decoys.
- CONCRETE_STRATEGIST: silo/disarm pressure.
- MAD_DEFENDER: defense, intercepts, second-strike posture.
- MIRV_CONTROLLER: deception, ghost signatures, radar pressure.
- DOOMSDAY_HOARDER: slow large weapons.
- BALANCED: general-purpose baseline.

### 21.5 AI Balance Profiles

Balance profiles:

- Standard PvE.
- Gentle PvE.
- High Pressure PvE.
- Debug Fast.

Profiles tune:

- AI charge gain per tick.
- AI add-charge step.
- Launch cooldown.
- Radar cooldown.
- Decoy cooldown.
- Defense cooldown.
- Upgrade cooldown.
- Early launch delay.
- Minimum ticks before decoy/upgrade/civil defense.
- Maximum launch pressure gates.
- Preferred impact grace.
- Preferred max immediate garbage.
- Preferred civil defense starting charges.
- Preferred civil defense shield pieces.

## 22. MAB Battle Shell UI

### 22.1 Battle Shell

The main current MAB UI is:

- `com.tetris.mab.ui.MabBattleShellPanel`

It replaced an older dashboard-style MAB PvE panel.

Layout:

- Top left: player station banner.
- Top center: ops deck / DEFCON / radar.
- Top right: opponent station banner.
- Middle left: player piece bay and player board.
- Middle center: fixed-width ops deck.
- Middle right: opponent board and intel bay.
- Bottom left: player tactical strip.
- Bottom center: mode plate / shared ops.
- Bottom right: opponent tactical strip.

It uses a `JLayeredPane` so overlays can sit over the battle UI.

### 22.2 Player Board

The player board in MAB is the normal `GamePanel`, re-hosted in:

- `MabBoardHostPanel`

This keeps the underlying Tetris rendering and input behavior consistent.

### 22.3 Opponent Board

The opponent board is:

- `MabOpponentBoardPanel`

It wraps a render-only `GamePanel` bound to the opponent's `GameState`.

It displays:

- Opponent board.
- AI archetype.
- AI difficulty.
- Balance profile.
- Lines.
- Pieces.
- Stack height.
- Charge.
- Armed state.
- Active launches.
- Incoming threats.
- AI placement plan.
- AI phase.
- Target PPS.
- Hard-drop count.
- Top-out status.

### 22.4 Ops Deck

The center ops deck is:

- `MabOpsDeckPanel`

It displays:

- DEFCON ladder.
- Animated radar sweep.
- Threat pips.
- Mode line.
- Mode subline.
- Match clock.

It is read-only and does not mutate match state.

### 22.5 Tactical Strips

The bottom strips are:

- `MabTacticalStripPanel`

They display:

- Charge current/required.
- Charge gauge.
- Tetris route pips.
- Spin route pips.
- Defense state.
- Last clear.

Defense display states:

- SAFE.
- INCOMING.
- INTERCEPT.
- IMPACT.

### 22.6 Station Banners

Station banners are:

- `MabStationBannerPanel`

They show dramatic participant state, such as:

- Build.
- Ready.
- Incoming.
- Intercept.
- Launch.
- Impact.
- Over.

### 22.7 Stage Presenter

Dramatic stage model:

- `MabStage`
- `MabStagePresenter`
- `MabStageStripPanel`

Stage priority:

1. MATCH_OVER.
2. INCOMING_THREAT.
3. IMPACT_READY.
4. LAUNCH_FIRED.
5. SPIN_INTERCEPT.
6. NUKE_READY.
7. LAUNCH_TETRIS_ROUTE / LAUNCH_SPIN_ROUTE.
8. BUILD_CHARGE.

Stage headlines include:

- BUILD CHARGE.
- NUKE READY.
- LAUNCH TETRIS.
- LAUNCH SPIN.
- INCOMING THREAT.
- SPIN INTERCEPT.
- LAUNCH FIRED.
- IMPACT INCOMING.
- MATCH OVER.

### 22.8 Input Adapter

The battle-shell input adapter is:

- `MabBattleShellInputAdapter`

It installs a global `KeyEventDispatcher` while the shell is active. This solves
the issue where focusable controls or overlays could steal focus and leave held
movement keys stuck.

It:

- Forwards key presses/releases to the existing `InputHandler`.
- Clears held keys on focus loss.
- Clears held keys on window deactivation.
- Stops forwarding when a modal overlay is visible.
- Shuts down when the shell is removed.

### 22.9 Result Overlay

The battle shell includes an in-window result overlay:

- Shows match result title.
- Shows cause text.
- Shows stats text.
- Provides Restart.
- Provides Back to Menu.
- Clears held input before and after overlay actions.

### 22.10 Upgrade Draft Overlay

The upgrade overlay is:

- `MabUpgradeDraftOverlayPanel`

It:

- Shows 3 card choices.
- Shows rarity/category styling.
- Shows stack/max status.
- Supports keyboard navigation.
- Confirms a selection.
- Dismisses after selection.
- Clears held input around the modal state.

### 22.11 Legacy and Companion MAB UI

Additional MAB UI classes remain:

- `MabPveGamePanel`: older PvE dashboard.
- `MabHudPanel`: player-facing HUD.
- `MabCompactHudPanel`: compact HUD.
- `MabPlayerFacingController`: player HUD refresh/controller bridge.
- `MabCommandGuideWindow`, `MabCommandGuideModel`, `MabCommandGuideFormatter`.
- `MabMatchResultDialog`, `MabMatchResultSummary`, `MabMatchResultFormatter`.
- `MabNukeRedesignPanel`, `MabNukePresetDefinition`.
- `MabUpgradePanel`, `MabUpgradeWindow`, `MabUpgradeFormatter`.
- `MabAlertModel`, `MabAlertFormatter`, `MabAlertSeverity`, `MabAlert`.
- `MabActionFeedback`.

Some are legacy or optional debug/companion surfaces; the battle shell is the
current primary PvE screen.

## 23. MAB Result and Win Conditions

### 23.1 Top-out Driven Result

A participant loses when their Tetris board tops out. Causes can be:

- Normal Tetris block-out.
- Lock-out.
- Garbage overflow from impacts/waves.

The opponent is declared winner.

### 23.2 Match Result Display

The battle shell result overlay displays:

- Win/loss title.
- Cause.
- Match stats.
- Restart action.
- Back-to-menu action.

Legacy result formatter/dialog classes also exist for older MAB UI paths.

## 24. Debugging, Probes, and Simulation

### 24.1 Simulation Package

Simulation and probe classes live in:

- `com.tetris.mab.sim`

They include:

- `MabChargeCalculatorProbe`
- `MabBattleShellLayoutProbe`
- `MabBoardAiPaceProbe`
- `MabActionCodeInputProbe`
- `MabInputStateProbe`
- `MabHeadlessSimulation`
- `MabSharedPieceSequenceProbe`
- `MabSimulationRunner`
- `MabThreatLifecycleProbe`
- `MabUpgradeOverlayLayoutProbe`
- `MabSpinDetectionProbe`
- `MabSimulationTelemetry`
- `MabUpgradeDraftProbe`
- `MabSimulationMode`
- `MabSimulationInvariants`
- `MabSimplifiedCoreProbe`
- `MabUpgradeEffectProbe`

### 24.2 Simulation Modes

Simulation modes:

- SMOKE.
- AI_VS_DUMMY.
- AI_VS_AI.
- LAUNCH_IMPACT.
- RADAR_DECOY.
- CIVIL_DEFENSE.
- UPGRADE_FLOW.

### 24.3 Purpose of Probes

The probes validate:

- Charge formula values.
- Shared piece sequence behavior.
- Visible board AI pacing.
- Battle shell layout and non-zero child bounds.
- Input state clearing.
- Action-code attempt creation/progression.
- Threat lifecycle pruning.
- All-spin recognition.
- Upgrade draft generation.
- Upgrade overlay layout.
- Upgrade effect resolver behavior.
- Headless match invariants.

### 24.4 Debug HUDs

Optional debug/companion HUDs:

- Controlled by config and system properties.
- `mab.debug.hud` can default the debug HUD setting.
- `mab.pve.companionHud` can enable companion player HUD behavior.
- `mab.input.debug` logs battle shell input adapter behavior.

### 24.5 Target Output

Compiled classes and build products are under:

- `target/`

The worktree currently includes generated class files and many development docs.

## 25. Architecture by Package

### 25.1 `com.tetris`

Main application package:

- `Main`: program entry point, start-level parsing, look and feel, launcher setup.

### 25.2 `com.tetris.controller`

Controller package:

- `GameController`: runtime coordinator for game state, timers, views, MAB, AI,
  pause/reset/settings/debug behavior.
- `InputHandler`: keyboard input, DAS/ARR/SDF, rotations, hold, pause/reset,
  settings, console, held-key clearing.
- `GameLaunchMode`: mode enum with normal Tetris, MAB PvE, and reserved/debug
  MAB value.

### 25.3 `com.tetris.model`

Core Tetris model:

- `Board`: grid, collision, locking, line clears, ghost position, garbage.
- `GameState`: active Tetris game state and rules.
- `ScoreSystem`: scoring, levels, combos, B2B, perfect clear, PPS/time.
- `Tetromino`: immutable active piece.
- `TetrominoType`: piece shapes and colors.
- `Position`: integer coordinate value.
- `BagRandomizer`: 7-bag random piece generator.
- `SRSData`: wall kick data.
- `SpinDetector`: generic immobile spin detector.
- `Settings`: persistent settings singleton.

### 25.4 `com.tetris.model.nuke`

Educational Nuke Builder model:

- `NukeDesign`: mutable slot selections and derived conceptual stats.
- `NukeSlot`: slot catalog.
- `NukePart`: selectable conceptual component.

### 25.5 `com.tetris.events`

Event bridge:

- `GameEventListener`: listener interface and event records.
- `PieceLockResult`: detailed lock result for spin/MAB routing.
- `GarbageRowPattern`: row-hole pattern and source tag.

### 25.6 `com.tetris.view`

Normal Swing UI:

- `StartMenu`: launcher and card host.
- `GameView`: embeddable play view.
- `MainFrame`: standalone frame.
- `GamePanel`: board renderer.
- `SidePanel`: hold/stats/B2B/combo HUD.
- `NextPanel`: next queue and controls panel.
- `SettingsPanel`: settings UI.
- `NukeBuilderDialog`: educational builder UI.
- `DevConsolePanel`: developer console overlay.

### 25.7 `com.tetris.view.theme`

Normal UI styling:

- `Theme`: colors, fonts, borders, helpers.
- `Components`: buttons/labels/shared component helpers.
- `BlockRenderer`: block rendering styles.

### 25.8 `com.tetris.mab`

MAB core:

- `MutuallyAssuredBlocksMatch`: central match coordinator.
- `ParticipantState`: per-side strategic state wrapper.
- `ParticipantId`: PLAYER_A / PLAYER_B.
- `MatchMode`: PVP_LOCAL / PVE.
- `MatchDifficulty`: EASY / NORMAL / HARD / DOOMSDAY.
- `MatchPhase`: SETUP / ACTIVE / UPGRADE_PAUSE / GAME_OVER.
- `DefconState`: DEFCON level state.
- `NukeBuildState`: charge/current design/armed state.
- `SiloState`: silo integrity and defensive upgrades.
- `SiloDamageState`: STABLE / DAMAGED / COMPROMISED.
- `ActiveLaunchState`: outgoing launch.
- `IncomingThreatState`: defender-visible threat.
- `PieceCountdownTimer`: piece-based timer.
- `PieceTimerManager`: timer registry/advancer.
- `TimerAdvanceMode`: timer ownership/advance mode.
- `ActionCodeProgressState`: per-participant legacy action-code state.
- `MatchEventLogEntry`: event log entries.
- `MatchDebugSnapshot`: debug state snapshot.
- `RestraintState`, `SecondStrikeState`, `CivilDefenseState`: strategic support
  state.

### 25.9 `com.tetris.mab.clear`

Simplified clear routing:

- `MabChargeCalculator`: charge formula.
- `MabClearResult`: MAB clear event.
- `MabSimplifiedStrategicState`: charge/route HUD state.

### 25.10 `com.tetris.mab.action`

Legacy action-code system:

- Action definitions, attempts, manager, registry, token requirements, result
  enums, categories, and action types.

### 25.11 `com.tetris.mab.launch`

Launch enums:

- `LaunchPhase`.
- `ThreatStatus`.

### 25.12 `com.tetris.mab.nuke`

MAB strategic nuke model:

- `NukeDesign`.
- `NukeDesignFactory`.
- `NukeDesignValidator`.
- `NukeReadinessScaler`.
- `NukeBuilderAdapter`.
- `BuilderNukeSpec`.
- `NukeDoctrineType`.
- `NukeSizeCategory`.
- `GarbageProfile`.
- `DisarmProfile`.
- `SiloDamageProfile`.
- `MirvProfile`.
- `EmpProfile`.
- `DecoyProfile`.
- `FullClearThresholdProfile`.

### 25.13 `com.tetris.mab.impact`

Impact system:

- `ImpactResolver`.
- `ImpactResult`.
- `ImpactResolutionStatus`.
- `ImpactWaveState`.
- `ImpactGarbagePlan`.
- `RadiationGarbagePatternGenerator`.
- `RadiationLevel`.

### 25.14 `com.tetris.mab.intercept`

Intercept system:

- `InterceptRegistry`.
- `InterceptResolver`.
- `InterceptDefinition`.
- `InterceptResult`.
- `InterceptType`.
- `InterceptOutcome`.
- `InterceptMitigationState`.

### 25.15 `com.tetris.mab.defense`

Defense support:

- `CivilDefenseMitigation`.
- `ImpactGracePolicy`.
- `ImpactGraceDecision`.
- Civil-defense and impact-grace helpers.

### 25.16 `com.tetris.mab.intel`

Radar/intel:

- `RadarScanCalculator`.
- `RadarScanResult`.
- `RadarScanType`.
- `RadarIntelState`.
- `IntelLevel`.

### 25.17 `com.tetris.mab.decoy`

Decoys:

- `DecoyRegistry`.
- `DecoyResolver`.
- `DecoyDefinition`.
- `DecoyResolutionResult`.
- `ActiveDecoyState`.
- `DecoyType`.
- `DecoyVisibility`.

### 25.18 `com.tetris.mab.ai`

AI:

- `MabBoardAiDriver`.
- `MabBoardAiPlan`.
- `MabAiDriver`.
- `MabAiPolicy`.
- `MabAiState`.
- `MabAiDecision`.
- `MabAiDecisionType`.
- `MabAiArchetype`.
- `MabAiDifficulty`.

### 25.19 `com.tetris.mab.balance`

Balance profiles:

- `MabBalanceProfile`.
- `MabBalanceProfiles`.

### 25.20 `com.tetris.mab.upgrade`

Legacy upgrade system:

- `UpgradeRegistry`.
- `UpgradeDefinition`.
- `UpgradeState`.
- `UpgradeType`.
- `UpgradeCategory`.
- `UpgradeApplicationResult`.
- `UpgradeChoiceSet`.
- `NukeRedesignRetentionRules`.

### 25.21 `com.tetris.mab.upgrade.draft`

Level-up draft system:

- `MabUpgradeDraftRegistry`.
- `MabUpgradeDraftManager`.
- `MabUpgradeDraft`.
- `MabUpgradeCard`.
- `MabUpgradeInventory`.
- `MabUpgradeEffectResolver`.
- `MabAiUpgradePicker`.
- `MabUpgradeCategory`.
- `MabUpgradeRarity`.
- `MabUpgradeArchetype`.

### 25.22 `com.tetris.mab.ui`

MAB UI:

- Battle shell, ops deck, tactical strips, station banners, board host, opponent
  board, piece bay, upgrade overlay, setup dialog, legacy HUDs, command guide,
  result display, alert formatting, themes, and player-facing controllers.

### 25.23 `com.tetris.mab.sim`

Headless simulations and validation probes.

## 26. Current Implementation Notes and Caveats

### 26.1 Primary Current Playable Path

The most complete current path is:

1. Launch app.
2. Choose Start MAB.
3. Choose PvE vs AI.
4. Configure AI/start level/balance/debug.
5. Play in the battle shell.
6. Clear/spin/Tetris to build charge.
7. Draft upgrades on level-up.
8. Launch and defend through the simplified MAB loop.

Normal single-player Tetris is also complete and playable.

### 26.2 Legacy Systems Still Present

The repository contains both old and new systems:

- Old MAB dashboard panels and compact HUDs.
- Old action-code progression.
- Old point-based upgrade window.
- New battle shell.
- New level-up draft overlay.
- New simplified clear/route system.

This is why some packages look more expansive than the current player-facing
flow requires.

### 26.3 Active Upgrade Cards

The v2 registry defines Manual Override and EMP as active cards. The cards and
effect tags exist. Manual Override also has a ready-cycle indicator flag in
participant state. However, in the current source pass, the complete live
hotkey/overlay implementation for those active decisions is not wired into the
battle shell input path in the same concrete way as the level-up draft overlay.

### 26.4 PvP Status

The codebase has local-PvP-oriented types and factories, but the launcher and
UI are currently oriented around PvE vs AI. The menu labels PvP as coming soon.

### 26.5 Networking Status

There is no networking layer in the current implementation.

### 26.6 Safety of Educational Builder

The educational Nuke Builder includes conceptual historical/physics content and
coarse game-like estimates. It is separate from the MAB strategic presets and is
not needed to play the battle mode.

## 27. What the Player Actually Sees

### 27.1 Normal Tetris Screen

The player sees:

- Dark themed board.
- Falling tetromino.
- Ghost piece.
- Hold preview.
- Next queue.
- Score/level/lines/time/pieces/PPS.
- B2B and combo badges.
- Pause/game-over overlays.
- Settings accessible from toolbar or keybind.

### 27.2 MAB PvE Screen

The player sees:

- Their own board on the left.
- Opponent AI board on the right.
- Central DEFCON/radar ops deck.
- Station banners.
- Tactical strips with charge and route progress.
- Opponent AI status and plan.
- Incoming/impact/launch visual states.
- Upgrade draft overlay on level-up.
- Result overlay at match end.

### 27.3 Moment-to-Moment MAB Readability

The current UI tries to make the strategy layer readable through:

- Charge meter.
- Launch route pips.
- Last clear label.
- Defense state.
- Incoming threat warnings.
- Opponent board visibility.
- Radar pips.
- DEFCON ladder.
- AI plan text.
- Result summaries.

## 28. Complete Gameplay Summary

Normal Tetris is the foundation: the player moves, rotates, soft drops, hard
drops, holds, and locks tetrominoes on a 10x20 board using modern rules. The
game scores line clears, T-spins, combos, back-to-back clears, perfect clears,
and drop distance while raising level every 10 lines.

MAB then turns those same Tetris actions into a battle economy. Clears and spins
produce charge. At 100 charge, the player must prove readiness by completing a
route: either multiple Tetrises or multiple spins. Completing a route launches a
strike. The opponent receives a warning window. Spins during that window become
intercepts. Failed or partial defense lets impacts resolve into garbage, delayed
waves, disarm damage, and silo damage. Leveling up pauses the match for upgrade
drafts, and those cards bend the charge, route, defense, and power systems.

The result is a local desktop game where standard Tetris skill remains the
mechanical core, but the surrounding battle layer creates a second strategic
question: when to build, when to attack, when to spin defensively, and which
upgrade path makes the current match winnable.
