package com.tetris.mab;

import com.tetris.mab.action.ActionCodeAttempt;
import com.tetris.mab.action.ActionCodeManager;

/**
 * Step-2 placeholder type that now wraps the real
 * {@link com.tetris.mab.action.ActionCodeManager}. Kept as a thin
 * wrapper so {@link ParticipantState}'s public surface does not change.
 */
public class ActionCodeProgressState {

    private final ActionCodeManager manager = new ActionCodeManager();

    public ActionCodeManager getManager() { return manager; }

    public boolean hasActiveAttempt() { return manager.hasActiveAttempt(); }
    public boolean hasPendingConfirmation() { return manager.hasPendingConfirmation(); }

    public ActionCodeAttempt getActiveAttempt() { return manager.getActiveAttempt(); }
    public ActionCodeAttempt getPendingConfirmationAttempt() {
        return manager.getPendingConfirmationAttempt();
    }

    public String toDebugString() { return manager.toDebugString(); }
}
