package com.tetris.mab.upgrade;

/** Outcome of attempting to apply a single upgrade. */
public record UpgradeApplicationResult(
        boolean success,
        UpgradeType type,
        int newLevel,
        int costPaid,
        String message) {

    public static UpgradeApplicationResult success(UpgradeType type, int newLevel, int costPaid,
                                                   String message) {
        return new UpgradeApplicationResult(true, type, newLevel, costPaid,
                message == null ? "ok" : message);
    }

    public static UpgradeApplicationResult failed(UpgradeType type, String message) {
        return new UpgradeApplicationResult(false, type, 0, 0,
                message == null ? "failed" : message);
    }
}
