package com.tetris.mab.nuke;

/**
 * How a nuke turns into rows of garbage on the target board. Schema
 * only; the actual garbage-generation engine is not implemented in
 * Step 3.
 */
public record GarbageProfile(
        int baseGarbageLines,
        int maxImmediateLines,
        boolean usesWaves,
        int waveCount,
        int piecesBetweenWaves,
        boolean targetedGarbage) {
}
