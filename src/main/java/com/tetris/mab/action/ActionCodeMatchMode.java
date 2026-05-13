package com.tetris.mab.action;

/**
 * How leniently {@link ActionCodeMatcher} treats ordinary line-clear
 * tokens. Spin wildcards still apply in every mode unless the required
 * token has {@code spinMayReplace == false}.
 */
public enum ActionCodeMatchMode {
    /** Any clear ≥ required satisfies the requirement. */
    FLEXIBLE,
    /** Strategic actions require exact match; non-strategic actions are flexible. */
    MOSTLY_FLEXIBLE,
    /** Ordinary clears must match the required count exactly. */
    STRICT
}
