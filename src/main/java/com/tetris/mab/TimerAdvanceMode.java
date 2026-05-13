package com.tetris.mab;

/**
 * How a {@link PieceCountdownTimer} consumes piece-lock events.
 *
 * <p>Mutually Assured Blocks uses piece-count timers (not real-time
 * timers) so that pausing the game and the speed difference between
 * participants don't desync the strategic layer.
 */
public enum TimerAdvanceMode {
    /** Advances when the timer's owner locks a piece. */
    OWNER_PIECES,
    /** Advances when the owner's opponent locks a piece. */
    OPPONENT_PIECES,
    /** Advances when either player locks a piece. */
    EITHER_PLAYER_PIECES,
    /** Advances only when {@link ParticipantId#PLAYER_A} locks a piece. */
    PLAYER_A_PIECES,
    /** Advances only when {@link ParticipantId#PLAYER_B} locks a piece. */
    PLAYER_B_PIECES
}
