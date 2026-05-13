package com.tetris.mab.intercept;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mutable per-launch / per-threat mitigation accumulator. Multiple
 * partial interceptions stack additively but no single damage category
 * can be reduced more than {@link #MAX_PARTIAL_REDUCTION} (0.95). Once
 * {@link #markFullyIntercepted} is called all multipliers collapse to
 * {@code 0.0}.
 */
public final class InterceptMitigationState {

    /** Hard cap on additive partial reduction per damage category. */
    public static final double MAX_PARTIAL_REDUCTION = 0.95;

    private double blastReductionRatio;
    private double radiationReductionRatio;
    private double disarmReductionRatio;
    private double siloDamageReductionRatio;
    private int totalInterceptPowerApplied;
    private boolean fullyIntercepted;
    private final List<String> interceptLog = new ArrayList<>();

    public double getBlastReductionRatio() { return blastReductionRatio; }
    public double getRadiationReductionRatio() { return radiationReductionRatio; }
    public double getDisarmReductionRatio() { return disarmReductionRatio; }
    public double getSiloDamageReductionRatio() { return siloDamageReductionRatio; }
    public int getTotalInterceptPowerApplied() { return totalInterceptPowerApplied; }
    public boolean isFullyIntercepted() { return fullyIntercepted; }
    public List<String> getInterceptLog() { return Collections.unmodifiableList(interceptLog); }

    public void addPartialMitigation(InterceptDefinition def) {
        if (def == null || fullyIntercepted) return;
        blastReductionRatio = clampAdd(blastReductionRatio, def.blastReductionRatio());
        radiationReductionRatio = clampAdd(radiationReductionRatio, def.radiationReductionRatio());
        disarmReductionRatio = clampAdd(disarmReductionRatio, def.disarmReductionRatio());
        siloDamageReductionRatio = clampAdd(siloDamageReductionRatio, def.siloDamageReductionRatio());
        totalInterceptPowerApplied += Math.max(0, def.interceptPower());
        interceptLog.add(def.interceptType().name() + ":partial:power=" + def.interceptPower());
    }

    public void markFullyIntercepted(InterceptDefinition def) {
        this.fullyIntercepted = true;
        if (def != null) {
            totalInterceptPowerApplied += Math.max(0, def.interceptPower());
            interceptLog.add(def.interceptType().name() + ":full:power=" + def.interceptPower());
        }
        // Full intercept neutralises all damage categories.
        blastReductionRatio = 1.0;
        radiationReductionRatio = 1.0;
        disarmReductionRatio = 1.0;
        siloDamageReductionRatio = 1.0;
    }

    public double getEffectiveBlastMultiplier() {
        return fullyIntercepted ? 0.0 : 1.0 - blastReductionRatio;
    }

    public double getEffectiveRadiationMultiplier() {
        return fullyIntercepted ? 0.0 : 1.0 - radiationReductionRatio;
    }

    public double getEffectiveDisarmMultiplier() {
        return fullyIntercepted ? 0.0 : 1.0 - disarmReductionRatio;
    }

    public double getEffectiveSiloDamageMultiplier() {
        return fullyIntercepted ? 0.0 : 1.0 - siloDamageReductionRatio;
    }

    private static double clampAdd(double current, double add) {
        if (add <= 0.0) return current;
        double v = current + add;
        if (v > MAX_PARTIAL_REDUCTION) v = MAX_PARTIAL_REDUCTION;
        if (v < 0.0) v = 0.0;
        return v;
    }

    public String toDebugString() {
        if (fullyIntercepted) {
            return "Mitig{FULL power=" + totalInterceptPowerApplied + "}";
        }
        if (totalInterceptPowerApplied == 0) return "Mitig{none}";
        return "Mitig{power=" + totalInterceptPowerApplied
                + " blast=" + fmt(blastReductionRatio)
                + " rad=" + fmt(radiationReductionRatio)
                + " dis=" + fmt(disarmReductionRatio)
                + " silo=" + fmt(siloDamageReductionRatio) + "}";
    }

    private static String fmt(double d) { return String.format("%.2f", d); }
}
