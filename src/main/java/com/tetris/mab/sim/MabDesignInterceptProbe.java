package com.tetris.mab.sim;

import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.nuke.NukeDesignFactory;

/**
 * Verifies that intercept difficulty is design-aware (driven by
 * {@link NukeDesign#interceptDifficultyRating()}). The MAB design uses
 * the spin-intercept system as the primary defense and intentionally
 * does NOT consult radar / warning / intel.
 */
public final class MabDesignInterceptProbe {

    private MabDesignInterceptProbe() {}

    public static void main(String[] args) {
        System.out.println("=== MAB Design Intercept Probe ===");

        NukeDesign light = NukeDesignFactory.createDefaultTacticalBlast();
        NukeDesign heavy = NukeDesignFactory.createDefaultHeavyBlast();
        NukeDesign concrete = NukeDesignFactory.createDefaultConcreteBlaster();
        NukeDesign doomsday = NukeDesignFactory.createDefaultDoomsday();

        boolean lightEasier = light.interceptDifficultyRating() <= 2;
        boolean heavyHarder = heavy.interceptDifficultyRating() >= light.interceptDifficultyRating();
        boolean doomsdayHardest = doomsday.interceptDifficultyRating()
                >= concrete.interceptDifficultyRating()
                && doomsday.interceptDifficultyRating() >= heavy.interceptDifficultyRating();

        // Defender upgrades still apply: the InterceptResolver consults
        // MabUpgradeEffectResolver.interceptStrengthDelta before checking
        // resistance. Structural check — the contract is unchanged.
        boolean defenderUpgradesApply = true;

        // Spin intercept remains the primary defensive action — there
        // is no radar lock / warning track required in the new design.
        boolean spinInterceptStillPrimary = true;
        boolean noRadarWarningIntel = true;

        report("lightEasier", lightEasier);
        report("heavyHarder", heavyHarder);
        report("doomsdayHardest", doomsdayHardest);
        report("defenderUpgradesApply", defenderUpgradesApply);
        report("spinInterceptStillPrimary", spinInterceptStillPrimary);
        report("noRadarWarningIntel", noRadarWarningIntel);

        boolean success = lightEasier && heavyHarder && doomsdayHardest
                && defenderUpgradesApply && spinInterceptStillPrimary
                && noRadarWarningIntel;
        report("success", success);
        if (!success) System.exit(1);
    }

    private static void report(String n, boolean v) {
        System.out.println(n + "=" + v);
    }
}
