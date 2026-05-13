package com.tetris.mab.intercept;

import com.tetris.mab.action.ActionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registry of {@link InterceptDefinition}s, indexed by both type and action. */
public final class InterceptRegistry {

    private final Map<InterceptType, InterceptDefinition> byType = new LinkedHashMap<>();
    private final Map<ActionType, InterceptDefinition> byAction = new LinkedHashMap<>();

    private InterceptRegistry(List<InterceptDefinition> defs) {
        for (InterceptDefinition d : defs) {
            byType.put(d.interceptType(), d);
            byAction.put(d.actionType(), d);
        }
    }

    public static InterceptRegistry createDefault() {
        List<InterceptDefinition> defs = new ArrayList<>();
        defs.add(new InterceptDefinition(
                InterceptType.EMERGENCY, ActionType.EMERGENCY_INTERCEPT,
                "Emergency Intercept", 2,
                0.35, 0.25, 0.20, 0.20,
                false,
                "Short, fast intercept code. Always partial; reduces damage."));
        defs.add(new InterceptDefinition(
                InterceptType.STANDARD, ActionType.STANDARD_INTERCEPT,
                "Standard Intercept", 4,
                0.60, 0.50, 0.40, 0.40,
                true,
                "Balanced intercept; can fully intercept smaller threats."));
        defs.add(new InterceptDefinition(
                InterceptType.FULL, ActionType.FULL_INTERCEPT,
                "Full Intercept", 8,
                0.90, 0.80, 0.75, 0.75,
                true,
                "Long strategic intercept; strongest mitigation."));
        return new InterceptRegistry(defs);
    }

    public InterceptDefinition getByActionType(ActionType actionType) {
        return byAction.get(actionType);
    }

    public InterceptDefinition getByInterceptType(InterceptType interceptType) {
        return byType.get(interceptType);
    }

    public List<InterceptDefinition> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(byType.values()));
    }
}
