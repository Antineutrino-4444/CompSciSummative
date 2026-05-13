package com.tetris.mab.launch;

/**
 * Lifecycle phase of an {@link com.tetris.mab.ActiveLaunchState}.
 *
 * <ul>
 *   <li>{@link #AUTHORIZED} – launch was accepted by the action-code system.</li>
 *   <li>{@link #COUNTDOWN} – attacker-side launch timer is running.</li>
 *   <li>{@link #IN_FLIGHT} – defender warning/flight timer is running.</li>
 *   <li>{@link #IMPACT_READY} – timer completed; later impact resolver should process it.</li>
 *   <li>{@link #RESOLVED} – future state after impact resolution.</li>
 *   <li>{@link #CANCELLED} – future state for intercept/cancel/disarm outcomes.</li>
 * </ul>
 */
public enum LaunchPhase {
    AUTHORIZED,
    COUNTDOWN,
    IN_FLIGHT,
    IMPACT_READY,
    RESOLVED,
    CANCELLED
}
