package com.tetris.controller;

/**
 * Shared timing contract for gameplay input and rendering.
 *
 * <p>The game renders at a fixed 60 FPS in every launch mode. Frame-based
 * input settings convert wall-clock milliseconds against this same contract
 * so DAS/ARR behavior does not drift when the render loop changes.
 */
public final class FrameRate {

    public static final int RENDER_FPS = 60;
    public static final int PHYSICS_HZ = 120;

    public static final long RENDER_INTERVAL_NS = 1_000_000_000L / RENDER_FPS;
    public static final long PHYSICS_INTERVAL_NS = 1_000_000_000L / PHYSICS_HZ;

    public static final int RENDER_INTERVAL_MS_ROUNDED =
            (int) Math.round(1000.0 / RENDER_FPS);
    public static final int PHYSICS_INTERVAL_MS_ROUNDED =
            (int) Math.round(1000.0 / PHYSICS_HZ);

    public static int framesForMillisCeil(int millis) {
        if (millis <= 0) return 1;
        return Math.max(1, (int) Math.ceil(millis * (RENDER_FPS / 1000.0)));
    }

    public static int framesForMillisRounded(int millis) {
        if (millis <= 0) return 1;
        return Math.max(1, (int) Math.round(millis * (RENDER_FPS / 1000.0)));
    }

    private FrameRate() {}
}
