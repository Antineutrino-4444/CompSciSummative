package com.tetris.mab.nuke;

import com.tetris.model.nuke.NukePart;
import com.tetris.model.nuke.NukeSlot;

/**
 * Safe bridge from the educational Nuke Builder model to MAB's abstract
 * strategic nuke design. It preserves the builder as a conceptual toy:
 * gameplay summaries mention size class, doctrine flavor, and broad
 * gameplay ratings, never construction instructions or component details.
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
        int radiation = (doctrine == NukeDoctrineType.DIRTY_BOMB
                || doctrine == NukeDoctrineType.SALTED_WARHEAD
                || doctrine == NukeDoctrineType.DOOMSDAY)
                ? clamp(2 + complexity / 2, 1, 8)
                : clamp(1 + complexity / 4, 1, 5);
        int disarm = (doctrine == NukeDoctrineType.EMP_PAYLOAD
                || doctrine == NukeDoctrineType.CONCRETE_BLASTER
                || doctrine == NukeDoctrineType.BUNKER_BUSTER)
                ? clamp(3 + complexity / 2, 1, 8)
                : clamp(1 + complexity / 5, 1, 5);
        int silo = (doctrine == NukeDoctrineType.BUNKER_BUSTER
                || doctrine == NukeDoctrineType.CONCRETE_BLASTER)
                ? clamp(3 + complexity / 2, 1, 8)
                : clamp(complexity / 4, 0, 5);
        int speed = delivery.startsWith("ICBM") || delivery.startsWith("Air-launched")
                ? 7 : (delivery.startsWith("Free-fall") ? 4 : 5);
        int stealth = fuze.startsWith("Programmable") ? 6 : 4;
        boolean mirv = doctrine == NukeDoctrineType.MIRV;
        boolean emp = doctrine == NukeDoctrineType.EMP_PAYLOAD;
        boolean decoy = stealth >= 7;

        return new BuilderNukeSpec(
                "builder_" + Math.abs((cfg + delivery + tamper + fuze).hashCode()),
                "Builder Concept",
                doctrine.name(),
                size,
                blast,
                radiation,
                disarm,
                silo,
                speed,
                stealth,
                mirv,
                emp,
                decoy);
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
        sb.append(" | Doctrine: ").append(doctrineLabel(d.getDoctrineType()));
        sb.append(" | Gameplay: charge ").append(d.getBaseBuildChargeRequired());
        sb.append(", blast ").append(d.getBlastRating());
        sb.append(", defense pressure ").append(d.getDisarmRating() + d.getSiloDamageRating());
        if (d.getMirvProfile() != null) sb.append(", split launch");
        if (d.getEmpProfile() != null) sb.append(", EMP profile");
        if (d.getDecoyProfile() != null) sb.append(", decoy profile");
        return sb.toString();
    }

    public boolean isSafeGameplaySummary(String summary) {
        if (summary == null) return false;
        String s = summary.toLowerCase();
        String[] blocked = {
                "kg", "kilogram", "u-235", "u-238", "pu-239", "plutonium",
                "uranium", "tritium", "deuterium", "lens", "initiator",
                "tamper", "fissile", "implosion", "casing", "fuze"
        };
        for (String token : blocked) {
            if (s.contains(token)) return false;
        }
        return true;
    }

    private static NukeDoctrineType doctrineFor(String cfg, String delivery,
                                                String tamper, String fuze,
                                                double conceptualYield) {
        if (cfg.startsWith("Salted")) return NukeDoctrineType.SALTED_WARHEAD;
        if (conceptualYield > 10000) return NukeDoctrineType.DOOMSDAY;
        if (delivery.startsWith("ICBM")) return NukeDoctrineType.MIRV;
        if (delivery.startsWith("Naval") || fuze.startsWith("Contact")) {
            return NukeDoctrineType.BUNKER_BUSTER;
        }
        if (tamper.startsWith("U-238") || tamper.startsWith("Natural-uranium")
                || tamper.startsWith("Depleted-uranium")) {
            return NukeDoctrineType.DIRTY_BOMB;
        }
        if (cfg.startsWith("Teller") || cfg.startsWith("Boosted")
                || cfg.startsWith("Layer-cake")) {
            return NukeDoctrineType.CLEAN_FUSION;
        }
        return NukeDoctrineType.PLACEHOLDER;
    }

    private static String nameOf(NukePart part) {
        return part == null || part == NukePart.NONE ? "" : part.getName();
    }

    private static String doctrineLabel(NukeDoctrineType doctrine) {
        return doctrine == null ? "DEFAULT" : doctrine.name().replace('_', ' ');
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
