package com.tetris.mab.action;

/**
 * How a completed action-code sequence is confirmed before it counts
 * as a fully completed action.
 *
 * <ul>
 *   <li>{@link #NONE} — completes immediately on sequence completion.</li>
 *   <li>{@link #KEYBOARD_CONFIRM} — sequence completion enters
 *       PENDING_CONFIRMATION and waits for an explicit
 *       {@code confirmActionAttempt(...)} call.</li>
 *   <li>{@link #HARD_FOUR_CONFIRM} — the final token in the sequence
 *       is a hard 4-line clear that itself acts as the confirmation
 *       (spins cannot replace it).</li>
 * </ul>
 */
public enum ActionConfirmationMode {
    NONE,
    KEYBOARD_CONFIRM,
    HARD_FOUR_CONFIRM
}
