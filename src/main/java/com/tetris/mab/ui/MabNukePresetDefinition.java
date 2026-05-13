package com.tetris.mab.ui;

import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.nuke.NukeDesignFactory;

import java.util.List;
import java.util.function.Supplier;

/**
 * Step 16 — player-facing nuke preset entry. Wraps an existing
 * {@link NukeDesignFactory} factory method so the UI does not have to
 * construct {@link NukeDesign} records directly (the schema has many
 * required fields and DEFCON tables that were tuned in Step 3).
 *
 * <p>Custom point-and-click design construction is intentionally
 * deferred — see Step16.md, "intentionally not implemented yet".
 *
 * <p><b>Offline-only.</b>
 */
public final class MabNukePresetDefinition {

    private final String displayName;
    private final String description;
    private final Supplier<NukeDesign> factory;

    public MabNukePresetDefinition(String displayName,
                                   String description,
                                   Supplier<NukeDesign> factory) {
        if (displayName == null || displayName.isBlank())
            throw new IllegalArgumentException("displayName");
        if (factory == null) throw new IllegalArgumentException("factory");
        this.displayName = displayName;
        this.description = description == null ? "" : description;
        this.factory = factory;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    /** Builds a fresh {@link NukeDesign} instance. */
    public NukeDesign toNukeDesign() { return factory.get(); }

    @Override public String toString() { return displayName; }

    // ─────────────────────── Built-in presets ────────────────

    /**
     * Returns the static preset list shown in the redesign panel. Uses
     * existing {@link NukeDesignFactory} entries because their DEFCON
     * tables and validation are already covered by Step 3 and the
     * smoke simulation.
     */
    public static List<MabNukePresetDefinition> defaults() {
        return List.of(
                new MabNukePresetDefinition(
                        "Placeholder Tactical",
                        "Cheap fallback. Low yield, simple action code.",
                        NukeDesignFactory::createDefaultPlaceholder),
                new MabNukePresetDefinition(
                        "Clean Fusion Strategic",
                        "High blast, low radiation. Strategic-class doctrine.",
                        NukeDesignFactory::createDefaultCleanFusion),
                new MabNukePresetDefinition(
                        "Dirty Tactical",
                        "Tactical pressure. Heavy radiation, modest blast.",
                        NukeDesignFactory::createDefaultDirtyBomb),
                new MabNukePresetDefinition(
                        "Concrete Blaster",
                        "Theater-class silo breaker. Heavy disarm + silo damage.",
                        NukeDesignFactory::createDefaultConcreteBlaster),
                new MabNukePresetDefinition(
                        "MIRV Package",
                        "Strategic MIRV. Multiple impact signatures.",
                        NukeDesignFactory::createDefaultMirv),
                new MabNukePresetDefinition(
                        "EMP Special",
                        "EMP-doctrine non-radiation strike.",
                        NukeDesignFactory::createDefaultEmp),
                new MabNukePresetDefinition(
                        "Bunker Buster",
                        "Deep-silo damage focus.",
                        NukeDesignFactory::createDefaultBunkerBuster),
                new MabNukePresetDefinition(
                        "Doomsday Demonstrator",
                        "Doomsday-scale. Long timers, very expensive to charge.",
                        NukeDesignFactory::createDefaultDoomsday)
        );
    }
}
