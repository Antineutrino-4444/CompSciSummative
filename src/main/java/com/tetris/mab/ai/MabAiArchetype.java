package com.tetris.mab.ai;

/** Strategic flavour for the offline MAB AI driver. */
public enum MabAiArchetype {
    /** Builds and launches smaller nukes frequently. */
    TACTICAL_SPAMMER("Tactical Tempo"),
    /** Favours radiation/messy impact profiles. */
    DIRTY_BOMBER("Radiation Pressure"),
    /** Favours silo/disarm pressure. */
    CONCRETE_STRATEGIST("Silo Breaker"),
    /** Prioritises civil defence, intercepts, second-strike posture. */
    MAD_DEFENDER("Second-Strike Defender"),
    /** Control-focused archetype that prefers disruption payloads. */
    PAYLOAD_CONTROLLER("Payload Controller"),
    /** Slowly builds large weapons, launches rarely. */
    DOOMSDAY_HOARDER("Doomsday Builder"),
    /** General-purpose baseline. */
    BALANCED("Balanced");

    private final String displayName;

    MabAiArchetype(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
