package com.tetris.mab.intercept;

import com.tetris.mab.action.ActionType;

/**
 * Immutable definition of an intercept ability. The {@code *ReductionRatio}
 * fields are clamped to {@code [0.0, 1.0]} and represent the fraction of
 * the corresponding nuke damage stat that this intercept removes.
 */
public record InterceptDefinition(
        InterceptType interceptType,
        ActionType actionType,
        String displayName,
        int interceptPower,
        double blastReductionRatio,
        double radiationReductionRatio,
        double disarmReductionRatio,
        double siloDamageReductionRatio,
        boolean canFullyIntercept,
        String description) {

    public InterceptDefinition {
        if (interceptType == null) throw new IllegalArgumentException("interceptType");
        if (actionType == null) throw new IllegalArgumentException("actionType");
        if (displayName == null) throw new IllegalArgumentException("displayName");
        blastReductionRatio = clamp01(blastReductionRatio);
        radiationReductionRatio = clamp01(radiationReductionRatio);
        disarmReductionRatio = clamp01(disarmReductionRatio);
        siloDamageReductionRatio = clamp01(siloDamageReductionRatio);
        if (interceptPower < 0) interceptPower = 0;
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }
}
