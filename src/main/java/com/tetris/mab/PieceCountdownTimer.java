package com.tetris.mab;

/**
 * Piece-count timer (not real-time). Counts down by one each time a
 * matching piece-lock event arrives (see {@link TimerAdvanceMode}).
 * Step 2 only scaffolds the timer mechanic; it has no payload of its
 * own — consumers inspect {@code id} / {@code reason} to decide what to
 * do when {@link #isCompleted()} flips to {@code true}.
 */
public class PieceCountdownTimer {

    private final String id;
    private final ParticipantId owner;
    private final TimerAdvanceMode advanceMode;
    private int remainingPieces;
    private boolean completed;
    private String reason;

    public PieceCountdownTimer(String id, ParticipantId owner, TimerAdvanceMode advanceMode,
                               int remainingPieces, String reason) {
        this.id = id;
        this.owner = owner;
        this.advanceMode = advanceMode;
        this.remainingPieces = Math.max(0, remainingPieces);
        this.reason = reason == null ? "" : reason;
        this.completed = this.remainingPieces == 0;
    }

    public String getId() { return id; }
    public ParticipantId getOwner() { return owner; }
    public TimerAdvanceMode getAdvanceMode() { return advanceMode; }
    public int getRemainingPieces() { return remainingPieces; }
    public boolean isCompleted() { return completed; }
    public String getReason() { return reason; }

    /**
     * Returns {@code true} if a piece lock by {@code whoLocked} should
     * cause this timer to count down by one.
     */
    public boolean shouldAdvanceFor(ParticipantId whoLocked) {
        if (completed || whoLocked == null) return false;
        return switch (advanceMode) {
            case OWNER_PIECES         -> whoLocked == owner;
            case OPPONENT_PIECES      -> whoLocked != owner;
            case EITHER_PLAYER_PIECES -> true;
            case PLAYER_A_PIECES      -> whoLocked == ParticipantId.PLAYER_A;
            case PLAYER_B_PIECES      -> whoLocked == ParticipantId.PLAYER_B;
        };
    }

    /** Decrements the remaining-piece count by one and sets completion flag. */
    void tick() {
        if (completed) return;
        if (remainingPieces > 0) remainingPieces--;
        if (remainingPieces == 0) completed = true;
    }

    /** Force-completes the timer (used when cancelled via the manager). */
    void forceComplete() {
        remainingPieces = 0;
        completed = true;
    }

    @Override
    public String toString() {
        return "Timer{" + id + " owner=" + owner + " mode=" + advanceMode
                + " left=" + remainingPieces + (completed ? " DONE" : "")
                + (reason.isEmpty() ? "" : " reason=" + reason) + "}";
    }
}
