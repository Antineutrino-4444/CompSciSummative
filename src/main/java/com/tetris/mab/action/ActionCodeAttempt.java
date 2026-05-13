package com.tetris.mab.action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Live state of one player's attempt at a single {@link ActionCodeDefinition}. */
public class ActionCodeAttempt {

    private final ActionCodeDefinition definition;
    private final List<ActionClearToken> completedTokens = new ArrayList<>();
    private int progressIndex;
    private boolean active = true;
    private boolean sequenceComplete;
    private boolean pendingConfirmation;
    private boolean confirmed;
    private boolean completed;
    private boolean failed;
    private final int startedAtPieceCount;
    private int lastAdvancedAtPieceCount;
    private String failureReason = "";

    public ActionCodeAttempt(ActionCodeDefinition definition, int startedAtPieceCount) {
        if (definition == null) throw new IllegalArgumentException("definition");
        this.definition = definition;
        this.startedAtPieceCount = startedAtPieceCount;
        this.lastAdvancedAtPieceCount = startedAtPieceCount;
    }

    // ─────────────────────── Accessors ───────────────────────

    public ActionCodeDefinition getDefinition() { return definition; }
    public List<ActionClearToken> getCompletedTokens() { return Collections.unmodifiableList(completedTokens); }
    public int getProgressIndex() { return progressIndex; }
    public boolean isActive() { return active; }
    public boolean isSequenceComplete() { return sequenceComplete; }
    public boolean isPendingConfirmation() { return pendingConfirmation; }
    public boolean isConfirmed() { return confirmed; }
    public boolean isCompleted() { return completed; }
    public boolean isFailed() { return failed; }
    public int getStartedAtPieceCount() { return startedAtPieceCount; }
    public int getLastAdvancedAtPieceCount() { return lastAdvancedAtPieceCount; }
    public String getFailureReason() { return failureReason; }

    /** The next required token, or {@code null} if the sequence is finished. */
    public ActionCodeTokenRequirement getExpectedNextRequirement() {
        if (progressIndex >= definition.sequenceLength()) return null;
        return definition.getSequence().get(progressIndex);
    }

    // ─────────────────────── Mutation (package-private) ──────

    void advance(ActionClearToken token, int pieceCount) {
        completedTokens.add(token);
        progressIndex++;
        lastAdvancedAtPieceCount = pieceCount;
    }

    void markSequenceComplete(int pieceCount) {
        sequenceComplete = true;
        active = false;
        lastAdvancedAtPieceCount = pieceCount;
    }

    void markPendingConfirmation() {
        pendingConfirmation = true;
    }

    void confirm() {
        confirmed = true;
        pendingConfirmation = false;
    }

    void complete() {
        completed = true;
        active = false;
    }

    void fail(String reason) {
        failed = true;
        active = false;
        pendingConfirmation = false;
        failureReason = reason == null ? "" : reason;
    }

    void cancel(String reason) {
        active = false;
        pendingConfirmation = false;
        failed = true;
        failureReason = reason == null ? "cancelled" : reason;
    }

    public String toDebugString() {
        StringBuilder sb = new StringBuilder();
        sb.append(definition.getId());
        sb.append(' ').append(progressIndex).append('/').append(definition.sequenceLength());
        if (pendingConfirmation) sb.append(" PENDING");
        if (confirmed) sb.append(" CONFIRMED");
        if (completed) sb.append(" COMPLETED");
        if (failed) sb.append(" FAILED");
        if (!failureReason.isEmpty()) sb.append('(').append(failureReason).append(')');
        return sb.toString();
    }
}
