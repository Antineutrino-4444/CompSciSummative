package com.tetris.mab.action;

/**
 * Classification of how a piece-lock that produced a line clear was
 * rotated/kicked. NONE means an ordinary, non-spin clear.
 *
 * <p>O-spins are intentionally NOT represented because they are not
 * recognised by the engine as a meaningful spin.
 *
 * <p>Whether a spin acts as a wildcard for an action-code requirement
 * is decided by {@link #isEligibleActionWildcard()}. Step 4 only
 * supports T_SPIN and T_SPIN_MINI as eligible wildcards because the
 * existing engine event {@link com.tetris.events.GameEventListener.LinesClearedEvent}
 * exposes those flags. The other piece-specific spins are kept in the
 * enum so future engine work can flip them on without a new release.
 */
public enum SpinKind {
    NONE,
    T_SPIN,
    T_SPIN_MINI,
    I_SPIN,
    J_SPIN,
    L_SPIN,
    S_SPIN,
    Z_SPIN;

    public boolean isSpin() {
        return this != NONE;
    }

    /**
     * Whether this spin kind is allowed to act as a wildcard token
     * inside an action-code sequence. Only enabled for spins the
     * engine can currently detect reliably.
     */
    public boolean isEligibleActionWildcard() {
        return switch (this) {
            case T_SPIN, T_SPIN_MINI -> true;
            // I/J/L/S/Z spins are part of the enum for forward
            // compatibility but the engine does not flag them yet,
            // so they do not act as wildcards in this step.
            default -> false;
        };
    }
}
