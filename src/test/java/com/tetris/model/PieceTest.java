package com.tetris.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Piece enum (particle-themed pieces).
 */
class PieceTest {

    @Test
    void allPiecesHaveFourCells() {
        for (Piece piece : Piece.values()) {
            for (int rot = 0; rot < 4; rot++) {
                int[][] cells = piece.getCells(rot);
                assertEquals(4, cells.length,
                        piece.name() + " rotation " + rot + " should have 4 cells");
            }
        }
    }

    @Test
    void allPiecesHaveValidColor() {
        for (Piece piece : Piece.values()) {
            assertTrue(piece.getColor() > 0, piece.name() + " color should be positive");
            assertNotNull(piece.getColorHex());
            assertTrue(piece.getColorHex().startsWith("#"));
        }
    }

    @Test
    void boundingBoxSizes() {
        // Bottom Quark Blue (I-equivalent) has 4×4
        assertEquals(4, Piece.BOTTOM_QUARK_B.getBoundingBox());
        // All others have 3×3
        assertEquals(3, Piece.TOP_QUARK_R.getBoundingBox());
        assertEquals(3, Piece.TOP_QUARK_G.getBoundingBox());
        assertEquals(3, Piece.TOP_QUARK_B.getBoundingBox());
        assertEquals(3, Piece.BOTTOM_QUARK_R.getBoundingBox());
        assertEquals(3, Piece.BOTTOM_QUARK_G.getBoundingBox());
        assertEquals(3, Piece.GLUON.getBoundingBox());
    }

    @Test
    void topQuarksAreTopType() {
        assertTrue(Piece.TOP_QUARK_R.isTopQuark());
        assertTrue(Piece.TOP_QUARK_G.isTopQuark());
        assertTrue(Piece.TOP_QUARK_B.isTopQuark());
        assertFalse(Piece.TOP_QUARK_R.isBottomQuark());
        assertFalse(Piece.TOP_QUARK_R.isGluon());
    }

    @Test
    void bottomQuarksAreBottomType() {
        assertTrue(Piece.BOTTOM_QUARK_R.isBottomQuark());
        assertTrue(Piece.BOTTOM_QUARK_G.isBottomQuark());
        assertTrue(Piece.BOTTOM_QUARK_B.isBottomQuark());
        assertFalse(Piece.BOTTOM_QUARK_R.isTopQuark());
        assertFalse(Piece.BOTTOM_QUARK_R.isGluon());
    }

    @Test
    void gluonIsGluonType() {
        assertTrue(Piece.GLUON.isGluon());
        assertFalse(Piece.GLUON.isTopQuark());
        assertFalse(Piece.GLUON.isBottomQuark());
    }

    @Test
    void particleTypeEnum() {
        assertEquals(Piece.ParticleType.TOP_QUARK, Piece.TOP_QUARK_R.getParticleType());
        assertEquals(Piece.ParticleType.BOTTOM_QUARK, Piece.BOTTOM_QUARK_R.getParticleType());
        assertEquals(Piece.ParticleType.GLUON, Piece.GLUON.getParticleType());
    }

    @Test
    void allPiecesHaveLabels() {
        for (Piece piece : Piece.values()) {
            assertNotNull(piece.getLabel());
            assertFalse(piece.getLabel().isEmpty());
        }
    }

    @Test
    void allPiecesHavePixelArt() {
        for (Piece piece : Piece.values()) {
            String[] art = piece.getPixelArt();
            assertNotNull(art);
            assertEquals(4, art.length, piece.name() + " should have 4-row pixel art");
        }
    }

    @Test
    void allPiecesHaveSpawnColumns() {
        for (Piece piece : Piece.values()) {
            assertEquals(3, piece.getSpawnColumn(),
                    piece.name() + " should spawn at column 3");
        }
    }

    @Test
    void allPiecesHaveSpawnRows() {
        for (Piece piece : Piece.values()) {
            assertEquals(19, piece.getSpawnRow());
        }
    }

    @Test
    void gluonDoesNotRotate() {
        // All 4 rotation states should be identical for Gluon (O-equivalent)
        int[][] r0 = Piece.GLUON.getCells(0);
        for (int rot = 1; rot < 4; rot++) {
            int[][] rx = Piece.GLUON.getCells(rot);
            assertArrayEquals(r0, rx, "Gluon rotation " + rot + " should match rotation 0");
        }
    }

    @Test
    void rotationWrapsAround() {
        Piece piece = Piece.TOP_QUARK_R;
        assertArrayEquals(piece.getCells(0), piece.getCells(4));
        assertArrayEquals(piece.getCells(1), piece.getCells(5));
        assertArrayEquals(piece.getCells(3), piece.getCells(-1));
    }

    @Test
    void sevenPieceTypes() {
        assertEquals(7, Piece.values().length,
                "Should have exactly 7 particle piece types");
    }
}
