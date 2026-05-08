# Modern Tetris — Java + Maven

A faithful implementation of modern Tetris following the Tetris Guideline specification, built in Java with Swing rendering and Maven build system. Handling settings sourced from a TETR.IO config file (config.ttc).

## Quick Start

```bash
# Build
mvn package

# Run
mvn exec:java

# Or run the JAR directly (optional: start at a specific level 1–20)
java -jar target/modern-tetris-1.0.0.jar
java -jar target/modern-tetris-1.0.0.jar 5
```

## Controls

| Key | Action |
|---|---|
| ← → | Move left / right |
| ↓ | Soft drop |
| Space | Hard drop (instant lock) |
| ↑ | Rotate clockwise |
| Z | Rotate counter-clockwise |
| A | Rotate 180° |
| C / Shift | Hold piece |
| P / Escape | Pause / resume |
| R | Reset (anytime) |

## Features Implemented

### Core Mechanics
- **Standard 10×20 playfield** with 4-row hidden buffer zone above
- **All 7 tetrominoes** (I, O, T, S, Z, J, L) with Guideline-standard colors
- **Super Rotation System (SRS)** — full wall kick tables for all pieces
- **7-bag randomizer** — each piece appears exactly once per bag of 7
- **Ghost piece** — translucent preview of where the piece will land
- **Hold piece** — store a piece for later (once per lock cycle)
- **Next piece preview** — shows the next 5 upcoming pieces

### Movement & Timing
- **Hard drop** — instant drop + lock, 2 points per cell
- **Soft drop** — SDF-based (6× gravity speed), 1 point per cell
- **Lock delay** — 500ms before a grounded piece locks
- **Lock reset** — moving or rotating on the ground resets the lock timer (max 15 resets)
- **DAS (Delayed Auto Shift)** — 167ms (from config.ttc: handling.das = 10 frames)
- **ARR (Auto Repeat Rate)** — 33ms (from config.ttc: handling.arr = 2 frames)
- **SDF (Soft Drop Factor)** — 6× gravity speed (from config.ttc: handling.sdf = 6)
- **IRS (Initial Rotation System)** — tap mode: pre-rotate on spawn
- **IHS (Initial Hold System)** — tap mode: pre-hold on spawn
- **Reset** — R key resets the game at any time (not just game over/paused)

### Scoring System
- **Line clears**: Single (100), Double (300), Triple (500), Tetris (800) × level
- **T-Spin detection**: Full T-Spin and T-Spin Mini with appropriate scoring
- **Back-to-back bonus**: Consecutive Tetrises or T-Spins earn 1.5× score
- **Combo system**: Consecutive line-clearing pieces earn 50 × combo × level bonus
- **Level progression**: Level increases every 10 lines
- **Gravity curve**: `(0.8 − (level−1) × 0.007) ^ (level−1)` seconds per drop

### Rendering
- 3D beveled block effect (highlight + shadow edges)
- Ghost piece with translucent rendering
- Side panel with Hold, Next, Score, Level, Lines, Combo display
- Pause and Game Over overlay screens
- 60fps Swing Timer game loop

## Project Structure

```
src/main/java/com/tetris/
├── Main.java                    # Entry point
├── model/
│   ├── Position.java            # Immutable (x,y) coordinate
│   ├── TetrominoType.java       # 7 piece types with rotation states & colors
│   ├── Tetromino.java           # Active piece (immutable, with movement methods)
│   ├── SRSData.java             # SRS wall kick offset tables
│   ├── Board.java               # 10×24 grid, collision, locking, line clearing
│   ├── BagRandomizer.java       # 7-bag random piece generation
│   ├── ScoreSystem.java         # Scoring, levels, gravity, combos, B2B
│   └── GameState.java           # Central game logic coordinator
├── controller/
│   ├── GameController.java      # Game loop, input routing, lifecycle
│   └── InputHandler.java        # Keyboard capture, DAS/ARR processing
└── view/
    ├── MainFrame.java           # JFrame window layout
    ├── GamePanel.java           # Playfield renderer (board, pieces, ghost)
    └── SidePanel.java           # Side info panel (hold, next, score, controls)
```

## Architecture

**MVC pattern:**
- **Model** (`model/`): All game state and rules. Immutable piece objects, pure collision logic, no UI dependencies.
- **View** (`view/`): Swing rendering. Reads model state, draws pixels. No game logic.
- **Controller** (`controller/`): Connects model and view. Runs the 60fps game loop via Swing Timer on the EDT.

## How Key Systems Work

### Super Rotation System (SRS)
When the player rotates a piece, the system first tries the basic rotation. If that collides, it tests up to 4 wall kick offsets (translations) from the SRS tables. The first valid position wins. Different tables are used for the I-piece (4×4 bounding box) vs J/L/S/T/Z pieces (3×3 bounding box). The O-piece has identical rotation states so never needs kicks.

### 7-Bag Randomizer
Instead of pure random, all 7 pieces are shuffled into a "bag" and dealt in order. When the bag empties, a new shuffled bag is created. This guarantees you see each piece at least once every 7, with a maximum drought of 12 between identical pieces.

### T-Spin Detection
A T-Spin is recognized when: (1) the piece is T, (2) the last move was a rotation, and (3) at least 3 of the 4 diagonal corners around the T center are occupied. A "Mini" T-Spin occurs when fewer than 2 "front-facing" corners are filled (unless the 4th wall kick test was used, which promotes it to a full T-Spin).

### Lock Delay
When a piece lands on the ground, a 500ms timer starts. If the player moves or rotates the piece, the timer resets — up to 15 times. This allows finesse moves at the bottom without rushing, while still preventing infinite stalling.

---

# UI Status Report &amp; Overhaul Plan

> **Document scope.** This report audits the current Swing-based UI layer of the game, calls out concrete weaknesses on a per-file basis, gives the Nuke Builder a dedicated deep-dive, and lays out a phased plan to completely overhaul the UI — with a from-scratch redesign for the Nuke Builder.
>
> **Status as of audit:** all UI logic lives in `src/main/java/com/tetris/view/` across 6 files. The Nuke Builder alone is **~2,580 lines** in a single `JDialog` subclass and is the single largest source of UI debt in the project. None of the UI uses a design system, layout-manager helper, or shared theming module — every screen re-declares its own colors, fonts, paddings, and borders.

## 1. Executive Summary

### 1.1 Top-level findings

| # | Category | Severity | Where it hurts most |
|---|---|---|---|
| 1 | **God-class dialog** — `NukeBuilderDialog` is ~2,580 LOC, mixes layout, painting, model, controller, and key-handling | 🔴 Critical | [NukeBuilderDialog.java](src/main/java/com/tetris/view/NukeBuilderDialog.java) |
| 2 | **No shared design system** — colors, fonts, spacings copy-pasted across every file with subtle drift | 🔴 Critical | every view file |
| 3 | **Hardcoded pixel sizes &amp; non-responsive layouts** — fixed widths like `720`, `280`, `180`, `PANEL_WIDTH = 180`, `setPreferredSize(…, 710)` | 🔴 Critical | [SidePanel.java](src/main/java/com/tetris/view/SidePanel.java#L62-L86), [NukeBuilderDialog.java](src/main/java/com/tetris/view/NukeBuilderDialog.java#L181-L184) |
| 4 | **Custom invented keyboard model** in the nuke builder (column focus enum + manual chrome repaint) instead of using Swing focus traversal | 🔴 Critical | [NukeBuilderDialog.java](src/main/java/com/tetris/view/NukeBuilderDialog.java#L82-L100) |
| 5 | **Per-frame allocations in `paintComponent`** — `new Font(...)`, `new Color(...)`, `new BasicStroke(...)` every repaint at 60 fps | 🟠 High | [GamePanel.java](src/main/java/com/tetris/view/GamePanel.java#L113-L210), [SidePanel.java](src/main/java/com/tetris/view/SidePanel.java#L97-L260) |
| 6 | **Inconsistent screens** — Start menu, settings, in-game and nuke builder each use different button styling, border conventions, and font sizes | 🟠 High | all view files |
| 7 | **Mutating global `UIManager` from a dialog constructor** — `SettingsPanel` writes shared L&amp;F state with side effects across other windows | 🟠 High | [SettingsPanel.java](src/main/java/com/tetris/view/SettingsPanel.java#L121-L131) |
| 8 | **Accessibility gaps** — small fixed font sizes (10–12 pt), low-contrast greys (`80,120,140` on `6,8,16`), no focus rings on most controls, `setFocusable(false)` everywhere | 🟠 High | [SidePanel.java](src/main/java/com/tetris/view/SidePanel.java#L65-L72), [NukeBuilderDialog.java](src/main/java/com/tetris/view/NukeBuilderDialog.java) |
| 9 | **Side panel painted by hand** with manual y-cursor arithmetic instead of real Swing components — values can't be selected, copied, or resized | 🟡 Medium | [SidePanel.java](src/main/java/com/tetris/view/SidePanel.java#L99-L130) |
| 10 | **Undecorated full-screen JFrame/JDialog** with no way to minimise / alt-tab cleanly on Windows; no graceful exit beyond ESC / Quit button | 🟡 Medium | [StartMenu.java](src/main/java/com/tetris/view/StartMenu.java#L28-L36), [NukeBuilderDialog.java](src/main/java/com/tetris/view/NukeBuilderDialog.java#L132-L143) |

### 1.2 Visual identity assessment

The intent is clear and pretty good in places — a "CERN/LHC control-room" aesthetic with deep navy panels, electric cyan accents, and monospaced text. The problem is **execution drift**:

- **At least 4 different "background" colors** are in use that all want to be the same value: `(4,6,14)`, `(6,8,16)`, `(8,12,20)`, `(14,20,32)`.
- **At least 3 "accent cyan" values**: `(0,200,220)`, `(0,220,240)`, `(0,160,180)`.
- **Three different "label grey" tones** spread across `SidePanel`, `SettingsPanel`, `NukeBuilderDialog`.
- Mixed font families on the same screen (`Monospaced`, `SansSerif`) without a clear hierarchy rule.
- Section borders use both `LineBorder` and `drawRoundRect` styles depending on the file.

The result: each screen looks "almost" like the others but is visibly off.

---

## 2. Per-File Audit

### 2.1 [`MainFrame.java`](src/main/java/com/tetris/view/MainFrame.java)

**Role.** Top-level game window. `BorderLayout` with `SidePanel` (WEST) and `GamePanel` (CENTER). Owns the menu bar.

**Weaknesses.**

- **Dual key listener attachment** ([MainFrame.java](src/main/java/com/tetris/view/MainFrame.java#L99-L104)) — input is attached to *both* the game panel and the frame "for safety", meaning every key event fires twice into `InputHandler`. Either the handler internally deduplicates (fragile) or the user sees double inputs when DAS races. Should use key bindings on the root pane.
- **Forced repaint cascade** ([MainFrame.java](src/main/java/com/tetris/view/MainFrame.java#L120-L125)) — `repaint()` is overridden to manually call `gamePanel.repaint()` and `sidePanel.repaint()`. This is what `super.repaint()` already does for opaque children; the override risks recursion and defeats Swing's repaint manager coalescing.
- **Hard-coded minimum size** `(420, 400)` — chosen for a 30 px cell, will be too small on hi-DPI and too cramped if `PANEL_WIDTH` ever changes.
- **Menu bar uses default L&amp;F** — clashes visually with the LHC palette of every other surface; "Tools" menu is the only entry point and is easy to miss.
- **No status bar / no FPS / no game info in the chrome** — wasted top edge.
- **ESC binding silently disposes the window** with no confirmation — accidentally hitting ESC mid-game throws away the run.

---

### 2.2 [`GamePanel.java`](src/main/java/com/tetris/view/GamePanel.java)

**Role.** Renders the playfield, current piece, ghost, locked cells, overlays, and a decorative animated "particle network" background.

**Weaknesses.**

- **Per-frame `new Font(...)` and `new Color(...)`** in `drawOverlay` ([GamePanel.java](src/main/java/com/tetris/view/GamePanel.java#L210-L226)), `drawCell` ([GamePanel.java](src/main/java/com/tetris/view/GamePanel.java#L188-L209)) and the connection loop ([GamePanel.java](src/main/java/com/tetris/view/GamePanel.java#L289-L322)). At 60 fps × 80 nodes × ~3,160 pair checks this is the dominant allocator in the whole app.
- **Particle network is heavy *and* always-on** — runs even while paused or in game-over. `O(n²)` neighbour search, no spatial partition. On low-end hardware this is the first thing that drops frames.
- **Decoration leaks into the playfield** — particles draw *behind* the board background but their connection lines cross over it before the board is opaque-filled, producing a faint shimmer through the field at low `boardOpacity`.
- **No separation between "game world" rendering and "chrome" rendering** — overlays, particles, grid, board, and pieces are all in one giant `paintComponent`.
- **Bevel highlight is hand-crafted with `fillRect` strips** rather than a reusable `BlockRenderer` — the same code appears almost verbatim in `SidePanel.drawMiniPiece`.
- **No animation for line clears, lock flashes, soft-drop trails, hard-drop impact, or T-spin/tetris confetti** — modern Tetris UX gives constant feedback; this panel gives none.
- **Pause / Game Over overlays are static text** — no fade, no menu, no buttons. Game-over has no "retry / quit" affordance.
- **Hidden-row buffer is invisible** — hard to debug spawn collisions; should optionally render the top 4 rows dimmed.

---

### 2.3 [`SidePanel.java`](src/main/java/com/tetris/view/SidePanel.java)

**Role.** Hold / Next / Score / Last Action / Controls reference, all hand-painted into one `JPanel`.

**Weaknesses.**

- **Fixed `(180, 710)` preferred size** ([SidePanel.java](src/main/java/com/tetris/view/SidePanel.java#L80-L86)). Comment even admits "Height must accommodate Hold(70) + Next(270) + Score(120) + Action(30) + Controls(~141)…" — the layout is computed by hand because there are no real components.
- **Manual `y` cursor** ([SidePanel.java](src/main/java/com/tetris/view/SidePanel.java#L107-L130)) is a re-implementation of `BoxLayout`/`GridBagLayout` in a `paintComponent`. It has no support for resizing, hi-DPI, fonts changing, or the user wanting bigger preview cells.
- **Score / level / lines are not selectable text** — players can't copy a score, screen readers can't read them, accessibility tools see nothing.
- **`MINI_CELL = 18` is hardcoded** — the side panel does not scale with the playfield even though the playfield does.
- **Controls section duplicates settings data** — it manually walks every `Settings.getKeyXxx()` getter ([SidePanel.java](src/main/java/com/tetris/view/SidePanel.java#L210-L228)). Adding a new control means editing both `Settings` *and* this panel by hand.
- **Scanlines &amp; drift line** ([SidePanel.java](src/main/java/com/tetris/view/SidePanel.java#L353-L364)) repaint every frame using `System.currentTimeMillis()` as animation source — pleasant, but this drives a constant repaint of the side panel that does nothing useful when the game is paused.
- **Hold "used" state is shown as flat grey** `(80,80,80)` — no diagonal hatching, no opacity shift, no "lock" icon. Easy to misread.
- **No Last-Action history / line-clear log** — only the most recent action is shown, then it vanishes.

---

### 2.4 [`SettingsPanel.java`](src/main/java/com/tetris/view/SettingsPanel.java)

**Role.** Modal tabbed dialog (Handling / Controls / Visual / Game) for editing `Settings`.

**Weaknesses.**

- **Mutates global `UIManager`** in the constructor ([SettingsPanel.java](src/main/java/com/tetris/view/SettingsPanel.java#L121-L131)). This *permanently* alters tab styling for any other `JTabbedPane` opened later in the same JVM. Should be confined via a custom `TabbedPaneUI` or a `SynthLookAndFeel`.
- **Repetitive boilerplate** — `loadFromSettings()` and `saveToSettings()` each list every setting by hand ([SettingsPanel.java](src/main/java/com/tetris/view/SettingsPanel.java#L301-L380)). 13 keybinds × 2 = 26 line pairs that must stay in sync. Should be driven by a `Setting&lt;T&gt;` registry.
- **No live preview** — changing handling sliders or visual opacities does not update the running game until Save is pressed and the dialog is closed.
- **No search / filter** — the Controls tab is a long flat list; no way to find "rotate" without scrolling.
- **No conflict detection** — two actions can be bound to the same key and the dialog will save it without warning.
- **Key capture mode** has no visible hint about how to cancel beyond a tooltip, and ESC during capture is also the application-wide exit shortcut — interactions can collide.
- **Dialog is `setResizable(false)`** with `pack()` — on hi-DPI or different fonts the layout can clip.
- **No keyboard navigation between tabs** beyond the default Ctrl+Tab / mouse — no numbered-tab shortcuts.
- **Tooltips are the only documentation** for what each setting actually does — many users never discover them.

---

### 2.5 [`StartMenu.java`](src/main/java/com/tetris/view/StartMenu.java)

**Role.** Fullscreen, undecorated launcher window with Play / Nuke Builder / Settings / Quit buttons.

**Weaknesses.**

- **Undecorated full-screen `JFrame`** ([StartMenu.java](src/main/java/com/tetris/view/StartMenu.java#L31-L36), again at [#L113-L117](src/main/java/com/tetris/view/StartMenu.java#L113-L117)) — cannot be minimised, dragged, or resized. On Windows it can become impossible to alt-tab back to if focus is lost. Combined with the in-game window also being its own JFrame, the taskbar fills with two app entries.
- **Decorative only** — no background art, no recent-score readout, no "continue" / "high score" / "stats" / "credits" — wasted real estate at full screen.
- **Buttons are 280×44 px fixed** — a tiny island in the middle of a 1920×1080 screen.
- **No visible version / build info / changelog link.**
- **Quit is `System.exit(0)`** with no confirmation — fat-finger risk.
- **Footer mentions "Tools → Nuke Builder inside the game"**, but the in-game menu bar is itself easy to overlook (see §2.1).
- **Two entry points to settings** (start menu and in-game menu) but no entry point from a paused game overlay — discoverability is poor.

---

### 2.6 [`NukeBuilderDialog.java`](src/main/java/com/tetris/view/NukeBuilderDialog.java) — deep dive in §3

This file alone is the largest UI weakness in the project; see the dedicated section below.

---

## 3. Nuke Builder — Dedicated Weakness Report

> The Nuke Builder is conceptually great (an educational, KSP-style cross-section constructor) but its implementation has accumulated severe UI debt. The class is **~2,580 lines**, contains **multiple inner painter classes**, mixes model/view/controller, and reinvents Swing facilities (focus, layout, theming) inside one constructor.

### 3.1 Architectural problems

1. **God class.** Dialog construction, schematic painting (`SchematicPanel` inner class), terminal painting (`FuzeTerminal`), switcher rail painting (`SwitcherRail`), keyboard model (`FocusCol`, `fusionRowIndex`), domain logic (`FusionDetails` static struct, `slotApplicable`), styling, and dialog lifecycle all live in one file.
2. **No MVP/MVC.** The dialog *owns* a `NukeDesign`. There is no separate controller, no presenter, no event bus — every interaction directly mutates `design` from inside a button listener and then calls `refresh()` to rebuild the world.
3. **`refresh()` is a sledgehammer.** Every state change rebuilds the parts list, repaints the schematic, repaints the fuze terminal, repaints the switcher rail, recomputes the build summary, recomputes the info text, recomputes derived stats, and repaints chrome. There is no diffing — clicking a single radio option rebuilds the entire palette column.
4. **Duplicated palette of constants.** `DARK_BG`, `PANEL_BG`, `TEXT_FG`, `ACCENT`, `ACCENT_DIM`, `BTN_BG` are re-declared inside this class (lines 56–62) with values that drift from `SettingsPanel`'s "identical" palette.
5. **Custom keyboard focus model.** `FocusCol` enum + `fusionRowIndex` integer + manual chrome highlighting + `setFocusable(false)` on every button to prevent Swing's real focus from interfering. This re-implements Swing's focus traversal — badly — because the original was getting in the way of arrow-key bindings on the window.

### 3.2 Layout problems

- **Hybrid `BorderLayout` abuse.** The root uses `BorderLayout`, but `NORTH` is itself a `BorderLayout` containing header / schematic / fuze terminal stacked vertically. `WEST` is a multi-column `BorderLayout` palette. `CENTER` is the info sidebar. `EAST` is the stats sidebar. This makes the visual order **(palette | info | stats)** with the schematic floating on top — an uncommon arrangement that fights muscle memory ("schematic should be the centrepiece").
- **Hardcoded column widths** — `720` for the palette wrap, `180` for slots column, `280` for the fusion column, `300` for schematic height, `108` for fuze terminal. None scale; on a 1920 × 1080 screen the actual schematic gets ~600 px of width while the palette eats almost half the window.
- **Two-level nesting just to "hug content"** — `slotsBarHost`, `partsHost`, `fusionPair`, `rightCols`, `leftCol`, `wrap` are wrapper panels added solely to defeat `BorderLayout.CENTER`'s stretch behaviour. A `MigLayout` or a single `GridBagLayout` would replace ~150 lines of nesting.
- **Fullscreen-only undecorated dialog** ([NukeBuilderDialog.java](src/main/java/com/tetris/view/NukeBuilderDialog.java#L138-L143)) — same problems as `StartMenu`. There is no way to keep the dialog at a smaller size, snap it to half the screen, or alt-tab back to the underlying game window cleanly.

### 3.3 Interaction problems

- **Three-column palette with a custom "switcher rail"** between the parts list and the fusion designer ([NukeBuilderDialog.java](src/main/java/com/tetris/view/NukeBuilderDialog.java#L237-L322)). The rail exists *only* to teach the user that the right-arrow key crosses a column boundary — a clear sign that the interaction model is non-obvious.
- **The schematic is the headline visual but is the most passive part of the screen.** Slots are clicked from the textual list on the left, not from the schematic itself. The "click a region of the schematic to make that slot active" promise from the file's own header comment is only partially delivered.
- **Forced ordering** — the user must select a "Configuration" first or every other slot shows the same scolding message ("Select a configuration first"). This is a wizard-style flow shoehorned into a free-form palette UI.
- **Reset / Close are tiny footer buttons** under the slot list — easy to miss, no separation from the slot buttons themselves.
- **No "save build / load build / share build"** — every session starts from blank.
- **No undo / redo.** Misclicking a part means re-finding the previous one in the list.
- **Build summary is a `JTextArea` of `String.format` lines** ([NukeBuilderDialog.java](src/main/java/com/tetris/view/NukeBuilderDialog.java#L386-L411)) — the section that *should* be the most polished is rendered as monospaced ASCII.

### 3.4 Visual problems

- **Eleven slot colors** ([NukeBuilderDialog.java](src/main/java/com/tetris/view/NukeBuilderDialog.java#L64-L78)) chosen ad-hoc — orange / bronze / yellow / red / cyan / violet / grey / olive / green / navy / blue. There's no perceptual ordering, no colour-blind safety, and several pairs are hard to tell apart on the dark background (bronze vs olive, navy vs blue, violet vs red).
- **Chrome amber** `(255,170,60)` for keyboard hints clashes with the cyan accent identity used everywhere else.
- **Mixed icon vocabulary** — Unicode arrows (`\u2190`, `\u2192`), chevrons drawn as text in 36 pt SansSerif, plus emoji in `StartMenu` (`▶`, `☢`, `⚙`, `✕`). No coherent icon set.
- **Custom `paint` in inner classes** uses ad-hoc `RenderingHints` setup, ad-hoc `Font` construction, and ad-hoc `Graphics2D.dispose()` on a copy — boilerplate that should be a single helper.

### 3.5 Code-quality problems

- **Magic numbers everywhere** — `setPreferredSize(new Dimension(720, 0))`, `setPreferredSize(new Dimension(180, 0))`, `setPreferredSize(new Dimension(280, 0))`, `EmptyBorder(6, 14, 6, 14)`, `EmptyBorder(10, 14, 6, 14)`, `BasicStroke(thickness)` with thickness derived from a per-pixel formula, etc.
- **`JTextArea` mis-used as a label** — `buildSummary`, `analogLabel`, `radiusLabel`, `effectsArea`, `warningsArea` are all read-only `JTextArea`s used for static formatted text. A custom `JComponent` with `Graphics2D.drawString` or a `JLabel` with HTML would be clearer.
- **`partsListPanel.putClientProperty("fusionWrap", fusionWrap)`** ([NukeBuilderDialog.java](src/main/java/com/tetris/view/NukeBuilderDialog.java#L233-L234)) — sneaks references through the Swing client-property bag because the author didn't want another field. Hides dependencies and breaks IDE refactors.
- **`abbrevSlot(String name)` switches on user-facing strings** ([NukeBuilderDialog.java](src/main/java/com/tetris/view/NukeBuilderDialog.java#L425-L443)) — renaming a slot in `NukeSlot.java` silently breaks the abbreviation in the build summary.
- **`buildSummary` field is named oddly** — declared as `private JTextArea buildSummary;` *immediately* before a method named `buildSummaryPanel()`. The naming collision is a smell.
- **No tests.** Nothing pins the layout, the keyboard model, or the slot-applicability rules.

### 3.6 What it does well (keep these)

- The **schematic cross-section** concept is genuinely cool and educational — preserve and *promote* it to the centre of the redesign.
- The **fuze terminal** as a small "diegetic" readout is a nice touch — keep, but make it smaller and corner-pinned.
- The **build summary** at the bottom of the slot column is a good idea — keep, but render it as proper components.
- The **per-slot color coding** is a sound idea — but rebuild the palette with a consistent perceptual scale.
- The **educational info text** is the actual product value — the new UI must give this even more space.

---

## 4. UI Overhaul Plan

### 4.1 Guiding principles

1. **One design system, one source of truth.** All colors, fonts, paddings, radii, borders, and standard component styles live in a single `view.theme` package and *every* screen pulls from it.
2. **Components, not paint.** Replace hand-painted widgets in `SidePanel` and the nuke builder with real Swing components (or one custom `BlockRenderer` shared between game + previews). Painting is reserved for the playfield, the schematic, and decorative backgrounds.
3. **Responsive layouts.** No more hardcoded preferred sizes for whole columns. Use `MigLayout` (already a tiny dependency) or carefully chosen `GridBagLayout` with weight ratios. Min/max sizes only on truly fixed elements (mini piece previews).
4. **Decouple model from view.** Each screen has a small *presenter* object that mediates between `Settings` / `GameState` / `NukeDesign` and the panel. The panel only listens to presenter events and reads view-state.
5. **Animation budget.** Background decoration must run at ≤ 30 fps on the EDT or move to an off-EDT timer that publishes diff frames. Pause animations entirely when the window loses focus.
6. **Accessibility baseline.** Minimum 14 pt body font, 4.5:1 contrast for body text, real focus rings, tab-order that matches reading order, all controls reachable by keyboard, screen-reader names on every non-decorative widget.
7. **Decorated windows by default.** Stop using undecorated full-screen frames for the launcher and the nuke builder. Provide a "fullscreen" toggle instead.

### 4.2 New package layout

```
src/main/java/com/tetris/view/
├── theme/
│   ├── Theme.java               // colors, fonts, paddings, radii (single source)
│   ├── Components.java          // styled JButton/JLabel/JSlider factories
│   ├── BlockRenderer.java       // shared 3D-bevel cell painter
│   └── Animations.java          // requestAnimationFrame-style helpers
├── chrome/
│   ├── AppFrame.java            // shared decorated JFrame base
│   ├── TitleBar.java            // optional custom title bar (when undecorated)
│   └── StatusBar.java           // FPS / build / mode indicator
├── start/
│   └── StartScreen.java         // replaces StartMenu, rich landing page
├── game/
│   ├── PlayfieldPanel.java      // pure board renderer
│   ├── BackdropPanel.java       // particle network, throttled, isolated
│   ├── HudSidebar.java          // hold / next / score (real components)
│   └── PauseOverlay.java        // proper menu overlay with buttons
├── settings/
│   ├── SettingsDialog.java      // shell
│   ├── SettingsRegistry.java    // declarative Setting<T> entries
│   └── tabs/{Handling,Controls,Visual,Game}Tab.java
└── nuke/
    ├── NukeBuilderFrame.java    // shell (was NukeBuilderDialog)
    ├── SchematicCanvas.java     // interactive cross-section, click-to-select
    ├── PartsDrawer.java         // slide-in palette for the active slot
    ├── BuildSummaryCard.java    // proper component
    ├── StatsCard.java           // yield / mass / complexity
    ├── EducationCard.java       // long-form info text, scrollable
    ├── FuzeTerminal.java        // moved out as its own component
    └── controller/NukeBuilderPresenter.java
```

### 4.3 Phased plan

#### Phase 1 — Design system foundation (no behaviour change)

- [ ] Create `view.theme.Theme` with **canonical** color tokens: `bg.0`, `bg.1`, `bg.2`, `accent`, `accent.dim`, `text.primary`, `text.muted`, `warn`, `danger`, `success`.
- [ ] Replace every hard-coded `new Color(...)` in the view layer with a token reference.
- [ ] Define a font scale: `display` (28), `h1` (20), `h2` (16), `body` (14), `mono` (14), `caption` (12) — and one mono family, one sans family, no more.
- [ ] Spacing scale `s0..s5` in 4 px increments. Replace literal `EmptyBorder(6, 14, 6, 14)` with `Theme.padding(s2, s4)`.
- [ ] `Components.styledButton(...)`, `Components.sectionPanel(...)`, `Components.titledRow(...)` factories.
- [ ] `BlockRenderer.draw(g2, x, y, size, color, mode)` shared between `PlayfieldPanel` and previews.
- [ ] Migrate `MainFrame`, `GamePanel`, `SidePanel`, `SettingsPanel`, `StartMenu`, `NukeBuilderDialog` to use the new tokens **without** changing layout.

#### Phase 2 — Main game screens

- [ ] Replace `MainFrame` with `AppFrame`; single key listener via `InputMap`/`ActionMap` on the root pane (kill the dual-attach).
- [ ] Move particle network into `BackdropPanel`, throttle to 30 fps, pause on focus-lost, move connection search to a grid bucket.
- [ ] Rebuild `SidePanel` as `HudSidebar`: a `JPanel` with real `JLabel` / `MiniPiecePanel` children inside a `BoxLayout`. Score values become selectable text. Controls section reads from a `Settings` listener (no manual list).
- [ ] Add `PauseOverlay` JComponent with **Resume / Settings / Restart / Quit to Menu** buttons.
- [ ] Add line-clear flash, hard-drop impact pulse, and Tetris/T-Spin banner animations driven by `Animations`.
- [ ] Status bar at the bottom: FPS, current bag position, build version.

#### Phase 3 — Nuke Builder rebuild (the big one)

Goal: the schematic becomes the centerpiece; everything else orbits it.

**Target layout** (decorated, resizable, default 1280 × 800; remembers last size):

```
┌───────────────────────────────────────────────────────────────────────────┐
│  ☢  NUKE BUILDER — EDUCATIONAL DEMONSTRATION         [ Save | Load | × ] │  ← real title bar
├──────────────┬───────────────────────────────────────┬────────────────────┤
│              │                                       │  STATS             │
│  STAGES      │                                       │  ─────             │
│  ┌────────┐  │     ╔═════════════════════════════╗   │  Yield   100 kt   │
│  │ Config │  │     ║                             ║   │  Mass     2.1 t   │
│  │ Fissil │  │     ║      INTERACTIVE            ║   │  Cmplx   ●●●○○    │
│  │ Tampr  │  │     ║      CROSS-SECTION          ║   │  Analog  W76      │
│  │ Init   │  │     ║      (click any layer to    ║   │  Radius  3.4 km   │
│  │ Impl ● │  │     ║       select that slot)     ║   │                    │
│  │ Boost  │  │     ║                             ║   │  EFFECTS           │
│  │ Sec    │  │     ║                             ║   │  …                 │
│  │ Casing │  │     ╚═════════════════════════════╝   │  WARNINGS          │
│  │ Fuze   │  │                                       │  …                 │
│  │ Safety │  │   ┌─ Education ────────────────────┐  │                    │
│  │ Delivr │  │   │ scrollable long-form text for  │  │                    │
│  └────────┘  │   │ the currently-selected part …  │  │                    │
│              │   └────────────────────────────────┘  │                    │
├──────────────┴───────────────────────────────────────┴────────────────────┤
│  BUILD SUMMARY:  W88-style · 2-stage TN · 475 kt           [ Reset Build ]│
└───────────────────────────────────────────────────────────────────────────┘
                  ↑ FUZE terminal slides up from this bar when fuze is active
```

Interaction model:

- **Schematic is the primary input.** Click any colored region → that slot becomes active and a *parts drawer* slides in over the right of the schematic, showing the available parts in cards (image / name / one-line description / select button). Click outside or press Esc to close the drawer.
- **Stages column** is secondary navigation; mirrors the schematic regions in textual form for keyboard users.
- **Fusion sub-designer** becomes a *modal overlay* anchored to the secondary region of the schematic, not a third column. Activated when a Teller-Ulam configuration is committed.
- **Build summary** is a single bottom strip with a one-line description ("W88-style two-stage thermonuclear, ~475 kt") plus a Reset button. Detailed stats live in the right-hand stats card.
- **Education text** is always visible under the schematic at the size it deserves, with Markdown-ish formatting (headings, lists).
- **Fuze terminal** becomes an opt-in overlay anchored to the bottom edge — slides up only when the fuze slot is active.
- **Save / Load / Share** lives in the title bar; designs serialise to a small JSON.

Engineering tasks:

- [ ] Extract `NukeBuilderPresenter` — owns `NukeDesign`, exposes `Observable` events (`slotChanged`, `partChanged`, `derivedStatsChanged`).
- [ ] Extract `SchematicCanvas` from the inner `SchematicPanel`; add hit-testing so clicks on regions select slots.
- [ ] Extract `PartsDrawer` JComponent (slide animation via `Animations.slideIn`).
- [ ] Move slot-applicability and per-config part filtering into `NukeSlot` / `NukePart` metadata; the view only renders.
- [ ] Move the keyboard model to standard Swing focus traversal (`FocusTraversalPolicy`) plus `KeyStroke` bindings on the root pane for the global shortcuts. Delete `FocusCol` enum + `fusionRowIndex` + `SwitcherRail`.
- [ ] Replace every `JTextArea`-as-label with `Components.bodyLabel(html)` or a custom paragraph component.
- [ ] Replace 11 ad-hoc slot colors with a curated palette of 11 from a perceptually-uniform scale (e.g. ColorBrewer "Set3" or a custom OKLCH ramp), validated for color-blind safety.
- [ ] Make the window decorated, resizable, with sane min size `(960, 640)`; persist size + position to `Settings`.
- [ ] Add Save / Load (JSON), Undo / Redo, and a "Compare to historical analog" sidebar entry.
- [ ] Unit-test the presenter (slot-applicability, derived stats) so the rewrite is safe.

#### Phase 4 — Settings overhaul

- [ ] Introduce a declarative `SettingsRegistry` so each `Setting<T>` knows its label, tooltip, range, default, and how to render itself. `loadFromSettings` / `saveToSettings` collapse to one loop.
- [ ] Live preview: changes propagate immediately via a `Settings` observer; Cancel rolls back from a snapshot.
- [ ] Conflict detection on key bindings; a warning row appears when a key is double-bound.
- [ ] Search box at the top of the dialog ("DAS", "rotate", …) that filters all tabs.
- [ ] Per-control "Reset" buttons in addition to the global Reset Defaults.
- [ ] Resizable dialog; remembers last size.
- [ ] No more `UIManager.put(...)` from inside the dialog — wire the tab styling through the design system.

#### Phase 5 — Start screen overhaul

- [ ] Replace undecorated `StartMenu` with a decorated, resizable `StartScreen` (defaults to maximised but allows windowed mode and minimise / alt-tab cleanly).
- [ ] Real landing page: marquee piece animation on the left, primary actions on the right (**Play**, **Continue last run** if a save exists, **Nuke Builder**, **Settings**, **About**, **Quit**).
- [ ] High-score / last-run stats card.
- [ ] Confirm dialog on Quit.
- [ ] Background uses the same `BackdropPanel` as the game, throttled.

#### Phase 6 — Polish pass

- [ ] Hi-DPI audit (Windows 125 % / 150 % / 200 %).
- [ ] Light-mode variant of the theme (toggle in Settings → Visual).
- [ ] Sound-design hook points (line clear, lock, menu-confirm, slot-select).
- [ ] Final keyboard-only walkthrough of every screen with focus rings visible.
- [ ] Replace emoji icons in buttons with a curated SVG icon set rasterised to the theme's accent color.

### 4.4 Cross-cutting cleanup checklist

- [ ] Eliminate every `new Color(...)` and `new Font(...)` outside the `theme` package.
- [ ] Eliminate every `setPreferredSize(new Dimension(<int>, <int>))` outside genuinely fixed widgets (mini-piece previews, status bar).
- [ ] Eliminate every `paintComponent` allocation by caching `Font` / `Color` / `BasicStroke` in static finals or in `Theme`.
- [ ] Replace every `JTextArea` used as a static label with a `JLabel` (HTML) or a paragraph component.
- [ ] Replace `MainFrame.repaint()` override with reliance on Swing's normal repaint manager.
- [ ] Single `KeyboardController` that maps `Settings`-defined keystrokes to actions via `InputMap`/`ActionMap`; remove duplicate listener attachment.
- [ ] Persist window size/position for every top-level window in `Settings`.
- [ ] Add `assert SwingUtilities.isEventDispatchThread();` at the top of every public mutator on view classes.

### 4.5 Risk &amp; sequencing notes

- Phases **1 and 3** are the highest value and the highest risk. Do Phase 1 first because everything else depends on the design system; do Phase 3 second because the Nuke Builder is the most visible weakness and a contained subtree of the codebase.
- Phase 2 changes touch the game loop's repaint path — coordinate with the `GameController` author to ensure no input regressions.
- Phase 4 (settings) is mostly mechanical refactor and can be done in parallel with Phase 3 by a different contributor.
- Phases 5 and 6 are pure polish and can ship incrementally after Phase 3 lands.

---

## 5. Quick-win Backlog (can land before the big overhaul)

These are low-risk, low-effort fixes worth doing now even before Phase 1:

- [ ] Remove the duplicate key listener attachment in `MainFrame` ([MainFrame.java](src/main/java/com/tetris/view/MainFrame.java#L99-L104)).
- [ ] Stop overriding `MainFrame.repaint()` ([MainFrame.java](src/main/java/com/tetris/view/MainFrame.java#L120-L125)).
- [ ] Cache the overlay `Font` and `Color` objects in `GamePanel` instead of allocating per frame.
- [ ] Add a confirmation dialog to `StartMenu` Quit and `MainFrame` ESC.
- [ ] Allow `StartMenu` and `NukeBuilderDialog` to be windowed (drop `setUndecorated(true)` behind a Settings flag).
- [ ] In `SettingsPanel`, restore previous `UIManager` values in `dispose()` so other dialogs are unaffected.
- [ ] Add a "Cancel capture" hint label visible during key-bind capture mode.
- [ ] In `NukeBuilderDialog`, replace `JTextArea` labels with `JLabel` (HTML) — purely mechanical, ~30 LOC change, big readability win.
- [ ] Replace the `partsListPanel.putClientProperty(...)` hack with explicit fields.

---

*End of UI Status Report &amp; Overhaul Plan.*
