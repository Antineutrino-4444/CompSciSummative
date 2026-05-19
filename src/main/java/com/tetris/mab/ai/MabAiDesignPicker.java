package com.tetris.mab.ai;

import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.nuke.NukeDesignFactory;

/**
 * Picks the offline MAB AI's starting Nuke Builder design based on
 * archetype (and, where useful, difficulty).
 *
 * <p>Lets the AI behave like an opponent who pre-built a warhead that
 * fits its strategic identity instead of always defaulting to the
 * shell's placeholder design. Each archetype maps to a default loadout
 * that synergises with its decision tendencies (charge tempo, feint
 * preference, intercept appetite — see {@link MabAiPolicy}).
 *
 * <p>Higher tiers (HARD+) get harder-hitting variants where there is
 * an obvious in-archetype upgrade.
 */
public final class MabAiDesignPicker {

    private MabAiDesignPicker() {}

    /** Pick a fresh design for the given archetype + difficulty. */
    public static NukeDesign pick(MabAiArchetype archetype, MabAiDifficulty difficulty) {
        if (archetype == null) archetype = MabAiArchetype.BALANCED;
        int tier = difficulty == null ? 1 : difficulty.tier();
        return switch (archetype) {
            case TACTICAL_SPAMMER -> tier >= MabAiDifficulty.HARD.tier()
                    ? NukeDesignFactory.createDefaultHeavyBlast()
                    : NukeDesignFactory.createDefaultTacticalBlast();
            case DIRTY_BOMBER -> tier >= MabAiDifficulty.HARD.tier()
                    ? NukeDesignFactory.createDefaultSaltedPayload()
                    : NukeDesignFactory.createDefaultDirtyPayload();
            case CONCRETE_STRATEGIST -> tier >= MabAiDifficulty.HARD.tier()
                    ? NukeDesignFactory.createDefaultConcreteBlaster()
                    : NukeDesignFactory.createDefaultBunkerBuster();
            case MAD_DEFENDER -> tier >= MabAiDifficulty.HARD.tier()
                    ? NukeDesignFactory.createDefaultHeavyBlast()
                    : NukeDesignFactory.createDefaultCleanFusion();
            case PAYLOAD_CONTROLLER -> NukeDesignFactory.createDefaultEmp();
            case DOOMSDAY_HOARDER -> NukeDesignFactory.createDefaultDoomsday();
            case BALANCED -> tier >= MabAiDifficulty.HARD.tier()
                    ? NukeDesignFactory.createDefaultHeavyBlast()
                    : NukeDesignFactory.createDefaultTacticalBlast();
        };
    }
}
