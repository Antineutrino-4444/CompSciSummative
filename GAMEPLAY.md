# Particle Tetris — Full Gameplay Rundown

## The Concept

This is Tetris re-skinned as a **particle physics simulation**. Instead of standard tetrominoes (I, O, T, S, Z, L, J), you play with **quarks** and **gluons** — the subatomic particles that make up protons and neutrons. The core mechanic is **hadron formation**: you build composite particles by strategically connecting quarks through gluon bridges. Line clears are removed — the **only** way to remove cells from the board is by forming hadrons.

---

## The Pieces (5 Types)

There are **5 piece shapes** dispensed from a weighted 7-piece bag (2 top quarks, 2 bottom quarks, 3 gluons per bag). Shape variants (A/B) are chosen randomly per quark slot:

| Piece | Shape | Size | Color | Label |
|-------|-------|------|-------|-------|
| **Top Quark A** | L-tromino (like a small L) | 3 cells, 2×2 bounding box | Red (`#FF4444`) | "u" |
| **Top Quark B** | Straight tromino (line of 3) | 3 cells, 3×1 bounding box | Red (`#FF4444`) | "u" |
| **Bottom Quark A** | J-tromino (mirror-L) | 3 cells, 2×2 bounding box | Green (`#44CC44`) | "d" |
| **Bottom Quark B** | Straight tromino (line of 3) | 3 cells, 3×1 bounding box | Green (`#44CC44`) | "d" |
| **Gluon** | Domino (2 cells) | 2 cells, rotates horizontal↔vertical | Gold (`#FFCC00`) | "g" |

Both top quark variants share the same red color and "u" label — color tells you the **particle type**, not the shape. Same for bottom quarks (both green, "d"). This eliminates confusion about whether color variants are mechanically different particles.

All pieces are **smaller than normal Tetris pieces** — quarks are 3-cell trominoes and gluons are 2-cell dominoes. This makes the 10-wide board feel more spacious and gives more room for strategic placement.

Each piece is drawn as a **colored sphere/ball** with a letter label ("u", "d", or "g") inside, rendered on a dark space-themed background.

---

## Controls (Standard Modern Tetris)

| Action | Keys |
|--------|------|
| Move left | Left Arrow / A |
| Move right | Right Arrow / D |
| Soft drop | Down Arrow / S (held = 20 cells/sec) |
| Hard drop | Space |
| Rotate CW | Up Arrow / X |
| Rotate CCW | Z / Control |
| Hold | C / Shift |
| Pause | Escape / F1 |
| Restart | R (game over only) |

---

## Core Mechanics

All standard modern Tetris mechanics are implemented with particle-physics-specific tuning:

1. **Gravity**: Pieces fall at a speed determined by level. The formula is `(0.8 − (level−1) × 0.007)^(level−1)` seconds per row — same as guideline Tetris. Level 1 starts at ~1 second/row, getting faster each level.

2. **DAS/ARR (Delayed Auto-Shift / Auto-Repeat Rate)**: When you hold left/right, there's a 167ms delay before auto-repeat kicks in, then the piece moves at 33ms intervals (about 30 cells/second). This matches competitive Tetris feel.

3. **Lock Delay**: When a piece lands on a surface, you have **500ms** to move/rotate it before it locks. Each move/rotation resets this timer (up to **8 resets max**). No infinite-manipulation exception — this prevents stalling in a puzzle game.

4. **Wall Kicks**: Rotation uses simplified SRS (Super Rotation System) wall kicks. L/J trominoes try 5 offsets (center, left, right, up, down). Line trominoes and gluons try 6 offsets. If a rotation would cause a collision, the system tries these alternative positions before giving up.

5. **Hold Piece**: You can hold one piece (C or Shift). Swaps your current piece with the held piece, or stores the current and draws from the bag if hold was empty. **Hold is freely reusable** — no once-per-piece restriction. The puzzle is already hard enough.

6. **Ghost Piece**: A transparent preview shows where your piece would land if you hard-dropped right now.

7. **Next Queue**: Shows the upcoming **3 pieces** in the right panel.

8. **Weighted 7-Piece Bag**: Each bag contains 2 top quarks, 2 bottom quarks, and 3 gluons (~43% gluons), shuffled together. The A/B shape variant for quarks is chosen randomly per slot. Gluons are the most common piece since every hadron recipe requires them.

9. **Undo**: You get **2 undo uses per game** — press to reverse your last piece placement and try again. Board state is fully restored.

---

## The Hadron Formation Mechanic (The Core Loop)

After every piece locks, the game scans for **hadron formations** — composite particles made by combining quarks through gluon bridges. **This is the only way to remove cells from the board.**

**The key rule: quarks adjacent to each other do NOT automatically combine. They MUST be connected THROUGH gluon cells.**

### The Three Hadron Recipes

| Hadron | Recipe | Color |
|--------|--------|-------|
| **Proton** (uud) | 2 Top quarks + 1 Bottom quark + at least 2 gluons connecting them | Red (`#FF4444`) |
| **Neutron** (udd) | 1 Top quark + 2 Bottom quarks + at least 2 gluons connecting them | Blue (`#4488FF`) |
| **Pion** (ud̄) | 1 Top quark + 1 Bottom quark + 1 gluon between them | Orange (`#FFAA00`) |

The detector tries **larger recipes first** (Proton > Neutron > Pion), so if you have enough quarks connected, the game prefers to form protons/neutrons over pions.

### How Detection Works (Step by Step)

When a piece locks:

1. **Find gluon clusters**: Starting from gluon cells near the placed piece, BFS (breadth-first search) to find all connected gluon cells (gluons touching gluons form a continuous gluon network).

2. **Collect adjacent quarks**: Any quark cell that is **individually** orthogonally adjacent to any gluon in the cluster is included. Only cells touching a gluon participate — no quark-to-quark chaining.

3. **Match recipes**: Check if the collected quarks match any hadron recipe. Gluons are sorted by adjacency to quarks so the "bridging" gluon (the one actually between quarks) is consumed first.

4. **Consume cells**: All participating quark and gluon cells are removed from the board.

5. **Sticky gravity**: Connected groups of remaining cells fall as rigid units. A floating 3-cell chunk falls together until it lands on something — preserving the structures you built.

6. **Cascade detection**: After gravity, detection runs again. If new hadrons form, they cascade — each iteration multiplies the combo counter (×1, ×2, ×4, ×8). This continues until no more hadrons form.

### Strategic Implications

- **Pion is the easiest**: Place one "u" quark, one "d" quark, and one gluon between them. Just 3 cells consumed.
- **Proton/Neutron are harder**: You need 5+ cells arranged with gluon bridges connecting all quarks.
- **Gluons are the critical piece**: Without gluons, quarks sitting next to each other do nothing. The gluon is the "glue" that makes everything work.
- **Planning is key**: You can place quarks first, leaving gaps, then drop gluons into the gaps to complete the bridge and trigger hadron formation.
- **Precise quark control**: Only quark cells individually touching a gluon participate. If you drop a 3-cell line quark and only the tip touches the bridge, only that tip is consumed. This makes pion-vs-proton a deliberate choice.
- **Cascade combos**: Setting up chain reactions where one hadron's consumption causes cells to fall into position for the next is the key to high scores.

---

## Scoring

| Event | Base Score |
|-------|-----------|
| Pion formed | 100 |
| Proton formed | 400 |
| Neutron formed | 400 |

**Cascade combo multipliers**: When hadrons chain within a single lock event:
- 1st hadron: ×1
- 2nd hadron: ×2
- 3rd hadron: ×4
- 4th+ hadron: ×8

Score is the primary progression metric displayed on the HUD.

---

## The Formation Animation

When a hadron forms, a **0.6-second two-phase animation** plays on the consumed cells:

- **Phase 1 (0–0.3s)**: Expanding glow rings and bright white/colored flashes appear on each consumed cell position, drawing attention to which cells are being consumed.
- **Phase 2 (0.3–0.6s)**: The glowing particles converge toward the center of the formation, shrinking and fading. A growing burst of the hadron's color appears at the center point.

The animation is overlaid on the playfield and uses the hadron's signature color (red for proton, blue for neutron, orange for pion).

---

## Visual Connections: Gluon Bridges

On the board, whenever a gluon cell is orthogonally adjacent to a quark or another gluon, a **gold connecting line** is drawn between their centers. This gives a visual indicator of which particles are "bound" — you can see the strong force connections forming as you build.

- Gluon-to-quark bridges: brighter gold lines (60% opacity)
- Gluon-to-gluon bridges: slightly dimmer (40% opacity)

---

## UI Layout

The screen layout:
- **Left panel**: Hold box (top-left), then below it: Level number, Score, Particles contained, a particle legend (showing what u/d/g look like), and recipe hints.
- **Center**: The 10×20 playfield with dark-space background and subtle grid lines.
- **Right panel**: Next queue (3 upcoming pieces), then below: "Discovered" hadron panel showing Proton, Neutron, and Pion with their icons, how many you've formed, and recipe descriptions.

The hadron discovery panel shows each hadron type with a **composite icon** (small balls arranged to show the quark composition with bridge lines between them). Undiscovered hadrons are dimmed to 20% opacity as a teaser. Once you form one, it lights up with a golden "×N" count.

---

## Game Over

The game ends (**"Containment Breach"**) in two ways:
1. **Block out**: A new piece spawns and immediately collides — the board is too full.
2. **Lock out**: A piece locks entirely above the visible 20-row playfield (all cells in the buffer zone).

A dark overlay appears with "CONTAINMENT BREACH" in red and "Press R to restart."

---

## Leveling

- Every **5 particles contained** advances you one level.
- Higher levels = faster gravity (pieces fall quicker).
- Level is displayed in the left panel.
- Score tracks your performance with combo multipliers rewarding chain reactions.

---

## Summary: What Makes This Different From Normal Tetris

1. **Smaller pieces** (2-3 cells instead of 4) = more granular placement, denser strategies.
2. **Hadrons are the only removal mechanic** — no line clears, every decision feeds into particle formation.
3. **Gluon bridge mechanic** = you're building composite particles by connecting quarks through gluon networks.
4. **Three hadron types** to discover with increasing difficulty (Pion → Neutron/Proton).
5. **Sticky gravity** after hadron consumption — connected cell groups fall as rigid units, preserving your structures.
6. **Cascade combos** — chain reactions multiply your score (×1, ×2, ×4, ×8).
7. **Score system** — real scoring with difficulty-weighted hadrons and combo multipliers.
8. **Visual theme** — particles as glowing spheres with connecting bridge lines, dark space aesthetic, physics-themed nomenclature.
