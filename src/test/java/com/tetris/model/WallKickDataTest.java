package com.tetris.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the WallKickData class covering SRS wall kick offset tables
 * for both JLSTZ and I pieces.
 */
class WallKickDataTest {

    @Test
    void jlstzKicksHave5Tests() {
        // Test all 8 rotation transitions for T piece
        int[][] rotations = {{0,1},{1,0},{1,2},{2,1},{2,3},{3,2},{3,0},{0,3}};
        for (int[] rot : rotations) {
            int[][] kicks = WallKickData.getKicks(Piece.T, rot[0], rot[1]);
            assertNotNull(kicks, "Kicks should exist for " + rot[0] + "→" + rot[1]);
            assertEquals(5, kicks.length,
                    "Should have 5 kick tests for " + rot[0] + "→" + rot[1]);
        }
    }

    @Test
    void iKicksHave5Tests() {
        int[][] rotations = {{0,1},{1,0},{1,2},{2,1},{2,3},{3,2},{3,0},{0,3}};
        for (int[] rot : rotations) {
            int[][] kicks = WallKickData.getKicks(Piece.I, rot[0], rot[1]);
            assertNotNull(kicks);
            assertEquals(5, kicks.length);
        }
    }

    @Test
    void firstKickTestIsAlwaysZeroZero() {
        // The first test is always (0,0) - try the rotation in place
        for (Piece piece : new Piece[]{Piece.T, Piece.S, Piece.Z, Piece.J, Piece.L}) {
            int[][] kicks = WallKickData.getKicks(piece, 0, 1);
            assertEquals(0, kicks[0][0]);
            assertEquals(0, kicks[0][1]);
        }
        int[][] iKicks = WallKickData.getKicks(Piece.I, 0, 1);
        assertEquals(0, iKicks[0][0]);
        assertEquals(0, iKicks[0][1]);
    }

    @Test
    void oKickIsJustZeroZero() {
        int[][] kicks = WallKickData.getKicks(Piece.O, 0, 1);
        assertNotNull(kicks);
        assertEquals(1, kicks.length);
        assertEquals(0, kicks[0][0]);
        assertEquals(0, kicks[0][1]);
    }

    @Test
    void sameRotationReturnsZeroKick() {
        int[][] kicks = WallKickData.getKicks(Piece.T, 0, 0);
        assertNotNull(kicks);
        assertEquals(1, kicks.length);
        assertEquals(0, kicks[0][0]);
        assertEquals(0, kicks[0][1]);
    }

    @Test
    void kickDataIsSymmetric() {
        // 0→R and R→0 should be inverse operations in terms of offset signs
        int[][] forwardKicks = WallKickData.getKicks(Piece.T, 0, 1);
        int[][] reverseKicks = WallKickData.getKicks(Piece.T, 1, 0);
        assertNotNull(forwardKicks);
        assertNotNull(reverseKicks);
        // First test (0,0) should be same
        assertEquals(forwardKicks[0][0], reverseKicks[0][0]);
        assertEquals(forwardKicks[0][1], reverseKicks[0][1]);
        // Other tests should have opposite signs
        for (int i = 1; i < 5; i++) {
            assertEquals(-forwardKicks[i][0], reverseKicks[i][0],
                    "Kick " + i + " dx should be inverted");
            assertEquals(-forwardKicks[i][1], reverseKicks[i][1],
                    "Kick " + i + " dy should be inverted");
        }
    }
}
