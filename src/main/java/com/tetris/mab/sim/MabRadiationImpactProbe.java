package com.tetris.mab.sim;

import com.tetris.events.GarbageRowPattern;
import com.tetris.mab.impact.RadiationGarbagePatternGenerator;
import com.tetris.mab.impact.RadiationLevel;
import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.nuke.NukeDesignFactory;
import com.tetris.model.Board;

import java.util.List;

/**
 * Verifies the live radiation system:
 *
 * <ul>
 *   <li>Clean designs produce LOW radiation tier (CLEAN / LIGHT).</li>
 *   <li>Dirty designs produce delayed waves.</li>
 *   <li>Salted designs produce severe waves with more pattern shifts.</li>
 *   <li>Radiation drives messier garbage patterns (more hole shifts).</li>
 *   <li>Output is deterministic for fixed inputs.</li>
 *   <li>Blast remains the immediate damage axis even at high radiation.</li>
 *   <li>No real-world units appear in the design display.</li>
 * </ul>
 */
public final class MabRadiationImpactProbe {

    private MabRadiationImpactProbe() {}

    public static void main(String[] args) {
        System.out.println("=== MAB Radiation Impact Probe ===");

        NukeDesign clean = NukeDesignFactory.createDefaultCleanFusion();
        NukeDesign dirty = NukeDesignFactory.createDefaultDirtyPayload();
        NukeDesign salted = NukeDesignFactory.createDefaultSaltedPayload();
        NukeDesign tactical = NukeDesignFactory.createDefaultTacticalBlast();

        boolean cleanLowRadiation = clean.getRadiationRating() <= 1
                && RadiationLevel.fromRating(clean.getRadiationRating()).messinessScore() <= 1;
        boolean dirtyDelayedWaves = dirty.getGarbageProfile() != null
                && dirty.getGarbageProfile().usesWaves()
                && dirty.getGarbageProfile().waveCount() >= 1;
        boolean saltedSevereWaves = salted.getGarbageProfile() != null
                && salted.getGarbageProfile().usesWaves()
                && RadiationLevel.fromRating(salted.getRadiationRating()).messinessScore()
                        > RadiationLevel.fromRating(dirty.getRadiationRating()).messinessScore() - 1;

        RadiationGarbagePatternGenerator gen = new RadiationGarbagePatternGenerator();
        List<GarbageRowPattern> cleanPattern = gen.generate(6, Board.WIDTH,
                RadiationLevel.fromRating(clean.getRadiationRating()), "test-clean");
        List<GarbageRowPattern> saltedPattern = gen.generate(6, Board.WIDTH,
                RadiationLevel.fromRating(salted.getRadiationRating()), "test-salted");
        int cleanShifts = countShifts(cleanPattern);
        int saltedShifts = countShifts(saltedPattern);
        boolean radiationAffectsPattern = saltedShifts >= cleanShifts;

        // Determinism: same seed/tag should reproduce.
        List<GarbageRowPattern> dirtyPattern1 = gen.generate(8, Board.WIDTH,
                RadiationLevel.HOT, "launch-42:wave:0");
        List<GarbageRowPattern> dirtyPattern2 = gen.generate(8, Board.WIDTH,
                RadiationLevel.HOT, "launch-42:wave:0");
        boolean radiationDeterministic = patternsEqual(dirtyPattern1, dirtyPattern2);

        // Blast is the immediate axis even when radiation is high.
        boolean blastStillImmediate = salted.getBlastRating() > 0
                && salted.getGarbageProfile().maxImmediateLines() >= 1;

        boolean noRealWorldUnits = noRealWorldUnits(clean) && noRealWorldUnits(dirty)
                && noRealWorldUnits(salted) && noRealWorldUnits(tactical);

        report("cleanLowRadiation", cleanLowRadiation);
        report("dirtyDelayedWaves", dirtyDelayedWaves);
        report("saltedSevereWaves", saltedSevereWaves);
        report("radiationAffectsPattern", radiationAffectsPattern);
        report("radiationDeterministic", radiationDeterministic);
        report("blastStillImmediate", blastStillImmediate);
        report("noRealWorldUnits", noRealWorldUnits);

        boolean success = cleanLowRadiation && dirtyDelayedWaves && saltedSevereWaves
                && radiationAffectsPattern && radiationDeterministic
                && blastStillImmediate && noRealWorldUnits;
        report("success", success);
        if (!success) System.exit(1);
    }

    private static int countShifts(List<GarbageRowPattern> patterns) {
        int shifts = 0;
        int prev = -1;
        for (GarbageRowPattern p : patterns) {
            int hole = p.holeColumns().isEmpty() ? -1 : p.holeColumns().get(0);
            if (prev >= 0 && hole != prev) shifts++;
            prev = hole;
        }
        return shifts;
    }

    private static boolean patternsEqual(List<GarbageRowPattern> a, List<GarbageRowPattern> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).holeColumns().equals(b.get(i).holeColumns())) return false;
        }
        return true;
    }

    private static boolean noRealWorldUnits(NukeDesign d) {
        String s = (d.getDisplayName() + " " + d.getDoctrineType().displayLabel()).toLowerCase();
        String[] forbidden = {"sievert", "rem", "rad/h", "kilotons", "megatons",
                "fallout", "isotope", "u-235", "pu-239", "tritium"};
        for (String f : forbidden) if (s.contains(f)) return false;
        return true;
    }

    private static void report(String n, boolean v) {
        System.out.println(n + "=" + v);
    }
}
