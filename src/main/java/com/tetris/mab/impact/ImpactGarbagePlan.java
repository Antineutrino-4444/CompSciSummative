package com.tetris.mab.impact;

import com.tetris.events.GarbageRowPattern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable plan describing how a single nuke impact turns into rows
 * of garbage on the defender's board.
 */
public final class ImpactGarbagePlan {

    private final int totalLines;
    private final int immediateLines;
    private final int delayedLines;
    private final int waveCount;
    private final int piecesBetweenWaves;
    private final RadiationLevel radiationLevel;
    private final List<GarbageRowPattern> immediatePatterns;
    private final List<List<GarbageRowPattern>> delayedWavePatterns;

    public ImpactGarbagePlan(int totalLines,
                             int immediateLines,
                             int delayedLines,
                             int waveCount,
                             int piecesBetweenWaves,
                             RadiationLevel radiationLevel,
                             List<GarbageRowPattern> immediatePatterns,
                             List<List<GarbageRowPattern>> delayedWavePatterns) {
        this.totalLines = Math.max(0, totalLines);
        this.immediateLines = Math.max(0, immediateLines);
        this.delayedLines = Math.max(0, delayedLines);
        this.waveCount = Math.max(0, waveCount);
        this.piecesBetweenWaves = Math.max(0, piecesBetweenWaves);
        this.radiationLevel = radiationLevel == null ? RadiationLevel.CLEAN : radiationLevel;
        this.immediatePatterns = immediatePatterns == null
                ? List.of()
                : List.copyOf(immediatePatterns);
        if (delayedWavePatterns == null) {
            this.delayedWavePatterns = List.of();
        } else {
            List<List<GarbageRowPattern>> waves = new ArrayList<>(delayedWavePatterns.size());
            for (List<GarbageRowPattern> w : delayedWavePatterns) {
                waves.add(w == null ? List.of() : List.copyOf(w));
            }
            this.delayedWavePatterns = Collections.unmodifiableList(waves);
        }
    }

    public int getTotalLines() { return totalLines; }
    public int getImmediateLines() { return immediateLines; }
    public int getDelayedLines() { return delayedLines; }
    public int getWaveCount() { return waveCount; }
    public int getPiecesBetweenWaves() { return piecesBetweenWaves; }
    public RadiationLevel getRadiationLevel() { return radiationLevel; }
    public List<GarbageRowPattern> getImmediatePatterns() { return immediatePatterns; }
    public List<List<GarbageRowPattern>> getDelayedWavePatterns() { return delayedWavePatterns; }
}
