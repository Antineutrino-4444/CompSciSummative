package com.tetris.mab.nuke;

import com.tetris.model.nuke.NukePart;
import com.tetris.model.nuke.NukeSlot;

/**
 * Safe bridge from the educational Nuke Builder model to MAB's abstract
 * strategic nuke design. It preserves the educational builder as the
 * loadout engine for MAB: design choices feed straight into live charge
 * requirement, launch tempo, impact profile, and DEFCON behavior.
 *
 * <p>The gameplay summaries only mention size class, doctrine flavor,
 * and broad gameplay ratings — never construction instructions,
 * component details, isotope names, or engineering data. Educational
 * details remain in the builder dialog itself; they never reach live
 * MAB UI.
 */
public final class MabNukeBuilderBridge {

    private final NukeBuilderAdapter adapter = new NukeBuilderAdapter();

    public BuilderNukeSpec toBuilderSpec(com.tetris.model.nuke.NukeDesign design) {
        if (design == null || design.get(NukeSlot.CONFIGURATION) == NukePart.NONE) {
            return null;
        }
        String cfg = nameOf(design.get(NukeSlot.CONFIGURATION));
        String delivery = nameOf(design.get(NukeSlot.DELIVERY));
        String tamper = nameOf(design.get(NukeSlot.TAMPER));
        String fuze = nameOf(design.get(NukeSlot.FUZE));
        double conceptualYield = Math.max(0.0, design.getEstimatedYieldKt());
        int complexity = clamp((int) Math.round(design.getComplexityScore()), 1, 10);

        NukeDoctrineType doctrine = doctrineFor(cfg, delivery, tamper, fuze, conceptualYield);
        int size = clamp((int) Math.round(12 + Math.cbrt(Math.max(1.0, conceptualYield)) * 5
                + complexity * 5), 8, 100);
        int blast = clamp((int) Math.round(1 + Math.log10(Math.max(1.0, conceptualYield)) * 2), 1, 8);
        int radiation = (doctrine == NukeDoctrineType.DIRTY_PAYLOAD
                || doctrine == NukeDoctrineType.SALTED_PAYLOAD
                || doctrine == NukeDoctrineType.DOOMSDAY)
                ? clamp(2 + complexity / 2, 1, 8)
                : clamp(1 + complexity / 4, 1, 5);
        int emp = doctrine == NukeDoctrineType.EMP_PAYLOAD
                ? clamp(4 + complexity / 2, 3, 8)
                : (doctrine == NukeDoctrineType.DOOMSDAY ? 4 : 0);
        int disarm = (doctrine == NukeDoctrineType.CONCRETE_BLASTER
                || doctrine == NukeDoctrineType.BUNKER_BUSTER)
                ? clamp(3 + complexity / 2, 1, 8)
                : (doctrine == NukeDoctrineType.EMP_PAYLOAD
                        ? clamp(2 + complexity / 3, 1, 6)
                        : clamp(1 + complexity / 5, 1, 5));
        int silo = (doctrine == NukeDoctrineType.BUNKER_BUSTER
                || doctrine == NukeDoctrineType.CONCRETE_BLASTER)
                ? clamp(3 + complexity / 2, 1, 8)
                : clamp(complexity / 4, 0, 5);
        int speed = delivery.startsWith("ICBM") || delivery.startsWith("Air-launched")
                ? 7 : (delivery.startsWith("Free-fall") ? 4 : 5);
        // Stability degrades with sheer complexity but improves with
        // safety hardware. Capped 0..10.
        String safety = nameOf(design.get(NukeSlot.SAFETY));
        int stability = clamp(7 - complexity / 2
                + (safety.startsWith("Enhanced") ? 3
                : safety.startsWith("Permissive") ? 2
                : safety.startsWith("In-flight") ? 1 : 0), 1, 10);

        boolean dirty = doctrine == NukeDoctrineType.DIRTY_PAYLOAD;
        boolean salted = doctrine == NukeDoctrineType.SALTED_PAYLOAD;
        boolean clean = doctrine == NukeDoctrineType.CLEAN_FUSION;
        boolean bunker = doctrine == NukeDoctrineType.BUNKER_BUSTER
                || doctrine == NukeDoctrineType.CONCRETE_BLASTER;
        boolean doomsday = doctrine == NukeDoctrineType.DOOMSDAY;

        return new BuilderNukeSpec(
                "builder_" + Math.abs((cfg + delivery + tamper + fuze).hashCode()),
                builderDisplayName(doctrine, conceptualYield),
                doctrine.name(),
                size,
                blast,
                radiation,
                emp,
                disarm,
                silo,
                speed,
                stability,
                complexity,
                dirty, salted, clean, bunker, doomsday);
    }

    public NukeDesign toMabDesign(com.tetris.model.nuke.NukeDesign design) {
        BuilderNukeSpec spec = toBuilderSpec(design);
        if (spec == null) return NukeDesignFactory.createDefaultPlaceholder();
        return adapter.fromBuilderSpec(spec);
    }

    public NukeDesign fromUnknownBuilderObject(Object object) {
        if (object instanceof com.tetris.model.nuke.NukeDesign educational) {
            return toMabDesign(educational);
        }
        return adapter.fromExistingBuilderObject(object);
    }

    public String safeGameplaySummary(NukeDesign design) {
        NukeDesign d = design == null ? NukeDesignFactory.createDefaultPlaceholder() : design;
        StringBuilder sb = new StringBuilder();
        sb.append(d.getDisplayName());
        sb.append(" | Size: ").append(d.getSizeCategory().name().replace('_', ' '));
        sb.append(" | Payload: ").append(d.getDoctrineType().displayLabel());
        sb.append(" | Charge ").append(d.getBaseBuildChargeRequired());
        sb.append(" | Blast ").append(d.getBlastRating());
        if (d.getRadiationRating() > 0)  sb.append(" | Rad ").append(d.getRadiationRating());
        if (d.getEmpRating() > 0)        sb.append(" | EMP ").append(d.getEmpRating());
        if (d.getDisarmRating() > 0)     sb.append(" | Disarm ").append(d.getDisarmRating());
        if (d.getSiloDamageRating() > 0) sb.append(" | Silo ").append(d.getSiloDamageRating());
        return sb.toString();
    }

    public boolean isSafeGameplaySummary(String summary) {
        if (summary == null) return false;
        String s = summary.toLowerCase();
        String[] blocked = {
                "kg", "kilogram", "u-235", "u-238", "pu-239", "plutonium",
                "uranium", "tritium", "deuterium", "lens", "initiator",
                "tamper", "fissile", "implosion", "casing", "fuze",
                "mirv", "radar", "warning", "intel", "decoy", "detection",
                "critical mass", "isotope"
        };
        for (String token : blocked) {
            if (s.contains(token)) return false;
        }
        return true;
    }

    private static NukeDoctrineType doctrineFor(String cfg, String delivery,
                                                String tamper, String fuze,
                                                double conceptualYield) {
        // Salted/cobalt-jacket choice in the builder always routes to
        // the dedicated salted-payload doctrine.
        if (cfg.startsWith("Salted")) return NukeDoctrineType.SALTED_PAYLOAD;
        if (conceptualYield > 10000) return NukeDoctrineType.DOOMSDAY;
        // Old MIRV-ish ICBM path is remapped to heavy single-payload.
        if (delivery.startsWith("ICBM")) return NukeDoctrineType.HEAVY_BLAST;
        if (delivery.startsWith("Naval") || fuze.startsWith("Contact")) {
            return NukeDoctrineType.BUNKER_BUSTER;
        }
        if (tamper.startsWith("U-238") || tamper.startsWith("Natural-uranium")
                || tamper.startsWith("Depleted-uranium")) {
            return NukeDoctrineType.DIRTY_PAYLOAD;
        }
        if (cfg.startsWith("Teller") || cfg.startsWith("Boosted")
                || cfg.startsWith("Layer-cake")) {
            return NukeDoctrineType.CLEAN_FUSION;
        }
        if (cfg.startsWith("Implosion") && conceptualYield > 50) {
            return NukeDoctrineType.HEAVY_BLAST;
        }
        return NukeDoctrineType.TACTICAL_BLAST;
    }

    private static String builderDisplayName(NukeDoctrineType doctrine, double conceptualYield) {
        String base = doctrine.displayLabel();
        if (conceptualYield <= 0) return base + " (Concept)";
        // Don't put raw kt numbers in player-facing gameplay UI — yield
        // band labels are safer and still informative.
        String band = conceptualYield < 20    ? "Tactical"
                    : conceptualYield < 200   ? "Theater"
                    : conceptualYield < 2000  ? "Strategic"
                    : conceptualYield < 10000 ? "Heavy"
                    :                            "Doomsday";
        return band + " " + base;
    }

    private static String nameOf(NukePart part) {
        return part == null || part == NukePart.NONE ? "" : part.getName();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
