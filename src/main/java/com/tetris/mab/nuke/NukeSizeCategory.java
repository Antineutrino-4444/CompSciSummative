package com.tetris.mab.nuke;

/**
 * Coarse size band of a strategic nuke. Drives DEFCON readiness
 * scaling, debug summaries, and any future "how flashy is the launch"
 * UI cue.
 */
public enum NukeSizeCategory {
    MICRO,
    TACTICAL,
    THEATER,
    STRATEGIC,
    SUPERHEAVY,
    DOOMSDAY_SCALE;

    /**
     * Maps a base build-charge requirement to a default size band.
     * Used by {@link NukeBuilderAdapter} when a builder spec doesn't
     * carry an explicit size category.
     */
    public static NukeSizeCategory fromBuildCharge(int buildCharge) {
        if (buildCharge <= 10)  return MICRO;
        if (buildCharge <= 25)  return TACTICAL;
        if (buildCharge <= 40)  return THEATER;
        if (buildCharge <= 70)  return STRATEGIC;
        if (buildCharge <= 110) return SUPERHEAVY;
        return DOOMSDAY_SCALE;
    }
}
