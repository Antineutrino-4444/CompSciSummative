package com.tetris.mab;

/**
 * Top-level mode of a Mutually Assured Blocks match. Step 2 only
 * scaffolds the structural distinction; AI behavior is not yet
 * implemented.
 */
public enum MatchMode {
    /** Two human players sharing one machine, each with their own GameState. */
    PVP_LOCAL,
    /** One human vs an AI opponent (AI driver not implemented yet). */
    PVE
}
