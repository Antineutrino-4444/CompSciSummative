package com.tetris.mab.ui;

import com.tetris.mab.ai.MabAiArchetype;
import com.tetris.mab.ai.MabAiDifficulty;
import com.tetris.mab.balance.MabBalanceProfile;
import com.tetris.mab.balance.MabBalanceProfiles;

/**
 * Step 18 — immutable PvE setup configuration captured by the
 * {@link MabPveSetupDialog} and consumed by the
 * {@link com.tetris.controller.GameController} when launching MAB PvE.
 *
 * <p><b>Offline-only.</b> No networking, no remote-player concept.
 */
public final class MabPveConfig {

    private final int startLevel;
    private final MabAiArchetype aiArchetype;
    private final MabAiDifficulty aiDifficulty;
    private final boolean showPlayerHud;
    private final boolean showDebugHud;
    private final String label;
    private final String balanceProfileId;
    private final MabNukeDesignSelection nukeDesignSelection;

    public MabPveConfig(int startLevel,
                        MabAiArchetype aiArchetype,
                        MabAiDifficulty aiDifficulty,
                        boolean showPlayerHud,
                        boolean showDebugHud,
                        String label,
                        String balanceProfileId,
                        MabNukeDesignSelection nukeDesignSelection) {
        this.startLevel = clampLevel(startLevel);
        this.aiArchetype = (aiArchetype == null) ? MabAiArchetype.BALANCED : aiArchetype;
        this.aiDifficulty = (aiDifficulty == null) ? MabAiDifficulty.NORMAL : aiDifficulty;
        this.showPlayerHud = showPlayerHud;
        this.showDebugHud = showDebugHud;
        this.label = (label == null || label.isBlank()) ? "MAB PvE" : label;
        this.balanceProfileId = (balanceProfileId == null || balanceProfileId.isBlank())
                ? MabBalanceProfiles.STANDARD_PVE : balanceProfileId;
        this.nukeDesignSelection = nukeDesignSelection == null
                ? MabNukeDesignSelection.defaultSelection()
                : nukeDesignSelection;
    }

    public MabPveConfig(int startLevel,
                        MabAiArchetype aiArchetype,
                        MabAiDifficulty aiDifficulty,
                        boolean showPlayerHud,
                        boolean showDebugHud,
                        String label,
                        String balanceProfileId) {
        this(startLevel, aiArchetype, aiDifficulty, showPlayerHud, showDebugHud, label,
                balanceProfileId, MabNukeDesignSelection.defaultSelection());
    }

    /** Backward-compatible constructor (defaults to standard-pve profile). */
    public MabPveConfig(int startLevel,
                        MabAiArchetype aiArchetype,
                        MabAiDifficulty aiDifficulty,
                        boolean showPlayerHud,
                        boolean showDebugHud,
                        String label) {
        this(startLevel, aiArchetype, aiDifficulty, showPlayerHud, showDebugHud, label,
                MabBalanceProfiles.STANDARD_PVE);
    }

    public static MabPveConfig defaults() {
        return new MabPveConfig(1,
                MabAiArchetype.BALANCED,
                MabAiDifficulty.NORMAL,
                true,
                debugHudDefault(),
                "MAB PvE",
                MabBalanceProfiles.STANDARD_PVE,
                MabNukeDesignSelection.defaultSelection());
    }

    public static MabPveConfig fromSelections(int startLevel,
                                              MabAiArchetype archetype,
                                              MabAiDifficulty difficulty,
                                              boolean showDebugHud) {
        return fromSelections(startLevel, archetype, difficulty, showDebugHud,
                MabBalanceProfiles.STANDARD_PVE);
    }

    public static MabPveConfig fromSelections(int startLevel,
                                              MabAiArchetype archetype,
                                              MabAiDifficulty difficulty,
                                              boolean showDebugHud,
                                              String balanceProfileId) {
        return fromSelections(startLevel, archetype, difficulty, showDebugHud,
                balanceProfileId, MabNukeDesignSelection.defaultSelection());
    }

    public static MabPveConfig fromSelections(int startLevel,
                                              MabAiArchetype archetype,
                                              MabAiDifficulty difficulty,
                                              boolean showDebugHud,
                                              String balanceProfileId,
                                              MabNukeDesignSelection nukeDesignSelection) {
        return new MabPveConfig(startLevel, archetype, difficulty, true, showDebugHud,
                "MAB PvE", balanceProfileId, nukeDesignSelection);
    }

    public static boolean debugHudDefault() {
        return "true".equalsIgnoreCase(System.getProperty("mab.debug.hud", "false"));
    }

    private static int clampLevel(int level) {
        if (level < 1) return 1;
        if (level > 20) return 20;
        return level;
    }

    public int getStartLevel() { return startLevel; }
    public MabAiArchetype getAiArchetype() { return aiArchetype; }
    public MabAiDifficulty getAiDifficulty() { return aiDifficulty; }
    public boolean isShowPlayerHud() { return showPlayerHud; }
    public boolean isShowDebugHud() { return showDebugHud; }
    public String getLabel() { return label; }
    public String getBalanceProfileId() { return balanceProfileId; }
    public MabBalanceProfile getBalanceProfile() { return MabBalanceProfiles.byId(balanceProfileId); }
    public MabNukeDesignSelection getNukeDesignSelection() { return nukeDesignSelection; }

    @Override
    public String toString() {
        return "MabPveConfig[startLevel=" + startLevel
                + ", archetype=" + aiArchetype
                + ", difficulty=" + aiDifficulty
                + ", balance=" + balanceProfileId
                + ", nuke=" + nukeDesignSelection.getSource()
                + ", playerHud=" + showPlayerHud
                + ", debugHud=" + showDebugHud + "]";
    }
}
