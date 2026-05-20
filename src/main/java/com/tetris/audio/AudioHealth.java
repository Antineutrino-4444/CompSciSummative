package com.tetris.audio;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Process-wide audio "circuit breaker".
 *
 * <p>Both the {@link SoundEffectManager} and the {@link MusicDirector} report
 * real playback failures (audio line unavailable, decode/IO errors) here.
 * After {@link #MAX_ATTEMPTS} failures the breaker <em>trips</em>: the audio
 * subsystems stop attempting playback and a one-shot UI callback fires so the
 * game can show a closeable popup. The breaker stays tripped until
 * {@link #reset()}, which the dev-console {@code reloadaudio} command calls.
 *
 * <p>Only genuine playback failures count — a missing asset file is a content
 * problem, not an audio-system failure, and does not advance the counter.
 */
public final class AudioHealth {

    /** Number of failed playback attempts before audio is disabled. */
    public static final int MAX_ATTEMPTS = 5;

    private static final AudioHealth SHARED = new AudioHealth();

    public static AudioHealth shared() { return SHARED; }

    private final AtomicInteger failures = new AtomicInteger();
    private volatile boolean tripped;
    private volatile String lastReason = "";
    private volatile Consumer<String> onTrip;

    private AudioHealth() {}

    /** True once the failure limit has been hit; audio playback is suppressed. */
    public boolean isDisabled() { return tripped; }

    public int getFailureCount() { return failures.get(); }

    public int getMaxAttempts() { return MAX_ATTEMPTS; }

    public String getLastReason() { return lastReason; }

    /**
     * Registers the callback fired exactly once when the breaker trips. The
     * argument is a user-facing message. The callback may be invoked from a
     * non-EDT audio thread, so implementations must marshal to the EDT.
     */
    public void setOnTrip(Consumer<String> callback) { this.onTrip = callback; }

    /**
     * Records one genuine playback failure. When the count reaches
     * {@link #MAX_ATTEMPTS} the breaker trips and the {@link #setOnTrip}
     * callback fires once. No-op once already tripped.
     */
    public void recordFailure(String reason) {
        if (tripped) return;
        lastReason = reason == null ? "" : reason;
        int n = failures.incrementAndGet();
        if (n < MAX_ATTEMPTS) return;
        synchronized (this) {
            if (tripped) return;
            tripped = true;
        }
        System.err.println("[audio] Disabled after " + n + " failed attempt(s). Last: "
                + lastReason + "  — type 'reloadaudio' in the dev console (~) to retry.");
        Consumer<String> cb = onTrip;
        if (cb != null) {
            try { cb.accept(buildMessage()); } catch (RuntimeException ignored) {}
        }
    }

    /**
     * Re-enables audio and clears the failure count. Backs the dev-console
     * {@code reloadaudio} command.
     */
    public void reset() {
        failures.set(0);
        lastReason = "";
        tripped = false;
        System.out.println("[audio] Re-enabled (reloadaudio).");
    }

    private String buildMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("Audio playback failed ").append(MAX_ATTEMPTS)
          .append(" times, so audio has been turned off.\n");
        if (!lastReason.isEmpty()) {
            sb.append("Last error: ").append(lastReason).append('\n');
        }
        sb.append("Open the dev console (press ` or type \"debug\") and run  reloadaudio  to try again.");
        return sb.toString();
    }
}
