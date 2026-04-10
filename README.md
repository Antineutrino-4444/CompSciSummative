# Particle Tetris — Quark Forge

A Tetris-based particle physics puzzle game where you combine **quarks** and **gluons** to create **hadrons**. Built with Java 17 and JavaFX.

## Concept

Instead of traditional tetrominoes, you play with **Top Quarks**, **Bottom Quarks**, and **Gluons**. The core Tetris mechanics remain: pieces fall, you stack them, full lines clear. But now, the key mechanic is **gluon bridging**: quarks don't combine just by touching — they must be connected through **gluon cells** to form hadrons.

### The Gluon Mechanic

**Gluons are the heart of the strategy.** They are 2-cell domino pieces that you place strategically between quarks to create bridges. Simply stacking quarks next to each other does nothing — you need gluons to mediate the strong force:

1. Place quarks on the board (3-cell trominoes)
2. Place gluons between them (2-cell dominos, rotatable)
3. When the right quark recipe is connected through a gluon network → hadron forms!

## Particles (Falling Pieces)

| Piece | Shape | Type | Color |
|-------|-------|------|-------|
| Top Quark A | L-tromino (3 cells) | Up-type quark | Red |
| Top Quark B | Line tromino (3 cells) | Up-type quark | Blue |
| Bottom Quark A | J-tromino (3 cells) | Down-type quark | Green |
| Bottom Quark B | Line tromino (3 cells) | Down-type quark | Purple |
| Gluon | Domino (2 cells) | Force Carrier | Gold |

Each particle is rendered as a colored circle with a letter label (u/d/g).

## Hadron Recipes

Quarks must be connected **through gluons** to form hadrons:

| Hadron | Recipe | Formation |
|--------|--------|-----------|
| **Proton** (uud) | 2 Top + 1 Bottom + 2 Gluons | 3 quarks all adjacent to a connected gluon network |
| **Neutron** (udd) | 1 Top + 2 Bottom + 2 Gluons | 3 quarks all adjacent to a connected gluon network |
| **Pion** (ud) | 1 Top + 1 Bottom + 1 Gluon | 2 quarks bridged by a single gluon |

When a hadron forms:
1. Only the cells used in the recipe are consumed (unused quarks stay!)
2. Cells above drop down (gravity)
3. The hadron is recorded in your discovery panel

### Visual: Gluon Bridges
Adjacent gluon-quark pairs show **golden connecting lines** on the board, so you can see your bridge network forming in real-time.

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

### Core Tetris Mechanics
- **5-Bag Randomizer** — Fair distribution of all 5 particle types
- **Wall Kicks** — Rotation with kick tests for trominoes
- **Hold Piece** — Swap the active piece into hold (once per drop)
- **Ghost Piece** — Preview of where the piece will land
- **Next Queue** — Shows the next 5 upcoming particles
- **Lock Delay** — 500ms with up to 15 move resets
- **DAS/ARR** — Smooth held-key movement
- **Line Clearing** — Full lines still clear (it's still Tetris!)

### Particle Physics Layer
- **Gluon-Bridge Detection** — Quarks only combine when connected through gluon cells
- **Selective Consumption** — Only cells used in a recipe are consumed; extras survive
- **Gravity** — Cells above consumed gaps drop down
- **Discovery Panel** — Tracks all discovered hadrons with counts and icons

### Visual Style
- **Particle Balls** — Each particle rendered as a colored circle
- **Gluon Bridges** — Golden connecting lines between gluon-quark pairs
- **Dark Space Theme** — Deep-space background with neon accents
- **Hadron Icons** — Composite ball diagrams for each hadron type

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
│   ├── Piece.java           # 5 particle types (2 top quarks, 2 bottom quarks, gluon)
│   ├── Board.java           # 10×40 playfield grid, collision detection, line clearing
│   ├── WallKickData.java    # Wall kick offset tables for trominoes
│   ├── BagRandomizer.java   # Bag random particle generator with preview queue
│   ├── Hadron.java          # 3 hadron types (Proton, Neutron, Pion) with recipes
│   ├── HadronDetector.java  # Gluon-bridge-based hadron detection
│   └── GameState.java       # Core game engine (gravity, locking, hadron detection)
└── ui/
    ├── TetrisApp.java       # JavaFX application entry point
    └── GameRenderer.java    # Canvas-based rendering (circles, bridges, panels)
```

## Architecture

### Model Layer
All game logic is UI-independent and fully testable (99 unit tests). The key mechanic is `HadronDetector`, which uses BFS through gluon cells to find connected particle groups.

### Gluon-Bridge Detection Algorithm
1. After a piece locks, collect cells of the placed piece + neighbors
2. Find all gluon cells in the search area
3. BFS through connected gluons to build a "gluon network"
4. Collect all quarks adjacent to any gluon in the network
5. Match the quark counts against hadron recipes (largest first)
6. Consume only the cells needed for matched hadrons
7. Apply column gravity

### Why Gluons Make It Tactical
- Domino gluons (2 cells) cover more ground but must be positioned carefully
- You need to plan where to build your gluon bridges
- Gluons rotate (horizontal/vertical) for precise placement
- Baryons (Proton/Neutron) need 2 gluons — 4 cells of bridge space!
- Line clears can disrupt your bridge network
- Selective consumption means unused quarks stay for future combinations
