package com.tetris.model;

import java.util.Map;

/**
 * Represents the particle-themed falling pieces in Particle Tetris.
 *
 * <p>Instead of standard tetrominoes, the game uses quarks and gluons.
 * Each piece retains the same shape mechanics as classic Tetris (SRS rotation,
 * wall kicks) but is themed as a subatomic particle.</p>
 *
 * <h3>Particle Types</h3>
 * <ul>
 *   <li><b>Top Quarks</b> (charge +2/3): Red, Green, Blue color charges — T, S, Z shapes</li>
 *   <li><b>Bottom Quarks</b> (charge −1/3): Lighter versions — J, L, I shapes</li>
 *   <li><b>Gluon</b>: Yellow/gold — O shape (force carrier, binds quarks together)</li>
 * </ul>
 *
 * <p>Shapes and rotations follow the standard Tetris Guideline (SRS) for each
 * equivalent tetromino.</p>
 *
 * <h3>Pixel Art Labels</h3>
 * <p>Each piece type has a simple pixel art icon drawn on its cells to
 * distinguish particles visually beyond color alone.</p>
 */
public enum Piece {

    // ==================== TOP QUARKS (up-type, charge +2/3) ====================

    /**
     * Top Quark (Red) — T-shape. The "pointing" shape represents
     * the quark's spin direction.
     */
    TOP_QUARK_R(new int[][][] {
        {{1,0},{0,1},{1,1},{2,1}},
        {{1,0},{1,1},{2,1},{1,2}},
        {{0,1},{1,1},{2,1},{1,2}},
        {{1,0},{0,1},{1,1},{1,2}}
    }, 0xFF3333, 3, ParticleType.TOP_QUARK, "t",
    new String[] {
        " ## ",
        "####",
        " ## ",
        "    "
    }),

    /**
     * Top Quark (Green) — S-shape.
     */
    TOP_QUARK_G(new int[][][] {
        {{1,0},{2,0},{0,1},{1,1}},
        {{1,0},{1,1},{2,1},{2,2}},
        {{1,1},{2,1},{0,2},{1,2}},
        {{0,0},{0,1},{1,1},{1,2}}
    }, 0x33FF33, 3, ParticleType.TOP_QUARK, "t",
    new String[] {
        " ## ",
        "####",
        " ## ",
        "    "
    }),

    /**
     * Top Quark (Blue) — Z-shape.
     */
    TOP_QUARK_B(new int[][][] {
        {{0,0},{1,0},{1,1},{2,1}},
        {{2,0},{1,1},{2,1},{1,2}},
        {{0,1},{1,1},{1,2},{2,2}},
        {{1,0},{0,1},{1,1},{0,2}}
    }, 0x3388FF, 3, ParticleType.TOP_QUARK, "t",
    new String[] {
        " ## ",
        "####",
        " ## ",
        "    "
    }),

    // ==================== BOTTOM QUARKS (down-type, charge -1/3) ====================

    /**
     * Bottom Quark (Red) — J-shape.
     */
    BOTTOM_QUARK_R(new int[][][] {
        {{0,0},{0,1},{1,1},{2,1}},
        {{1,0},{2,0},{1,1},{1,2}},
        {{0,1},{1,1},{2,1},{2,2}},
        {{1,0},{1,1},{0,2},{1,2}}
    }, 0xCC4444, 3, ParticleType.BOTTOM_QUARK, "b",
    new String[] {
        "####",
        " ## ",
        " ## ",
        "    "
    }),

    /**
     * Bottom Quark (Green) — L-shape.
     */
    BOTTOM_QUARK_G(new int[][][] {
        {{2,0},{0,1},{1,1},{2,1}},
        {{1,0},{1,1},{1,2},{2,2}},
        {{0,1},{1,1},{2,1},{0,2}},
        {{0,0},{1,0},{1,1},{1,2}}
    }, 0x44CC44, 3, ParticleType.BOTTOM_QUARK, "b",
    new String[] {
        "####",
        " ## ",
        " ## ",
        "    "
    }),

    /**
     * Bottom Quark (Blue) — I-shape (straight line).
     * Uses a 4×4 bounding box for SRS rotation.
     */
    BOTTOM_QUARK_B(new int[][][] {
        {{0,1},{1,1},{2,1},{3,1}},
        {{2,0},{2,1},{2,2},{2,3}},
        {{0,2},{1,2},{2,2},{3,2}},
        {{1,0},{1,1},{1,2},{1,3}}
    }, 0x4488CC, 4, ParticleType.BOTTOM_QUARK, "b",
    new String[] {
        "####",
        " ## ",
        " ## ",
        "    "
    }),

    // ==================== GLUON (force carrier) ====================

    /**
     * Gluon — O-shape (2×2 square). The force carrier that binds quarks.
     * Does not rotate (all rotation states identical).
     */
    GLUON(new int[][][] {
        {{1,0},{2,0},{1,1},{2,1}},
        {{1,0},{2,0},{1,1},{2,1}},
        {{1,0},{2,0},{1,1},{2,1}},
        {{1,0},{2,0},{1,1},{2,1}}
    }, 0xFFCC00, 3, ParticleType.GLUON, "g",
    new String[] {
        "####",
        "#  #",
        "#  #",
        "####"
    });

    /**
     * Categorizes pieces into particle families for hadron detection.
     */
    public enum ParticleType {
        TOP_QUARK,
        BOTTOM_QUARK,
        GLUON
    }

    /**
     * The shape data for all 4 rotation states.
     * {@code cells[rotation][cellIndex]} gives {col, row} offsets within bounding box.
     */
    private final int[][][] cells;

    /** The color of this piece as a 24-bit RGB value. */
    private final int color;

    /** The size of the bounding box (3 for most pieces, 4 for bottom quark blue/I). */
    private final int boundingBox;

    /** The particle family this piece belongs to. */
    private final ParticleType particleType;

    /** Short label for the particle (e.g. "t" for top quark). */
    private final String label;

    /** 4×4 pixel art pattern for this particle type. '#' = filled pixel. */
    private final String[] pixelArt;

    Piece(int[][][] cells, int color, int boundingBox, ParticleType particleType,
          String label, String[] pixelArt) {
        this.cells = cells;
        this.color = color;
        this.boundingBox = boundingBox;
        this.particleType = particleType;
        this.label = label;
        this.pixelArt = pixelArt;
    }

    /**
     * Returns the cell positions for the given rotation state.
     *
     * @param rotation the rotation state (0=spawn, 1=R/CW, 2=180, 3=L/CCW)
     * @return array of {col, row} pairs relative to the piece's bounding box origin
     */
    public int[][] getCells(int rotation) {
        return cells[rotation & 3];
    }

    /**
     * Returns the color of this piece as a 24-bit RGB integer.
     */
    public int getColor() {
        return color;
    }

    /**
     * Returns the bounding box size for this piece.
     */
    public int getBoundingBox() {
        return boundingBox;
    }

    /**
     * Returns the color as a JavaFX-compatible hex string (e.g. "#FF3333").
     */
    public String getColorHex() {
        return String.format("#%06X", color);
    }

    /**
     * Returns the particle family this piece belongs to.
     */
    public ParticleType getParticleType() {
        return particleType;
    }

    /**
     * Returns the short label for this particle type.
     */
    public String getLabel() {
        return label;
    }

    /**
     * Returns the 4×4 pixel art pattern for this particle.
     */
    public String[] getPixelArt() {
        return pixelArt;
    }

    /**
     * Returns whether this piece is a top quark (any color charge).
     */
    public boolean isTopQuark() {
        return particleType == ParticleType.TOP_QUARK;
    }

    /**
     * Returns whether this piece is a bottom quark (any color charge).
     */
    public boolean isBottomQuark() {
        return particleType == ParticleType.BOTTOM_QUARK;
    }

    /**
     * Returns whether this piece is a gluon.
     */
    public boolean isGluon() {
        return particleType == ParticleType.GLUON;
    }

    /**
     * Spawn column — pieces spawn centered in the 10-wide playfield.
     */
    private static final Map<Piece, Integer> SPAWN_COLUMNS = Map.of(
        TOP_QUARK_R, 3, TOP_QUARK_G, 3, TOP_QUARK_B, 3,
        BOTTOM_QUARK_R, 3, BOTTOM_QUARK_G, 3, BOTTOM_QUARK_B, 3,
        GLUON, 3
    );

    /**
     * Returns the column at which this piece spawns.
     */
    public int getSpawnColumn() {
        return SPAWN_COLUMNS.get(this);
    }

    /**
     * Returns the row at which this piece spawns.
     */
    public int getSpawnRow() {
        return 19;
    }
}
