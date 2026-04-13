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