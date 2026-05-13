package com.tetris.mab;

import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.nuke.NukeDesignFactory;

/**
 * Per-participant nuke build progress. Step 3 wires this to the real
 * {@link NukeDesign} schema; the previous string-only placeholder
 * fields are gone.
 */
public class NukeBuildState {

    /** Initial DEFCON used until the match coordinator refreshes us. */
    private static final int DEFAULT_DEFCON_LEVEL = 5;

    private int currentBuildCharge;
    private int effectiveBuildChargeRequired;
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
        this.effectiveBuildChargeRequired =
                this.currentDesign.effectiveBuildChargeRequired(initialDefconLevel);
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
        this.effectiveBuildChargeRequired =
                this.currentDesign.effectiveBuildChargeRequired(currentDefconLevel);
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
        this.effectiveBuildChargeRequired =
                this.currentDesign.effectiveBuildChargeRequired(currentDefconLevel);
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
    public boolean isArmed() { return armed; }
    public int getOverbuiltCharge() { return overbuiltCharge; }

    public String toDebugString() {
        return "NukeBuild{" + currentBuildCharge + "/" + effectiveBuildChargeRequired
                + " armed=" + armed
                + " design=" + currentDesign.getId()
                + " name=" + currentDesign.getDisplayName()
                + " doctrine=" + currentDesign.getDoctrineType()
                + " size=" + currentDesign.getSizeCategory()
                + " overbuilt=" + overbuiltCharge
                + "}";
    }
}
