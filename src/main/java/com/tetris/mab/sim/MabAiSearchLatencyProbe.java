package com.tetris.mab.sim;

import com.tetris.mab.ai.MabAiDifficulty;
import com.tetris.mab.ai.search.AiBoardModel;
import com.tetris.mab.ai.search.AiEvaluator;
import com.tetris.mab.ai.search.AiSearch;
import com.tetris.mab.ai.search.AiSearchSettings;
import com.tetris.model.BagRandomizer;
import com.tetris.model.TetrominoType;

import java.util.ArrayList;
import java.util.List;

/**
 * Measures pure AI-search latency per difficulty so we can confirm
 * the search fits inside its time budget and won't starve the visible
 * driver of placements.
 *
 * <p>Run:
 * <pre>
 *   java -cp target\classes com.tetris.mab.sim.MabAiSearchLatencyProbe
 * </pre>
 *
 * <p>Offline-only.
 */
public final class MabAiSearchLatencyProbe {

    public static void main(String[] args) {
        int iterations = 200;
        if (args.length > 0) try { iterations = Math.max(20, Integer.parseInt(args[0])); }
            catch (NumberFormatException ignored) {}

        System.out.println("MAB AI search-latency probe iterations=" + iterations);
        System.out.printf("%-8s  %-7s  %-9s  %-9s  %-9s  %-9s%n",
                "diff", "budget", "avgMs", "p50Ms", "p95Ms", "maxMs");

        for (MabAiDifficulty d : new MabAiDifficulty[] {
                MabAiDifficulty.EASY, MabAiDifficulty.MEDIUM,
                MabAiDifficulty.HARD, MabAiDifficulty.EXPERT,
                MabAiDifficulty.MASTER }) {
            AiSearchSettings s = AiSearchSettings.forDifficulty(d);
            AiEvaluator ev = new AiEvaluator();
            AiSearch search = new AiSearch(s, ev, 1337L);
            AiBoardModel board = new AiBoardModel();
            BagRandomizer bag = new BagRandomizer();

            double[] times = new double[iterations];
            for (int i = 0; i < iterations; i++) {
                TetrominoType current = bag.next();
                List<TetrominoType> preview = new ArrayList<>();
                for (int k = 0; k < s.lookaheadDepth; k++) preview.add(bag.next());
                long t0 = System.nanoTime();
                AiSearch.Result r = search.search(new AiSearch.Inputs(
                        board, current, null, true, preview, false, 0));
                long t1 = System.nanoTime();
                times[i] = (t1 - t0) / 1_000_000.0;
                if (r != null && r.move != null) {
                    board = r.move.postBoard.deepCopy();
                }
            }
            java.util.Arrays.sort(times);
            double avg = 0;
            for (double t : times) avg += t;
            avg /= times.length;
            double p50 = times[times.length / 2];
            double p95 = times[Math.min(times.length - 1, (int) (times.length * 0.95))];
            double max = times[times.length - 1];
            System.out.printf("%-8s  %-7d  %-9.2f  %-9.2f  %-9.2f  %-9.2f%n",
                    d.name(), s.searchTimeBudgetMillis, avg, p50, p95, max);
        }
    }

    private MabAiSearchLatencyProbe() {}
}
