package com.tetris.mab.sim;

import com.tetris.controller.GameController;
import com.tetris.controller.GameLaunchMode;
import com.tetris.controller.FrameRate;
import com.tetris.mab.ai.MabAiArchetype;
import com.tetris.mab.ai.MabAiDifficulty;
import com.tetris.mab.balance.MabBalanceProfiles;
import com.tetris.mab.ui.MabAiVsAiConfig;
import com.tetris.mab.ui.MabLocalPvpConfig;
import com.tetris.mab.ui.MabPveConfig;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

/**
 * Automated FPS gate for the playable controller paths.
 *
 * <p>The probe starts each embedded game mode without human input, listens to
 * the controller's render-frame probe port, discards a warmup window, then
 * verifies every measured one-second bucket stays within +/-2 frames of the
 * fixed 60 FPS target.
 */
public final class GameFpsStabilityProbe {

    private static final int DEFAULT_MEASURE_SECONDS = 4;
    private static final int ALLOWED_FRAME_DELTA = 1;

    public static void main(String[] args) throws Exception {
        int measureSeconds = DEFAULT_MEASURE_SECONDS;
        if (args.length > 0) {
            try {
                measureSeconds = Math.max(2, Integer.parseInt(args[0]));
            } catch (NumberFormatException ignored) {}
        }

        System.out.println("=== Game FPS Stability Probe ===");
        System.out.println("targetFps=" + FrameRate.RENDER_FPS);
        System.out.println("allowedFrameDelta=" + ALLOWED_FRAME_DELTA);
        System.out.println("measureSeconds=" + measureSeconds);

        List<Scenario> scenarios = new ArrayList<>();
        scenarios.add(new Scenario("NORMAL_TETRIS",
                () -> new GameController(1, GameLaunchMode.NORMAL_TETRIS)));
        scenarios.add(new Scenario("MAB_DEBUG_MODE",
                () -> new GameController(1, GameLaunchMode.MAB_DEBUG)));
        for (MabAiDifficulty difficulty : List.of(
                MabAiDifficulty.EASY,
                MabAiDifficulty.MEDIUM,
                MabAiDifficulty.HARD,
                MabAiDifficulty.EXPERT,
                MabAiDifficulty.MASTER)) {
            scenarios.add(new Scenario("MAB_PVE_" + difficulty.name(),
                    () -> new GameController(MabPveConfig.fromSelections(
                            1,
                            MabAiArchetype.BALANCED,
                            difficulty,
                            false,
                            MabBalanceProfiles.STANDARD_PVE))));
        }
        scenarios.add(new Scenario("MAB_LOCAL_PVP",
                () -> new GameController(MabLocalPvpConfig.defaults())));
        scenarios.add(new Scenario("MAB_AI_VS_AI_MASTER",
                () -> new GameController(new MabAiVsAiConfig(
                        1,
                        MabAiArchetype.TACTICAL_SPAMMER,
                        MabAiDifficulty.MASTER,
                        MabAiArchetype.DOOMSDAY_HOARDER,
                        MabAiDifficulty.MASTER,
                        MabBalanceProfiles.STANDARD_PVE))));

        boolean ok = true;
        for (Scenario scenario : scenarios) {
            ok &= runScenario(scenario, measureSeconds);
        }

        System.out.println("success=" + ok);
        if (!ok) System.exit(1);
    }

    private static boolean runScenario(Scenario scenario, int measureSeconds) throws Exception {
        GameController controller = scenario.controllerFactory().get();
        FrameRecorder recorder = new FrameRecorder(measureSeconds);
        JComponent[] root = new JComponent[1];
        LongConsumer listener = recorder::record;
        controller.addRenderFrameProbeListener(listener);

        try {
            SwingUtilities.invokeAndWait(() ->
                    root[0] = controller.startEmbedded(() -> {}));
            boolean completed = recorder.awaitCompletion(timeoutMillis(measureSeconds));
            Report report = recorder.report();
            boolean targetOk = controller.getTargetRenderFps() == FrameRate.RENDER_FPS;
            boolean rootOk = root[0] != null;
            boolean bucketsOk = report.within(FrameRate.RENDER_FPS, ALLOWED_FRAME_DELTA);
            boolean ok = completed && targetOk && rootOk && bucketsOk;

            System.out.println("scenario=" + scenario.name()
                    + " root=" + (root[0] == null ? "null" : root[0].getClass().getSimpleName())
                    + " completed=" + completed
                    + " buckets=" + Arrays.toString(report.bucketCounts())
                    + " min=" + report.min()
                    + " max=" + report.max()
                    + " avg=" + String.format("%.2f", report.average())
                    + " lastRenderFps=" + controller.getLastRenderFpsForProbe()
                    + " ok=" + ok);
            return ok;
        } finally {
            controller.removeRenderFrameProbeListener(listener);
            SwingUtilities.invokeAndWait(controller::stop);
        }
    }

    private static long timeoutMillis(int measureSeconds) {
        return 1_500L + measureSeconds * 1_000L + 3_000L;
    }

    private record Scenario(String name, Supplier<GameController> controllerFactory) {}

    private record Report(int[] bucketCounts, int min, int max, double average) {
        boolean within(int target, int delta) {
            if (bucketCounts.length == 0) return false;
            for (int count : bucketCounts) {
                if (Math.abs(count - target) > delta) return false;
            }
            return true;
        }
    }

    private static final class FrameRecorder {
        private static final long WARMUP_NS = 1_000_000_000L;
        private static final long SECOND_NS = 1_000_000_000L;

        private final int measureSeconds;
        private final ArrayList<Long> samples = new ArrayList<>();
        private long firstSeenNs = -1L;
        private long measureStartNs = -1L;
        private boolean complete;

        FrameRecorder(int measureSeconds) {
            this.measureSeconds = Math.max(1, measureSeconds);
        }

        synchronized void record(long nowNs) {
            if (firstSeenNs < 0L) firstSeenNs = nowNs;
            if (nowNs - firstSeenNs < WARMUP_NS) return;
            if (measureStartNs < 0L) measureStartNs = nowNs;
            long elapsed = nowNs - measureStartNs;
            if (elapsed < measureSeconds * SECOND_NS) {
                samples.add(nowNs);
            } else {
                complete = true;
                notifyAll();
            }
        }

        synchronized boolean awaitCompletion(long timeoutMs) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (!complete && System.currentTimeMillis() < deadline) {
                wait(Math.max(1L, deadline - System.currentTimeMillis()));
            }
            return complete;
        }

        synchronized Report report() {
            int[] buckets = new int[measureSeconds];
            if (!samples.isEmpty()) {
                long base = samples.get(0);
                for (long sample : samples) {
                    int bucket = (int) ((sample - base) / SECOND_NS);
                    if (bucket >= 0 && bucket < buckets.length) buckets[bucket]++;
                }
            }
            int min = buckets.length == 0 ? 0 : Integer.MAX_VALUE;
            int max = 0;
            int total = 0;
            for (int bucket : buckets) {
                min = Math.min(min, bucket);
                max = Math.max(max, bucket);
                total += bucket;
            }
            double average = buckets.length == 0 ? 0.0 : total / (double) buckets.length;
            return new Report(buckets, min == Integer.MAX_VALUE ? 0 : min, max, average);
        }
    }

    private GameFpsStabilityProbe() {}
}
