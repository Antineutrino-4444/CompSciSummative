package com.tetris.mab.intercept;

import com.tetris.mab.ActiveLaunchState;
import com.tetris.mab.IncomingThreatState;
import com.tetris.mab.ParticipantState;
import com.tetris.mab.launch.LaunchPhase;
import com.tetris.mab.launch.ThreatStatus;
import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.nuke.NukeDoctrineType;
import com.tetris.mab.nuke.NukeSizeCategory;
import com.tetris.mab.upgrade.draft.MabUpgradeEffectResolver;

/**
 * Stateless deterministic intercept resolver. No RNG — full vs partial
 * is decided purely from {@link InterceptDefinition#interceptPower()}
 * vs the threat resistance derived from the attacker's
 * {@link NukeDesign} / launch state.
 */
public final class InterceptResolver {

    public InterceptResult resolveIntercept(InterceptDefinition definition,
                                            ActiveLaunchState launch,
                                            IncomingThreatState threat,
                                            ParticipantState defender,
                                            ParticipantState attacker) {
        if (definition == null) {
            return InterceptResult.failed(InterceptOutcome.FAILED_ERROR, null,
                    null, null, defender == null ? null : defender.getId(),
                    "null definition");
        }
        if (threat == null) {
            return InterceptResult.failed(InterceptOutcome.FAILED_NO_THREAT, definition,
                    launch == null ? null : launch.getLaunchId(), null,
                    defender == null ? null : defender.getId(), "no threat");
        }
        if (launch == null) {
            return InterceptResult.failed(InterceptOutcome.FAILED_INVALID_TARGET, definition,
                    null, threat.getThreatId(),
                    defender == null ? null : defender.getId(), "no matching launch");
        }
        ThreatStatus status = threat.getStatus();
        if (status == ThreatStatus.INTERCEPTED
                || (threat.getInterceptMitigation() != null
                        && threat.getInterceptMitigation().isFullyIntercepted())) {
            return InterceptResult.failed(InterceptOutcome.FAILED_ALREADY_INTERCEPTED, definition,
                    launch.getLaunchId(), threat.getThreatId(),
                    defender.getId(), "already intercepted");
        }
        if (status == ThreatStatus.RESOLVED || status == ThreatStatus.CANCELLED) {
            return InterceptResult.failed(InterceptOutcome.FAILED_THREAT_NOT_ACTIVE, definition,
                    launch.getLaunchId(), threat.getThreatId(),
                    defender.getId(), "threat not active: " + status);
        }
        if (status == ThreatStatus.IMPACT_READY) {
            return InterceptResult.failed(InterceptOutcome.FAILED_TOO_LATE, definition,
                    launch.getLaunchId(), threat.getThreatId(),
                    defender.getId(), "threat already impact-ready");
        }
        if (status != ThreatStatus.WARNING_ACTIVE) {
            return InterceptResult.failed(InterceptOutcome.FAILED_THREAT_NOT_ACTIVE, definition,
                    launch.getLaunchId(), threat.getThreatId(),
                    defender.getId(), "threat not warning-active");
        }
        if (launch.getPhase() != LaunchPhase.IN_FLIGHT) {
            return InterceptResult.failed(InterceptOutcome.FAILED_THREAT_NOT_ACTIVE, definition,
                    launch.getLaunchId(), threat.getThreatId(),
                    defender.getId(), "launch not in flight: " + launch.getPhase());
        }

        InterceptDefinition effectiveDefinition = applyUpgradePower(definition, defender, attacker);
        int resistance = computeThreatResistance(launch, threat);
        boolean canFull = effectiveDefinition.canFullyIntercept()
                && effectiveDefinition.interceptPower() >= resistance;
        if (canFull) {
            launch.markFullyIntercepted(effectiveDefinition);
            threat.markFullyIntercepted(effectiveDefinition);
            return InterceptResult.full(effectiveDefinition, launch.getLaunchId(), threat.getThreatId(),
                    defender.getId(), launch.getAttacker(),
                    launch.getNukeDesignId(), launch.getNukeDisplayName(),
                    resistance, "full intercept");
        }
        // Partial: stack mitigation onto both launch and threat.
        launch.applyPartialIntercept(effectiveDefinition);
        threat.applyPartialIntercept(effectiveDefinition);
        return InterceptResult.partial(effectiveDefinition, launch.getLaunchId(), threat.getThreatId(),
                defender.getId(), launch.getAttacker(),
                launch.getNukeDesignId(), launch.getNukeDisplayName(),
                resistance, "partial intercept");
    }

    private InterceptDefinition applyUpgradePower(InterceptDefinition definition,
                                                  ParticipantState defender,
                                                  ParticipantState attacker) {
        int delta = 0;
        if (defender != null) {
            delta += MabUpgradeEffectResolver.interceptStrengthDelta(defender.getUpgradeInventory());
        }
        if (attacker != null) {
            delta += MabUpgradeEffectResolver.penetratorDelta(attacker.getUpgradeInventory());
        }
        if (delta == 0) return definition;
        return new InterceptDefinition(
                definition.interceptType(),
                definition.actionType(),
                definition.displayName(),
                Math.max(0, definition.interceptPower() + delta),
                definition.blastReductionRatio(),
                definition.radiationReductionRatio(),
                definition.disarmReductionRatio(),
                definition.siloDamageReductionRatio(),
                definition.canFullyIntercept(),
                definition.description());
    }

    /**
     * Deterministic threat resistance:
     * <ul>
     *   <li>Base from size category (MICRO=1 .. DOOMSDAY_SCALE=8)</li>
     *   <li>+2 MIRV doctrine, +1 DECOY_PACKAGE doctrine, +2 DOOMSDAY doctrine</li>
     *   <li>+1 if {@code detectionProfile >= 4}</li>
     *   <li>+1 if {@code warningPiecesRemaining <= 1} (very late intercept)</li>
     * </ul>
     */
    public int computeThreatResistance(ActiveLaunchState launch, IncomingThreatState threat) {
        int base = baseResistanceFor(launch.getSizeCategory());
        NukeDoctrineType doctrine = launch.getDoctrineType();
        if (doctrine != null) {
            switch (doctrine) {
                case MIRV          -> base += 2;
                case DECOY_PACKAGE -> base += 1;
                case DOOMSDAY      -> base += 2;
                default            -> { /* no modifier */ }
            }
        }
        NukeDesign design = launch.getNukeDesign();
        if (design != null && design.getDetectionProfile() >= 4) base += 1;
        if (threat != null && threat.getWarningPiecesRemaining() <= 1) base += 1;
        return Math.max(1, base);
    }

    private static int baseResistanceFor(NukeSizeCategory size) {
        if (size == null) return 2;
        return switch (size) {
            case MICRO          -> 1;
            case TACTICAL       -> 2;
            case THEATER        -> 3;
            case STRATEGIC      -> 4;
            case SUPERHEAVY     -> 6;
            case DOOMSDAY_SCALE -> 8;
        };
    }

}
