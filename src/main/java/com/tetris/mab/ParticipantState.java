package com.tetris.mab;

import com.tetris.mab.decoy.ActiveDecoyState;
import com.tetris.model.GameState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Per-participant strategic state for a Mutually Assured Blocks match.
 * Wraps the existing single-player {@link GameState} (which remains the
 * sole owner of all gameplay rules) and aggregates the placeholder
 * Step-2 strategic systems around it.
 */
public class ParticipantState {

    private final ParticipantId id;
    private final GameState gameState;

    private final NukeBuildState nukeBuildState = new NukeBuildState();
    private final SiloState siloState = new SiloState();
    private final UpgradeState upgradeState = new UpgradeState();
    private final ActionCodeProgressState actionCodeProgressState = new ActionCodeProgressState();
    private final List<ActiveLaunchState> activeLaunches = new ArrayList<>();
    private final List<IncomingThreatState> incomingThreats = new ArrayList<>();
    private final RadarIntelState radarIntel = new RadarIntelState();
    private final RestraintState restraintState = new RestraintState();
    private final SecondStrikeState secondStrikeState = new SecondStrikeState();
    private final CivilDefenseState civilDefenseState = new CivilDefenseState();
    private final List<ActiveDecoyState> activeDecoys = new ArrayList<>();
    /** Step 21 \u2014 simplified strategic state for skill-based MAB PvE. */
    private final com.tetris.mab.clear.MabSimplifiedStrategicState simplifiedState =
            new com.tetris.mab.clear.MabSimplifiedStrategicState();
    /** Step 24 \u2014 per-participant level-up draft inventory. Not
     *  global, not static. The legacy {@link UpgradeState} above is
     *  bypassed in normal MAB PvE flow. */
    private final com.tetris.mab.upgrade.draft.MabUpgradeInventory upgradeInventory =
            new com.tetris.mab.upgrade.draft.MabUpgradeInventory();
    /** Step 25 - local active-card runtime state (Manual Override, EMP). */
    private final com.tetris.mab.upgrade.draft.MabActiveDoctrineState activeDoctrineState =
            new com.tetris.mab.upgrade.draft.MabActiveDoctrineState();
    /** Step 24 \u2014 tracks per-launch-cycle bookkeeping (e.g.
     *  Tetris Doctrine "once per cycle" flag). */
    private boolean tetrisDoctrineUsedThisCycle = false;
    /** Step 24 \u2014 Emergency Protocols one-shot flag. */
    private boolean emergencyProtocolsUsed = false;
    /** Step 24 \u2014 Bunker one-shot flag (first impact halved). */
    private boolean bunkerUsed = false;
    /** Step 24 \u2014 Hardened Silos one-shot flag (once/match top-out cap). */
    private boolean hardenedSilosUsed = false;
    /** Step 24 \u2014 Dead Hand Protocol one-shot flag. */
    private boolean deadHandUsed = false;
    /** Step 24 \u2014 Light the Fuse one-shot flag (first launch +1 line). */
    private boolean lightTheFuseUsed = false;
    /** Step 24 \u2014 Manual Override indicator: charge reached 100 this cycle. */
    private boolean manualOverrideReadyThisCycle = false;
    /** Step 24 \u2014 consecutive-clear streak for Streak Stoker. */
    private int streakCount = 0;
    /** Step 24 \u2014 spin clears so far in the current launch cycle (Spin Network). */
    private int spinsThisCycle = 0;
    /** Step 24 \u2014 wall-clock ms of the last non-zero clear (-1 = never). */
    private long lastClearTimeMs = -1;
    /** Step 24 \u2014 Quiet Storm 10-second idle bonus is armed. */
    private boolean quietStormArmed = false;
    /** Soft cap on retained decoys (active + recently expired). */
    private static final int DECOY_HISTORY_CAP = 20;

    private int piecesLocked;
    private int linesClearedTotal;
    private int garbageReceivedTotal;
    private int maxStackHeight;
    private boolean toppedOut;
    private String selectedInterceptThreatId;

    public ParticipantState(ParticipantId id, GameState gameState) {
        if (id == null) throw new IllegalArgumentException("id");
        if (gameState == null) throw new IllegalArgumentException("gameState");
        this.id = id;
        this.gameState = gameState;
        // Tag the GameState with this participant id so its TopOutEvent
        // payloads carry a meaningful player id. Single-player gameplay
        // (no listeners attached) is unaffected.
        gameState.setPlayerId(id.name());
    }

    public ParticipantId getId() { return id; }
    public GameState getGameState() { return gameState; }
    public NukeBuildState getNukeBuildState() { return nukeBuildState; }
    public SiloState getSiloState() { return siloState; }
    public UpgradeState getUpgradeState() { return upgradeState; }
    public ActionCodeProgressState getActionCodeProgressState() { return actionCodeProgressState; }
    public com.tetris.mab.action.ActionCodeManager getActionCodeManager() { return actionCodeProgressState.getManager(); }
    public List<ActiveLaunchState> getActiveLaunches() { return activeLaunches; }
    public List<IncomingThreatState> getIncomingThreats() { return incomingThreats; }
    public RadarIntelState getRadarIntel() { return radarIntel; }
    public RadarIntelState getRadarIntelState() { return radarIntel; }
    public RestraintState getRestraintState() { return restraintState; }
    public SecondStrikeState getSecondStrikeState() { return secondStrikeState; }
    public CivilDefenseState getCivilDefenseState() { return civilDefenseState; }

    /** Step 21 \u2014 simplified strategic state (charge / launch progress). */
    public com.tetris.mab.clear.MabSimplifiedStrategicState getSimplifiedState() {
        return simplifiedState;
    }

    /** Step 24 \u2014 per-participant draft inventory. */
    public com.tetris.mab.upgrade.draft.MabUpgradeInventory getUpgradeInventory() {
        return upgradeInventory;
    }

    /** Step 25 - active doctrine cooldown/use state. */
    public com.tetris.mab.upgrade.draft.MabActiveDoctrineState getActiveDoctrineState() {
        return activeDoctrineState;
    }

    /** Step 24 \u2014 Tetris Doctrine "once per launch cycle" flag. */
    public boolean isTetrisDoctrineUsedThisCycle() { return tetrisDoctrineUsedThisCycle; }
    public void setTetrisDoctrineUsedThisCycle(boolean v) { this.tetrisDoctrineUsedThisCycle = v; }

    /** Step 24 \u2014 Emergency Protocols once-per-match flag. */
    public boolean isEmergencyProtocolsUsed() { return emergencyProtocolsUsed; }
    public void setEmergencyProtocolsUsed(boolean v) { this.emergencyProtocolsUsed = v; }

    /** Step 24 \u2014 Bunker once-per-match flag. */
    public boolean isBunkerUsed() { return bunkerUsed; }
    public void setBunkerUsed(boolean v) { this.bunkerUsed = v; }

    /** Step 24 \u2014 Hardened Silos once-per-match flag. */
    public boolean isHardenedSilosUsed() { return hardenedSilosUsed; }
    public void setHardenedSilosUsed(boolean v) { this.hardenedSilosUsed = v; }

    /** Step 24 \u2014 Dead Hand Protocol once-per-match flag. */
    public boolean isDeadHandUsed() { return deadHandUsed; }
    public void setDeadHandUsed(boolean v) { this.deadHandUsed = v; }

    /** Step 24 \u2014 Light the Fuse once-per-match flag. */
    public boolean isLightTheFuseUsed() { return lightTheFuseUsed; }
    public void setLightTheFuseUsed(boolean v) { this.lightTheFuseUsed = v; }

    /** Step 24 \u2014 Manual Override indicator: true while charge >= 100 this cycle. */
    public boolean isManualOverrideReadyThisCycle() { return manualOverrideReadyThisCycle; }
    public void setManualOverrideReadyThisCycle(boolean v) { this.manualOverrideReadyThisCycle = v; }

    /** Step 24 \u2014 consecutive-clear streak counter (Streak Stoker). */
    public int getStreakCount() { return streakCount; }
    public void setStreakCount(int v) { this.streakCount = v; }

    /** Step 24 \u2014 spin clears in the current launch cycle (Spin Network). */
    public int getSpinsThisCycle() { return spinsThisCycle; }
    public void setSpinsThisCycle(int v) { this.spinsThisCycle = v; }

    /** Step 24 \u2014 wall-clock ms of the last non-zero clear (-1 = never cleared). */
    public long getLastClearTimeMs() { return lastClearTimeMs; }
    public void setLastClearTimeMs(long v) { this.lastClearTimeMs = v; }

    /** Step 24 \u2014 Quiet Storm 10-second idle bonus is armed and waiting to fire. */
    public boolean isQuietStormArmed() { return quietStormArmed; }
    public void setQuietStormArmed(boolean v) { this.quietStormArmed = v; }

    // ─── Step 11: decoy storage ────────────────────────────
    public List<ActiveDecoyState> getActiveDecoys() {
        return Collections.unmodifiableList(new ArrayList<>(activeDecoys));
    }

    public void addActiveDecoy(ActiveDecoyState decoy) {
        if (decoy == null) return;
        activeDecoys.add(decoy);
        // Cap retained list to avoid unbounded growth.
        while (activeDecoys.size() > DECOY_HISTORY_CAP) {
            // Drop oldest expired entry first; if none expired, drop oldest.
            int dropIdx = -1;
            for (int i = 0; i < activeDecoys.size(); i++) {
                if (activeDecoys.get(i).isExpired()) { dropIdx = i; break; }
            }
            if (dropIdx < 0) dropIdx = 0;
            activeDecoys.remove(dropIdx);
        }
    }

    public int getActiveDecoyCount() {
        int n = 0;
        for (ActiveDecoyState d : activeDecoys) if (d.isActive()) n++;
        return n;
    }

    public int getFalseLaunchSignatureCount() {
        int n = 0;
        for (ActiveDecoyState d : activeDecoys) {
            if (d.isActive() && d.createsFalseLaunchSignature()) n += d.getFalseLaunchCount();
        }
        return n;
    }

    public int getFalseThreatSignatureCount() {
        int n = 0;
        for (ActiveDecoyState d : activeDecoys) {
            if (d.isActive() && d.createsFalseThreatSignature()) n += d.getFalseThreatCount();
        }
        return n;
    }

    public int getActiveConfidencePenaltyAgainstScanners() {
        int p = 0;
        for (ActiveDecoyState d : activeDecoys) if (d.isActive()) p += d.getConfidencePenalty();
        return p;
    }

    public Optional<ActiveDecoyState> findActiveDecoyById(String decoyId) {
        if (decoyId == null) return Optional.empty();
        for (ActiveDecoyState d : activeDecoys) {
            if (decoyId.equals(d.getDecoyId()) && d.isActive()) return Optional.of(d);
        }
        return Optional.empty();
    }

    /** Tick all active decoys by one owner piece. */
    public void tickDecoys() {
        for (ActiveDecoyState d : activeDecoys) {
            if (d.isActive()) d.tickPiece();
        }
    }

    /** Drop expired decoys, but keep recent history under the cap. */
    public void removeExpiredDecoys() {
        // Keep all entries; the cap in addActiveDecoy handles growth.
        // Caller-driven removal is exposed for tests if needed.
        activeDecoys.removeIf(ActiveDecoyState::isExpired);
    }

    public String decoyDebugString() {
        ActiveDecoyState first = null;
        for (ActiveDecoyState d : activeDecoys) {
            if (d.isActive()) { first = d; break; }
        }
        return "Feints{active=" + getActiveDecoyCount()
                + " falseLaunch=" + getFalseLaunchSignatureCount()
                + " falseThreat=" + getFalseThreatSignatureCount()
                + " penalty=" + getActiveConfidencePenaltyAgainstScanners()
                + (first == null ? "" : " first=" + first.getDecoyId())
                + "}";
    }

    public int getPiecesLocked() { return piecesLocked; }
    public int getLinesClearedTotal() { return linesClearedTotal; }
    public int getGarbageReceivedTotal() { return garbageReceivedTotal; }
    public int getMaxStackHeight() { return maxStackHeight; }
    public boolean isToppedOut() { return toppedOut; }

    public String getSelectedInterceptThreatId() { return selectedInterceptThreatId; }
    public void setSelectedInterceptThreatId(String threatId) { this.selectedInterceptThreatId = threatId; }
    public void clearSelectedInterceptThreatId() { this.selectedInterceptThreatId = null; }

    // ───── Mutators called by the match coordinator ────────────

    void incrementPiecesLocked() { piecesLocked++; }
    void addLinesCleared(int n) { if (n > 0) linesClearedTotal += n; }
    void addGarbageReceived(int n) { if (n > 0) garbageReceivedTotal += n; }
    void markToppedOut() { toppedOut = true; }

    void refreshMaxStackHeight() {
        try {
            int h = gameState.getBoard().getStackHeight();
            if (h > maxStackHeight) maxStackHeight = h;
        } catch (RuntimeException ignored) {}
    }

    public String toDebugString() {
        return "Participant{" + id
                + " pieces=" + piecesLocked
                + " lines=" + linesClearedTotal
                + " garbage=" + garbageReceivedTotal
                + " maxHeight=" + maxStackHeight
                + (toppedOut ? " TOPPED_OUT" : "")
                + " " + nukeBuildState.toDebugString()
                + " " + actionDebugString()
                + " " + siloState.toDebugString()
                + " " + launchesDebugString()
                + " " + threatsDebugString()
                + " " + civilDefenseState.toDebugString()
                + " " + upgradeState.toDebugString()
                + " " + activeDoctrineState.toDebugString()
                + " " + radarIntel.toDebugString()
                + " " + decoyDebugString()
                + "}";
    }

    private String launchesDebugString() {
        int impactReady = 0;
        for (ActiveLaunchState l : activeLaunches) {
            if (l.isImpactReady()) impactReady++;
        }
        ActiveLaunchState first = activeLaunches.isEmpty() ? null : activeLaunches.get(0);
        return "Launches{active=" + activeLaunches.size()
                + " impactReady=" + impactReady
                + (first == null ? ""
                        : " first=" + first.getLaunchId() + " phase=" + first.getPhase())
                + "}";
    }

    private String threatsDebugString() {
        int impactReady = 0;
        int interceptable = 0;
        int fullyIntercepted = 0;
        int partial = 0;
        for (IncomingThreatState t : incomingThreats) {
            if (t.getStatus() == com.tetris.mab.launch.ThreatStatus.IMPACT_READY) impactReady++;
            if (t.getStatus() == com.tetris.mab.launch.ThreatStatus.WARNING_ACTIVE) interceptable++;
            if (t.getStatus() == com.tetris.mab.launch.ThreatStatus.INTERCEPTED) fullyIntercepted++;
            if (t.getInterceptMitigation() != null
                    && t.getInterceptMitigation().getTotalInterceptPowerApplied() > 0
                    && !t.getInterceptMitigation().isFullyIntercepted()) {
                partial++;
            }
        }
        IncomingThreatState first = incomingThreats.isEmpty() ? null : incomingThreats.get(0);
        return "Threats{incoming=" + incomingThreats.size()
                + " impactReady=" + impactReady
                + " interceptable=" + interceptable
                + " fullyIntercepted=" + fullyIntercepted
                + " partialMitig=" + partial
                + " target=" + (selectedInterceptThreatId == null ? "-" : selectedInterceptThreatId)
                + (first == null ? ""
                        : " first=" + first.getThreatId()
                                + " impactDelay=" + first.getWarningPiecesRemaining())
                + "}";
    }

    private String actionDebugString() {
        com.tetris.mab.action.ActionCodeAttempt active = actionCodeProgressState.getActiveAttempt();
        com.tetris.mab.action.ActionCodeAttempt pending = actionCodeProgressState.getPendingConfirmationAttempt();
        if (pending != null) {
            return "Action{pending " + pending.getDefinition().getId() + "}";
        }
        if (active != null) {
            com.tetris.mab.action.ActionCodeTokenRequirement next = active.getExpectedNextRequirement();
            String nextStr = next == null ? "-" : String.valueOf(next.requiredLineCount());
            return "Action{" + active.getDefinition().getId()
                    + " " + active.getProgressIndex() + "/" + active.getDefinition().sequenceLength()
                    + " next=" + nextStr + "}";
        }
        return "Action{none}";
    }
}
