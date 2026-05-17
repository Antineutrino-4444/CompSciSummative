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
     *   <li>Base from size category (MICRO=1 .. DOOMSDAY_SCALE=8).</li>
     *   <li>Doctrine modifier via {@link NukeDesign#interceptDifficultyRating()}
     *       — light tactical = 1, heavy / clean / dirty = 2, bunker /
     *       concrete = 3, doomsday = 4. Older legacy doctrines
     *       (MIRV / DECOY_PACKAGE) map through the same rating so no
     *       MIRV-specific code path remains.</li>
     *   <li>+1 if {@code impactDelay remaining <= 1} (very late intercept).</li>
     *   <li>Stable designs (stability >= 7) are slightly easier to
     *       intercept (-1); high-complexity designs are slightly
     *       harder (+1 if complexity >= 7).</li>
     * </ul>
     *
     * <p>Detection profile is no longer consulted — radar / detection
     * concepts have been removed from the MAB design.
     */
    public int computeThreatResistance(ActiveLaunchState launch, IncomingThreatState threat) {
        int base = baseResistanceFor(launch.getSizeCategory());
        NukeDesign design = launch.getNukeDesign();
        if (design != null) {
            base += design.interceptDifficultyRating();
            if (design.getStabilityRating() >= 7) base -= 1;
            if (design.getComplexityRating() >= 7) base += 1;
        }
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
