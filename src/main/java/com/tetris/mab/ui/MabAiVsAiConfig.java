package com.tetris.mab.ui;

import com.tetris.mab.ai.MabAiArchetype;
import com.tetris.mab.ai.MabAiDifficulty;
import com.tetris.mab.balance.MabBalanceProfile;
import com.tetris.mab.balance.MabBalanceProfiles;

/**
 * Immutable setup choices for the offline AI-vs-AI exhibition mode.
 *
 * <p>Each side carries an independent archetype + difficulty so the
 * spectator can stage matchups like "MASTER tactical spammer vs HARD
 * doomsday hoarder". Designs are picked from the AI's
 * {@link com.tetris.mab.ai.MabAiDesignPicker} at match build time.
 */
public final class MabAiVsAiConfig {

    private final int startLevel;
    private final MabAiArchetype playerAArchetype;
    private final MabAiDifficulty playerADifficulty;
    private final MabAiArchetype playerBArchetype;
    private final MabAiDifficulty playerBDifficulty;
    private final String balanceProfileId;

    public MabAiVsAiConfig(int startLevel,
                            MabAiArchetype playerAArchetype,
                            MabAiDifficulty playerADifficulty,
                            MabAiArchetype playerBArchetype,
                            MabAiDifficulty playerBDifficulty,
                            String balanceProfileId) {
        this.startLevel = clampLevel(startLevel);
        this.playerAArchetype = (playerAArchetype == null) ? MabAiArchetype.BALANCED : playerAArchetype;
        this.playerADifficulty = (playerADifficulty == null) ? MabAiDifficulty.MASTER : playerADifficulty;
        this.playerBArchetype = (playerBArchetype == null) ? MabAiArchetype.BALANCED : playerBArchetype;
        this.playerBDifficulty = (playerBDifficulty == null) ? MabAiDifficulty.HARD : playerBDifficulty;
        this.balanceProfileId = (balanceProfileId == null || balanceProfileId.isBlank())
                ? MabBalanceProfiles.STANDARD_PVE : balanceProfileId;
    }

    public static MabAiVsAiConfig defaults() {
        return new MabAiVsAiConfig(1,
                MabAiArchetype.BALANCED, MabAiDifficulty.MASTER,
                MabAiArchetype.BALANCED, MabAiDifficulty.HARD,
                MabBalanceProfiles.STANDARD_PVE);
    }

    private static int clampLevel(int level) {
        if (level < 1) return 1;
        if (level > 20) return 20;
        return level;
    }

    public int getStartLevel() { return startLevel; }
    public MabAiArchetype getPlayerAArchetype() { return playerAArchetype; }
    public MabAiDifficulty getPlayerADifficulty() { return playerADifficulty; }
    public MabAiArchetype getPlayerBArchetype() { return playerBArchetype; }
    public MabAiDifficulty getPlayerBDifficulty() { return playerBDifficulty; }
    public String getBalanceProfileId() { return balanceProfileId; }
    public MabBalanceProfile getBalanceProfile() { return MabBalanceProfiles.byId(balanceProfileId); }

    @Override
    public String toString() {
        return "MabAiVsAiConfig[startLevel=" + startLevel
                + ", A=" + playerAArchetype + "/" + playerADifficulty
                + ", B=" + playerBArchetype + "/" + playerBDifficulty
                + ", balance=" + balanceProfileId + "]";
    }
}
