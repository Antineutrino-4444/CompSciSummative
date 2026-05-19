package com.tetris.mab.ui;

/**
 * Formats a {@link MabMatchResultSummary} into player-facing text for
 * the result dialog.
 *
 * <p><b>Offline-only.</b>
 */
public final class MabMatchResultFormatter {

    private MabMatchResultFormatter() {}

    public static String formatTitle(MabMatchResultSummary s) {
        if (s == null) return "Match Result";
        return "Mutually Assured Blocks - " + s.getTitle();
    }

    public static String formatCompact(MabMatchResultSummary s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(s.getTitle()).append('\n');
        sb.append(s.getReason()).append('\n');
        sb.append("DEFCON ").append(s.getDefconLevel())
          .append(" | launches=").append(s.getLaunchesAuthorized())
          .append(" impacts=").append(s.getImpactsResolved())
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
        sb.append("Radiation waves      : ").append(s.getRadiationWavesApplied()).append('\n');
        sb.append("EMP disruptions      : ").append(s.getEmpDisruptions()).append('\n');
        sb.append("Disarm applied       : ").append(s.getDisarmEvents())
          .append(" events / ").append(s.getDisarmAmountApplied()).append(" charge\n");
        sb.append("Silo damage applied  : ").append(s.getSiloDamageEvents())
          .append(" events / ").append(s.getSiloDamageApplied()).append(" integrity\n");
        sb.append("Civil defense uses   : ").append(s.getCivilDefenseActivations()).append('\n');
        sb.append("Upgrades applied     : ").append(s.getUpgradesApplied()).append('\n');
        sb.append("AI decisions exec.   : ").append(s.getAiDecisionsExecuted()).append('\n');
        sb.append('\n');
        sb.append("Survival time        : ").append(formatMillis(s.getSurvivalMillis())).append('\n');
        sb.append("Your lines cleared   : ").append(s.getPlayerLinesCleared()).append('\n');
        sb.append("Your pieces locked   : ").append(s.getPlayerPiecesLocked()).append('\n');
        sb.append("Your max height      : ").append(s.getPlayerMaxStackHeight()).append('\n');
        sb.append("Opponent max height  : ").append(s.getOpponentMaxStackHeight()).append('\n');
        sb.append("Your nuke charge     : ").append(s.getPlayerCharge()).append('\n');
        sb.append("Opponent nuke charge : ").append(s.getOpponentCharge()).append('\n');
        sb.append('\n');
        sb.append("Your final warhead   : ").append(s.getPlayerWarheadSummary()).append('\n');
        sb.append("Opponent final       : ").append(s.getOpponentWarheadSummary()).append('\n');
        sb.append("Last weapon/effects  : ").append(s.getLastWeaponSummary()).append('\n');
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

    private static String formatMillis(long ms) {
        long total = Math.max(0L, ms) / 1000L;
        long min = total / 60L;
        long sec = total % 60L;
        return min + ":" + (sec < 10 ? "0" : "") + sec;
    }
}
