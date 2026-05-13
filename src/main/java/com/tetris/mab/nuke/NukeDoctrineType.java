package com.tetris.mab.nuke;

/**
 * Strategic doctrine of a {@link NukeDesign}. Determines the broad
 * "shape" of the weapon (clean, dirty, EMP, MIRV, decoy, ...).
 *
 * <p>This enum is part of the Step-3 strategic schema and is
 * deliberately decoupled from the UI builder's
 * {@code com.tetris.model.nuke} package.
 */
public enum NukeDoctrineType {
    CLEAN_FUSION,
    DIRTY_BOMB,
    SALTED_WARHEAD,
    CONCRETE_BLASTER,
    EMP_PAYLOAD,
    MIRV,
    BUNKER_BUSTER,
    DECOY_PACKAGE,
    DOOMSDAY,
    PLACEHOLDER;

    /**
     * Lenient parser: case-insensitive, accepts spaces, hyphens, and
     * underscores. Unknown or null values resolve to {@link #PLACEHOLDER}.
     */
    public static NukeDoctrineType fromString(String value) {
        if (value == null) return PLACEHOLDER;
        String normalized = value.trim().replace('-', '_').replace(' ', '_').toUpperCase();
        if (normalized.isEmpty()) return PLACEHOLDER;
        for (NukeDoctrineType t : values()) {
            if (t.name().equals(normalized)) return t;
        }
        return PLACEHOLDER;
    }
}
