package com.tetris.mab;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Global DEFCON readiness state for a Mutually Assured Blocks match.
 *
 * <p>DEFCON is shared by both participants. It represents readiness
 * level (5 = peacetime, 1 = nuclear war imminent), not damage. Crossing
 * a {@link #thresholds} value moves the level downward (5 → 1).
 *
 * <p>Step 2 only tracks the readiness number and an escalation meter —
 * DEFCON does not yet modify gameplay (no damage modifiers, no upgrade
 * gating).
 */
public class DefconState {

    private static final double[] GRAVITY_MULTIPLIERS_BY_LEVEL =
            { 0.0, 1.65, 1.38, 1.18, 1.05, 1.00 };

    private int level;
    private int escalationMeter;
    private final TreeMap<Integer, Integer> thresholds; // escalation -> defcon level

    public DefconState() {
        this.escalationMeter = 0;
        this.level = 5;
        this.thresholds = new TreeMap<>();
        this.thresholds.put(0,   5);
        this.thresholds.put(100, 4);
        this.thresholds.put(250, 3);
        this.thresholds.put(500, 2);
        this.thresholds.put(850, 1);
        recomputeLevel();
    }

    /**
     * Adds escalation. {@code reason} is informational only and is meant
     * to be forwarded into the match event log by the caller.
     *
     * <p>Returns a {@link DefconChangeResult} describing whether the
     * level changed, so the caller can refresh dependent state (e.g.
     * effective nuke build charge) on a transition.
     */
    public DefconChangeResult addEscalation(int amount, String reason) {
        int prev = level;
        if (amount > 0) {
            escalationMeter += amount;
            recomputeLevel();
        }
        return new DefconChangeResult(prev, level, prev != level, escalationMeter, reason);
    }

    private void recomputeLevel() {
        Map.Entry<Integer, Integer> e = thresholds.floorEntry(escalationMeter);
        if (e != null) level = e.getValue();
    }

    public int getLevel() { return level; }
    public int getEscalationMeter() { return escalationMeter; }
    public double getGravityMultiplier() { return gravityMultiplierForLevel(level); }

    public static double gravityMultiplierForLevel(int level) {
        if (level < 1) level = 1;
        if (level > 5) level = 5;
        return GRAVITY_MULTIPLIERS_BY_LEVEL[level];
    }

    /**
     * Returns the escalation value at which the level will next drop, or
     * -1 if already at the lowest defined level (DEFCON 1).
     */
    public int getNextThreshold() {
        Map.Entry<Integer, Integer> e = thresholds.higherEntry(escalationMeter);
        return e == null ? -1 : e.getKey();
    }

    /**
     * Returns 0.0–1.0 progress within the current DEFCON band toward the
     * next escalation threshold. Returns 1.0 when already at DEFCON 1.
     */
    public double getProgressToNextThreshold() {
        int next = getNextThreshold();
        if (next < 0) return 1.0;
        Map.Entry<Integer, Integer> cur = thresholds.floorEntry(escalationMeter);
        int start = (cur != null) ? cur.getKey() : 0;
        if (next <= start) return 0.0;
        return Math.min(1.0, (double)(escalationMeter - start) / (next - start));
    }

    public boolean isAtDefcon(int level) { return this.level == level; }

    /** Returns an unmodifiable view of the escalation→defcon mapping. */
    public Map<Integer, Integer> getThresholds() {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(thresholds));
    }

    public String toDebugString() {
        int next = getNextThreshold();
        return "DEFCON " + level + " (escalation=" + escalationMeter
                + (next >= 0 ? ", next at " + next : ", floor")
                + ")";
    }
}
