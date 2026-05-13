package com.tetris.mab.ai;

/** Step 13 — difficulty rung for the offline PvE AI driver. */
public enum MabAiDifficulty {
    /** Slower charge, less frequent decisions, weaker defensive timing. */
    EASY,
    /** Moderate. */
    NORMAL,
    /** Faster charge, more frequent scans/defence. */
    HARD,
    /** Acts quickly for visible testing. */
    DEBUG
}
