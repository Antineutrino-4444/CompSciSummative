package com.tetris.mab.action;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Per-participant action-code state machine. Owns at most one active
 * attempt and at most one pending-confirmation attempt at a time, plus
 * a bounded ring of recently completed/failed attempts.
 */
public class ActionCodeManager {

    private static final int DEFAULT_MAX_HISTORY = 20;

    private ActionCodeAttempt activeAttempt;
    private ActionCodeAttempt pendingConfirmationAttempt;
    private final Deque<ActionCodeAttempt> completedAttempts = new ArrayDeque<>();
    private final int maxCompletedHistory;

    public ActionCodeManager() {
        this(DEFAULT_MAX_HISTORY);
    }

    public ActionCodeManager(int maxCompletedHistory) {
        this.maxCompletedHistory = Math.max(1, maxCompletedHistory);
    }

    // ─────────────────────── Accessors ───────────────────────

    public boolean hasActiveAttempt() { return activeAttempt != null; }
    public boolean hasPendingConfirmation() { return pendingConfirmationAttempt != null; }
    public ActionCodeAttempt getActiveAttempt() { return activeAttempt; }
    public ActionCodeAttempt getPendingConfirmationAttempt() { return pendingConfirmationAttempt; }

    public List<ActionCodeAttempt> getCompletedAttempts() {
        return Collections.unmodifiableList(new ArrayList<>(completedAttempts));
    }

    // ─────────────────────── Mutation ────────────────────────

    /**
     * Begin a new attempt. Cancels any existing active attempt and any
     * pending-confirmation attempt.
     */
    public void startAttempt(ActionCodeDefinition definition, int currentPieceCount) {
        if (definition == null) throw new IllegalArgumentException("definition");
        if (activeAttempt != null) {
            activeAttempt.cancel("replaced");
            archive(activeAttempt);
            activeAttempt = null;
        }
        if (pendingConfirmationAttempt != null) {
            pendingConfirmationAttempt.cancel("replaced");
            archive(pendingConfirmationAttempt);
            pendingConfirmationAttempt = null;
        }
        activeAttempt = new ActionCodeAttempt(definition, currentPieceCount);
    }

    /** Cancel the active attempt and any pending confirmation. */
    public ActionCodeResult cancelAttempt(String reason) {
        boolean any = false;
        if (activeAttempt != null) {
            activeAttempt.cancel(reason);
            archive(activeAttempt);
            activeAttempt = null;
            any = true;
        }
        if (pendingConfirmationAttempt != null) {
            pendingConfirmationAttempt.cancel(reason);
            archive(pendingConfirmationAttempt);
            pendingConfirmationAttempt = null;
            any = true;
        }
        return any ? ActionCodeResult.CANCELLED : ActionCodeResult.NOT_ACTIVE;
    }

    /** Feed a line-clear token into the currently active attempt. */
    public ActionCodeResult processLineClear(ActionClearToken token,
                                             ActionCodeMatchMode matchMode,
                                             int currentPieceCount) {
        if (activeAttempt == null) return ActionCodeResult.NOT_ACTIVE;
        if (token == null || token.lineCount() <= 0) return ActionCodeResult.IGNORED;

        ActionCodeDefinition def = activeAttempt.getDefinition();
        ActionCodeTokenRequirement next = activeAttempt.getExpectedNextRequirement();
        if (next == null) {
            // Defensive: should not happen because completed attempts are
            // moved out of activeAttempt immediately.
            return ActionCodeResult.NOT_ACTIVE;
        }

        boolean ok = ActionCodeMatcher.matches(token, next, matchMode, def.isStrategicAction());
        if (!ok) {
            activeAttempt.fail("token mismatch: got " + token.toDebugString()
                    + " expected " + next.toDebugString());
            archive(activeAttempt);
            activeAttempt = null;
            return ActionCodeResult.FAILED_RESET;
        }

        activeAttempt.advance(token, currentPieceCount);

        if (activeAttempt.getProgressIndex() < def.sequenceLength()) {
            return ActionCodeResult.ADVANCED;
        }

        // Sequence complete.
        activeAttempt.markSequenceComplete(currentPieceCount);
        ActionCodeAttempt finished = activeAttempt;
        activeAttempt = null;

        return switch (def.getConfirmationMode()) {
            case NONE -> {
                finished.confirm();
                finished.complete();
                archive(finished);
                yield ActionCodeResult.COMPLETED;
            }
            case HARD_FOUR_CONFIRM -> {
                // Final hard 4 was the confirmation token itself.
                finished.confirm();
                finished.complete();
                archive(finished);
                yield ActionCodeResult.COMPLETED;
            }
            case KEYBOARD_CONFIRM -> {
                finished.markPendingConfirmation();
                pendingConfirmationAttempt = finished;
                yield ActionCodeResult.PENDING_CONFIRMATION;
            }
        };
    }

    /** Confirm the pending-confirmation attempt, if any. */
    public ActionCodeResult confirmPending() {
        if (pendingConfirmationAttempt == null) return ActionCodeResult.NOT_ACTIVE;
        ActionCodeAttempt a = pendingConfirmationAttempt;
        a.confirm();
        a.complete();
        archive(a);
        pendingConfirmationAttempt = null;
        return ActionCodeResult.COMPLETED;
    }

    /** Pop the most recently completed attempt, leaving older ones in history. */
    public ActionCodeAttempt consumeMostRecentCompleted() {
        return completedAttempts.pollLast();
    }

    public void clearCompletedHistory() {
        completedAttempts.clear();
    }

    private void archive(ActionCodeAttempt a) {
        completedAttempts.addLast(a);
        while (completedAttempts.size() > maxCompletedHistory) {
            completedAttempts.pollFirst();
        }
    }

    public String toDebugString() {
        StringBuilder sb = new StringBuilder("ActionMgr{");
        if (activeAttempt != null) {
            sb.append("active=").append(activeAttempt.toDebugString());
        } else {
            sb.append("active=none");
        }
        if (pendingConfirmationAttempt != null) {
            sb.append(" pending=").append(pendingConfirmationAttempt.toDebugString());
        }
        sb.append(" history=").append(completedAttempts.size()).append('}');
        return sb.toString();
    }
}
