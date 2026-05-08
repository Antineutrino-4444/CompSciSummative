package com.tetris.model.nuke;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NukeDesign.java
 * ===============
 * Holds the player's current selection across all {@link NukeSlot}s and
 * computes a back-of-the-envelope summary: estimated yield,
 * design complexity score, a textual description, and a list of
 * compatibility warnings explaining why certain combinations would fail
 * in real life (e.g. plutonium in a gun-type weapon).
 *
 * All numbers are coarse pedagogical estimates only.
 */
public final class NukeDesign {

    private final Map<NukeSlot, NukePart> selections = new LinkedHashMap<>();

    /** Cosmetic + numerical tuning of a Teller-Ulam secondary stage.
     *  Only meaningful when CONFIGURATION is a Two-stage thermonuclear
     *  design — ignored otherwise. Each fusion stage (secondary,
     *  optional tertiary, optional quaternary) carries its own choice
     *  of pusher / channel filler / fuel / spark plug, indexed by
     *  stage number 0..2. Mirrors the inline FUSION DESIGN column in
     *  NukeBuilderDialog. */
    public static final int MAX_FUSION_STAGES = 3;
    private final String[]  fusionPusher        = { "U-238", "U-238", "U-238" };
    private final String[]  fusionChannelFiller = { "Polystyrene foam", "Polystyrene foam", "Polystyrene foam" };
    private final boolean[] fusionSparkPlug     = { true, false, false };
    /** Number of fusion stages: 1 = standard Teller-Ulam (secondary only),
     *  2 = three-stage (adds a U-238 jacketed tertiary, fission-fusion-
     *  fission, ≈ Castle Bravo), 3 = four-stage (adds a quaternary —
     *  hypothetical, beyond any real device). Each extra stage roughly
     *  doubles total yield but multiplies fallout. */
    private int     fusionStageCount    = 1;

    public NukeDesign() {
        for (NukeSlot s : NukeSlot.ALL) {
            selections.put(s, NukePart.NONE);
        }
    }

    public NukePart get(NukeSlot slot) {
        return selections.getOrDefault(slot, NukePart.NONE);
    }

    public void set(NukeSlot slot, NukePart part) {
        selections.put(slot, part);
    }

    public void reset() {
        for (NukeSlot s : NukeSlot.ALL) selections.put(s, NukePart.NONE);
        for (int i = 0; i < MAX_FUSION_STAGES; i++) {
            fusionPusher[i]        = "U-238";
            fusionChannelFiller[i] = "Polystyrene foam";
            fusionSparkPlug[i]     = (i == 0); // only the secondary has a spark plug by default
        }
        fusionStageCount    = 1;
    }

    // ─────────── Fusion tuning ( Teller-Ulam only ) ─────────

    public void setFusionPusher(int stage, String v)        { if (stage>=0 && stage<MAX_FUSION_STAGES) fusionPusher[stage]        = v; }
    public void setFusionChannelFiller(int stage, String v) { if (stage>=0 && stage<MAX_FUSION_STAGES) fusionChannelFiller[stage] = v; }
    public void setFusionSparkPlug(int stage, boolean v)    { if (stage>=0 && stage<MAX_FUSION_STAGES) fusionSparkPlug[stage]     = v; }
    public void setFusionStageCount(int n)                  { fusionStageCount = Math.max(1, Math.min(MAX_FUSION_STAGES, n)); }

    public String  getFusionPusher(int stage)        { return (stage>=0 && stage<MAX_FUSION_STAGES) ? fusionPusher[stage]        : "U-238"; }
    public String  getFusionChannelFiller(int stage) { return (stage>=0 && stage<MAX_FUSION_STAGES) ? fusionChannelFiller[stage] : "Polystyrene foam"; }
    public boolean getFusionSparkPlug(int stage)     { return (stage>=0 && stage<MAX_FUSION_STAGES) && fusionSparkPlug[stage]; }
    public int     getFusionStageCount()             { return fusionStageCount; }
    /** True when the design has at least one extra fusion stage beyond
     *  the standard Teller-Ulam secondary (i.e. it is a 3-stage or
     *  4-stage weapon). Preserved as the old name for the schematic
     *  painter, which draws an outer U-238 jacket whenever this is set. */
    public boolean getFusionTertiary()      { return fusionStageCount >= 2; }

    /** True only when fusion tuning actually affects the yield model. */
    private boolean isTwoStage() {
        NukePart cfg = get(NukeSlot.CONFIGURATION);
        if (cfg == NukePart.NONE) return false;
        String n = cfg.getName();
        return n.startsWith("Teller-Ulam") || n.startsWith("Enhanced") || n.startsWith("Salted");
    }

    /** Multiplicative yield modifier from the fusion sub-designer.
     *  Returns 1.0 when not in a two-stage configuration so it has no
     *  effect on fission-only designs. Coarse pedagogical numbers.
     *  Each fusion stage carries its own pusher / channel / spark-plug
     *  choices; this loop multiplies their contributions together. */
    private double fusionYieldMultiplier() {
        if (!isTwoStage()) return 1.0;
        double m = 1.0;
        for (int i = 0; i < fusionStageCount; i++) {
            // Pusher / tamper material wrapped around this stage.
            switch (fusionPusher[i]) {
                case "U-238"    -> m *= 1.30; // fast-fission jacket adds yield
                case "Lead"     -> m *= 0.83; // inert, cleaner
                case "Tungsten" -> m *= 0.90; // inert + dense, slightly better confinement
            }
            // Radiation-channel filler in front of this stage.
            switch (fusionChannelFiller[i]) {
                case "Polystyrene foam" -> m *= 1.00; // canonical baseline
                case "Vacuum"           -> m *= 1.05; // theoretical, slightly higher coupling
            }
            // Spark plug. The secondary (i == 0) relies on its
            // Pu-239 rod for reliable ignition, so the bonus/penalty
            // there is large. Higher stages are already neutron-flooded
            // by the previous stage, so a spark plug there is a smaller
            // ignition assist (and a small efficiency loss if omitted).
            if (i == 0) {
                if (fusionSparkPlug[0]) m *= 1.20;
                else                    m *= 0.70;
            } else {
                if (fusionSparkPlug[i]) m *= 1.05;
                else                    m *= 0.95;
            }
            // Stage cascade: each successive stage roughly doubles yield
            // (the canonical "3-stage" U-238 jacket trick — Castle
            // Bravo, 1954). The hypothetical 4-stage adds ×1.5 more.
            if (i >= 1) m *= (i == 1) ? 6.00 : 1.50;
        }
        return m;
    }

    // ─────────── Derived statistics ───────────

    public double getComplexityScore() {
        double c = 0;
        for (NukePart p : selections.values()) {
            if (p != NukePart.NONE) c += p.getComplexity();
        }
        return c;
    }

    /**
     * Very rough yield estimate in kilotons of TNT equivalent.
     * Combines a base yield from each component with an efficiency
     * multiplier that boosting/secondary stages amplify.
     */
    public double getEstimatedYieldKt() {
        NukePart cfg = get(NukeSlot.CONFIGURATION);
        if (cfg == NukePart.NONE) return 0;

        String  cn          = cfg.getName();
        boolean isBoostCfg  = cn.startsWith("Boosted");
        boolean isSloika    = cn.startsWith("Layer-cake");
        boolean isThermoCfg = cn.startsWith("Teller-Ulam")
                           || cn.startsWith("Enhanced")
                           || cn.startsWith("Salted");
        // Designs in which D-T boost is physically meaningful: the chain
        // reaction has to still be building when the fusion neutrons
        // arrive. True for boosted-fission primaries, layer-cake, and
        // any thermonuclear primary; false for raw gun-type or simple
        // Fat-Man-class implosion.
        boolean boostApplies = isBoostCfg || isSloika || isThermoCfg;

        NukePart fissile = get(NukeSlot.FISSILE);
        NukePart tamper  = get(NukeSlot.TAMPER);
        NukePart initP   = get(NukeSlot.INITIATOR);
        NukePart boost   = get(NukeSlot.BOOST);
        NukePart sec     = get(NukeSlot.SECONDARY);

        // Anti-synergy #6: a Teller-Ulam secondary doesn't ignite without
        // sufficient X-ray flux from a working primary. If the primary
        // is missing fissile / implosion / initiator, the secondary is
        // dead — its base and efficiency contributions are zeroed and a
        // FIZZLE warning is emitted by getWarnings().
        boolean secondaryFails = isThermoCfg && (
                fissile == NukePart.NONE
             || get(NukeSlot.IMPLOSION) == NukePart.NONE
             || initP   == NukePart.NONE);

        boolean hasU238Jacket = tamper != NukePart.NONE
                && tamper.getName().startsWith("U-238 fast-fission");

        double base = cfg.getBaseYieldKt();
        double eff  = cfg.getEfficiencyBonus();
        for (NukeSlot s : NukeSlot.ALL) {
            if (s == NukeSlot.CONFIGURATION) continue;
            NukePart p = get(s);
            if (p == NukePart.NONE) continue;

            double pBase = p.getBaseYieldKt();
            double pEff  = p.getEfficiencyBonus();

            // Synergy #1: boost gas only contributes where it physically
            // applies. In gun-type / simple implosion designs, the gas
            // is just inert filler.
            if (s == NukeSlot.BOOST && !boostApplies) {
                pBase = 0;
                pEff  = 0;
            }

            // Synergy #2: U-238 fast-fission jacket × thermonuclear
            // secondary. The 14-MeV fusion neutrons fast-fission the
            // jacket. Calibrated alongside the U-238 pusher factor so
            // the two don't double-count the same fast-fission physics.
            if (s == NukeSlot.SECONDARY && hasU238Jacket) {
                pBase *= 1.20;
            }
            // Anti-synergy #6: dead secondary contributes nothing.
            if (s == NukeSlot.SECONDARY && secondaryFails) {
                pBase = 0;
                pEff  = 0;
            }

            // Anti-synergy #8: a heavy U-238 jacket on a small primary
            // fights against the high assembly velocity that makes a
            // hollow / levitated pit work in the first place.
            if (s == NukeSlot.IMPLOSION
                    && p.getName().startsWith("Hollow")
                    && hasU238Jacket) {
                pEff *= 0.85;
            }

            base += pBase;
            eff  += pEff;
        }

        // Synergy #3: composite HEU/Pu pit + D-T boost. Pu provides
        // reliable spontaneous-fission initiation, HEU extends the
        // boosted chain. Neither alone gets the bonus.
        if (boostApplies
                && fissile != NukePart.NONE
                && fissile.getName().startsWith("Composite")
                && boost   != NukePart.NONE
                && boost.getName().startsWith("Deuterium-Tritium")) {
            eff += 0.10;
        }

        // Heuristic: yield scales with (1 + efficiency * 2)
        double scaled = base * (1.0 + eff * 2.0);

        // Fusion sub-designer multipliers (pusher / channel filler /
        // spark plug / U-238 jacket). No-op for non-thermonuclear.
        scaled *= fusionYieldMultiplier();

        // Synergy #4: spark-plug × fuel match. fusionYieldMultiplier()
        // already applies a flat ×1.20 for spark plug enabled. Adjust
        // here for the fuel where the real interaction differs:
        //   • Natural LiD  — Li-7 (n,2n) reactions add unexpected yield
        //                    (×2.00 net) — the Castle Bravo surprise.
        if (isThermoCfg && fusionSparkPlug[0] && sec != NukePart.NONE) {
            String sn = sec.getName();
            if (sn.startsWith("Natural LiD")) scaled *= (2.00 / 1.20);
        }

        // Anti-synergy #7: boost gas without an initiator misses the
        // timing window — degraded but not dead, ×0.5 multiplier.
        if (boostApplies && boost != NukePart.NONE && initP == NukePart.NONE) {
            scaled *= 0.5;
        }

        // Compatibility penalties. A hard FIZZLE warning means the
        // device cannot physically work — nothing ignites, not even a
        // sub-kt fizzle, so the total yield is forced to exactly zero.
        // FIZZLE-SOFT warnings (designs that *can* work but unreliably)
        // still leave a 5% sub-kt yield. Order matters because the
        // string "FIZZLE" prefix-matches "FIZZLE-SOFT".
        boolean hardFizzle = false;
        for (String w : getWarnings()) {
            if (w.startsWith("FIZZLE-SOFT")) {
                scaled *= 0.05;
            } else if (w.startsWith("FIZZLE")) {
                hardFizzle = true;
            }
        }
        if (hardFizzle) return 0;
        return Math.max(0, scaled);
    }

    /**
     * Human-readable list of warnings about physically/logically
     * incompatible combinations. Educational, not engineering advice.
     */
    public List<String> getWarnings() {
        List<String> w = new ArrayList<>();
        NukePart cfg   = get(NukeSlot.CONFIGURATION);
        NukePart fuel  = get(NukeSlot.FISSILE);
        NukePart imp   = get(NukeSlot.IMPLOSION);
        NukePart sec   = get(NukeSlot.SECONDARY);
        NukePart boost = get(NukeSlot.BOOST);
        NukePart cas   = get(NukeSlot.CASING);
        NukePart del   = get(NukeSlot.DELIVERY);
        NukePart fuze  = get(NukeSlot.FUZE);
        NukePart init  = get(NukeSlot.INITIATOR);

        if (cfg == NukePart.NONE) {
            w.add("No weapon configuration selected.");
            return w;
        }

        String n = cfg.getName();
        boolean isGun     = n.startsWith("Gun-type");
        boolean isImpl    = n.startsWith("Implosion");
        boolean isBoost   = n.startsWith("Boosted");
        boolean isSloika  = n.startsWith("Layer-cake");
        boolean isThermo  = n.startsWith("Teller-Ulam") || n.startsWith("Enhanced") || n.startsWith("Salted");

        // ── Fissile-fuel rules ──
        if (fuel == NukePart.NONE) {
            w.add("FIZZLE: no fissile material selected — nothing to drive the chain reaction.");
        }
        if (isGun && fuel != NukePart.NONE && fuel.getName().startsWith("Weapons-grade Plutonium")) {
            w.add("FIZZLE: gun-type assembly is too slow for plutonium — Pu-240 " +
                  "spontaneous fission would pre-detonate the core.");
        }
        if (isGun && fuel != NukePart.NONE && fuel.getName().startsWith("Reactor-grade")) {
            w.add("FIZZLE: reactor-grade Pu has a far higher Pu-240 content than " +
                  "weapons-grade — gun-type assembly is hopeless.");
        }
        if (fuel != NukePart.NONE && fuel.getName().startsWith("Reactor-grade")
                && !isGun) {
            // FIZZLE-SOFT: the device can work, but unreliably and at
            // a fraction of design yield (Pu-240 pre-detonation in the
            // implosion regime). The gun-type case is already a hard
            // FIZZLE above, so we exclude it here to avoid double
            // penalty.
            w.add("FIZZLE-SOFT: reactor-grade plutonium gives an unreliable, probably " +
                  "sub-kt yield even with implosion (declassified DOE 1997).");
        }

        // ── Implosion rules ──
        if ((isImpl || isBoost || isSloika || isThermo) && imp == NukePart.NONE) {
            w.add("FIZZLE: implosion-based design but no implosion system selected — " +
                  "no way to compress the pit.");
        }
        if (isGun && imp != NukePart.NONE) {
            w.add("Implosion lenses selected for a gun-type weapon — they would be unused.");
        }

        // ── Initiator ──
        if (!isGun && init == NukePart.NONE) {
            w.add("No neutron initiator — the chain reaction may start at the wrong " +
                  "moment, giving a fizzle or unpredictable yield.");
        }

        // ── Boost ──
        if (isBoost && boost == NukePart.NONE) {
            w.add("'Boosted' configuration but no boost gas — falls back to a plain " +
                  "implosion fission yield.");
        }
        if (!isBoost && !isSloika && !isThermo && boost != NukePart.NONE) {
            w.add("Boost gas is only useful in boosted-fission, layer-cake, or " +
                  "thermonuclear designs — here it contributes nothing to yield.");
        }
        // Anti-synergy #7: boost gas without a neutron initiator misses
        // its timing window. Not a fizzle — just degraded.
        if ((isBoost || isSloika || isThermo) && boost != NukePart.NONE && init == NukePart.NONE) {
            w.add("Boost gas without a neutron initiator: the D-T fusion pulse " +
                  "misses its timing window — boost contribution roughly halved.");
        }

        // ── Secondary ──
        // Anti-synergy #6: a Teller-Ulam secondary needs a working
        // primary to ignite. Without fissile / implosion / initiator,
        // the X-ray drive fails and the secondary is dead.
        if (isThermo && sec != NukePart.NONE
                && (fuel == NukePart.NONE || imp == NukePart.NONE || init == NukePart.NONE)) {
            w.add("FIZZLE: thermonuclear secondary failed to ignite — the primary " +
                  "is incomplete (missing fissile material, implosion system, or " +
                  "neutron initiator), so there is no X-ray drive to compress it.");
        }
        if (isThermo && sec == NukePart.NONE) {
            w.add("Thermonuclear configuration but no fusion secondary — only the " +
                  "fission primary will fire.");
        }
        if (!isThermo && !isSloika && sec != NukePart.NONE) {
            w.add("Fusion secondary selected but the configuration has no two-stage " +
                  "primary to ignite it.");
        }
        if (isThermo && cas != NukePart.NONE && !cas.getName().startsWith("Radiation")) {
            w.add("Two-stage thermonuclear designs need a radiation-case casing to " +
                  "channel X-rays from the primary onto the secondary.");
        }

        // ── Delivery / casing compatibility ──
        if (del != NukePart.NONE && del.getName().startsWith("Artillery") && cas != NukePart.NONE
                && !cas.getName().startsWith("Cylindrical")) {
            w.add("Artillery delivery requires a cylindrical shell-body casing.");
        }
        if (del != NukePart.NONE && del.getName().startsWith("ICBM")
                && cas != NukePart.NONE && !cas.getName().startsWith("Re-entry")) {
            w.add("Ballistic-missile delivery requires a re-entry vehicle aeroshell.");
        }
        if (del != NukePart.NONE && del.getName().startsWith("Naval depth")
                && fuze != NukePart.NONE && !fuze.getName().startsWith("Hydrostatic")) {
            w.add("A naval depth charge needs a hydrostatic (depth-triggered) fuze.");
        }

        // ── Salted / cobalt warning ──
        if (n.startsWith("Salted")) {
            w.add("'Salted' designs were proposed only as a doomsday cautionary tale " +
                  "(Szilárd, 1950). None has ever been built or tested.");
        }

        // ── Fusion sub-designer warnings (only meaningful for 2-stage) ──
        if (isThermo) {
            if (!fusionSparkPlug[0] && sec != NukePart.NONE
                    && !sec.getName().startsWith("Spark-plug")) {
                w.add("No Pu-239 spark plug in the secondary — fusion ignition is marginal; " +
                      "yield will be a fraction of the design intent.");
            }
            if (fusionStageCount == 2) {
                w.add("3-stage fission-fusion-fission: the U-238 outer jacket roughly " +
                      "doubles yield but produces extreme radioactive fallout (Castle " +
                      "Bravo, 1954).");
            }
            if (fusionStageCount >= 3) {
                w.add("4-stage device: a hypothetical extrapolation beyond any weapon " +
                      "ever built. The Soviet Tsar Bomba (1961) used a 3-stage design " +
                      "that was already producing intolerable fallout — a 4-stage " +
                      "device would be a deliberate planet-scale contamination event.");
            }
            // Per-stage pusher warning: any inert pusher anywhere in
            // the cascade is a notable design choice (Tsar Bomba's lead
            // tamper roughly halved yield). Mention each one.
            for (int i = 0; i < fusionStageCount; i++) {
                String p = fusionPusher[i];
                if (p.equals("Lead") || p.equals("Tungsten")) {
                    String stageLabel = (i == 0) ? "secondary"
                                      : (i == 1) ? "tertiary"
                                                 : "quaternary";
                    w.add("Inert " + p.toLowerCase() + " pusher on the " + stageLabel +
                          ": a 'cleaner' stage with lower yield (no fast-fission " +
                          "contribution from a U-238 tamper).");
                }
            }
            // Synergy #4: Castle Bravo surprise. Natural LiD plus an
            // active spark plug: Li-7 (n,2n) reactions release extra
            // tritium and unexpected yield. Informational — the yield
            // model already accounts for the multiplier.
            if (fusionSparkPlug[0] && sec != NukePart.NONE
                    && sec.getName().startsWith("Natural LiD")) {
                w.add("Castle Bravo surprise: natural LiD + Pu-239 spark plug — Li-7 " +
                      "(n,2n) reactions breed extra tritium and the device yields " +
                      "~67% more than the spec sheet predicts. The 1954 designers " +
                      "under-predicted Castle Bravo's yield by 2.5× for exactly " +
                      "this reason.");
            }
        }

        // Synergy #2 (informational): U-238 fast-fission jacket boosts
        // a thermonuclear secondary by ~20% in this model (calibrated
        // alongside the U-238 pusher factor so the two don't double-count
        // the same fast-fission physics). Historically the jacket was
        // the dominant yield contribution in F-F-F designs.
        if (isThermo && sec != NukePart.NONE
                && get(NukeSlot.TAMPER) != NukePart.NONE
                && get(NukeSlot.TAMPER).getName().startsWith("U-238 fast-fission")) {
            w.add("U-238 fast-fission jacket around the fusion secondary: 14-MeV " +
                  "fusion neutrons fast-fission the jacket, contributing extra " +
                  "yield (and the bulk of the long-lived fallout).");
        }

        // Synergy #3 (informational): composite pit + D-T boost.
        if ((isBoost || isSloika || isThermo)
                && fuel != NukePart.NONE && fuel.getName().startsWith("Composite")
                && boost != NukePart.NONE && boost.getName().startsWith("Deuterium-Tritium")) {
            w.add("Composite HEU/Pu pit + D-T boost: the Pu-239 fraction provides " +
                  "reliable spontaneous-fission initiation while the HEU extends " +
                  "the boosted chain — a small but meaningful efficiency bonus.");
        }

        // Anti-synergy #8 (informational): heavy U-238 jacket against a
        // hollow / levitated pit fights the high assembly velocity.
        if (imp != NukePart.NONE && imp.getName().startsWith("Hollow")
                && get(NukeSlot.TAMPER) != NukePart.NONE
                && get(NukeSlot.TAMPER).getName().startsWith("U-238 fast-fission")) {
            w.add("Heavy U-238 jacket on a hollow / levitated pit: the jacket's " +
                  "mass slows the imploding shell before impact, costing some of " +
                  "the high-velocity advantage that makes a levitated pit work.");
        }

        return w;
    }

    /**
     * Picks the closest historical analog for the current design, if any.
     */
    public String getHistoricalAnalog() {
        NukePart cfg = get(NukeSlot.CONFIGURATION);
        if (cfg == NukePart.NONE) return "—";
        NukePart sec = get(NukeSlot.SECONDARY);
        NukePart boost = get(NukeSlot.BOOST);
        NukePart del = get(NukeSlot.DELIVERY);

        String name = cfg.getName();
        if (name.startsWith("Gun-type"))
            return "Little Boy (Mk-I) — Hiroshima, 6 Aug 1945. ~15 kt from ≈64 kg of " +
                   "highly-enriched uranium fired down a gun barrel. Crude, heavy " +
                   "(4.4 t), and so confident in its physics it was never test-fired.";
        if (name.startsWith("Implosion"))
            return "Fat Man (Mk-III) — Nagasaki, 9 Aug 1945. ~21 kt from a 6.2 kg " +
                   "plutonium core symmetrically crushed by 32 explosive lenses. The " +
                   "design that all later fission weapons descend from.";
        if (name.startsWith("Layer-cake"))
            return "Joe-4 / RDS-6s — Sakharov\u2019s 'layer cake', USSR Aug 1953, ~400 kt. " +
                   "Alternating shells of Li-6-D fusion fuel and U-238 around a fission " +
                   "core. Boosted, not truly thermonuclear — no separate radiation stage.";
        if (name.startsWith("Boosted") && boost != NukePart.NONE) {
            if (del != NukePart.NONE && del.getName().startsWith("ICBM"))
                return "W76 / W87-class missile primary — ~100 kt boosted-fission warhead " +
                       "riding a Trident D5 or Minuteman III RV. A few kg of HEU/Pu plus " +
                       "a puff of D-T gas: compact enough that 8\u201312 fit on one bus.";
            return "B61 family (1968\u2013present) — dial-a-yield boosted fission, ~0.3 kt " +
                   "to 340 kt selectable on the ground. Still in the US stockpile after " +
                   "a half-century of life-extension programmes.";
        }
        if (name.startsWith("Teller-Ulam")) {
            if (sec.getName().startsWith("Cryogenic"))
                return "Ivy Mike — Enewetak Atoll, 1 Nov 1952, ~10.4 Mt. The first true " +
                       "two-stage Teller-Ulam test, using cryogenic liquid deuterium so " +
                       "unwieldy the device was a 74-tonne cylinder the size of a railroad " +
                       "car — not a deliverable weapon, just a physics demonstration.";
            if (sec.getName().startsWith("Lithium-6"))
                return "Castle Bravo — Bikini Atoll, 1 Mar 1954, ~15 Mt (2.5\u00d7 predicted). " +
                       "Designers underestimated the cross-section of Li-7, which bred " +
                       "extra tritium and runaway fast-fission in the U-238 jacket. The " +
                       "largest US test ever, with catastrophic fallout.";
            return "Teller-Ulam thermonuclear class — the architecture every modern " +
                   "strategic warhead descends from: fission primary, X-ray channel, " +
                   "radiation-imploded fusion secondary.";
        }
        if (name.startsWith("Enhanced"))
            return "W70-3 / W79 \u2018neutron bomb\u2019 (Sam Cohen, 1958; deployed 1981) — " +
                   "a low-yield (\u22481 kt) ER warhead designed to kill armoured crews " +
                   "with prompt fast neutrons while leaving structures relatively intact.";
        if (name.startsWith("Salted"))
            return "Szil\u00e1rd\u2019s 'Cobalt Bomb' (1950, thought-experiment only) — a " +
                   "thermonuclear device jacketed in Co-59. Neutron capture would breed " +
                   "Co-60 fallout with a 5.27-year half-life, rendering large areas " +
                   "uninhabitable for decades. Proposed as a cautionary tale, never built.";
        return "—";
    }

    /**
     * Categorical effects description for the estimated yield, paraphrased
     * from public effects-of-nuclear-weapons references (Glasstone & Dolan,
     * Nukemap educational notes). Each band lists concrete numbers for
     * fireball radius, 5-psi blast (severe building damage), 3rd-degree
     * thermal burns, and the lethal prompt-radiation footprint.
     */
    public String getEffectsSummary() {
        double kt = getEstimatedYieldKt();
        if (kt <= 0) return "No detonation. The configuration produces no chain " +
                            "reaction — either the fissile material is missing, the " +
                            "geometry won't go critical, or a 'FIZZLE' warning " +
                            "is suppressing the yield.";

        if (kt < 1)
            return "• Sub-kiloton (≈ a large conventional munition).\n" +
                   "• Fireball: tens of metres across, lasting under a second.\n" +
                   "• Severe blast (5 psi): a few hundred metres — unreinforced " +
                       "buildings collapse.\n" +
                   "• Thermal: 3rd-degree burns out to ~300 m on exposed skin.\n" +
                   "• Prompt radiation usually outranges blast: ~600 m lethal dose.\n" +
                   "• Comparable to: the Davy Crockett recoilless rifle warhead (W54).";

        if (kt < 20)
            return "• Kiloton range — the Hiroshima / Nagasaki band.\n" +
                   "• Fireball: ~150–200 m, surface temperatures > 5000 K.\n" +
                   "• Severe blast (5 psi): ≈1.5–2 km — reinforced concrete shears.\n" +
                   "• Thermal: 3rd-degree burns to ≈3 km, ignites cloth and paper.\n" +
                   "• Prompt radiation: lethal within ≈1.2 km even through walls.\n" +
                   "• Fallout: significant downwind for hours if a ground burst.";

        if (kt < 100)
            return "• Tens of kilotons — typical modern tactical or primary yield.\n" +
                   "• Fireball: ~400 m, visible for hundreds of km on a clear night.\n" +
                   "• Severe blast (5 psi): ≈ 3–5 km radius, total destruction inside.\n" +
                   "• Thermal: 3rd-degree burns to ≈ 7 km — mass conflagrations possible.\n" +
                   "• Prompt radiation becomes secondary; blast/thermal dominate.\n" +
                   "• Mushroom cloud rises to ≈12 km, into the lower stratosphere.";

        if (kt < 1000)
            return "• Hundreds of kilotons — typical strategic warhead (W76, W87 class).\n" +
                   "• Fireball: ≈ 1 km diameter, hotter than the Sun's surface.\n" +
                   "• Severe blast (5 psi): ≈ 5–10 km — a major city centre flattened.\n" +
                   "• Thermal: 3rd-degree burns to ≈ 15–20 km, line-of-sight only.\n" +
                   "• EMP from a high airburst can disable electronics across an entire " +
                       "continental footprint.\n" +
                   "• Mushroom cloud rises to ≈20 km — well into the stratosphere.";

        if (kt < 10000)
            return "• Megaton class — historically the staple of Cold-War strategic " +
                       "forces (B53, W56, MIRV-era heavies).\n" +
                   "• Fireball: 2–3 km across, briefly outshines the Sun.\n" +
                   "• Severe blast (5 psi): ≈ 12–20 km — an entire metropolitan area " +
                       "flattened in one shot.\n" +
                   "• Thermal: 3rd-degree burns to ≈ 30–40 km; firestorms over the " +
                       "whole damage zone.\n" +
                   "• Stratospheric injection ~30 km — fallout may circle the globe.\n" +
                   "• A single ground burst kicks roughly a million tonnes of debris into " +
                       "the atmosphere.";

        return "• Multi-megaton — only test devices have ever reached this scale.\n" +
               "• Tsar Bomba (USSR, 30 Oct 1961, ~50 Mt half-yield) — the largest " +
                   "man-made explosion ever. Fireball ~8 km across; mushroom cloud 64 km " +
                   "tall; window panes broken in Finland and Sweden.\n" +
               "• Severe blast (5 psi): ≈ 30+ km — effects on the scale of an entire " +
                   "metropolitan region.\n" +
               "• Thermal: 3rd-degree burns past 100 km on a clear day.\n" +
               "• Climatic effects measurable globally: stratospheric aerosol injection " +
                   "comparable to a moderate volcanic eruption.";
    }

    /** Approximate severe-damage radius (km) — very rough scaling law. */
    public double getDamageRadiusKm() {
        double kt = getEstimatedYieldKt();
        if (kt <= 0) return 0;
        // Severe damage (~5 psi) scales roughly with cube root of yield;
        // 1 kt ≈ 0.6 km at 5 psi airburst.
        return 0.6 * Math.cbrt(kt);
    }
}
