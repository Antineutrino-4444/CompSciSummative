package com.tetris.mab.nuke;

/**
 * Strategic doctrine of a {@link NukeDesign}.
 *
 * <p>This enum is the current MAB strategic doctrine model. Legacy
 * values ({@link #MIRV}, {@link #DECOY_PACKAGE}, {@link #DIRTY_BOMB},
 * {@link #SALTED_WARHEAD}) are kept only as binary-compatible
 * placeholders / aliases so older save data and existing probes still
 * resolve. They are never produced by the Nuke Builder, never offered
 * as a preset, never displayed to the player, and never schedule split
 * payloads or fake-launch behavior.
 */
public enum NukeDoctrineType {

    // ── New current doctrine model ────────────────────────────
    TACTICAL_BLAST,
    HEAVY_BLAST,
    DIRTY_PAYLOAD,
    SALTED_PAYLOAD,
    EMP_PAYLOAD,
    BUNKER_BUSTER,
    CONCRETE_BLASTER,
    CLEAN_FUSION,
    DOOMSDAY,
    PLACEHOLDER,

    /** Legacy alias of {@link #DIRTY_PAYLOAD}. Never produced by current code. */
    @Deprecated
    DIRTY_BOMB,
    /** Legacy alias of {@link #SALTED_PAYLOAD}. Never produced by current code. */
    @Deprecated
    SALTED_WARHEAD,
    /** Legacy/removed. Remapped to {@link #HEAVY_BLAST} by the bridge. */
    @Deprecated
    MIRV,
    /** Legacy/removed. Remapped to {@link #PLACEHOLDER}. */
    @Deprecated
    DECOY_PACKAGE;

    /**
     * Lenient parser: case-insensitive, accepts spaces, hyphens, and
     * underscores. Unknown or null values resolve to {@link #PLACEHOLDER}.
     * Legacy doctrine names are remapped to their current equivalents
     * (e.g. {@code DIRTY_BOMB} → {@link #DIRTY_PAYLOAD},
     * {@code MIRV} → {@link #HEAVY_BLAST}).
     */
    public static NukeDoctrineType fromString(String value) {
        if (value == null) return PLACEHOLDER;
        String normalized = value.trim().replace('-', '_').replace(' ', '_').toUpperCase();
        if (normalized.isEmpty()) return PLACEHOLDER;
        // Migration aliases for legacy stored strings.
        switch (normalized) {
            case "DIRTY_BOMB":     return DIRTY_PAYLOAD;
            case "SALTED_WARHEAD": return SALTED_PAYLOAD;
            case "MIRV":           return HEAVY_BLAST;
            case "DECOY_PACKAGE":  return PLACEHOLDER;
            default: /* fall through */
        }
        for (NukeDoctrineType t : values()) {
            if (t.name().equals(normalized)) return t;
        }
        return PLACEHOLDER;
    }

    /** Player-facing label without engineering jargon. */
    public String displayLabel() {
        return switch (this) {
            case TACTICAL_BLAST    -> "Tactical Blast";
            case HEAVY_BLAST       -> "Heavy Blast";
            case DIRTY_PAYLOAD,
                 DIRTY_BOMB        -> "Dirty Payload";
            case SALTED_PAYLOAD,
                 SALTED_WARHEAD    -> "Salted Payload";
            case EMP_PAYLOAD       -> "EMP Payload";
            case BUNKER_BUSTER     -> "Bunker Buster";
            case CONCRETE_BLASTER  -> "Concrete Blaster";
            case CLEAN_FUSION      -> "Clean Fusion";
            case DOOMSDAY          -> "Doomsday";
            case PLACEHOLDER       -> "Training Payload";
            case MIRV              -> "Heavy Blast";        // legacy, never shown
            case DECOY_PACKAGE     -> "Training Payload";   // legacy, never shown
        };
    }

    /** True if this doctrine is still produced by the current Nuke Builder / factory. */
    public boolean isCurrent() {
        return switch (this) {
            case MIRV, DECOY_PACKAGE, DIRTY_BOMB, SALTED_WARHEAD -> false;
            default -> true;
        };
    }
}
