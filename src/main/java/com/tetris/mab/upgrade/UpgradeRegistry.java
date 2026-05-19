package com.tetris.mab.upgrade;

import com.tetris.mab.UpgradeState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Static lookup of all known {@link UpgradeDefinition}s. Construct via
 * {@link #createDefault()} which seeds the Step-9 defaults.
 */
public final class UpgradeRegistry {

    private final Map<UpgradeType, UpgradeDefinition> byType =
            new EnumMap<>(UpgradeType.class);

    private UpgradeRegistry() {}

    public static UpgradeRegistry createDefault() {
        UpgradeRegistry r = new UpgradeRegistry();
        // Silo systems
        r.add(new UpgradeDefinition(UpgradeType.HARDENED_SILO, "hardened_silo",
                "Hardened Silo", UpgradeCategory.SILO_SYSTEMS, 3, 1, 5, List.of(),
                "Reduces disarm and silo damage taken."));
        r.add(new UpgradeDefinition(UpgradeType.DEEP_BUNKER, "deep_bunker",
                "Deep Bunker", UpgradeCategory.SILO_SYSTEMS, 2, 2, 4,
                List.of(UpgradeType.HARDENED_SILO),
                "Stronger silo protection against concrete/blaster-style attacks."));
        r.add(new UpgradeDefinition(UpgradeType.DISTRIBUTED_STOCKPILE, "distributed_stockpile",
                "Distributed Stockpile", UpgradeCategory.SILO_SYSTEMS, 2, 2, 4, List.of(),
                "Reduces nuke-charge loss from disarm damage."));
        r.add(new UpgradeDefinition(UpgradeType.RAPID_ASSEMBLY_LINE, "rapid_assembly_line",
                "Rapid Assembly Line", UpgradeCategory.SILO_SYSTEMS, 3, 1, 5, List.of(),
                "Improves rebuild tempo after launch or disarm."));
        r.add(new UpgradeDefinition(UpgradeType.SECURE_LAUNCH_CHAIN, "secure_launch_chain",
                "Secure Launch Chain", UpgradeCategory.SILO_SYSTEMS, 2, 2, 3, List.of(),
                "Later reduces code disruption and launch compromise."));
        r.add(new UpgradeDefinition(UpgradeType.BLAST_DOORS, "blast_doors",
                "Blast Doors", UpgradeCategory.SILO_SYSTEMS, 2, 2, 4, List.of(),
                "Reduces immediate silo integrity loss."));
        r.add(new UpgradeDefinition(UpgradeType.SILO_CAMOUFLAGE, "silo_camouflage",
                "Silo Camouflage", UpgradeCategory.SILO_SYSTEMS, 2, 1, 5, List.of(),
                "Later affects launch visibility."));

        // Defense
        r.add(new UpgradeDefinition(UpgradeType.SHELTERS, "shelters",
                "Shelters", UpgradeCategory.DEFENSE, 3, 1, 5, List.of(),
                "Improves civil defense mitigation."));
        r.add(new UpgradeDefinition(UpgradeType.GARBAGE_CONTROL, "garbage_control",
                "Garbage Control", UpgradeCategory.DEFENSE, 3, 1, 5, List.of(),
                "Adds grace headroom and reduces immediate garbage."));
        r.add(new UpgradeDefinition(UpgradeType.EMERGENCY_PROTOCOLS, "emergency_protocols",
                "Emergency Protocols", UpgradeCategory.DEFENSE, 2, 2, 3,
                List.of(UpgradeType.SHELTERS),
                "Enables stronger emergency civil defense later."));
        r.add(new UpgradeDefinition(UpgradeType.INTERCEPT_CREWS, "intercept_crews",
                "Intercept Crews", UpgradeCategory.DEFENSE, 2, 2, 4, List.of(),
                "Later improves intercept power."));

        // Launch systems
        r.add(new UpgradeDefinition(UpgradeType.RAPID_LAUNCH_DRILLS, "rapid_launch_drills",
                "Rapid Launch Drills", UpgradeCategory.LAUNCH_SYSTEMS, 2, 2, 4, List.of(),
                "Later reduces launch countdown by small amounts."));
        r.add(new UpgradeDefinition(UpgradeType.SECURE_AUTHORIZATION, "secure_authorization",
                "Secure Authorization", UpgradeCategory.LAUNCH_SYSTEMS, 2, 2, 3, List.of(),
                "Later protects against launch-chain compromise."));
        r.add(new UpgradeDefinition(UpgradeType.COUNTDOWN_AUTOMATION, "countdown_automation",
                "Countdown Automation", UpgradeCategory.LAUNCH_SYSTEMS, 2, 2, 2, List.of(),
                "Later improves late-DEFCON launch tempo."));

        // Threat tracking
        r.add(new UpgradeDefinition(UpgradeType.EARLY_WARNING_RADAR, "early_warning_radar",
                "Threat Sensor Grid", UpgradeCategory.WARNING_RADAR, 3, 1, 5, List.of(),
                "Later improves threat tracking and route clarity."));
        r.add(new UpgradeDefinition(UpgradeType.SIGNAL_ANALYSIS, "signal_analysis",
                "Signal Analysis", UpgradeCategory.WARNING_RADAR, 2, 2, 4,
                List.of(UpgradeType.EARLY_WARNING_RADAR),
                "Later improves doctrine identification."));
        r.add(new UpgradeDefinition(UpgradeType.THREAT_TRACKING, "threat_tracking",
                "Threat Tracking", UpgradeCategory.WARNING_RADAR, 2, 2, 3,
                List.of(UpgradeType.EARLY_WARNING_RADAR),
                "Later improves intercept targeting."));

        // Tempo
        r.add(new UpgradeDefinition(UpgradeType.BUILD_EFFICIENCY, "build_efficiency",
                "Build Efficiency", UpgradeCategory.TEMPO_SYSTEMS, 3, 1, 5, List.of(),
                "Adds small bonus charge from line clears."));
        r.add(new UpgradeDefinition(UpgradeType.LINE_CLEAR_LOGISTICS, "line_clear_logistics",
                "Line Clear Logistics", UpgradeCategory.TEMPO_SYSTEMS, 2, 2, 5, List.of(),
                "Later improves reward for complex clears."));

        // Nuke design
        r.add(new UpgradeDefinition(UpgradeType.WARHEAD_REFINEMENT, "warhead_refinement",
                "Warhead Refinement", UpgradeCategory.NUKE_DESIGN, 3, 1, 5, List.of(),
                "Later improves design efficiency."));
        r.add(new UpgradeDefinition(UpgradeType.CLEANER_FUSION, "cleaner_fusion",
                "Cleaner Fusion", UpgradeCategory.NUKE_DESIGN, 2, 2, 4, List.of(),
                "Later supports lower-radiation high-blast designs."));
        r.add(new UpgradeDefinition(UpgradeType.DIRTY_PAYLOAD_ENGINEERING, "dirty_payload",
                "Dirty Payload Engineering", UpgradeCategory.NUKE_DESIGN, 2, 2, 4, List.of(),
                "Later supports higher messiness payloads."));
        r.add(new UpgradeDefinition(UpgradeType.PENETRATION_PACKAGE, "penetration_package",
                "Penetration Package", UpgradeCategory.NUKE_DESIGN, 2, 2, 3, List.of(),
                "Later improves anti-silo payloads."));

        // MAD systems
        r.add(new UpgradeDefinition(UpgradeType.SECOND_STRIKE_DOCTRINE_I, "second_strike_doctrine_i",
                "Second-Strike Doctrine I", UpgradeCategory.MAD_SYSTEMS, 1, 2, 3, List.of(),
                "Placeholder for later manual second-strike behavior."));
        r.add(new UpgradeDefinition(UpgradeType.SECOND_STRIKE_DOCTRINE_II, "second_strike_doctrine_ii",
                "Second-Strike Doctrine II", UpgradeCategory.MAD_SYSTEMS, 1, 3, 2,
                List.of(UpgradeType.SECOND_STRIKE_DOCTRINE_I),
                "Placeholder for later stronger second-strike behavior."));
        r.add(new UpgradeDefinition(UpgradeType.DEAD_HAND_PROTOCOL, "dead_hand_protocol",
                "Dead Hand Protocol", UpgradeCategory.MAD_SYSTEMS, 1, 4, 1,
                List.of(UpgradeType.SECOND_STRIKE_DOCTRINE_II),
                "Placeholder for later automatic retaliation behavior."));
        r.add(new UpgradeDefinition(UpgradeType.ASSURED_RETALIATION, "assured_retaliation",
                "Assured Retaliation", UpgradeCategory.MAD_SYSTEMS, 1, 3, 2,
                List.of(UpgradeType.SECOND_STRIKE_DOCTRINE_I),
                "Placeholder for later retaliation bonuses."));
        return r;
    }

    private void add(UpgradeDefinition def) { byType.put(def.type(), def); }

    public UpgradeDefinition get(UpgradeType type) { return type == null ? null : byType.get(type); }

    public List<UpgradeDefinition> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(byType.values()));
    }

    public List<UpgradeDefinition> getByCategory(UpgradeCategory category) {
        List<UpgradeDefinition> out = new ArrayList<>();
        for (UpgradeDefinition d : byType.values()) {
            if (d.category() == category) out.add(d);
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Definitions currently selectable for {@code state} at DEFCON
     * {@code defconLevel}: not maxed, prerequisites met, DEFCON gate
     * permits the upgrade. Affordability is intentionally NOT filtered
     * here — UI may want to show unaffordable choices greyed out.
     */
    public List<UpgradeDefinition> getAvailableFor(UpgradeState state, int defconLevel) {
        List<UpgradeDefinition> out = new ArrayList<>();
        if (state == null) return out;
        for (UpgradeDefinition d : byType.values()) {
            if (defconLevel > d.requiredDefconMaximum()) continue;
            if (state.isMaxed(d)) continue;
            if (!state.hasPrerequisites(d)) continue;
            out.add(d);
        }
        return Collections.unmodifiableList(out);
    }
}
