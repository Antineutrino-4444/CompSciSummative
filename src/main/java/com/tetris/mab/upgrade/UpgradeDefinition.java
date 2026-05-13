package com.tetris.mab.upgrade;

import java.util.List;

/**
 * Immutable definition of a single upgrade entry. Identifier, display
 * name, max-level, base cost, DEFCON gating and prerequisite list are
 * all locked in at construction time.
 */
public record UpgradeDefinition(
        UpgradeType type,
        String id,
        String displayName,
        UpgradeCategory category,
        int maxLevel,
        int baseCost,
        int requiredDefconMaximum,
        List<UpgradeType> prerequisites,
        String description) {

    public UpgradeDefinition {
        if (type == null) throw new IllegalArgumentException("type");
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id");
        if (displayName == null || displayName.isBlank())
            throw new IllegalArgumentException("displayName");
        if (category == null) throw new IllegalArgumentException("category");
        if (maxLevel < 1) throw new IllegalArgumentException("maxLevel < 1");
        if (baseCost < 0) throw new IllegalArgumentException("baseCost < 0");
        if (requiredDefconMaximum < 1 || requiredDefconMaximum > 5)
            throw new IllegalArgumentException("requiredDefconMaximum out of range");
        prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
        description = description == null ? "" : description;
    }
}
