package com.tetris.mab.ui;

import com.tetris.mab.MatchDebugSnapshot;
import com.tetris.mab.MatchEventLogEntry;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.NukeBuildState;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ParticipantState;
import com.tetris.mab.nuke.NukeDesign;

import java.util.List;
import java.util.Map;

/**
 * Immutable, snapshot-only summary of a finished MAB match. Built from
 * the live {@link MutuallyAssuredBlocksMatch} when the result overlay
 * appears.
 *
 * <p>Counts are derived from the bounded match event log. The match
 * keeps a finite log, so very long matches may undercount. This is
 * acceptable for a player-facing post-match summary.
 *
 * <p><b>Offline-only.</b>
 */
public final class MabMatchResultSummary {

    private final boolean matchOver;
    private final ParticipantId winner;
    private final ParticipantId playerId;
    private final String title;
    private final String reason;
    private final int defconLevel;
    private final int totalEvents;
    private final int playerLinesCleared;
    private final int playerPiecesLocked;
    private final long survivalMillis;
    private final int playerMaxStackHeight;
    private final int opponentMaxStackHeight;
    private final int playerCharge;
    private final int opponentCharge;
    private final int launchesAuthorized;
    private final int impactsResolved;
    private final int civilDefenseActivations;
    private final int radiationWavesApplied;
    private final int empDisruptions;
    private final int disarmEvents;
    private final int disarmAmountApplied;
    private final int siloDamageEvents;
    private final int siloDamageApplied;
    private final int upgradesApplied;
    private final int aiDecisionsExecuted;
    private final String finalNukeDesign;
    private final String playerWarheadSummary;
    private final String opponentWarheadSummary;
    private final String lastWeaponSummary;
    private final List<String> highlightEvents;

    private MabMatchResultSummary(Builder b) {
        this.matchOver = b.matchOver;
        this.winner = b.winner;
        this.playerId = b.playerId;
        this.title = b.title;
        this.reason = b.reason;
        this.defconLevel = b.defconLevel;
        this.totalEvents = b.totalEvents;
        this.playerLinesCleared = b.playerLinesCleared;
        this.playerPiecesLocked = b.playerPiecesLocked;
        this.survivalMillis = b.survivalMillis;
        this.playerMaxStackHeight = b.playerMaxStackHeight;
        this.opponentMaxStackHeight = b.opponentMaxStackHeight;
        this.playerCharge = b.playerCharge;
        this.opponentCharge = b.opponentCharge;
        this.launchesAuthorized = b.launchesAuthorized;
        this.impactsResolved = b.impactsResolved;
        this.civilDefenseActivations = b.civilDefenseActivations;
        this.radiationWavesApplied = b.radiationWavesApplied;
        this.empDisruptions = b.empDisruptions;
        this.disarmEvents = b.disarmEvents;
        this.disarmAmountApplied = b.disarmAmountApplied;
        this.siloDamageEvents = b.siloDamageEvents;
        this.siloDamageApplied = b.siloDamageApplied;
        this.upgradesApplied = b.upgradesApplied;
        this.aiDecisionsExecuted = b.aiDecisionsExecuted;
        this.finalNukeDesign = b.finalNukeDesign;
        this.playerWarheadSummary = b.playerWarheadSummary;
        this.opponentWarheadSummary = b.opponentWarheadSummary;
        this.lastWeaponSummary = b.lastWeaponSummary;
        this.highlightEvents = List.copyOf(b.highlightEvents);
    }

    public boolean isMatchOver() { return matchOver; }
    public ParticipantId getWinner() { return winner; }
    public ParticipantId getPlayerId() { return playerId; }
    public String getTitle() { return title; }
    public String getReason() { return reason; }
    public int getDefconLevel() { return defconLevel; }
    public int getTotalEvents() { return totalEvents; }
    public int getPlayerLinesCleared() { return playerLinesCleared; }
    public int getPlayerPiecesLocked() { return playerPiecesLocked; }
    public long getSurvivalMillis() { return survivalMillis; }
    public int getPlayerMaxStackHeight() { return playerMaxStackHeight; }
    public int getOpponentMaxStackHeight() { return opponentMaxStackHeight; }
    public int getPlayerCharge() { return playerCharge; }
    public int getOpponentCharge() { return opponentCharge; }
    public int getLaunchesAuthorized() { return launchesAuthorized; }
    public int getImpactsResolved() { return impactsResolved; }
    public int getCivilDefenseActivations() { return civilDefenseActivations; }
    public int getRadiationWavesApplied() { return radiationWavesApplied; }
    public int getEmpDisruptions() { return empDisruptions; }
    public int getDisarmEvents() { return disarmEvents; }
    public int getDisarmAmountApplied() { return disarmAmountApplied; }
    public int getSiloDamageEvents() { return siloDamageEvents; }
    public int getSiloDamageApplied() { return siloDamageApplied; }
    public int getUpgradesApplied() { return upgradesApplied; }
    public int getAiDecisionsExecuted() { return aiDecisionsExecuted; }
    public String getFinalNukeDesign() { return finalNukeDesign; }
    public String getPlayerWarheadSummary() { return playerWarheadSummary; }
    public String getOpponentWarheadSummary() { return opponentWarheadSummary; }
    public String getLastWeaponSummary() { return lastWeaponSummary; }
    public List<String> getHighlightEvents() { return highlightEvents; }

    public static MabMatchResultSummary from(MutuallyAssuredBlocksMatch match,
                                             ParticipantId playerId) {
        if (match == null) throw new IllegalArgumentException("match");
        if (playerId == null) playerId = ParticipantId.PLAYER_A;

        MatchDebugSnapshot snap = match.toDebugSnapshot();
        boolean over = match.isGameOver();
        ParticipantId winner = match.getWinner();

        MatchDebugSnapshot.ParticipantSummary self = (playerId == ParticipantId.PLAYER_A)
                ? snap.playerA() : snap.playerB();
        MatchDebugSnapshot.ParticipantSummary opp = (playerId == ParticipantId.PLAYER_A)
                ? snap.playerB() : snap.playerA();
        ParticipantId opponentId = playerId == ParticipantId.PLAYER_A
                ? ParticipantId.PLAYER_B : ParticipantId.PLAYER_A;

        Builder b = new Builder();
        b.matchOver = over;
        b.winner = winner;
        b.playerId = playerId;
        b.defconLevel = snap.defconLevel();
        b.playerLinesCleared = self.linesClearedTotal();
        b.playerPiecesLocked = self.piecesLocked();
        b.survivalMillis = match.getElapsedMatchMillis();
        b.playerMaxStackHeight = maxStackHeight(match, playerId);
        b.opponentMaxStackHeight = maxStackHeight(match, opponentId);
        b.playerCharge = self.currentNukeCharge();
        b.opponentCharge = opp.currentNukeCharge();
        b.finalNukeDesign = self.currentNukeDisplayName();
        b.playerWarheadSummary = summarizeWarhead(match, playerId);
        b.opponentWarheadSummary = summarizeWarhead(match, opponentId);

        List<MatchEventLogEntry> log = match.getEventLog();
        b.totalEvents = log.size();
        String reason = null;
        String lastWeapon = null;
        java.util.EnumMap<ParticipantId, String> lastGarbageSource =
                new java.util.EnumMap<>(ParticipantId.class);
        for (MatchEventLogEntry e : log) {
            String t = e.eventType();
            switch (t) {
                case "LAUNCH_AUTHORIZED" -> b.launchesAuthorized++;
                case "IMPACT_RESOLVED" -> b.impactsResolved++;
                case "GARBAGE_INSERTED" -> {
                    if (e.participantId() != null) {
                        lastGarbageSource.put(e.participantId(),
                                value(e.metadata(), "source", ""));
                    }
                }
                case "IMPACT_GARBAGE_WAVE_APPLIED" -> b.radiationWavesApplied++;
                case "MAB_ACTIVE_DOCTRINE_USED" -> {
                    if ("emp".equalsIgnoreCase(value(e.metadata(), "doctrine", ""))) {
                        b.empDisruptions++;
                    }
                }
                case "IMPACT_DISARM_APPLIED" -> {
                    b.disarmEvents++;
                    b.disarmAmountApplied += intValue(e.metadata(), "amountApplied");
                }
                case "IMPACT_SILO_DAMAGE_APPLIED" -> {
                    b.siloDamageEvents++;
                    b.siloDamageApplied += intValue(e.metadata(), "damageApplied");
                }
                case "CIVIL_DEFENSE_ACTIVATED" -> b.civilDefenseActivations++;
                case "UPGRADE_APPLIED" -> b.upgradesApplied++;
                case "AI_DECISION_EXECUTED" -> b.aiDecisionsExecuted++;
                case "TOP_OUT" -> reason = classifyTopOut(e,
                        lastGarbageSource.get(e.participantId()));
                default -> { /* counted only when we care */ }
            }
            String weapon = summarizeWeaponEvent(e);
            if (weapon != null) lastWeapon = weapon;
        }
        b.lastWeaponSummary = lastWeapon == null ? "(none launched)" : lastWeapon;
        b.highlightEvents = MabHudFormatter.formatEventFeed(log, 6);

        if (!over) {
            b.title = "Match In Progress";
            b.reason = (reason == null) ? "Match still active." : reason;
        } else if (winner == null) {
            b.title = "Mutual Collapse";
            b.reason = (reason == null) ? "Both arsenals collapsed." : reason;
        } else if (winner == playerId) {
            b.title = "Victory - Opponent Collapsed";
            b.reason = (reason == null) ? "Opponent arsenal collapsed." : reason;
        } else {
            b.title = "Defeat - Your Stack Collapsed";
            b.reason = (reason == null) ? "Your stack collapsed." : reason;
        }
        return new MabMatchResultSummary(b);
    }

    private static String summarizeWarhead(MutuallyAssuredBlocksMatch match, ParticipantId pid) {
        if (match == null || pid == null) return "(unknown)";
        ParticipantState p = match.getParticipant(pid);
        if (p == null) return "(unknown)";
        NukeBuildState nb = p.getNukeBuildState();
        if (nb == null) return "(unknown)";
        NukeDesign d = nb.getCurrentDesign();
        if (d == null) return "(unknown)";
        return d.getDisplayName()
                + " | " + (d.getDoctrineType() == null ? "Payload" : d.getDoctrineType().displayLabel())
                + " | charge " + nb.getCurrentBuildCharge() + "/" + nb.getEffectiveBuildChargeRequired()
                + " | BLAST " + d.getBlastRating()
                + " RAD " + d.getRadiationRating()
                + " EMP " + d.getEmpRating()
                + " DISARM " + d.getDisarmRating()
                + " SILO " + d.getSiloDamageRating()
                + " | intercept diff " + d.interceptDifficultyRating();
    }

    private static int maxStackHeight(MutuallyAssuredBlocksMatch match, ParticipantId pid) {
        ParticipantState p = match == null ? null : match.getParticipant(pid);
        if (p == null) return 0;
        int current = 0;
        try {
            current = p.getGameState().getBoard().getStackHeight();
        } catch (RuntimeException ignored) {}
        return Math.max(p.getMaxStackHeight(), current);
    }

    private static String summarizeWeaponEvent(MatchEventLogEntry e) {
        if (e == null || e.eventType() == null || e.metadata() == null) return null;
        Map<String, Object> m = e.metadata();
        if ("IMPACT_RESOLVED".equals(e.eventType())) {
            return "Impact: " + value(m, "designName", e.message())
                    + " | BLAST " + value(m, "blast", "?")
                    + " RAD " + value(m, "radiation", "?")
                    + " DISARM " + value(m, "disarmApplied", "?")
                    + " SILO " + value(m, "siloDamageApplied", "?")
                    + " | rows " + value(m, "immediateRows", "0")
                    + "/" + value(m, "delayedRows", "0");
        }
        if ("LAUNCH_AUTHORIZED".equals(e.eventType())) {
            return "Launch: " + value(m, "designName", e.message())
                    + " | payload " + value(m, "doctrine", "?")
                    + " | size " + value(m, "size", "?");
        }
        return null;
    }

    private static String value(Map<String, Object> m, String key, String fallback) {
        if (m == null) return fallback;
        Object v = m.get(key);
        return v == null ? fallback : String.valueOf(v);
    }

    private static int intValue(Map<String, Object> m, String key) {
        if (m == null) return 0;
        Object v = m.get(key);
        if (v instanceof Number n) return n.intValue();
        try {
            return v == null ? 0 : Integer.parseInt(String.valueOf(v));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static String classifyTopOut(MatchEventLogEntry e, String lastGarbageSource) {
        String raw = e == null ? "" : e.message();
        if (!"GARBAGE_OVERFLOW".equals(raw)) {
            return "Top-out: " + raw;
        }
        String source = lastGarbageSource == null ? "" : lastGarbageSource.toLowerCase();
        if (source.contains(":wave:")) {
            return "Radiation wave overflow: " + lastGarbageSource;
        }
        if (source.contains(":immediate:")) {
            return "Blast overflow: " + lastGarbageSource;
        }
        return "Garbage overflow: " + (lastGarbageSource == null || lastGarbageSource.isBlank()
                ? "unknown source" : lastGarbageSource);
    }

    private static final class Builder {
        boolean matchOver;
        ParticipantId winner;
        ParticipantId playerId;
        String title = "Match Ended";
        String reason = "";
        int defconLevel;
        int totalEvents;
        int playerLinesCleared;
        int playerPiecesLocked;
        long survivalMillis;
        int playerMaxStackHeight;
        int opponentMaxStackHeight;
        int playerCharge;
        int opponentCharge;
        int launchesAuthorized;
        int impactsResolved;
        int civilDefenseActivations;
        int radiationWavesApplied;
        int empDisruptions;
        int disarmEvents;
        int disarmAmountApplied;
        int siloDamageEvents;
        int siloDamageApplied;
        int upgradesApplied;
        int aiDecisionsExecuted;
        String finalNukeDesign = "(none)";
        String playerWarheadSummary = "(unknown)";
        String opponentWarheadSummary = "(unknown)";
        String lastWeaponSummary = "(none launched)";
        List<String> highlightEvents = List.of();
    }
}
