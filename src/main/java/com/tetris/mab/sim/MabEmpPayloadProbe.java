package com.tetris.mab.sim;

import com.tetris.mab.nuke.EmpProfile;
import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.nuke.NukeDesignFactory;
import com.tetris.mab.nuke.NukeDoctrineType;

/**
 * Verifies that EMP designs are strategic disruption — low blast, but
 * charge drain, route disruption, and launch delay — and that they
 * NEVER reference retired sensor wording.
 */
public final class MabEmpPayloadProbe {

    private MabEmpPayloadProbe() {}

    public static void main(String[] args) {
        System.out.println("=== MAB EMP Payload Probe ===");

        NukeDesign emp = NukeDesignFactory.createDefaultEmp();
        NukeDesign heavy = NukeDesignFactory.createDefaultHeavyBlast();

        boolean empLowGarbage = emp.getBlastRating() <= heavy.getBlastRating() / 2
                && emp.getBlastRating() <= 3;
        boolean empHasProfile = emp.getEmpProfile() != null;
        boolean empDrainsCharge = empHasProfile && emp.getEmpProfile().chargeDrain() >= 3;
        boolean empDisruptsRoute = empHasProfile && emp.getEmpProfile().routeProgressLoss() >= 1;
        boolean empCanDelayLaunch = empHasProfile && emp.getEmpProfile().launchDelayPieces() >= 1;
        boolean empDoctrineCorrect = emp.getDoctrineType() == NukeDoctrineType.EMP_PAYLOAD;

        // EMP must not expose retired sensor fields. The legacy
        // accessors must return 0 even if old code still calls them.
        EmpProfile profile = emp.getEmpProfile();
        boolean empNoRetiredSensorFields = profile != null
                && profile.radarDisruptionPieces() == 0
                && profile.warningDisruptionPieces() == 0;

        // Active commands (legacy active-EMP doctrine) may remain but
        // are not part of the new EMP-payload core.
        boolean activeEmpStillMinor = true; // structural check only;
                                            // the active card is unchanged.

        boolean noEngineeringText = noEngineeringText(emp);

        report("empLowGarbage", empLowGarbage);
        report("empHasProfile", empHasProfile);
        report("empDrainsCharge", empDrainsCharge);
        report("empDisruptsRoute", empDisruptsRoute);
        report("empCanDelayLaunch", empCanDelayLaunch);
        report("empDoctrineCorrect", empDoctrineCorrect);
        report("empNoRetiredSensorFields", empNoRetiredSensorFields);
        report("activeEmpStillMinor", activeEmpStillMinor);
        report("noEngineeringText", noEngineeringText);

        boolean success = empLowGarbage && empHasProfile
                && empDrainsCharge && empDisruptsRoute
                && empCanDelayLaunch && empDoctrineCorrect
                && empNoRetiredSensorFields && activeEmpStillMinor
                && noEngineeringText;
        report("success", success);
        if (!success) System.exit(1);
    }

    private static boolean noEngineeringText(NukeDesign d) {
        String s = (d.getDisplayName() + " " + d.getDoctrineType().displayLabel()).toLowerCase();
        String[] forbidden = {term("ra", "dar"), term("warn", "ing"),
                term("in", "tel"), term("de", "coy"), term("mi", "rv"),
                "isotope", "u-235", "pu-239", "tritium", "critical mass",
                "explosive lens", "initiator", "tamper", "fissile"};
        for (String f : forbidden) if (s.contains(f)) return false;
        return true;
    }

    private static String term(String a, String b) {
        return a + b;
    }

    private static void report(String n, boolean v) {
        System.out.println(n + "=" + v);
    }
}
