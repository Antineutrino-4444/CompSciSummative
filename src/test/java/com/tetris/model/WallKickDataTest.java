package com.tetris.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WallKickData with particle-themed pieces.
 */
class WallKickDataTest {

    @Test
    void bottomQuarkBlueUsesIKicks() {
        // Bottom Quark Blue (I-equivalent) should use I-piece kick data
        int[][] kicks = WallKickData.getKicks(Piece.BOTTOM_QUARK_B, 0, 1);
        assertNotNull(kicks);
        assertEquals(5, kicks.length, "Should have 5 kick tests");
    }

    @Test
    void topQuarksUseJLSTZKicks() {
        for (Piece p : new Piece[]{Piece.TOP_QUARK_R, Piece.TOP_QUARK_G, Piece.TOP_QUARK_B}) {
            int[][] kicks = WallKickData.getKicks(p, 0, 1);
            assertNotNull(kicks, p.name() + " should have kick data");
            assertEquals(5, kicks.length, p.name() + " should have 5 kick tests");
        }
    }

    @Test
    void bottomQuarksRGUseJLSTZKicks() {
        for (Piece p : new Piece[]{Piece.BOTTOM_QUARK_R, Piece.BOTTOM_QUARK_G}) {
            int[][] kicks = WallKickData.getKicks(p, 0, 1);
            assertNotNull(kicks);
            assertEquals(5, kicks.length);
        }
    }

    @Test
    void gluonReturnsNoOpKick() {
        int[][] kicks = WallKickData.getKicks(Piece.GLUON, 0, 1);
        assertNotNull(kicks);
        assertEquals(1, kicks.length, "Gluon should have 1 kick test (no-op)");
        assertArrayEquals(new int[]{0, 0}, kicks[0]);
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
        // For JLSTZ pieces, first kick test is always (0,0)
        for (Piece p : new Piece[]{Piece.TOP_QUARK_R, Piece.BOTTOM_QUARK_R}) {
            for (int from = 0; from < 4; from++) {
                int to = (from + 1) & 3;
                int[][] kicks = WallKickData.getKicks(p, from, to);
                assertArrayEquals(new int[]{0, 0}, kicks[0],
                        "First kick should be (0,0) for " + p.name());
            }
        }
    }
}
