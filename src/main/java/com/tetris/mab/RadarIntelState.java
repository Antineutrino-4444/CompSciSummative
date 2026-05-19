package com.tetris.mab;

import com.tetris.mab.intel.IntelLevel;
import com.tetris.mab.intel.RadarScanResult;
import com.tetris.mab.intel.StaleIntelSnapshot;

/**
 * Per-participant route-readout state. Stores the most recent enemy
 * scan result wrapped in a {@link StaleIntelSnapshot}, plus running
 * totals about how many scans have been performed.
 *
 * <p>Step 10 replaces the placeholder. Scans are truthful at the time
 * they are produced - feints / false-doctrine signals are NOT
 * implemented yet.
 */
public class RadarIntelState {

    private StaleIntelSnapshot enemyIntel;
    private int totalScansPerformed;
    private int successfulScans;
    private int failedScans;
    private int bestIntelRankAchieved;
    private int lastScanSequenceNumber;
    private String lastScanSummary;

    public StaleIntelSnapshot getEnemyIntel() { return enemyIntel; }
    public RadarScanResult getLastScan() { return enemyIntel == null ? null : enemyIntel.getLastScan(); }
    public int getTotalScansPerformed() { return totalScansPerformed; }
    public int getSuccessfulScans() { return successfulScans; }
    public int getFailedScans() { return failedScans; }
    public int getBestIntelRankAchieved() { return bestIntelRankAchieved; }
    public int getLastScanSequenceNumber() { return lastScanSequenceNumber; }
    public String getLastScanSummary() { return lastScanSummary; }

    /** Stores a new scan. Failed scans count toward stats but do not wipe last good readout. */
    public void recordScan(RadarScanResult result) {
        if (result == null) return;
        totalScansPerformed++;
        lastScanSequenceNumber = result.scanSequenceNumber();
        if (result.success()) {
            successfulScans++;
            enemyIntel = new StaleIntelSnapshot(result);
            if (result.intelLevel() != null
                    && result.intelLevel().rank() > bestIntelRankAchieved) {
                bestIntelRankAchieved = result.intelLevel().rank();
            }
            lastScanSummary = "ok level=" + result.intelLevel()
                    + " conf=" + result.confidencePercent();
        } else {
            failedScans++;
            lastScanSummary = "failed: " + result.message();
            // Keep the previous enemyIntel so we don't wipe the last good readout.
        }
    }

    /** Tick when the scanner places a piece. Safe with no current readout. */
    public void tickScannerPiece() {
        if (enemyIntel != null) enemyIntel.tickScannerPiece();
    }

    /** Tick when the target places a piece. Safe with no current readout. */
    public void tickTargetPiece() {
        if (enemyIntel != null) enemyIntel.tickTargetPiece();
    }

    /** Hard mark stored readout as stale (e.g. enemy redesigned/launched). */
    public void markEnemyIntelStale(String reason) {
        if (enemyIntel != null) enemyIntel.markStale(reason);
    }

    public IntelLevel getLastIntelLevel() {
        RadarScanResult r = getLastScan();
        return r == null ? IntelLevel.NONE : r.intelLevel();
    }

    public int getLastIntelConfidence() {
        RadarScanResult r = getLastScan();
        return r == null ? 0 : r.confidencePercent();
    }

    public boolean isEnemyIntelStale() {
        return enemyIntel != null && enemyIntel.isStale();
    }

    public int getEnemyIntelStalenessScore() {
        return enemyIntel == null ? 0 : enemyIntel.getEstimatedStalenessScore();
    }

    public String toDebugString() {
        return "RouteReadout{scans=" + totalScansPerformed
                + " ok=" + successfulScans
                + " fail=" + failedScans
                + " best=" + bestIntelRankAchieved
                + " " + (enemyIntel == null ? "Readout{none}" : enemyIntel.toDebugString())
                + "}";
    }
}
