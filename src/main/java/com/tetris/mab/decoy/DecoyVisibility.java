package com.tetris.mab.decoy;

/** How visible an active decoy currently is to scans. Step 11. */
public enum DecoyVisibility {
    HIDDEN,
    SUSPECTED,
    VISIBLE_AS_REAL,
    IDENTIFIED_AS_DECOY,
    EXPIRED
}
