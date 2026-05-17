package com.tetris.mab.ui;

import com.tetris.mab.MatchDebugSnapshot;
import com.tetris.mab.MatchDebugSnapshot.ParticipantSummary;
import com.tetris.mab.MatchEventLogEntry;
import com.tetris.mab.ParticipantId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Step 15 — formats {@link MatchDebugSnapshot} fields into compact,
 * player-facing strings. Stateless, display-only, no model mutation.
 *
 * <p>This class deliberately omits raw debug metadata, internal IDs,
 * and large maps that are useful for the developer debug HUD but not
 * for a player.
 *
 * <p><b>Offline-only.</b> No networking concepts appear here.
 */
public final class MabHudFormatter {

    /**
     * Event types that the player-facing feed surfaces. All other
     * entries from the match log are filtered out.
     */
    private static final Set<String> PLAYER_FACING_EVENTS = new HashSet<>(Arrays.asList(
            "MATCH_STARTED",
            "DEFCON_CHANGED",
            "LINES_CLEARED",
            "NUKE_CHARGE_ADDED",
            "NUKE_ARMED",
            "ACTION_STARTED",
            "ACTION_PROGRESS",
            "ACTION_COMPLETED",
            "ACTION_CONFIRMATION_PENDING",
            "ACTION_CONFIRMED",
            "LAUNCH_AUTHORIZED",
            "LAUNCH_COUNTDOWN_STARTED",
            "LAUNCH_IN_FLIGHT",
            "LAUNCH_IMPACT_READY",
            "THREAT_IMPACT_READY",
            "IMPACT_RESOLVED",
            "CIVIL_DEFENSE_ACTIVATED",
            "CIVIL_DEFENSE_CONSUMED",
            "CIVIL_DEFENSE_MITIGATION_APPLIED",
            "INTERCEPT_RESOLVED",
            "INTERCEPT_PARTIAL",
            // Legacy radar / decoy events are not surfaced to the
            // player. They remain in the event log only for replay /
            // debug analysis.
            "UPGRADE_APPLIED",
            "UPGRADE_SIDE_EFFECT_APPLIED",
            "UPGRADE_APPLY_REJECTED",
            "UPGRADE_APPLY_REJECTED_NOT_IN_PAUSE",
            "UPGRADE_PAUSE_OPENED",
            "UPGRADE_PAUSE_CLOSED",
            "UPGRADE_PAUSE_OPEN_REJECTED",
            "UPGRADE_PAUSE_CLOSE_REJECTED",
            "NUKE_REDESIGNED",
            "NUKE_REDESIGN_REJECTED",
            "AI_DECISION_EXECUTED",
            "ACTION_STARTED",
            "ACTION_ADVANCED",
            "ACTION_PENDING_CONFIRMATION",
            "ACTION_CONFIRMED",
            "ACTION_COMPLETED",
            "ACTION_CANCELLED",
            "ACTION_FAILED_RESET",
            "ACTION_CONFIRM_FAILED",
            "ACTION_START_REJECTED_NOT_ARMED",
            "ACTION_START_REJECTED_NO_THREAT",
            "ACTION_START_REJECTED_NO_ACTIVE_THREAT",
            "LAUNCH_AUTHORIZED",
            "LAUNCH_AUTHORIZATION_REJECTED_PHASE",
            "LAUNCH_AUTHORIZATION_REJECTED_NOT_ARMED",
            "LAUNCH_COUNTDOWN_STARTED",
            "LAUNCH_IN_FLIGHT",
            "INCOMING_THREAT_CREATED",
            "IMPACT_READY",
            "IMPACT_RESOLVED",
            "THREAT_FULLY_INTERCEPTED",
            "THREAT_PARTIALLY_INTERCEPTED",
            "INTERCEPT_STARTED",
            "INTERCEPT_FAILED",
            "TOP_OUT",
            "MATCH_ENDED"
    ));

    private MabHudFormatter() {}

    /** Returns a multi-line summary of the player's own arsenal/state. */
    public static String formatPlayerStatus(MatchDebugSnapshot snapshot, ParticipantId playerId) {
        ParticipantSummary p = pickSelf(snapshot, playerId);
        if (p == null) return "(no player state)";
        StringBuilder sb = new StringBuilder();
        sb.append("Nuke design : ").append(safe(p.currentNukeDisplayName())).append('\n');
        sb.append("            (").append(safe(String.valueOf(p.doctrineType())))
                .append(" / ").append(safe(String.valueOf(p.sizeCategory()))).append(")\n");
        sb.append("Charge      : ").append(p.currentNukeCharge())
                .append(" / ").append(p.requiredNukeCharge()).append('\n');
        sb.append("Armed       : ").append(p.armed() ? "YES" : "no").append('\n');
        sb.append("Silo        : ").append(p.siloIntegrity()).append("/100  (")
                .append(p.siloDamageState()).append(")\n");
        sb.append("Civ. defence: charges=").append(p.civilDefenseCharges())
                .append("  shield=").append(p.civilDefenseShieldPiecesRemaining())
                .append(p.civilDefenseEmergencyActive() ? "  EMERGENCY" : "").append('\n');
        sb.append("Upgrade pts : ").append(p.upgradePoints())
                .append("  (lifetime ").append(p.totalUpgradePointsEarned()).append(")\n");
        sb.append("Active launches: ").append(p.activeLaunchCount());
        if (p.firstActiveLaunchId() != null) {
            sb.append("  [first ").append(p.firstActiveLaunchPhase()).append(']');
        }
        sb.append('\n');
        sb.append("Decoys out  : ").append(p.activeDecoyCount());
        if (p.firstActiveDecoyType() != null) {
            sb.append("  (").append(p.firstActiveDecoyType()).append(')');
        }
        return sb.toString();
    }

    /** Returns a multi-line view of the opponent (intel-limited). */
    public static String formatOpponentStatus(MatchDebugSnapshot snapshot, ParticipantId playerId) {
        ParticipantSummary self = pickSelf(snapshot, playerId);
        if (self == null) return "(no opponent state)";
        StringBuilder sb = new StringBuilder();
        sb.append("Last intel  : ").append(safe(String.valueOf(self.lastIntelLevel())))
                .append("  conf=").append(self.lastIntelConfidence()).append('\n');
        sb.append("Stale       : ").append(self.intelStale() ? "YES" : "no")
                .append("  (").append(self.intelStalenessScore()).append(")\n");
        sb.append("Scans       : ok=").append(self.successfulRadarScans())
                .append("  fail=").append(self.failedRadarScans())
                .append("  total=").append(self.totalRadarScans()).append('\n');
        if (self.lastScanSummary() != null && !self.lastScanSummary().isEmpty()) {
            sb.append("Last scan   : ").append(self.lastScanSummary()).append('\n');
        }
        sb.append("Best rank   : ").append(self.bestIntelRankAchieved());
        return sb.toString();
    }

    /** Returns a compact summary of incoming threats to this player. */
    public static String formatThreatStatus(MatchDebugSnapshot snapshot, ParticipantId playerId) {
        ParticipantSummary p = pickSelf(snapshot, playerId);
        if (p == null) return "(no threat data)";
        StringBuilder sb = new StringBuilder();
        sb.append("Incoming    : ").append(p.incomingThreatCount()).append('\n');
        sb.append("Impact-ready: ").append(p.impactReadyThreatCount()).append('\n');
        sb.append("Pending wave: ").append(p.pendingImpactWaveCount()).append('\n');
        if (p.firstIncomingThreatId() != null) {
            sb.append("Next threat : warn=")
                    .append(p.firstIncomingThreatWarningPiecesRemaining())
                    .append(" pcs\n");
        }
        sb.append("Selected ICB: ").append(safe(p.selectedInterceptThreatId())).append('\n');
        sb.append("Interceptable: ").append(p.interceptableThreatCount())
                .append("  full=").append(p.fullyInterceptedThreatCount())
                .append("  partial=").append(p.partiallyMitigatedThreatCount());
        return sb.toString();
    }

    /** Returns a compact view of the active action-code attempt. */
    public static String formatActionCodeStatus(MatchDebugSnapshot snapshot, ParticipantId playerId) {
        ParticipantSummary p = pickSelf(snapshot, playerId);
        if (p == null) return "(no action data)";
        StringBuilder sb = new StringBuilder();
        if (p.activeActionId() == null) {
            sb.append("Active      : (none)\n");
            sb.append("             Start a command by clearing the listed line\n");
            sb.append("             counts in order. See Command Guide.\n");
        } else {
            sb.append("Active      : ").append(safe(p.activeActionName())).append('\n');
            sb.append("Progress    : ").append(p.activeActionProgress())
                    .append(" / ").append(p.activeActionRequiredLength()).append('\n');
            if (p.activeActionExpectedNextClear() > 0) {
                sb.append("Next clear  : ").append(p.activeActionExpectedNextClear())
                        .append(" line(s)\n");
            }
        }
        if (p.pendingConfirmationActionId() != null) {
            sb.append("Confirm     : ").append(safe(p.pendingConfirmationActionName()))
                    .append('\n');
            sb.append("             Complete the final 4-line clear (Tetris) to confirm.\n");
        }
        sb.append("Completed   : ").append(p.completedActionCount());
        return sb.toString();
    }

    /**
     * Returns the most urgent threat / launch banner string, or an
     * empty string if there is nothing to flag.
     */
    public static String formatThreatLaunchBanner(MatchDebugSnapshot snapshot, ParticipantId playerId) {
        ParticipantSummary p = pickSelf(snapshot, playerId);
        if (p == null) return "";
        if (p.impactReadyThreatCount() > 0) {
            return "IMPACT READY \u2014 use intercept / civil defense or resolve impact";
        }
        if (p.incomingThreatCount() > 0) {
            return "INCOMING THREAT \u2014 warning pieces remaining: "
                    + p.firstIncomingThreatWarningPiecesRemaining();
        }
        if (p.impactReadyLaunchCount() > 0) {
            return "YOUR IMPACT IS READY";
        }
        if (p.activeLaunchCount() > 0) {
            return "LAUNCH IN FLIGHT";
        }
        return "";
    }

    /** Returns a one-line launch readiness summary. */
    public static String formatLaunchReadiness(MatchDebugSnapshot snapshot, ParticipantId playerId) {
        ParticipantSummary p = pickSelf(snapshot, playerId);
        if (p == null) return "";
        String name = safe(p.currentNukeDisplayName());
        if (p.impactReadyLaunchCount() > 0) return name + ": Impact ready";
        if (p.activeLaunchCount() > 0) return name + ": Launch in flight";
        if (p.pendingConfirmationActionId() != null) {
            return name + ": Launch pending confirmation";
        }
        if (p.armed()) return name + ": Armed \u2014 enter launch code";
        if (p.requiredNukeCharge() > 0) {
            return name + ": Charging " + p.currentNukeCharge() + "/" + p.requiredNukeCharge();
        }
        return name + ": Not enough charge";
    }

    /** Convenience alias for opponent intel. */
    public static String formatRadarIntel(MatchDebugSnapshot snapshot, ParticipantId playerId) {
        return formatOpponentStatus(snapshot, playerId);
    }

    /**
     * Step 18 — returns a "MATCH OVER" banner once the match is over,
     * or an empty string while play continues. The result categorises
     * win / loss / mutual collapse from the player's perspective.
     */
    public static String formatEndStateBanner(MatchDebugSnapshot snapshot, ParticipantId playerId) {
        if (snapshot == null) return "";
        if (snapshot.currentPhase() != com.tetris.mab.MatchPhase.GAME_OVER) return "";
        ParticipantId winner = snapshot.winner();
        if (winner == null) return "MATCH OVER \u2014 Mutual Collapse";
        if (winner == playerId) return "MATCH OVER \u2014 Victory";
        return "MATCH OVER \u2014 Defeat";
    }

    /**
     * Step 18 — short opponent description for the HUD header
     * (archetype + difficulty), or an empty string if either is null.
     */
    public static String formatOpponentLine(com.tetris.mab.ai.MabAiArchetype archetype,
                                            com.tetris.mab.ai.MabAiDifficulty difficulty) {
        if (archetype == null && difficulty == null) return "";
        return "Opponent: " + (archetype == null ? "?" : archetype)
                + " / " + (difficulty == null ? "?" : difficulty);
    }

    /**
     * Returns a list of compact event-feed rows suitable for a
     * monospaced JTextArea. Most-recent first. Only player-facing
     * event types are included.
     */
    public static List<String> formatEventFeed(List<MatchEventLogEntry> events, int maxRows) {
        List<String> out = new ArrayList<>();
        if (events == null) return out;
        int limit = Math.max(1, maxRows);
        // Walk from newest to oldest.
        for (int i = events.size() - 1; i >= 0 && out.size() < limit; i--) {
            MatchEventLogEntry e = events.get(i);
            if (e == null) continue;
            String t = e.eventType();
            if (t == null || t.isEmpty()) continue;
            if (!PLAYER_FACING_EVENTS.contains(t)) continue;
            StringBuilder sb = new StringBuilder();
            sb.append('[').append(e.sequenceNumber()).append("] ").append(t);
            if (e.participantId() != null) sb.append(' ').append(e.participantId());
            String msg = e.message();
            if (msg != null && !msg.isEmpty()) {
                sb.append(" — ");
                if (msg.length() > 60) sb.append(msg, 0, 60).append('…');
                else sb.append(msg);
            }
            out.add(sb.toString());
        }
        return out;
    }

    /** Returns a one-line global header (DEFCON / phase / paused). */
    public static String formatGlobalHeader(MatchDebugSnapshot snapshot) {
        if (snapshot == null) return "(no match)";
        StringBuilder sb = new StringBuilder();
        sb.append("DEFCON ").append(snapshot.defconLevel())
                .append("  ESC=").append(snapshot.escalationMeter())
                .append("  phase=").append(snapshot.currentPhase());
        if (snapshot.paused()) sb.append("  [PAUSED]");
        if (snapshot.winner() != null) sb.append("  WINNER=").append(snapshot.winner());
        return sb.toString();
    }

    // ─────────────────────── Helpers ────────────────────────────

    private static ParticipantSummary pickSelf(MatchDebugSnapshot snapshot, ParticipantId pid) {
        if (snapshot == null) return null;
        if (pid == ParticipantId.PLAYER_B) return snapshot.playerB();
        return snapshot.playerA();
    }

    private static String safe(String s) {
        return (s == null || s.isEmpty()) ? "—" : s;
    }
}
