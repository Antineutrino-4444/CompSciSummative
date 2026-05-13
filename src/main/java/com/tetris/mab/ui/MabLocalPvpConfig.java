package com.tetris.mab.ui;

import com.tetris.mab.balance.MabBalanceProfile;
import com.tetris.mab.balance.MabBalanceProfiles;

/**
 * Immutable setup choices for offline same-keyboard MAB local PvP.
 */
public final class MabLocalPvpConfig {

    private final int startLevel;
    private final String playerAName;
    private final String playerBName;
    private final String balanceProfileId;
    private final MabNukeDesignSelection nukeDesignP1;
    private final MabNukeDesignSelection nukeDesignP2;

    public MabLocalPvpConfig(int startLevel,
                             String playerAName,
                             String playerBName,
                             String balanceProfileId,
                             MabNukeDesignSelection nukeDesignP1,
                             MabNukeDesignSelection nukeDesignP2) {
        this.startLevel = clampLevel(startLevel);
        this.playerAName = clean(playerAName, "Player 1");
        this.playerBName = clean(playerBName, "Player 2");
        this.balanceProfileId = (balanceProfileId == null || balanceProfileId.isBlank())
                ? MabBalanceProfiles.STANDARD_PVE : balanceProfileId;
        this.nukeDesignP1 = nukeDesignP1 == null
                ? MabNukeDesignSelection.defaultSelection()
                : nukeDesignP1;
        this.nukeDesignP2 = nukeDesignP2 == null
                ? MabNukeDesignSelection.defaultSelection()
                : nukeDesignP2;
    }

    public static MabLocalPvpConfig defaults() {
        return new MabLocalPvpConfig(1, "Player 1", "Player 2",
                MabBalanceProfiles.STANDARD_PVE,
                MabNukeDesignSelection.defaultSelection(),
                MabNukeDesignSelection.defaultSelection());
    }

    private static int clampLevel(int level) {
        if (level < 1) return 1;
        if (level > 20) return 20;
        return level;
    }

    private static String clean(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String trimmed = value.trim();
        return trimmed.length() > 20 ? trimmed.substring(0, 20) : trimmed;
    }

    public int getStartLevel() { return startLevel; }
    public String getPlayerAName() { return playerAName; }
    public String getPlayerBName() { return playerBName; }
    public String getBalanceProfileId() { return balanceProfileId; }
    public MabBalanceProfile getBalanceProfile() { return MabBalanceProfiles.byId(balanceProfileId); }
    public MabNukeDesignSelection getNukeDesignP1() { return nukeDesignP1; }
    public MabNukeDesignSelection getNukeDesignP2() { return nukeDesignP2; }

    @Override
    public String toString() {
        return "MabLocalPvpConfig[startLevel=" + startLevel
                + ", playerA=" + playerAName
                + ", playerB=" + playerBName
                + ", balance=" + balanceProfileId
                + ", nukeP1=" + nukeDesignP1.getSource()
                + ", nukeP2=" + nukeDesignP2.getSource() + "]";
    }
}
