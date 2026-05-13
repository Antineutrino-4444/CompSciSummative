package com.tetris.mab;

/**
 * Immutable, render-agnostic snapshot of a Mutually Assured Blocks
 * match. Numeric / textual only — never carries {@code Color[][]} or
 * any other rendering payload — so it is safe to serialise, log, or
 * forward to a debug overlay.
 */
public record MatchDebugSnapshot(
        MatchMode matchMode,
        MatchDifficulty difficulty,
        MatchPhase currentPhase,
        boolean paused,
        ParticipantId winner,
        int defconLevel,
        int escalationMeter,
        ParticipantSummary playerA,
        ParticipantSummary playerB,
        int activeTimerCount,
        int recentEventCount) {

    /** Per-participant numeric/textual summary. */
    public record ParticipantSummary(
            ParticipantId id,
            int piecesLocked,
            int linesClearedTotal,
            int garbageReceivedTotal,
            String currentNukeDesignId,
            String currentNukeDisplayName,
            com.tetris.mab.nuke.NukeDoctrineType doctrineType,
            com.tetris.mab.nuke.NukeSizeCategory sizeCategory,
            int currentNukeCharge,
            int requiredNukeCharge,
            boolean armed,
            SiloDamageState siloDamageState,
            int siloIntegrity,
            int incomingThreatCount,
            int activeLaunchCount,
            int impactReadyLaunchCount,
            int impactReadyThreatCount,
            int pendingImpactWaveCount,
            String firstActiveLaunchId,
            com.tetris.mab.launch.LaunchPhase firstActiveLaunchPhase,
            String firstIncomingThreatId,
            int firstIncomingThreatWarningPiecesRemaining,
            boolean toppedOut,
            String activeActionId,
            String activeActionName,
            int activeActionProgress,
            int activeActionRequiredLength,
            int activeActionExpectedNextClear,
            String pendingConfirmationActionId,
            String pendingConfirmationActionName,
            int completedActionCount,
            String selectedInterceptThreatId,
            int interceptableThreatCount,
            int fullyInterceptedThreatCount,
            int partiallyMitigatedThreatCount,
            int civilDefenseCharges,
            int civilDefenseShieldPiecesRemaining,
            boolean civilDefenseEmergencyActive,
            int totalCivilDefenseActivations,
            int upgradePoints,
            int totalUpgradePointsEarned,
            int upgradeCount,
            String recentUpgradeSummary,
            String siloUpgradeLevels,
            String civilDefenseUpgradeLevels,
            int totalRadarScans,
            int successfulRadarScans,
            int failedRadarScans,
            int bestIntelRankAchieved,
            com.tetris.mab.intel.IntelLevel lastIntelLevel,
            int lastIntelConfidence,
            boolean intelStale,
            int intelStalenessScore,
            String lastScanSummary,
            int activeDecoyCount,
            int falseLaunchSignatureCount,
            int falseThreatSignatureCount,
            int activeDecoyConfidencePenalty,
            String firstActiveDecoyId,
            com.tetris.mab.decoy.DecoyType firstActiveDecoyType,
            int maskedLaunchDecoyCount) {

        public static ParticipantSummary of(ParticipantState p, int pendingImpactWaveCount) {
            NukeBuildState nb = p.getNukeBuildState();
            com.tetris.mab.nuke.NukeDesign design = nb.getCurrentDesign();
            com.tetris.mab.action.ActionCodeManager mgr = p.getActionCodeManager();
            com.tetris.mab.action.ActionCodeAttempt active = mgr.getActiveAttempt();
            com.tetris.mab.action.ActionCodeAttempt pending = mgr.getPendingConfirmationAttempt();
            String activeId = null, activeName = null, pendingId = null, pendingName = null;
            int progress = 0, requiredLen = 0, nextClear = 0;
            if (active != null) {
                activeId = active.getDefinition().getId();
                activeName = active.getDefinition().getDisplayName();
                progress = active.getProgressIndex();
                requiredLen = active.getDefinition().sequenceLength();
                com.tetris.mab.action.ActionCodeTokenRequirement next = active.getExpectedNextRequirement();
                if (next != null) nextClear = next.requiredLineCount();
            }
            if (pending != null) {
                pendingId = pending.getDefinition().getId();
                pendingName = pending.getDefinition().getDisplayName();
            }
            int impactReadyLaunches = 0;
            ActiveLaunchState firstLaunch = p.getActiveLaunches().isEmpty()
                    ? null : p.getActiveLaunches().get(0);
            for (ActiveLaunchState l : p.getActiveLaunches()) {
                if (l.isImpactReady()) impactReadyLaunches++;
            }
            int impactReadyThreats = 0;
            int interceptable = 0;
            int fullyIntercepted = 0;
            int partiallyMitigated = 0;
            IncomingThreatState firstThreat = p.getIncomingThreats().isEmpty()
                    ? null : p.getIncomingThreats().get(0);
            for (IncomingThreatState t : p.getIncomingThreats()) {
                com.tetris.mab.launch.ThreatStatus s = t.getStatus();
                if (s == com.tetris.mab.launch.ThreatStatus.IMPACT_READY) impactReadyThreats++;
                if (s == com.tetris.mab.launch.ThreatStatus.WARNING_ACTIVE) interceptable++;
                if (s == com.tetris.mab.launch.ThreatStatus.INTERCEPTED) fullyIntercepted++;
                com.tetris.mab.intercept.InterceptMitigationState mit = t.getInterceptMitigation();
                if (mit != null && mit.getTotalInterceptPowerApplied() > 0 && !mit.isFullyIntercepted()) {
                    partiallyMitigated++;
                }
            }
            return new ParticipantSummary(
                    p.getId(),
                    p.getPiecesLocked(),
                    p.getLinesClearedTotal(),
                    p.getGarbageReceivedTotal(),
                    design.getId(),
                    design.getDisplayName(),
                    design.getDoctrineType(),
                    design.getSizeCategory(),
                    nb.getCurrentBuildCharge(),
                    nb.getEffectiveBuildChargeRequired(),
                    nb.isArmed(),
                    p.getSiloState().getDamageState(),
                    p.getSiloState().getIntegrity(),
                    p.getIncomingThreats().size(),
                    p.getActiveLaunches().size(),
                    impactReadyLaunches,
                    impactReadyThreats,
                    pendingImpactWaveCount,
                    firstLaunch == null ? null : firstLaunch.getLaunchId(),
                    firstLaunch == null ? null : firstLaunch.getPhase(),
                    firstThreat == null ? null : firstThreat.getThreatId(),
                    firstThreat == null ? 0 : firstThreat.getWarningPiecesRemaining(),
                    p.isToppedOut(),
                    activeId, activeName, progress, requiredLen, nextClear,
                    pendingId, pendingName,
                    mgr.getCompletedAttempts().size(),
                    p.getSelectedInterceptThreatId(),
                    interceptable,
                    fullyIntercepted,
                    partiallyMitigated,
                    p.getCivilDefenseState().getActiveCharges(),
                    p.getCivilDefenseState().getShieldPiecesRemaining(),
                    p.getCivilDefenseState().isEmergencyProtocolActive(),
                    p.getCivilDefenseState().getTotalActivations(),
                    p.getUpgradeState().getUpgradePoints(),
                    p.getUpgradeState().getTotalUpgradePointsEarned(),
                    p.getUpgradeState().getTotalLevels(),
                    summarizeRecentUpgrades(p.getUpgradeState()),
                    summarizeSiloUpgrades(p.getSiloState()),
                    summarizeCivilDefenseUpgrades(p.getCivilDefenseState()),
                    p.getRadarIntel().getTotalScansPerformed(),
                    p.getRadarIntel().getSuccessfulScans(),
                    p.getRadarIntel().getFailedScans(),
                    p.getRadarIntel().getBestIntelRankAchieved(),
                    p.getRadarIntel().getLastIntelLevel(),
                    p.getRadarIntel().getLastIntelConfidence(),
                    p.getRadarIntel().isEnemyIntelStale(),
                    p.getRadarIntel().getEnemyIntelStalenessScore(),
                    summarizeRadar(p.getRadarIntel()),
                    p.getActiveDecoyCount(),
                    p.getFalseLaunchSignatureCount(),
                    p.getFalseThreatSignatureCount(),
                    p.getActiveConfidencePenaltyAgainstScanners(),
                    firstActiveDecoyId(p),
                    firstActiveDecoyType(p),
                    countMaskedLaunchDecoys(p));
        }

        private static String summarizeRecentUpgrades(UpgradeState s) {
            java.util.List<com.tetris.mab.upgrade.UpgradeType> recent = s.getRecentlyApplied();
            if (recent.isEmpty()) return "none";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < recent.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(recent.get(i));
            }
            return sb.toString();
        }

        private static String summarizeSiloUpgrades(SiloState s) {
            return "hard=" + s.getHardeningLevel()
                    + " bunker=" + s.getDeepBunkerLevel()
                    + " dist=" + s.getDistributedStockpileLevel()
                    + " sec=" + s.getLaunchSecurityLevel()
                    + " asm=" + s.getAssemblySpeedLevel()
                    + " doors=" + s.getBlastDoorLevel()
                    + " camo=" + s.getCamouflageLevel();
        }

        private static String summarizeCivilDefenseUpgrades(CivilDefenseState c) {
            return "shelter=" + c.getShelterLevel()
                    + " gc=" + c.getGarbageControlLevel()
                    + " emerg=" + c.getEmergencyProtocolLevel();
        }

        private static String summarizeRadar(RadarIntelState r) {
            String s = r.getLastScanSummary();
            return s == null ? "no-scans" : s;
        }

        private static String firstActiveDecoyId(ParticipantState p) {
            for (com.tetris.mab.decoy.ActiveDecoyState d : p.getActiveDecoys()) {
                if (d.isActive()) return d.getDecoyId();
            }
            return null;
        }

        private static com.tetris.mab.decoy.DecoyType firstActiveDecoyType(ParticipantState p) {
            for (com.tetris.mab.decoy.ActiveDecoyState d : p.getActiveDecoys()) {
                if (d.isActive()) return d.getType();
            }
            return null;
        }

        private static int countMaskedLaunchDecoys(ParticipantState p) {
            int n = 0;
            for (com.tetris.mab.decoy.ActiveDecoyState d : p.getActiveDecoys()) {
                if (d.isActive() && d.masksRealLaunch()) n++;
            }
            return n;
        }
    }
}
