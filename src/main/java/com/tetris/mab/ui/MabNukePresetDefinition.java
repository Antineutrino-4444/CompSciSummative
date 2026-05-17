package com.tetris.mab.ui;

import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.nuke.NukeDesignFactory;

import java.util.List;
import java.util.function.Supplier;

/**
 * Player-facing nuke preset entry. Wraps an existing
 * {@link NukeDesignFactory} factory method so the UI does not have to
 * construct {@link NukeDesign} records directly.
 *
 * <p>The preset ladder mirrors the current MAB doctrine model and
 * deliberately excludes MIRV / decoy / radar / warning concepts:
 * <ol>
 *   <li>Training Payload (placeholder)</li>
 *   <li>Light Tactical Blast</li>
 *   <li>Dirty Tactical Payload</li>
 *   <li>EMP Disruptor</li>
 *   <li>Bunker Buster</li>
 *   <li>Concrete Blaster</li>
 *   <li>Heavy Strategic Blast</li>
 *   <li>Clean Fusion Strategic</li>
 *   <li>Salted Payload</li>
 *   <li>Doomsday Device</li>
 * </ol>
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

    public static List<MabNukePresetDefinition> defaults() {
        return List.of(
                new MabNukePresetDefinition(
                        "Training Payload",
                        "Cheap fallback. Low charge requirement, light impact.",
                        NukeDesignFactory::createDefaultPlaceholder),
                new MabNukePresetDefinition(
                        "Light Tactical Blast",
                        "Fast build. Short launch countdown. Low radiation, modest blast.",
                        NukeDesignFactory::createDefaultTacticalBlast),
                new MabNukePresetDefinition(
                        "Dirty Tactical Payload",
                        "Tactical pressure. Heavy delayed radiation waves.",
                        NukeDesignFactory::createDefaultDirtyPayload),
                new MabNukePresetDefinition(
                        "EMP Disruptor",
                        "Low blast. Drains opponent charge, disrupts launch route.",
                        NukeDesignFactory::createDefaultEmp),
                new MabNukePresetDefinition(
                        "Bunker Buster",
                        "Targets opponent silo / infrastructure. Moderate blast.",
                        NukeDesignFactory::createDefaultBunkerBuster),
                new MabNukePresetDefinition(
                        "Concrete Blaster",
                        "Anti-nuke role. Heavy disarm and silo damage.",
                        NukeDesignFactory::createDefaultConcreteBlaster),
                new MabNukePresetDefinition(
                        "Heavy Strategic Blast",
                        "Slow to arm. Decisive single-payload blast.",
                        NukeDesignFactory::createDefaultHeavyBlast),
                new MabNukePresetDefinition(
                        "Clean Fusion Strategic",
                        "Strong clean blast, low radiation, reliable.",
                        NukeDesignFactory::createDefaultCleanFusion),
                new MabNukePresetDefinition(
                        "Salted Payload",
                        "Extreme radiation identity. Long delayed waves.",
                        NukeDesignFactory::createDefaultSaltedPayload),
                new MabNukePresetDefinition(
                        "Doomsday Device",
                        "Doomsday-scale. Very expensive charge, long timers.",
                        NukeDesignFactory::createDefaultDoomsday)
        );
    }
}
