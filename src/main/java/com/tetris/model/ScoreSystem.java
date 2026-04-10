package com.tetris.model;

/**
 * Scoring system for Particle Tetris.
 *
 * <p>Score is awarded for forming hadrons, with higher-complexity particles
 * worth more points. Cascade combos (multiple hadrons formed in a single
 * lock sequence) multiply the score.</p>
 */
public class ScoreSystem {

    /** Points awarded for forming a Pion (1u + 1d + 1g). */
    public static final int PION_SCORE = 100;

    /** Points awarded for forming a Proton (2u + 1d + 2g). */
    public static final int PROTON_SCORE = 400;

    /** Points awarded for forming a Neutron (1u + 2d + 2g). */
    public static final int NEUTRON_SCORE = 400;

    /** Combo multipliers indexed by combo count (0-based). Clamped at index 3 for 4+. */
    public static final int[] COMBO_MULTIPLIERS = {1, 2, 4, 8};

    private int score;
    private int totalParticlesContained;

    public ScoreSystem() {
        this.score = 0;
        this.totalParticlesContained = 0;
    }

    /**
     * Awards points for a hadron formation at the given combo level.
     *
     * @param hadron     the hadron that was formed
     * @param comboIndex the 0-based combo chain index (0 = first hadron in chain)
     * @return the points awarded for this specific formation
     */
    public int award(Hadron hadron, int comboIndex) {
        int base = getBaseScore(hadron);
        int multiplier = COMBO_MULTIPLIERS[Math.min(comboIndex, COMBO_MULTIPLIERS.length - 1)];
        int points = base * multiplier;
        score += points;
        totalParticlesContained++;
        return points;
    }

    /**
     * Returns the base score for a hadron type.
     */
    public static int getBaseScore(Hadron hadron) {
        return switch (hadron) {
            case PION -> PION_SCORE;
            case PROTON -> PROTON_SCORE;
            case NEUTRON -> NEUTRON_SCORE;
        };
    }

    /**
     * Returns the combo multiplier for the given 0-based combo index.
     */
    public static int getComboMultiplier(int comboIndex) {
        return COMBO_MULTIPLIERS[Math.min(comboIndex, COMBO_MULTIPLIERS.length - 1)];
    }

    public int getScore() { return score; }
    public int getTotalParticlesContained() { return totalParticlesContained; }
}
