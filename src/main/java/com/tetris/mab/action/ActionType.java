package com.tetris.mab.action;

/** Concrete action identity used by the registry and the public match API. */
public enum ActionType {
    // ─── Launches ───
    MICRO_LAUNCH,
    TACTICAL_LAUNCH,
    THEATER_LAUNCH,
    STRATEGIC_LAUNCH,
    DIRTY_LAUNCH,
    HEAVY_SPLIT_LAUNCH,
    CONCRETE_BLASTER_LAUNCH,
    SUPERHEAVY_LAUNCH,
    DOOMSDAY_LAUNCH,

    // ─── Defense ───
    EMERGENCY_INTERCEPT,
    STANDARD_INTERCEPT,
    FULL_INTERCEPT,

    // ─── Utility / analysis / feint / restraint ───
    ROUTE_SCAN,
    SILO_HARDEN,
    CIVIL_DEFENSE,
    FEINT_LAUNCH,
    GHOST_SPLIT,
    FALSE_DOCTRINE_SIGNAL,
    DUMMY_SILO_HEAT,
    MASKED_LAUNCH,
    COUNTERLAUNCH_PREP,
    TREATY_RESTRAINT_LOCK,
    EMP_PULSE,
    CONCRETE_BLASTER_ARM,

    /** Catch-all for ad-hoc / dynamically created actions. */
    CUSTOM;

    public boolean isLaunch() {
        return switch (this) {
            case MICRO_LAUNCH, TACTICAL_LAUNCH, THEATER_LAUNCH, STRATEGIC_LAUNCH,
                 DIRTY_LAUNCH, HEAVY_SPLIT_LAUNCH, CONCRETE_BLASTER_LAUNCH,
                 SUPERHEAVY_LAUNCH, DOOMSDAY_LAUNCH, MASKED_LAUNCH -> true;
            default -> false;
        };
    }

    public boolean isDefense() {
        return switch (this) {
            case EMERGENCY_INTERCEPT, STANDARD_INTERCEPT, FULL_INTERCEPT,
                 CIVIL_DEFENSE, SILO_HARDEN -> true;
            default -> false;
        };
    }

    public boolean isIntel() {
        return this == ROUTE_SCAN;
    }

    public boolean isDecoy() {
        return switch (this) {
            case FEINT_LAUNCH, GHOST_SPLIT, FALSE_DOCTRINE_SIGNAL,
                 DUMMY_SILO_HEAT, MASKED_LAUNCH -> true;
            default -> false;
        };
    }

    public boolean isUtility() {
        return switch (this) {
            case COUNTERLAUNCH_PREP, EMP_PULSE, CONCRETE_BLASTER_ARM,
                 TREATY_RESTRAINT_LOCK, CUSTOM -> true;
            default -> false;
        };
    }
}
