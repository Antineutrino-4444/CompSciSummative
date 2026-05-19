package com.tetris.mab.balance;

import com.tetris.mab.ai.MabAiDifficulty;

/**
 * Step 19 — immutable PvE balance profile.
 *
 * <p>This is Balance Pass v1: a single struct that centralizes the
 * AI pacing knobs (per-tick charge gain, per-decision charge step,
 * post-action cooldowns) plus a small set of PvE pressure gates
 * (early-launch delay, minimum ticks before feint/upgrade/civil
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

    private final int easyRouteScanCooldown;
    private final int normalRouteScanCooldown;
    private final int hardRouteScanCooldown;
    private final int debugRouteScanCooldown;

    private final int easyFeintCooldown;
    private final int normalFeintCooldown;
    private final int hardFeintCooldown;
    private final int debugFeintCooldown;

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
    private final int aiMinimumTicksBeforeFeint;
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

        this.easyRouteScanCooldown   = positive(b.easyRouteScanCooldown,   "easyRouteScanCooldown");
        this.normalRouteScanCooldown = positive(b.normalRouteScanCooldown, "normalRouteScanCooldown");
        this.hardRouteScanCooldown   = positive(b.hardRouteScanCooldown,   "hardRouteScanCooldown");
        this.debugRouteScanCooldown  = nonNegative(b.debugRouteScanCooldown,  "debugRouteScanCooldown");

        this.easyFeintCooldown   = positive(b.easyFeintCooldown,   "easyFeintCooldown");
        this.normalFeintCooldown = positive(b.normalFeintCooldown, "normalFeintCooldown");
        this.hardFeintCooldown   = positive(b.hardFeintCooldown,   "hardFeintCooldown");
        this.debugFeintCooldown  = nonNegative(b.debugFeintCooldown,  "debugFeintCooldown");

        this.easyDefenseCooldown   = positive(b.easyDefenseCooldown,   "easyDefenseCooldown");
        this.normalDefenseCooldown = positive(b.normalDefenseCooldown, "normalDefenseCooldown");
        this.hardDefenseCooldown   = positive(b.hardDefenseCooldown,   "hardDefenseCooldown");
        this.debugDefenseCooldown  = nonNegative(b.debugDefenseCooldown,  "debugDefenseCooldown");

        this.easyUpgradeCooldown   = positive(b.easyUpgradeCooldown,   "easyUpgradeCooldown");
        this.normalUpgradeCooldown = positive(b.normalUpgradeCooldown, "normalUpgradeCooldown");
        this.hardUpgradeCooldown   = positive(b.hardUpgradeCooldown,   "hardUpgradeCooldown");
        this.debugUpgradeCooldown  = nonNegative(b.debugUpgradeCooldown,  "debugUpgradeCooldown");

        this.aiEarlyLaunchDelayTicks         = nonNegative(b.aiEarlyLaunchDelayTicks,         "aiEarlyLaunchDelayTicks");
        this.aiMinimumTicksBeforeFeint       = nonNegative(b.aiMinimumTicksBeforeFeint,       "aiMinimumTicksBeforeFeint");
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
            case MEDIUM, NORMAL -> normalAiChargeGainPerTick;
            case HARD, EXPERT, MASTER -> hardAiChargeGainPerTick;
            case DEBUG -> debugAiChargeGainPerTick;
        };
    }

    public int addChargeStepFor(MabAiDifficulty d) {
        return switch (d) {
            case EASY -> easyAiAddChargeStep;
            case MEDIUM, NORMAL -> normalAiAddChargeStep;
            case HARD, EXPERT, MASTER -> hardAiAddChargeStep;
            case DEBUG -> debugAiAddChargeStep;
        };
    }

    public int launchCooldownFor(MabAiDifficulty d) {
        return switch (d) {
            case EASY -> easyLaunchCooldown;
            case MEDIUM, NORMAL -> normalLaunchCooldown;
            case HARD, EXPERT, MASTER -> hardLaunchCooldown;
            case DEBUG -> debugLaunchCooldown;
        };
    }

    public int routeScanCooldownFor(MabAiDifficulty d) {
        return switch (d) {
            case EASY -> easyRouteScanCooldown;
            case MEDIUM, NORMAL -> normalRouteScanCooldown;
            case HARD, EXPERT, MASTER -> hardRouteScanCooldown;
            case DEBUG -> debugRouteScanCooldown;
        };
    }

    public int feintCooldownFor(MabAiDifficulty d) {
        return switch (d) {
            case EASY -> easyFeintCooldown;
            case MEDIUM, NORMAL -> normalFeintCooldown;
            case HARD, EXPERT, MASTER -> hardFeintCooldown;
            case DEBUG -> debugFeintCooldown;
        };
    }

    public int defenseCooldownFor(MabAiDifficulty d) {
        return switch (d) {
            case EASY -> easyDefenseCooldown;
            case MEDIUM, NORMAL -> normalDefenseCooldown;
            case HARD, EXPERT, MASTER -> hardDefenseCooldown;
            case DEBUG -> debugDefenseCooldown;
        };
    }

    public int upgradeCooldownFor(MabAiDifficulty d) {
        return switch (d) {
            case EASY -> easyUpgradeCooldown;
            case MEDIUM, NORMAL -> normalUpgradeCooldown;
            case HARD, EXPERT, MASTER -> hardUpgradeCooldown;
            case DEBUG -> debugUpgradeCooldown;
        };
    }

    public int getAiEarlyLaunchDelayTicks() { return aiEarlyLaunchDelayTicks; }
    public int getAiMinimumTicksBeforeFeint() { return aiMinimumTicksBeforeFeint; }
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

        private int easyRouteScanCooldown;
        private int normalRouteScanCooldown;
        private int hardRouteScanCooldown;
        private int debugRouteScanCooldown;

        private int easyFeintCooldown;
        private int normalFeintCooldown;
        private int hardFeintCooldown;
        private int debugFeintCooldown;

        private int easyDefenseCooldown;
        private int normalDefenseCooldown;
        private int hardDefenseCooldown;
        private int debugDefenseCooldown;

        private int easyUpgradeCooldown;
        private int normalUpgradeCooldown;
        private int hardUpgradeCooldown;
        private int debugUpgradeCooldown;

        private int aiEarlyLaunchDelayTicks;
        private int aiMinimumTicksBeforeFeint;
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
        public Builder routeScanCooldown(int easy, int normal, int hard, int debug) {
            this.easyRouteScanCooldown = easy;
            this.normalRouteScanCooldown = normal;
            this.hardRouteScanCooldown = hard;
            this.debugRouteScanCooldown = debug;
            return this;
        }
        public Builder feintCooldown(int easy, int normal, int hard, int debug) {
            this.easyFeintCooldown = easy;
            this.normalFeintCooldown = normal;
            this.hardFeintCooldown = hard;
            this.debugFeintCooldown = debug;
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
        public Builder aiMinimumTicksBeforeFeint(int v) { this.aiMinimumTicksBeforeFeint = v; return this; }
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
