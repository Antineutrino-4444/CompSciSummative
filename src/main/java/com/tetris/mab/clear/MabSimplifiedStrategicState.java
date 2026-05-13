package com.tetris.mab.clear;

/**
 * Step 21 \u2014 simplified strategic state per participant.
 *
 * <p>Replaces the action-code progress tracker for normal MAB PvE / local
 * 1v1. Tracks raw charge progress, the post-charge launch progress (how
 * many Tetrises and how many spins the player has banked toward firing),
 * and short labels for the HUD/last-clear strip.
 *
 * <p>Charge accumulation itself lives on
 * {@link com.tetris.mab.NukeBuildState}; this class mirrors the snapshot
 * for HUD display and owns the launch-progress counters.
 *
 * <p><b>Offline-only.</b>
 */
public final class MabSimplifiedStrategicState {

    public enum LastTrigger {
        NONE,
        CHARGE_GAIN,
        NUKE_READY,
        LAUNCH_PROGRESS_TETRIS,
        LAUNCH_PROGRESS_SPIN,
        LAUNCH_FIRED_TETRIS,
        LAUNCH_FIRED_SPIN,
        SPIN_INTERCEPT
    }

    public static final int LAUNCH_TETRIS_GOAL = 4;
    public static final int LAUNCH_SPIN_GOAL = 2;

    private int chargeCurrent;
    private int chargeRequired = 100;
    private boolean nukeReady;
    private int launchTetrisProgress;
    private int launchSpinProgress;
    /** Step 24 — per-instance goal so upgrades like Fast Fuse
     *  can lower the Tetris route target without mutating the
     *  shared static constant. */
    private int launchTetrisGoal = LAUNCH_TETRIS_GOAL;
    private int launchSpinGoal   = LAUNCH_SPIN_GOAL;
    /** Step 24 — if true, a successful spin-route launch retains
     *  one spin route pip (Spin Launch Crew). */
    private boolean spinRouteKeepsOnePip = false;

    private String lastClearText = "";
    private int lastChargeGained;
    private LastTrigger lastStrategicTrigger = LastTrigger.NONE;
    private int b2bChainLength;

    public int chargeCurrent()         { return chargeCurrent; }
    public int chargeRequired()        { return chargeRequired; }
    public boolean nukeReady()         { return nukeReady; }
    public int launchTetrisProgress()  { return launchTetrisProgress; }
    public int launchSpinProgress()    { return launchSpinProgress; }
    public int launchTetrisGoal()      { return launchTetrisGoal; }
    public int launchSpinGoal()        { return launchSpinGoal; }

    /** Step 24 — set Tetris route target (default {@value #LAUNCH_TETRIS_GOAL}). */
    public void setLaunchTetrisGoal(int goal) { this.launchTetrisGoal = Math.max(1, goal); }
    /** Step 24 — set spin route target (default {@value #LAUNCH_SPIN_GOAL}). */
    public void setLaunchSpinGoal(int goal)   { this.launchSpinGoal   = Math.max(1, goal); }
    /** Step 24 — set Spin Launch Crew effect. */
    public void setSpinRouteKeepsOnePip(boolean v) { this.spinRouteKeepsOnePip = v; }
    public String lastClearText()      { return lastClearText; }
    public int lastChargeGained()      { return lastChargeGained; }
    public LastTrigger lastStrategicTrigger() { return lastStrategicTrigger; }
    public int b2bChainLength()        { return b2bChainLength; }

    /** Mirror current charge values from the underlying NukeBuildState. */
    public void syncFromNuke(int current, int required) {
        this.chargeCurrent = Math.max(0, current);
        this.chargeRequired = Math.max(1, required);
        this.nukeReady = chargeCurrent >= chargeRequired;
    }

    public void recordClear(String text, int chargeGained, boolean backToBack) {
        this.lastClearText = text == null ? "" : text;
        this.lastChargeGained = chargeGained;
        this.lastStrategicTrigger = LastTrigger.CHARGE_GAIN;
        if (backToBack) b2bChainLength++;
        else b2bChainLength = 0;
    }

    public void markNukeReady() {
        this.nukeReady = true;
        this.lastStrategicTrigger = LastTrigger.NUKE_READY;
    }

    public void incrementTetrisProgress() {
        if (!nukeReady) return;
        launchTetrisProgress++;
        lastStrategicTrigger = LastTrigger.LAUNCH_PROGRESS_TETRIS;
    }

    public void incrementSpinProgress() {
        if (!nukeReady) return;
        launchSpinProgress++;
        lastStrategicTrigger = LastTrigger.LAUNCH_PROGRESS_SPIN;
    }

    public boolean shouldFireTetrisLaunch() {
        return nukeReady && launchTetrisProgress >= launchTetrisGoal;
    }

    public boolean shouldFireSpinLaunch() {
        return nukeReady && launchSpinProgress >= launchSpinGoal;
    }

    public void onLaunchFired(boolean tetrisRoute) {
        this.chargeCurrent = 0;
        this.nukeReady = false;
        this.launchTetrisProgress = 0;
        // Step 24 — Spin Launch Crew retains 1 spin pip after a spin-route launch.
        this.launchSpinProgress = (!tetrisRoute && spinRouteKeepsOnePip) ? 1 : 0;
        this.lastStrategicTrigger = tetrisRoute
                ? LastTrigger.LAUNCH_FIRED_TETRIS
                : LastTrigger.LAUNCH_FIRED_SPIN;
    }

    public void onSpinIntercept() {
        this.lastStrategicTrigger = LastTrigger.SPIN_INTERCEPT;
    }
}
