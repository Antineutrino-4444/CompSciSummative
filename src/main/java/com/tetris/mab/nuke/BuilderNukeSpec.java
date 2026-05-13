package com.tetris.mab.nuke;

/**
 * Neutral, immutable DTO bridging the UI nuke builder
 * ({@code com.tetris.model.nuke}, etc.) and the strategic
 * {@link NukeDesign} schema. The builder produces a
 * {@code BuilderNukeSpec}; {@link NukeBuilderAdapter} converts it into
 * a fully populated {@link NukeDesign}.
 *
 * <p>Plain-data only — no UI types, no game-engine references.
 */
public record BuilderNukeSpec(
        String id,
        String name,
        String doctrineType,
        int size,
        int blast,
        int radiation,
        int disarm,
        int siloDamage,
        int speed,
        int stealth,
        boolean mirv,
        boolean emp,
        boolean decoy) {
}
