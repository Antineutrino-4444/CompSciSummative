package com.tetris.mab.action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable description of one action and the line-clear sequence
 * required to trigger it.
 */
public final class ActionCodeDefinition {

    private final String id;
    private final String displayName;
    private final ActionType actionType;
    private final ActionCategory category;
    private final List<ActionCodeTokenRequirement> sequence;
    private final boolean strategicAction;
    private final boolean requiresArmedNuke;
    private final boolean requiresIncomingThreat;
    private final boolean consumesNukeCharge;
    private final ActionConfirmationMode confirmationMode;
    private final String description;

    public ActionCodeDefinition(String id,
                                String displayName,
                                ActionType actionType,
                                ActionCategory category,
                                List<ActionCodeTokenRequirement> sequence,
                                boolean strategicAction,
                                boolean requiresArmedNuke,
                                boolean requiresIncomingThreat,
                                boolean consumesNukeCharge,
                                ActionConfirmationMode confirmationMode,
                                String description) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName");
        if (actionType == null) throw new IllegalArgumentException("actionType");
        if (category == null) throw new IllegalArgumentException("category");
        if (sequence == null || sequence.isEmpty()) throw new IllegalArgumentException("sequence must not be empty");
        if (confirmationMode == null) throw new IllegalArgumentException("confirmationMode");

        List<ActionCodeTokenRequirement> copy = new ArrayList<>(sequence.size());
        for (ActionCodeTokenRequirement t : sequence) {
            if (t == null) throw new IllegalArgumentException("null token in sequence");
            if (t.requiredLineCount() < 1 || t.requiredLineCount() > 4) {
                throw new IllegalArgumentException(
                        "required line count must be 1..4, got " + t.requiredLineCount());
            }
            copy.add(t);
        }

        if (confirmationMode == ActionConfirmationMode.HARD_FOUR_CONFIRM) {
            ActionCodeTokenRequirement last = copy.get(copy.size() - 1);
            if (!last.confirmationToken() || last.requiredLineCount() != 4 || last.spinMayReplace()) {
                throw new IllegalArgumentException(
                        "HARD_FOUR_CONFIRM requires final token to be a hard, "
                                + "non-spin-replaceable confirmation 4");
            }
        }

        this.id = id;
        this.displayName = displayName;
        this.actionType = actionType;
        this.category = category;
        this.sequence = Collections.unmodifiableList(copy);
        this.strategicAction = strategicAction;
        this.requiresArmedNuke = requiresArmedNuke;
        this.requiresIncomingThreat = requiresIncomingThreat;
        this.consumesNukeCharge = consumesNukeCharge;
        this.confirmationMode = confirmationMode;
        this.description = description == null ? "" : description;
    }

    /**
     * Convenience factory: builds a definition where every token is a
     * normal {@link ActionCodeTokenRequirement#normal(int)}.
     */
    public static ActionCodeDefinition fromLineCounts(String id,
                                                      String displayName,
                                                      ActionType actionType,
                                                      ActionCategory category,
                                                      List<Integer> lineCounts,
                                                      boolean strategicAction,
                                                      boolean requiresArmedNuke,
                                                      boolean requiresIncomingThreat,
                                                      boolean consumesNukeCharge,
                                                      ActionConfirmationMode confirmationMode,
                                                      String description) {
        if (lineCounts == null || lineCounts.isEmpty()) {
            throw new IllegalArgumentException("lineCounts must not be empty");
        }
        List<ActionCodeTokenRequirement> seq = new ArrayList<>(lineCounts.size());
        for (Integer c : lineCounts) {
            if (c == null) throw new IllegalArgumentException("null line count");
            seq.add(ActionCodeTokenRequirement.normal(c));
        }
        return new ActionCodeDefinition(id, displayName, actionType, category, seq,
                strategicAction, requiresArmedNuke, requiresIncomingThreat,
                consumesNukeCharge, confirmationMode, description);
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public ActionType getActionType() { return actionType; }
    public ActionCategory getCategory() { return category; }
    public List<ActionCodeTokenRequirement> getSequence() { return sequence; }
    public boolean isStrategicAction() { return strategicAction; }
    public boolean requiresArmedNuke() { return requiresArmedNuke; }
    public boolean requiresIncomingThreat() { return requiresIncomingThreat; }
    public boolean consumesNukeCharge() { return consumesNukeCharge; }
    public ActionConfirmationMode getConfirmationMode() { return confirmationMode; }
    public String getDescription() { return description; }

    public int sequenceLength() { return sequence.size(); }

    public String toDebugString() {
        StringBuilder sb = new StringBuilder();
        sb.append(id).append('[');
        for (int i = 0; i < sequence.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(sequence.get(i).toDebugString());
        }
        sb.append("] confirm=").append(confirmationMode);
        return sb.toString();
    }
}
