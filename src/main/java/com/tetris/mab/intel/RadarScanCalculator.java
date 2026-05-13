package com.tetris.mab.intel;

import com.tetris.mab.ActiveLaunchState;
import com.tetris.mab.IncomingThreatState;
import com.tetris.mab.NukeBuildState;
import com.tetris.mab.ParticipantState;
import com.tetris.mab.SiloState;
import com.tetris.mab.UpgradeState;
import com.tetris.mab.decoy.ActiveDecoyState;
import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.nuke.NukeDoctrineType;
import com.tetris.mab.upgrade.UpgradeType;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates a (scanner, target, defcon, scan-type) tuple into a
 * {@link RadarScanResult}. Pure / deterministic; produces no side
 * effects on participant state.
 *
 * <p>Step 11: this calculator now reads the target's active decoys
 * and applies confidence penalties, false signature counts, doctrine
 * spoofing, dummy silo heat inflation, and masked-launch suppression.
 * High-confidence {@code FULL_READOUT} scans (\u2265 85) pierce these
 * effects.
 */
public final class RadarScanCalculator {

    /** Confidence at or above which FULL_READOUT scans pierce decoys. */
    public static final int PIERCE_CONFIDENCE = 85;

    public RadarScanResult scan(ParticipantState scanner,
                                 ParticipantState target,
                                 int defconLevel,
                                 int scanSequenceNumber,
                                 RadarScanType requestedType) {
        if (scanner == null || target == null) {
            return RadarScanResult.failed(
                    scanner == null ? null : scanner.getId(),
                    target == null ? null : target.getId(),
                    requestedType == null ? RadarScanType.BASIC : requestedType,
                    scanSequenceNumber,
                    "scanner or target null");
        }
        RadarScanType scanType = requestedType == null ? RadarScanType.BASIC : requestedType;

        if (scanType == RadarScanType.DEBUG) {
            return buildResult(scanner, target, defconLevel, scanSequenceNumber,
                    scanType, IntelLevel.FULL_READOUT, 100);
        }

        UpgradeState upg = scanner.getUpgradeState();
        int radar = upg.getLevel(UpgradeType.EARLY_WARNING_RADAR);
        int signal = upg.getLevel(UpgradeType.SIGNAL_ANALYSIS);
        int tracking = upg.getLevel(UpgradeType.THREAT_TRACKING);
        int camo = target.getSiloState().getCamouflageLevel();

        IntelLevel level;
        if (defconLevel <= 3) level = IntelLevel.DOCTRINE_ESTIMATE;
        else level = IntelLevel.SIZE_ESTIMATE;

        level = bumpRank(level, radar);
        if (signal >= 1) level = bumpRank(level, 1);
        if (tracking >= 1 && level.rank() < IntelLevel.LAUNCH_WARNING.rank()) {
            level = IntelLevel.LAUNCH_WARNING;
        }
        if (defconLevel <= 2) level = bumpRank(level, 1);

        int confidence = 60;
        confidence += 10 * radar;
        confidence += 10 * signal;
        boolean hasLaunchOrThreat = !target.getActiveLaunches().isEmpty()
                || !scanner.getIncomingThreats().isEmpty();
        if (hasLaunchOrThreat) confidence += 10 * tracking;
        if (signal >= 2) confidence += 10;
        confidence -= 5 * camo;
        // Step 11: decoys further reduce scanner confidence.
        confidence -= sumActiveDecoyPenalties(target);
        if (confidence < 10) confidence = 10;
        if (confidence > 100) confidence = 100;

        return buildResult(scanner, target, defconLevel, scanSequenceNumber,
                scanType, level, confidence);
    }

    private static int sumActiveDecoyPenalties(ParticipantState target) {
        int p = 0;
        for (ActiveDecoyState d : target.getActiveDecoys()) {
            if (d.isActive()) p += d.getConfidencePenalty();
        }
        return p;
    }

    private static IntelLevel bumpRank(IntelLevel base, int delta) {
        if (delta <= 0) return base;
        int targetRank = Math.min(IntelLevel.FULL_READOUT.rank(), base.rank() + delta);
        for (IntelLevel v : IntelLevel.values()) {
            if (v.rank() == targetRank) return v;
        }
        return IntelLevel.FULL_READOUT;
    }

    private static RadarScanResult buildResult(ParticipantState scanner,
                                                ParticipantState target,
                                                int defconLevel,
                                                int scanSequenceNumber,
                                                RadarScanType scanType,
                                                IntelLevel level,
                                                int confidence) {
        NukeBuildState targetBuild = target.getNukeBuildState();
        NukeDesign design = targetBuild.getCurrentDesign();
        SiloState silo = target.getSiloState();

        // Step 11: decoy bookkeeping.
        List<ActiveDecoyState> activeDecoys = collectActiveDecoys(target);
        boolean piercing = level == IntelLevel.FULL_READOUT && confidence >= PIERCE_CONFIDENCE;
        StringBuilder decoyMsg = new StringBuilder();

        // Defaults.
        String designId = null, displayName = null, doctrine = null, size = null;
        int chargeEst = -1, requiredEst = -1;
        boolean armedEst = false;
        String siloDamage = null;
        int siloIntegrity = -1;
        int activeLaunchCount = -1;
        int incomingThreatCount = -1;
        String firstLaunchId = null, firstLaunchPhase = null;
        int firstLaunchCountdown = -1;
        String firstThreatId = null, firstThreatStatus = null;
        int firstThreatWarning = -1;

        if (level.atLeast(IntelLevel.SIZE_ESTIMATE) && design != null) {
            size = design.getSizeCategory() == null ? null
                    : design.getSizeCategory().name();
        }
        if (level.atLeast(IntelLevel.DOCTRINE_ESTIMATE) && design != null) {
            NukeDoctrineType realDoctrine = design.getDoctrineType();
            if (!piercing && hasActiveDecoyOfFlag(activeDecoys, DecoyFlag.DOCTRINE)) {
                NukeDoctrineType fake = spoofDoctrine(realDoctrine);
                doctrine = fake == null ? null : fake.name();
                appendMsg(decoyMsg, "doctrine spoofed by decoy");
            } else {
                doctrine = realDoctrine == null ? null : realDoctrine.name();
                if (piercing && hasActiveDecoyOfFlag(activeDecoys, DecoyFlag.DOCTRINE)) {
                    appendMsg(decoyMsg, "false doctrine signal pierced");
                }
            }
            designId = design.getId();
            displayName = design.getDisplayName();
        }
        if (level.atLeast(IntelLevel.PROGRESS_ESTIMATE)) {
            int realCharge = targetBuild.getCurrentBuildCharge();
            int realRequired = targetBuild.getEffectiveBuildChargeRequired();
            int displayedCharge;
            if (!piercing && hasActiveDecoyOfFlag(activeDecoys, DecoyFlag.SILO_HEAT)) {
                int boost = Math.max(10, (int) Math.round(realRequired * 0.25));
                displayedCharge = Math.min(realRequired, realCharge + boost);
                appendMsg(decoyMsg, "build progress inflated by dummy silo heat");
            } else {
                displayedCharge = realCharge;
                if (piercing && hasActiveDecoyOfFlag(activeDecoys, DecoyFlag.SILO_HEAT)) {
                    appendMsg(decoyMsg, "dummy silo heat filtered");
                }
            }
            chargeEst = roundToNearest(displayedCharge, 5);
            requiredEst = roundToNearest(realRequired, 5);
            if (confidence >= 60) armedEst = targetBuild.isArmed();
            else armedEst = targetBuild.isArmed() && realCharge >= realRequired;
            siloDamage = silo.getDamageState() == null ? null : silo.getDamageState().name();
        }
        if (level.atLeast(IntelLevel.LAUNCH_WARNING)) {
            int realLaunches = target.getActiveLaunches().size();
            int realThreats = scanner.getIncomingThreats().size();
            int falseLaunches = sumFalseLaunches(activeDecoys);
            int falseThreats = sumFalseThreats(activeDecoys);
            int maskedHidden = countMaskedHidden(activeDecoys, target);

            int displayedLaunches = realLaunches + falseLaunches - maskedHidden;
            int displayedThreats = realThreats + falseThreats;

            if (piercing) {
                displayedLaunches = realLaunches;
                displayedThreats = realThreats;
                if (falseLaunches > 0 || falseThreats > 0) {
                    appendMsg(decoyMsg, "decoy signatures identified");
                }
                if (maskedHidden > 0) {
                    appendMsg(decoyMsg, "masking pierced");
                }
            } else {
                if (falseLaunches > 0 || falseThreats > 0) {
                    appendMsg(decoyMsg, "false signatures present");
                }
                if (maskedHidden > 0) {
                    appendMsg(decoyMsg, "masked launch suppressed from count");
                }
            }
            if (displayedLaunches < 0) displayedLaunches = 0;
            if (displayedThreats < 0) displayedThreats = 0;
            activeLaunchCount = displayedLaunches;
            incomingThreatCount = displayedThreats;

            ActiveLaunchState firstLaunch = pickVisibleFirstLaunch(target, activeDecoys, piercing);
            if (firstLaunch != null) {
                firstLaunchId = firstLaunch.getLaunchId();
                firstLaunchPhase = firstLaunch.getPhase() == null
                        ? null : firstLaunch.getPhase().name();
                firstLaunchCountdown = roundUpToNearest(
                        firstLaunch.getLaunchCountdownPieces(), 2);
            }
            IncomingThreatState firstThreat = scanner.getIncomingThreats().isEmpty()
                    ? null : scanner.getIncomingThreats().get(0);
            if (firstThreat != null) {
                firstThreatId = firstThreat.getThreatId();
                firstThreatStatus = firstThreat.getStatus() == null
                        ? null : firstThreat.getStatus().name();
                firstThreatWarning = roundUpToNearest(
                        firstThreat.getWarningPiecesRemaining(), 2);
            }
        }
        if (level.atLeast(IntelLevel.FULL_READOUT)) {
            int realCharge = targetBuild.getCurrentBuildCharge();
            int realRequired = targetBuild.getEffectiveBuildChargeRequired();
            if (piercing) {
                chargeEst = realCharge;
                requiredEst = realRequired;
                armedEst = targetBuild.isArmed();
                siloIntegrity = silo.getIntegrity();
                ActiveLaunchState firstLaunch = target.getActiveLaunches().isEmpty()
                        ? null : target.getActiveLaunches().get(0);
                if (firstLaunch != null) {
                    firstLaunchCountdown = firstLaunch.getLaunchCountdownPieces();
                }
                IncomingThreatState firstThreat = scanner.getIncomingThreats().isEmpty()
                        ? null : scanner.getIncomingThreats().get(0);
                if (firstThreat != null) {
                    firstThreatWarning = firstThreat.getWarningPiecesRemaining();
                }
            } else {
                requiredEst = realRequired;
                armedEst = targetBuild.isArmed();
            }
        }

        String message = "scan ok level=" + level + " conf=" + confidence;
        if (decoyMsg.length() > 0) message += "; " + decoyMsg;

        return RadarScanResult.success(
                scanner.getId(), target.getId(), scanType, level,
                scanSequenceNumber,
                scanner.getPiecesLocked(), target.getPiecesLocked(),
                defconLevel,
                designId, displayName, doctrine, size,
                chargeEst, requiredEst, armedEst,
                siloDamage, siloIntegrity,
                activeLaunchCount, incomingThreatCount,
                firstLaunchId, firstLaunchPhase, firstLaunchCountdown,
                firstThreatId, firstThreatStatus, firstThreatWarning,
                confidence,
                message);
    }

    // ─── decoy helpers ────────────────────────────────────────

    private enum DecoyFlag { DOCTRINE, SILO_HEAT }

    private static List<ActiveDecoyState> collectActiveDecoys(ParticipantState target) {
        List<ActiveDecoyState> out = new ArrayList<>();
        for (ActiveDecoyState d : target.getActiveDecoys()) {
            if (d.isActive()) out.add(d);
        }
        return out;
    }

    private static boolean hasActiveDecoyOfFlag(List<ActiveDecoyState> decoys, DecoyFlag flag) {
        for (ActiveDecoyState d : decoys) {
            if (flag == DecoyFlag.DOCTRINE && d.falsifiesDoctrine()) return true;
            if (flag == DecoyFlag.SILO_HEAT && d.falsifiesBuildProgress()) return true;
        }
        return false;
    }

    private static int sumFalseLaunches(List<ActiveDecoyState> decoys) {
        int n = 0;
        for (ActiveDecoyState d : decoys) {
            if (d.createsFalseLaunchSignature()) n += d.getFalseLaunchCount();
        }
        return n;
    }

    private static int sumFalseThreats(List<ActiveDecoyState> decoys) {
        int n = 0;
        for (ActiveDecoyState d : decoys) {
            if (d.createsFalseThreatSignature()) n += d.getFalseThreatCount();
        }
        return n;
    }

    private static int countMaskedHidden(List<ActiveDecoyState> decoys,
                                          ParticipantState target) {
        int n = 0;
        for (ActiveDecoyState d : decoys) {
            if (!d.masksRealLaunch()) continue;
            String linked = d.getLinkedLaunchId();
            if (linked == null) continue;
            for (ActiveLaunchState l : target.getActiveLaunches()) {
                if (linked.equals(l.getLaunchId())) { n++; break; }
            }
        }
        return n;
    }

    private static ActiveLaunchState pickVisibleFirstLaunch(ParticipantState target,
                                                             List<ActiveDecoyState> decoys,
                                                             boolean piercing) {
        if (target.getActiveLaunches().isEmpty()) return null;
        if (piercing) return target.getActiveLaunches().get(0);
        for (ActiveLaunchState l : target.getActiveLaunches()) {
            if (!isMasked(l, decoys)) return l;
        }
        return null;
    }

    private static boolean isMasked(ActiveLaunchState launch, List<ActiveDecoyState> decoys) {
        for (ActiveDecoyState d : decoys) {
            if (d.masksRealLaunch() && launch.getLaunchId().equals(d.getLinkedLaunchId())) {
                return true;
            }
        }
        return false;
    }

    private static NukeDoctrineType spoofDoctrine(NukeDoctrineType real) {
        if (real == null) return NukeDoctrineType.DIRTY_BOMB;
        return switch (real) {
            case CLEAN_FUSION     -> NukeDoctrineType.DIRTY_BOMB;
            case DIRTY_BOMB       -> NukeDoctrineType.CLEAN_FUSION;
            case SALTED_WARHEAD   -> NukeDoctrineType.CLEAN_FUSION;
            case CONCRETE_BLASTER -> NukeDoctrineType.MIRV;
            case MIRV             -> NukeDoctrineType.CONCRETE_BLASTER;
            case BUNKER_BUSTER    -> NukeDoctrineType.CONCRETE_BLASTER;
            case EMP_PAYLOAD      -> NukeDoctrineType.DECOY_PACKAGE;
            case DECOY_PACKAGE    -> NukeDoctrineType.EMP_PAYLOAD;
            case DOOMSDAY         -> NukeDoctrineType.MIRV;
            case PLACEHOLDER      -> NukeDoctrineType.DIRTY_BOMB;
        };
    }

    private static void appendMsg(StringBuilder sb, String s) {
        if (sb.length() > 0) sb.append("; ");
        sb.append(s);
    }

    private static int roundToNearest(int value, int step) {
        if (step <= 1) return value;
        int sign = value < 0 ? -1 : 1;
        int abs = Math.abs(value);
        int rounded = ((abs + step / 2) / step) * step;
        return sign * rounded;
    }

    private static int roundUpToNearest(int value, int step) {
        if (step <= 1) return value;
        if (value <= 0) return value;
        return ((value + step - 1) / step) * step;
    }
}
