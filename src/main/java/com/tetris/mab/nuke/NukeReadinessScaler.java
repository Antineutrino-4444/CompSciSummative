package com.tetris.mab.nuke;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper that produces DEFCON-keyed maps of build-charge / launch-code
 * / launch-time / warning-time, scaled by {@link NukeSizeCategory}.
 *
 * <p>This is a generic fallback used by {@link NukeBuilderAdapter}.
 * The hand-tuned default designs in {@link NukeDesignFactory} use
 * explicit literal maps and do not go through this helper.
 *
 * <p>Crucially, this helper only changes <em>deployment difficulty</em>
 * (build cost, code complexity, timings). It never touches damage
 * ratings.
 */
public final class NukeReadinessScaler {

    private NukeReadinessScaler() {}

    /** Per-DEFCON multipliers for the build-charge requirement. */
    public static Map<Integer, Integer> buildCharge(int base, NukeSizeCategory size) {
        double[] m = buildChargeMultipliers(size);
        Map<Integer, Integer> out = new LinkedHashMap<>();
        // index: 0=DEFCON5 ... 4=DEFCON1
        for (int i = 0; i < 5; i++) {
            int defcon = 5 - i;
            int v = (int) Math.max(1, Math.round(base * m[i]));
            out.put(defcon, v);
        }
        return out;
    }

    /** Per-DEFCON launch-time scaling, floor of 3 pieces. */
    public static Map<Integer, Integer> launchTime(int baseTime, NukeSizeCategory size) {
        double[] m = timingMultipliers(size);
        Map<Integer, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            int defcon = 5 - i;
            int v = Math.max(3, (int) Math.round(baseTime * m[i]));
            out.put(defcon, v);
        }
        return out;
    }

    /** Per-DEFCON warning-time scaling, floor of 3 pieces. */
    public static Map<Integer, Integer> warningTime(int baseTime, NukeSizeCategory size) {
        return launchTime(baseTime, size);
    }

    /**
     * Per-DEFCON launch-code scaling. Higher DEFCON → longer code
     * (peacetime requires elaborate authorization); lower DEFCON →
     * shorter code (war footing). Codes are derived from {@code baseCode}
     * by truncation/extension; values never leave 1..4.
     */
    public static Map<Integer, List<Integer>> launchCode(List<Integer> baseCode, NukeSizeCategory size) {
        Map<Integer, List<Integer>> out = new LinkedHashMap<>();
        if (baseCode == null || baseCode.isEmpty()) {
            for (int defcon = 5; defcon >= 1; defcon--) out.put(defcon, List.of());
            return out;
        }
        int[] lengthDelta = lengthDeltas(size); // index 0..4 = DEFCON 5..1
        int baseLen = baseCode.size();
        for (int i = 0; i < 5; i++) {
            int defcon = 5 - i;
            int targetLen = Math.max(1, baseLen + lengthDelta[i]);
            java.util.ArrayList<Integer> code = new java.util.ArrayList<>(targetLen);
            for (int k = 0; k < targetLen; k++) {
                int v = baseCode.get(k % baseLen);
                if (v < 1) v = 1;
                if (v > 4) v = 4;
                code.add(v);
            }
            out.put(defcon, List.copyOf(code));
        }
        return out;
    }

    private static double[] buildChargeMultipliers(NukeSizeCategory size) {
        // index 0=DEFCON5 (peacetime, costliest) ... 4=DEFCON1 (war, cheapest)
        return switch (size) {
            case MICRO          -> new double[]{1.10, 1.05, 1.00, 0.95, 0.90};
            case TACTICAL       -> new double[]{1.10, 1.05, 1.00, 0.90, 0.80};
            case THEATER        -> new double[]{1.30, 1.15, 1.00, 0.88, 0.78};
            case STRATEGIC      -> new double[]{1.40, 1.20, 1.00, 0.86, 0.74};
            case SUPERHEAVY     -> new double[]{1.55, 1.28, 1.00, 0.82, 0.68};
            case DOOMSDAY_SCALE -> new double[]{1.70, 1.35, 1.00, 0.80, 0.60};
        };
    }

    private static double[] timingMultipliers(NukeSizeCategory size) {
        return switch (size) {
            case MICRO          -> new double[]{1.10, 1.05, 1.00, 0.95, 0.90};
            case TACTICAL       -> new double[]{1.20, 1.10, 1.00, 0.90, 0.80};
            case THEATER        -> new double[]{1.25, 1.10, 1.00, 0.90, 0.80};
            case STRATEGIC      -> new double[]{1.40, 1.20, 1.00, 0.85, 0.75};
            case SUPERHEAVY     -> new double[]{1.55, 1.25, 1.00, 0.80, 0.70};
            case DOOMSDAY_SCALE -> new double[]{1.70, 1.30, 1.00, 0.80, 0.65};
        };
    }

    private static int[] lengthDeltas(NukeSizeCategory size) {
        // index 0..4 = DEFCON 5..1
        return switch (size) {
            case MICRO          -> new int[]{ 0,  0,  0,  0,  0};
            case TACTICAL       -> new int[]{ 0,  0,  0, -1, -2};
            case THEATER        -> new int[]{+1,  0,  0, -1, -2};
            case STRATEGIC      -> new int[]{+1,  0,  0, -1, -2};
            case SUPERHEAVY     -> new int[]{+2, +1,  0, -1, -2};
            case DOOMSDAY_SCALE -> new int[]{+1,  0,  0, -1, -2};
        };
    }
}
