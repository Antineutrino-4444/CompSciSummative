package com.tetris.mab.launch;

/**
 * Lifecycle status of an {@link com.tetris.mab.IncomingThreatState}.
 *
 * <ul>
 *   <li>{@link #WARNING_ACTIVE} – internal lifecycle name; the threat is
 *       in flight with intercept window open. The player-facing label is
 *       "IN FLIGHT" / "IMPACT PENDING", never "warning active" — see
 *       {@link #playerFacingLabel()}.</li>
 *   <li>{@link #IMPACT_READY} – in-flight timer expired; the impact
 *       resolver will process this threat.</li>
 *   <li>{@link #INTERCEPTED} – after a successful spin intercept.</li>
 *   <li>{@link #RESOLVED} – after impact resolution.</li>
 *   <li>{@link #CANCELLED} – cancellation / disarm outcomes.</li>
 * </ul>
 */
public enum ThreatStatus {
    WARNING_ACTIVE,
    IMPACT_READY,
    INTERCEPTED,
    RESOLVED,
    CANCELLED;

    /**
     * Player-facing label. The MAB design intentionally does not expose
     * a "warning system" / "radar" concept — the defender still sees
     * inbound impacts because the game needs to be readable, but the
     * labels are framed as IN FLIGHT / IMPACT PENDING / SPIN TO
     * INTERCEPT, never as warning / radar / detection.
     */
    public String playerFacingLabel() {
        return switch (this) {
            case WARNING_ACTIVE -> "IN FLIGHT";
            case IMPACT_READY   -> "IMPACT PENDING";
            case INTERCEPTED    -> "INTERCEPT SUCCESS";
            case RESOLVED       -> "IMPACT RESOLVED";
            case CANCELLED      -> "LAUNCH CANCELLED";
        };
    }
}
