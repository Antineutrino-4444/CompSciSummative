package com.tetris.mab.ui;

import com.tetris.mab.MatchDebugSnapshot;
import com.tetris.mab.MatchEventLogEntry;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;

import java.util.ArrayList;
import java.util.List;

/**
 * Step 18 — immutable, snapshot-only summary of a finished MAB PvE
 * match. Built from the live {@link MutuallyAssuredBlocksMatch} via
 * {@link #from(MutuallyAssuredBlocksMatch, ParticipantId)} when the
 * player-facing result dialog appears.
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
    private final int playerCharge;
    private final int opponentCharge;
    private final int launchesAuthorized;
    private final int impactsResolved;
    private final int radarScans;
    private final int decoysActivated;
    private final int civilDefenseActivations;
    private final int upgradesApplied;
    private final int aiDecisionsExecuted;
    private final String finalNukeDesign;
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
        this.playerCharge = b.playerCharge;
        this.opponentCharge = b.opponentCharge;
        this.launchesAuthorized = b.launchesAuthorized;
        this.impactsResolved = b.impactsResolved;
        this.radarScans = b.radarScans;
        this.decoysActivated = b.decoysActivated;
        this.civilDefenseActivations = b.civilDefenseActivations;
        this.upgradesApplied = b.upgradesApplied;
        this.aiDecisionsExecuted = b.aiDecisionsExecuted;
        this.finalNukeDesign = b.finalNukeDesign;
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
    public int getPlayerCharge() { return playerCharge; }
    public int getOpponentCharge() { return opponentCharge; }
    public int getLaunchesAuthorized() { return launchesAuthorized; }
    public int getImpactsResolved() { return impactsResolved; }
    public int getRadarScans() { return radarScans; }
    public int getDecoysActivated() { return decoysActivated; }
    public int getCivilDefenseActivations() { return civilDefenseActivations; }
    public int getUpgradesApplied() { return upgradesApplied; }
    public int getAiDecisionsExecuted() { return aiDecisionsExecuted; }
    public String getFinalNukeDesign() { return finalNukeDesign; }
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

        Builder b = new Builder();
        b.matchOver = over;
        b.winner = winner;
        b.playerId = playerId;
        b.defconLevel = snap.defconLevel();
        b.playerLinesCleared = self.linesClearedTotal();
        b.playerPiecesLocked = self.piecesLocked();
        b.playerCharge = self.currentNukeCharge();
        b.opponentCharge = opp.currentNukeCharge();
        b.finalNukeDesign = self.currentNukeDisplayName();

        // Walk the bounded log to compute outcome stats and highlight events.
        List<MatchEventLogEntry> log = match.getEventLog();
        b.totalEvents = log.size();
        String reason = null;
        for (MatchEventLogEntry e : log) {
            String t = e.eventType();
            switch (t) {
                case "LAUNCH_AUTHORIZED" -> b.launchesAuthorized++;
                case "IMPACT_RESOLVED" -> b.impactsResolved++;
                case "RADAR_SCAN_COMPLETED" -> b.radarScans++;
                case "DECOY_ACTIVATED" -> b.decoysActivated++;
                case "CIVIL_DEFENSE_ACTIVATED" -> b.civilDefenseActivations++;
                case "UPGRADE_APPLIED" -> b.upgradesApplied++;
                case "AI_DECISION_EXECUTED" -> b.aiDecisionsExecuted++;
                case "TOP_OUT" -> reason = "Top-out: " + e.message();
                default -> { /* counted only when we care */ }
            }
        }
        // Build highlight events: last 6 player-facing-ish entries.
        int from = Math.max(0, log.size() - 12);
        List<String> highlights = new ArrayList<>();
        for (int i = from; i < log.size(); i++) {
            MatchEventLogEntry e = log.get(i);
            highlights.add("#" + e.sequenceNumber() + " " + e.eventType()
                    + (e.message() == null || e.message().isBlank() ? "" : " — " + e.message()));
        }
        b.highlightEvents = highlights;

        // Title + reason.
        if (!over) {
            b.title = "Match In Progress";
            b.reason = (reason == null) ? "Match still active." : reason;
        } else if (winner == null) {
            b.title = "Mutual Collapse";
            b.reason = (reason == null) ? "Both arsenals collapsed." : reason;
        } else if (winner == playerId) {
            b.title = "Victory — Opponent Collapsed";
            b.reason = (reason == null) ? "Opponent arsenal collapsed." : reason;
        } else {
            b.title = "Defeat — Your Stack Collapsed";
            b.reason = (reason == null) ? "Your stack collapsed." : reason;
        }
        return new MabMatchResultSummary(b);
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
        int playerCharge;
        int opponentCharge;
        int launchesAuthorized;
        int impactsResolved;
        int radarScans;
        int decoysActivated;
        int civilDefenseActivations;
        int upgradesApplied;
        int aiDecisionsExecuted;
        String finalNukeDesign = "(none)";
        List<String> highlightEvents = List.of();
    }
}
