package com.tetris.mab.upgrade;

import com.tetris.mab.ParticipantId;

import java.util.List;

/** Snapshot of which upgrades a participant could choose from right now. */
public record UpgradeChoiceSet(
        ParticipantId participantId,
        int defconLevel,
        int availableUpgradePoints,
        List<UpgradeDefinition> choices) {

    public UpgradeChoiceSet {
        choices = choices == null ? List.of() : List.copyOf(choices);
    }
}
