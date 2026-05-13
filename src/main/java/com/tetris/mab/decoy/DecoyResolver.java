package com.tetris.mab.decoy;

import com.tetris.mab.ParticipantState;

/**
 * Builds {@link ActiveDecoyState} instances from a definition. The
 * resolver does NOT mutate participant decoy lists \u2014 the match
 * coordinator owns storage and side effects.
 */
public final class DecoyResolver {

    public DecoyResolutionResult activateDecoy(DecoyDefinition definition,
                                                ParticipantState owner,
                                                ParticipantState target,
                                                String decoyId,
                                                String linkedLaunchId) {
        if (definition == null || owner == null || target == null || decoyId == null) {
            return DecoyResolutionResult.failed(
                    definition == null ? null : definition.type(),
                    definition == null ? null : definition.actionType(),
                    owner == null ? null : owner.getId(),
                    target == null ? null : target.getId(),
                    "null definition/owner/target/decoyId");
        }
        ActiveDecoyState state = new ActiveDecoyState(
                decoyId,
                definition.type(),
                definition.actionType(),
                owner.getId(),
                target.getId(),
                definition.displayName(),
                definition.durationPieces(),
                definition.falseLaunchCount(),
                definition.falseThreatCount(),
                definition.confidencePenalty(),
                definition.createsFalseLaunchSignature(),
                definition.createsFalseThreatSignature(),
                definition.falsifiesDoctrine(),
                definition.falsifiesBuildProgress(),
                definition.masksRealLaunch(),
                linkedLaunchId);
        owner.addActiveDecoy(state);
        return DecoyResolutionResult.success(definition,
                owner.getId(), target.getId(),
                decoyId, linkedLaunchId,
                "decoy activated: " + definition.id());
    }
}
