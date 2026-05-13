package com.tetris.mab.decoy;

import com.tetris.mab.action.ActionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registry of default {@link DecoyDefinition}s. Step 11. */
public final class DecoyRegistry {

    private final Map<DecoyType, DecoyDefinition> byType;
    private final Map<ActionType, DecoyDefinition> byActionType;

    private DecoyRegistry(List<DecoyDefinition> defs) {
        Map<DecoyType, DecoyDefinition> byT = new LinkedHashMap<>();
        Map<ActionType, DecoyDefinition> byA = new LinkedHashMap<>();
        for (DecoyDefinition d : defs) {
            byT.put(d.type(), d);
            byA.put(d.actionType(), d);
        }
        this.byType = Collections.unmodifiableMap(byT);
        this.byActionType = Collections.unmodifiableMap(byA);
    }

    public static DecoyRegistry createDefault() {
        List<DecoyDefinition> defs = new ArrayList<>();
        defs.add(new DecoyDefinition(
                DecoyType.DECOY_LAUNCH, ActionType.DECOY_LAUNCH,
                "decoy_launch", "Decoy Launch",
                12, 1, 1, 10,
                true, true, false, false, false,
                "Produces a false launch-like radar signature."));
        defs.add(new DecoyDefinition(
                DecoyType.GHOST_MIRV, ActionType.GHOST_MIRV,
                "ghost_mirv", "Ghost MIRV",
                16, 3, 3, 15,
                true, true, false, false, false,
                "Produces multiple false MIRV-like signatures."));
        defs.add(new DecoyDefinition(
                DecoyType.FALSE_DOCTRINE_SIGNAL, ActionType.FALSE_DOCTRINE_SIGNAL,
                "false_doctrine_signal", "False Doctrine Signal",
                14, 0, 0, 10,
                false, false, true, false, false,
                "Misreports the apparent doctrine of the current nuke."));
        defs.add(new DecoyDefinition(
                DecoyType.DUMMY_SILO_HEAT, ActionType.DUMMY_SILO_HEAT,
                "dummy_silo_heat", "Dummy Silo Heat",
                14, 0, 0, 10,
                false, false, false, true, false,
                "Exaggerates or fakes build-charge / silo activity."));
        defs.add(new DecoyDefinition(
                DecoyType.MASKED_LAUNCH, ActionType.MASKED_LAUNCH,
                "masked_launch", "Masked Launch",
                10, 0, 0, 20,
                false, false, false, false, true,
                "Real launch action that starts a launch with reduced radar visibility."));
        return new DecoyRegistry(defs);
    }

    public DecoyDefinition getByActionType(ActionType actionType) {
        return byActionType.get(actionType);
    }

    public DecoyDefinition getByType(DecoyType type) {
        return byType.get(type);
    }

    public List<DecoyDefinition> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(byType.values()));
    }
}
