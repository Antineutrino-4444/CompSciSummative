package com.tetris.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ScoreSystem class covering line clear scoring, T-spins,
 * combos, back-to-back, perfect clears, soft/hard drop, and level progression.
 */
class ScoreSystemTest {

    private ScoreSystem scoring;

    @BeforeEach
    void setUp() {
        scoring = new ScoreSystem(1);
    }

    @Test
    void initialState() {
        assertEquals(0, scoring.getScore());
        assertEquals(1, scoring.getLevel());
        assertEquals(0, scoring.getTotalLinesCleared());
        assertEquals(-1, scoring.getCombo());
        assertFalse(scoring.isBackToBack());
    }

    @Test
    void singleLineClear() {
        scoring.onLineClear(1, false, false, false);
        assertEquals(100, scoring.getScore()); // 100 × level 1
        assertEquals(1, scoring.getTotalLinesCleared());
    }

    @Test
    void doubleLineClear() {
        scoring.onLineClear(2, false, false, false);
        assertEquals(300, scoring.getScore()); // 300 × level 1
    }

    @Test
    void tripleLineClear() {
        scoring.onLineClear(3, false, false, false);
        assertEquals(500, scoring.getScore()); // 500 × level 1
    }

    @Test
    void tetrisLineClear() {
        scoring.onLineClear(4, false, false, false);
        assertEquals(800, scoring.getScore()); // 800 × level 1
    }

    @Test
    void scoringScalesWithLevel() {
        ScoreSystem level5 = new ScoreSystem(5);
        level5.onLineClear(1, false, false, false);
        assertEquals(500, level5.getScore()); // 100 × 5
    }

    @Test
    void tSpinNoLines() {
        scoring.onLineClear(0, true, false, false);
        assertEquals(400, scoring.getScore());
    }

    @Test
    void tSpinSingle() {
        scoring.onLineClear(1, true, false, false);
        assertEquals(800, scoring.getScore());
    }

    @Test
    void tSpinDouble() {
        scoring.onLineClear(2, true, false, false);
        assertEquals(1200, scoring.getScore());
    }

    @Test
    void tSpinTriple() {
        scoring.onLineClear(3, true, false, false);
        assertEquals(1600, scoring.getScore());
    }

    @Test
    void miniTSpinNoLines() {
        scoring.onLineClear(0, false, true, false);
        assertEquals(100, scoring.getScore());
    }

    @Test
    void miniTSpinSingle() {
        scoring.onLineClear(1, false, true, false);
        assertEquals(200, scoring.getScore());
    }

    @Test
    void backToBackTetris() {
        scoring.onLineClear(4, false, false, false); // 800, combo 0
        scoring.onLineClear(4, false, false, false); // 800×1.5=1200 + combo 1×50 = 1250
        assertEquals(2050, scoring.getScore());      // 800 + 1250 = 2050
    }

    @Test
    void backToBackBrokenByNonDifficultClear() {
        scoring.onLineClear(4, false, false, false); // 800, combo 0, B2B starts
        scoring.onLineClear(1, false, false, false); // 100 + combo 1×50 = 150, breaks B2B
        scoring.onLineClear(4, false, false, false); // 800 + combo 2×50 = 900, no B2B
        assertEquals(1850, scoring.getScore());      // 800 + 150 + 900
    }

    @Test
    void backToBackTSpin() {
        scoring.onLineClear(4, false, false, false); // 800, combo 0, B2B starts
        scoring.onLineClear(2, true, false, false);  // 1200×1.5=1800 + combo 1×50 = 1850
        assertEquals(2650, scoring.getScore());      // 800 + 1850
    }

    @Test
    void comboScoring() {
        // 3 consecutive line clears
        scoring.onLineClear(1, false, false, false); // 100, combo 0
        scoring.onLineClear(1, false, false, false); // 100 + 50×1 = 150, combo 1
        scoring.onLineClear(1, false, false, false); // 100 + 50×2 = 200, combo 2
        assertEquals(450, scoring.getScore());
    }

    @Test
    void comboResetsOnNoLineClear() {
        scoring.onLineClear(1, false, false, false); // combo 0
        scoring.onLineClear(1, false, false, false); // combo 1
        scoring.onPieceLockNoLines();                 // combo reset
        scoring.onLineClear(1, false, false, false); // combo 0 again
        assertEquals(350, scoring.getScore()); // 100 + 150 + 100
    }

    @Test
    void perfectClearBonus() {
        scoring.onLineClear(4, false, false, true); // 800 + 2000 = 2800
        assertEquals(2800, scoring.getScore());
    }

    @Test
    void perfectClearSingle() {
        scoring.onLineClear(1, false, false, true); // 100 + 800 = 900
        assertEquals(900, scoring.getScore());
    }

    @Test
    void softDropPoints() {
        scoring.onSoftDrop(5);
        assertEquals(5, scoring.getScore());
    }

    @Test
    void hardDropPoints() {
        scoring.onHardDrop(10);
        assertEquals(20, scoring.getScore());
    }

    @Test
    void levelAdvancesEvery10Lines() {
        for (int i = 0; i < 10; i++) {
            scoring.onLineClear(1, false, false, false);
        }
        assertEquals(2, scoring.getLevel());
        assertEquals(10, scoring.getTotalLinesCleared());
    }

    @Test
    void levelAdvancesWith4LineClear() {
        scoring.onLineClear(4, false, false, false); // 4 lines
        assertEquals(1, scoring.getLevel()); // Still level 1 (need 10)
        scoring.onLineClear(4, false, false, false); // 8 lines
        assertEquals(1, scoring.getLevel()); // Still level 1
        scoring.onLineClear(4, false, false, false); // 12 lines
        assertEquals(2, scoring.getLevel()); // Level 2
    }

    @Test
    void gravityStartsSlow() {
        double gravity = scoring.getGravityInterval();
        assertTrue(gravity > 0.5, "Level 1 gravity should be > 0.5 seconds");
        assertTrue(gravity <= 1.0, "Level 1 gravity should be <= 1.0 seconds");
    }

    @Test
    void gravityGetsFasterWithLevel() {
        ScoreSystem low = new ScoreSystem(1);
        ScoreSystem high = new ScoreSystem(10);
        assertTrue(high.getGravityInterval() < low.getGravityInterval(),
                "Higher level should have faster gravity");
    }

    @Test
    void lastActionText() {
        scoring.onLineClear(4, false, false, false);
        assertEquals("Tetris", scoring.getLastAction());
    }

    @Test
    void lastActionTextTSpin() {
        scoring.onLineClear(2, true, false, false);
        assertEquals("T-Spin Double", scoring.getLastAction());
    }

    @Test
    void constructorDefaultLevel() {
        ScoreSystem defaultScoring = new ScoreSystem();
        assertEquals(1, defaultScoring.getLevel());
    }
}
