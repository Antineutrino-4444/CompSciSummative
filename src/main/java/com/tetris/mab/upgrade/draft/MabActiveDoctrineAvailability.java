package com.tetris.mab.upgrade.draft;

/**
 * Read-only row consumed by the active-doctrine and status overlays.
 */
public record MabActiveDoctrineAvailability(
        MabActiveDoctrineType type,
        boolean owned,
        boolean available,
        String status,
        String reason,
        int chargeCost) {

    public String displayName() {
        return type == null ? "" : type.displayName();
    }
}
