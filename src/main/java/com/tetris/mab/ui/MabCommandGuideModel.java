package com.tetris.mab.ui;

import com.tetris.mab.MatchPhase;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.NukeBuildState;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ParticipantState;
import com.tetris.mab.action.ActionCategory;
import com.tetris.mab.action.ActionCodeDefinition;
import com.tetris.mab.action.ActionCodeRegistry;
import com.tetris.mab.action.ActionCodeTokenRequirement;
import com.tetris.mab.action.ActionConfirmationMode;
import com.tetris.mab.nuke.NukeDesign;

import java.util.ArrayList;
import java.util.List;

/**
 * Step 17 — derives a player-facing {@link MabCommandGuideEntry} list
 * from the match's live {@link ActionCodeRegistry} plus a synthetic
 * "current launch" entry sourced from the player's armed nuke design.
 *
 * <p><b>Display only.</b> Never mutates match state.
 */
public final class MabCommandGuideModel {

    public List<MabCommandGuideEntry> buildEntries(MutuallyAssuredBlocksMatch match,
                                                   ParticipantId playerId) {
        List<MabCommandGuideEntry> out = new ArrayList<>();
        if (match == null) return out;

        ActionCodeRegistry reg = match.getActionCodeRegistry();
        ParticipantState player = (playerId == null)
                ? null : safeParticipant(match, playerId);

        boolean inUpgradePause = match.getCurrentPhase() == MatchPhase.UPGRADE_PAUSE;
        boolean armed = player != null
                && player.getNukeBuildState() != null
                && player.getNukeBuildState().isArmed();
        boolean hasIncoming = player != null
                && !player.getIncomingThreats().isEmpty();
        boolean confirmationPending = player != null
                && player.getActionCodeManager() != null
                && player.getActionCodeManager().getPendingConfirmationAttempt() != null;

        // 1) Current-launch synthetic entry (dynamic).
        out.add(buildCurrentLaunchEntry(reg, player, inUpgradePause, armed));

        // 2) Confirmation guidance synthetic entry.
        out.add(buildConfirmationEntry(player, confirmationPending, inUpgradePause));

        // 3) Wrap every registry definition (skip per-nuke launches —
        //    the synthetic entry above already covers the player's
        //    current nuke).
        if (reg != null) {
            for (ActionCodeDefinition def : reg.getAll()) {
                if (def.getCategory() == ActionCategory.LAUNCH) continue;
                out.add(toEntry(def, inUpgradePause, armed, hasIncoming));
            }
        }

        return out;
    }

    private MabCommandGuideEntry buildCurrentLaunchEntry(ActionCodeRegistry reg,
                                                         ParticipantState player,
                                                         boolean inUpgradePause,
                                                         boolean armed) {
        String name = "Current Nuke Launch";
        String code = "(no nuke design loaded)";
        String desc = "Begins launch authorization for the currently armed nuke. "
                + "Ends in a hard 4-line clear confirmation.";
        if (player != null) {
            NukeBuildState nb = player.getNukeBuildState();
            NukeDesign d = nb == null ? null : nb.getCurrentDesign();
            if (d != null && reg != null) {
                ActionCodeDefinition launch = reg.launchDefinitionForNuke(d, 5);
                if (launch != null) {
                    name = "Launch: " + d.getDisplayName();
                    code = formatTokens(launch.getSequence());
                    desc = "Launch sequence for " + d.getDisplayName()
                            + ". Hard 4-line clear confirmation at the end.";
                } else {
                    name = "Launch: " + d.getDisplayName();
                    code = "(no launch code defined for this design)";
                }
            }
        }
        String avail;
        if (inUpgradePause) avail = "Upgrade pause active — gameplay commands paused";
        else if (!armed) avail = "Requires armed nuke";
        else avail = "Available";
        return new MabCommandGuideEntry(
                "current_launch", name, "Launch", code, desc, avail,
                true, false, false, false, false);
    }

    private MabCommandGuideEntry buildConfirmationEntry(ParticipantState player,
                                                        boolean confirmationPending,
                                                        boolean inUpgradePause) {
        String code = "Hard drop, then complete the final 4-line clear (Tetris) to confirm.";
        String desc = "Used to confirm launches and other commands that require explicit "
                + "confirmation. Spins cannot replace the confirmation token.";
        String avail;
        if (inUpgradePause) avail = "Upgrade pause active — gameplay commands paused";
        else if (confirmationPending) avail = "Confirmation pending";
        else avail = "Idle (no confirmation required right now)";
        return new MabCommandGuideEntry(
                "confirm_launch", "Confirm Launch", "Confirmation",
                code, desc, avail,
                false, false, false, false, true);
    }

    private MabCommandGuideEntry toEntry(ActionCodeDefinition def,
                                         boolean inUpgradePause,
                                         boolean armed,
                                         boolean hasIncoming) {
        ActionCategory cat = def.getCategory();
        String catName = cat == null ? "Other" : cat.name();
        String code = formatTokens(def.getSequence());
        String desc = def.getDescription() == null ? "" : def.getDescription();
        if (def.getConfirmationMode() == ActionConfirmationMode.HARD_FOUR_CONFIRM) {
            desc = appendIfMissing(desc, "Ends in a hard 4-line clear confirmation.");
        } else if (def.getConfirmationMode() == ActionConfirmationMode.KEYBOARD_CONFIRM) {
            desc = appendIfMissing(desc, "Requires keyboard confirmation when prompted.");
        }
        String avail = computeAvailability(def, inUpgradePause, armed, hasIncoming);
        return new MabCommandGuideEntry(
                def.getId(), def.getDisplayName(), catName,
                code, desc, avail,
                cat == ActionCategory.LAUNCH,
                cat == ActionCategory.DEFENSE,
                cat == ActionCategory.INTEL,
                cat == ActionCategory.DECOY,
                cat == ActionCategory.UTILITY || cat == ActionCategory.RESTRAINT);
    }

    private String computeAvailability(ActionCodeDefinition def,
                                       boolean inUpgradePause,
                                       boolean armed,
                                       boolean hasIncoming) {
        if (inUpgradePause) return "Upgrade pause active — gameplay commands paused";
        if (def.requiresArmedNuke() && !armed) return "Requires armed nuke";
        if (def.requiresIncomingThreat() && !hasIncoming) return "No incoming threat";
        return "Available";
    }

    /** Renders a token sequence as e.g. "1, A2, 3, C4". */
    public static String formatTokens(List<ActionCodeTokenRequirement> seq) {
        if (seq == null || seq.isEmpty()) return "(empty)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < seq.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(seq.get(i).toDebugString());
        }
        return sb.toString();
    }

    private static String appendIfMissing(String desc, String suffix) {
        if (desc == null || desc.isBlank()) return suffix;
        if (desc.contains(suffix)) return desc;
        return desc + " " + suffix;
    }

    private static ParticipantState safeParticipant(MutuallyAssuredBlocksMatch match,
                                                    ParticipantId id) {
        try { return match.getParticipant(id); }
        catch (RuntimeException ex) { return null; }
    }
}
