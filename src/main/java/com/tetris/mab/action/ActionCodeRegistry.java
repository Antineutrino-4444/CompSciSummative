package com.tetris.mab.action;

import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.nuke.NukeDoctrineType;
import com.tetris.mab.nuke.NukeSizeCategory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Default {@link ActionCodeDefinition} library + per-nuke launch builders. */
public final class ActionCodeRegistry {

    private final Map<String, ActionCodeDefinition> byId = new LinkedHashMap<>();
    private final Map<ActionType, ActionCodeDefinition> byType = new LinkedHashMap<>();
    private final List<String> ambiguityWarnings;

    private ActionCodeRegistry(List<ActionCodeDefinition> defs) {
        for (ActionCodeDefinition d : defs) {
            byId.put(d.getId(), d);
            byType.putIfAbsent(d.getActionType(), d);
        }
        this.ambiguityWarnings = Collections.unmodifiableList(
                ActionCodeAmbiguityChecker.findAmbiguities(defs));
    }

    // ─────────────────────── Default library ────────────────

    public static ActionCodeRegistry createDefault() {
        List<ActionCodeDefinition> defs = new ArrayList<>();

        // ─── Launches (HARD_FOUR_CONFIRM by default) ───
        // The trailing C4 is non-replaceable; targeted anchors break
        // remaining same-length collisions while leaving spins useful
        // at the other positions.
        defs.add(launchHardFour("micro_launch", "Micro Launch",
                ActionType.MICRO_LAUNCH,
                List.of(normal(1)),
                false));
        defs.add(launchHardFour("tactical_launch", "Tactical Launch",
                ActionType.TACTICAL_LAUNCH,
                List.of(normal(1), anchored(2)),
                false));
        defs.add(launchHardFour("theater_launch", "Theater Launch",
                ActionType.THEATER_LAUNCH,
                List.of(anchored(2), normal(3)),
                true));
        defs.add(launchHardFour("strategic_launch", "Strategic Launch",
                ActionType.STRATEGIC_LAUNCH,
                List.of(anchored(4), normal(3)),
                true));
        defs.add(launchHardFour("dirty_launch", "Dirty Launch",
                ActionType.DIRTY_LAUNCH,
                List.of(anchored(1), normal(3), normal(2)),
                false));
        defs.add(launchHardFour("heavy_split_launch", "Heavy Split Launch",
                ActionType.HEAVY_SPLIT_LAUNCH,
                List.of(anchored(3), anchored(2), normal(3)),
                true));
        defs.add(launchHardFour("concrete_blaster_launch", "Concrete Blaster Launch",
                ActionType.CONCRETE_BLASTER_LAUNCH,
                List.of(normal(2), anchored(4), normal(2)),
                true));
        defs.add(launchHardFour("superheavy_launch", "Superheavy Launch",
                ActionType.SUPERHEAVY_LAUNCH,
                List.of(normal(4), anchored(3), normal(4)),
                true));
        defs.add(launchHardFour("doomsday_launch", "Doomsday Launch",
                ActionType.DOOMSDAY_LAUNCH,
                List.of(normal(4), anchored(4), normal(4), anchored(4)),
                true));

        // ─── Defense ───
        defs.add(actionFromTokens("emergency_intercept", "Emergency Intercept",
                ActionType.EMERGENCY_INTERCEPT, ActionCategory.DEFENSE,
                List.of(normal(1), anchored(2), anchored(1)),
                false, false, true, false,
                ActionConfirmationMode.NONE,
                "Quick intercept of an incoming threat."));
        defs.add(actionFromTokens("standard_intercept", "Standard Intercept",
                ActionType.STANDARD_INTERCEPT, ActionCategory.DEFENSE,
                List.of(normal(1), normal(2), anchored(3)),
                false, false, true, false,
                ActionConfirmationMode.NONE,
                "Standard intercept of an incoming threat."));
        defs.add(actionFromTokens("full_intercept", "Full Intercept",
                ActionType.FULL_INTERCEPT, ActionCategory.DEFENSE,
                List.of(normal(1), anchored(2), normal(3), anchored(2), normal(1)),
                true, false, true, false,
                ActionConfirmationMode.KEYBOARD_CONFIRM,
                "Full-spectrum intercept; requires explicit confirmation."));
        defs.add(actionFromTokens("civil_defense", "Civil Defense",
                ActionType.CIVIL_DEFENSE, ActionCategory.DEFENSE,
                List.of(normal(1), anchored(1), normal(2)),
                false, false, false, false,
                ActionConfirmationMode.NONE,
                "Boost civilian survival."));

        // ─── Retired analysis branch ───
        defs.add(actionFromTokens("route_scan", "Route Analysis",
                ActionType.ROUTE_SCAN, ActionCategory.ANALYSIS,
                List.of(normal(2), normal(1), anchored(2)),
                false, false, false, false,
                ActionConfirmationMode.NONE, "Analyze route pressure."));

        // ─── Utility ───
        defs.add(actionFromTokens("silo_harden", "Silo Harden",
                ActionType.SILO_HARDEN, ActionCategory.UTILITY,
                List.of(anchored(2), normal(2), anchored(2)),
                false, false, false, false,
                ActionConfirmationMode.NONE, "Harden the silo against damage."));
        defs.add(actionFromTokens("counterlaunch_prep", "Counterlaunch Prep",
                ActionType.COUNTERLAUNCH_PREP, ActionCategory.UTILITY,
                List.of(anchored(3), normal(1), anchored(3)),
                false, false, false, false,
                ActionConfirmationMode.NONE, "Prepare a counter-launch posture."));
        defs.add(actionFromTokens("emp_pulse", "EMP Pulse",
                ActionType.EMP_PULSE, ActionCategory.UTILITY,
                List.of(normal(1), normal(2), anchored(2), normal(1)),
                false, false, false, false,
                ActionConfirmationMode.NONE, "Disrupt opponent launch timing."));
        defs.add(actionFromTokens("concrete_blaster_arm", "Concrete Blaster Arm",
                ActionType.CONCRETE_BLASTER_ARM, ActionCategory.UTILITY,
                List.of(normal(2), anchored(4), normal(2)),
                true, false, false, false,
                ActionConfirmationMode.KEYBOARD_CONFIRM,
                "Arm the Concrete Blaster doctrine."));

        // ─── Restraint ───
        defs.add(actionFromTokens("treaty_restraint_lock", "Treaty / Restraint Lock",
                ActionType.TREATY_RESTRAINT_LOCK, ActionCategory.RESTRAINT,
                List.of(normal(1), anchored(1), normal(1), anchored(1)),
                false, false, false, false,
                ActionConfirmationMode.KEYBOARD_CONFIRM, "Lock self into restraint."));

        // ─── Retired feint branch ───
        defs.add(actionFromTokens("feint_launch", "Feint Launch",
                ActionType.FEINT_LAUNCH, ActionCategory.FEINT,
                List.of(normal(1), anchored(3), normal(1)),
                false, false, false, false,
                ActionConfirmationMode.KEYBOARD_CONFIRM, "Fake a launch."));
        defs.add(actionFromTokens("ghost_split", "Ghost Split",
                ActionType.GHOST_SPLIT, ActionCategory.FEINT,
                List.of(normal(2), anchored(1), normal(2), normal(1)),
                false, false, false, false,
                ActionConfirmationMode.KEYBOARD_CONFIRM, "Fake a split launch signature."));
        defs.add(actionFromTokens("false_doctrine_signal", "False Doctrine Signal",
                ActionType.FALSE_DOCTRINE_SIGNAL, ActionCategory.FEINT,
                List.of(anchored(3), anchored(1), normal(1)),
                false, false, false, false,
                ActionConfirmationMode.NONE, "Broadcast a misleading doctrine."));
        defs.add(actionFromTokens("dummy_silo_heat", "Dummy Silo Heat",
                ActionType.DUMMY_SILO_HEAT, ActionCategory.FEINT,
                List.of(normal(2), anchored(2), normal(1)),
                false, false, false, false,
                ActionConfirmationMode.NONE, "Generate fake silo heat."));
        defs.add(actionFromTokens("masked_launch", "Masked Launch",
                ActionType.MASKED_LAUNCH, ActionCategory.FEINT,
                List.of(normal(1), anchored(2), normal(1), anchored(3)),
                true, true, false, true,
                ActionConfirmationMode.KEYBOARD_CONFIRM,
                "Real launch hidden behind a feint."));

        return new ActionCodeRegistry(defs);
    }

    /** Build a launch definition that ends in a hard 4-line confirmation. */
    private static ActionCodeDefinition launchHardFour(String id, String name,
                                                       ActionType type,
                                                       List<ActionCodeTokenRequirement> preTokens,
                                                       boolean strategic) {
        List<ActionCodeTokenRequirement> seq = new ArrayList<>(preTokens.size() + 1);
        seq.addAll(preTokens);
        seq.add(ActionCodeTokenRequirement.hardConfirmFour());
        return new ActionCodeDefinition(
                id, name, type, ActionCategory.LAUNCH, seq,
                strategic, true, false, true,
                ActionConfirmationMode.HARD_FOUR_CONFIRM,
                "Launch action; ends in hard 4 confirmation.");
    }

    /** Generic action definition with a hand-built token sequence. */
    private static ActionCodeDefinition actionFromTokens(String id, String name,
                                                         ActionType type,
                                                         ActionCategory category,
                                                         List<ActionCodeTokenRequirement> sequence,
                                                         boolean strategic,
                                                         boolean requiresArmedNuke,
                                                         boolean requiresIncomingThreat,
                                                         boolean consumesNukeCharge,
                                                         ActionConfirmationMode mode,
                                                         String description) {
        return new ActionCodeDefinition(id, name, type, category, sequence,
                strategic, requiresArmedNuke, requiresIncomingThreat,
                consumesNukeCharge, mode, description);
    }

    private static ActionCodeTokenRequirement normal(int n) {
        return ActionCodeTokenRequirement.normal(n);
    }

    private static ActionCodeTokenRequirement anchored(int n) {
        return ActionCodeTokenRequirement.anchored(n);
    }

    // ─────────────────────── Lookup ────────────────────────

    public ActionCodeDefinition getByType(ActionType type) {
        return type == null ? null : byType.get(type);
    }

    public ActionCodeDefinition getById(String id) {
        return id == null ? null : byId.get(id);
    }

    public List<ActionCodeDefinition> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(byId.values()));
    }

    public List<String> getAmbiguityWarnings() {
        return ambiguityWarnings;
    }

    // ─────────────────────── Per-nuke launch builders ────────

    public ActionCodeDefinition launchDefinitionForNuke(NukeDesign design, int defconLevel) {
        if (design == null) throw new IllegalArgumentException("design");
        List<Integer> code = design.effectiveLaunchCode(defconLevel);
        if (code == null || code.isEmpty()) {
            // Fall back to a single 1 so we still produce a valid definition.
            code = Arrays.asList(1);
        }
        ActionType type = mapDoctrineToActionType(design);
        boolean strategic = isStrategicForNuke(design, code);
        return ActionCodeDefinition.fromLineCounts(
                "launch_" + design.getId(),
                "Launch " + design.getDisplayName(),
                type, ActionCategory.LAUNCH, code,
                strategic, true, false, true,
                ActionConfirmationMode.KEYBOARD_CONFIRM,
                "Launch the currently armed " + design.getDisplayName() + ".");
    }

    public ActionCodeDefinition launchDefinitionForNukeWithHardFourConfirm(NukeDesign design,
                                                                           int defconLevel) {
        if (design == null) throw new IllegalArgumentException("design");
        List<Integer> code = design.effectiveLaunchCode(defconLevel);
        if (code == null) code = Arrays.asList(1);

        List<ActionCodeTokenRequirement> seq = new ArrayList<>(code.size() + 1);
        for (Integer c : code) {
            seq.add(ActionCodeTokenRequirement.normal(c));
        }
        seq.add(ActionCodeTokenRequirement.hardConfirmFour());

        ActionType type = mapDoctrineToActionType(design);
        boolean strategic = isStrategicForNuke(design, code);
        return new ActionCodeDefinition(
                "launch_" + design.getId() + "_confirm4",
                "Launch " + design.getDisplayName() + " Confirm 4",
                type, ActionCategory.LAUNCH, seq,
                strategic, true, false, true,
                ActionConfirmationMode.HARD_FOUR_CONFIRM,
                "Launch the armed " + design.getDisplayName()
                        + " with a hard 4-line confirmation.");
    }

    private static ActionType mapDoctrineToActionType(NukeDesign design) {
        NukeDoctrineType d = design.getDoctrineType();
        if (d == null) d = NukeDoctrineType.PLACEHOLDER;
        switch (d) {
            case DIRTY_BOMB: return ActionType.DIRTY_LAUNCH;
            case MIRV: return ActionType.HEAVY_SPLIT_LAUNCH;
            case CONCRETE_BLASTER: return ActionType.CONCRETE_BLASTER_LAUNCH;
            case DOOMSDAY: return ActionType.DOOMSDAY_LAUNCH;
            default: break;
        }
        NukeSizeCategory s = design.getSizeCategory();
        if (s == null) return ActionType.TACTICAL_LAUNCH;
        return switch (s) {
            case MICRO -> ActionType.MICRO_LAUNCH;
            case TACTICAL -> ActionType.TACTICAL_LAUNCH;
            case THEATER -> ActionType.THEATER_LAUNCH;
            case STRATEGIC -> ActionType.STRATEGIC_LAUNCH;
            case SUPERHEAVY -> ActionType.SUPERHEAVY_LAUNCH;
            case DOOMSDAY_SCALE -> ActionType.DOOMSDAY_LAUNCH;
        };
    }

    private static boolean isStrategicForNuke(NukeDesign design, List<Integer> code) {
        NukeSizeCategory s = design.getSizeCategory();
        if (s != null) {
            switch (s) {
                case THEATER:
                case STRATEGIC:
                case SUPERHEAVY:
                case DOOMSDAY_SCALE:
                    return true;
                default:
                    break;
            }
        }
        NukeDoctrineType d = design.getDoctrineType();
        if (d == NukeDoctrineType.MIRV
                || d == NukeDoctrineType.CONCRETE_BLASTER
                || d == NukeDoctrineType.DOOMSDAY) return true;
        return code != null && code.size() >= 3;
    }
}
