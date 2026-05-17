package com.tetris.mab;

import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.nuke.NukeDesignFactory;

/**
 * Per-participant nuke build progress. Wires the participant's currently
 * equipped {@link NukeDesign} to the live charge requirement and exposes
 * the design's other tempo properties (launch countdown, impact delay,
 * launch route requirements) for callers that need them.
 *
 * <p><b>Design-specific charge:</b>
 * {@link #getEffectiveBuildChargeRequired()} is always read from the
 * current {@link NukeDesign} and the current DEFCON level. It is never
 * a hardcoded universal value.
 */
public class NukeBuildState {

    /** Initial DEFCON used until the match coordinator refreshes us. */
    private static final int DEFAULT_DEFCON_LEVEL = 5;

    private int currentBuildCharge;
    private int effectiveBuildChargeRequired;
    private int currentDefconLevel = DEFAULT_DEFCON_LEVEL;
    private boolean armed;
    private NukeDesign currentDesign;
    private int overbuiltCharge;

    public NukeBuildState() {
        this(NukeDesignFactory.createDefaultPlaceholder(), DEFAULT_DEFCON_LEVEL);
    }

    public NukeBuildState(NukeDesign initialDesign, int initialDefconLevel) {
        this.currentDesign = (initialDesign != null)
                ? initialDesign
                : NukeDesignFactory.createDefaultPlaceholder();
        this.currentDefconLevel = clampDefcon(initialDefconLevel);
        this.effectiveBuildChargeRequired =
                this.currentDesign.effectiveBuildChargeRequired(this.currentDefconLevel);
        this.currentBuildCharge = 0;
        this.overbuiltCharge = 0;
        this.armed = false;
    }

    // ─────────────────────── Mutation ────────────────────────

    /** Replaces the current design and refreshes effective charge. */
    public void setDesign(NukeDesign design, int currentDefconLevel) {
        this.currentDesign = (design != null)
                ? design
                : NukeDesignFactory.createDefaultPlaceholder();
        this.currentDefconLevel = clampDefcon(currentDefconLevel);
        this.effectiveBuildChargeRequired =
                this.currentDesign.effectiveBuildChargeRequired(this.currentDefconLevel);
        recomputeArmedAndOverbuilt();
    }

    /**
     * Switch to a new design while keeping a fraction of the charge
     * already spent on the old one.
     */
    public void redesign(NukeDesign newDesign, int currentDefconLevel, double retainedChargeRatio) {
        double r = retainedChargeRatio;
        if (r < 0.0) r = 0.0;
        if (r > 1.0) r = 1.0;
        this.currentBuildCharge = (int) Math.floor(this.currentBuildCharge * r);
        setDesign(newDesign, currentDefconLevel);
    }

    /** Recomputes effective build charge for the (possibly changed) DEFCON. */
    public void refreshForDefcon(int currentDefconLevel) {
        this.currentDefconLevel = clampDefcon(currentDefconLevel);
        this.effectiveBuildChargeRequired =
                this.currentDesign.effectiveBuildChargeRequired(this.currentDefconLevel);
        recomputeArmedAndOverbuilt();
    }

    /** Adds nuke build charge. Negative or zero amounts are ignored. */
    public void addCharge(int amount) {
        if (amount <= 0) return;
        currentBuildCharge += amount;
        recomputeArmedAndOverbuilt();
    }

    /** Reduces nuke build charge by {@code amount}, clamped at 0. */
    public void reduceCharge(int amount) {
        if (amount <= 0) return;
        currentBuildCharge = Math.max(0, currentBuildCharge - amount);
        recomputeArmedAndOverbuilt();
    }

    /** Resets accumulated charge after a nuke is fired. */
    public void resetCharge() {
        currentBuildCharge = 0;
        overbuiltCharge = 0;
        armed = false;
    }

    private void recomputeArmedAndOverbuilt() {
        if (effectiveBuildChargeRequired <= 0) {
            armed = currentBuildCharge > 0;
            overbuiltCharge = Math.max(0, currentBuildCharge);
            return;
        }
        armed = currentBuildCharge >= effectiveBuildChargeRequired;
        overbuiltCharge = Math.max(0, currentBuildCharge - effectiveBuildChargeRequired);
    }

    // ─────────────────────── Accessors ───────────────────────

    public NukeDesign getCurrentDesign() { return currentDesign; }
    public int getCurrentBuildCharge() { return currentBuildCharge; }
    public int getEffectiveBuildChargeRequired() { return effectiveBuildChargeRequired; }
    public int getCurrentDefconLevel() { return currentDefconLevel; }
    public boolean isArmed() { return armed; }
    public int getOverbuiltCharge() { return overbuiltCharge; }

    /** Launch countdown (in pieces) for the current design + DEFCON. */
    public int getEffectiveLaunchCountdownPieces() {
        return currentDesign.effectiveLaunchTimePieces(currentDefconLevel);
    }

    /**
     * Pieces between launch and impact for the current design + DEFCON
     * (intercept-window length). Player-facing as "IMPACT IN N PIECES",
     * never as "warning time".
     */
    public int getEffectiveImpactDelayPieces() {
        return currentDesign.effectiveImpactDelayPieces(currentDefconLevel);
    }

    /** Tetris-route launch goal for the current design + DEFCON. */
    public int getEffectiveLaunchTetrisGoal() {
        return currentDesign.effectiveLaunchTetrisGoal(currentDefconLevel);
    }

    /** Spin-route launch goal for the current design + DEFCON. */
    public int getEffectiveLaunchSpinGoal() {
        return currentDesign.effectiveLaunchSpinGoal(currentDefconLevel);
    }

    private static int clampDefcon(int level) {
        if (level < 1) return 1;
        if (level > 5) return 5;
        return level;
    }

    public String toDebugString() {
        return "NukeBuild{" + currentBuildCharge + "/" + effectiveBuildChargeRequired
                + " armed=" + armed
                + " design=" + currentDesign.getId()
                + " name=" + currentDesign.getDisplayName()
                + " doctrine=" + currentDesign.getDoctrineType()
                + " size=" + currentDesign.getSizeCategory()
                + " defcon=" + currentDefconLevel
                + " launchT=" + getEffectiveLaunchCountdownPieces()
                + " impactT=" + getEffectiveImpactDelayPieces()
                + " tetrisGoal=" + getEffectiveLaunchTetrisGoal()
                + " spinGoal=" + getEffectiveLaunchSpinGoal()
                + " overbuilt=" + overbuiltCharge
                + "}";
    }
}
