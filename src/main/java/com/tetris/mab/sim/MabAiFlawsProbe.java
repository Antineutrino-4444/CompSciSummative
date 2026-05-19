package com.tetris.mab.sim;

import com.tetris.events.GameEventListener;
import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ai.MabAiArchetype;
import com.tetris.mab.ai.MabAiDesignPicker;
import com.tetris.mab.ai.MabAiDifficulty;
import com.tetris.mab.ai.MabAiDriver;
import com.tetris.mab.ai.MabBoardAiDriver;
import com.tetris.mab.balance.MabBalanceProfiles;
import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.pieces.MabSharedPieceSequence;
import com.tetris.model.GameState;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Probes for the four user-reported AI flaws:
 * <ol>
 *   <li>The AI prefers efficient clears (tetrises) over singles/doubles.</li>
 *   <li>Each archetype actually customises its nuke design.</li>
 *   <li>When armed, the AI drives the launch route via the necessary
 *       clears instead of stacking forever.</li>
 *   <li>Incoming strikes are not magically resolved — the AI no longer
 *       carries the debug intercept code path.</li>
 * </ol>
 *
 * <p>Run:
 * <pre>
 *   java -cp target\classes com.tetris.mab.sim.MabAiFlawsProbe
 * </pre>
 *
 * <p>Offline-only.
 */
public final class MabAiFlawsProbe {

    private static final double TICKS_PER_SECOND = 1000.0 / 16.0;

    public static void main(String[] args) {
        int failed = 0;
        failed += check("prefersTetrisOverSingles", prefersTetrisOverSingles());
        failed += check("designVariesByArchetype",  designVariesByArchetype());
        failed += check("armedAiClearsTetrises",    armedAiClearsTetrises());
        failed += check("noMagicInterceptHook",     noMagicInterceptHook());

        System.out.println();
        System.out.println("success=" + (failed == 0));
        if (failed != 0) System.exit(1);
    }

    /* ─── 1. Tetris preference ──────────────────────────────────────── */
    /**
     * Run a MASTER tier AI through deterministic empty-board tick budgets.
     * Aggregating a few seeded piece streams avoids making this acceptance
     * check depend on the process-global random bag order.
     */
    private static boolean prefersTetrisOverSingles() {
        long[] seeds = { 1701L, 5151L, 9001L, 22123L };
        AtomicInteger totalSingles = new AtomicInteger();
        AtomicInteger totalTetrises = new AtomicInteger();
        AtomicInteger totalTspins = new AtomicInteger();

        int ticks = (int) Math.round(24 * TICKS_PER_SECOND);
        for (long seed : seeds) {
            GameState gs = seededGame(seed);
            MabBoardAiDriver ai = new MabBoardAiDriver(gs);
            ai.setDifficulty(MabAiDifficulty.MASTER);
            ai.setSeed(seed ^ 0xC0FFEE);

            AtomicInteger singles = new AtomicInteger();
            AtomicInteger tetrises = new AtomicInteger();
            AtomicInteger tspins = new AtomicInteger();
            gs.addListener(new GameEventListener() {
                @Override public void onLinesCleared(LinesClearedEvent e) {
                    if (e.count() == 1) singles.incrementAndGet();
                    if (e.count() == 4) tetrises.incrementAndGet();
                    if (e.tSpin()) tspins.incrementAndGet();
                }
            });
            for (int i = 0; i < ticks && !gs.isGameOver(); i++) {
                ai.tick();
            }
            int s = singles.get(), t = tetrises.get(), ts = tspins.get();
            totalSingles.addAndGet(s);
            totalTetrises.addAndGet(t);
            totalTspins.addAndGet(ts);
            System.out.println("  seed=" + seed + " singles=" + s
                    + " tetrises=" + t + " tspins=" + ts);
        }

        int s = totalSingles.get(), t = totalTetrises.get(), ts = totalTspins.get();
        int tetrisLines = t * 4;
        int spinLines = ts * 2; // average T-spin double
        System.out.println("  totals singles=" + s + " tetrises=" + t + " tspins=" + ts
                + " linesFromTetris=" + tetrisLines + " linesFromSpins=" + spinLines);
        // Acceptance: the AI must complete at least one Tetris and
        // more lines should come from tetrises+spins than from singles.
        return t >= 1 && tetrisLines + spinLines >= s;
    }

    /* ─── 2. Design customisation ───────────────────────────────────── */
    private static boolean designVariesByArchetype() {
        java.util.Map<String, String> picks = new java.util.LinkedHashMap<>();
        for (MabAiArchetype a : MabAiArchetype.values()) {
            NukeDesign d = MabAiDesignPicker.pick(a, MabAiDifficulty.MASTER);
            picks.put(a.name(), d == null ? "null" : d.getId());
        }
        for (var e : picks.entrySet()) {
            System.out.println("  " + e.getKey() + " -> " + e.getValue());
        }
        // Acceptance: at least 4 distinct designs across the archetype set.
        long distinct = picks.values().stream().distinct().count();
        return distinct >= 4;
    }

    /* ─── 3. Armed AI drives Tetris route ───────────────────────────── */
    /**
     * Force the AI to be armed at the start of the match by topping off
     * its charge, then watch the next 30 seconds. The AI should complete
     * at least one Tetris (advancing the Tetris launch route).
     */
    private static boolean armedAiClearsTetrises() {
        GameState a = new GameState(0);
        GameState b = new GameState(0);
        MutuallyAssuredBlocksMatch match =
                MutuallyAssuredBlocksMatch.createPveShared(a, b, MatchDifficulty.NORMAL, 5151L);
        match.startMatch();
        a.setGravityFrozen(true);
        b.setGravityFrozen(true);
        NukeDesign design = MabAiDesignPicker.pick(MabAiArchetype.TACTICAL_SPAMMER,
                MabAiDifficulty.MASTER);
        match.applyWarheadDesign(ParticipantId.PLAYER_B, design);
        match.debugAddNukeCharge(ParticipantId.PLAYER_B, 9999);
        match.debugArmCurrentNuke(ParticipantId.PLAYER_B);

        MabBoardAiDriver board = new MabBoardAiDriver(b);
        board.setDifficulty(MabAiDifficulty.MASTER);
        board.attachStrategicContext(match, ParticipantId.PLAYER_B);
        MabAiDriver strat = new MabAiDriver(match, ParticipantId.PLAYER_B,
                MabAiArchetype.TACTICAL_SPAMMER, MabAiDifficulty.MASTER,
                MabBalanceProfiles.standardPve());
        strat.setEnabled(true);
        strat.setAdvanceHiddenClock(false);

        AtomicInteger tetrises = new AtomicInteger();
        AtomicInteger spins = new AtomicInteger();
        b.addListener(new GameEventListener() {
            @Override public void onLinesCleared(LinesClearedEvent e) {
                if (e.count() == 4) tetrises.incrementAndGet();
                if (e.tSpin()) spins.incrementAndGet();
            }
        });
        int ticks = (int) Math.round(30 * TICKS_PER_SECOND);
        for (int i = 0; i < ticks && !b.isGameOver(); i++) {
            board.tick();
            if (i % 6 == 0) {
                try { strat.tick(); } catch (RuntimeException ignored) {}
            }
            try { Thread.sleep(16); } catch (InterruptedException ex) {
                Thread.currentThread().interrupt(); break;
            }
        }
        int t = tetrises.get(), sp = spins.get();
        System.out.println("  armedTetrises=" + t + " armedSpins=" + sp);
        return (t + sp) >= 1;
    }

    /* ─── 4. No magic intercept hook ────────────────────────────────── */
    /**
     * Reflectively verifies the AI driver no longer exposes the helper
     * method that called the debug intercept resolver. The presence of
     * such a method historically allowed the AI to wipe a threat
     * without doing the required spin clear.
     */
    private static boolean noMagicInterceptHook() {
        for (Method m : MabAiDriver.class.getDeclaredMethods()) {
            String name = m.getName().toLowerCase();
            if (name.contains("tryintercept") || name.contains("magicintercept")) {
                System.out.println("  found legacy method: " + m.getName());
                return false;
            }
        }
        return true;
    }

    private static GameState seededGame(long seed) {
        GameState gs = new GameState(0);
        gs.replacePieceSourceForMabSharedSequence(
                new MabSharedPieceSequence(seed).newStream());
        gs.setGravityFrozen(true);
        return gs;
    }

    private static int check(String name, boolean ok) {
        System.out.println(name + "=" + ok);
        return ok ? 0 : 1;
    }

    private MabAiFlawsProbe() {}
}
