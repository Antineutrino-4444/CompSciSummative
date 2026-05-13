package com.tetris.mab.intel;

import com.tetris.mab.ParticipantId;

/**
 * Immutable snapshot returned by a radar scan. Truthful at the moment
 * it was produced; can become stale (see {@link StaleIntelSnapshot}).
 *
 * <p>Decoys, false-doctrine signals, masked launches, and camouflage
 * deception are NOT modelled yet \u2014 silo camouflage only reduces
 * confidence. Strings are {@code null} and ints are {@code -1} when
 * the current intel level does not unlock that field.
 */
public record RadarScanResult(
        boolean success,
        ParticipantId scanner,
        ParticipantId target,
        RadarScanType scanType,
        IntelLevel intelLevel,
        int scanSequenceNumber,
        int scannerPiecesLockedAtScan,
        int targetPiecesLockedAtScan,
        int defconAtScan,
        String targetNukeDesignId,
        String targetNukeDisplayName,
        String targetDoctrineType,
        String targetSizeCategory,
        int targetBuildChargeEstimate,
        int targetBuildChargeRequiredEstimate,
        boolean targetArmedEstimate,
        String targetSiloDamageState,
        int targetSiloIntegrityEstimate,
        int activeLaunchCountEstimate,
        int incomingThreatCountEstimate,
        String firstActiveLaunchId,
        String firstActiveLaunchPhase,
        int firstActiveLaunchCountdownEstimate,
        String firstIncomingThreatId,
        String firstIncomingThreatStatus,
        int firstIncomingThreatWarningEstimate,
        int confidencePercent,
        boolean staleImmediately,
        String message) {

    public static RadarScanResult success(ParticipantId scanner, ParticipantId target,
                                          RadarScanType scanType, IntelLevel intelLevel,
                                          int scanSequenceNumber,
                                          int scannerPiecesLockedAtScan,
                                          int targetPiecesLockedAtScan,
                                          int defconAtScan,
                                          String targetNukeDesignId,
                                          String targetNukeDisplayName,
                                          String targetDoctrineType,
                                          String targetSizeCategory,
                                          int targetBuildChargeEstimate,
                                          int targetBuildChargeRequiredEstimate,
                                          boolean targetArmedEstimate,
                                          String targetSiloDamageState,
                                          int targetSiloIntegrityEstimate,
                                          int activeLaunchCountEstimate,
                                          int incomingThreatCountEstimate,
                                          String firstActiveLaunchId,
                                          String firstActiveLaunchPhase,
                                          int firstActiveLaunchCountdownEstimate,
                                          String firstIncomingThreatId,
                                          String firstIncomingThreatStatus,
                                          int firstIncomingThreatWarningEstimate,
                                          int confidencePercent,
                                          String message) {
        return new RadarScanResult(true, scanner, target, scanType, intelLevel,
                scanSequenceNumber, scannerPiecesLockedAtScan, targetPiecesLockedAtScan,
                defconAtScan,
                targetNukeDesignId, targetNukeDisplayName,
                targetDoctrineType, targetSizeCategory,
                targetBuildChargeEstimate, targetBuildChargeRequiredEstimate,
                targetArmedEstimate, targetSiloDamageState, targetSiloIntegrityEstimate,
                activeLaunchCountEstimate, incomingThreatCountEstimate,
                firstActiveLaunchId, firstActiveLaunchPhase,
                firstActiveLaunchCountdownEstimate,
                firstIncomingThreatId, firstIncomingThreatStatus,
                firstIncomingThreatWarningEstimate,
                clampConfidence(confidencePercent),
                false, message == null ? "ok" : message);
    }

    public static RadarScanResult failed(ParticipantId scanner, ParticipantId target,
                                         RadarScanType scanType, int scanSequenceNumber,
                                         String message) {
        return new RadarScanResult(false, scanner, target, scanType, IntelLevel.NONE,
                scanSequenceNumber, 0, 0, 0,
                null, null, null, null,
                -1, -1, false, null, -1,
                -1, -1,
                null, null, -1,
                null, null, -1,
                0, true, message == null ? "failed" : message);
    }

    private static int clampConfidence(int v) {
        if (v < 0) return 0;
        if (v > 100) return 100;
        return v;
    }
}
