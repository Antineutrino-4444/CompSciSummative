package com.tetris.mab.ui;

import com.tetris.mab.MatchDebugSnapshot;
import com.tetris.mab.MatchDebugSnapshot.ParticipantSummary;
import com.tetris.mab.MatchEventLogEntry;
import com.tetris.mab.ParticipantId;

import java.util.ArrayList;
import java.util.List;

/**
 * Step 17 — derives a short list of player-facing alerts from a
 * {@link MatchDebugSnapshot} plus a window of recent
 * {@link MatchEventLogEntry}s.
 *
 * <p>Stateless. Never mutates the match.
 */
public final class MabAlertModel {

    /**
     * Returns the most recent player-facing alerts, newest first.
     * Caller decides how many to show.
     */
    public List<MabAlert> buildAlerts(MatchDebugSnapshot snapshot,
                                      List<MatchEventLogEntry> recentEvents,
                                      ParticipantId playerId) {
        List<MabAlert> out = new ArrayList<>();
        if (snapshot == null) return out;
        ParticipantId pid = (playerId == null) ? ParticipantId.PLAYER_A : playerId;

        // 1) State-derived alerts (no event sequence — use 0).
        ParticipantSummary me = (pid == ParticipantId.PLAYER_A)
                ? snapshot.playerA() : snapshot.playerB();
        if (me != null) {
            if (me.impactReadyThreatCount() > 0) {
                out.add(new MabAlert(0, MabAlertSeverity.CRITICAL,
                        "IMPACT PENDING",
                        "Inbound impact is about to resolve. SPIN TO INTERCEPT, "
                                + "or civil defense, or it lands next tick."));
            } else if (me.incomingThreatCount() > 0) {
                out.add(new MabAlert(0, MabAlertSeverity.CRITICAL,
                        "INCOMING IMPACT",
                        "Impacts inbound: " + me.incomingThreatCount()
                                + ". IMPACT IN "
                                + me.firstIncomingThreatWarningPiecesRemaining()
                                + " PIECES."));
            }
            if (me.impactReadyLaunchCount() > 0) {
                out.add(new MabAlert(0, MabAlertSeverity.WARNING,
                        "Your impact ready",
                        "Your launch is ready to impact the opponent."));
            } else if (me.activeLaunchCount() > 0) {
                out.add(new MabAlert(0, MabAlertSeverity.INFO,
                        "Launch in flight",
                        "Active launches: " + me.activeLaunchCount() + "."));
            }
            if (me.armed()) {
                out.add(new MabAlert(0, MabAlertSeverity.INFO,
                        "Nuke armed",
                        me.currentNukeDisplayName() + " is armed and ready to launch."));
            }
            if (me.upgradePoints() > 0
                    && snapshot.currentPhase() != com.tetris.mab.MatchPhase.UPGRADE_PAUSE) {
                out.add(new MabAlert(0, MabAlertSeverity.INFO,
                        "Upgrade points available",
                        "You have " + me.upgradePoints() + " upgrade point(s). "
                                + "Open Upgrades when safe to spend them."));
            }
            // Legacy intel-staleness alert removed — the MAB design has
            // no radar / intel UI, so there is nothing for the player to
            // refresh.
            if (me.pendingConfirmationActionId() != null) {
                out.add(new MabAlert(0, MabAlertSeverity.WARNING,
                        "Confirmation pending",
                        "Confirm: " + me.pendingConfirmationActionName()
                                + " — complete the final 4-line clear (Tetris)."));
            }
        }

        // 2) Event-derived alerts.
        if (recentEvents != null) {
            for (int i = recentEvents.size() - 1; i >= 0; i--) {
                MatchEventLogEntry e = recentEvents.get(i);
                MabAlert a = mapEvent(e, pid);
                if (a != null) out.add(a);
            }
        }
        return out;
    }

    private MabAlert mapEvent(MatchEventLogEntry e, ParticipantId pid) {
        String t = e.eventType();
        boolean own = e.participantId() == pid;
        boolean opponent = e.participantId() != null && e.participantId() != pid;
        switch (t) {
            case "DEFCON_CHANGED":
                return new MabAlert(e.sequenceNumber(), MabAlertSeverity.WARNING,
                        "DEFCON changed", e.message());
            case "LAUNCH_AUTHORIZED":
                if (opponent) return new MabAlert(e.sequenceNumber(),
                        MabAlertSeverity.WARNING,
                        "Opponent launch authorized", e.message());
                return new MabAlert(e.sequenceNumber(), MabAlertSeverity.SUCCESS,
                        "Your launch authorized", e.message());
            case "INCOMING_THREAT_CREATED":
                return new MabAlert(e.sequenceNumber(), MabAlertSeverity.CRITICAL,
                        "INCOMING IMPACT", e.message());
            case "IMPACT_READY":
                return new MabAlert(e.sequenceNumber(), MabAlertSeverity.CRITICAL,
                        "IMPACT PENDING", e.message());
            case "IMPACT_RESOLVED":
                return new MabAlert(e.sequenceNumber(),
                        own ? MabAlertSeverity.WARNING : MabAlertSeverity.SUCCESS,
                        own ? "You took an impact" : "Your strike impacted",
                        e.message());
            case "CIVIL_DEFENSE_ACTIVATED":
            case "CIVIL_DEFENSE_MITIGATION_APPLIED":
                return new MabAlert(e.sequenceNumber(), MabAlertSeverity.SUCCESS,
                        "Civil defense", e.message());
            case "INTERCEPT_RESOLVED":
            case "THREAT_FULLY_INTERCEPTED":
                return new MabAlert(e.sequenceNumber(), MabAlertSeverity.SUCCESS,
                        "Intercept resolved", e.message());
            case "INTERCEPT_FAILED":
            case "THREAT_PARTIALLY_INTERCEPTED":
                return new MabAlert(e.sequenceNumber(), MabAlertSeverity.WARNING,
                        "Intercept partial / failed", e.message());
            case "RADAR_SCAN_COMPLETED":
            case "RADAR_SCAN_REJECTED":
            case "DECOY_ACTIVATED":
                // Legacy radar / decoy events are no longer surfaced to
                // the player. The MAB design intentionally has no radar,
                // intel, or decoy player-facing UI.
                return null;
            case "UPGRADE_APPLIED":
                return new MabAlert(e.sequenceNumber(), MabAlertSeverity.SUCCESS,
                        "Upgrade applied", e.message());
            case "NUKE_REDESIGNED":
                return new MabAlert(e.sequenceNumber(), MabAlertSeverity.INFO,
                        "Nuke redesigned", e.message());
            case "ACTION_COMPLETED":
            case "ACTION_CONFIRMED":
                return new MabAlert(e.sequenceNumber(), MabAlertSeverity.SUCCESS,
                        "Action completed", e.message());
            case "ACTION_PENDING_CONFIRMATION":
                return new MabAlert(e.sequenceNumber(), MabAlertSeverity.WARNING,
                        "Confirmation pending", e.message());
            case "ACTION_FAILED_RESET":
            case "ACTION_CONFIRM_FAILED":
                return new MabAlert(e.sequenceNumber(), MabAlertSeverity.WARNING,
                        "Action failed", e.message());
            case "ACTION_START_REJECTED_NOT_ARMED":
            case "ACTION_START_REJECTED_NO_THREAT":
            case "ACTION_START_REJECTED_NO_ACTIVE_THREAT":
            case "LAUNCH_AUTHORIZATION_REJECTED_NOT_ARMED":
            case "LAUNCH_AUTHORIZATION_REJECTED_PHASE":
                return new MabAlert(e.sequenceNumber(), MabAlertSeverity.WARNING,
                        "Action rejected", e.message());
            default:
                return null;
        }
    }
}
