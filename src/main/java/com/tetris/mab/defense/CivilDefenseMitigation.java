package com.tetris.mab.defense;

/**
 * Immutable snapshot of one civil-defense application against a single
 * impact. Produced by {@link com.tetris.mab.CivilDefenseState#consumeForImpact()}
 * and passed into the impact resolver.
 *
 * <p>Reduction ratios are clamped to {@code [0.0, 0.9]} — civil defense
 * alone can never reduce all damage to zero.
 */
public record CivilDefenseMitigation(
        boolean active,
        int chargesConsumed,
        double blastReductionRatio,
        double radiationReductionRatio,
        double disarmReductionRatio,
        double siloDamageReductionRatio,
        int immediateGarbageReduction,
        int extraGraceRows,
        String source) {

    public static final double MAX_RATIO = 0.9;

    public CivilDefenseMitigation {
        blastReductionRatio       = clamp(blastReductionRatio);
        radiationReductionRatio   = clamp(radiationReductionRatio);
        disarmReductionRatio      = clamp(disarmReductionRatio);
        siloDamageReductionRatio  = clamp(siloDamageReductionRatio);
        if (chargesConsumed < 0) chargesConsumed = 0;
        if (immediateGarbageReduction < 0) immediateGarbageReduction = 0;
        if (extraGraceRows < 0) extraGraceRows = 0;
    }

    private static double clamp(double v) {
        if (Double.isNaN(v) || v <= 0.0) return 0.0;
        return Math.min(MAX_RATIO, v);
    }

    public static CivilDefenseMitigation none() {
        return new CivilDefenseMitigation(false, 0,
                0.0, 0.0, 0.0, 0.0, 0, 0, "none");
    }

    public static CivilDefenseMitigation basic(int chargesConsumed) {
        return new CivilDefenseMitigation(true, chargesConsumed,
                0.25, 0.30, 0.15, 0.15,
                1, 2, "basic");
    }

    public static CivilDefenseMitigation emergency(int chargesConsumed) {
        return new CivilDefenseMitigation(true, chargesConsumed,
                0.40, 0.45, 0.25, 0.25,
                2, 4, "emergency");
    }

    public double blastMultiplier()      { return 1.0 - blastReductionRatio; }
    public double radiationMultiplier()  { return 1.0 - radiationReductionRatio; }
    public double disarmMultiplier()     { return 1.0 - disarmReductionRatio; }
    public double siloDamageMultiplier() { return 1.0 - siloDamageReductionRatio; }

    public String toDebugString() {
        if (!active) return "CDMit{none}";
        return "CDMit{" + source
                + " charges=" + chargesConsumed
                + " blast=-" + blastReductionRatio
                + " rad=-" + radiationReductionRatio
                + " disarm=-" + disarmReductionRatio
                + " silo=-" + siloDamageReductionRatio
                + " immRed=" + immediateGarbageReduction
                + " extraGrace=" + extraGraceRows
                + "}";
    }
}
