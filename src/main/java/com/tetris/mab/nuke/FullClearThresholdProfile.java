package com.tetris.mab.nuke;

/**
 * Per-doctrine multipliers describing how easy it is for a defender
 * to disarm an inbound nuke via a Perfect Clear. Schema only.
 */
public record FullClearThresholdProfile(
        double normalRequiredRatio,
        double strategicRequiredRatio,
        double concreteBlasterRequiredRatio,
        double bunkerBusterRequiredRatio,
        double doomsdayRequiredRatio) {

    /** Reasonable defaults; smaller ratios = easier to PC-disarm. */
    public static FullClearThresholdProfile defaults() {
        return new FullClearThresholdProfile(1.5, 1.25, 0.9, 1.0, 1.0);
    }
}
