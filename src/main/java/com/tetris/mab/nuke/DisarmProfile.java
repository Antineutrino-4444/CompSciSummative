package com.tetris.mab.nuke;

/**
 * How a nuke responds to defensive disarm attempts (full-clear power,
 * required clear ratio, etc.). Schema only.
 */
public record DisarmProfile(
        int disarmPower,
        double fullClearRequiredRatio,
        boolean canFullClear,
        boolean smallBombFullClearBlocked) {
}
