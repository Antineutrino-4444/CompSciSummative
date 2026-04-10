package com.tetris.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Piece enum (particle-themed pieces).
 */
class PieceTest {

    @Test
    void allPiecesHaveCorrectCellCounts() {
        // Top/Bottom quark A: 3 cells (L-tromino)
        assertEquals(3, Piece.TOP_QUARK_A.getCellCount());
        assertEquals(3, Piece.BOTTOM_QUARK_A.getCellCount());
        // Top/Bottom quark B: 3 cells (line tromino)
        assertEquals(3, Piece.TOP_QUARK_B.getCellCount());
        assertEquals(3, Piece.BOTTOM_QUARK_B.getCellCount());
        // Gluon: 1 cell
        assertEquals(1, Piece.GLUON.getCellCount());
    }

    @Test
    void cellCountsMatchCellsArray() {
        for (Piece piece : Piece.values()) {
            for (int rot = 0; rot < 4; rot++) {
                int[][] cells = piece.getCells(rot);
                assertEquals(piece.getCellCount(), cells.length,
                        piece.name() + " rotation " + rot + " cell count mismatch");
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
        assertEquals(2, Piece.TOP_QUARK_A.getBoundingBox());
        assertEquals(3, Piece.TOP_QUARK_B.getBoundingBox());
        assertEquals(2, Piece.BOTTOM_QUARK_A.getBoundingBox());
        assertEquals(3, Piece.BOTTOM_QUARK_B.getBoundingBox());
        assertEquals(1, Piece.GLUON.getBoundingBox());
    }

    @Test
    void topQuarksAreTopType() {
        assertTrue(Piece.TOP_QUARK_A.isTopQuark());
        assertTrue(Piece.TOP_QUARK_B.isTopQuark());
        assertFalse(Piece.TOP_QUARK_A.isBottomQuark());
        assertFalse(Piece.TOP_QUARK_A.isGluon());
        assertTrue(Piece.TOP_QUARK_A.isQuark());
    }

    @Test
    void bottomQuarksAreBottomType() {
        assertTrue(Piece.BOTTOM_QUARK_A.isBottomQuark());
        assertTrue(Piece.BOTTOM_QUARK_B.isBottomQuark());
        assertFalse(Piece.BOTTOM_QUARK_A.isTopQuark());
        assertFalse(Piece.BOTTOM_QUARK_A.isGluon());
        assertTrue(Piece.BOTTOM_QUARK_A.isQuark());
    }

    @Test
    void gluonIsGluonType() {
        assertTrue(Piece.GLUON.isGluon());
        assertFalse(Piece.GLUON.isTopQuark());
        assertFalse(Piece.GLUON.isBottomQuark());
        assertFalse(Piece.GLUON.isQuark());
    }

    @Test
    void particleTypeEnum() {
        assertEquals(Piece.ParticleType.TOP_QUARK, Piece.TOP_QUARK_A.getParticleType());
        assertEquals(Piece.ParticleType.BOTTOM_QUARK, Piece.BOTTOM_QUARK_A.getParticleType());
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
    void quarkLabels() {
        assertEquals("u", Piece.TOP_QUARK_A.getLabel());
        assertEquals("u", Piece.TOP_QUARK_B.getLabel());
        assertEquals("d", Piece.BOTTOM_QUARK_A.getLabel());
        assertEquals("d", Piece.BOTTOM_QUARK_B.getLabel());
        assertEquals("g", Piece.GLUON.getLabel());
    }

    @Test
    void allPiecesHaveSpawnColumns() {
        for (Piece piece : Piece.values()) {
            assertEquals(4, piece.getSpawnColumn(),
                    piece.name() + " should spawn at column 4");
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
        int[][] r0 = Piece.GLUON.getCells(0);
        for (int rot = 1; rot < 4; rot++) {
            int[][] rx = Piece.GLUON.getCells(rot);
            assertArrayEquals(r0, rx, "Gluon rotation " + rot + " should match rotation 0");
        }
    }

    @Test
    void rotationWrapsAround() {
        Piece piece = Piece.TOP_QUARK_A;
        assertArrayEquals(piece.getCells(0), piece.getCells(4));
        assertArrayEquals(piece.getCells(1), piece.getCells(5));
        assertArrayEquals(piece.getCells(3), piece.getCells(-1));
    }

    @Test
    void fivePieceTypes() {
        assertEquals(5, Piece.values().length,
                "Should have exactly 5 particle piece types");
    }

    @Test
    void cellsAreWithinBoundingBox() {
        for (Piece piece : Piece.values()) {
            int bb = piece.getBoundingBox();
            for (int rot = 0; rot < 4; rot++) {
                int[][] cells = piece.getCells(rot);
                for (int[] cell : cells) {
                    assertTrue(cell[0] >= 0 && cell[0] < bb + 1,
                            piece.name() + " rot " + rot + " col " + cell[0] + " out of bounds");
                    assertTrue(cell[1] >= 0 && cell[1] < bb + 1,
                            piece.name() + " rot " + rot + " row " + cell[1] + " out of bounds");
                }
            }
        }
    }
}
