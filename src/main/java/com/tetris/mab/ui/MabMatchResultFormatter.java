package com.tetris.mab.ui;

/**
 * Step 18 — formats a {@link MabMatchResultSummary} into player-facing
 * text for the result dialog.
 *
 * <p><b>Offline-only.</b>
 */
public final class MabMatchResultFormatter {

    private MabMatchResultFormatter() {}

    public static String formatTitle(MabMatchResultSummary s) {
        if (s == null) return "Match Result";
        return "Mutually Assured Blocks — " + s.getTitle();
    }

    public static String formatCompact(MabMatchResultSummary s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(s.getTitle()).append('\n');
        sb.append(s.getReason()).append('\n');
        sb.append("DEFCON ").append(s.getDefconLevel())
          .append(" | launches=").append(s.getLaunchesAuthorized())
          .append(" impacts=").append(s.getImpactsResolved())
          .append(" scans=").append(s.getRadarScans())
          .append(" decoys=").append(s.getDecoysActivated())
          .append(" cd=").append(s.getCivilDefenseActivations())
          .append(" upgrades=").append(s.getUpgradesApplied());
        return sb.toString();
    }

    public static String formatBody(MabMatchResultSummary s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(s.getTitle()).append('\n');
        sb.append("Reason : ").append(s.getReason()).append('\n');
        sb.append('\n');
        sb.append("DEFCON reached       : ").append(s.getDefconLevel()).append('\n');
        sb.append("Launches authorized  : ").append(s.getLaunchesAuthorized()).append('\n');
        sb.append("Impacts resolved     : ").append(s.getImpactsResolved()).append('\n');
        sb.append("Radar scans          : ").append(s.getRadarScans()).append('\n');
        sb.append("Decoys activated     : ").append(s.getDecoysActivated()).append('\n');
        sb.append("Civil defense uses   : ").append(s.getCivilDefenseActivations()).append('\n');
        sb.append("Upgrades applied     : ").append(s.getUpgradesApplied()).append('\n');
        sb.append("AI decisions exec.   : ").append(s.getAiDecisionsExecuted()).append('\n');
        sb.append('\n');
        sb.append("Your lines cleared   : ").append(s.getPlayerLinesCleared()).append('\n');
        sb.append("Your pieces locked   : ").append(s.getPlayerPiecesLocked()).append('\n');
        sb.append("Your nuke charge     : ").append(s.getPlayerCharge()).append('\n');
        sb.append("Opponent nuke charge : ").append(s.getOpponentCharge()).append('\n');
        sb.append("Final nuke design    : ").append(s.getFinalNukeDesign()).append('\n');
        sb.append('\n');
        sb.append("Total events logged  : ").append(s.getTotalEvents())
          .append("  (event log is bounded; very long matches may undercount)\n");
        sb.append('\n');
        sb.append("Recent events:\n");
        if (s.getHighlightEvents().isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (String h : s.getHighlightEvents()) {
                sb.append("  ").append(h).append('\n');
            }
        }
        return sb.toString();
    }
}
