package com.tetris.mab.upgrade.draft;

/**
 * Design-layer archetype for a draft card. A card may belong to one or
 * two archetypes; dual-archetype cards are explicitly listed in the spec
 * ({@code B2B Amplifier}, {@code Counterspin Training}).
 *
 * <p>Archetypes are used by the AI drafter to bias picks toward its committed
 * build identity and by the UI to group/filter cards. They have no mechanical
 * effect on gameplay.
 */
public enum MabUpgradeArchetype {
    COMBO_REACTOR,
    SPIN_SPECIALIST,
    TETRIS_STOCKPILER,
    TURTLE,
    RUSHER,
    WILDCARD
}
