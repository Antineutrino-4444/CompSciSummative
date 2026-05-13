package com.tetris.mab;

/** Coarse phase of a Mutually Assured Blocks match. */
public enum MatchPhase {
    /** Match has been constructed but {@code startMatch()} has not been called yet. */
    SETUP,
    /** Both participants are playing. */
    ACTIVE,
    /** Match is paused so a participant can pick an upgrade (placeholder). */
    UPGRADE_PAUSE,
    /** Match has ended; {@code winner} (if any) is set. */
    GAME_OVER
}
