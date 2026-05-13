package com.tetris.mab.sim;

import com.tetris.mab.ai.MabAiArchetype;
import com.tetris.mab.ai.MabAiDifficulty;
import com.tetris.mab.balance.MabBalanceProfile;
import com.tetris.mab.balance.MabBalanceProfiles;

/**
 * Step 14 — immutable configuration for a single headless MAB
 * simulation run.
 *
 * <p>Null {@code mode} defaults to {@link MabSimulationMode#SMOKE}.
 * Null archetypes default to {@link MabAiArchetype#BALANCED}.
 * Null difficulty defaults to {@link MabAiDifficulty#NORMAL}.
 */
public record MabSimulationConfig(
        MabSimulationMode mode,
        int ticks,
        MabAiArchetype playerAArchetype,
        MabAiArchetype playerBArchetype,
        MabAiDifficulty difficulty,
        boolean enablePlayerAAi,
        boolean enablePlayerBAi,
        boolean autoResolveImpacts,
        boolean verboseEvents,
        int maxEventRows,
        String label,
        String balanceProfileId) {

    public MabSimulationConfig {
        if (ticks < 0) throw new IllegalArgumentException("ticks must be >= 0");
        if (maxEventRows < 0) throw new IllegalArgumentException("maxEventRows must be >= 0");
        if (mode == null) mode = MabSimulationMode.SMOKE;
        if (playerAArchetype == null) playerAArchetype = MabAiArchetype.BALANCED;
        if (playerBArchetype == null) playerBArchetype = MabAiArchetype.BALANCED;
        if (difficulty == null) difficulty = MabAiDifficulty.NORMAL;
        if (label == null) label = mode.name();
        if (balanceProfileId == null || balanceProfileId.isBlank()) {
            balanceProfileId = MabBalanceProfiles.STANDARD_PVE;
        }
    }

    /** Backward-compatible 12-arg constructor used by older callers. */
    public MabSimulationConfig(MabSimulationMode mode, int ticks,
                               MabAiArchetype a, MabAiArchetype b, MabAiDifficulty d,
                               boolean enableA, boolean enableB,
                               boolean autoResolveImpacts, boolean verboseEvents,
                               int maxEventRows, String label) {
        this(mode, ticks, a, b, d, enableA, enableB,
                autoResolveImpacts, verboseEvents, maxEventRows, label,
                MabBalanceProfiles.STANDARD_PVE);
    }

    public MabBalanceProfile balanceProfile() {
        return MabBalanceProfiles.byId(balanceProfileId);
    }

    /** Returns a copy of this config with the given profile id. */
    public MabSimulationConfig withBalanceProfileId(String id) {
        return new MabSimulationConfig(mode, ticks, playerAArchetype, playerBArchetype,
                difficulty, enablePlayerAAi, enablePlayerBAi,
                autoResolveImpacts, verboseEvents, maxEventRows, label,
                (id == null || id.isBlank()) ? MabBalanceProfiles.STANDARD_PVE : id);
    }

    public static MabSimulationConfig smoke() {
        return new MabSimulationConfig(MabSimulationMode.SMOKE, 80,
                MabAiArchetype.BALANCED, MabAiArchetype.BALANCED,
                MabAiDifficulty.NORMAL,
                false, false,
                true, false, 40, "SMOKE");
    }

    public static MabSimulationConfig aiVsDummy(int ticks) {
        return new MabSimulationConfig(MabSimulationMode.AI_VS_DUMMY,
                Math.max(ticks, 0),
                MabAiArchetype.BALANCED, MabAiArchetype.TACTICAL_SPAMMER,
                MabAiDifficulty.NORMAL,
                false, true,
                true, false, 40, "AI_VS_DUMMY");
    }

    public static MabSimulationConfig aiVsAi(int ticks, MabAiArchetype a,
                                             MabAiArchetype b, MabAiDifficulty difficulty) {
        return new MabSimulationConfig(MabSimulationMode.AI_VS_AI,
                Math.max(ticks, 0),
                a, b, difficulty,
                true, true,
                true, false, 40, "AI_VS_AI");
    }

    public static MabSimulationConfig radarDecoy() {
        return new MabSimulationConfig(MabSimulationMode.RADAR_DECOY, 20,
                MabAiArchetype.BALANCED, MabAiArchetype.MIRV_CONTROLLER,
                MabAiDifficulty.NORMAL,
                false, false,
                true, false, 40, "RADAR_DECOY");
    }

    public static MabSimulationConfig launchImpact() {
        return new MabSimulationConfig(MabSimulationMode.LAUNCH_IMPACT, 60,
                MabAiArchetype.TACTICAL_SPAMMER, MabAiArchetype.BALANCED,
                MabAiDifficulty.NORMAL,
                false, false,
                true, false, 40, "LAUNCH_IMPACT");
    }

    public static MabSimulationConfig civilDefense() {
        return new MabSimulationConfig(MabSimulationMode.CIVIL_DEFENSE, 60,
                MabAiArchetype.TACTICAL_SPAMMER, MabAiArchetype.MAD_DEFENDER,
                MabAiDifficulty.NORMAL,
                false, false,
                true, false, 40, "CIVIL_DEFENSE");
    }

    public static MabSimulationConfig upgradeFlow() {
        return new MabSimulationConfig(MabSimulationMode.UPGRADE_FLOW, 20,
                MabAiArchetype.BALANCED, MabAiArchetype.BALANCED,
                MabAiDifficulty.NORMAL,
                false, false,
                true, false, 40, "UPGRADE_FLOW");
    }
}
