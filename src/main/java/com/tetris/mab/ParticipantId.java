package com.tetris.mab;

/**
 * Identifier for one of the two participants in a Mutually Assured
 * Blocks match. The naming is symmetric — "PLAYER_A" is not necessarily
 * the human in PvE mode; that mapping is an upper-layer concern.
 */
public enum ParticipantId {
    PLAYER_A,
    PLAYER_B;

    /** Returns the other participant id. */
    public ParticipantId opponent() {
        return this == PLAYER_A ? PLAYER_B : PLAYER_A;
    }
}
