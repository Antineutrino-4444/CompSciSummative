package com.tetris.mab.balance;

import java.util.List;

/**
 * Step 19 — registry of provisional Balance Pass v1 PvE profiles.
 * Values here are intentionally tuneable and are validated by the
 * Step 14 simulation harness.
 */
public final class MabBalanceProfiles {

    public static final String STANDARD_PVE      = "standard-pve";
    public static final String GENTLE_PVE        = "gentle-pve";
    public static final String HIGH_PRESSURE_PVE = "high-pressure-pve";
    public static final String DEBUG_FAST        = "debug-fast";

    private MabBalanceProfiles() {}

    public static MabBalanceProfile standardPve() {
        return MabBalanceProfile.builder()
                .id(STANDARD_PVE)
                .displayName("Standard PvE")
                .description("Default PvE pacing — visible AI pressure but not overwhelming.")
                .chargeGainPerTick(1, 3, 5, 12)
                .addChargeStep(8, 15, 24, 50)
                .launchCooldown(18, 14, 10, 2)
                .radarCooldown(18, 14, 10, 2)
                .decoyCooldown(26, 22, 16, 3)
                .defenseCooldown(12, 10, 7, 2)
                .upgradeCooldown(36, 30, 22, 4)
                .aiEarlyLaunchDelayTicks(35)
                .aiMinimumTicksBeforeDecoy(20)
                .aiMinimumTicksBeforeUpgrade(40)
                .aiMinimumTicksBeforeCivilDefense(10)
                .maxAiLaunchesBeforeTick80(2)
                .maxAiLaunchesBeforeTick160(5)
                .preferredImpactGracePieces(5)
                .preferredMaxImmediateGarbageRows(6)
                .preferredCivilDefenseStartingCharges(1)
                .preferredCivilDefenseShieldPieces(8)
                .defaultForPve(true)
                .build();
    }

    public static MabBalanceProfile gentlePve() {
        return MabBalanceProfile.builder()
                .id(GENTLE_PVE)
                .displayName("Gentle PvE")
                .description("Slower AI pacing for learning.")
                .chargeGainPerTick(1, 2, 3, 12)
                .addChargeStep(6, 10, 16, 50)
                .launchCooldown(24, 20, 16, 2)
                .radarCooldown(22, 18, 14, 2)
                .decoyCooldown(32, 28, 22, 3)
                .defenseCooldown(14, 12, 9, 2)
                .upgradeCooldown(44, 36, 28, 4)
                .aiEarlyLaunchDelayTicks(60)
                .aiMinimumTicksBeforeDecoy(30)
                .aiMinimumTicksBeforeUpgrade(50)
                .aiMinimumTicksBeforeCivilDefense(14)
                .maxAiLaunchesBeforeTick80(1)
                .maxAiLaunchesBeforeTick160(3)
                .preferredImpactGracePieces(7)
                .preferredMaxImmediateGarbageRows(4)
                .preferredCivilDefenseStartingCharges(2)
                .preferredCivilDefenseShieldPieces(10)
                .defaultForPve(false)
                .build();
    }

    public static MabBalanceProfile highPressurePve() {
        return MabBalanceProfile.builder()
                .id(HIGH_PRESSURE_PVE)
                .displayName("High Pressure PvE")
                .description("Faster AI pacing for testing and challenge.")
                .chargeGainPerTick(2, 4, 7, 12)
                .addChargeStep(10, 18, 28, 50)
                .launchCooldown(12, 10, 7, 2)
                .radarCooldown(12, 10, 7, 2)
                .decoyCooldown(20, 16, 12, 3)
                .defenseCooldown(10, 8, 6, 2)
                .upgradeCooldown(30, 24, 18, 4)
                .aiEarlyLaunchDelayTicks(20)
                .aiMinimumTicksBeforeDecoy(14)
                .aiMinimumTicksBeforeUpgrade(30)
                .aiMinimumTicksBeforeCivilDefense(8)
                .maxAiLaunchesBeforeTick80(3)
                .maxAiLaunchesBeforeTick160(7)
                .preferredImpactGracePieces(4)
                .preferredMaxImmediateGarbageRows(8)
                .preferredCivilDefenseStartingCharges(1)
                .preferredCivilDefenseShieldPieces(6)
                .defaultForPve(false)
                .build();
    }

    public static MabBalanceProfile debugFast() {
        return MabBalanceProfile.builder()
                .id(DEBUG_FAST)
                .displayName("Debug Fast")
                .description("Quick action for developer validation.")
                .chargeGainPerTick(4, 6, 10, 12)
                .addChargeStep(20, 30, 40, 50)
                .launchCooldown(4, 3, 2, 1)
                .radarCooldown(4, 3, 2, 1)
                .decoyCooldown(6, 4, 3, 1)
                .defenseCooldown(4, 3, 2, 1)
                .upgradeCooldown(8, 6, 4, 1)
                .aiEarlyLaunchDelayTicks(0)
                .aiMinimumTicksBeforeDecoy(0)
                .aiMinimumTicksBeforeUpgrade(0)
                .aiMinimumTicksBeforeCivilDefense(0)
                .maxAiLaunchesBeforeTick80(20)
                .maxAiLaunchesBeforeTick160(40)
                .preferredImpactGracePieces(2)
                .preferredMaxImmediateGarbageRows(8)
                .preferredCivilDefenseStartingCharges(1)
                .preferredCivilDefenseShieldPieces(4)
                .defaultForPve(false)
                .build();
    }

    public static List<MabBalanceProfile> all() {
        return List.of(standardPve(), gentlePve(), highPressurePve(), debugFast());
    }

    /** Returns the profile with the given id, or {@link #standardPve()} if unknown/null. */
    public static MabBalanceProfile byId(String id) {
        if (id == null) return standardPve();
        return switch (id) {
            case STANDARD_PVE      -> standardPve();
            case GENTLE_PVE        -> gentlePve();
            case HIGH_PRESSURE_PVE -> highPressurePve();
            case DEBUG_FAST        -> debugFast();
            default                -> standardPve();
        };
    }
}
