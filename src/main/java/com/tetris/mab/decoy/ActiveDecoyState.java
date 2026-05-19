package com.tetris.mab.decoy;

import com.tetris.mab.ParticipantId;
import com.tetris.mab.action.ActionType;

/**
 * Mutable per-decoy state. Owned by the participant that activated the
 * decoy. Lifetime is measured in owner piece locks.
 */
public class ActiveDecoyState {

    private final String decoyId;
    private final DecoyType type;
    private final ActionType actionType;
    private final ParticipantId owner;
    private final ParticipantId target;
    private final String displayName;
    private final int durationPiecesTotal;
    private int durationPiecesRemaining;
    private final int falseLaunchCount;
    private final int falseThreatCount;
    private final int confidencePenalty;
    private final boolean createsFalseLaunchSignature;
    private final boolean createsFalseThreatSignature;
    private final boolean falsifiesDoctrine;
    private final boolean falsifiesBuildProgress;
    private final boolean masksRealLaunch;
    private String linkedLaunchId;
    private DecoyVisibility visibility;
    private boolean expired;

    public ActiveDecoyState(String decoyId,
                            DecoyType type,
                            ActionType actionType,
                            ParticipantId owner,
                            ParticipantId target,
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
                            String linkedLaunchId) {
        this.decoyId = decoyId;
        this.type = type;
        this.actionType = actionType;
        this.owner = owner;
        this.target = target;
        this.displayName = displayName;
        this.durationPiecesTotal = Math.max(0, durationPieces);
        this.durationPiecesRemaining = this.durationPiecesTotal;
        this.falseLaunchCount = Math.max(0, falseLaunchCount);
        this.falseThreatCount = Math.max(0, falseThreatCount);
        this.confidencePenalty = Math.max(0, confidencePenalty);
        this.createsFalseLaunchSignature = createsFalseLaunchSignature;
        this.createsFalseThreatSignature = createsFalseThreatSignature;
        this.falsifiesDoctrine = falsifiesDoctrine;
        this.falsifiesBuildProgress = falsifiesBuildProgress;
        this.masksRealLaunch = masksRealLaunch;
        this.linkedLaunchId = linkedLaunchId;
        // Visibility defaults: false-signature decoys masquerade as real;
        // masking decoys hide; doctrine / silo-heat decoys are SUSPECTED.
        if (masksRealLaunch) this.visibility = DecoyVisibility.HIDDEN;
        else if (createsFalseLaunchSignature || createsFalseThreatSignature)
            this.visibility = DecoyVisibility.VISIBLE_AS_REAL;
        else this.visibility = DecoyVisibility.SUSPECTED;
    }

    public String getDecoyId() { return decoyId; }
    public DecoyType getType() { return type; }
    public ActionType getActionType() { return actionType; }
    public ParticipantId getOwner() { return owner; }
    public ParticipantId getTarget() { return target; }
    public String getDisplayName() { return displayName; }
    public int getDurationPiecesTotal() { return durationPiecesTotal; }
    public int getDurationPiecesRemaining() { return durationPiecesRemaining; }
    public int getFalseLaunchCount() { return falseLaunchCount; }
    public int getFalseThreatCount() { return falseThreatCount; }
    public int getConfidencePenalty() { return confidencePenalty; }
    public boolean createsFalseLaunchSignature() { return createsFalseLaunchSignature; }
    public boolean createsFalseThreatSignature() { return createsFalseThreatSignature; }
    public boolean falsifiesDoctrine() { return falsifiesDoctrine; }
    public boolean falsifiesBuildProgress() { return falsifiesBuildProgress; }
    public boolean masksRealLaunch() { return masksRealLaunch; }
    public String getLinkedLaunchId() { return linkedLaunchId; }
    public void setLinkedLaunchId(String linkedLaunchId) { this.linkedLaunchId = linkedLaunchId; }
    public DecoyVisibility getVisibility() { return visibility; }
    public boolean isExpired() { return expired; }

    /** Decrement remaining duration; expire on reaching zero. */
    public void tickPiece() {
        if (expired) return;
        if (durationPiecesRemaining > 0) durationPiecesRemaining--;
        if (durationPiecesRemaining <= 0) markExpired();
    }

    public void markExpired() {
        expired = true;
        durationPiecesRemaining = 0;
        visibility = DecoyVisibility.EXPIRED;
    }

    public void markIdentified() {
        if (expired) return;
        visibility = DecoyVisibility.IDENTIFIED_AS_DECOY;
    }

    public boolean isActive() {
        return !expired && durationPiecesRemaining > 0;
    }

    public String toDebugString() {
        return "Feint{" + decoyId
                + " type=" + type
                + " owner=" + owner
                + " remaining=" + durationPiecesRemaining + "/" + durationPiecesTotal
                + " vis=" + visibility
                + (linkedLaunchId == null ? "" : " linked=" + linkedLaunchId)
                + (expired ? " EXPIRED" : "")
                + "}";
    }
}
