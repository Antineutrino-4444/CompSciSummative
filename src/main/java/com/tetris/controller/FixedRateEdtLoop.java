package com.tetris.controller;

import javax.swing.SwingUtilities;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fixed-rate scheduler that performs Swing work on the EDT.
 *
 * <p>{@link javax.swing.Timer} only accepts millisecond delays, which means
 * 60 FPS has to be approximated as either 16 ms or 17 ms. This loop schedules
 * against nanoseconds and posts the actual callback onto the EDT. It coalesces
 * missed callbacks to at most one queued EDT task, so a temporary stall does
 * not build an unbounded render backlog.
 */
final class FixedRateEdtLoop {

    private final String threadName;
    private final long intervalNs;
    private final Runnable callback;
    private final boolean coalesce;
    private final AtomicBoolean queued = new AtomicBoolean(false);
    private final LoopTelemetry telemetry = new LoopTelemetry();

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> future;
    private volatile boolean running;

    FixedRateEdtLoop(String threadName, long intervalNs, Runnable callback) {
        this(threadName, intervalNs, callback, true);
    }

    FixedRateEdtLoop(String threadName, long intervalNs, Runnable callback, boolean coalesce) {
        if (intervalNs <= 0L) throw new IllegalArgumentException("intervalNs");
        if (callback == null) throw new IllegalArgumentException("callback");
        this.threadName = (threadName == null || threadName.isBlank())
                ? "fixed-rate-edt-loop"
                : threadName;
        this.intervalNs = intervalNs;
        this.callback = callback;
        this.coalesce = coalesce;
    }

    synchronized void start() {
        if (running) return;
        running = true;
        queued.set(false);
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, threadName);
            t.setDaemon(true);
            return t;
        });
        future = executor.scheduleAtFixedRate(
                this::postTick, 0L, intervalNs, TimeUnit.NANOSECONDS);
    }

    synchronized void stop() {
        running = false;
        queued.set(false);
        if (future != null) {
            future.cancel(false);
            future = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    TelemetrySnapshot snapshot() {
        return telemetry.snapshot(threadName, intervalNs, coalesce, running);
    }

    private void postTick() {
        if (!running) return;
        long postedNs = System.nanoTime();
        telemetry.recordPosted();
        if (coalesce && !queued.compareAndSet(false, true)) {
            telemetry.recordSkipped();
            return;
        }
        SwingUtilities.invokeLater(() -> {
            long startedNs = System.nanoTime();
            if (coalesce) queued.set(false);
            if (running) {
                try {
                    callback.run();
                } finally {
                    telemetry.recordExecuted(startedNs - postedNs,
                            System.nanoTime() - startedNs);
                }
            }
        });
    }

    static final class TelemetrySnapshot {
        final String threadName;
        final long intervalNs;
        final boolean coalesce;
        final boolean running;
        final long postedTicks;
        final long executedTicks;
        final long skippedTicks;
        final long lastQueueDelayNs;
        final long maxQueueDelayNs;
        final long totalQueueDelayNs;
        final long lastCallbackNs;
        final long maxCallbackNs;
        final long totalCallbackNs;

        TelemetrySnapshot(String threadName, long intervalNs, boolean coalesce,
                          boolean running, long postedTicks, long executedTicks,
                          long skippedTicks, long lastQueueDelayNs,
                          long maxQueueDelayNs, long totalQueueDelayNs,
                          long lastCallbackNs, long maxCallbackNs,
                          long totalCallbackNs) {
            this.threadName = threadName;
            this.intervalNs = intervalNs;
            this.coalesce = coalesce;
            this.running = running;
            this.postedTicks = postedTicks;
            this.executedTicks = executedTicks;
            this.skippedTicks = skippedTicks;
            this.lastQueueDelayNs = lastQueueDelayNs;
            this.maxQueueDelayNs = maxQueueDelayNs;
            this.totalQueueDelayNs = totalQueueDelayNs;
            this.lastCallbackNs = lastCallbackNs;
            this.maxCallbackNs = maxCallbackNs;
            this.totalCallbackNs = totalCallbackNs;
        }

        long averageQueueDelayNs() {
            return executedTicks <= 0L ? 0L : totalQueueDelayNs / executedTicks;
        }

        long averageCallbackNs() {
            return executedTicks <= 0L ? 0L : totalCallbackNs / executedTicks;
        }
    }

    private static final class LoopTelemetry {
        private final AtomicLong postedTicks = new AtomicLong();
        private final AtomicLong executedTicks = new AtomicLong();
        private final AtomicLong skippedTicks = new AtomicLong();
        private final AtomicLong lastQueueDelayNs = new AtomicLong();
        private final AtomicLong maxQueueDelayNs = new AtomicLong();
        private final AtomicLong totalQueueDelayNs = new AtomicLong();
        private final AtomicLong lastCallbackNs = new AtomicLong();
        private final AtomicLong maxCallbackNs = new AtomicLong();
        private final AtomicLong totalCallbackNs = new AtomicLong();

        void recordPosted() {
            postedTicks.incrementAndGet();
        }

        void recordSkipped() {
            skippedTicks.incrementAndGet();
        }

        void recordExecuted(long queueDelayNs, long callbackNs) {
            long safeQueueDelayNs = Math.max(0L, queueDelayNs);
            long safeCallbackNs = Math.max(0L, callbackNs);
            executedTicks.incrementAndGet();
            lastQueueDelayNs.set(safeQueueDelayNs);
            lastCallbackNs.set(safeCallbackNs);
            totalQueueDelayNs.addAndGet(safeQueueDelayNs);
            totalCallbackNs.addAndGet(safeCallbackNs);
            updateMax(maxQueueDelayNs, safeQueueDelayNs);
            updateMax(maxCallbackNs, safeCallbackNs);
        }

        TelemetrySnapshot snapshot(String threadName, long intervalNs,
                                   boolean coalesce, boolean running) {
            return new TelemetrySnapshot(
                    threadName,
                    intervalNs,
                    coalesce,
                    running,
                    postedTicks.get(),
                    executedTicks.get(),
                    skippedTicks.get(),
                    lastQueueDelayNs.get(),
                    maxQueueDelayNs.get(),
                    totalQueueDelayNs.get(),
                    lastCallbackNs.get(),
                    maxCallbackNs.get(),
                    totalCallbackNs.get());
        }

        private static void updateMax(AtomicLong target, long value) {
            long prev = target.get();
            while (value > prev && !target.compareAndSet(prev, value)) {
                prev = target.get();
            }
        }
    }
}
