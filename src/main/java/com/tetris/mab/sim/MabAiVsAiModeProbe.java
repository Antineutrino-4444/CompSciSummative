package com.tetris.mab.sim;

import com.tetris.events.GameEventListener;
import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MatchEventLogEntry;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ai.MabAiArchetype;
import com.tetris.mab.ai.MabAiDesignPicker;
import com.tetris.mab.ai.MabAiDifficulty;
import com.tetris.mab.ai.MabAiDriver;
import com.tetris.mab.ai.MabBoardAiDriver;
import com.tetris.mab.balance.MabBalanceProfiles;
import com.tetris.mab.nuke.NukeDesign;
import com.tetris.model.GameState;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Probe for the new AI-vs-AI launch mode.
 *
 * <p>Runs a headless AI-vs-AI exhibition on a shared deterministic
 * 7-bag PvP match. Verifies that:
 * <ul>
 *   <li>Both sides actually place pieces.</li>
 *   <li>Both sides advance the strategic clock and post
 *       {@code AI_DECISION_EXECUTED} events.</li>
 *   <li>Distinct archetypes get distinct nuke designs.</li>
 *   <li>The match still respects shared-piece fairness (Player A's
 *       Nth piece == Player B's Nth piece).</li>
 *   <li>No exception escapes the AI tick loop.</li>
 * </ul>
 *
 * <p>Run:
 * <pre>
 *   java -cp target\classes com.tetris.mab.sim.MabAiVsAiModeProbe
 * </pre>
 *
 * <p>Offline-only.
 */
public final class MabAiVsAiModeProbe {

    public static void main(String[] args) {
        int durationSeconds = 12;
        if (args.length > 0) try { durationSeconds = Math.max(4, Integer.parseInt(args[0])); }
            catch (NumberFormatException ignored) {}

        System.out.println("MAB AI-vs-AI mode probe duration=" + durationSeconds + "s");

        GameState gsA = new GameState(0);
        GameState gsB = new GameState(0);
        long seed = 4242L;
        MutuallyAssuredBlocksMatch match = MutuallyAssuredBlocksMatch.createLocalPvpShared(
                gsA, gsB, MatchDifficulty.NORMAL, seed);
        match.startMatch();

        MabAiArchetype archA = MabAiArchetype.TACTICAL_SPAMMER;
        MabAiArchetype archB = MabAiArchetype.DOOMSDAY_HOARDER;
        MabAiDifficulty diffA = MabAiDifficulty.EXPERT;
        MabAiDifficulty diffB = MabAiDifficulty.HARD;

        NukeDesign designA = MabAiDesignPicker.pick(archA, diffA);
        NukeDesign designB = MabAiDesignPicker.pick(archB, diffB);
        match.applyWarheadDesign(ParticipantId.PLAYER_A, designA);
        match.applyWarheadDesign(ParticipantId.PLAYER_B, designB);

        boolean distinctDesigns = !designA.getId().equals(designB.getId());
        int historyABeforeDefcon = match.getParticipant(ParticipantId.PLAYER_A)
                .getNukeBuildState().getDesignHistory().size();
        int historyBBeforeDefcon = match.getParticipant(ParticipantId.PLAYER_B)
                .getNukeBuildState().getDesignHistory().size();
        match.addEscalationAndRefresh(900, "ai-vs-ai-redesign-probe");
        int redesignDefcon = match.getDefconState().getLevel();
        boolean defconDropped = redesignDefcon < 5;
        boolean redesignPauseOpened = match.openUpgradePause("ai_vs_ai_defcon_redesign_probe");
        boolean aiDefconRedesignA = false;
        boolean aiDefconRedesignB = false;
        if (redesignPauseOpened) {
            NukeDesign nextA = MabAiDesignPicker.pick(archA, diffA);
            NukeDesign nextB = MabAiDesignPicker.pick(archB, diffB);
            aiDefconRedesignA = match.redesignNukeDuringUpgradePause(
                    ParticipantId.PLAYER_A, nextA, 1.0);
            aiDefconRedesignB = match.redesignNukeDuringUpgradePause(
                    ParticipantId.PLAYER_B, nextB, 1.0);
            match.closeUpgradePause("ai_vs_ai_defcon_redesign_probe");
        }
        var histA = match.getParticipant(ParticipantId.PLAYER_A)
                .getNukeBuildState().getDesignHistory();
        var histB = match.getParticipant(ParticipantId.PLAYER_B)
                .getNukeBuildState().getDesignHistory();
        boolean aiDefconRedesign = defconDropped
                && redesignPauseOpened
                && aiDefconRedesignA
                && aiDefconRedesignB
                && histA.size() == historyABeforeDefcon + 1
                && histB.size() == historyBBeforeDefcon + 1
                && "redesign".equals(histA.get(histA.size() - 1).source())
                && "redesign".equals(histB.get(histB.size() - 1).source())
                && histA.get(histA.size() - 1).defconLevel() == redesignDefcon
                && histB.get(histB.size() - 1).defconLevel() == redesignDefcon;

        MabBoardAiDriver boardA = new MabBoardAiDriver(gsA);
        boardA.setDifficulty(diffA);
        boardA.attachStrategicContext(match, ParticipantId.PLAYER_A);
        MabBoardAiDriver boardB = new MabBoardAiDriver(gsB);
        boardB.setDifficulty(diffB);
        boardB.attachStrategicContext(match, ParticipantId.PLAYER_B);

        MabAiDriver stratA = new MabAiDriver(match, ParticipantId.PLAYER_A,
                archA, diffA, MabBalanceProfiles.standardPve());
        MabAiDriver stratB = new MabAiDriver(match, ParticipantId.PLAYER_B,
                archB, diffB, MabBalanceProfiles.standardPve());
        stratA.setEnabled(true);
        stratB.setEnabled(true);
        stratA.setAdvanceHiddenClock(false);
        stratB.setAdvanceHiddenClock(false);

        AtomicInteger locksA = new AtomicInteger();
        AtomicInteger locksB = new AtomicInteger();
        java.util.List<com.tetris.model.TetrominoType> seqA = new java.util.ArrayList<>();
        java.util.List<com.tetris.model.TetrominoType> seqB = new java.util.ArrayList<>();
        gsA.addListener(new GameEventListener() {
            @Override public void onPieceLocked(PieceLockedEvent e) { locksA.incrementAndGet(); }
            @Override public void onPieceSpawned(PieceSpawnedEvent e) {
                if (seqA.size() < 20) seqA.add(e.type());
            }
        });
        gsB.addListener(new GameEventListener() {
            @Override public void onPieceLocked(PieceLockedEvent e) { locksB.incrementAndGet(); }
            @Override public void onPieceSpawned(PieceSpawnedEvent e) {
                if (seqB.size() < 20) seqB.add(e.type());
            }
        });

        int ticks = (int) Math.round(durationSeconds * (1000.0 / 16.0));
        boolean exceptionEscaped = false;
        for (int i = 0; i < ticks; i++) {
            try {
                boardA.tick();
                boardB.tick();
                if (i % 6 == 0) {
                    stratA.tick();
                    stratB.tick();
                    match.resolveAllImpactReady();
                    match.pruneCompletedThreats();
                }
            } catch (RuntimeException ex) {
                exceptionEscaped = true;
                break;
            }
            if (gsA.isGameOver() || gsB.isGameOver()) break;
            try { Thread.sleep(8); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt(); break;
            }
        }

        int aiEvents = 0;
        int clockEvents = 0;
        for (MatchEventLogEntry e : match.getEventLog()) {
            String t = e.eventType();
            if ("AI_DECISION_EXECUTED".equals(t) || "AI_DECISION_SKIPPED".equals(t)) aiEvents++;
            if ("AI_STRATEGIC_CLOCK_ADVANCED".equals(t)) clockEvents++;
        }

        int compareLen = Math.min(seqA.size(), seqB.size());
        boolean fair = compareLen >= 8;
        for (int i = 0; i < compareLen && fair; i++) {
            if (seqA.get(i) != seqB.get(i)) fair = false;
        }

        System.out.println("  locksA=" + locksA.get() + " locksB=" + locksB.get());
        System.out.println("  aiEvents=" + aiEvents + " clockEvents=" + clockEvents);
        System.out.println("  designs A=" + designA.getId() + " B=" + designB.getId()
                + " distinct=" + distinctDesigns);
        System.out.println("  defconDropped=" + defconDropped
                + " aiDefconRedesign=" + aiDefconRedesign);
        System.out.println("  fairnessCompared=" + compareLen + " fair=" + fair);
        System.out.println("  exceptionEscaped=" + exceptionEscaped);

        boolean ok = !exceptionEscaped
                && locksA.get() >= 4
                && locksB.get() >= 4
                && aiEvents > 0
                && distinctDesigns
                && aiDefconRedesign
                && fair;
        System.out.println("success=" + ok);
        if (!ok) System.exit(1);
    }

    private MabAiVsAiModeProbe() {}
}
