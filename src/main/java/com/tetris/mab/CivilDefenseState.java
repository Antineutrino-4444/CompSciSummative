package com.tetris.mab;

import com.tetris.mab.defense.CivilDefenseMitigation;

/**
 * Mutable per-participant civil-defense state. Tracks how many basic
 * activation charges are stored, how many piece locks the protective
 * shield is still active for, and cumulative reduction stats.
 *
 * <p>Activated by completed {@code CIVIL_DEFENSE} action codes. Consumed
 * (one charge per impact) by {@link #consumeForImpact()} when a real
 * impact resolution happens — never by skipped resolutions.
 *
 * <p>Civil defense is deterministic. There is no random chance.
 */
public class CivilDefenseState {

    public static final int DEFAULT_MAX_CHARGES = 3;

    private int activeCharges;
    private int maxCharges = DEFAULT_MAX_CHARGES;
    private int shieldPiecesRemaining;
    private int totalActivations;
    private int totalBlastReduced;
    private int totalRadiationReduced;
    private int totalDisarmReduced;
    private int totalSiloDamageReduced;
    private boolean emergencyProtocolActive;

    // ─── Step 9 upgrade levels (0..5) ───
    private int shelterLevel;
    private int garbageControlLevel;
    private int emergencyProtocolLevel;

    public int getActiveCharges() { return activeCharges; }
    public int getMaxCharges() { return maxCharges; }
    public int getShieldPiecesRemaining() { return shieldPiecesRemaining; }
    public int getTotalActivations() { return totalActivations; }
    public int getTotalBlastReduced() { return totalBlastReduced; }
    public int getTotalRadiationReduced() { return totalRadiationReduced; }
    public int getTotalDisarmReduced() { return totalDisarmReduced; }
    public int getTotalSiloDamageReduced() { return totalSiloDamageReduced; }
    public boolean isEmergencyProtocolActive() { return emergencyProtocolActive; }

    public void setMaxCharges(int v) { this.maxCharges = Math.max(1, v); }

    public int getShelterLevel() { return shelterLevel; }
    public void setShelterLevel(int v) { this.shelterLevel = clampLevel(v); }
    public int getGarbageControlLevel() { return garbageControlLevel; }
    public void setGarbageControlLevel(int v) { this.garbageControlLevel = clampLevel(v); }
    public int getEmergencyProtocolLevel() { return emergencyProtocolLevel; }
    public void setEmergencyProtocolLevel(int v) { this.emergencyProtocolLevel = clampLevel(v); }

    private static int clampLevel(int v) {
        if (v < 0) return 0;
        if (v > 5) return 5;
        return v;
    }

    /** True if this state can mitigate an incoming impact. */
    public boolean hasProtection() {
        return activeCharges > 0 || shieldPiecesRemaining > 0;
    }

    /** Standard CIVIL_DEFENSE activation. Adds a charge (capped) and
     * extends shield duration; does NOT clear existing duration. */
    public void activateBasic(int shieldPieces) {
        if (activeCharges < maxCharges) activeCharges++;
        if (shieldPieces > 0) {
            shieldPiecesRemaining = Math.max(shieldPiecesRemaining, shieldPieces);
        }
        totalActivations++;
    }

    /** Emergency activation: as basic, plus marks emergency protocol on. */
    public void activateEmergency(int shieldPieces) {
        activateBasic(shieldPieces);
        emergencyProtocolActive = true;
    }

    /**
     * Consumed exactly once per real impact resolution. Returns
     * {@link CivilDefenseMitigation#none()} if there is no active
     * protection. Otherwise consumes one active charge (if any) and
     * returns the corresponding mitigation profile. Shield duration is
     * NOT reset here — it ticks down by piece locks.
     */
    public CivilDefenseMitigation consumeForImpact() {
        if (!hasProtection()) return CivilDefenseMitigation.none();
        int consumed = 0;
        if (activeCharges > 0) {
            activeCharges--;
            consumed = 1;
        }
        CivilDefenseMitigation base = emergencyProtocolActive
                ? CivilDefenseMitigation.emergency(consumed)
                : CivilDefenseMitigation.basic(consumed);
        return applyUpgradeBoosts(base);
    }

    /**
     * Apply Step-9 upgrade boosts on top of the base mitigation profile.
     * Shelter level adds +0.05 to blast/radiation reduction per level.
     * Garbage control adds +1 extraGraceRows per level and +1
     * immediateGarbageReduction once level >= 2.
     */
    private CivilDefenseMitigation applyUpgradeBoosts(CivilDefenseMitigation base) {
        if (base == null || !base.active()) return base;
        if (shelterLevel == 0 && garbageControlLevel == 0) return base;
        double blast = base.blastReductionRatio() + 0.05 * shelterLevel;
        double rad = base.radiationReductionRatio() + 0.05 * shelterLevel;
        int immRed = base.immediateGarbageReduction()
                + (garbageControlLevel >= 2 ? 1 : 0);
        int extraGrace = base.extraGraceRows() + garbageControlLevel;
        return new CivilDefenseMitigation(
                base.active(), base.chargesConsumed(),
                blast, rad,
                base.disarmReductionRatio(), base.siloDamageReductionRatio(),
                immRed, extraGrace,
                base.source() + "+upg");
    }

    /** Called once per defender piece-lock. Decrements shield duration. */
    public void tickPiece() {
        if (shieldPiecesRemaining > 0) {
            shieldPiecesRemaining--;
            if (shieldPiecesRemaining == 0) {
                emergencyProtocolActive = false;
            }
        }
    }

    /** Accumulate reduction stats reported by an impact resolution. */
    public void addReductionStats(int blastReduced, int radiationReduced,
                                  int disarmReduced, int siloReduced) {
        if (blastReduced > 0) totalBlastReduced += blastReduced;
        if (radiationReduced > 0) totalRadiationReduced += radiationReduced;
        if (disarmReduced > 0) totalDisarmReduced += disarmReduced;
        if (siloReduced > 0) totalSiloDamageReduced += siloReduced;
    }

    public String toDebugString() {
        return "CivilDefense{charges=" + activeCharges + "/" + maxCharges
                + " shield=" + shieldPiecesRemaining
                + " emergency=" + emergencyProtocolActive
                + " activations=" + totalActivations
                + " shelter=" + shelterLevel
                + " gc=" + garbageControlLevel
                + " emerg=" + emergencyProtocolLevel
                + "}";
    }
}
