package com.tetris.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Piece enum covering shapes, colors, rotation states,
 * and spawn positions.
 */
class PieceTest {

    @Test
    void allPiecesExist() {
        assertEquals(7, Piece.values().length);
    }

    @Test
    void eachPieceHas4Cells() {
        for (Piece piece : Piece.values()) {
            for (int rot = 0; rot < 4; rot++) {
                int[][] cells = piece.getCells(rot);
                assertEquals(4, cells.length,
                        piece + " rotation " + rot + " should have 4 cells");
            }
        }
    }

    @Test
    void iPieceBoundingBoxIs4() {
        assertEquals(4, Piece.I.getBoundingBox());
    }

    @Test
    void otherPiecesBoundingBoxIs3() {
        for (Piece piece : new Piece[]{Piece.O, Piece.T, Piece.S, Piece.Z, Piece.J, Piece.L}) {
            assertEquals(3, piece.getBoundingBox(),
                    piece + " should have bounding box 3");
        }
    }

    @Test
    void colorsAreDistinct() {
        int[] colors = new int[Piece.values().length];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = Piece.values()[i].getColor();
        }
        for (int i = 0; i < colors.length; i++) {
            for (int j = i + 1; j < colors.length; j++) {
                assertNotEquals(colors[i], colors[j],
                        Piece.values()[i] + " and " + Piece.values()[j] +
                        " should have different colors");
            }
        }
    }

    @Test
    void colorHexFormat() {
        for (Piece piece : Piece.values()) {
            String hex = piece.getColorHex();
            assertTrue(hex.startsWith("#"), "Color hex should start with #");
            assertEquals(7, hex.length(), "Color hex should be 7 chars (#RRGGBB)");
        }
    }

    @Test
    void iPieceColor() {
        assertEquals(0x00FFFF, Piece.I.getColor()); // Cyan
    }

    @Test
    void oPieceColor() {
        assertEquals(0xFFFF00, Piece.O.getColor()); // Yellow
    }

    @Test
    void tPieceColor() {
        assertEquals(0xAA00FF, Piece.T.getColor()); // Purple
    }

    @Test
    void spawnColumnIsCentered() {
        // All pieces spawn at column 3 (centered in 10-wide field)
        for (Piece piece : Piece.values()) {
            assertEquals(3, piece.getSpawnColumn(),
                    piece + " should spawn at column 3");
        }
    }

    @Test
    void spawnRowIsAboveVisible() {
        for (Piece piece : Piece.values()) {
            int spawnRow = piece.getSpawnRow();
            assertTrue(spawnRow >= 19,
                    piece + " should spawn at or above visible area");
        }
    }

    @Test
    void rotationWrapsAround() {
        Piece piece = Piece.T;
        // getCells should handle rotation values > 3
        int[][] cells4 = piece.getCells(4); // Should wrap to 0
        int[][] cells0 = piece.getCells(0);
        assertArrayEquals(cells0[0], cells4[0]);
    }

    @Test
    void oPieceAllRotationsIdentical() {
        for (int r = 0; r < 4; r++) {
            int[][] cells = Piece.O.getCells(r);
            int[][] cells0 = Piece.O.getCells(0);
            for (int i = 0; i < 4; i++) {
                assertArrayEquals(cells0[i], cells[i],
                        "O-piece rotation " + r + " should match rotation 0");
            }
        }
    }

    @Test
    void iPieceSpawnIsHorizontal() {
        // I-piece rotation 0 should be a horizontal line in row 1
        int[][] cells = Piece.I.getCells(0);
        int commonY = cells[0][1];
        for (int[] cell : cells) {
            assertEquals(commonY, cell[1],
                    "I-piece spawn should be a horizontal line");
        }
    }
}
