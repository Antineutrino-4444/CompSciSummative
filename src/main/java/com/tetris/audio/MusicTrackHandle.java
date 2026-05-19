package com.tetris.audio;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.nio.file.Path;

/** Runtime wrapper around one Java Sound {@link Clip}. */
final class MusicTrackHandle {

    private static final int FADE_STEPS = 24;

    private final Path path;
    private final Clip clip;
    private final FloatControl gainControl;
    private final FloatControl panControl;
    private final Runnable onNaturalEnd;
    private final Object lock = new Object();
    private volatile boolean closing;
    private volatile boolean naturalEndReported;
    private volatile float masterVolume;
    private volatile float fadeLevel;

    static MusicTrackHandle open(Path path, float pan, float masterVolume,
                                 Runnable onNaturalEnd) throws Exception {
        AudioInputStream stream = AudioSystem.getAudioInputStream(path.toFile());
        Clip clip = AudioSystem.getClip();
        try (stream) {
            clip.open(stream);
        }
        return new MusicTrackHandle(path, clip, pan, masterVolume, onNaturalEnd);
    }

    private MusicTrackHandle(Path path, Clip clip, float pan, float masterVolume,
                             Runnable onNaturalEnd) {
        this.path = path;
        this.clip = clip;
        this.onNaturalEnd = onNaturalEnd == null ? () -> {} : onNaturalEnd;
        this.masterVolume = clamp01(masterVolume);
        this.fadeLevel = 0f;
        this.gainControl = control(clip, FloatControl.Type.MASTER_GAIN);
        this.panControl = control(clip, FloatControl.Type.PAN);
        if (panControl != null) {
            try { panControl.setValue(Math.max(-1f, Math.min(1f, pan))); }
            catch (IllegalArgumentException ignored) {}
        }
        applyGain();
        clip.addLineListener(event -> {
            if (event.getType() == LineEvent.Type.STOP
                    && !closing
                    && !naturalEndReported
                    && isAtNaturalEnd()) {
                naturalEndReported = true;
                this.onNaturalEnd.run();
            }
        });
    }

    Path getPath() { return path; }

    void start() {
        synchronized (lock) {
            if (!closing) clip.start();
        }
    }

    void setMasterVolume(float volume) {
        this.masterVolume = clamp01(volume);
        applyGain();
    }

    void fadeIn(int millis) {
        fadeTo(1f, millis, false);
    }

    void fadeOutAndClose(int millis) {
        closing = true;
        fadeTo(0f, millis, true);
    }

    void stopNow() {
        closing = true;
        synchronized (lock) {
            try { clip.stop(); } catch (RuntimeException ignored) {}
            try { clip.close(); } catch (RuntimeException ignored) {}
        }
    }

    private boolean isAtNaturalEnd() {
        try {
            long length = clip.getMicrosecondLength();
            long pos = clip.getMicrosecondPosition();
            return length > 0 && pos >= length - 25_000L;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private void fadeTo(float target, int millis, boolean closeAfter) {
        int duration = Math.max(0, millis);
        Thread t = new Thread(() -> {
            float start = fadeLevel;
            int steps = duration <= 0 ? 1 : FADE_STEPS;
            long sleep = steps <= 0 ? 0L : duration / steps;
            for (int i = 1; i <= steps; i++) {
                float f = start + (target - start) * (i / (float) steps);
                fadeLevel = clamp01(f);
                applyGain();
                if (sleep > 0L) {
                    try { Thread.sleep(sleep); }
                    catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            fadeLevel = clamp01(target);
            applyGain();
            if (closeAfter) stopNow();
        }, "mab-music-fade");
        t.setDaemon(true);
        t.start();
    }

    private void applyGain() {
        FloatControl gain = gainControl;
        if (gain == null) return;
        float volume = clamp01(masterVolume * fadeLevel);
        float db = volume <= 0.0001f
                ? gain.getMinimum()
                : (float) (20.0 * Math.log10(volume));
        db = Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db));
        synchronized (lock) {
            try { gain.setValue(db); }
            catch (IllegalArgumentException ignored) {}
        }
    }

    private static FloatControl control(Clip clip, FloatControl.Type type) {
        try {
            if (clip.isControlSupported(type)) {
                return (FloatControl) clip.getControl(type);
            }
        } catch (RuntimeException ignored) {}
        return null;
    }

    private static float clamp01(float v) {
        if (Float.isNaN(v)) return 1f;
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }
}
