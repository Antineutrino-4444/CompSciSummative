package com.tetris.mab.intel;

/**
 * Tracks how stale a stored {@link RadarScanResult} has become as both
 * the scanner and the target keep playing. The score grows with piece
 * locks (per-piece for the target, every 3 pieces for the scanner)
 * and with notable enemy events (redesign, launch, getting hit).
 *
 * <p>{@code stale} flips true once {@code estimatedStalenessScore >= 4},
 * but {@link #markStale(String)} can flip it earlier in response to a
 * known major enemy state change.
 */
public class StaleIntelSnapshot {

    /** Threshold at which the stored readout is considered stale. */
    public static final int STALE_SCORE_THRESHOLD = 4;

    private final RadarScanResult lastScan;
    private int scannerPiecesSinceScan;
    private int targetPiecesSinceScan;
    private boolean stale;
    private int estimatedStalenessScore;
    private String staleReason;

    public StaleIntelSnapshot(RadarScanResult lastScan) {
        this.lastScan = lastScan;
        if (lastScan != null && lastScan.staleImmediately()) {
            this.stale = true;
            this.estimatedStalenessScore = STALE_SCORE_THRESHOLD;
            this.staleReason = "scan failed or had no usable info";
        }
    }

    public RadarScanResult getLastScan() { return lastScan; }
    public int getScannerPiecesSinceScan() { return scannerPiecesSinceScan; }
    public int getTargetPiecesSinceScan() { return targetPiecesSinceScan; }
    public boolean isStale() { return stale; }
    public int getEstimatedStalenessScore() { return estimatedStalenessScore; }
    public String getStaleReason() { return staleReason; }

    /** Scanner placed a piece. Adds +1 every 3 scanner pieces. */
    public void tickScannerPiece() {
        if (lastScan == null) return;
        scannerPiecesSinceScan++;
        if (scannerPiecesSinceScan % 3 == 0) {
            addScore(1, "scanner pieces");
        }
    }

    /** Target placed a piece. Adds +1 each time. */
    public void tickTargetPiece() {
        if (lastScan == null) return;
        targetPiecesSinceScan++;
        addScore(1, "target pieces");
    }

    /** Hard mark stale (e.g. enemy redesigned or launched). */
    public void markStale(String reason) {
        if (lastScan == null) return;
        if (reason != null && reason.contains("redesign")) addScore(3, reason);
        else if (reason != null && reason.contains("launch")) addScore(3, reason);
        else if (reason != null && reason.contains("hit")) addScore(2, reason);
        else if (reason != null && reason.contains("disarm")) addScore(2, reason);
        else if (reason != null && reason.contains("silo")) addScore(2, reason);
        else addScore(STALE_SCORE_THRESHOLD, reason);
        this.stale = true;
        this.staleReason = (reason == null ? "marked stale" : reason);
    }

    private void addScore(int delta, String reason) {
        if (delta <= 0) return;
        estimatedStalenessScore += delta;
        if (!stale && estimatedStalenessScore >= STALE_SCORE_THRESHOLD) {
            stale = true;
            staleReason = reason;
        }
    }

    public String toDebugString() {
        if (lastScan == null) return "Readout{none}";
        return "Readout{level=" + lastScan.intelLevel()
                + " conf=" + lastScan.confidencePercent()
                + " stale=" + stale
                + " score=" + estimatedStalenessScore
                + " reason=" + (staleReason == null ? "-" : staleReason)
                + "}";
    }
}
