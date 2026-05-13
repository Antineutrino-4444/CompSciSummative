package com.tetris.mab.upgrade;

/** All concrete upgrade types available in the upgrade registry. */
public enum UpgradeType {
    // Silo systems
    HARDENED_SILO,
    DEEP_BUNKER,
    DISTRIBUTED_STOCKPILE,
    RAPID_ASSEMBLY_LINE,
    SECURE_LAUNCH_CHAIN,
    BLAST_DOORS,
    SILO_CAMOUFLAGE,

    // Defense
    SHELTERS,
    GARBAGE_CONTROL,
    EMERGENCY_PROTOCOLS,
    INTERCEPT_CREWS,

    // Launch systems
    RAPID_LAUNCH_DRILLS,
    SECURE_AUTHORIZATION,
    COUNTDOWN_AUTOMATION,

    // Warning / radar
    EARLY_WARNING_RADAR,
    SIGNAL_ANALYSIS,
    THREAT_TRACKING,

    // Tempo
    BUILD_EFFICIENCY,
    LINE_CLEAR_LOGISTICS,

    // Nuke design
    WARHEAD_REFINEMENT,
    CLEANER_FUSION,
    DIRTY_PAYLOAD_ENGINEERING,
    PENETRATION_PACKAGE,

    // MAD systems
    SECOND_STRIKE_DOCTRINE_I,
    SECOND_STRIKE_DOCTRINE_II,
    DEAD_HAND_PROTOCOL,
    ASSURED_RETALIATION
}
