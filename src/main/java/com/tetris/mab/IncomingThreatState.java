package com.tetris.mab;

import com.tetris.mab.intercept.InterceptDefinition;
import com.tetris.mab.intercept.InterceptMitigationState;
import com.tetris.mab.launch.ThreatStatus;
import com.tetris.mab.nuke.NukeDoctrineType;
import com.tetris.mab.nuke.NukeSizeCategory;

/**
 * Mutable defender-side view of an inbound launch. Created when the
 * attacker's launch countdown completes and the launch enters
 * IN_FLIGHT. Step 5 only tracks the threat through warning ticks until
 * it is marked IMPACT_READY for a future impact resolver.
 */
public class IncomingThreatState {

    private final String threatId;
    private final String launchId;
    private final ParticipantId attacker;
    private final ParticipantId defender;
    private final String knownOrEstimatedDoctrine;
    private final String nukeDesignId;
    private final String nukeDisplayName;
    private final NukeDoctrineType doctrineType;
    private final NukeSizeCategory sizeCategory;
    private final int warningPiecesTotal;
    private final String flightTimerId;

    private int warningPiecesRemaining;
    private ThreatStatus status;
    private boolean intercepted;
    private final InterceptMitigationState interceptMitigation = new InterceptMitigationState();

    public IncomingThreatState(String threatId,
                               String launchId,
                               ParticipantId attacker,
                               ParticipantId defender,
                               String knownOrEstimatedDoctrine,
                               String nukeDesignId,
                               String nukeDisplayName,
                               NukeDoctrineType doctrineType,
                               NukeSizeCategory sizeCategory,
                               int warningPiecesTotal,
                               String flightTimerId) {
        if (threatId == null) throw new IllegalArgumentException("threatId");
        if (launchId == null) throw new IllegalArgumentException("launchId");
        if (attacker == null) throw new IllegalArgumentException("attacker");
        if (defender == null) throw new IllegalArgumentException("defender");
        this.threatId = threatId;
        this.launchId = launchId;
        this.attacker = attacker;
        this.defender = defender;
        this.knownOrEstimatedDoctrine = knownOrEstimatedDoctrine;
        this.nukeDesignId = nukeDesignId;
        this.nukeDisplayName = nukeDisplayName;
        this.doctrineType = doctrineType;
        this.sizeCategory = sizeCategory;
        this.warningPiecesTotal = Math.max(0, warningPiecesTotal);
        this.warningPiecesRemaining = this.warningPiecesTotal;
        this.flightTimerId = flightTimerId;
        this.status = ThreatStatus.WARNING_ACTIVE;
        this.intercepted = false;
    }

    // ─────────── Accessors ───────────

    public String getThreatId() { return threatId; }
    public String getLaunchId() { return launchId; }
    public ParticipantId getAttacker() { return attacker; }
    public ParticipantId getDefender() { return defender; }
    public String getKnownOrEstimatedDoctrine() { return knownOrEstimatedDoctrine; }
    public String getNukeDesignId() { return nukeDesignId; }
    public String getNukeDisplayName() { return nukeDisplayName; }
    public NukeDoctrineType getDoctrineType() { return doctrineType; }
    public NukeSizeCategory getSizeCategory() { return sizeCategory; }
    public int getWarningPiecesTotal() { return warningPiecesTotal; }
    public int getWarningPiecesRemaining() { return warningPiecesRemaining; }
    public String getFlightTimerId() { return flightTimerId; }
    public ThreatStatus getStatus() { return status; }
    public boolean isIntercepted() { return intercepted; }
    public InterceptMitigationState getInterceptMitigation() { return interceptMitigation; }

    // ─────────── Mutators ───────────

    public void decrementWarningPieces() {
        if (warningPiecesRemaining > 0) warningPiecesRemaining--;
    }

    public void markImpactReady() {
        this.status = ThreatStatus.IMPACT_READY;
        this.warningPiecesRemaining = 0;
    }

    public void markIntercepted() {
        this.status = ThreatStatus.INTERCEPTED;
        this.intercepted = true;
    }

    /**
     * Apply partial intercept mitigation. Only valid while the threat is
     * still in {@link ThreatStatus#WARNING_ACTIVE}; later phases ignore.
     */
    public void applyPartialIntercept(InterceptDefinition definition) {
        if (status != ThreatStatus.WARNING_ACTIVE) return;
        interceptMitigation.addPartialMitigation(definition);
    }

    /** Mark this threat fully intercepted: status → INTERCEPTED, mitigation neutralises all damage. */
    public void markFullyIntercepted(InterceptDefinition definition) {
        interceptMitigation.markFullyIntercepted(definition);
        this.status = ThreatStatus.INTERCEPTED;
        this.intercepted = true;
    }

    public void markResolved() {
        this.status = ThreatStatus.RESOLVED;
    }

    public void markCancelled() {
        this.status = ThreatStatus.CANCELLED;
    }

    public String toDebugString() {
        return "Threat{" + threatId
                + " launch=" + launchId
                + " " + attacker + "→" + defender
                + " doctrine=" + doctrineType
                + " size=" + sizeCategory
                + " warning=" + warningPiecesRemaining + "/" + warningPiecesTotal
                + " status=" + status
                + (intercepted ? " INTERCEPTED" : "")
                + "}";
    }
}
