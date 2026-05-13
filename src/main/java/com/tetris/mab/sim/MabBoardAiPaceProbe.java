package com.tetris.mab.sim;

import com.tetris.mab.ai.MabAiDifficulty;
import com.tetris.mab.ai.MabBoardAiDriver;
import com.tetris.model.GameState;

/**
 * Step 20 Third Refinement — manual probe. Spins the visible-board AI
 * for each difficulty and prints measured PPS so the playtest section
 * of Step20.md can record actual numbers without launching the Swing
 * UI on a headless CI machine.
 *
 * <p>Run:
 * <pre>
 *   java -cp target\classes com.tetris.mab.sim.MabBoardAiPaceProbe
 * </pre>
 *
 * <p><b>Offline-only.</b>
 */
public final class MabBoardAiPaceProbe {

    public static void main(String[] args) {
        int seconds = 6;
        if (args.length > 0) {
            try { seconds = Math.max(2, Integer.parseInt(args[0])); }
            catch (NumberFormatException ignored) {}
        }
        int ticks = (int) Math.round(seconds * (1000.0 / 16.0));
        System.out.println("MAB board-AI pace probe: " + seconds + " s (" + ticks + " ticks)");
        System.out.printf("%-8s  %-12s  %-7s  %-7s  %-7s%n",
                "diff", "targetPps", "drops", "measPps", "ticks");
        for (MabAiDifficulty d : MabAiDifficulty.values()) {
            GameState gs = new GameState(0);
            MabBoardAiDriver ai = new MabBoardAiDriver(gs);
            ai.setDifficulty(d);
            long start = System.currentTimeMillis();
            for (int i = 0; i < ticks; i++) {
                ai.tick();
                if (gs.isGameOver()) break;
                try { Thread.sleep(16); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); break;
                }
            }
            long elapsedMs = System.currentTimeMillis() - start;
            double measured = ai.getHardDropCount() / (elapsedMs / 1000.0);
            System.out.printf("%-8s  %-12.2f  %-7d  %-7.2f  %-7d  go=%s%n",
                    d.name(), ai.getTargetPps(),
                    ai.getHardDropCount(), measured, (int) ai.getTickCount(),
                    gs.isGameOver());
        }
    }

    private MabBoardAiPaceProbe() {}
}
