package com.tetris.mab.ai;

import com.tetris.mab.ParticipantId;
import com.tetris.mab.balance.MabBalanceProfile;
import com.tetris.mab.balance.MabBalanceProfiles;

/**
 * Mutable per-participant state for the strategic AI driver.
 *
 * <p>Rewritten from first principles for the new AI but preserves the
 * field shape consumed by older UI code. Cooldowns never decrement past
 * zero. Telemetry counters live on this object so the simulation
 * harness can surface them without poking into the driver.
 */
public final class MabAiState {

    private final ParticipantId participantId;
    private MabAiArchetype archetype;
    private MabAiDifficulty difficulty;
    private MabBalanceProfile balanceProfile;
    private boolean enabled;

    private int aiTicks;
    private int simulatedPieces;
    private int chargeBudget;

    private int launchCooldownPieces;
    private int routeScanCooldownPieces;
    private int feintCooldownPieces;
    private int defenseCooldownPieces;
    private int upgradeCooldownPieces;

    private int totalDecisions;
    private int launchesStarted;
    private int routeScansPerformed;
    private int feintsActivated;
    private int civilDefenseActivations;
    private int impactsResolved;
    private int upgradesPicked;
    private int interceptsAttempted;
    private int interceptsSucceeded;

    private String lastDecisionSummary = "";

    public MabAiState(ParticipantId participantId,
                     MabAiArchetype archetype,
                     MabAiDifficulty difficulty) {
        if (participantId == null) throw new IllegalArgumentException("participantId");
        if (archetype == null) throw new IllegalArgumentException("archetype");
        if (difficulty == null) throw new IllegalArgumentException("difficulty");
        this.participantId = participantId;
        this.archetype = archetype;
        this.difficulty = difficulty;
        this.balanceProfile = MabBalanceProfiles.standardPve();
        this.enabled = false;
    }

    public ParticipantId getParticipantId() { return participantId; }
    public MabAiArchetype getArchetype() { return archetype; }
    public MabAiDifficulty getDifficulty() { return difficulty; }
    public MabBalanceProfile getBalanceProfile() { return balanceProfile; }
    public boolean isEnabled() { return enabled; }
    public int getAiTicks() { return aiTicks; }
    public int getSimulatedPieces() { return simulatedPieces; }
    public int getChargeBudget() { return chargeBudget; }
    public int getLaunchCooldownPieces() { return launchCooldownPieces; }
    public int getRouteScanCooldownPieces() { return routeScanCooldownPieces; }
    public int getFeintCooldownPieces() { return feintCooldownPieces; }
    public int getDefenseCooldownPieces() { return defenseCooldownPieces; }
    public int getUpgradeCooldownPieces() { return upgradeCooldownPieces; }
    public int getTotalDecisions() { return totalDecisions; }
    public int getLaunchesStarted() { return launchesStarted; }
    public int getRouteScansPerformed() { return routeScansPerformed; }
    public int getFeintsActivated() { return feintsActivated; }
    public int getCivilDefenseActivations() { return civilDefenseActivations; }
    public int getImpactsResolved() { return impactsResolved; }
    public int getUpgradesPicked() { return upgradesPicked; }
    public int getInterceptsAttempted() { return interceptsAttempted; }
    public int getInterceptsSucceeded() { return interceptsSucceeded; }
    public String getLastDecisionSummary() { return lastDecisionSummary; }

    public void enable()  { this.enabled = true; }
    public void disable() { this.enabled = false; }

    public void setArchetype(MabAiArchetype archetype) {
        if (archetype != null) this.archetype = archetype;
    }
    public void setDifficulty(MabAiDifficulty difficulty) {
        if (difficulty != null) this.difficulty = difficulty;
    }
    public void setBalanceProfile(MabBalanceProfile profile) {
        if (profile != null) this.balanceProfile = profile;
    }

    public void incrementAiTicks() { aiTicks++; }
    public void addSimulatedPiece() { simulatedPieces++; }

    public void addChargeBudget(int amount) {
        if (amount > 0) chargeBudget += amount;
    }
    public void spendChargeBudget(int amount) {
        if (amount <= 0) return;
        chargeBudget = Math.max(0, chargeBudget - amount);
    }

    public void setLaunchCooldownPieces(int v)   { launchCooldownPieces   = Math.max(0, v); }
    public void setRouteScanCooldownPieces(int v) { routeScanCooldownPieces = Math.max(0, v); }
    public void setFeintCooldownPieces(int v)     { feintCooldownPieces     = Math.max(0, v); }
    public void setDefenseCooldownPieces(int v)  { defenseCooldownPieces  = Math.max(0, v); }
    public void setUpgradeCooldownPieces(int v)  { upgradeCooldownPieces  = Math.max(0, v); }

    public void tickCooldowns() {
        if (launchCooldownPieces   > 0) launchCooldownPieces--;
        if (routeScanCooldownPieces > 0) routeScanCooldownPieces--;
        if (feintCooldownPieces     > 0) feintCooldownPieces--;
        if (defenseCooldownPieces  > 0) defenseCooldownPieces--;
        if (upgradeCooldownPieces  > 0) upgradeCooldownPieces--;
    }

    public void recordLaunch()           { launchesStarted++; }
    public void recordRouteScan()        { routeScansPerformed++; }
    public void recordFeint()            { feintsActivated++; }
    public void recordCivilDefense()     { civilDefenseActivations++; }
    public void recordImpactResolution() { impactsResolved++; }
    public void recordUpgradePick()      { upgradesPicked++; }
    public void recordInterceptAttempt(boolean success) {
        interceptsAttempted++;
        if (success) interceptsSucceeded++;
    }
    public void recordDecision()         { totalDecisions++; }

    public void setLastDecision(MabAiDecision d) {
        if (d == null) { lastDecisionSummary = ""; return; }
        StringBuilder sb = new StringBuilder();
        sb.append(d.type());
        if (!d.detail().isEmpty()) sb.append('(').append(d.detail()).append(')');
        sb.append(d.executed() ? " ok" : " skipped");
        if (!d.message().isEmpty()) sb.append(": ").append(d.message());
        this.lastDecisionSummary = sb.toString();
    }

    public String toDebugString() {
        return "MabAiState{" + participantId
                + " arch=" + archetype
                + " diff=" + difficulty
                + " enabled=" + enabled
                + " ticks=" + aiTicks
                + " simPieces=" + simulatedPieces
                + " budget=" + chargeBudget
                + " cd[L=" + launchCooldownPieces
                + " R=" + routeScanCooldownPieces
                + " F=" + feintCooldownPieces
                + " S=" + defenseCooldownPieces
                + " U=" + upgradeCooldownPieces + "]"
                + " launches=" + launchesStarted
                + " routeScans=" + routeScansPerformed
                + " feints=" + feintsActivated
                + " civDef=" + civilDefenseActivations
                + " impacts=" + impactsResolved
                + " upgrades=" + upgradesPicked
                + " intercept=" + interceptsSucceeded + "/" + interceptsAttempted
                + " last=" + lastDecisionSummary
                + "}";
    }
}
