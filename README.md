# Modern Tetris

A complete implementation of Tetris following the **Tetris Guideline** (modern rule set), built with Java 17 and JavaFX.

## Features

### Core Mechanics (Tetris Guideline Compliant)
- **7-Bag Randomizer** — Pieces are dealt from shuffled bags of all 7 types, guaranteeing fair distribution (max gap between same piece ≤ 12)
- **Super Rotation System (SRS)** — Full wall kick support for all piece types with 5 kick tests per rotation, including proper I-piece offsets
- **Hold Piece** — Swap the active piece into hold (once per piece drop)
- **Ghost Piece** — Translucent preview showing where the piece will land
- **Next Queue** — Shows the next 5 upcoming pieces
- **Lock Delay** — 500ms lock delay with up to 15 move/rotation resets before forced lock
- **DAS/ARR** — Delayed Auto-Shift (167ms) and Auto-Repeat Rate (33ms) for smooth movement
- **Soft Drop** — Accelerated downward movement (1 point per cell)
- **Hard Drop** — Instant placement (2 points per cell)

### Scoring System
| Action | Points |
|--------|--------|
| Single | 100 × level |
| Double | 300 × level |
| Triple | 500 × level |
| Tetris | 800 × level |
| T-Spin | 400 × level |
| T-Spin Single | 800 × level |
| T-Spin Double | 1200 × level |
| T-Spin Triple | 1600 × level |
| Mini T-Spin | 100 × level |
| Mini T-Spin Single | 200 × level |
| Back-to-Back bonus | 1.5× multiplier |
| Combo | 50 × combo count × level |
| Perfect Clear (Single) | 800 × level |
| Perfect Clear (Double) | 1200 × level |
| Perfect Clear (Triple) | 1800 × level |
| Perfect Clear (Tetris) | 2000 × level |

### T-Spin Detection
- **Full T-Spin**: 3-corner rule — at least 3 of the 4 corners of the T-piece's bounding box must be occupied
- **Mini T-Spin**: T-Spin conditions met but front corners not both filled (unless kick test 5 was used)

### Level Progression
- Level advances every 10 lines cleared
- Gravity speed: `(0.8 - (level-1) × 0.007) ^ (level-1)` seconds per row
- Starts at level 1 (~1 second per row), reaching near-instant gravity at high levels

### Playfield
- 10 columns × 40 rows (20 visible + 20 buffer zone above)
- Row 0 = bottom, coordinates increase upward

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
│   ├── Piece.java           # 7 tetromino types with shapes, colors, rotations
│   ├── Board.java           # 10×40 playfield grid, collision detection, line clearing
│   ├── WallKickData.java    # SRS wall kick offset tables (JLSTZ + I-piece)
│   ├── BagRandomizer.java   # 7-bag random piece generator with preview queue
│   ├── ScoreSystem.java     # Scoring, levels, combos, B2B, perfect clears
│   └── GameState.java       # Core game engine (gravity, locking, DAS/ARR, hold, ghost)
└── ui/
    ├── TetrisApp.java       # JavaFX application entry point and input handling
    └── GameRenderer.java    # Canvas-based rendering (board, pieces, HUD)

src/test/java/com/tetris/model/
├── PieceTest.java           # Piece shapes, colors, spawn positions
├── BoardTest.java           # Grid operations, collisions, line clears
├── WallKickDataTest.java    # SRS kick table validation
├── BagRandomizerTest.java   # 7-bag distribution and preview queue
├── ScoreSystemTest.java     # Scoring rules, combos, B2B, levels
└── GameStateTest.java       # Game logic integration tests
```

## Architecture

### Model Layer (`com.tetris.model`)
The game logic is completely separated from the UI. All state is managed by `GameState`, which composes:
- `Board` — the grid of placed blocks
- `BagRandomizer` — the piece sequence generator
- `ScoreSystem` — scoring and level tracking

This separation allows the game to be tested without any UI dependency (103 unit tests).

### UI Layer (`com.tetris.ui`)
The UI uses JavaFX `Canvas` for rendering, providing:
- 3D-effect block rendering with highlights and shadows
- Ghost piece visualization
- Hold and next-queue panels
- Score/level/lines display
- Combo and back-to-back indicators
- Game over and pause overlays

### Game Loop
The game loop runs via JavaFX `AnimationTimer` at display refresh rate (~60fps). Each frame:
1. Processes DAS/ARR for held movement keys
2. Applies gravity
3. Updates lock delay timer
4. Renders the frame
