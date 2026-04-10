# Particle Tetris — Quark Forge

A Tetris-based particle physics puzzle game where you combine **quarks** and **gluons** to create **hadrons**. Built with Java 17 and JavaFX.

## Concept

Instead of traditional tetrominoes, you play with subatomic particles — **Top Quarks**, **Bottom Quarks**, and **Gluons**. The core Tetris mechanics remain: pieces fall, you stack them, full lines clear. But now, when the right combination of quarks and gluons land adjacent to each other on the board, they fuse into **hadrons** (composite particles like Protons and Neutrons), the participating cells are consumed, and the hadron is added to your discovery log.

## Particles (Falling Pieces)

| Piece | Shape | Type | Color Charges |
|-------|-------|------|---------------|
| Top Quark (Red) | T-shape | Quark (+2/3) | Red |
| Top Quark (Green) | S-shape | Quark (+2/3) | Green |
| Top Quark (Blue) | Z-shape | Quark (+2/3) | Blue |
| Bottom Quark (Red) | J-shape | Quark (−1/3) | Dark Red |
| Bottom Quark (Green) | L-shape | Quark (−1/3) | Dark Green |
| Bottom Quark (Blue) | I-shape | Quark (−1/3) | Dark Blue |
| Gluon | O-shape (2×2) | Force Carrier | Gold |

Each piece has a pixel-art icon drawn inside its cells to distinguish particle types.

## Hadron Recipes

When adjacent cells on the board contain the right mix of particles, they fuse:

| Hadron | Recipe | How to Create |
|--------|--------|---------------|
| **Proton** | 2 Top + 1 Bottom Quark | 3 connected cells: 2 top quarks + 1 bottom quark |
| **Neutron** | 1 Top + 2 Bottom Quark | 3 connected cells: 1 top quark + 2 bottom quarks |
| **Pion π⁺** | Top Quark + Gluon | Any top quark touching a gluon |
| **Pion π⁻** | Bottom Quark + Gluon | Any bottom quark touching a gluon |
| **Pion π⁰** | 2 Same Quarks + Gluon | Gluon between 2 quarks of the same flavor |

"Connected" means orthogonally adjacent (up/down/left/right — no diagonals).

When a hadron forms:
1. The participating cells are consumed (removed from the board)
2. Cells above drop down (gravity)
3. The hadron is recorded in your discovery panel

## Controls

| Key | Action |
|-----|--------|
| ← / A | Move left |
| → / D | Move right |
| ↓ / S | Soft drop |
| Space | Hard drop |
| ↑ / X | Rotate clockwise |
| Z / Ctrl | Rotate counter-clockwise |
| C / Shift | Hold piece |
| Escape / F1 | Pause/Resume |
| R | Restart (when game over) |

## Features

### Core Tetris Mechanics (Guideline Compliant)
- **7-Bag Randomizer** — Fair distribution of all 7 particle types
- **Super Rotation System (SRS)** — Full wall kick support
- **Hold Piece** — Swap the active piece into hold (once per drop)
- **Ghost Piece** — Preview of where the piece will land
- **Next Queue** — Shows the next 5 upcoming particles
- **Lock Delay** — 500ms with up to 15 move resets
- **DAS/ARR** — Smooth held-key movement
- **Line Clearing** — Full lines still clear (it's still Tetris!)

### Particle Physics Layer
- **Hadron Detection** — Automatic scanning for valid particle combinations after each piece locks
- **Cell Consumption** — Hadron-forming cells are removed, creating gaps (with gravity!)
- **Discovery Panel** — Pixel-art display of all discovered hadrons with counts
- **Recipe Guide** — On-screen reference for all hadron recipes

### Visual Style
- **Pixel Art** — Each particle and hadron has a distinct pixel-art icon
- **Dark Space Theme** — Deep-space background with neon accents
- **Particle Labels** — Cells show particle type icons inside

## Building & Running

### Prerequisites
- Java 17+
- Maven 3.8+

### Build
```bash
mvn compile
```

### Run Tests
```bash
mvn test
```

### Run the Game
```bash
mvn javafx:run
```

## Project Structure

```
src/main/java/com/tetris/
├── model/
│   ├── Piece.java           # 7 particle types (quarks + gluon) with shapes and pixel art
│   ├── Board.java           # 10×40 playfield grid, collision detection, line clearing
│   ├── WallKickData.java    # SRS wall kick offset tables
│   ├── BagRandomizer.java   # 7-bag random particle generator with preview queue
│   ├── Hadron.java          # 5 hadron types with recipes, pixel art, and metadata
│   ├── HadronDetector.java  # Scans board for valid particle combinations
│   └── GameState.java       # Core game engine (gravity, locking, hadron detection)
└── ui/
    ├── TetrisApp.java       # JavaFX application entry point
    └── GameRenderer.java    # Canvas-based rendering (particles, hadrons, pixel art)

src/test/java/com/tetris/model/
├── PieceTest.java           # Particle piece shapes, types, pixel art
├── BoardTest.java           # Grid operations, collisions, line clears
├── WallKickDataTest.java    # SRS kick table validation for particle pieces
├── BagRandomizerTest.java   # 7-bag distribution
├── HadronTest.java          # Hadron enum properties and recipes
├── HadronDetectorTest.java  # Hadron detection, cell consumption, gravity
└── GameStateTest.java       # Game logic integration tests
```

## Architecture

### Model Layer
All game logic is UI-independent and fully testable (86 unit tests). The key addition over standard Tetris is `HadronDetector`, which scans the board for adjacent particle combinations after each piece locks.

### Hadron Detection Algorithm
1. After a piece locks, collect all cells of the placed piece and their neighbors
2. Search for connected groups matching baryon recipes (3-cell: Proton, Neutron)
3. Search for neutral pion patterns (3-cell: gluon between 2 same quarks)
4. Search for charged pion pairs (2-cell: quark + gluon)
5. Consume matched cells, apply column gravity

### UI Layer
Canvas-based rendering with:
- Pixel-art particle cells (4×4 icon grid per cell)
- 8×8 pixel-art hadron icons in the discovery panel
- Recipe legend and particle type guide
- "Containment Breach" game over (because physics!)
