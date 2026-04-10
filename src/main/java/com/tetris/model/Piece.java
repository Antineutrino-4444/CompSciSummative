package com.tetris.model;

import java.util.Map;

/**
 * Represents the particle-themed falling pieces in Particle Tetris.
 *
 * <h3>Particle Types and Shapes</h3>
 * <ul>
 *   <li><b>Top Quarks</b> — 3-cell trominoes (L-shape and line variants).
 *       Red and blue color charges.</li>
 *   <li><b>Bottom Quarks</b> — 3-cell trominoes (J-shape and line variants).
 *       Green and purple color charges.</li>
 *   <li><b>Gluon</b> — Single cell. The force carrier that binds quarks.
 *       Must be placed between quarks to enable hadron formation.</li>
 * </ul>
 *
 * <h3>Gluon Mechanic</h3>
 * <p>Gluons are the key tactical element. Quarks adjacent to each other do NOT
 * automatically combine. Instead, quarks must be connected <b>through gluon cells</b>
 * to form hadrons. A gluon cell adjacent to a quark cell creates a "bridge".
 * When the correct quark recipe is connected through a gluon network, a hadron forms.</p>
 */
public enum Piece {

    // ==================== TOP QUARKS (up-type, charge +2/3) ====================

    /**
     * Top Quark A — L-tromino shape.
     * 3 cells in an L pattern.
     */
    TOP_QUARK_A(new int[][][] {
        // rot 0: ##    rot 1: #     rot 2:  #    rot 3: ##
        //         #           ##           ##           #
        {{0,0},{1,0},{1,1}},  // rot 0
        {{0,0},{0,1},{1,1}},  // rot 1
        {{0,1},{1,0},{1,1}},  // rot 2 (mirror flip effectively)
        {{0,0},{1,0},{0,1}}   // rot 3
    }, 0xFF4444, 2, ParticleType.TOP_QUARK, "u"),

    /**
     * Top Quark B — straight tromino (line of 3).
     */
    TOP_QUARK_B(new int[][][] {
        // rot 0: ###     rot 1: #
        //                       #
        //                       #
        {{0,0},{1,0},{2,0}},  // rot 0: horizontal
        {{0,0},{0,1},{0,2}},  // rot 1: vertical
        {{0,0},{1,0},{2,0}},  // rot 2: horizontal (same as 0)
        {{0,0},{0,1},{0,2}}   // rot 3: vertical (same as 1)
    }, 0x5588FF, 3, ParticleType.TOP_QUARK, "u"),

    // ==================== BOTTOM QUARKS (down-type, charge -1/3) ====================

    /**
     * Bottom Quark A — J-tromino shape (mirror of L).
     */
    BOTTOM_QUARK_A(new int[][][] {
        // rot 0: ##    rot 1: ##    rot 2: #     rot 3:  #
        //        #            #           ##          ##
        {{0,0},{1,0},{0,1}},  // rot 0
        {{0,0},{0,1},{1,0}},  // rot 1 (same as rot 0 for J)
        {{0,0},{1,1},{0,1}},  // rot 2
        {{1,0},{0,1},{1,1}}   // rot 3
    }, 0x44CC44, 2, ParticleType.BOTTOM_QUARK, "d"),

    /**
     * Bottom Quark B — straight tromino (line of 3).
     */
    BOTTOM_QUARK_B(new int[][][] {
        {{0,0},{1,0},{2,0}},  // rot 0: horizontal
        {{0,0},{0,1},{0,2}},  // rot 1: vertical
        {{0,0},{1,0},{2,0}},  // rot 2: horizontal
        {{0,0},{0,1},{0,2}}   // rot 3: vertical
    }, 0xBB66FF, 3, ParticleType.BOTTOM_QUARK, "d"),

    // ==================== GLUON (force carrier) ====================

    /**
     * Gluon — single cell. The force carrier that binds quarks together.
     * Place between quarks to create bridges for hadron formation.
     * Does not rotate (single cell has no orientation).
     */
    GLUON(new int[][][] {
        {{0,0}},  // rot 0
        {{0,0}},  // rot 1
        {{0,0}},  // rot 2
        {{0,0}}   // rot 3
    }, 0xFFCC00, 1, ParticleType.GLUON, "g");

    /**
     * Categorizes pieces into particle families for hadron detection.
     */
    public enum ParticleType {
        TOP_QUARK,
        BOTTOM_QUARK,
        GLUON
    }

    private final int[][][] cells;
    private final int color;
    private final int boundingBox;
    private final ParticleType particleType;
    private final String label;

    Piece(int[][][] cells, int color, int boundingBox, ParticleType particleType,
          String label) {
        this.cells = cells;
        this.color = color;
        this.boundingBox = boundingBox;
        this.particleType = particleType;
        this.label = label;
    }

    /**
     * Returns the cell positions for the given rotation state.
     * Each entry is {col, row} relative to the piece's bounding box origin.
     */
    public int[][] getCells(int rotation) {
        return cells[rotation & 3];
    }

    public int getColor() { return color; }
    public int getBoundingBox() { return boundingBox; }
    public String getColorHex() { return String.format("#%06X", color); }
    public ParticleType getParticleType() { return particleType; }
    public String getLabel() { return label; }

    public boolean isTopQuark() { return particleType == ParticleType.TOP_QUARK; }
    public boolean isBottomQuark() { return particleType == ParticleType.BOTTOM_QUARK; }
    public boolean isGluon() { return particleType == ParticleType.GLUON; }
    public boolean isQuark() { return isTopQuark() || isBottomQuark(); }

    /**
     * Returns the column at which this piece spawns (centered in the 10-wide playfield).
     */
    public int getSpawnColumn() {
        return 4;
    }

    /**
     * Returns the row at which this piece spawns.
     */
    public int getSpawnRow() {
        return 19;
    }

    /**
     * Returns the number of cells in this piece.
     */
    public int getCellCount() {
        return cells[0].length;
    }
}
