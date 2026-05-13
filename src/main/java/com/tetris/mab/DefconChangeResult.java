package com.tetris.mab;

/**
 * Result of an escalation event applied to {@link DefconState}.
 * Returned from {@link DefconState#addEscalation(int, String)} so the
 * caller can detect whether DEFCON crossed a threshold and refresh
 * dependent state (e.g. effective nuke build charge) accordingly.
 */
public record DefconChangeResult(
        int previousLevel,
        int currentLevel,
        boolean changed,
        int escalationMeter,
        String reason) {
}
