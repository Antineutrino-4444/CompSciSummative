package com.tetris.mab.upgrade.draft;

/**
 * Per-participant runtime state for active doctrine cards. Ownership stays in
 * {@link MabUpgradeInventory}; this object only tracks use/cooldown facts.
 */
public final class MabActiveDoctrineState {

    private boolean manualOverrideUsedThisCycle;
    private boolean empUsedThisMatch;
    private MabActiveDoctrineType lastDoctrineUsed;
    private String lastResult = "";

    public boolean isManualOverrideUsedThisCycle() {
        return manualOverrideUsedThisCycle;
    }

    public void setManualOverrideUsedThisCycle(boolean manualOverrideUsedThisCycle) {
        this.manualOverrideUsedThisCycle = manualOverrideUsedThisCycle;
    }

    public void resetManualOverrideCycle() {
        this.manualOverrideUsedThisCycle = false;
    }

    public boolean isEmpUsedThisMatch() {
        return empUsedThisMatch;
    }

    public void setEmpUsedThisMatch(boolean empUsedThisMatch) {
        this.empUsedThisMatch = empUsedThisMatch;
    }

    public MabActiveDoctrineType getLastDoctrineUsed() {
        return lastDoctrineUsed;
    }

    public String getLastResult() {
        return lastResult;
    }

    public void recordUse(MabActiveDoctrineType type, String result) {
        this.lastDoctrineUsed = type;
        this.lastResult = result == null ? "" : result;
    }

    public String toDebugString() {
        return "ActiveDoctrine{manualUsedCycle=" + manualOverrideUsedThisCycle
                + " empUsedMatch=" + empUsedThisMatch
                + " last=" + (lastDoctrineUsed == null ? "-" : lastDoctrineUsed.name())
                + " result=" + lastResult
                + "}";
    }
}
