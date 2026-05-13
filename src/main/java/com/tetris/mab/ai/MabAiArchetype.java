package com.tetris.mab.ai;

/** Step 13 — strategic flavour for the offline PvE AI driver. */
public enum MabAiArchetype {
    /** Builds and launches smaller nukes frequently. */
    TACTICAL_SPAMMER,
    /** Favours radiation/messy impacts and decoys. */
    DIRTY_BOMBER,
    /** Favours silo/disarm pressure. */
    CONCRETE_STRATEGIST,
    /** Prioritises civil defence, intercepts, second-strike posture. */
    MAD_DEFENDER,
    /** Favours MIRV-like deception, ghost signatures, radar pressure. */
    MIRV_CONTROLLER,
    /** Slowly builds large weapons, launches rarely. */
    DOOMSDAY_HOARDER,
    /** General-purpose baseline. */
    BALANCED
}
