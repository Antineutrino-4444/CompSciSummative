package com.tetris.mab.launch;

/**
 * Lifecycle status of an {@link com.tetris.mab.IncomingThreatState}.
 *
 * <ul>
 *   <li>{@link #WARNING_ACTIVE} – defender is being warned; flight timer running.</li>
 *   <li>{@link #IMPACT_READY} – warning timer expired; later impact resolver should process it.</li>
 *   <li>{@link #INTERCEPTED} – future state after a successful intercept.</li>
 *   <li>{@link #RESOLVED} – future state after impact resolution.</li>
 *   <li>{@link #CANCELLED} – future state for cancellation/disarm outcomes.</li>
 * </ul>
 */
public enum ThreatStatus {
    WARNING_ACTIVE,
    IMPACT_READY,
    INTERCEPTED,
    RESOLVED,
    CANCELLED
}
