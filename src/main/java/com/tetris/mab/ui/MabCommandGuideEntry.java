package com.tetris.mab.ui;

import java.util.Objects;

/**
 * Step 17 — immutable player-facing description of one strategic
 * command. Built by {@link MabCommandGuideModel} and rendered by
 * {@link MabCommandGuideFormatter}.
 *
 * <p><b>Display only.</b> Never mutates match state.
 */
public record MabCommandGuideEntry(String id,
                                   String displayName,
                                   String category,
                                   String codeText,
                                   String description,
                                   String availabilityText,
                                   boolean launchCommand,
                                   boolean defenseCommand,
                                   boolean intelCommand,
                                   boolean deceptionCommand,
                                   boolean utilityCommand) {

    public MabCommandGuideEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        category = category == null ? "" : category;
        codeText = codeText == null ? "" : codeText;
        description = description == null ? "" : description;
        availabilityText = availabilityText == null ? "" : availabilityText;
    }
}
