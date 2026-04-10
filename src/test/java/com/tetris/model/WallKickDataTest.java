package com.tetris.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WallKickData with particle-themed pieces.
 */
class WallKickDataTest {

    @Test
    void lineTriominoUsesLineKicks() {
        for (Piece p : new Piece[]{Piece.TOP_QUARK_B, Piece.BOTTOM_QUARK_B}) {
            int[][] kicks = WallKickData.getKicks(p, 0, 1);
            assertNotNull(kicks, p.name() + " should have kick data");
            assertTrue(kicks.length >= 3, p.name() + " should have multiple kick tests");
        }
    }

    @Test
    void ljTriominoUsesLJKicks() {
        for (Piece p : new Piece[]{Piece.TOP_QUARK_A, Piece.BOTTOM_QUARK_A}) {
            int[][] kicks = WallKickData.getKicks(p, 0, 1);
            assertNotNull(kicks, p.name() + " should have kick data");
            assertTrue(kicks.length >= 3, p.name() + " should have multiple kick tests");
        }
    }

    @Test
    void gluonUsesLineKicks() {
        int[][] kicks = WallKickData.getKicks(Piece.GLUON, 0, 1);
        assertNotNull(kicks, "Gluon domino should have kick data");
        assertTrue(kicks.length >= 3, "Gluon domino should have multiple kick tests");
        assertArrayEquals(new int[]{0, 0}, kicks[0], "First gluon kick should be (0,0)");
    }

    @Test
    void sameRotationReturnsNoOp() {
        for (Piece p : Piece.values()) {
            int[][] kicks = WallKickData.getKicks(p, 2, 2);
            assertNotNull(kicks);
            assertEquals(1, kicks.length);
            assertArrayEquals(new int[]{0, 0}, kicks[0]);
        }
    }

    @Test
    void firstKickIsAlwaysZeroOffset() {
        for (Piece p : new Piece[]{Piece.TOP_QUARK_A, Piece.BOTTOM_QUARK_A,
                                    Piece.TOP_QUARK_B, Piece.BOTTOM_QUARK_B}) {
            for (int from = 0; from < 4; from++) {
                int to = (from + 1) & 3;
                int[][] kicks = WallKickData.getKicks(p, from, to);
                assertArrayEquals(new int[]{0, 0}, kicks[0],
                        "First kick should be (0,0) for " + p.name());
            }
        }
    }

    @Test
    void allRotationTransitionsHaveData() {
        for (Piece p : new Piece[]{Piece.TOP_QUARK_A, Piece.TOP_QUARK_B}) {
            for (int from = 0; from < 4; from++) {
                for (int to = 0; to < 4; to++) {
                    int[][] kicks = WallKickData.getKicks(p, from, to);
                    assertNotNull(kicks, p.name() + " " + from + "->" + to + " should have kicks");
                    assertTrue(kicks.length >= 1);
                }
            }
        }
    }
}
