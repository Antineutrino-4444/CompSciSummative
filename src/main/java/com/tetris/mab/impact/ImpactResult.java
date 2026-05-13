package com.tetris.mab.impact;

import com.tetris.mab.ParticipantId;

/**
 * Immutable result returned by {@link ImpactResolver#resolveImpact}.
 */
public record ImpactResult(
        ImpactResolutionStatus status,
        String launchId,
        String threatId,
        ParticipantId attacker,
        ParticipantId defender,
        String nukeDesignId,
        String nukeDisplayName,
        String doctrineType,
        String sizeCategory,
        int blastRating,
        int radiationRating,
        int disarmRating,
        int siloDamageRating,
        int garbageLinesAppliedImmediately,
        int garbageLinesDelayed,
        RadiationLevel radiationLevel,
        int defenderChargeBefore,
        int defenderChargeAfter,
        int disarmAmountApplied,
        boolean defenderNukeFullyDisarmed,
        int siloIntegrityBefore,
        int siloIntegrityAfter,
        int siloDamageApplied,
        boolean civilDefenseApplied,
        int civilDefenseChargesConsumed,
        int civilDefenseImmediateRowsReduced,
        int requestedImmediateRowsBeforeGrace,
        int allowedImmediateRowsAfterGrace,
        int graceDeferredRows,
        int siloUpgradeDisarmReduced,
        int siloUpgradeDamageReduced,
        String message) {

    public static ImpactResult skipped(ImpactResolutionStatus status, String launchId,
                                       String threatId, String message) {
        return new ImpactResult(status, launchId, threatId, null, null, null, null, null, null,
                0, 0, 0, 0, 0, 0, RadiationLevel.CLEAN,
                0, 0, 0, false, 0, 0, 0,
                false, 0, 0, 0, 0, 0,
                0, 0,
                message);
    }
}
