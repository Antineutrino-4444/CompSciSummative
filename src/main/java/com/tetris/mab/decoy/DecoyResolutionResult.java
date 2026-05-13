package com.tetris.mab.decoy;

import com.tetris.mab.ParticipantId;
import com.tetris.mab.action.ActionType;

/** Result of attempting to activate a decoy. Step 11. */
public record DecoyResolutionResult(
        boolean success,
        DecoyType type,
        ActionType actionType,
        ParticipantId owner,
        ParticipantId target,
        String decoyId,
        String linkedLaunchId,
        int durationPieces,
        int falseLaunchCount,
        int falseThreatCount,
        int confidencePenalty,
        String message) {

    public static DecoyResolutionResult success(DecoyDefinition def,
                                                 ParticipantId owner,
                                                 ParticipantId target,
                                                 String decoyId,
                                                 String linkedLaunchId,
                                                 String message) {
        return new DecoyResolutionResult(true, def.type(), def.actionType(),
                owner, target, decoyId, linkedLaunchId,
                def.durationPieces(), def.falseLaunchCount(),
                def.falseThreatCount(), def.confidencePenalty(),
                message == null ? "ok" : message);
    }

    public static DecoyResolutionResult failed(DecoyType type,
                                                ActionType actionType,
                                                ParticipantId owner,
                                                ParticipantId target,
                                                String message) {
        return new DecoyResolutionResult(false, type, actionType,
                owner, target, null, null, 0, 0, 0, 0,
                message == null ? "failed" : message);
    }
}
