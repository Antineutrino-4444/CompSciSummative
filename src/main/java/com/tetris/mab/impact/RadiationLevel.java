package com.tetris.mab.impact;

/**
 * Coarse radiation tier of a nuke's garbage output. Drives the
 * messiness of the per-row hole pattern produced by
 * {@link RadiationGarbagePatternGenerator}. Radiation is intentionally
 * NOT modelled as a separate cleanup status — it lives in the garbage
 * pattern itself, so clearing the garbage naturally clears the
 * radiation footprint.
 */
public enum RadiationLevel {
    CLEAN,
    LIGHT,
    DIRTY,
    HOT,
    SEVERE,
    SALTED;

    /** Maps a NukeDesign {@code radiationRating} to a tier. */
    public static RadiationLevel fromRating(int radiationRating) {
        if (radiationRating <= 0) return CLEAN;
        return switch (radiationRating) {
            case 1 -> LIGHT;
            case 2 -> DIRTY;
            case 3 -> HOT;
            case 4 -> SEVERE;
            default -> SALTED;
        };
    }

    /** 0..5 messiness score used by the pattern generator. */
    public int messinessScore() {
        return switch (this) {
            case CLEAN  -> 0;
            case LIGHT  -> 1;
            case DIRTY  -> 2;
            case HOT    -> 3;
            case SEVERE -> 4;
            case SALTED -> 5;
        };
    }

    public String toDebugString() {
        return name() + "(m=" + messinessScore() + ")";
    }
}
