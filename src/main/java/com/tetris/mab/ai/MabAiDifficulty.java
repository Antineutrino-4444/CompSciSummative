package com.tetris.mab.ai;

/**
 * Difficulty rung for the MAB AI opponent.
 *
 * <p>Re-built from first principles for the new offline AI. Higher tiers
 * unlock deeper search, wider beams, more lookahead, sharper risk control,
 * design-aware strategic foresight and lower mistake rates.
 *
 * <p>Legacy aliases {@link #NORMAL} (== MEDIUM) and {@link #DEBUG} (a
 * speed-tuned variant of MASTER for visible-board diagnostics) are kept
 * so existing UI dropdowns and config records compile unchanged.
 */
public enum MabAiDifficulty {

    /** Survives imperfectly, shallow strategy, frequent mistakes. */
    EASY,
    /** Stacks competently, knows basic charge/launch timing. */
    MEDIUM,
    /** Legacy alias for {@link #MEDIUM}. */
    NORMAL,
    /** Meaningful Nuke Builder & DEFCON strategy, low mistake rate. */
    HARD,
    /** Strong Tetris play, pressure timing, intercept decisions, upgrade synergy. */
    EXPERT,
    /** Deep search, design-aware route planning, deliberate launch timing. */
    MASTER,
    /** Speed-tuned MASTER for visible-board diagnostics — never used in PvE menus. */
    DEBUG;

    /** Returns a numeric tier 0..5 (EASY low, MASTER/DEBUG high). */
    public int tier() {
        return switch (this) {
            case EASY -> 0;
            case MEDIUM, NORMAL -> 1;
            case HARD -> 2;
            case EXPERT -> 3;
            case MASTER -> 4;
            case DEBUG -> 5;
        };
    }

    /** Player-visible label. */
    public String displayName() {
        return switch (this) {
            case EASY -> "Easy";
            case MEDIUM, NORMAL -> "Medium";
            case HARD -> "Hard";
            case EXPERT -> "Expert";
            case MASTER -> "Master";
            case DEBUG -> "Debug";
        };
    }
}
