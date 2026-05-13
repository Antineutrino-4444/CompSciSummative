package com.tetris.mab.impact;

import com.tetris.mab.ParticipantId;

/**
 * Mutable state for a single delayed garbage wave scheduled by a nuke
 * impact. Stored on the match coordinator so the wave can be applied
 * when its piece-count timer completes.
 */
public final class ImpactWaveState {

    private final String waveId;
    private final String launchId;
    private final ParticipantId defender;
    private final int waveIndex;
    private final int totalWaves;
    private final int rows;
    private final RadiationLevel radiationLevel;
    private final String timerId;
    private boolean applied;

    public ImpactWaveState(String waveId, String launchId, ParticipantId defender,
                           int waveIndex, int totalWaves, int rows,
                           RadiationLevel radiationLevel, String timerId) {
        this.waveId = waveId;
        this.launchId = launchId;
        this.defender = defender;
        this.waveIndex = waveIndex;
        this.totalWaves = totalWaves;
        this.rows = Math.max(0, rows);
        this.radiationLevel = radiationLevel == null ? RadiationLevel.CLEAN : radiationLevel;
        this.timerId = timerId;
    }

    public String getWaveId() { return waveId; }
    public String getLaunchId() { return launchId; }
    public ParticipantId getDefender() { return defender; }
    public int getWaveIndex() { return waveIndex; }
    public int getTotalWaves() { return totalWaves; }
    public int getRows() { return rows; }
    public RadiationLevel getRadiationLevel() { return radiationLevel; }
    public String getTimerId() { return timerId; }
    public boolean isApplied() { return applied; }

    public void markApplied() { this.applied = true; }

    public String toDebugString() {
        return "Wave{" + waveId
                + " launch=" + launchId
                + " defender=" + defender
                + " " + (waveIndex + 1) + "/" + totalWaves
                + " rows=" + rows
                + " rad=" + radiationLevel
                + (applied ? " APPLIED" : "")
                + "}";
    }
}
