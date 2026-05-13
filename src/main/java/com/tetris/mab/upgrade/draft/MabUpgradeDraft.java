package com.tetris.mab.upgrade.draft;

import com.tetris.mab.ParticipantId;

import java.util.Collections;
import java.util.List;

/**
 * Step 24 \u2014 a single offered upgrade draft: one participant,
 * one level-up event, exactly 3 card choices.
 */
public final class MabUpgradeDraft {

    private final ParticipantId participantId;
    private final int level;
    private final List<MabUpgradeCard> choices;

    public MabUpgradeDraft(ParticipantId participantId, int level, List<MabUpgradeCard> choices) {
        if (choices == null || choices.size() != 3) {
            throw new IllegalArgumentException(
                    "draft requires exactly 3 choices, got "
                            + (choices == null ? "null" : choices.size()));
        }
        this.participantId = participantId;
        this.level = level;
        this.choices = Collections.unmodifiableList(choices);
    }

    public ParticipantId getParticipantId() { return participantId; }
    public int getLevel() { return level; }
    public List<MabUpgradeCard> getChoices() { return choices; }

    public MabUpgradeCard getChoice(int idx) { return choices.get(idx); }
}
