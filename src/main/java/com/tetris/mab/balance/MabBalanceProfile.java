package com.tetris.mab.balance;

import com.tetris.mab.ai.MabAiDifficulty;

/**
 * Step 19 — immutable PvE balance profile.
 *
 * <p>This is Balance Pass v1: a single struct that centralizes the
 * AI pacing knobs (per-tick charge gain, per-decision charge step,
 * post-action cooldowns) plus a small set of PvE pressure gates
 * (early-launch delay, minimum ticks before decoy/upgrade/civil
 * defence) and a few preferred safety values.
 *
 * <p>Profiles are model-only and Swing-independent. Values are
 * provisional and intended to be tuned by the headless simulation
 * harness.
 */
public final class MabBalanceProfile {

    // --- identity ---
    private final String id;
    private final String displayName;
    private final String description;

    // --- AI pacing per difficulty ---
    private final int easyAiChargeGainPerTick;
    private final int normalAiChargeGainPerTick;
    private final int hardAiChargeGainPerTick;
    private final int debugAiChargeGainPerTick;

    private final int easyAiAddChargeStep;
    private final int normalAiAddChargeStep;
    private final int hardAiAddChargeStep;
    private final int debugAiAddChargeStep;

    private final int easyLaunchCooldown;
    private final int normalLaunchCooldown;
    private final int hardLaunchCooldown;
    private final int debugLaunchCooldown;

    private final int easyRadarCooldown;
    private final int normalRadarCooldown;
    private final int hardRadarCooldown;
    private final int debugRadarCooldown;

    private final int easyDecoyCooldown;
    private final int normalDecoyCooldown;
    private final int hardDecoyCooldown;
    private final int debugDecoyCooldown;

    private final int easyDefenseCooldown;
    private final int normalDefenseCooldown;
    private final int hardDefenseCooldown;
    private final int debugDefenseCooldown;

    private final int easyUpgradeCooldown;
    private final int normalUpgradeCooldown;
    private final int hardUpgradeCooldown;
    private final int debugUpgradeCooldown;

    // --- PvE pressure ---
    private final int aiEarlyLaunchDelayTicks;
    private final int aiMinimumTicksBeforeDecoy;
    private final int aiMinimumTicksBeforeUpgrade;
    private final int aiMinimumTicksBeforeCivilDefense;
    private final int maxAiLaunchesBeforeTick80;
    private final int maxAiLaunchesBeforeTick160;

    // --- strategic safety ---
    private final int preferredImpactGracePieces;
    private final int preferredMaxImmediateGarbageRows;
    private final int preferredCivilDefenseStartingCharges;
    private final int preferredCivilDefenseShieldPieces;

    // --- metadata ---
    private final boolean defaultForPve;

    private MabBalanceProfile(Builder b) {
        if (b.id == null || b.id.isBlank()) throw new IllegalArgumentException("id");
        if (b.displayName == null || b.displayName.isBlank()) throw new IllegalArgumentException("displayName");
        this.id = b.id;
        this.displayName = b.displayName;
        this.description = b.description == null ? "" : b.description;

        this.easyAiChargeGainPerTick   = nonNegative(b.easyAiChargeGainPerTick,   "easyAiChargeGainPerTick");
        this.normalAiChargeGainPerTick = nonNegative(b.normalAiChargeGainPerTick, "normalAiChargeGainPerTick");
        this.hardAiChargeGainPerTick   = nonNegative(b.hardAiChargeGainPerTick,   "hardAiChargeGainPerTick");
        this.debugAiChargeGainPerTick  = nonNegative(b.debugAiChargeGainPerTick,  "debugAiChargeGainPerTick");

        this.easyAiAddChargeStep   = nonNegative(b.easyAiAddChargeStep,   "easyAiAddChargeStep");
        this.normalAiAddChargeStep = nonNegative(b.normalAiAddChargeStep, "normalAiAddChargeStep");
        this.hardAiAddChargeStep   = nonNegative(b.hardAiAddChargeStep,   "hardAiAddChargeStep");
        this.debugAiAddChargeStep  = nonNegative(b.debugAiAddChargeStep,  "debugAiAddChargeStep");

        this.easyLaunchCooldown   = positive(b.easyLaunchCooldown,   "easyLaunchCooldown");
        this.normalLaunchCooldown = positive(b.normalLaunchCooldown, "normalLaunchCooldown");
        this.hardLaunchCooldown   = positive(b.hardLaunchCooldown,   "hardLaunchCooldown");
        this.debugLaunchCooldown  = nonNegative(b.debugLaunchCooldown,  "debugLaunchCooldown");

        this.easyRadarCooldown   = positive(b.easyRadarCooldown,   "easyRadarCooldown");
        this.normalRadarCooldown = positive(b.normalRadarCooldown, "normalRadarCooldown");
        this.hardRadarCooldown   = positive(b.hardRadarCooldown,   "hardRadarCooldown");
        this.debugRadarCooldown  = nonNegative(b.debugRadarCooldown,  "debugRadarCooldown");

        this.easyDecoyCooldown   = positive(b.easyDecoyCooldown,   "easyDecoyCooldown");
        this.normalDecoyCooldown = positive(b.normalDecoyCooldown, "normalDecoyCooldown");
        this.hardDecoyCooldown   = positive(b.hardDecoyCooldown,   "hardDecoyCooldown");
        this.debugDecoyCooldown  = nonNegative(b.debugDecoyCooldown,  "debugDecoyCooldown");

        this.easyDefenseCooldown   = positive(b.easyDefenseCooldown,   "easyDefenseCooldown");
        this.normalDefenseCooldown = positive(b.normalDefenseCooldown, "normalDefenseCooldown");
        this.hardDefenseCooldown   = positive(b.hardDefenseCooldown,   "hardDefenseCooldown");
        this.debugDefenseCooldown  = nonNegative(b.debugDefenseCooldown,  "debugDefenseCooldown");

        this.easyUpgradeCooldown   = positive(b.easyUpgradeCooldown,   "easyUpgradeCooldown");
        this.normalUpgradeCooldown = positive(b.normalUpgradeCooldown, "normalUpgradeCooldown");
        this.hardUpgradeCooldown   = positive(b.hardUpgradeCooldown,   "hardUpgradeCooldown");
        this.debugUpgradeCooldown  = nonNegative(b.debugUpgradeCooldown,  "debugUpgradeCooldown");

        this.aiEarlyLaunchDelayTicks         = nonNegative(b.aiEarlyLaunchDelayTicks,         "aiEarlyLaunchDelayTicks");
        this.aiMinimumTicksBeforeDecoy       = nonNegative(b.aiMinimumTicksBeforeDecoy,       "aiMinimumTicksBeforeDecoy");
        this.aiMinimumTicksBeforeUpgrade     = nonNegative(b.aiMinimumTicksBeforeUpgrade,     "aiMinimumTicksBeforeUpgrade");
        this.aiMinimumTicksBeforeCivilDefense = nonNegative(b.aiMinimumTicksBeforeCivilDefense, "aiMinimumTicksBeforeCivilDefense");
        this.maxAiLaunchesBeforeTick80       = nonNegative(b.maxAiLaunchesBeforeTick80,       "maxAiLaunchesBeforeTick80");
        this.maxAiLaunchesBeforeTick160      = nonNegative(b.maxAiLaunchesBeforeTick160,      "maxAiLaunchesBeforeTick160");
        if (this.maxAiLaunchesBeforeTick160 < this.maxAiLaunchesBeforeTick80) {
            throw new IllegalArgumentException("maxAiLaunchesBeforeTick160 must be >= maxAiLaunchesBeforeTick80");
        }

        this.preferredImpactGracePieces           = nonNegative(b.preferredImpactGracePieces,           "preferredImpactGracePieces");
        this.preferredMaxImmediateGarbageRows     = nonNegative(b.preferredMaxImmediateGarbageRows,     "preferredMaxImmediateGarbageRows");
        this.preferredCivilDefenseStartingCharges = nonNegative(b.preferredCivilDefenseStartingCharges, "preferredCivilDefenseStartingCharges");
        this.preferredCivilDefenseShieldPieces    = nonNegative(b.preferredCivilDefenseShieldPieces,    "preferredCivilDefenseShieldPieces");

        this.defaultForPve = b.defaultForPve;
    }

    private static int nonNegative(int v, String name) {
        if (v < 0) throw new IllegalArgumentException(name + " must be >= 0, got " + v);
        return v;
    }

    private static int positive(int v, String name) {
        if (v <= 0) throw new IllegalArgumentException(name + " must be > 0 for non-DEBUG cooldowns, got " + v);
        return v;
    }

    public static Builder builder() { return new Builder(); }

    // --- accessors ---
    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    public int chargeGainPerTickFor(MabAiDifficulty d) {
        return switch (d) {
            case EASY -> easyAiChargeGainPerTick;
            case NORMAL -> normalAiChargeGainPerTick;
            case HARD -> hardAiChargeGainPerTick;
            case DEBUG -> debugAiChargeGainPerTick;
        };
    }

    public int addChargeStepFor(MabAiDifficulty d) {
        return switch (d) {
            case EASY -> easyAiAddChargeStep;
            case NORMAL -> normalAiAddChargeStep;
            case HARD -> hardAiAddChargeStep;
            case DEBUG -> debugAiAddChargeStep;
        };
    }

    public int launchCooldownFor(MabAiDifficulty d) {
        return switch (d) {
            case EASY -> easyLaunchCooldown;
            case NORMAL -> normalLaunchCooldown;
            case HARD -> hardLaunchCooldown;
            case DEBUG -> debugLaunchCooldown;
        };
    }

    public int radarCooldownFor(MabAiDifficulty d) {
        return switch (d) {
            case EASY -> easyRadarCooldown;
            case NORMAL -> normalRadarCooldown;
            case HARD -> hardRadarCooldown;
            case DEBUG -> debugRadarCooldown;
        };
    }

    public int decoyCooldownFor(MabAiDifficulty d) {
        return switch (d) {
            case EASY -> easyDecoyCooldown;
            case NORMAL -> normalDecoyCooldown;
            case HARD -> hardDecoyCooldown;
            case DEBUG -> debugDecoyCooldown;
        };
    }

    public int defenseCooldownFor(MabAiDifficulty d) {
        return switch (d) {
            case EASY -> easyDefenseCooldown;
            case NORMAL -> normalDefenseCooldown;
            case HARD -> hardDefenseCooldown;
            case DEBUG -> debugDefenseCooldown;
        };
    }

    public int upgradeCooldownFor(MabAiDifficulty d) {
        return switch (d) {
            case EASY -> easyUpgradeCooldown;
            case NORMAL -> normalUpgradeCooldown;
            case HARD -> hardUpgradeCooldown;
            case DEBUG -> debugUpgradeCooldown;
        };
    }

    public int getAiEarlyLaunchDelayTicks() { return aiEarlyLaunchDelayTicks; }
    public int getAiMinimumTicksBeforeDecoy() { return aiMinimumTicksBeforeDecoy; }
    public int getAiMinimumTicksBeforeUpgrade() { return aiMinimumTicksBeforeUpgrade; }
    public int getAiMinimumTicksBeforeCivilDefense() { return aiMinimumTicksBeforeCivilDefense; }
    public int getMaxAiLaunchesBeforeTick80() { return maxAiLaunchesBeforeTick80; }
    public int getMaxAiLaunchesBeforeTick160() { return maxAiLaunchesBeforeTick160; }

    public int getPreferredImpactGracePieces() { return preferredImpactGracePieces; }
    public int getPreferredMaxImmediateGarbageRows() { return preferredMaxImmediateGarbageRows; }
    public int getPreferredCivilDefenseStartingCharges() { return preferredCivilDefenseStartingCharges; }
    public int getPreferredCivilDefenseShieldPieces() { return preferredCivilDefenseShieldPieces; }

    public boolean isDefaultForPve() { return defaultForPve; }

    @Override
    public String toString() {
        return "MabBalanceProfile[" + id + " '" + displayName + "']";
    }

    /** Builder — verbose but keeps the immutable profile readable. */
    public static final class Builder {
        private String id;
        private String displayName;
        private String description = "";

        private int easyAiChargeGainPerTick;
        private int normalAiChargeGainPerTick;
        private int hardAiChargeGainPerTick;
        private int debugAiChargeGainPerTick;

        private int easyAiAddChargeStep;
        private int normalAiAddChargeStep;
        private int hardAiAddChargeStep;
        private int debugAiAddChargeStep;

        private int easyLaunchCooldown;
        private int normalLaunchCooldown;
        private int hardLaunchCooldown;
        private int debugLaunchCooldown;

        private int easyRadarCooldown;
        private int normalRadarCooldown;
        private int hardRadarCooldown;
        private int debugRadarCooldown;

        private int easyDecoyCooldown;
        private int normalDecoyCooldown;
        private int hardDecoyCooldown;
        private int debugDecoyCooldown;

        private int easyDefenseCooldown;
        private int normalDefenseCooldown;
        private int hardDefenseCooldown;
        private int debugDefenseCooldown;

        private int easyUpgradeCooldown;
        private int normalUpgradeCooldown;
        private int hardUpgradeCooldown;
        private int debugUpgradeCooldown;

        private int aiEarlyLaunchDelayTicks;
        private int aiMinimumTicksBeforeDecoy;
        private int aiMinimumTicksBeforeUpgrade;
        private int aiMinimumTicksBeforeCivilDefense;
        private int maxAiLaunchesBeforeTick80;
        private int maxAiLaunchesBeforeTick160;

        private int preferredImpactGracePieces;
        private int preferredMaxImmediateGarbageRows;
        private int preferredCivilDefenseStartingCharges;
        private int preferredCivilDefenseShieldPieces;

        private boolean defaultForPve;

        public Builder id(String v) { this.id = v; return this; }
        public Builder displayName(String v) { this.displayName = v; return this; }
        public Builder description(String v) { this.description = v; return this; }

        public Builder chargeGainPerTick(int easy, int normal, int hard, int debug) {
            this.easyAiChargeGainPerTick = easy;
            this.normalAiChargeGainPerTick = normal;
            this.hardAiChargeGainPerTick = hard;
            this.debugAiChargeGainPerTick = debug;
            return this;
        }
        public Builder addChargeStep(int easy, int normal, int hard, int debug) {
            this.easyAiAddChargeStep = easy;
            this.normalAiAddChargeStep = normal;
            this.hardAiAddChargeStep = hard;
            this.debugAiAddChargeStep = debug;
            return this;
        }
        public Builder launchCooldown(int easy, int normal, int hard, int debug) {
            this.easyLaunchCooldown = easy;
            this.normalLaunchCooldown = normal;
            this.hardLaunchCooldown = hard;
            this.debugLaunchCooldown = debug;
            return this;
        }
        public Builder radarCooldown(int easy, int normal, int hard, int debug) {
            this.easyRadarCooldown = easy;
            this.normalRadarCooldown = normal;
            this.hardRadarCooldown = hard;
            this.debugRadarCooldown = debug;
            return this;
        }
        public Builder decoyCooldown(int easy, int normal, int hard, int debug) {
            this.easyDecoyCooldown = easy;
            this.normalDecoyCooldown = normal;
            this.hardDecoyCooldown = hard;
            this.debugDecoyCooldown = debug;
            return this;
        }
        public Builder defenseCooldown(int easy, int normal, int hard, int debug) {
            this.easyDefenseCooldown = easy;
            this.normalDefenseCooldown = normal;
            this.hardDefenseCooldown = hard;
            this.debugDefenseCooldown = debug;
            return this;
        }
        public Builder upgradeCooldown(int easy, int normal, int hard, int debug) {
            this.easyUpgradeCooldown = easy;
            this.normalUpgradeCooldown = normal;
            this.hardUpgradeCooldown = hard;
            this.debugUpgradeCooldown = debug;
            return this;
        }

        public Builder aiEarlyLaunchDelayTicks(int v) { this.aiEarlyLaunchDelayTicks = v; return this; }
        public Builder aiMinimumTicksBeforeDecoy(int v) { this.aiMinimumTicksBeforeDecoy = v; return this; }
        public Builder aiMinimumTicksBeforeUpgrade(int v) { this.aiMinimumTicksBeforeUpgrade = v; return this; }
        public Builder aiMinimumTicksBeforeCivilDefense(int v) { this.aiMinimumTicksBeforeCivilDefense = v; return this; }
        public Builder maxAiLaunchesBeforeTick80(int v) { this.maxAiLaunchesBeforeTick80 = v; return this; }
        public Builder maxAiLaunchesBeforeTick160(int v) { this.maxAiLaunchesBeforeTick160 = v; return this; }

        public Builder preferredImpactGracePieces(int v) { this.preferredImpactGracePieces = v; return this; }
        public Builder preferredMaxImmediateGarbageRows(int v) { this.preferredMaxImmediateGarbageRows = v; return this; }
        public Builder preferredCivilDefenseStartingCharges(int v) { this.preferredCivilDefenseStartingCharges = v; return this; }
        public Builder preferredCivilDefenseShieldPieces(int v) { this.preferredCivilDefenseShieldPieces = v; return this; }

        public Builder defaultForPve(boolean v) { this.defaultForPve = v; return this; }

        public MabBalanceProfile build() { return new MabBalanceProfile(this); }
    }
}
