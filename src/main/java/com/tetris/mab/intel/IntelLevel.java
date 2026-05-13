package com.tetris.mab.intel;

/**
 * Tier of information a radar scan can deliver. Higher ranks include
 * everything from lower ranks. Used by {@link RadarScanCalculator} to
 * decide which fields of {@link RadarScanResult} are populated.
 */
public enum IntelLevel {
    NONE(0),
    CONTACT(1),
    SIZE_ESTIMATE(2),
    DOCTRINE_ESTIMATE(3),
    PROGRESS_ESTIMATE(4),
    LAUNCH_WARNING(5),
    FULL_READOUT(6);

    private final int rank;

    IntelLevel(int rank) { this.rank = rank; }

    public int rank() { return rank; }

    public boolean atLeast(IntelLevel other) {
        return other != null && this.rank >= other.rank;
    }
}
