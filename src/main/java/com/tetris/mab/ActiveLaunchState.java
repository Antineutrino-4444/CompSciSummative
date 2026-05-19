package com.tetris.mab;

import com.tetris.mab.intercept.InterceptDefinition;
import com.tetris.mab.intercept.InterceptMitigationState;
import com.tetris.mab.launch.LaunchPhase;
import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.nuke.NukeDoctrineType;
import com.tetris.mab.nuke.NukeSizeCategory;

/**
 * Mutable per-launch state owned by the attacker {@link ParticipantState}.
 * Step 5 fills this in for real (was a placeholder); impact resolution
 * itself still happens in a later step — this state simply tracks the
 * launch through countdown and impact-delay phases until it is marked
 * IMPACT_READY for a future resolver.
 */
public class ActiveLaunchState {

    private final String launchId;
    private final ParticipantId attacker;
    private final ParticipantId defender;
    private final String nukeDesignId;
    private final String nukeDisplayName;
    private final NukeDoctrineType doctrineType;
    private final NukeSizeCategory sizeCategory;
    private final NukeDesign nukeDesign;
    private final int defconAtLaunch;
    private final int launchCountdownPieces;
    private final int warningPieces;
    private final String launchTimerId;

    private String flightTimerId;
    private LaunchPhase phase;
    private boolean inFlight;
    private boolean impactReady;
    private boolean manualOverride;
    private boolean empWeakened;
    private boolean deadHandCounterLaunch;
    private int extraGarbageLines;
    private final InterceptMitigationState interceptMitigation = new InterceptMitigationState();

    public ActiveLaunchState(String launchId,
                             ParticipantId attacker,
                             ParticipantId defender,
                             String nukeDesignId,
                             String nukeDisplayName,
                             NukeDoctrineType doctrineType,
                             NukeSizeCategory sizeCategory,
                             NukeDesign nukeDesign,
                             int defconAtLaunch,
                             int launchCountdownPieces,
                             int warningPieces,
                             String launchTimerId,
                             LaunchPhase initialPhase) {
        if (launchId == null) throw new IllegalArgumentException("launchId");
        if (attacker == null) throw new IllegalArgumentException("attacker");
        if (defender == null) throw new IllegalArgumentException("defender");
        if (initialPhase != LaunchPhase.AUTHORIZED && initialPhase != LaunchPhase.COUNTDOWN) {
            throw new IllegalArgumentException("initial phase must be AUTHORIZED or COUNTDOWN");
        }
        this.launchId = launchId;
        this.attacker = attacker;
        this.defender = defender;
        this.nukeDesignId = nukeDesignId;
        this.nukeDisplayName = nukeDisplayName;
        this.doctrineType = doctrineType;
        this.sizeCategory = sizeCategory;
        this.nukeDesign = nukeDesign;
        this.defconAtLaunch = defconAtLaunch;
        this.launchCountdownPieces = Math.max(0, launchCountdownPieces);
        this.warningPieces = Math.max(0, warningPieces);
        this.launchTimerId = launchTimerId;
        this.phase = initialPhase;
        this.inFlight = false;
        this.impactReady = false;
    }

    // ─────────── Accessors ───────────

    public String getLaunchId() { return launchId; }
    public ParticipantId getAttacker() { return attacker; }
    public ParticipantId getDefender() { return defender; }
    public String getNukeDesignId() { return nukeDesignId; }
    public String getNukeDisplayName() { return nukeDisplayName; }
    public NukeDoctrineType getDoctrineType() { return doctrineType; }
    public NukeSizeCategory getSizeCategory() { return sizeCategory; }
    public NukeDesign getNukeDesign() { return nukeDesign; }
    public int getDefconAtLaunch() { return defconAtLaunch; }
    public int getLaunchCountdownPieces() { return launchCountdownPieces; }
    public int getWarningPieces() { return warningPieces; }
    public String getLaunchTimerId() { return launchTimerId; }
    public String getFlightTimerId() { return flightTimerId; }
    public LaunchPhase getPhase() { return phase; }
    public boolean isInFlight() { return inFlight; }
    public boolean isImpactReady() { return impactReady; }
    public boolean isManualOverride() { return manualOverride; }
    public boolean isEmpWeakened() { return empWeakened; }
    public boolean isDeadHandCounterLaunch() { return deadHandCounterLaunch; }
    public int getExtraGarbageLines() { return extraGarbageLines; }
    public InterceptMitigationState getInterceptMitigation() { return interceptMitigation; }

    // ─────────── Mutators ───────────

    public void markCountdownStarted() {
        this.phase = LaunchPhase.COUNTDOWN;
        this.inFlight = false;
        this.impactReady = false;
    }

    public void markInFlight(String flightTimerId) {
        this.flightTimerId = flightTimerId;
        this.phase = LaunchPhase.IN_FLIGHT;
        this.inFlight = true;
        this.impactReady = false;
    }

    public void markImpactReady() {
        this.phase = LaunchPhase.IMPACT_READY;
        this.inFlight = true;
        this.impactReady = true;
    }

    public void markResolved() {
        this.phase = LaunchPhase.RESOLVED;
        this.inFlight = false;
        this.impactReady = true;
    }

    public void markCancelled() {
        this.phase = LaunchPhase.CANCELLED;
        this.inFlight = false;
        this.impactReady = false;
    }

    public void markManualOverride() {
        this.manualOverride = true;
    }

    public void markEmpWeakened() {
        this.empWeakened = true;
    }

    public void markDeadHandCounterLaunch() {
        this.deadHandCounterLaunch = true;
    }

    public void addExtraGarbageLines(int rows) {
        if (rows > 0) extraGarbageLines += rows;
    }

    /** Stack partial intercept mitigation; does NOT change phase. */
    public void applyPartialIntercept(InterceptDefinition definition) {
        interceptMitigation.addPartialMitigation(definition);
    }

    /** Mark this launch fully intercepted: phase → CANCELLED, mitigation neutralises all damage. */
    public void markFullyIntercepted(InterceptDefinition definition) {
        interceptMitigation.markFullyIntercepted(definition);
        markCancelled();
    }

    public String toDebugString() {
        return "Launch{" + launchId
                + " " + attacker + "→" + defender
                + " design=" + nukeDesignId
                + " doctrine=" + doctrineType
                + " size=" + sizeCategory
                + " defcon=" + defconAtLaunch
                + " countdown=" + launchCountdownPieces
                + " impactDelay=" + warningPieces
                + " phase=" + phase
                + (manualOverride ? " manualOverride" : "")
                + (empWeakened ? " empWeakened" : "")
                + (deadHandCounterLaunch ? " deadHand" : "")
                + (extraGarbageLines > 0 ? " extraGarbage=" + extraGarbageLines : "")
                + (inFlight ? " inFlight" : "")
                + (impactReady ? " IMPACT_READY" : "")
                + "}";
    }
}
