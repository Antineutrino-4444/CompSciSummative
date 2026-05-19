package com.tetris.controller;

import javax.swing.SwingUtilities;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private void postTick() {
        if (!running) return;
        if (coalesce && !queued.compareAndSet(false, true)) return;
        SwingUtilities.invokeLater(() -> {
            if (coalesce) queued.set(false);
            if (running) callback.run();
        });
    }
}
