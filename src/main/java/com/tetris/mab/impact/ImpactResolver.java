package com.tetris.mab.impact;

import com.tetris.events.GarbageRowPattern;
import com.tetris.mab.ActiveLaunchState;
import com.tetris.mab.IncomingThreatState;
import com.tetris.mab.ParticipantState;
import com.tetris.mab.PieceCountdownTimer;
import com.tetris.mab.PieceTimerManager;
import com.tetris.mab.SiloState;
import com.tetris.mab.TimerAdvanceMode;
import com.tetris.mab.defense.CivilDefenseMitigation;
import com.tetris.mab.defense.ImpactGraceDecision;
import com.tetris.mab.defense.ImpactGracePolicy;
import com.tetris.mab.intercept.InterceptMitigationState;
import com.tetris.mab.launch.LaunchPhase;
import com.tetris.mab.launch.ThreatStatus;
import com.tetris.mab.nuke.GarbageProfile;
import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.upgrade.draft.MabUpgradeEffectResolver;
import com.tetris.model.Board;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * First-pass nuke impact resolver. Translates an IMPACT_READY launch
 * + threat pair into:
 *
 * <ul>
 *   <li>immediate radiation-patterned garbage applied via the existing
 *       {@code GameState.insertGarbagePattern(...)} API,</li>
 *   <li>delayed garbage waves scheduled on a {@link PieceTimerManager}
 *       (defender-owned ticks),</li>
 *   <li>disarm damage against the defender's nuke build charge,</li>
 *   <li>silo damage against the defender's {@link SiloState},</li>
 *   <li>RESOLVED state on both the launch and the threat.</li>
 * </ul>
 *
 * <p>DEFCON does NOT modify any damage value here — bigger bombs stay
 * big regardless of readiness. All damage numbers come from the
 * attacker's {@link NukeDesign}.
 */
public final class ImpactResolver {

    private final RadiationGarbagePatternGenerator patternGenerator;

    public ImpactResolver() {
        this(new RadiationGarbagePatternGenerator());
    }

    public ImpactResolver(RadiationGarbagePatternGenerator generator) {
        this.patternGenerator = generator == null ? new RadiationGarbagePatternGenerator() : generator;
    }

    /**
     * Resolve a single impact. Side effects (garbage insertion, charge
     * reduction, silo damage, timer scheduling, state mutation) are all
     * applied by this method. Newly-scheduled delayed waves are reported
     * via {@code waveSink} (typically the match coordinator's
     * {@code addPendingImpactWave}).
     */
    public ImpactResult resolveImpact(ActiveLaunchState launch,
                                      IncomingThreatState threat,
                                      ParticipantState attacker,
                                      ParticipantState defender,
                                      PieceTimerManager timerManager,
                                      long impactSequenceNumber,
                                      Consumer<ImpactWaveState> waveSink) {
        return resolveImpact(launch, threat, attacker, defender, timerManager,
                impactSequenceNumber, waveSink,
                CivilDefenseMitigation.none(), null);
    }

    /**
     * Step 8 entry point: same as the simpler overload but applies
     * civil-defense mitigation (after intercept mitigation) and runs
     * an {@link ImpactGracePolicy} on the planned immediate rows so
     * unsafe overflow is shifted into delayed waves instead of an
     * instant top-out.
     */
    public ImpactResult resolveImpact(ActiveLaunchState launch,
                                      IncomingThreatState threat,
                                      ParticipantState attacker,
                                      ParticipantState defender,
                                      PieceTimerManager timerManager,
                                      long impactSequenceNumber,
                                      Consumer<ImpactWaveState> waveSink,
                                      CivilDefenseMitigation civilDefense,
                                      ImpactGracePolicy gracePolicy) {
        if (launch == null) {
            return ImpactResult.skipped(ImpactResolutionStatus.LAUNCH_NOT_FOUND, null, null,
                    "launch not found");
        }
        if (threat == null) {
            return ImpactResult.skipped(ImpactResolutionStatus.THREAT_NOT_FOUND,
                    launch.getLaunchId(), null, "threat not found");
        }
        if (defender == null) {
            return ImpactResult.skipped(ImpactResolutionStatus.DEFENDER_NOT_FOUND,
                    launch.getLaunchId(), threat.getThreatId(), "defender not found");
        }
        if (launch.getPhase() == LaunchPhase.RESOLVED
                || threat.getStatus() == ThreatStatus.RESOLVED) {
            return ImpactResult.skipped(ImpactResolutionStatus.SKIPPED_ALREADY_RESOLVED,
                    launch.getLaunchId(), threat.getThreatId(), "already resolved");
        }
        if (launch.getPhase() == LaunchPhase.CANCELLED
                || threat.getStatus() == ThreatStatus.CANCELLED
                || threat.getStatus() == ThreatStatus.INTERCEPTED) {
            return ImpactResult.skipped(ImpactResolutionStatus.SKIPPED_CANCELLED,
                    launch.getLaunchId(), threat.getThreatId(), "cancelled or intercepted");
        }
        // Defensive: full mitigation flag set without status update.
        InterceptMitigationState mit = threat.getInterceptMitigation();
        if (mit != null && mit.isFullyIntercepted()) {
            return ImpactResult.skipped(ImpactResolutionStatus.SKIPPED_CANCELLED,
                    launch.getLaunchId(), threat.getThreatId(), "fully intercepted");
        }
        if (launch.getPhase() != LaunchPhase.IMPACT_READY
                || threat.getStatus() != ThreatStatus.IMPACT_READY) {
            return ImpactResult.skipped(ImpactResolutionStatus.SKIPPED_NOT_READY,
                    launch.getLaunchId(), threat.getThreatId(), "not impact-ready");
        }

        NukeDesign design = launch.getNukeDesign();
        // Damage numbers come straight from the design (DEFCON-independent).
        int blast = design == null ? 0 : design.getBlastRating();
        int radRating = design == null ? 0 : design.getRadiationRating();
        int disarm = design == null ? 0 : design.getDisarmRating();
        int siloDmg = design == null ? 0 : design.getSiloDamageRating();

        blast += Math.max(0, launch.getExtraGarbageLines());

        if (launch.isManualOverride()) {
            blast = halfRoundedUp(blast);
            radRating = halfRoundedUp(radRating);
            disarm = halfRoundedUp(disarm);
            siloDmg = halfRoundedUp(siloDmg);
        }
        if (launch.isEmpWeakened()) {
            blast = Math.max(0, blast / 3);
            radRating = Math.max(0, radRating / 3);
            disarm = Math.max(0, disarm / 3);
            siloDmg = Math.max(0, siloDmg / 3);
        }

        // Apply intercept mitigation multipliers — NEVER mutate NukeDesign.
        double blastMul = mit == null ? 1.0 : mit.getEffectiveBlastMultiplier();
        double radMul   = mit == null ? 1.0 : mit.getEffectiveRadiationMultiplier();
        double disMul   = mit == null ? 1.0 : mit.getEffectiveDisarmMultiplier();
        double siloMul  = mit == null ? 1.0 : mit.getEffectiveSiloDamageMultiplier();
        int afterInterceptBlast    = (int) Math.round(blast    * blastMul);
        int afterInterceptRad      = (int) Math.round(radRating* radMul);
        int afterInterceptDisarm   = (int) Math.round(disarm   * disMul);
        int afterInterceptSiloDmg  = (int) Math.round(siloDmg  * siloMul);

        // ── Step 8: civil defense applies AFTER intercept ──
        CivilDefenseMitigation cd = civilDefense == null
                ? CivilDefenseMitigation.none() : civilDefense;
        int effectiveBlast    = (int) Math.round(afterInterceptBlast   * cd.blastMultiplier());
        int effectiveRadRating= (int) Math.round(afterInterceptRad     * cd.radiationMultiplier());
        int effectiveDisarm   = (int) Math.round(afterInterceptDisarm  * cd.disarmMultiplier());
        int effectiveSiloDmg  = (int) Math.round(afterInterceptSiloDmg * cd.siloDamageMultiplier());

        effectiveBlast += MabUpgradeEffectResolver.extraIncomingGarbage(
                defender.getUpgradeInventory());
        if (effectiveBlast > 0) {
            int stackHeight = defender.getGameState() == null
                    ? 0 : defender.getGameState().getBoardHeight();
            boolean stackUpperThird = stackHeight >= 13;
            int upgradeReduction = MabUpgradeEffectResolver.incomingGarbageMitigation(
                    defender.getUpgradeInventory(),
                    stackUpperThird,
                    defender.isEmergencyProtocolsUsed(),
                    defender.isBunkerUsed(),
                    effectiveBlast);
            if (upgradeReduction > 0) {
                effectiveBlast = Math.max(1, effectiveBlast - upgradeReduction);
                if (defender.getUpgradeInventory().hasTag("defense_bunker")
                        && !defender.isBunkerUsed()) {
                    defender.setBunkerUsed(true);
                }
                if (stackUpperThird
                        && defender.getUpgradeInventory().hasTag("defense_emergency")
                        && !defender.isEmergencyProtocolsUsed()) {
                    defender.setEmergencyProtocolsUsed(true);
                }
            }
        }
        RadiationLevel rad = RadiationLevel.fromRating(effectiveRadRating);

        // ─── Build the garbage plan ───
        ImpactGarbagePlan plan = buildPlan(design, defender, rad,
                effectiveBlast, launch.getLaunchId());

        // Civil defense flat reduction on the planned immediate rows.
        int plannedImmediateRows = plan.getImmediatePatterns().size();
        int cdImmediateReduced = Math.min(plannedImmediateRows, cd.immediateGarbageReduction());
        int requestedImmediate = Math.max(0, plannedImmediateRows - cdImmediateReduced);

        // ─── Grace policy decides what's safe to insert right now ───
        ImpactGracePolicy gp = gracePolicy == null ? new ImpactGracePolicy() : gracePolicy;
        ImpactGraceDecision grace = gp.decide(defender, requestedImmediate,
                plan.getDelayedWavePatterns().size(), cd.extraGraceRows(),
                "nuke:" + launch.getLaunchId());
        int allowedImmediate = grace.allowedImmediateRows();
        int graceDeferred = grace.deferredRows();

        if (graceDeferred > 0) {
            boolean deadHandReady = MabUpgradeEffectResolver.deadHandProtocolActive(
                    defender.getUpgradeInventory()) && !defender.isDeadHandUsed();
            boolean hardenedReady = MabUpgradeEffectResolver.hardenedSilosActive(
                    defender.getUpgradeInventory()) && !defender.isHardenedSilosUsed();
            if (deadHandReady) {
                defender.setDeadHandUsed(true);
                graceDeferred = 0;
            } else if (hardenedReady) {
                defender.setHardenedSilosUsed(true);
                graceDeferred = 0;
            }
        }

        // ─── Apply immediate garbage (allowed slice only) ───
        int immediateApplied = 0;
        List<GarbageRowPattern> immediatePatterns = plan.getImmediatePatterns();
        if (allowedImmediate > 0 && !immediatePatterns.isEmpty()) {
            // Drop the leading rows that civil-defense + grace cut, keep the tail.
            int startIdx = Math.max(0, immediatePatterns.size() - allowedImmediate);
            List<GarbageRowPattern> slice = immediatePatterns.subList(
                    startIdx, immediatePatterns.size());
            String source = "nuke:" + launch.getLaunchId() + ":immediate:" + rad.name().toLowerCase();
            defender.getGameState().insertGarbagePattern(slice, source);
            immediateApplied = slice.size();
        }

        // ─── Schedule designed delayed waves ───
        int delayedTotal = 0;
        List<List<GarbageRowPattern>> waves = plan.getDelayedWavePatterns();
        for (int waveIndex = 0; waveIndex < waves.size(); waveIndex++) {
            int rows = waves.get(waveIndex).size();
            delayedTotal += rows;
            String waveId = "wave-" + launch.getLaunchId() + "-" + (waveIndex + 1);
            String timerId = "impact_garbage_wave:" + launch.getLaunchId() + ":" + waveIndex;
            // Sequential scheduling: each wave's timer is independent
            // and runs piecesBetweenWaves defender-pieces from now.
            int pieces = Math.max(1, plan.getPiecesBetweenWaves() * (waveIndex + 1));
            ImpactWaveState wave = new ImpactWaveState(
                    waveId, launch.getLaunchId(), defender.getId(),
                    waveIndex, waves.size(), rows, rad, timerId);
            if (timerManager != null) {
                timerManager.addTimer(new PieceCountdownTimer(
                        timerId, defender.getId(), TimerAdvanceMode.OWNER_PIECES,
                        pieces, "impact_garbage_wave:" + launch.getLaunchId() + ":" + waveIndex));
            }
            if (waveSink != null) waveSink.accept(wave);
        }

        // ─── Schedule grace-deferred rows as additional 1-row waves ───
        if (graceDeferred > 0) {
            int boardWidth = boardWidthOf(defender);
            int graceWaveCount = graceDeferred; // one row per wave keeps it gentle
            int baseWaveOffset = waves.size();
            for (int i = 0; i < graceWaveCount; i++) {
                List<GarbageRowPattern> rowPattern = patternGenerator.generate(
                        1, boardWidth, rad,
                        launch.getLaunchId() + ":grace-wave:" + i);
                int waveIndex = baseWaveOffset + i;
                String waveId = "wave-" + launch.getLaunchId() + "-grace-" + (i + 1);
                String timerId = "impact_garbage_wave:" + launch.getLaunchId() + ":grace:" + i;
                int pieces = 2 * (i + 1); // 2 pieces between grace waves
                ImpactWaveState wave = new ImpactWaveState(
                        waveId, launch.getLaunchId(), defender.getId(),
                        waveIndex, baseWaveOffset + graceWaveCount,
                        rowPattern.size(), rad, timerId);
                if (timerManager != null) {
                    timerManager.addTimer(new PieceCountdownTimer(
                            timerId, defender.getId(), TimerAdvanceMode.OWNER_PIECES,
                            pieces,
                            "nuke:" + launch.getLaunchId() + ":grace-wave:" + i
                                    + ":" + rad.name().toLowerCase()));
                }
                if (waveSink != null) waveSink.accept(wave);
            }
            delayedTotal += graceDeferred;
        }

        // ─── Step 9: silo-upgrade mitigation (after intercept + CD) ───
        SiloState siloState = defender.getSiloState();
        double hardRed = Math.min(0.30, 0.10 * siloState.getHardeningLevel());
        double distRed = Math.min(0.30, 0.15 * siloState.getDistributedStockpileLevel());
        double bunkerRed = Math.min(0.30, 0.15 * siloState.getDeepBunkerLevel());
        double doorRed = Math.min(0.20, 0.10 * siloState.getBlastDoorLevel());
        int preUpgradeDisarm = effectiveDisarm;
        int preUpgradeSiloDmg = effectiveSiloDmg;
        effectiveDisarm = (int) Math.round(effectiveDisarm * (1.0 - hardRed) * (1.0 - distRed));
        effectiveSiloDmg = (int) Math.round(effectiveSiloDmg * (1.0 - bunkerRed) * (1.0 - doorRed));
        int siloUpgradeDisarmReduced = Math.max(0, preUpgradeDisarm - effectiveDisarm);
        int siloUpgradeDamageReduced = Math.max(0, preUpgradeSiloDmg - effectiveSiloDmg);

        // ─── Apply disarm damage ───
        int chargeBefore = defender.getNukeBuildState().getCurrentBuildCharge();
        int disarmApplied = Math.min(chargeBefore, Math.max(0, effectiveDisarm));
        if (disarmApplied > 0) {
            defender.getNukeBuildState().reduceCharge(disarmApplied);
        }
        int chargeAfter = defender.getNukeBuildState().getCurrentBuildCharge();
        boolean fullyDisarmed = chargeBefore > 0 && chargeAfter == 0;

        // ─── Apply silo damage ───
        int siloBefore = defender.getSiloState().getIntegrity();
        int siloAppliedAmt = Math.max(0, effectiveSiloDmg);
        if (siloAppliedAmt > 0) {
            defender.getSiloState().applyDamage(siloAppliedAmt);
        }
        int siloAfter = defender.getSiloState().getIntegrity();

        // ─── Apply EMP strategic disruption ───
        // EMP is gameplay-level tempo disruption only. It NEVER touches
        // radar, warning, or intel systems (those have been removed
        // from the MAB design). Effects, in order:
        //   1. extra charge drain on top of disarm,
        //   2. route-progress loss (Tetris and spin pips),
        //   3. launch-delay pieces added to any in-flight opponent launch.
        com.tetris.mab.nuke.EmpProfile empProfile =
                design == null ? null : design.getEmpProfile();
        int empChargeDrain = 0;
        int empRouteLoss = 0;
        int empLaunchDelay = 0;
        if (empProfile != null && !launch.isEmpWeakened()) {
            int chargeBeforeEmp = defender.getNukeBuildState().getCurrentBuildCharge();
            int rawDrain = Math.max(0, empProfile.chargeDrain());
            if (launch.isManualOverride()) rawDrain = halfRoundedUp(rawDrain);
            empChargeDrain = Math.min(chargeBeforeEmp, rawDrain);
            if (empChargeDrain > 0) {
                defender.getNukeBuildState().reduceCharge(empChargeDrain);
            }
            empRouteLoss = Math.max(0, empProfile.routeProgressLoss());
            if (empRouteLoss > 0) {
                com.tetris.mab.clear.MabSimplifiedStrategicState ss =
                        defender.getSimplifiedState();
                if (ss != null) {
                    // Drain spin pips first (smaller pool), then Tetris pips.
                    int remaining = empRouteLoss;
                    while (remaining > 0 && ss.launchSpinProgress() > 0) {
                        ss.decrementSpinProgress();
                        remaining--;
                    }
                    while (remaining > 0 && ss.launchTetrisProgress() > 0) {
                        ss.decrementTetrisProgress();
                        remaining--;
                    }
                }
            }
            empLaunchDelay = Math.max(0, empProfile.launchDelayPieces());
            if (empLaunchDelay > 0) {
                for (ActiveLaunchState al : defender.getActiveLaunches()) {
                    if (al.getPhase() == LaunchPhase.COUNTDOWN
                            || al.getPhase() == LaunchPhase.IN_FLIGHT) {
                        al.markEmpWeakened();
                    }
                }
            }
        }

        // ─── Mark resolved ───
        launch.markResolved();
        threat.markResolved();

        StringBuilder msg = new StringBuilder("ok #").append(impactSequenceNumber);
        if (mit != null && mit.getTotalInterceptPowerApplied() > 0) {
            msg.append(" mitigated:").append(mit.toDebugString());
        }
        if (launch.isManualOverride()) {
            msg.append(" manualOverride");
        }
        if (launch.isEmpWeakened()) {
            msg.append(" empWeakened");
        }
        if (launch.getExtraGarbageLines() > 0) {
            msg.append(" extraGarbage=").append(launch.getExtraGarbageLines());
        }
        if (defender.isHardenedSilosUsed()) {
            msg.append(" hardenedSilosState");
        }
        if (defender.isDeadHandUsed()) {
            msg.append(" deadHandState");
        }
        if (cd.active()) {
            msg.append(" cd:").append(cd.toDebugString());
        }
        if (grace.deferredAny()) {
            msg.append(" grace:").append(grace.toDebugString());
        }
        if (empChargeDrain > 0 || empRouteLoss > 0 || empLaunchDelay > 0) {
            msg.append(" emp:chargeDrain=").append(empChargeDrain)
               .append(",routeLoss=").append(empRouteLoss)
               .append(",launchDelay=").append(empLaunchDelay);
        }

        return new ImpactResult(
                ImpactResolutionStatus.RESOLVED,
                launch.getLaunchId(), threat.getThreatId(),
                launch.getAttacker(), launch.getDefender(),
                launch.getNukeDesignId(), launch.getNukeDisplayName(),
                launch.getDoctrineType() == null ? null : launch.getDoctrineType().name(),
                launch.getSizeCategory() == null ? null : launch.getSizeCategory().name(),
                effectiveBlast, effectiveRadRating, effectiveDisarm, effectiveSiloDmg,
                immediateApplied, delayedTotal,
                rad,
                chargeBefore, chargeAfter, disarmApplied, fullyDisarmed,
                siloBefore, siloAfter, siloBefore - siloAfter,
                cd.active(), cd.chargesConsumed(), cdImmediateReduced,
                requestedImmediate, allowedImmediate, graceDeferred,
                siloUpgradeDisarmReduced, siloUpgradeDamageReduced,
                msg.toString());
    }

    private static int halfRoundedUp(int value) {
        if (value <= 0) return 0;
        return (value + 1) / 2;
    }

    private ImpactGarbagePlan buildPlan(NukeDesign design,
                                        ParticipantState defender,
                                        RadiationLevel rad,
                                        int effectiveBlastLines,
                                        String launchId) {
        if (design == null) {
            return new ImpactGarbagePlan(0, 0, 0, 0, 0, rad, List.of(), List.of());
        }
        GarbageProfile gp = design.getGarbageProfile();
        int total = Math.max(0, effectiveBlastLines);
        int designedTotal = gp == null ? 0 : Math.max(0, gp.baseGarbageLines());
        int maxImmediate = gp == null ? total : Math.max(0, gp.maxImmediateLines());
        // If mitigation reduced total below the design max-immediate cap,
        // still respect the cap proportionally so very small impacts don't
        // all become "immediate".
        if (designedTotal > 0 && total < designedTotal) {
            maxImmediate = Math.min(maxImmediate, total);
        }
        int immediateLines = Math.min(total, maxImmediate);
        int delayedLines = Math.max(0, total - immediateLines);
        int waveCount = (gp != null && gp.usesWaves()) ? Math.max(0, gp.waveCount()) : 0;
        int piecesBetweenWaves = gp == null ? 0 : Math.max(0, gp.piecesBetweenWaves());
        int boardWidth = boardWidthOf(defender);

        List<GarbageRowPattern> immediatePatterns = patternGenerator.generate(
                immediateLines, boardWidth, rad, launchId + ":immediate");

        List<List<GarbageRowPattern>> wavePatterns = new ArrayList<>();
        if (delayedLines > 0 && waveCount > 0) {
            int rowsPerWave = delayedLines / waveCount;
            int remainder = delayedLines - rowsPerWave * waveCount;
            for (int i = 0; i < waveCount; i++) {
                int waveRows = rowsPerWave + (i < remainder ? 1 : 0);
                if (waveRows <= 0) continue;
                wavePatterns.add(patternGenerator.generate(
                        waveRows, boardWidth, rad, launchId + ":wave:" + i));
            }
        } else if (delayedLines > 0) {
            // No declared waves but we have delayed lines: lump them into one wave.
            wavePatterns.add(patternGenerator.generate(
                    delayedLines, boardWidth, rad, launchId + ":wave:0"));
            waveCount = 1;
        }

        return new ImpactGarbagePlan(total, immediateLines, delayedLines,
                waveCount, piecesBetweenWaves, rad, immediatePatterns, wavePatterns);
    }

    private static int boardWidthOf(ParticipantState defender) {
        // Engine board width is fixed for this project; defender param
        // kept for symmetry / future per-board widths.
        if (defender == null) return Board.WIDTH;
        return Board.WIDTH;
    }
}
