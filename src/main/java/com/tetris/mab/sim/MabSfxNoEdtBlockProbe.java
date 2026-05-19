package com.tetris.mab.sim;

import com.tetris.audio.SfxAssetResolver;
import com.tetris.audio.SoundEffect;
import com.tetris.audio.SoundEffectManager;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Verifies that {@link SoundEffectManager#play} never blocks the Swing EDT.
 * Disk IO and clip start/close happen on a daemon executor, so a burst of
 * play() calls dispatched on the EDT must finish in single-digit
 * milliseconds even if the manager is in audio-enabled mode and physically
 * decoding clips on the worker thread.
 */
public final class MabSfxNoEdtBlockProbe {

    private static int failed;

    private MabSfxNoEdtBlockProbe() {}

    public static void main(String[] args) throws Exception {
        System.out.println("=== MAB SFX No EDT Block Probe ===");
        Path repoSfx = Paths.get("sfx").toAbsolutePath().normalize();

        // First pass: audio-disabled to validate the contract semantics
        // without touching the audio subsystem.
        SoundEffectManager disabled = new SoundEffectManager(
                new SfxAssetResolver(repoSfx), false);
        long t0 = System.nanoTime();
        for (int i = 0; i < 500; i++) disabled.play(SoundEffect.MOVE);
        long elapsedNoAudioMs = (System.nanoTime() - t0) / 1_000_000L;
        check("disabled.fastBurst", elapsedNoAudioMs < 50L);

        // Second pass: audio-enabled, exercising the executor handoff.
        SoundEffectManager enabled = new SoundEffectManager(
                new SfxAssetResolver(repoSfx), true);
        enabled.setMuted(true); // mute = no actual playback yet still records latency
        CountDownLatch done = new CountDownLatch(1);
        long[] edtElapsedMs = { -1L };
        SwingUtilities.invokeLater(() -> {
            long s = System.nanoTime();
            for (int i = 0; i < 500; i++) {
                enabled.play(SoundEffect.MOVE);
                enabled.play(SoundEffect.ROTATE);
                enabled.play(SoundEffect.HOLD);
            }
            edtElapsedMs[0] = (System.nanoTime() - s) / 1_000_000L;
            done.countDown();
        });
        boolean settled = done.await(2, TimeUnit.SECONDS);
        check("enabled.dispatchedOnEdt", settled);
        if (settled) {
            check("enabled.fastBurstOnEdt", edtElapsedMs[0] < 100L);
            System.out.println("  burst1500onEdt=" + edtElapsedMs[0] + "ms");
        }

        // Third pass: unmute and let one real play occur. It should still
        // not block the calling thread (we measure the play() return time).
        enabled.setMuted(false);
        long s2 = System.nanoTime();
        enabled.play(SoundEffect.HOLD);
        long blockingMs = (System.nanoTime() - s2) / 1_000_000L;
        check("enabled.realPlayNonBlocking", blockingMs < 30L);

        boolean ok = failed == 0;
        System.out.println("success=" + ok);
        if (!ok) System.exit(1);
    }

    private static void check(String name, boolean cond) {
        if (cond) {
            System.out.println("  pass " + name);
        } else {
            failed++;
            System.out.println("  FAIL " + name);
        }
    }
}
