package com.tetris.mab.decoy;

import com.tetris.mab.action.ActionType;

/**
 * Immutable definition of a decoy / misinformation effect. Defaults
 * are produced by {@link DecoyRegistry#createDefault()}.
 */
public record DecoyDefinition(
        DecoyType type,
        ActionType actionType,
        String id,
        String displayName,
        int durationPieces,
        int falseLaunchCount,
        int falseThreatCount,
        int confidencePenalty,
        boolean createsFalseLaunchSignature,
        boolean createsFalseThreatSignature,
        boolean falsifiesDoctrine,
        boolean falsifiesBuildProgress,
        boolean masksRealLaunch,
        String description) {

    public DecoyDefinition {
        if (type == null) throw new IllegalArgumentException("type");
        if (actionType == null) throw new IllegalArgumentException("actionType");
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id");
        if (displayName == null || displayName.isBlank())
            throw new IllegalArgumentException("displayName");
        if (durationPieces < 0) throw new IllegalArgumentException("durationPieces");
        if (falseLaunchCount < 0) throw new IllegalArgumentException("falseLaunchCount");
        if (falseThreatCount < 0) throw new IllegalArgumentException("falseThreatCount");
        if (confidencePenalty < 0) throw new IllegalArgumentException("confidencePenalty");
    }
}
