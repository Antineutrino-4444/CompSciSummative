package com.tetris.mab.intercept;

import com.tetris.mab.ParticipantId;

/** Immutable result returned by {@link InterceptResolver#resolveIntercept}. */
public record InterceptResult(
        InterceptOutcome outcome,
        InterceptType interceptType,
        String launchId,
        String threatId,
        ParticipantId defender,
        ParticipantId attacker,
        String nukeDesignId,
        String nukeDisplayName,
        int interceptPower,
        int threatResistance,
        double blastReductionApplied,
        double radiationReductionApplied,
        double disarmReductionApplied,
        double siloDamageReductionApplied,
        String message) {

    public static InterceptResult full(InterceptDefinition def, String launchId, String threatId,
                                       ParticipantId defender, ParticipantId attacker,
                                       String nukeDesignId, String nukeDisplayName,
                                       int threatResistance, String message) {
        return new InterceptResult(InterceptOutcome.FULLY_INTERCEPTED,
                def.interceptType(), launchId, threatId, defender, attacker,
                nukeDesignId, nukeDisplayName,
                def.interceptPower(), threatResistance,
                def.blastReductionRatio(), def.radiationReductionRatio(),
                def.disarmReductionRatio(), def.siloDamageReductionRatio(),
                message);
    }

    public static InterceptResult partial(InterceptDefinition def, String launchId, String threatId,
                                          ParticipantId defender, ParticipantId attacker,
                                          String nukeDesignId, String nukeDisplayName,
                                          int threatResistance, String message) {
        return new InterceptResult(InterceptOutcome.PARTIALLY_INTERCEPTED,
                def.interceptType(), launchId, threatId, defender, attacker,
                nukeDesignId, nukeDisplayName,
                def.interceptPower(), threatResistance,
                def.blastReductionRatio(), def.radiationReductionRatio(),
                def.disarmReductionRatio(), def.siloDamageReductionRatio(),
                message);
    }

    public static InterceptResult failed(InterceptOutcome outcome, InterceptDefinition def,
                                         String launchId, String threatId,
                                         ParticipantId defender, String message) {
        InterceptType type = def == null ? null : def.interceptType();
        int power = def == null ? 0 : def.interceptPower();
        return new InterceptResult(outcome, type, launchId, threatId, defender, null,
                null, null, power, 0, 0.0, 0.0, 0.0, 0.0, message);
    }
}
