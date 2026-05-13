package com.tetris.mab.nuke;

/**
 * Multiple-Independent-Reentry-Vehicle profile: split a payload across
 * timed waves of garbage. Schema only.
 */
public record MirvProfile(
        int waveCount,
        int linesPerWave,
        int piecesBetweenWaves) {
}
