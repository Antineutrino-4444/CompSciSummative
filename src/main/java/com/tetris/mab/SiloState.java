package com.tetris.mab;

/**
 * Per-participant silo state. Step 6 adds real damage / repair
 * mutators and the {@link SiloDamageState} threshold refresh.
 * Hardening / launch-security / mitigation effects are NOT applied
 * here yet — that's a later upgrade-effects step.
 */
public class SiloState {

    private int integrity = 100;
    private int hardeningLevel = 0;
    private int deepBunkerLevel = 0;
    private int distributedStockpileLevel = 0;
    private int launchSecurityLevel = 0;
    private int assemblySpeedLevel = 0;
    private int blastDoorLevel = 0;
    private int camouflageLevel = 0;
    private SiloDamageState damageState = SiloDamageState.STABLE;

    public int getIntegrity() { return integrity; }
    public void setIntegrity(int integrity) {
        this.integrity = clamp(integrity);
        refreshDamageState();
    }
    public int getHardeningLevel() { return hardeningLevel; }
    public void setHardeningLevel(int v) { this.hardeningLevel = clampLevel(v); }
    public int getDeepBunkerLevel() { return deepBunkerLevel; }
    public void setDeepBunkerLevel(int v) { this.deepBunkerLevel = clampLevel(v); }
    public int getDistributedStockpileLevel() { return distributedStockpileLevel; }
    public void setDistributedStockpileLevel(int v) { this.distributedStockpileLevel = clampLevel(v); }
    public int getLaunchSecurityLevel() { return launchSecurityLevel; }
    public void setLaunchSecurityLevel(int v) { this.launchSecurityLevel = clampLevel(v); }
    public int getAssemblySpeedLevel() { return assemblySpeedLevel; }
    public void setAssemblySpeedLevel(int v) { this.assemblySpeedLevel = clampLevel(v); }
    public int getBlastDoorLevel() { return blastDoorLevel; }
    public void setBlastDoorLevel(int v) { this.blastDoorLevel = clampLevel(v); }
    public int getCamouflageLevel() { return camouflageLevel; }
    public void setCamouflageLevel(int v) { this.camouflageLevel = clampLevel(v); }
    public SiloDamageState getDamageState() { return damageState; }
    public void setDamageState(SiloDamageState s) { if (s != null) this.damageState = s; }

    /** Apply {@code amount} damage to integrity, clamped at 0. */
    public void applyDamage(int amount) {
        if (amount <= 0) return;
        this.integrity = clamp(integrity - amount);
        refreshDamageState();
    }

    /** Repair {@code amount} integrity, clamped at 100. */
    public void repair(int amount) {
        if (amount <= 0) return;
        this.integrity = clamp(integrity + amount);
        refreshDamageState();
    }

    private static int clamp(int v) {
        if (v < 0) return 0;
        if (v > 100) return 100;
        return v;
    }

    private static int clampLevel(int v) {
        if (v < 0) return 0;
        if (v > 5) return 5;
        return v;
    }

    private void refreshDamageState() {
        if (integrity >= 75)      damageState = SiloDamageState.STABLE;
        else if (integrity >= 50) damageState = SiloDamageState.DAMAGED;
        else if (integrity >= 25) damageState = SiloDamageState.COMPROMISED;
        else                      damageState = SiloDamageState.CRITICAL;
    }

    public String toDebugString() {
        return "Silo{HP=" + integrity + " " + damageState
                + " hard=" + hardeningLevel
                + " bunker=" + deepBunkerLevel
                + " distributed=" + distributedStockpileLevel
                + " security=" + launchSecurityLevel
                + " assembly=" + assemblySpeedLevel
                + " blastDoors=" + blastDoorLevel
                + " camo=" + camouflageLevel + "}";
    }
}
