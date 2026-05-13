package com.tetris.mab.intercept;

/** Intercept tier — defines speed vs strength tradeoff. */
public enum InterceptType {
    /** Short code, fast but weaker. */
    EMERGENCY,
    /** Balanced intercept. */
    STANDARD,
    /** Longer strategic intercept, strongest. */
    FULL
}
