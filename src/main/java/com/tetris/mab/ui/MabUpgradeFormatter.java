package com.tetris.mab.ui;

import com.tetris.mab.UpgradeState;
import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.upgrade.NukeRedesignRetentionRules;
import com.tetris.mab.upgrade.UpgradeApplicationResult;
import com.tetris.mab.upgrade.UpgradeChoiceSet;
import com.tetris.mab.upgrade.UpgradeDefinition;
import com.tetris.mab.upgrade.UpgradeType;

import java.util.List;

/**
 * Step 16 — formats {@link UpgradeChoiceSet}, {@link UpgradeDefinition},
 * and {@link NukeDesign} retention information for the player-facing
 * upgrade pause UI. Stateless, display-only.
 *
 * <p><b>Offline-only.</b>
 */
public final class MabUpgradeFormatter {

    private MabUpgradeFormatter() {}

    /** Compact one-line label for the upgrade list. */
    public static String formatListEntry(UpgradeDefinition def, UpgradeState state) {
        if (def == null) return "(none)";
        int lvl = state == null ? 0 : state.getLevel(def.type());
        int next = state == null ? def.baseCost() : state.costForNextLevel(def);
        String tag;
        if (state == null) tag = "?";
        else if (state.isMaxed(def)) tag = "MAX";
        else if (!state.hasPrerequisites(def)) tag = "lock";
        else if (next > state.getUpgradePoints()) tag = "$" + next;
        else tag = "ok " + next;
        return String.format("[%s] %-22s lvl %d/%d",
                tag, def.displayName(), lvl, def.maxLevel());
    }

    /** Multi-line detail for the selected upgrade. */
    public static String formatUpgradeDetail(UpgradeDefinition def, UpgradeState state) {
        if (def == null) return "(no upgrade selected)";
        StringBuilder sb = new StringBuilder();
        sb.append(def.displayName()).append('\n');
        sb.append("Type        : ").append(def.type()).append('\n');
        sb.append("Category    : ").append(def.category()).append('\n');
        int lvl = state == null ? 0 : state.getLevel(def.type());
        sb.append("Current lvl : ").append(lvl).append(" / ").append(def.maxLevel()).append('\n');
        if (state != null) {
            int cost = state.costForNextLevel(def);
            sb.append("Cost        : ").append(cost).append(" pts\n");
            sb.append("Points      : ").append(state.getUpgradePoints()).append('\n');
            if (state.isMaxed(def)) {
                sb.append("Status      : MAX LEVEL\n");
            } else if (!state.hasPrerequisites(def)) {
                sb.append("Status      : prerequisites missing\n");
            } else if (cost > state.getUpgradePoints()) {
                sb.append("Status      : need ")
                        .append(cost - state.getUpgradePoints())
                        .append(" more point(s)\n");
            } else {
                sb.append("Status      : affordable\n");
            }
        }
        sb.append("DEFCON max  : ").append(def.requiredDefconMaximum()).append('\n');
        if (!def.prerequisites().isEmpty()) {
            sb.append("Requires    : ");
            for (int i = 0; i < def.prerequisites().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(def.prerequisites().get(i));
            }
            sb.append('\n');
        }
        if (!def.description().isEmpty()) {
            sb.append('\n').append(def.description());
        }
        return sb.toString();
    }

    /** Choice-set header (points + DEFCON). */
    public static String formatChoiceSetHeader(UpgradeChoiceSet choices) {
        if (choices == null) return "(no choices)";
        return "Available points: " + choices.availableUpgradePoints()
                + "    DEFCON: " + choices.defconLevel()
                + "    Choices: " + choices.choices().size();
    }

    /** One-line summary of owned upgrade levels (only nonzero). */
    public static String formatOwnedUpgradeSummary(UpgradeState state) {
        if (state == null) return "(no state)";
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        for (var entry : state.getLevels().entrySet()) {
            UpgradeType t = entry.getKey();
            int lvl = entry.getValue() == null ? 0 : entry.getValue();
            if (lvl <= 0) continue;
            if (any) sb.append(", ");
            sb.append(t).append('=').append(lvl);
            any = true;
        }
        if (!any) sb.append("(none owned yet)");
        return sb.toString();
    }

    /** Apply-upgrade result line. */
    public static String formatApplyResult(UpgradeApplicationResult result) {
        if (result == null) return "";
        if (result.success()) {
            return "Applied " + result.type() + " -> level " + result.newLevel()
                    + " (cost " + result.costPaid() + ")";
        }
        return "Rejected: " + result.message();
    }

    /** Multi-line retention preview for a redesign. */
    public static String formatRetentionPreview(NukeDesign oldDesign,
                                                NukeDesign newDesign,
                                                int currentCharge) {
        if (newDesign == null) return "(no new design selected)";
        StringBuilder sb = new StringBuilder();
        sb.append("Old design  : ");
        sb.append(oldDesign == null ? "—" : oldDesign.getDisplayName()).append('\n');
        sb.append("New design  : ").append(newDesign.getDisplayName()).append('\n');
        double ratio = NukeRedesignRetentionRules.suggestedRetentionRatio(oldDesign, newDesign);
        int retained = (int) Math.floor(Math.max(0.0, currentCharge) * ratio);
        sb.append(String.format("Ratio       : %.2f%n", ratio));
        sb.append("Current chg : ").append(currentCharge).append('\n');
        sb.append("Retained    : ~").append(retained).append('\n');
        sb.append('\n');
        sb.append(NukeRedesignRetentionRules.explain(oldDesign, newDesign));
        return sb.toString();
    }

    /** Wraps a list of choices into newline-separated rows. */
    public static String formatUpgradeList(UpgradeChoiceSet choices, UpgradeState state) {
        if (choices == null) return "(no choices)";
        StringBuilder sb = new StringBuilder();
        sb.append(formatChoiceSetHeader(choices)).append('\n');
        List<UpgradeDefinition> list = choices.choices();
        if (list.isEmpty()) {
            sb.append("(no upgrades currently selectable)");
            return sb.toString();
        }
        for (UpgradeDefinition d : list) {
            sb.append(formatListEntry(d, state)).append('\n');
        }
        return sb.toString();
    }
}
