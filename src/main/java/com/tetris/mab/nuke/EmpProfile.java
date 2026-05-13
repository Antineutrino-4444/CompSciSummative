package com.tetris.mab.nuke;

/**
 * EMP profile: how long radar/warning intel is disrupted on the
 * target. Schema only.
 */
public record EmpProfile(
        int radarDisruptionPieces,
        int warningDisruptionPieces,
        boolean canFullClearWithUpgradeOnly) {
}
