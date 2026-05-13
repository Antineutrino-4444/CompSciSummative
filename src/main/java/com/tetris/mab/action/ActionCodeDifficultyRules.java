package com.tetris.mab.action;

import com.tetris.mab.MatchDifficulty;

/** Maps {@link MatchDifficulty} to a default {@link ActionCodeMatchMode}. */
public final class ActionCodeDifficultyRules {

    private ActionCodeDifficultyRules() {}

    public static ActionCodeMatchMode matchModeFor(MatchDifficulty difficulty) {
        if (difficulty == null) return ActionCodeMatchMode.MOSTLY_FLEXIBLE;
        return switch (difficulty) {
            case EASY -> ActionCodeMatchMode.FLEXIBLE;
            case NORMAL -> ActionCodeMatchMode.MOSTLY_FLEXIBLE;
            case HARD, DOOMSDAY -> ActionCodeMatchMode.STRICT;
        };
    }
}
