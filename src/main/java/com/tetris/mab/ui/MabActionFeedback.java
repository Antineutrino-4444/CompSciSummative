package com.tetris.mab.ui;

import com.tetris.mab.MatchEventLogEntry;
import com.tetris.mab.ParticipantId;

import java.awt.Color;
import java.util.List;
import java.util.Objects;

/**
 * Step 20 Third Refinement — derives the most recent player-facing
 * action-code feedback message from the match event log.
 *
 * <p>Stateless helper used by both the compact dashboard
 * ({@link MabCompactHudPanel}) and the top banner inside
 * {@link MabPveGamePanel}. Both surfaces render the same line so the
 * player gets consistent feedback regardless of where they are
 * looking when an event fires.
 *
 * <p><b>Offline-only.</b> No networking; no shared mutable state.
 */
public final class MabActionFeedback {

    /** Severity used to colour the resulting notification. */
    public enum Severity { INFO, SUCCESS, WARNING, CRITICAL, NONE }

    /** Read-only result tuple. */
    public static final class Message {
        public static final Message EMPTY = new Message(Severity.NONE, "", -1L);
        public final Severity severity;
        public final String text;
        public final long sequenceNumber;
        Message(Severity s, String t, long seq) {
            this.severity = s; this.text = t; this.sequenceNumber = seq;
        }
        public boolean isEmpty() { return severity == Severity.NONE || text.isEmpty(); }
    }

    private MabActionFeedback() {}

    /**
     * Walks {@code recentEvents} newest-first and returns the first
     * action-code-related event that targets {@code playerId} (or has
     * a null participant), formatted as a short tagged sentence.
     */
    public static Message latest(List<MatchEventLogEntry> recentEvents,
                                 ParticipantId playerId) {
        if (recentEvents == null || recentEvents.isEmpty()) return Message.EMPTY;
        ParticipantId pid = (playerId == null) ? ParticipantId.PLAYER_A : playerId;
        for (int i = recentEvents.size() - 1; i >= 0; i--) {
            MatchEventLogEntry e = recentEvents.get(i);
            if (e == null) continue;
            ParticipantId ep = e.participantId();
            if (ep != null && ep != pid) continue; // ignore opponent-only events
            Message m = mapEvent(e);
            if (m != null) return m;
        }
        return Message.EMPTY;
    }

    private static Message mapEvent(MatchEventLogEntry e) {
        String t = e.eventType();
        if (t == null) return null;
        long seq = e.sequenceNumber();
        switch (t) {
            case "INPUT_LINE_CLEAR": {
                Object cnt = e.metadata() == null ? null : e.metadata().get("count");
                int n = (cnt instanceof Number) ? ((Number) cnt).intValue() : 0;
                return new Message(Severity.INFO,
                        "INPUT: " + (n > 0 ? n + "-line" : "line")
                                + " clear received", seq);
            }
            case "ACTION_AUTO_STARTED":
                return new Message(Severity.SUCCESS,
                        "INPUT: clear accepted — started " + actionLabel(e), seq);
            case "ACTION_NO_MATCH": {
                Object cnt = e.metadata() == null ? null : e.metadata().get("count");
                int n = (cnt instanceof Number) ? ((Number) cnt).intValue() : 0;
                return new Message(Severity.WARNING,
                        "INPUT: " + (n > 0 ? n + "-line" : "line")
                                + " clear did not match any command", seq);
            }
            case "ACTION_STARTED":
                return new Message(Severity.SUCCESS,
                        "[OK] Command started: " + actionLabel(e), seq);
            case "ACTION_ADVANCED":
                return new Message(Severity.INFO,
                        "[OK] Progress: " + Objects.toString(e.message(), "advanced"),
                        seq);
            case "ACTION_PENDING_CONFIRMATION":
                return new Message(Severity.CRITICAL,
                        "[CRIT] Confirmation pending — clear a 4-line (Tetris) to confirm "
                                + actionLabel(e), seq);
            case "ACTION_CONFIRMED":
                return new Message(Severity.SUCCESS,
                        "[OK] Command confirmed: " + actionLabel(e), seq);
            case "ACTION_COMPLETED":
                return new Message(Severity.SUCCESS,
                        "[OK] Command complete: " + actionLabel(e), seq);
            case "ACTION_CANCELLED":
                return new Message(Severity.WARNING,
                        "[WARN] Command cancelled: " + actionLabel(e), seq);
            case "ACTION_FAILED_RESET":
                return new Message(Severity.WARNING,
                        "[WARN] Invalid sequence — command reset", seq);
            case "ACTION_CONFIRM_FAILED":
                return new Message(Severity.WARNING,
                        "[WARN] Confirmation failed — last clear was not a Tetris", seq);
            case "ACTION_START_REJECTED_NOT_ARMED":
                return new Message(Severity.WARNING,
                        "[WARN] Command rejected — nuke not armed", seq);
            case "ACTION_START_REJECTED_NO_THREAT":
                return new Message(Severity.WARNING,
                        "[WARN] Command rejected — no threat to act on", seq);
            case "ACTION_START_REJECTED_NO_ACTIVE_THREAT":
                return new Message(Severity.WARNING,
                        "[WARN] Command rejected — no active incoming threat", seq);
            case "ACTION_START_IGNORED":
            case "ACTION_CONFIRM_IGNORED":
            case "ACTION_CONFIRM_IGNORED_NONE_PENDING":
            case "ACTION_CANCEL_IGNORED_NONE_ACTIVE":
                return new Message(Severity.INFO,
                        "[INFO] " + Objects.toString(e.message(), t), seq);
            case "LAUNCH_AUTHORIZATION_REJECTED_NOT_ARMED":
                return new Message(Severity.WARNING,
                        "[WARN] Launch rejected — nuke not armed", seq);
            case "LAUNCH_AUTHORIZATION_REJECTED_PHASE":
                return new Message(Severity.WARNING,
                        "[WARN] Launch rejected — wrong phase", seq);
            // ── Step 21 simplified MAB events ──
            case "MAB_CLEAR_CHARGE_GAINED": {
                Object g = e.metadata() == null ? null : e.metadata().get("chargeGained");
                Object c = e.metadata() == null ? null : e.metadata().get("clear");
                int gain = (g instanceof Number) ? ((Number) g).intValue() : 0;
                String label = (c == null) ? "clear" : c.toString();
                return new Message(Severity.INFO,
                        "[INFO] " + label + " (+" + gain + " charge)", seq);
            }
            case "MAB_NUKE_READY":
                return new Message(Severity.SUCCESS,
                        "[OK] NUKE READY — fire 4 Tetrises OR 2 spins to launch", seq);
            case "MAB_LAUNCH_PROGRESS_TETRIS":
                return new Message(Severity.INFO,
                        "[INFO] Launch route: " + Objects.toString(e.message(), "Tetris"),
                        seq);
            case "MAB_LAUNCH_PROGRESS_SPIN":
                return new Message(Severity.INFO,
                        "[INFO] Launch route: " + Objects.toString(e.message(), "Spin"),
                        seq);
            case "MAB_LAUNCH_FIRED_SIMPLIFIED":
                return new Message(Severity.SUCCESS,
                        "[OK] " + Objects.toString(e.message(), "LAUNCH FIRED"), seq);
            case "MAB_SPIN_INTERCEPT_TRIGGERED":
                return new Message(Severity.WARNING,
                        "[WARN] " + Objects.toString(e.message(), "Spin intercept"), seq);
            case "MAB_INTERCEPT_RESOLVED_SIMPLIFIED":
                return new Message(Severity.SUCCESS,
                        "[OK] " + Objects.toString(e.message(), "Intercept resolved"), seq);
            default:
                return null;
        }
    }

    private static String actionLabel(MatchEventLogEntry e) {
        Object name = e.metadata() == null ? null : e.metadata().get("actionName");
        if (name != null && !name.toString().isBlank()) return name.toString();
        Object id = e.metadata() == null ? null : e.metadata().get("actionId");
        if (id != null && !id.toString().isBlank()) return id.toString();
        String msg = e.message();
        return (msg == null || msg.isBlank()) ? "(action)" : msg;
    }

    /** Severity → theme colour. */
    public static Color colorOf(Severity s) {
        if (s == null) return MabUiTheme.TEXT_MUTED;
        switch (s) {
            case CRITICAL: return MabUiTheme.CRITICAL;
            case WARNING:  return MabUiTheme.WARNING;
            case SUCCESS:  return MabUiTheme.SUCCESS;
            case INFO:     return MabUiTheme.INFO;
            default:       return MabUiTheme.TEXT_MUTED;
        }
    }
}
