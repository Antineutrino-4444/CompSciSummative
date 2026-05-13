package com.tetris.mab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Owns and advances a collection of {@link PieceCountdownTimer}s.
 *
 * <p>Strictly piece-driven — real-time clocks are intentionally not used
 * so that pausing the engine, switching tabs, or different gravity speeds
 * between participants can never cause a strategic-layer desync.
 */
public class PieceTimerManager {

    private final List<PieceCountdownTimer> timers = new ArrayList<>();
    private final List<PieceCountdownTimer> completedHistory = new ArrayList<>();

    /** Adds a new timer. Newly-added zero-piece timers are immediately completed. */
    public void addTimer(PieceCountdownTimer timer) {
        if (timer == null) return;
        timers.add(timer);
        if (timer.isCompleted()) completedHistory.add(timer);
    }

    /**
     * Cancels (force-completes and removes) the timer with the given id.
     * The cancelled timer is NOT added to the completed-history queue.
     */
    public boolean cancelTimer(String id) {
        if (id == null) return false;
        Iterator<PieceCountdownTimer> it = timers.iterator();
        while (it.hasNext()) {
            PieceCountdownTimer t = it.next();
            if (id.equals(t.getId())) {
                t.forceComplete();
                it.remove();
                return true;
            }
        }
        return false;
    }

    /**
     * Notifies the manager that {@code whoLocked} just locked a piece.
     * Each registered timer that opted into this participant's locks
     * decrements by one. Newly-completed timers are stashed for retrieval
     * via {@link #consumeCompletedTimers()}.
     */
    public void advanceForPieceLocked(ParticipantId whoLocked) {
        if (whoLocked == null || timers.isEmpty()) return;
        Iterator<PieceCountdownTimer> it = timers.iterator();
        while (it.hasNext()) {
            PieceCountdownTimer t = it.next();
            if (t.shouldAdvanceFor(whoLocked)) {
                t.tick();
                if (t.isCompleted()) {
                    completedHistory.add(t);
                    it.remove();
                }
            }
        }
    }

    /** Read-only view of the timers still in flight. */
    public List<PieceCountdownTimer> getActiveTimers() {
        return Collections.unmodifiableList(timers);
    }

    /**
     * Returns the timers that have completed since the previous call and
     * clears the internal completed queue.
     */
    public List<PieceCountdownTimer> consumeCompletedTimers() {
        if (completedHistory.isEmpty()) return Collections.emptyList();
        List<PieceCountdownTimer> out = new ArrayList<>(completedHistory);
        completedHistory.clear();
        return out;
    }

    /** Removes every timer (active and pending-completed). */
    public void clear() {
        timers.clear();
        completedHistory.clear();
    }
}
