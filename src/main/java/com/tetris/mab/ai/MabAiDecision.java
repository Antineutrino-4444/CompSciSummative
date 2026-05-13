package com.tetris.mab.ai;

import com.tetris.mab.ParticipantId;

/**
 * Step 13 — immutable record describing a single decision produced by
 * the PvE AI policy. {@code executed} indicates whether the driver
 * actually carried it out via the match API; {@code message} carries a
 * short human-readable reason.
 */
public record MabAiDecision(
        MabAiDecisionType type,
        ParticipantId participantId,
        String detail,
        boolean executed,
        String message) {

    public MabAiDecision {
        if (type == null) type = MabAiDecisionType.NONE;
        if (detail == null) detail = "";
        if (message == null) message = "";
    }

    public static MabAiDecision none(ParticipantId pid, String why) {
        return new MabAiDecision(MabAiDecisionType.NONE, pid, "", false, why == null ? "" : why);
    }

    public static MabAiDecision executed(MabAiDecisionType type, ParticipantId pid,
                                         String detail, String message) {
        return new MabAiDecision(type, pid, detail, true, message);
    }

    public static MabAiDecision skipped(MabAiDecisionType type, ParticipantId pid,
                                        String detail, String message) {
        return new MabAiDecision(type, pid, detail, false, message);
    }
}
