package com.tetris.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ScoreSystem.
 */
class ScoreSystemTest {

    private ScoreSystem score;

    @BeforeEach
    void setUp() {
        score = new ScoreSystem();
    }

    @Test
    void startsAtZero() {
        assertEquals(0, score.getScore());
        assertEquals(0, score.getTotalParticlesContained());
    }

    @Test
    void pionAwardsBaseScore() {
        int points = score.award(Hadron.PION, 0);
        assertEquals(ScoreSystem.PION_SCORE, points);
        assertEquals(ScoreSystem.PION_SCORE, score.getScore());
    }

    @Test
    void protonAwardsBaseScore() {
        int points = score.award(Hadron.PROTON, 0);
        assertEquals(ScoreSystem.PROTON_SCORE, points);
    }

    @Test
    void neutronAwardsBaseScore() {
        int points = score.award(Hadron.NEUTRON, 0);
        assertEquals(ScoreSystem.NEUTRON_SCORE, points);
    }

    @Test
    void comboMultiplierDoubles() {
        int points = score.award(Hadron.PION, 1);
        assertEquals(ScoreSystem.PION_SCORE * 2, points);
    }

    @Test
    void comboMultiplierQuadruples() {
        int points = score.award(Hadron.PION, 2);
        assertEquals(ScoreSystem.PION_SCORE * 4, points);
    }

    @Test
    void comboMultiplierOctuples() {
        int points = score.award(Hadron.PION, 3);
        assertEquals(ScoreSystem.PION_SCORE * 8, points);
    }

    @Test
    void comboMultiplierClampsAt8() {
        int points = score.award(Hadron.PION, 10);
        assertEquals(ScoreSystem.PION_SCORE * 8, points);
    }

    @Test
    void scoreAccumulates() {
        score.award(Hadron.PION, 0);     // 100
        score.award(Hadron.PROTON, 0);   // 400
        score.award(Hadron.NEUTRON, 0);  // 400
        assertEquals(900, score.getScore());
    }

    @Test
    void particlesContainedCounts() {
        score.award(Hadron.PION, 0);
        score.award(Hadron.PROTON, 0);
        score.award(Hadron.NEUTRON, 0);
        assertEquals(3, score.getTotalParticlesContained());
    }

    @Test
    void baseScoreConstants() {
        assertEquals(100, ScoreSystem.PION_SCORE);
        assertEquals(400, ScoreSystem.PROTON_SCORE);
        assertEquals(400, ScoreSystem.NEUTRON_SCORE);
    }

    @Test
    void comboMultiplierConstants() {
        assertArrayEquals(new int[]{1, 2, 4, 8}, ScoreSystem.COMBO_MULTIPLIERS);
    }

    @Test
    void getBaseScoreHelper() {
        assertEquals(100, ScoreSystem.getBaseScore(Hadron.PION));
        assertEquals(400, ScoreSystem.getBaseScore(Hadron.PROTON));
        assertEquals(400, ScoreSystem.getBaseScore(Hadron.NEUTRON));
    }

    @Test
    void getComboMultiplierHelper() {
        assertEquals(1, ScoreSystem.getComboMultiplier(0));
        assertEquals(2, ScoreSystem.getComboMultiplier(1));
        assertEquals(4, ScoreSystem.getComboMultiplier(2));
        assertEquals(8, ScoreSystem.getComboMultiplier(3));
        assertEquals(8, ScoreSystem.getComboMultiplier(99));
    }
}
