package com.tetris.mab.sim;

import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.nuke.NukeDesignFactory;

/**
 * Verifies that blast / radiation / EMP / disarm / silo damage are
 * separate, distinct impact axes on the strategic NukeDesign.
 */
public final class MabImpactProfileSeparationProbe {

    private MabImpactProfileSeparationProbe() {}

    public static void main(String[] args) {
        System.out.println("=== MAB Impact Profile Separation Probe ===");

        NukeDesign tactical = NukeDesignFactory.createDefaultTacticalBlast();
        NukeDesign heavy = NukeDesignFactory.createDefaultHeavyBlast();
        NukeDesign dirty = NukeDesignFactory.createDefaultDirtyPayload();
        NukeDesign emp = NukeDesignFactory.createDefaultEmp();
        NukeDesign concrete = NukeDesignFactory.createDefaultConcreteBlaster();
        NukeDesign bunker = NukeDesignFactory.createDefaultBunkerBuster();
        NukeDesign clean = NukeDesignFactory.createDefaultCleanFusion();

        boolean blastImmediate = heavy.getBlastRating() > tactical.getBlastRating()
                && heavy.getGarbageProfile().maxImmediateLines() >= 4;
        boolean radiationDelayed = dirty.getRadiationRating() > clean.getRadiationRating()
                && dirty.getGarbageProfile().usesWaves();
        boolean disarmReducesCharge = concrete.getDisarmRating() > tactical.getDisarmRating()
                && concrete.getDisarmProfile() != null
                && concrete.getDisarmProfile().disarmPower() >= 5;
        boolean siloDamagesInfrastructure = bunker.getSiloDamageRating() > tactical.getSiloDamageRating()
                && bunker.getSiloDamageProfile() != null
                && bunker.getSiloDamageProfile().siloDamagePower() >= 4;
        boolean empDisruptsTempo = emp.getEmpProfile() != null
                && emp.getEmpProfile().chargeDrain() > 0
                && emp.getBlastRating() < heavy.getBlastRating();

        // Each design should be the strongest in its own axis.
        boolean profilesDistinct =
                heavy.getBlastRating() > tactical.getBlastRating()
                && dirty.getRadiationRating() > heavy.getRadiationRating()
                && emp.getEmpRating() > heavy.getEmpRating()
                && concrete.getDisarmRating() > tactical.getDisarmRating()
                && bunker.getSiloDamageRating() > tactical.getSiloDamageRating();

        report("blastImmediate", blastImmediate);
        report("radiationDelayed", radiationDelayed);
        report("disarmReducesCharge", disarmReducesCharge);
        report("siloDamagesInfrastructure", siloDamagesInfrastructure);
        report("empDisruptsTempo", empDisruptsTempo);
        report("profilesDistinct", profilesDistinct);

        boolean success = blastImmediate && radiationDelayed
                && disarmReducesCharge && siloDamagesInfrastructure
                && empDisruptsTempo && profilesDistinct;
        report("success", success);
        if (!success) System.exit(1);
    }

    private static void report(String n, boolean v) {
        System.out.println(n + "=" + v);
    }
}
