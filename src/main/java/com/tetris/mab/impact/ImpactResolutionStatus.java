package com.tetris.mab.impact;

/**
 * Outcome status of an {@link ImpactResolver#resolveImpact} call.
 */
public enum ImpactResolutionStatus {
    RESOLVED,
    SKIPPED_NOT_READY,
    SKIPPED_ALREADY_RESOLVED,
    SKIPPED_CANCELLED,
    LAUNCH_NOT_FOUND,
    THREAT_NOT_FOUND,
    DEFENDER_NOT_FOUND,
    ERROR
}
