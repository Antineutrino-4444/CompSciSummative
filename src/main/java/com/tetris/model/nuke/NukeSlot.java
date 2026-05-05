package com.tetris.model.nuke;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * NukeSlot.java
 * =============
 * A category of nuclear weapon subsystem the player can configure
 * (e.g. "Weapon Configuration", "Fissile Material", "Tamper / Reflector").
 *
 * Each slot owns a list of {@link NukePart} options. The catalog defined
 * here is intentionally schematic and educational — every entry maps to a
 * real, openly published concept discussed in standard references on the
 * history and physics of nuclear weapons (Rhodes' "The Making of the
 * Atomic Bomb", the Nuclear Weapon Archive FAQ, Wikipedia, etc.).
 *
 * Nothing here constitutes engineering information. The numbers are
 * order-of-magnitude pedagogy.
 */
public final class NukeSlot {

    private final String id;
    private final String name;
    private final String description;
    private final List<NukePart> options;

    private NukeSlot(String id, String name, String description, NukePart... options) {
        this.id = id;
        this.name = name;
        this.description = description;
        List<NukePart> list = new ArrayList<>();
        list.add(NukePart.NONE);
        list.addAll(Arrays.asList(options));
        this.options = Collections.unmodifiableList(list);
    }

    public String getId()                { return id; }
    public String getName()              { return name; }
    public String getDescription()       { return description; }
    public List<NukePart> getOptions()   { return options; }

    // ════════════════════════════════════════════════════════════════
    // CATALOG
    // ════════════════════════════════════════════════════════════════

    public static final NukeSlot CONFIGURATION = new NukeSlot("config",
        "Weapon Configuration",
        "The overall architecture of the device. Determines what other " +
        "subsystems are physically meaningful.",
        new NukePart("Gun-type fission",
            "Fires one sub-critical mass of HEU into another down a barrel.",
            "Historical example: 'Little Boy' (Hiroshima, 1945, ~15 kt).\n" +
            "• Conceptually the simplest design: two sub-critical pieces of " +
            "highly enriched uranium are slammed together.\n" +
            "• Only works with U-235 — Pu-239 has too high a spontaneous fission " +
            "rate and would 'pre-detonate' (fizzle) before assembly.\n" +
            "• Very inefficient (~1% of fuel actually fissions) and heavy.\n" +
            "• Considered obsolete; abandoned by all major arsenals decades ago.",
            4400, 12.0, 0.01, 1.0),
        new NukePart("Implosion fission",
            "Spherical shell of explosives compresses a sub-critical pit to super-criticality.",
            "Historical example: 'Fat Man' (Nagasaki, 1945, ~21 kt); also 'Trinity'.\n" +
            "• Required a multi-year R&D effort (Manhattan Project) to make the " +
            "shockwave symmetric enough using shaped 'explosive lenses'.\n" +
            "• Works with both Pu-239 and U-235; far more material-efficient than gun-type.\n" +
            "• Foundation of essentially every later weapon design.",
            4700, 20.0, 0.15, 2.0),
        new NukePart("Linear (cylindrical) implosion",
            "Cylindrical pit imploded along its axis — fits inside a narrow shell.",
            "Used in artillery shells (W33, W48) and some 'Davy Crockett'-class warheads. " +
            "Less efficient than spherical implosion but can be packaged into a " +
            "diameter as small as 155 mm.",
            60, 1.5, 0.06, 2.0),
        new NukePart("Boosted fission",
            "Implosion fission primary with a small fusion-fuel boost in the pit.",
            "A few grams of deuterium-tritium gas are injected into the hollow pit. " +
            "When fission begins, the D-T fuses, releasing a flood of fast neutrons " +
            "that dramatically increases the fraction of fissile material consumed " +
            "before the core blows apart.\n" +
            "• Roughly 2–5× the yield of the same primary.\n" +
            "• Standard feature of every modern fission primary.",
            3200, 50.0, 0.35, 3.0),
        new NukePart("Layer-cake (single-stage)",
            "Alternating shells of fissile and fusion fuel — Sakharov's first H-bomb.",
            "Historical: Soviet 'RDS-6s' (Joe-4, 1953, ~400 kt) and U.S. 'Alarm Clock' concept. " +
            "Boosts fission yield with neutrons from a thin fusion layer, but cannot " +
            "scale into the megaton range — that required the two-stage Teller-Ulam idea.",
            6000, 400.0, 0.30, 3.5),
        new NukePart("Two-stage (Teller-Ulam)",
            "A fission 'primary' radiatively compresses a fusion 'secondary'.",
            "Historical: 'Ivy Mike' (1952, ~10 Mt); 'Castle Bravo' (1954, ~15 Mt); " +
            "'Tsar Bomba' (1961, ~50 Mt — capable of ~100 Mt with a U-238 jacket).\n" +
            "• X-rays from the primary are channeled to compress and ignite a " +
            "physically separate fusion stage — no theoretical yield ceiling.\n" +
            "• Foundation of essentially every modern strategic warhead.\n" +
            "\n" +
            "Variants of the same Teller-Ulam architecture (selected by which " +
            "secondary tamper you install):\n" +
            "• Enhanced-radiation (\"neutron bomb\") — a thin, neutron-transparent " +
            "tamper lets prompt fusion neutrons escape. W66, W70-3, W79; designed to " +
            "kill personnel while sparing structures, retired by the 1990s.\n" +
            "• Salted (\"cobalt bomb\") — a Co-59 jacket transmutes to highly " +
            "radioactive Co-60 in the neutron flux. Proposed by Leó Szilárd in 1950 " +
            "as a doomsday thought-experiment; never built.",
            350, 300.0, 0.40, 5.0),
        new NukePart("Pure fusion (theoretical)",
            "A fusion device with no fission trigger.",
            "Has never been built. Decades of research (laser ignition, Z-pinch, etc.) " +
            "have not produced a deployable trigger. Often discussed in non-proliferation " +
            "literature because it would, in principle, sidestep the need for fissile " +
            "material entirely — making a weapon impossible to detect through fissile " +
            "material accounting.",
            100, 0.5, 0.10, 6.0)
    );

    public static final NukeSlot FISSILE = new NukeSlot("fissile",
        "Fissile Material",
        "The atoms that actually undergo the chain reaction in the primary.",
        new NukePart("HEU (~90% U-235)",
            "Uranium enriched in the rare U-235 isotope.",
            "• Natural uranium is 0.7% U-235; weapons use ~90%+.\n" +
            "• Enrichment is the historically hardest part of building a bomb — " +
            "it requires immense industrial infrastructure (gaseous diffusion, " +
            "centrifuge cascades, etc.).\n" +
            "• Bare-sphere critical mass is on the order of ~50 kg; a tamper roughly halves that.\n" +
            "• Tolerates the gun-type design because U-235 has a low spontaneous fission rate.",
            50, 0.0, 0.0, 1.0),
        new NukePart("Weapons-grade Plutonium (Pu-239)",
            "Pu-239 bred in a reactor from U-238 and chemically separated.",
            "• Bare-sphere critical mass ~10 kg; a tamper drops it to a few kg.\n" +
            "• Cannot be used in a gun-type weapon: trace Pu-240 emits enough " +
            "neutrons to trigger a fizzle before assembly is complete.\n" +
            "• Requires implosion. Standard fissile material in modern compact warheads.",
            10, 0.0, 0.0, 1.5),
        new NukePart("Reactor-grade plutonium",
            "Pu from spent civilian reactor fuel — high Pu-240/241/242 content.",
            "Officially declassified by the U.S. DOE in 1997: a workable but unreliable " +
            "weapon can be made from reactor-grade Pu. The high spontaneous fission rate " +
            "makes yield erratic and probably sub-kiloton. The point is non-proliferation: " +
            "civilian plutonium is not 'safe' from weaponization.",
            12, 0.0, -0.05, 2.0),
        new NukePart("U-233",
            "Bred from thorium-232 in a reactor.",
            "Tested by the U.S. (Operation Teapot, 1955) and India (Pokhran-II, 1998). " +
            "Bare-sphere critical mass ~16 kg. Never widely deployed because the thorium " +
            "fuel cycle proved less convenient than enrichment or Pu breeding — but it is " +
            "a recurring topic in non-proliferation analysis of thorium reactors.",
            16, 0.0, 0.0, 1.5),
        new NukePart("Composite pit (HEU + Pu-239)",
            "Layered or alloyed pit using both fissile materials.",
            "Used in the late-1940s U.S. arsenal to stretch scarce plutonium supplies. " +
            "Less efficient than a pure-Pu pit but allowed more weapons to be fielded.",
            25, 0.0, 0.05, 2.0)
    );

    public static final NukeSlot TAMPER = new NukeSlot("tamper",
        "Tamper / Reflector",
        "A dense shell around the pit. Reflects neutrons back into the core " +
        "and uses inertia to hold the assembly together for a few extra " +
        "shakes — letting more of the fissile material burn before disassembly.",
        new NukePart("Beryllium reflector",
            "Light, excellent neutron reflector.",
            "Reduces critical mass and increases efficiency by scattering escaping " +
            "neutrons back into the pit. Used in modern light-weight designs.",
            15, 0.0, 0.05, 1.5),
        new NukePart("Natural-uranium tamper",
            "Heavy U-238 shell that confines the explosion inertially.",
            "Used in 'Fat Man' and most early designs. Dense enough that the core " +
            "stays super-critical for longer; the U-238 itself can fast-fission in " +
            "the high-energy neutron flux, adding a little extra yield.",
            120, 2.0, 0.10, 1.0),
        new NukePart("Depleted-uranium tamper",
            "U-238 left over from enrichment plants — same physics as natural-U.",
            "Identical neutronics to a natural-U tamper but uses an industrial waste " +
            "stream. The default in many modern designs.",
            120, 2.0, 0.10, 1.0),
        new NukePart("Tungsten-carbide tamper",
            "Very dense, neutron-reflecting, no fission contribution.",
            "Used in some 'clean' designs where minimizing fission products is " +
            "a goal. Heavier per unit yield than U-238 but produces less fallout.",
            150, 0.0, 0.07, 1.2),
        new NukePart("Lead tamper ('clean' variant)",
            "Less dense than U-238 but contributes no fission products.",
            "Famous use: 'Tsar Bomba' (1961) substituted lead for the U-238 jacket on " +
            "its secondary, halving the yield (50 Mt vs ~100 Mt) but reducing fallout " +
            "by an order of magnitude — making it the 'cleanest' multi-megaton test.",
            200, 0.0, 0.05, 1.2),
        new NukePart("U-238 fast-fission jacket",
            "Thick U-238 shell explicitly intended to fission in the neutron flux.",
            "Common on thermonuclear secondaries: the fusion stage produces 14 MeV " +
            "neutrons that fast-fission U-238, often roughly doubling total yield. " +
            "Also the dominant source of long-lived radioactive fallout in such " +
            "weapons — hence the term 'dirty' bomb when used heavily.",
            300, 0.0, 0.15, 1.5)
    );

    public static final NukeSlot INITIATOR = new NukeSlot("initiator",
        "Neutron Initiator",
        "Provides the first burst of neutrons at exactly the right moment to " +
        "kick off the chain reaction in the assembled super-critical mass.",
        new NukePart("Polonium-Beryllium 'Urchin'",
            "Crushed at peak compression; Po-210 alphas hit Be to release neutrons.",
            "Used in the earliest implosion weapons. Reliable but Po-210 has only a " +
            "138-day half-life, requiring frequent replacement — a major logistical " +
            "headache for early arsenals.",
            0.5, 0.0, 0.05, 1.5),
        new NukePart("External pulsed-neutron tube (MNI)",
            "A miniature D-T fusion accelerator triggered electronically.",
            "Modern initiators sit outside the pit and fire a precisely-timed neutron " +
            "pulse on command. They have no radioactive decay problem and allow " +
            "very accurate timing, which improves yield reproducibility.",
            1.0, 0.0, 0.10, 2.5)
    );

    public static final NukeSlot IMPLOSION = new NukeSlot("implosion",
        "Implosion System",
        "How a spherical (or cylindrical) shockwave is generated to compress " +
        "the pit. Ignored for gun-type designs.",
        new NukePart("32-lens explosive assembly",
            "Original Manhattan Project design: 32 shaped charges of two explosive types.",
            "Historical: 'Fat Man'. Two explosives with different detonation velocities " +
            "are arranged so a divergent wave from each detonator becomes convergent " +
            "as it reaches the pit. A landmark feat of mathematics, chemistry and " +
            "machining for its day.",
            2400, 0.0, 0.0, 2.0),
        new NukePart("Refined multi-point lens system",
            "Smaller, lighter, more uniform implosion using improved explosives.",
            "Post-WWII designs progressively reduced the number and mass of lenses " +
            "while improving symmetry. Enabled the smaller, lighter primaries needed " +
            "for missile warheads.",
            900, 0.0, 0.05, 2.5),
        new NukePart("Hollow / levitated pit",
            "Hollow plutonium shell collapsed onto a void; very high compression ratio.",
            "Modern primaries use a hollow 'levitated' pit. The air gap lets the " +
            "imploding shell accelerate before impact, achieving much higher density " +
            "than solid-pit designs. Combined with boosting, this gives high yield " +
            "from very little fissile material.",
            500, 0.0, 0.15, 3.5),
        new NukePart("Two-point linear implosion",
            "Modern compact design — two detonators on opposite sides.",
            "Used in some artillery shells and the W88 primary. Achieves implosion " +
            "with only two detonation points, allowing extremely small-diameter " +
            "warheads at the cost of more sophisticated explosive shaping.",
            300, 0.0, 0.10, 4.0)
    );

    public static final NukeSlot BOOST = new NukeSlot("boost",
        "Boost Gas",
        "Optional fusion fuel injected into the primary's pit.",
        new NukePart("Deuterium-Tritium gas",
            "A few grams of D-T injected into the hollow pit just before firing.",
            "When the fission reaction heats the pit interior to tens of millions of " +
            "kelvin, the D-T fuses. The fusion produces almost no extra yield by " +
            "itself, but each fusion releases a 14 MeV neutron — a flood of these " +
            "drives the fission reaction much further before the core disassembles. " +
            "Result: 2–5× yield from the same fissile mass.\n" +
            "Tritium has a 12.3-year half-life, so boost gas must be replaced " +
            "every few years — a major arsenal-maintenance cost.",
            0.005, 0.0, 0.20, 2.0),
        new NukePart("Pure-deuterium boost",
            "Cheaper substitute — much weaker boosting effect.",
            "Used in some early experimental designs before tritium production caught up. " +
            "Deuterium-deuterium fusion has a much higher ignition temperature than " +
            "D-T, so the boost effect is small.",
            0.005, 0.0, 0.05, 1.5)
    );

    public static final NukeSlot SECONDARY = new NukeSlot("secondary",
        "Fusion Secondary",
        "A physically separate fusion stage compressed by X-rays from the primary. " +
        "Only meaningful for two-stage thermonuclear configurations.",
        new NukePart("Cryogenic liquid deuterium",
            "Original 'wet' design — required refrigeration.",
            "Historical: 'Ivy Mike' (1952) used a building-sized cryogenic apparatus " +
            "to keep liquid deuterium cold. It worked (~10 Mt) but was not deployable " +
            "as a weapon — it weighed roughly 65 tons.",
            60000, 10000.0, 0.30, 4.0),
        new NukePart("Lithium-6 deuteride (dry)",
            "Solid 'dry' fuel: Li-6 captures a neutron to make tritium in-situ.",
            "Historical: 'Castle Bravo' (1954) — yielded ~15 Mt, more than double the " +
            "predicted 6 Mt because the designers had assumed Li-7 would be inert. " +
            "It wasn't. Standard fusion fuel ever since: storable at room temperature, " +
            "much lighter, weaponizable.",
            200, 1000.0, 0.40, 4.0),
        new NukePart("Natural LiD (Li-6 + Li-7)",
            "Cheaper, less enriched fusion fuel.",
            "The 'Castle Bravo' surprise came from Li-7 fissioning under fast neutrons. " +
            "Modern arsenals use highly Li-6-enriched fuel to make yield predictable; " +
            "natural-Li fuel is now mostly of historical interest.",
            220, 800.0, 0.30, 3.5),
        new NukePart("Spark-plug enhanced secondary",
            "Adds a small Pu rod through the centre of the secondary to ignite fusion.",
            "Standard component of modern Teller-Ulam designs: when the secondary is " +
            "compressed, the spark plug goes super-critical and provides the heat " +
            "needed to ignite fusion in the surrounding Li-D fuel.",
            500, 1500.0, 0.50, 5.0)
    );

    public static final NukeSlot CASING = new NukeSlot("casing",
        "Casing / Aeroshell",
        "The structural and aerodynamic outer shell. Drives delivery options and " +
        "provides the radiation case that channels X-rays in a Teller-Ulam design.",
        new NukePart("Mk-III ellipsoidal casing",
            "WWII-era 'Fat Man' shape — bulbous, draggy, built for low-altitude release.",
            "Famous from the Nagasaki bomb. Held together by riveted aluminum plates " +
            "with a sub-hemisphere of explosive lenses inside.",
            500, 0.0, 0.0, 1.0),
        new NukePart("Streamlined gravity-bomb casing",
            "Aerodynamic, fin-stabilized — the classic 'iron bomb' silhouette.",
            "Used by the B61, B83 and similar free-fall weapons. Includes a parachute " +
            "or retarder so the delivering aircraft can escape the blast.",
            350, 0.0, 0.0, 1.5),
        new NukePart("Re-entry vehicle (RV) aeroshell",
            "Conical body with an ablative heat shield — survives ICBM re-entry.",
            "MIRV warheads (W76, W78, W87, W88) live inside narrow conical RVs only " +
            "tens of cm across. The mass and diameter limits drive the use of compact " +
            "boosted primaries.",
            150, 0.0, 0.0, 2.5),
        new NukePart("Cylindrical artillery shell body",
            "Fits a standard 155 mm or 203 mm howitzer.",
            "W33, W48, W79. Forces the use of linear implosion or gun-type assembly. " +
            "Now retired by treaty in most arsenals.",
            50, 0.0, 0.0, 2.0),
        new NukePart("Radiation case (uranium liner)",
            "Outer Teller-Ulam case lined with heavy metal to channel X-rays.",
            "Required for any two-stage thermonuclear design — the case must be opaque " +
            "to thermal X-rays for long enough that radiation pressure can compress " +
            "the secondary before the case itself blows apart.",
            300, 0.0, 0.05, 3.0)
    );

    public static final NukeSlot FUZE = new NukeSlot("fuze",
        "Fuze",
        "Decides when the device fires. The wrong fuze choice can waste most " +
        "of the yield — a contact fuze on a city target produces a small " +
        "crater and a lot of fallout instead of a massive blast wave.",
        new NukePart("Contact fuze",
            "Detonates on impact.",
            "Maximizes ground shock and fallout but wastes most of the blast and " +
            "thermal effects: an airburst is far more destructive against soft " +
            "(structural and biological) targets. Mainly useful against deeply " +
            "buried hardened targets — though even then a delayed fuze in a " +
            "penetrator is preferred.",
            5, 0.0, -0.10, 1.0),
        new NukePart("Radar airburst fuze",
            "Detonates at a pre-set altitude using a downward-looking radar altimeter.",
            "First used on 'Little Boy' and 'Fat Man' — both burst at ~500–600 m for " +
            "maximum blast radius. The Mach-stem reflection at the right altitude " +
            "doubles the area of severe overpressure at ground level.",
            8, 0.0, 0.10, 2.0),
        new NukePart("Programmable height-of-burst fuze",
            "Modern digital fuze with selectable burst height and ground-burst options.",
            "Standard in modern warheads. Lets one warhead serve multiple missions " +
            "(soft-target airburst, hard-target ground burst, low-altitude tactical, etc.).",
            6, 0.0, 0.10, 3.0),
        new NukePart("Hydrostatic fuze (depth charge)",
            "Detonates at a preset water depth.",
            "Used on naval anti-submarine weapons such as the U.S. B57 / Mk 101 'Lulu'. " +
            "Now retired in favor of conventional torpedoes.",
            10, 0.0, 0.0, 2.0)
    );

    public static final NukeSlot SAFETY = new NukeSlot("safety",
        "Safety / Use-control",
        "Mechanisms that prevent accidental or unauthorized detonation. " +
        "Adds no yield — it only adds safety.",
        new NukePart("None (early-arsenal style)",
            "No active safeties beyond the firing-circuit interlock.",
            "Pre-1960 weapons had alarmingly few safeties. The 1961 Goldsboro B-52 " +
            "crash and the 1966 Palomares incident drove a complete redesign of " +
            "U.S. safety standards.",
            0, 0.0, 0.0, 0.5),
        new NukePart("In-flight Insertable Pit (IFI)",
            "Pit physically removed during transport; inserted only before use.",
            "Used on the B-36 / B-47 era weapons. Made an accidental nuclear yield " +
            "physically impossible during flight — at the cost of needing a " +
            "weaponeer to climb into the bomb bay before the mission.",
            20, 0.0, 0.0, 1.5),
        new NukePart("Permissive Action Link (PAL)",
            "Coded electronic lock — won't arm without an authorized release code.",
            "Introduced from the early 1960s onward. Modern PALs use multi-digit codes " +
            "and 'limited-try' lockouts that disable the warhead after a few wrong " +
            "attempts. Required by U.S. law on all forward-deployed warheads.",
            5, 0.0, 0.0, 2.0),
        new NukePart("Enhanced Nuclear Detonation Safety (ENDS)",
            "Multiple independent 'strong-link / weak-link' interlocks.",
            "Modern design philosophy: an 'abnormal environment' (fire, crash, " +
            "lightning, etc.) breaks weak-link safeties before any strong-link " +
            "arming circuit can complete. The probability of an accidental " +
            "nuclear yield is engineered to be below 1 in a billion per warhead-year.",
            10, 0.0, 0.0, 3.0)
    );

    public static final NukeSlot DELIVERY = new NukeSlot("delivery",
        "Delivery System",
        "How the device is intended to reach a target. Drives mass and " +
        "diameter constraints. Educational only — modern arms-control treaties " +
        "place strict limits on real systems in this category.",
        new NukePart("Free-fall gravity bomb",
            "Dropped from an aircraft.",
            "Mass budget is generous (hundreds of kg to a few tons). Historical " +
            "examples: B61, B83. Most flexible mission profile but vulnerable to " +
            "modern air defenses.",
            0, 0.0, 0.0, 1.0),
        new NukePart("ICBM / SLBM re-entry vehicle",
            "Compact warhead atop an intercontinental or submarine-launched ballistic missile.",
            "Strict mass and diameter limits drive the use of boosted, hollow-pit " +
            "primaries. Modern MIRV (Multiple Independently-targetable Re-entry " +
            "Vehicle) buses can carry several such warheads on one missile — a key " +
            "subject of the New START treaty.",
            0, 0.0, 0.0, 2.0),
        new NukePart("Air-launched cruise missile",
            "Subsonic stand-off weapon with a small warhead.",
            "Trades yield for stand-off range so the launching aircraft never has to " +
            "enter defended airspace. Examples: AGM-86, AGM-129, Russian Kh-55.",
            0, 0.0, 0.0, 1.5),
        new NukePart("Artillery shell",
            "Sub-kiloton to low-kiloton 'battlefield' device.",
            "W48 (155 mm, ~0.07 kt), W33 (203 mm, ~5–10 kt). Largely retired by " +
            "treaty in the 1990s — the safety case for nuclear artillery never " +
            "really closed.",
            0, 0.0, 0.0, 2.5),
        new NukePart("Recoilless rifle (Davy Crockett)",
            "Infamous infantry-launched ~20-ton-yield warhead.",
            "M28/M29 'Davy Crockett' (1961–71): a tripod-mounted recoilless gun firing " +
            "the W54 warhead. Range ~2–4 km — close enough that the crew was at risk " +
            "from prompt radiation. Considered the high-water mark of unwise " +
            "tactical-nuclear integration.",
            0, 0.0, 0.0, 3.0),
        new NukePart("Special Atomic Demolition Munition (SADM)",
            "Backpack-portable W54 variant for sabotage missions.",
            "U.S. Special Forces 'Green Light' teams trained from the 1960s through " +
            "the 1980s to parachute behind enemy lines and emplace SADMs against " +
            "bridges, tunnels and airfields. Retired in 1989 — almost universally " +
            "regarded today as a Cold War policy mistake.",
            0, 0.0, 0.0, 3.0),
        new NukePart("Air-to-air rocket (Genie)",
            "Unguided rocket fired from interceptor aircraft at bomber formations.",
            "AIR-2 'Genie' (1957–88) carried a 1.5 kt W25 warhead. Tested at very low " +
            "altitude over volunteer ground observers (Plumbbob 'John', 1957) to " +
            "demonstrate it was 'safe' to use over populated areas — by the standards " +
            "of the day.",
            0, 0.0, 0.0, 2.0),
        new NukePart("Naval depth charge",
            "Anti-submarine weapon dropped from ship or aircraft.",
            "U.S. B57 / 'Lulu' / 'Betty', Soviet RYu-2. Retired by both sides in the " +
            "1990s after the Presidential Nuclear Initiatives — conventional " +
            "torpedoes proved more effective and far less politically fraught.",
            0, 0.0, 0.0, 2.0),
        new NukePart("Test-stand only (not deliverable)",
            "Device is too heavy or fragile for any delivery vehicle.",
            "Many record-setting devices ('Ivy Mike', the 100 Mt 'Tsar Bomba' design) " +
            "were never deployable — they were experiments to prove a physical concept.",
            0, 0.0, 0.0, 1.0)
    );

    /** All slots in the order shown in the UI. */
    public static final List<NukeSlot> ALL = Collections.unmodifiableList(Arrays.asList(
        CONFIGURATION, FISSILE, TAMPER, INITIATOR, IMPLOSION,
        BOOST, SECONDARY, FUZE
    ));
}
