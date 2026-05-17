package com.tetris.mab.upgrade.draft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.tetris.mab.upgrade.draft.MabUpgradeArchetype.*;

/**
 * v2 registry — 40 upgrade cards across 6 categories and 6 archetypes.
 * INTEL category retired; no asymmetric-information cards exist in this pool.
 *
 * <p>Effect tag contract (consumed by {@link MabUpgradeEffectResolver}):
 * <pre>
 * # CHARGE (17)
 *   charge_clear_bonus_1   +1 charge per scoring clear per stack  (Efficient Reactor)
 *   charge_combo_4plus     combo 4+ adds +2 per chained clear     (Combo Capacitor)
 *   charge_streak_stoker   Nth consecutive clear → N bonus charge  (Streak Stoker)
 *   charge_b2b_amplifier   B2B multiplier 1.25 → 1.40             (B2B Amplifier)
 *   charge_combo_resonance combo length 6 → +30 instant charge    (Resonance Chamber)
 *   charge_spin_any        any spin clear → +2 charge             (Spin Doctrine)
 *   charge_spin_zero       0-line spin → +1 charge                (Wrist Drill)
 *   charge_spin_triple     spin triple → +8 charge                (TST Program)
 *   charge_intercept       full intercept → +10 charge            (Counterspin Training)
 *   charge_spin_chain      Nth spin in cycle → N-1 bonus charge   (Spin Network)
 *   charge_tetris_flat     each Tetris → +6 charge                (Tetris Doctrine)
 *   charge_clean_tetris    non-spin Tetris → +3 charge            (Clean Well Logistics)
 *   charge_perfect_clear   perfect clear → +8 charge              (Perfect Clear Battery)
 *   charge_wellsmith       Tetris-ready well → charge gain ×1.20  (Wellsmith)
 *   charge_quiet_storm     10 s idle → next clear +20 charge      (Quiet Storm)
 *   charge_stockpile       charge cap 100 → 130                   (Stockpile)
 *   charge_adrenaline      stack ≥ row 14 → charge gain ×1.5      (Adrenaline Sequence)
 *
 * # TEMPO (7)
 *   tempo_cascade_reactor  while ready: every 3rd combo clear → +1 route pip (Cascade Reactor)
 *   tempo_rapid_assembly   +10 charge after launch                (Rapid Assembly)
 *   tempo_hair_trigger     charge +25%; bleed 1/sec when idle     (Hair Trigger)
 *   tempo_retaliation      +20 charge per impact taken            (Retaliation Doctrine)
 *   tempo_manual_override  hotkey overlay → half-power fire at full charge (Manual Override)
 *   tempo_jam              opp reaches ready → +1 random route pip (Jam)
 *   tempo_emp              overlay → cancel opp launch for -40 charge (EMP)
 *
 * # DEFENSE (10)
 *   defense_shelters       impact garbage −1 line (floor 1)       (Shelters)
 *   defense_bunker         first impact halved                    (Bunker)
 *   defense_cold_steel     low stack → intercept refund +5        (Cold Steel)
 *   defense_intercept_str  intercept tier +1                      (Intercept Crews)
 *   defense_emergency      once/match high-stack impact −2        (Emergency Protocols)
 *   defense_reactive_plating 6 s doubled intercept window after impact (Reactive Plating)
 *   defense_ablative_spin  one intercept covers two clustered threats (Ablative Spin)
 *   defense_hardened_silos once/match impact cannot top you out   (Hardened Silos)
 *   defense_dead_hand      prevent fatal impact + auto uninterceptable counter (Dead Hand Protocol)
 *
 * # POWER (5)
 *   power_light_fuse       first launch +1 line                   (Light the Fuse)
 *   power_heavy_warhead    all launches +1 line                   (Heavy Warhead)
 *   power_penetrator       opp intercept −1 tier vs your launches (Penetrator Package)
 *   power_dirty_payload    +1 messy garbage line on impact        (Dirty Payload)
 *   power_glass_cannon     your launches +2 lines; impacts on you +1 (Glass Cannon)
 *
 * # TETRIS_ROUTE (1)
 *   route_tetris_minus1    Tetris route 4 → 3                     (Fast Fuse)
 *
 * # SPIN_ROUTE (1)
 *   route_spin_keep1       after spin launch, keep 1 spin pip     (Spin Launch Crew)
 * </pre>
 */
public final class MabUpgradeDraftRegistry {

    private final Map<String, MabUpgradeCard> byId = new LinkedHashMap<>();

    public MabUpgradeDraftRegistry() { seedDefaultCards(); }

    public List<MabUpgradeCard> getAllCards() {
        return Collections.unmodifiableList(new ArrayList<>(byId.values()));
    }
    public MabUpgradeCard getById(String id) { return byId.get(id); }
    public int size() { return byId.size(); }

    private void add(MabUpgradeCard c) { byId.put(c.getId(), c); }

    private static List<String> tags(String... t) { return Arrays.asList(t); }
    private static List<MabUpgradeArchetype> arch(MabUpgradeArchetype... a) { return Arrays.asList(a); }

    private void seedDefaultCards() {

        // ════════════════════════════════════════════════
        //  COMBO REACTOR ARCHETYPE
        // ════════════════════════════════════════════════

        add(new MabUpgradeCard("efficient_reactor", "Efficient Reactor", "REACTOR", "⊕ I",
                MabUpgradeCategory.CHARGE, MabUpgradeRarity.STANDARD,
                "+1 charge per scoring clear (per stack).",
                "Each scoring clear awards +1 bonus charge per stack owned. " +
                "Stacks additively: 1 copy = +1, 2 copies = +2 per clear.",
                2, true, tags("charge_clear_bonus_1"),
                1.0, arch(COMBO_REACTOR), false));

        add(new MabUpgradeCard("combo_capacitor", "Combo Capacitor", "COMBO", "⊕ I",
                MabUpgradeCategory.CHARGE, MabUpgradeRarity.STANDARD,
                "Combo 4+ gains +2 extra charge per chained clear.",
                "Sustaining a combo of 4 or higher adds +2 charge to each clear in that combo. " +
                "Applies from the 4th consecutive clear onward.",
                1, false, tags("charge_combo_4plus"),
                1.0, arch(COMBO_REACTOR), false));

        add(new MabUpgradeCard("streak_stoker", "Streak Stoker", "STOKER", "⊕ I",
                MabUpgradeCategory.CHARGE, MabUpgradeRarity.STANDARD,
                "Nth consecutive clear awards N bonus charge.",
                "Maintain a streak by clearing on consecutive locks. " +
                "The Nth consecutive clear (N starting at 1) awards N bonus charge. " +
                "Streak resets on any lock that does not clear a line.",
                1, false, tags("charge_streak_stoker"),
                1.0, arch(COMBO_REACTOR), false));

        // Dual archetype: Combo Reactor + Tetris Stockpiler
        add(new MabUpgradeCard("b2b_amplifier", "B2B Amplifier", "B2B", "⊕ II",
                MabUpgradeCategory.CHARGE, MabUpgradeRarity.ADVANCED,
                "B2B charge multiplier improves 1.25 → 1.40.",
                "Back-to-back difficult clears (Tetrises, all spin clears) multiply their " +
                "charge by 1.40 instead of the base 1.25 while the B2B streak holds.",
                1, false, tags("charge_b2b_amplifier"),
                1.0, arch(COMBO_REACTOR, TETRIS_STOCKPILER), false));

        add(new MabUpgradeCard("resonance_chamber", "Resonance Chamber", "RESONANCE", "⊕ II",
                MabUpgradeCategory.CHARGE, MabUpgradeRarity.ADVANCED,
                "Reaching combo length 6 grants +30 charge instantly.",
                "Fires the moment the 6th consecutive clear resolves. " +
                "One-shot per combo run; resets when the combo breaks.",
                1, false, tags("charge_combo_resonance"),
                1.0, arch(COMBO_REACTOR), false));

        add(new MabUpgradeCard("cascade_reactor", "Cascade Reactor", "CASCADE", "⦿ III",
                MabUpgradeCategory.TEMPO, MabUpgradeRarity.CRITICAL,
                "While nuke-ready, every 3rd combo clear advances your launch route.",
                "Only fires during the nuke-ready phase. Counts clears within an unbroken combo. " +
                "Every 3rd clear awards 1 route pip: Tetris clears → T pip, spin clears → S pip. " +
                "If the relevant route is full the pip is wasted.",
                1, false, tags("tempo_cascade_reactor"),
                1.0, arch(COMBO_REACTOR), false));

        // ════════════════════════════════════════════════
        //  SPIN SPECIALIST ARCHETYPE
        // ════════════════════════════════════════════════

        add(new MabUpgradeCard("spin_doctrine", "Spin Doctrine", "SPIN DOC", "⦵ I",
                MabUpgradeCategory.CHARGE, MabUpgradeRarity.STANDARD,
                "Any spin clear adds +2 charge.",
                "Applies to all spin-detected clears including 0-line spins.",
                1, false, tags("charge_spin_any"),
                1.0, arch(SPIN_SPECIALIST), false));

        add(new MabUpgradeCard("wrist_drill", "Wrist Drill", "WRIST", "⦵ I",
                MabUpgradeCategory.CHARGE, MabUpgradeRarity.STANDARD,
                "Zero-line spin clears award +1 charge.",
                "Without this card, 0-line spins award no charge. " +
                "Stacks with Spin Doctrine: a 0-line spin with both cards grants +3 charge.",
                1, false, tags("charge_spin_zero"),
                1.0, arch(SPIN_SPECIALIST), false));

        add(new MabUpgradeCard("tst_program", "TST Program", "TST", "⦵ II",
                MabUpgradeCategory.CHARGE, MabUpgradeRarity.ADVANCED,
                "Spin triples give +8 extra charge.",
                "Triggers on T-spin triples and any equivalent 3-line spin clear.",
                1, false, tags("charge_spin_triple"),
                1.0, arch(SPIN_SPECIALIST), false));

        // Dual archetype: Spin Specialist + Turtle
        add(new MabUpgradeCard("counterspin_training", "Counterspin Training", "COUNTERSPIN", "⦵ II",
                MabUpgradeCategory.CHARGE, MabUpgradeRarity.ADVANCED,
                "A full intercept refunds +10 charge.",
                "Triggers only on full intercepts (threat fully neutralized). " +
                "Partial intercepts give no refund.",
                1, false, tags("charge_intercept"),
                1.0, arch(SPIN_SPECIALIST, TURTLE), false));

        add(new MabUpgradeCard("spin_network", "Spin Network", "SPIN NET", "⦵ II",
                MabUpgradeCategory.CHARGE, MabUpgradeRarity.ADVANCED,
                "Spins within a launch cycle chain bonus charge.",
                "Maintains a counter resetting at the start of each launch cycle. " +
                "Each spin clear in the cycle awards bonus equal to the number of prior " +
                "spin clears that cycle: 1st spin = +0, 2nd = +1, 3rd = +2, and so on.",
                1, false, tags("charge_spin_chain"),
                1.0, arch(SPIN_SPECIALIST), false));

        add(new MabUpgradeCard("spin_launch_crew", "Spin Launch Crew", "SPIN CREW", "⦵ III",
                MabUpgradeCategory.SPIN_ROUTE, MabUpgradeRarity.CRITICAL,
                "A spin launch retains 1 spin route pip.",
                "After firing a launch via the spin route, the spin pip counter resets to 1 " +
                "instead of 0. The Tetris route resets normally.",
                1, false, tags("route_spin_keep1"),
                1.0, arch(SPIN_SPECIALIST), false));

        // ════════════════════════════════════════════════
        //  TETRIS STOCKPILER ARCHETYPE
        // ════════════════════════════════════════════════

        add(new MabUpgradeCard("tetris_doctrine", "Tetris Doctrine", "DOCTRINE", "⊢ I",
                MabUpgradeCategory.CHARGE, MabUpgradeRarity.STANDARD,
                "Each Tetris awards +6 bonus charge.",
                "Applies to all 4-line clears regardless of whether they were spin-detected. " +
                "Stacks with Clean Well Logistics (+3 only for non-spin Tetrises).",
                1, false, tags("charge_tetris_flat"),
                1.0, arch(TETRIS_STOCKPILER), false));

        add(new MabUpgradeCard("clean_well_logistics", "Clean Well Logistics", "CLEAN WELL", "⊢ I",
                MabUpgradeCategory.CHARGE, MabUpgradeRarity.STANDARD,
                "Non-spin Tetrises give +3 extra charge.",
                "Excludes any Tetris where the spin detector flagged the lock. " +
                "Pairs with Tetris Doctrine: a clean Tetris with both cards grants +9 charge.",
                1, false, tags("charge_clean_tetris"),
                1.0, arch(TETRIS_STOCKPILER), false));

        add(new MabUpgradeCard("perfect_clear_battery", "Perfect Clear Battery", "ALLCLEAR", "⊢ I",
                MabUpgradeCategory.CHARGE, MabUpgradeRarity.STANDARD,
                "Perfect clears give +8 extra charge.",
                "Triggers on any lock that leaves the board fully empty after clears resolve.",
                1, false, tags("charge_perfect_clear"),
                1.0, arch(TETRIS_STOCKPILER), false));

        add(new MabUpgradeCard("wellsmith", "Wellsmith", "WELLSMITH", "⊢ II",
                MabUpgradeCategory.CHARGE, MabUpgradeRarity.ADVANCED,
                "Maintaining a Tetris-ready well multiplies all charge gain by 1.20.",
                "A Tetris-ready well is a single empty column with depth ≥ 4 relative to " +
                "both neighbors. The bonus applies continuously while the condition holds " +
                "and drops the instant the well is filled.",
                1, false, tags("charge_wellsmith"),
                1.0, arch(TETRIS_STOCKPILER), false));

        // b2b_amplifier (shared) already added under Combo Reactor

        add(new MabUpgradeCard("fast_fuse", "Fast Fuse", "FAST FUSE", "⊢ III",
                MabUpgradeCategory.TETRIS_ROUTE, MabUpgradeRarity.CRITICAL,
                "Tetris route target reduced 4 → 3.",
                "Once nuke-ready, 3 Tetrises now complete the Tetris route instead of 4.",
                1, false, tags("route_tetris_minus1"),
                1.0, arch(TETRIS_STOCKPILER), false));

        // ════════════════════════════════════════════════
        //  TURTLE ARCHETYPE
        // ════════════════════════════════════════════════

        add(new MabUpgradeCard("shelters", "Shelters", "SHELTERS", "◇ I",
                MabUpgradeCategory.DEFENSE, MabUpgradeRarity.STANDARD,
                "Impact garbage reduced by 1 line (floor 1).",
                "Each incoming impact deals 1 fewer garbage line, minimum of 1.",
                1, false, tags("defense_shelters"),
                1.0, arch(TURTLE), false));

        add(new MabUpgradeCard("bunker", "Bunker", "BUNKER", "◇ I",
                MabUpgradeCategory.DEFENSE, MabUpgradeRarity.STANDARD,
                "First impact of the match has its line count halved.",
                "Reduces by 50%, rounded up (4-line → 2, 3-line → 2, 5-line → 3). " +
                "One-shot per match. Consumed after first impact applies.",
                1, false, tags("defense_bunker"),
                1.0, arch(TURTLE), false));

        add(new MabUpgradeCard("cold_steel", "Cold Steel", "COLD STL", "◇ I",
                MabUpgradeCategory.DEFENSE, MabUpgradeRarity.STANDARD,
                "While stack is low, intercepts refund +5 extra charge.",
                "Condition: stack's topmost occupied row is at or below row 8 " +
                "(top 12 rows empty). While true, full intercepts refund an additional " +
                "+5 charge on top of other refund cards.",
                1, false, tags("defense_cold_steel"),
                1.0, arch(TURTLE), false));

        add(new MabUpgradeCard("intercept_crews", "Intercept Crews", "INTERCEPT", "◇ I",
                MabUpgradeCategory.DEFENSE, MabUpgradeRarity.STANDARD,
                "Spin intercept tier improved by 1.",
                "Spin intercept mitigation is improved by one tier.",
                1, false, tags("defense_intercept_str"),
                1.0, arch(TURTLE), false));

        add(new MabUpgradeCard("emergency_protocols", "Emergency Protocols", "EMERGENCY", "◇ I",
                MabUpgradeCategory.DEFENSE, MabUpgradeRarity.STANDARD,
                "Once per match, a high-stack impact is softened by 2 lines.",
                "Triggers on the first impact taken while stack's topmost row is at or above " +
                "row 13 (upper third of board). Reduces line count by 2 (floor 1). " +
                "Consumed after first trigger.",
                1, false, tags("defense_emergency"),
                1.0, arch(TURTLE), false));

        add(new MabUpgradeCard("reactive_plating", "Reactive Plating", "REACTIVE", "◇ II",
                MabUpgradeCategory.DEFENSE, MabUpgradeRarity.ADVANCED,
                "After taking impact, intercept window doubles for 6 seconds.",
                "The in-flight window during which a spin lands as an intercept is doubled " +
                "for 6 seconds after each impact absorbed. Designed to give breathing room " +
                "during a chained attack.",
                1, false, tags("defense_reactive_plating"),
                1.0, arch(TURTLE), false));

        // counterspin_training (shared) already added under Spin Specialist

        add(new MabUpgradeCard("ablative_spin", "Ablative Spin", "ABLATIVE", "◇ III",
                MabUpgradeCategory.DEFENSE, MabUpgradeRarity.CRITICAL,
                "One spin intercept can cover two clustered threats.",
                "If two incoming threats are in flight within 2 ticks of each other, " +
                "a single spin intercept resolves both.",
                1, false, tags("defense_ablative_spin"),
                1.0, arch(TURTLE), false));

        add(new MabUpgradeCard("hardened_silos", "Hardened Silos", "HARDENED", "◇ III",
                MabUpgradeCategory.DEFENSE, MabUpgradeRarity.CRITICAL,
                "Once per match, an impact cannot top you out.",
                "If an impact would push your stack above row 20, the impact is capped at row 20: " +
                "stack fills to the top row exactly, extra garbage discarded. One-shot per match.",
                1, false, tags("defense_hardened_silos"),
                1.0, arch(TURTLE), false));

        add(new MabUpgradeCard("retaliation_doctrine", "Retaliation Doctrine", "RETALIATE", "⦿ III",
                MabUpgradeCategory.TEMPO, MabUpgradeRarity.CRITICAL,
                "Taking impact awards +20 charge.",
                "Each impact you take (after all reductions from Shelters / Bunker / etc.) " +
                "grants +20 charge. Anti-snowball card for the losing side.",
                1, false, tags("tempo_retaliation"),
                1.0, arch(TURTLE), false));

        // Dead Hand Protocol: 0.5× draft weight so it appears rarely
        add(new MabUpgradeCard("dead_hand_protocol", "Dead Hand Protocol", "DEAD HAND", "◇ III",
                MabUpgradeCategory.DEFENSE, MabUpgradeRarity.CRITICAL,
                "Once per match: prevent fatal impact and auto-fire an uninterceptable counter-launch.",
                "Triggers when impact would top you out. Effects in order: (1) impact capped at " +
                "row 20, (2) full-power launch auto-fired, (3) the counter-launch's intercept " +
                "window is disabled — opponent sees \"DEAD HAND STRIKE\" instead of the normal " +
                "intercept prompt. Card consumed after trigger.",
                1, false, tags("defense_dead_hand"),
                0.5, arch(TURTLE), false));

        // ════════════════════════════════════════════════
        //  RUSHER ARCHETYPE
        // ════════════════════════════════════════════════

        add(new MabUpgradeCard("light_the_fuse", "Light the Fuse", "FUSE", "◈ I",
                MabUpgradeCategory.POWER, MabUpgradeRarity.STANDARD,
                "First launch of the match deals +1 garbage line.",
                "Single-use, consumed after first launch fires. Stacks with other +line cards.",
                1, false, tags("power_light_fuse"),
                1.0, arch(RUSHER), false));

        add(new MabUpgradeCard("heavy_warhead", "Heavy Warhead", "HEAVY", "◈ II",
                MabUpgradeCategory.POWER, MabUpgradeRarity.ADVANCED,
                "Every launch you fire deals +1 garbage line.",
                "Flat bonus on every launch. Stacks with Dirty Payload and Glass Cannon.",
                1, false, tags("power_heavy_warhead"),
                1.0, arch(RUSHER), false));

        add(new MabUpgradeCard("penetrator_package", "Penetrator Package", "PENETRATOR", "◈ II",
                MabUpgradeCategory.POWER, MabUpgradeRarity.ADVANCED,
                "Opponent intercepts are one tier less effective against your launches.",
                "Reduces opponent intercept effectiveness against your launches by one tier.",
                1, false, tags("power_penetrator"),
                1.0, arch(RUSHER), false));

        add(new MabUpgradeCard("dirty_payload", "Dirty Payload", "DIRTY", "◈ II",
                MabUpgradeCategory.POWER, MabUpgradeRarity.ADVANCED,
                "Each impact you cause adds 1 messy garbage line.",
                "The extra line uses random-hole garbage generation rather than standard " +
                "single-column garbage. Stacks additively with Heavy Warhead.",
                1, false, tags("power_dirty_payload"),
                1.0, arch(RUSHER), false));

        add(new MabUpgradeCard("rapid_assembly", "Rapid Assembly", "RAPID", "⦿ II",
                MabUpgradeCategory.TEMPO, MabUpgradeRarity.ADVANCED,
                "Firing a launch refunds +10 charge.",
                "Triggered the instant the launch fires (not on impact). " +
                "Goes toward the next charge build.",
                1, false, tags("tempo_rapid_assembly"),
                1.0, arch(RUSHER), false));

        add(new MabUpgradeCard("hair_trigger", "Hair Trigger", "HAIR TRG", "⦿ II",
                MabUpgradeCategory.TEMPO, MabUpgradeRarity.ADVANCED,
                "Charge gain +25%; bleeds 1/sec when not clearing.",
                "All sources of charge gain are multiplied by 1.25. While you have not cleared " +
                "a line in the last 1 second, charge bleeds 1 point/sec until a clear resolves. " +
                "Cannot reduce charge below 0.",
                1, false, tags("tempo_hair_trigger"),
                1.0, arch(RUSHER), false));

        add(new MabUpgradeCard("glass_cannon", "Glass Cannon", "GLASS CAN", "◈ II",
                MabUpgradeCategory.POWER, MabUpgradeRarity.ADVANCED,
                "Your launches +2 lines; impacts on you +1 line.",
                "Both effects are permanent and unconditional while owned. " +
                "Trading damage is mathematically bad for you unless you launch more often.",
                1, false, tags("power_glass_cannon"),
                1.0, arch(RUSHER), false));

        // Manual Override: activeCard=true — player-invoked via hotkey when indicator is lit
        add(new MabUpgradeCard("manual_override", "Manual Override", "OVERRIDE", "⦿ III",
                MabUpgradeCategory.TEMPO, MabUpgradeRarity.CRITICAL,
                "At full charge, fire a half-power launch on demand without completing the route.",
                "When charge first hits 100 in a cycle, the OVERRIDE indicator lights on your " +
                "tactical strip. Press Q to open the overlay. Options: FIRE NOW (half power, " +
                "skips route) or HOLD (continue toward full launch). Default: HOLD. " +
                "No charge cost; indicator resets when the cycle ends.",
                1, false, tags("tempo_manual_override"),
                1.0, arch(RUSHER), true));

        // ════════════════════════════════════════════════
        //  WILDCARDS
        // ════════════════════════════════════════════════

        add(new MabUpgradeCard("quiet_storm", "Quiet Storm", "QUIET", "⊕ I",
                MabUpgradeCategory.CHARGE, MabUpgradeRarity.STANDARD,
                "After 10 seconds without a clear, next clear gains +20 charge.",
                "Idle timer resets whenever any line clears. Crossing the 10-second mark " +
                "arms the bonus, which fires on the next non-zero clear then disarms.",
                1, false, tags("charge_quiet_storm"),
                1.0, arch(WILDCARD), false));

        add(new MabUpgradeCard("stockpile", "Stockpile", "STOCKPILE", "⊕ II",
                MabUpgradeCategory.CHARGE, MabUpgradeRarity.ADVANCED,
                "Charge cap raised from 100 to 130.",
                "100 is still the nuke-ready threshold. The 100–130 reserve is held passively; " +
                "it can be spent by other cards (e.g. EMP) or buffer charge that would otherwise cap.",
                1, false, tags("charge_stockpile"),
                1.0, arch(WILDCARD), false));

        add(new MabUpgradeCard("adrenaline_sequence", "Adrenaline Sequence", "ADRENALINE", "⊕ II",
                MabUpgradeCategory.CHARGE, MabUpgradeRarity.ADVANCED,
                "While stack is dangerously high, charge gain is multiplied by 1.5.",
                "Condition: stack's topmost occupied row is at or above row 14 " +
                "(upper 30% of board). Bonus applies continuously while condition holds. " +
                "Comeback lever for the losing player.",
                1, false, tags("charge_adrenaline"),
                1.0, arch(WILDCARD), false));

        add(new MabUpgradeCard("jam", "Jam", "JAM", "⦿ II",
                MabUpgradeCategory.TEMPO, MabUpgradeRarity.ADVANCED,
                "When opponent reaches nuke-ready, their launch route gets +1 random pip that cycle.",
                "When opponent enters nuke-ready, one of their two routes (chosen by seeded RNG) " +
                "has its target raised by 1 for that cycle. Tetris route 4 → 5 or Spin route 2 → 3. " +
                "Resets when their next launch fires.",
                1, false, tags("tempo_jam"),
                1.0, arch(WILDCARD), false));

        // EMP: activeCard=true — auto-pauses on opponent launch, player chooses to cancel or skip
        add(new MabUpgradeCard("emp", "EMP", "EMP", "⦿ III",
                MabUpgradeCategory.TEMPO, MabUpgradeRarity.CRITICAL,
                "Once per match, cancel an opponent's launch by spending 40 charge.",
                "Triggers once per match on opponent's launch. Both boards pause and an overlay " +
                "appears. Options: CANCEL (−40 charge, neutralizes launch, consumes card) or SKIP. " +
                "Default: SKIP. If you have < 40 charge, CANCEL is greyed out.",
                1, false, tags("tempo_emp"),
                1.0, arch(WILDCARD), true));

        // Weapons Modernization: opens the nuke builder mid-match for the owning player.
        // maxStacks=1, not repeatable — the inventory entry is cleared on each DEFCON drop
        // so the card can be offered once per DEFCON level.
        add(new MabUpgradeCard("redesign_nuke", "Weapons Modernization", "REARM", "☢ II",
                MabUpgradeCategory.POWER, MabUpgradeRarity.ADVANCED,
                "Pause and redesign your nuclear warhead (once per DEFCON level).",
                "Opens the Nuke Builder for you immediately. Your opponent's board is paused " +
                "until you confirm your new design. The warhead upgrade takes effect on your " +
                "next launch. Can be drawn once per DEFCON level.",
                1, false, tags("redesign_nuke"),
                1.2, arch(WILDCARD, RUSHER), true));
    }
}
