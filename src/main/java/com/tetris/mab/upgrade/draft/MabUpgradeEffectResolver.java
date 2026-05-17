package com.tetris.mab.upgrade.draft;

import com.tetris.mab.clear.MabClearResult;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * v2 — stateless calculator for upgrade-driven gameplay modifiers.
 * State lives on {@link MabUpgradeInventory}; this class is pure logic.
 * Called from {@code MutuallyAssuredBlocksMatch} after the base
 * {@code MabChargeCalculator} computes the unmodified charge value.
 *
 * <p>All intel-related methods have been removed. No asymmetric-information
 * upgrades exist in the v2 card pool.
 */
public final class MabUpgradeEffectResolver {

    public static final int DEFAULT_TETRIS_GOAL = 4;
    public static final int DEFAULT_SPIN_GOAL   = 2;

    private static final Map<String, String> EFFECT_CLASSIFICATIONS = buildEffectClassifications();

    private MabUpgradeEffectResolver() {}

    public static Set<String> knownEffectTags() {
        return Collections.unmodifiableSet(EFFECT_CLASSIFICATIONS.keySet());
    }

    public static String effectClassification(String tag) {
        return EFFECT_CLASSIFICATIONS.getOrDefault(tag, "UNKNOWN");
    }

    public static boolean isKnownEffectTag(String tag) {
        return EFFECT_CLASSIFICATIONS.containsKey(tag);
    }

    private static Map<String, String> buildEffectClassifications() {
        Map<String, String> m = new LinkedHashMap<>();
        for (String tag : new String[] {
                "charge_clear_bonus_1", "charge_combo_4plus", "charge_streak_stoker",
                "charge_b2b_amplifier", "charge_combo_resonance", "charge_spin_any",
                "charge_spin_zero", "charge_spin_triple", "charge_intercept",
                "charge_spin_chain", "charge_tetris_flat", "charge_clean_tetris",
                "charge_perfect_clear", "charge_wellsmith", "charge_quiet_storm",
                "charge_stockpile", "charge_adrenaline", "tempo_rapid_assembly",
                "tempo_hair_trigger", "tempo_retaliation", "defense_shelters",
                "defense_bunker", "defense_cold_steel", "defense_intercept_str",
                "defense_emergency", "defense_hardened_silos", "defense_dead_hand",
                "power_light_fuse", "power_heavy_warhead", "power_penetrator",
                "power_dirty_payload", "power_glass_cannon", "route_tetris_minus1",
                "route_spin_keep1" }) {
            m.put(tag, "PASSIVE_EFFECT");
        }
        m.put("tempo_manual_override", "ACTIVE_EFFECT");
        m.put("tempo_emp", "ACTIVE_EFFECT");
        m.put("tempo_cascade_reactor", "PASSIVE_EFFECT");
        m.put("tempo_jam", "DOCUMENTED_DEFERRED");
        m.put("defense_reactive_plating", "STATUS_EFFECT");
        m.put("defense_ablative_spin", "DOCUMENTED_DEFERRED");
        m.put("redesign_nuke", "ACTIVE_EFFECT");
        return Collections.unmodifiableMap(m);
    }

    // ── Charge modifiers (per-clear) ─────────────────────────────────────

    /**
     * Applies all stateless upgrade charge bonuses for a single clear event.
     * Multipliers are applied first, flat bonuses second.
     *
     * @param inv     participant's inventory (null → return base)
     * @param result  the clear result (null → return base)
     * @param base    unmodified charge from MabChargeCalculator
     * @return        modified charge (always ≥ 0)
     */
    public static int applyChargeModifiers(MabUpgradeInventory inv,
                                           MabClearResult result,
                                           int base) {
        if (inv == null || result == null) return Math.max(0, base);
        double charge = base;

        // B2B Amplifier: re-apply ratio difference. Base used 1.25; target 1.40. Ratio = 1.12.
        if (result.backToBack() && inv.hasTag("charge_b2b_amplifier")) {
            charge = charge * (1.40 / 1.25);
        }

        // Efficient Reactor: +1 per stack on any scoring clear.
        if (base > 0) {
            int stacks = inv.countTagStacks("charge_clear_bonus_1");
            if (stacks > 0) charge += stacks;
        }

        // Combo Capacitor: +2 if combo ≥ 4.
        if (result.comboCount() >= 4 && inv.hasTag("charge_combo_4plus")) {
            charge += 2;
        }

        // Resonance Chamber: +30 exactly when combo hits 6.
        if (result.comboCount() == 6 && inv.hasTag("charge_combo_resonance")) {
            charge += 30;
        }

        // Perfect Clear Battery: +8 on perfect clears.
        if (result.perfectClear() && inv.hasTag("charge_perfect_clear")) {
            charge += 8;
        }

        // Tetris Doctrine (v2): +6 on any Tetris (spin or not).
        if (result.tetris() && inv.hasTag("charge_tetris_flat")) {
            charge += 6;
        }

        // Clean Well Logistics: +3 on non-spin Tetrises.
        if (result.tetris() && !result.isSpin() && inv.hasTag("charge_clean_tetris")) {
            charge += 3;
        }

        // Spin Doctrine: +2 on any spin clear.
        if (result.isSpin() && inv.hasTag("charge_spin_any")) {
            charge += 2;
        }

        // Wrist Drill: +1 on 0-line spin clears.
        if (result.isSpin() && result.linesCleared() == 0 && inv.hasTag("charge_spin_zero")) {
            charge += 1;
        }

        // TST Program: +8 on spin triple.
        if (result.isSpin() && result.linesCleared() == 3 && inv.hasTag("charge_spin_triple")) {
            charge += 8;
        }

        return Math.max(0, (int) Math.round(charge));
    }

    // ── Stateful charge bonuses (match tracks the extra context) ─────────

    /** Streak Stoker: N bonus charge on the Nth consecutive clear. */
    public static int streakStokerBonus(MabUpgradeInventory inv, int streakCount) {
        if (inv == null || streakCount <= 0) return 0;
        return inv.hasTag("charge_streak_stoker") ? streakCount : 0;
    }

    /**
     * Spin Network: each spin clear in the current cycle earns bonus charge equal to
     * the number of prior spin clears in that cycle (0 for the 1st, 1 for the 2nd, …).
     *
     * @param priorSpinsThisCycle number of spin clears already resolved in this launch cycle
     */
    public static int spinNetworkBonus(MabUpgradeInventory inv, int priorSpinsThisCycle) {
        if (inv == null || priorSpinsThisCycle <= 0) return 0;
        return inv.hasTag("charge_spin_chain") ? priorSpinsThisCycle : 0;
    }

    /** Wellsmith: returns 1.20 when well is Tetris-ready, 1.0 otherwise. */
    public static double wellsmithMultiplier(MabUpgradeInventory inv, boolean tetrisReadyWell) {
        if (inv == null || !tetrisReadyWell) return 1.0;
        return inv.hasTag("charge_wellsmith") ? 1.20 : 1.0;
    }

    /** Adrenaline Sequence: returns 1.5 when stack top-row ≥ 14 (0-indexed), 1.0 otherwise. */
    public static double adrenalineMultiplier(MabUpgradeInventory inv, boolean stackDangerouslyHigh) {
        if (inv == null || !stackDangerouslyHigh) return 1.0;
        return inv.hasTag("charge_adrenaline") ? 1.5 : 1.0;
    }

    /** Hair Trigger: returns 1.25 multiplier on all charge gain. */
    public static double hairTriggerMultiplier(MabUpgradeInventory inv) {
        if (inv == null) return 1.0;
        return inv.hasTag("tempo_hair_trigger") ? 1.25 : 1.0;
    }

    /** Hair Trigger: charge bleed rate in points/sec when not clearing. 0 if not owned. */
    public static int hairTriggerBleedPerSec(MabUpgradeInventory inv) {
        if (inv == null) return 0;
        return inv.hasTag("tempo_hair_trigger") ? 1 : 0;
    }

    /** Quiet Storm: +20 charge when the armed idle bonus fires. */
    public static int quietStormBonus(MabUpgradeInventory inv, boolean armedAndFiring) {
        if (inv == null || !armedAndFiring) return 0;
        return inv.hasTag("charge_quiet_storm") ? 20 : 0;
    }

    /** Stockpile: effective charge cap (100 normally, 130 with this card). */
    public static int effectiveChargeCap(MabUpgradeInventory inv) {
        if (inv != null && inv.hasTag("charge_stockpile")) return 130;
        return 100;
    }

    // ── Route targets ────────────────────────────────────────────────────

    /**
     * v1 compat stub — always returns false in v2 (Tetris Doctrine now gives +6 charge,
     * not an extra route pip). Match code that calls this will simply skip the extra pip.
     */
    public static boolean tetrisDoctrineActive(MabUpgradeInventory inv) {
        return false;
    }

    public static int getTetrisRouteTarget(MabUpgradeInventory inv) {
        if (inv != null && inv.hasTag("route_tetris_minus1")) return DEFAULT_TETRIS_GOAL - 1;
        return DEFAULT_TETRIS_GOAL;
    }

    public static int getSpinRouteTarget(MabUpgradeInventory inv) {
        return DEFAULT_SPIN_GOAL;
    }

    /** True if a spin-route launch should retain 1 spin pip afterward. */
    public static boolean spinRouteKeepsOnePipAfterLaunch(MabUpgradeInventory inv) {
        return inv != null && inv.hasTag("route_spin_keep1");
    }

    // ── Defense / power / tempo hooks ────────────────────────────────────

    /** Extra garbage lines on outgoing launches (Heavy Warhead, Glass Cannon). */
    public static int extraOutgoingGarbage(MabUpgradeInventory inv) {
        if (inv == null) return 0;
        int n = 0;
        if (inv.hasTag("power_heavy_warhead")) n += 1;
        if (inv.hasTag("power_dirty_payload")) n += 1;
        if (inv.hasTag("power_glass_cannon"))  n += 2;
        return n;
    }

    /** Extra garbage lines added to impacts arriving at this participant (Glass Cannon self-penalty). */
    public static int extraIncomingGarbage(MabUpgradeInventory inv) {
        if (inv == null) return 0;
        return inv.hasTag("power_glass_cannon") ? 1 : 0;
    }

    /**
     * Garbage lines mitigated from an incoming impact.
     *
     * @param stackInUpperThird     true when stack top-row ≥ 13 (upper third)
     * @param emergencyAlreadyUsed  true if Emergency Protocols has already fired
     * @param bunkerAlreadyUsed     true if Bunker has already fired
     */
    public static int incomingGarbageMitigation(MabUpgradeInventory inv,
                                                boolean stackInUpperThird,
                                                boolean emergencyAlreadyUsed,
                                                boolean bunkerAlreadyUsed,
                                                int rawLines) {
        if (inv == null) return 0;
        int n = 0;
        if (inv.hasTag("defense_shelters")) n += 1;
        if (inv.hasTag("defense_emergency") && stackInUpperThird && !emergencyAlreadyUsed) n += 2;
        if (inv.hasTag("defense_bunker") && !bunkerAlreadyUsed) {
            // First impact halved (rounded up), in addition to other reductions.
            int afterOthers = Math.max(1, rawLines - n);
            int halved = (afterOthers + 1) / 2;
            n += afterOthers - halved; // extra lines removed by bunker
        }
        return n;
    }

    /** Backward-compatible overload for callers that don't track Bunker state. */
    public static int incomingGarbageMitigation(MabUpgradeInventory inv,
                                                boolean stackInUpperThird,
                                                boolean emergencyAlreadyUsed) {
        return incomingGarbageMitigation(inv, stackInUpperThird, emergencyAlreadyUsed, true, 4);
    }

    /** Tier delta for spin intercept strength (positive = stronger). */
    public static int interceptStrengthDelta(MabUpgradeInventory inv) {
        if (inv == null) return 0;
        return inv.hasTag("defense_intercept_str") ? 1 : 0;
    }

    /** Tier delta applied to opponent's intercepts when targeting our launches. */
    public static int penetratorDelta(MabUpgradeInventory inv) {
        if (inv == null) return 0;
        return inv.hasTag("power_penetrator") ? -1 : 0;
    }

    /** Light the Fuse: +1 line on the very first launch (consumed after). */
    public static int lightTheFuseBonus(MabUpgradeInventory inv, boolean firstLaunchFired) {
        if (inv == null || firstLaunchFired) return 0;
        return inv.hasTag("power_light_fuse") ? 1 : 0;
    }

    /** Charge refund immediately after firing a launch. */
    public static int onLaunchFiredChargeRefund(MabUpgradeInventory inv) {
        if (inv == null) return 0;
        return inv.hasTag("tempo_rapid_assembly") ? 10 : 0;
    }

    /** Charge gained after taking an impact. */
    public static int onImpactTakenCharge(MabUpgradeInventory inv) {
        if (inv == null) return 0;
        return inv.hasTag("tempo_retaliation") ? 20 : 0;
    }

    /**
     * Charge refunded after a full intercept.
     * Cold Steel adds an extra +5 when stack is low.
     *
     * @param stackLow true when stack's topmost row ≤ 8 (top 12 rows empty)
     */
    public static int onInterceptResolvedCharge(MabUpgradeInventory inv, boolean stackLow) {
        if (inv == null) return 0;
        int n = 0;
        if (inv.hasTag("charge_intercept"))   n += 10;
        if (inv.hasTag("defense_cold_steel") && stackLow) n += 5;
        return n;
    }

    /** Backward-compatible overload (no stack-low context). */
    public static int onInterceptResolvedCharge(MabUpgradeInventory inv) {
        return onInterceptResolvedCharge(inv, false);
    }

    /** Reactive Plating: true if the 6-second doubled-intercept window should start. */
    public static boolean reactivePlatingActive(MabUpgradeInventory inv) {
        return inv != null && inv.hasTag("defense_reactive_plating");
    }

    /** Cascade Reactor: while ready, every 3rd combo clear awards a route pip. */
    public static boolean cascadeReactorTriggers(MabUpgradeInventory inv,
                                                 boolean nukeReady,
                                                 int comboCountAfterClear) {
        if (inv == null || !nukeReady || !inv.hasTag("tempo_cascade_reactor")) return false;
        int comboLength = comboCountAfterClear + 1;
        return comboLength > 0 && comboLength % 3 == 0;
    }

    /** True if a single spin intercept may double-mitigate clustered threats. */
    public static boolean ablativeSpinActive(MabUpgradeInventory inv) {
        return inv != null && inv.hasTag("defense_ablative_spin");
    }

    /** Hardened Silos: true if an impact that would cause top-out should be capped instead. */
    public static boolean hardenedSilosActive(MabUpgradeInventory inv) {
        return inv != null && inv.hasTag("defense_hardened_silos");
    }

    /** Dead Hand Protocol: true if the card should trigger on a fatal incoming impact. */
    public static boolean deadHandProtocolActive(MabUpgradeInventory inv) {
        return inv != null && inv.hasTag("defense_dead_hand");
    }

    // ── State sync ────────────────────────────────────────────────────────

    /** Push inventory-derived flags into a participant's simplified state. */
    public static void syncSimplifiedStateConfig(
            MabUpgradeInventory inv,
            com.tetris.mab.clear.MabSimplifiedStrategicState state) {
        if (state == null) return;
        state.setLaunchTetrisGoal(getTetrisRouteTarget(inv));
        state.setLaunchSpinGoal(getSpinRouteTarget(inv));
        state.setSpinRouteKeepsOnePip(spinRouteKeepsOnePipAfterLaunch(inv));
    }
}
