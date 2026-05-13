package com.tetris.mab.defense;

/**
 * Immutable record describing how many of the requested immediate
 * garbage rows the {@link ImpactGracePolicy} will allow this tick and
 * how many will be deferred into delayed waves.
 */
public record ImpactGraceDecision(
        int requestedImmediateRows,
        int allowedImmediateRows,
        int deferredRows,
        int safeRows,
        int stackHeight,
        int boardHeight,
        int emergencyHeadroom,
        String reason) {

    public boolean deferredAny() { return deferredRows > 0; }

    public String toDebugString() {
        return "Grace{requested=" + requestedImmediateRows
                + " allowed=" + allowedImmediateRows
                + " deferred=" + deferredRows
                + " safeRows=" + safeRows
                + " stack=" + stackHeight
                + " board=" + boardHeight
                + " headroom=" + emergencyHeadroom
                + " reason=" + reason
                + "}";
    }
}
