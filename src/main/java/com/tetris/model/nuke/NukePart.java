package com.tetris.model.nuke;

/**
 * NukePart.java
 * =============
 * A single selectable component option for the educational Nuke Builder.
 *
 * Each part belongs to a {@link NukeSlot} (e.g. "Fissile Material") and
 * carries human-readable educational text plus a few abstract "stats"
 * used for back-of-the-envelope yield estimation. The numbers are
 * deliberately coarse pedagogical figures drawn from publicly available
 * sources (Wikipedia "Nuclear weapon design", Atomic Heritage Foundation,
 * the Nuclear Weapon Archive's FAQ, etc.). They are NOT engineering data.
 *
 * The Nuke Builder is intended purely as a learning tool — to help a
 * player understand what the major subsystems of a nuclear device are,
 * how they relate historically, and what tradeoffs (efficiency, mass,
 * yield, complexity) drove the development of the field.
 */
public final class NukePart {

    public static final NukePart NONE = new NukePart(
            "— none —",
            "No component selected for this slot.",
            "",
            0.0, 0.0, 0.0, 1.0);

    private final String name;
    private final String shortDescription;
    private final String educationalNotes;

    /** Mass contribution in kilograms (rough). */
    private final double massKg;

    /** Base yield contribution in kilotons of TNT equivalent (rough). */
    private final double baseYieldKt;

    /** Efficiency contribution (fraction of fuel that fissions/fuses). */
    private final double efficiencyBonus;

    /** Multiplicative complexity factor (used for "design complexity" score). */
    private final double complexity;

    public NukePart(String name, String shortDescription, String educationalNotes,
                    double massKg, double baseYieldKt,
                    double efficiencyBonus, double complexity) {
        this.name = name;
        this.shortDescription = shortDescription;
        this.educationalNotes = educationalNotes;
        this.massKg = massKg;
        this.baseYieldKt = baseYieldKt;
        this.efficiencyBonus = efficiencyBonus;
        this.complexity = complexity;
    }

    public String getName()             { return name; }
    public String getShortDescription() { return shortDescription; }
    public String getEducationalNotes() { return educationalNotes; }
    public double getMassKg()           { return massKg; }
    public double getBaseYieldKt()      { return baseYieldKt; }
    public double getEfficiencyBonus()  { return efficiencyBonus; }
    public double getComplexity()       { return complexity; }

    @Override
    public String toString() { return name; }
}
