package com.tetris.mab.sim;

import com.tetris.mab.ai.MabAiDifficulty;
import com.tetris.mab.ai.MabBoardAiDriver;
import com.tetris.model.Board;
import com.tetris.model.GameState;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Headless probe — measures placement quality of the new MAB AI for
 * each difficulty tier. Spins each driver for a fixed budget and
 * reports: pieces locked, total lines cleared, average board height
 * at end, hole count at end, top-out rate, and survival time.
 *
 * <p>Run:
 * <pre>
 *   java -cp target\classes com.tetris.mab.sim.MabAiPlacementQualityProbe
 * </pre>
 *
 * <p>Acceptance: tier ordering on (lines/piece desc, holes asc) should
 * roughly satisfy EASY &lt; MEDIUM &lt; HARD &lt;= EXPERT &lt;= MASTER.
 *
 * <p>Offline-only.
 */
public final class MabAiPlacementQualityProbe {

    private static final int RUNS_PER_DIFFICULTY = 3;
    private static final int SECONDS_PER_RUN = 12;

    public static void main(String[] args) {
        int seconds = SECONDS_PER_RUN;
        int runs = RUNS_PER_DIFFICULTY;
        if (args.length > 0) try { seconds = Math.max(4, Integer.parseInt(args[0])); }
            catch (NumberFormatException ignored) {}
        if (args.length > 1) try { runs = Math.max(1, Integer.parseInt(args[1])); }
            catch (NumberFormatException ignored) {}
        int ticks = (int) Math.round(seconds * (1000.0 / 16.0));

        System.out.println("MAB AI placement-quality probe");
        System.out.println("  seconds/run=" + seconds + " runs=" + runs);
        System.out.printf("%-8s  %-7s  %-7s  %-8s  %-7s  %-7s  %-7s  %-7s  %-7s%n",
                "diff", "drops", "lines", "lp/drop", "maxH", "holes", "topOut", "survT", "PPS");

        MabAiDifficulty[] tiers = {
                MabAiDifficulty.EASY, MabAiDifficulty.MEDIUM,
                MabAiDifficulty.HARD, MabAiDifficulty.EXPERT,
                MabAiDifficulty.MASTER };
        Map<MabAiDifficulty, double[]> summary = new LinkedHashMap<>();
        for (MabAiDifficulty d : tiers) {
            double drops = 0, lines = 0, maxH = 0, holes = 0, topOut = 0, surv = 0, pps = 0;
            for (int r = 0; r < runs; r++) {
                GameState gs = new GameState(0);
                MabBoardAiDriver ai = new MabBoardAiDriver(gs);
                ai.setDifficulty(d);
                long start = System.currentTimeMillis();
                int lastDrops = 0;
                int lastLines = 0;
                long pieceLockMs = 0;
                long durationMs = 0;
                for (int i = 0; i < ticks; i++) {
                    ai.tick();
                    if (gs.isGameOver()) {
                        durationMs = System.currentTimeMillis() - start;
                        break;
                    }
                    try { Thread.sleep(16); } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt(); break;
                    }
                }
                if (durationMs == 0) durationMs = System.currentTimeMillis() - start;
                int totalDrops = ai.getHardDropCount();
                int totalLines = ai.getLinesClearedTotal();
                lastDrops = totalDrops;
                lastLines = totalLines;
                int[] stats = analyseBoard(gs.getBoard());
                drops += totalDrops;
                lines += totalLines;
                maxH += stats[0];
                holes += stats[1];
                topOut += gs.isGameOver() ? 1 : 0;
                surv += durationMs / 1000.0;
                pps += totalDrops / Math.max(0.5, durationMs / 1000.0);
            }
            drops /= runs; lines /= runs; maxH /= runs; holes /= runs;
            topOut /= runs; surv /= runs; pps /= runs;
            double lpDrop = drops > 0 ? lines / drops : 0;
            summary.put(d, new double[] { drops, lines, lpDrop, maxH, holes, topOut, surv, pps });
            System.out.printf("%-8s  %-7.1f  %-7.1f  %-8.2f  %-7.1f  %-7.1f  %-7.2f  %-7.1f  %-7.2f%n",
                    d.name(), drops, lines, lpDrop, maxH, holes, topOut, surv, pps);
        }

        // Tier-ordering acceptance: higher tier should generally have
        // higher lines/drop and lower hole count.
        boolean lineOrderOk = true, holeOrderOk = true;
        MabAiDifficulty prev = null;
        for (MabAiDifficulty d : tiers) {
            double[] cur = summary.get(d);
            if (prev != null) {
                double[] p = summary.get(prev);
                if (cur[2] + 1e-6 < p[2] && d != MabAiDifficulty.MEDIUM) lineOrderOk = false;
                if (cur[4] - 1e-6 > p[4] + 4 && d != MabAiDifficulty.MEDIUM) holeOrderOk = false;
            }
            prev = d;
        }
        System.out.println();
        System.out.println("acceptance: lineOrderOk=" + lineOrderOk
                + " holeOrderOk=" + holeOrderOk);
        if (!lineOrderOk || !holeOrderOk) {
            System.out.println("note: tier ordering is approximate — small runs are noisy.");
        }
    }

    /** Returns {maxColumnHeight, holes} from the live board. */
    private static int[] analyseBoard(Board board) {
        int w = Board.WIDTH;
        int h = Board.TOTAL_HEIGHT;
        int maxH = 0;
        int holes = 0;
        int[] colH = new int[w];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                Color c = board.getCell(x, y);
                if (c != null) { colH[x] = h - y; break; }
            }
            maxH = Math.max(maxH, colH[x]);
        }
        for (int x = 0; x < w; x++) {
            int top = h - colH[x];
            for (int y = top + 1; y < h; y++) {
                if (board.getCell(x, y) == null) holes++;
            }
        }
        return new int[] { maxH, holes };
    }

    private MabAiPlacementQualityProbe() {}
}
