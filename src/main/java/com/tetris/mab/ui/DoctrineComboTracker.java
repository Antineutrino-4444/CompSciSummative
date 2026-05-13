package com.tetris.mab.ui;

import com.tetris.mab.upgrade.draft.MabActiveDoctrineType;

/**
 * Tracks the rotation combo that fires an active doctrine.
 *
 * MANUAL OVERRIDE : CW  CCW CW  CCW [Hard Drop]
 * EMP             : CCW CW  CCW CW  [Hard Drop]
 *
 * Any wrong rotation key restarts the sequence from that key.
 * A hard drop before step 4 resets without firing.
 * No keypress for {@code TIMEOUT_MS} resets silently.
 */
final class DoctrineComboTracker {

    private static final long TIMEOUT_MS = 1500;

    private enum Path { NONE, MO, EMP }

    private Path path = Path.NONE;
    private int step = 0;   // 0 = idle, 1-3 = progress, 4 = awaiting hard drop
    private long lastMs = 0;

    void onRotateCW() {
        checkTimeout();
        lastMs = System.currentTimeMillis();
        switch (step) {
            case 0 -> { path = Path.MO;  step = 1; }
            case 1 -> { if (path == Path.EMP) step = 2; else restartCW(); }
            case 2 -> { if (path == Path.MO)  step = 3; else restartCW(); }
            case 3 -> { if (path == Path.EMP) step = 4; else restartCW(); }
            default -> restartCW();
        }
    }

    void onRotateCCW() {
        checkTimeout();
        lastMs = System.currentTimeMillis();
        switch (step) {
            case 0 -> { path = Path.EMP; step = 1; }
            case 1 -> { if (path == Path.MO)  step = 2; else restartCCW(); }
            case 2 -> { if (path == Path.EMP) step = 3; else restartCCW(); }
            case 3 -> { if (path == Path.MO)  step = 4; else restartCCW(); }
            default -> restartCCW();
        }
    }

    /** Returns the doctrine to fire, or {@code null} if the combo was not complete. */
    MabActiveDoctrineType onHardDrop() {
        checkTimeout();
        if (step == 4) {
            MabActiveDoctrineType result = (path == Path.MO)
                    ? MabActiveDoctrineType.MANUAL_OVERRIDE
                    : MabActiveDoctrineType.EMP;
            reset();
            return result;
        }
        reset();
        return null;
    }

    private void checkTimeout() {
        if (step > 0 && System.currentTimeMillis() - lastMs > TIMEOUT_MS) reset();
    }

    private void restartCW()  { reset(); path = Path.MO;  step = 1; lastMs = System.currentTimeMillis(); }
    private void restartCCW() { reset(); path = Path.EMP; step = 1; lastMs = System.currentTimeMillis(); }
    private void reset()      { path = Path.NONE; step = 0; }
}
