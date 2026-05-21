package com.tetris.view;

import javax.swing.JComponent;
import javax.swing.RepaintManager;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Low-overhead Swing paint telemetry used by the F3 overlay.
 *
 * <p>The game loop can only measure how long it takes to request a repaint.
 * Swing performs the actual dirty-region painting later on the EDT, so this
 * RepaintManager wrapper records the missing half of the frame-time story.
 */
public final class SwingPaintDiagnostics {

    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static volatile TimedRepaintManager manager;

    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) return;
        RepaintManager current = RepaintManager.currentManager((JComponent) null);
        if (current instanceof TimedRepaintManager timed) {
            manager = timed;
            return;
        }
        TimedRepaintManager timed = new TimedRepaintManager();
        manager = timed;
        RepaintManager.setCurrentManager(timed);
    }

    public static Snapshot snapshot() {
        TimedRepaintManager m = manager;
        return m == null ? Snapshot.EMPTY : m.snapshot();
    }

    public static void reset() {
        TimedRepaintManager m = manager;
        if (m != null) m.reset();
    }

    public static void recordBackdropPaint(long elapsedNs) {
        TimedRepaintManager m = manager;
        if (m != null) m.recordBackdropPaint(Math.max(0L, elapsedNs));
    }

    public static final class Snapshot {
        static final Snapshot EMPTY = new Snapshot(false, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L);

        public final boolean installed;
        public final long paintPasses;
        public final long dirtyRequests;
        public final long lastPaintNs;
        public final long maxPaintNs;
        public final long totalPaintNs;
        public final long backdropPasses;
        public final long lastBackdropPaintNs;
        public final long maxBackdropPaintNs;
        public final long totalBackdropPaintNs;

        Snapshot(boolean installed, long paintPasses, long dirtyRequests,
                 long lastPaintNs, long maxPaintNs, long totalPaintNs,
                 long backdropPasses, long lastBackdropPaintNs,
                 long maxBackdropPaintNs, long totalBackdropPaintNs) {
            this.installed = installed;
            this.paintPasses = paintPasses;
            this.dirtyRequests = dirtyRequests;
            this.lastPaintNs = lastPaintNs;
            this.maxPaintNs = maxPaintNs;
            this.totalPaintNs = totalPaintNs;
            this.backdropPasses = backdropPasses;
            this.lastBackdropPaintNs = lastBackdropPaintNs;
            this.maxBackdropPaintNs = maxBackdropPaintNs;
            this.totalBackdropPaintNs = totalBackdropPaintNs;
        }

        public long averagePaintNs() {
            return paintPasses <= 0L ? 0L : totalPaintNs / paintPasses;
        }

        public long averageBackdropPaintNs() {
            return backdropPasses <= 0L ? 0L : totalBackdropPaintNs / backdropPasses;
        }
    }

    private static final class TimedRepaintManager extends RepaintManager {
        private final AtomicLong paintPasses = new AtomicLong();
        private final AtomicLong dirtyRequests = new AtomicLong();
        private final AtomicLong lastPaintNs = new AtomicLong();
        private final AtomicLong maxPaintNs = new AtomicLong();
        private final AtomicLong totalPaintNs = new AtomicLong();
        private final AtomicLong backdropPasses = new AtomicLong();
        private final AtomicLong lastBackdropPaintNs = new AtomicLong();
        private final AtomicLong maxBackdropPaintNs = new AtomicLong();
        private final AtomicLong totalBackdropPaintNs = new AtomicLong();

        @Override
        public void addDirtyRegion(JComponent c, int x, int y, int w, int h) {
            if (w > 0 && h > 0) dirtyRequests.incrementAndGet();
            super.addDirtyRegion(c, x, y, w, h);
        }

        @Override
        public void paintDirtyRegions() {
            long startNs = System.nanoTime();
            try {
                super.paintDirtyRegions();
            } finally {
                long elapsedNs = Math.max(0L, System.nanoTime() - startNs);
                paintPasses.incrementAndGet();
                lastPaintNs.set(elapsedNs);
                totalPaintNs.addAndGet(elapsedNs);
                updateMax(maxPaintNs, elapsedNs);
            }
        }

        Snapshot snapshot() {
            return new Snapshot(
                    true,
                    paintPasses.get(),
                    dirtyRequests.get(),
                    lastPaintNs.get(),
                    maxPaintNs.get(),
                    totalPaintNs.get(),
                    backdropPasses.get(),
                    lastBackdropPaintNs.get(),
                    maxBackdropPaintNs.get(),
                    totalBackdropPaintNs.get());
        }

        void reset() {
            paintPasses.set(0L);
            dirtyRequests.set(0L);
            lastPaintNs.set(0L);
            maxPaintNs.set(0L);
            totalPaintNs.set(0L);
            backdropPasses.set(0L);
            lastBackdropPaintNs.set(0L);
            maxBackdropPaintNs.set(0L);
            totalBackdropPaintNs.set(0L);
        }

        void recordBackdropPaint(long elapsedNs) {
            backdropPasses.incrementAndGet();
            lastBackdropPaintNs.set(elapsedNs);
            totalBackdropPaintNs.addAndGet(elapsedNs);
            updateMax(maxBackdropPaintNs, elapsedNs);
        }

        private static void updateMax(AtomicLong target, long value) {
            long prev = target.get();
            while (value > prev && !target.compareAndSet(prev, value)) {
                prev = target.get();
            }
        }
    }

    private SwingPaintDiagnostics() {}
}
