package com.tetris.mab.debugui;

import com.tetris.mab.MatchDebugSnapshot;
import com.tetris.mab.MatchEventLogEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Step 12 — pure formatting helpers for the MAB debug HUD.
 *
 * <p>Converts a {@link MatchDebugSnapshot} into a compact
 * monospaced text block and {@link MatchEventLogEntry} entries into
 * one-line rows. No Swing dependencies — safe to use anywhere.
 *
 * <p><b>NOT FINAL UI.</b> This is a vertical-slice debug panel.
 */
public final class MabDebugFormatter {

    private static final int MAX_EVENT_ROW_LENGTH = 200;
    private static final int MAX_META_LENGTH = 90;

    private MabDebugFormatter() {}

    /** Multi-line summary of an entire match snapshot. */
    public static String formatSnapshot(MatchDebugSnapshot s) {
        if (s == null) return "(no snapshot)";
        StringBuilder sb = new StringBuilder();
        sb.append("=== MAB Debug / Vertical Slice ===\n");
        sb.append("mode=").append(s.matchMode())
          .append("  difficulty=").append(s.difficulty())
          .append("  phase=").append(s.currentPhase())
          .append(s.paused() ? "  [PAUSED]" : "");
        if (s.winner() != null) sb.append("  winner=").append(s.winner());
        sb.append('\n');
        sb.append("DEFCON=").append(s.defconLevel())
          .append("  escalation=").append(s.escalationMeter())
          .append("  activeTimers=").append(s.activeTimerCount())
          .append("  loggedEvents=").append(s.recentEventCount())
          .append('\n');
        sb.append('\n');
        sb.append(formatParticipant(s.playerA())).append('\n');
        sb.append(formatParticipant(s.playerB())).append('\n');
        return sb.toString();
    }

    /** Compact per-participant block. */
    public static String formatParticipant(MatchDebugSnapshot.ParticipantSummary p) {
        if (p == null) return "(no participant)";
        StringBuilder sb = new StringBuilder();
        sb.append("--- ").append(p.id()).append(" ---\n");
        sb.append("  pieces=").append(p.piecesLocked())
          .append("  lines=").append(p.linesClearedTotal())
          .append("  garbageRx=").append(p.garbageReceivedTotal())
          .append(p.toppedOut() ? "  TOPPED-OUT" : "")
          .append('\n');
        sb.append("  nuke=").append(p.currentNukeDisplayName())
          .append(" [").append(p.doctrineType()).append('/').append(p.sizeCategory()).append(']')
          .append("  charge=").append(p.currentNukeCharge()).append('/').append(p.requiredNukeCharge())
          .append(p.armed() ? "  ARMED" : "")
          .append('\n');
        sb.append("  silo=").append(p.siloDamageState())
          .append(" int=").append(p.siloIntegrity())
          .append("  silo-up=[").append(p.siloUpgradeLevels()).append(']')
          .append('\n');
        sb.append("  launches=").append(p.activeLaunchCount())
          .append(" (impactReady=").append(p.impactReadyLaunchCount()).append(')')
          .append("  threats=").append(p.incomingThreatCount())
          .append(" (impactReady=").append(p.impactReadyThreatCount()).append(')')
          .append("  pendingWaves=").append(p.pendingImpactWaveCount())
          .append('\n');
        if (p.firstActiveLaunchId() != null) {
            sb.append("  firstLaunch=").append(p.firstActiveLaunchId())
              .append(" phase=").append(p.firstActiveLaunchPhase())
              .append('\n');
        }
        if (p.firstIncomingThreatId() != null) {
            sb.append("  firstThreat=").append(p.firstIncomingThreatId())
              .append(" warnPiecesRemaining=").append(p.firstIncomingThreatWarningPiecesRemaining())
              .append('\n');
        }
        sb.append("  civDef charges=").append(p.civilDefenseCharges())
          .append(" shieldPieces=").append(p.civilDefenseShieldPiecesRemaining())
          .append(p.civilDefenseEmergencyActive() ? "  EMERGENCY" : "")
          .append("  totalActivations=").append(p.totalCivilDefenseActivations())
          .append("  cd-up=[").append(p.civilDefenseUpgradeLevels()).append(']')
          .append('\n');
        sb.append("  upgrade pts=").append(p.upgradePoints())
          .append(" (lifetime=").append(p.totalUpgradePointsEarned()).append(')')
          .append("  applied=").append(p.upgradeCount())
          .append("  recent=").append(p.recentUpgradeSummary())
          .append('\n');
        sb.append("  radar scans=").append(p.totalRadarScans())
          .append(" ok=").append(p.successfulRadarScans())
          .append(" fail=").append(p.failedRadarScans())
          .append("  bestRank=").append(p.bestIntelRankAchieved())
          .append("  lastIntel=").append(p.lastIntelLevel())
          .append(" conf=").append(p.lastIntelConfidence())
          .append(p.intelStale() ? "  STALE" : "")
          .append('\n');
        sb.append("  decoys active=").append(p.activeDecoyCount())
          .append("  falseLaunch=").append(p.falseLaunchSignatureCount())
          .append("  falseThreat=").append(p.falseThreatSignatureCount())
          .append("  confPenalty=").append(p.activeDecoyConfidencePenalty())
          .append("  masked=").append(p.maskedLaunchDecoyCount());
        if (p.firstActiveDecoyId() != null) {
            sb.append("  first=").append(p.firstActiveDecoyType()).append('/').append(p.firstActiveDecoyId());
        }
        sb.append('\n');
        if (p.activeActionId() != null) {
            sb.append("  actionInProgress=").append(p.activeActionId())
              .append(" prog=").append(p.activeActionProgress()).append('/').append(p.activeActionRequiredLength())
              .append(" nextClear=").append(p.activeActionExpectedNextClear())
              .append('\n');
        }
        return sb.toString();
    }

    /**
     * Formats up to {@code maxRows} most-recent events as compact one-line
     * rows. Newest events appear LAST (chronological order).
     */
    public static List<String> formatRecentEvents(List<MatchEventLogEntry> events, int maxRows) {
        List<String> out = new ArrayList<>();
        if (events == null || events.isEmpty() || maxRows <= 0) return out;
        int from = Math.max(0, events.size() - maxRows);
        for (int i = from; i < events.size(); i++) {
            out.add(formatRow(events.get(i)));
        }
        return out;
    }

    /** Help text shown in the HUD; lists controls/shortcuts. */
    public static String formatHelpText() {
        return  "MAB Debug / Vertical Slice — NOT final UI.\n"
              + "Buttons:\n"
              + "  Add Charge A/B    add 25 nuke charge (if phase allows)\n"
              + "  Arm A/B           top off current nuke design to armed\n"
              + "  Launch A/B        directly authorize a launch (auto-arms)\n"
              + "  Radar A/B         DEBUG-level radar scan against opponent\n"
              + "  CivDef A/B        manually activate civil defense\n"
              + "  Decoy A/B         activate DECOY_LAUNCH on opponent\n"
              + "  Resolve Impacts   apply every IMPACT_READY launch/threat\n"
              + "  Open/Close Pause  enter/exit upgrade pause phase\n"
              + "Keys (debug window only):\n"
              + "  F5 manual refresh   F6 charge A   F7 arm A\n"
              + "  F8 launch A         F9 radar A    F10 resolve impacts\n"
              + "  Esc closes the HUD; the game keeps running.\n";
    }

    private static String formatRow(MatchEventLogEntry e) {
        StringBuilder sb = new StringBuilder();
        sb.append('#').append(e.sequenceNumber()).append(' ').append(e.eventType());
        if (e.participantId() != null) sb.append(' ').append(e.participantId());
        if (!e.message().isEmpty()) sb.append(" — ").append(e.message());
        if (!e.metadata().isEmpty()) {
            String meta = compactMeta(e.metadata());
            if (!meta.isEmpty()) sb.append(' ').append(meta);
        }
        if (sb.length() > MAX_EVENT_ROW_LENGTH) {
            sb.setLength(MAX_EVENT_ROW_LENGTH - 1);
            sb.append('…');
        }
        return sb.toString();
    }

    private static String compactMeta(Map<String, Object> meta) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : meta.entrySet()) {
            if (!first) sb.append(' ');
            first = false;
            sb.append(entry.getKey()).append('=');
            String v = String.valueOf(entry.getValue());
            if (v.length() > 32) v = v.substring(0, 31) + "…";
            sb.append(v);
            if (sb.length() > MAX_META_LENGTH) {
                sb.append(" …");
                break;
            }
        }
        sb.append('}');
        return sb.toString();
    }
}
