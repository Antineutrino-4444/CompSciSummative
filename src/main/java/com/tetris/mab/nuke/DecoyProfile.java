package com.tetris.mab.nuke;

/**
 * Decoy/feint profile: produces fake launches, false doctrine
 * fingerprints, or dummy silo heat to mislead the opponent. Schema
 * only.
 */
public record DecoyProfile(
        boolean createsFakeLaunch,
        boolean createsFalseDoctrine,
        boolean createsDummySiloHeat,
        int decoyDurationPieces) {
}
