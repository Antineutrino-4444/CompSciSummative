package com.tetris.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.io.ByteArrayInputStream;
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
    private volatile float trackGain;
    private volatile float fadeLevel;

    static MusicTrackHandle open(Path path, float pan, float masterVolume,
                                 Runnable onNaturalEnd) throws Exception {
        return open(path, pan, masterVolume, 1f, onNaturalEnd);
    }

    static MusicTrackHandle open(Path path, float pan, float masterVolume,
                                 float trackGain,
                                 Runnable onNaturalEnd) throws Exception {
        Clip clip = AudioSystem.getClip();
        boolean hardPan = isHardPan(pan);
        try (AudioInputStream source = AudioSystem.getAudioInputStream(path.toFile())) {
            try (AudioInputStream stream = streamForPan(source, pan)) {
                clip.open(stream);
            }
        }
        return new MusicTrackHandle(path, clip, hardPan ? 0f : pan,
                masterVolume, trackGain, onNaturalEnd);
    }

    private MusicTrackHandle(Path path, Clip clip, float pan, float masterVolume,
                             float trackGain, Runnable onNaturalEnd) {
        this.path = path;
        this.clip = clip;
        this.onNaturalEnd = onNaturalEnd == null ? () -> {} : onNaturalEnd;
        this.masterVolume = clamp01(masterVolume);
        this.trackGain = clampGain(trackGain);
        this.fadeLevel = 0f;
        this.gainControl = control(clip, FloatControl.Type.MASTER_GAIN);
        FloatControl panLike = control(clip, FloatControl.Type.PAN);
        if (panLike == null) panLike = control(clip, FloatControl.Type.BALANCE);
        this.panControl = panLike;
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
        float volume = clamp01(masterVolume * trackGain * fadeLevel);
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

    /**
     * Java Sound's PAN/BALANCE controls are mixer-dependent and can still
     * leak hard-panned clips into both speakers. For result music we need
     * true isolation, so hard-panned tracks are converted to stereo PCM with
     * the opposite channel written as digital silence before opening the Clip.
     */
    private static AudioInputStream streamForPan(AudioInputStream source, float pan)
            throws Exception {
        if (!isHardPan(pan)) return source;

        try (AudioInputStream pcm = asPcm16(source)) {
            AudioFormat fmt = pcm.getFormat();
            int channels = fmt.getChannels();
            int frameSize = fmt.getFrameSize();
            if (channels <= 0 || frameSize < channels * 2) {
                throw new IllegalArgumentException("Unsupported PCM frame layout: " + fmt);
            }

            byte[] in = pcm.readAllBytes();
            int frames = in.length / frameSize;
            byte[] out = new byte[frames * 4]; // 16-bit stereo
            boolean bigEndian = fmt.isBigEndian();
            boolean leftOnly = pan < 0f;

            for (int frame = 0; frame < frames; frame++) {
                int frameOffset = frame * frameSize;
                int sum = 0;
                for (int ch = 0; ch < channels; ch++) {
                    sum += readSample16(in, frameOffset + ch * 2, bigEndian);
                }
                short mixed = (short) (sum / channels);
                int outOffset = frame * 4;
                if (leftOnly) {
                    writeSample16(out, outOffset, mixed);
                    writeSample16(out, outOffset + 2, (short) 0);
                } else {
                    writeSample16(out, outOffset, (short) 0);
                    writeSample16(out, outOffset + 2, mixed);
                }
            }

            AudioFormat outFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    fmt.getSampleRate(),
                    16,
                    2,
                    4,
                    fmt.getSampleRate(),
                    false);
            return new AudioInputStream(new ByteArrayInputStream(out),
                    outFormat, frames);
        }
    }

    private static boolean isHardPan(float pan) {
        return Math.abs(pan) >= 0.999f;
    }

    private static AudioInputStream asPcm16(AudioInputStream source) {
        AudioFormat fmt = source.getFormat();
        boolean alreadyPcm16 = fmt.getEncoding() == AudioFormat.Encoding.PCM_SIGNED
                && fmt.getSampleSizeInBits() == 16
                && fmt.getChannels() > 0
                && fmt.getFrameSize() >= fmt.getChannels() * 2;
        if (alreadyPcm16) return source;
        int channels = Math.max(1, fmt.getChannels());
        AudioFormat target = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                fmt.getSampleRate(),
                16,
                channels,
                channels * 2,
                fmt.getSampleRate(),
                false);
        return AudioSystem.getAudioInputStream(target, source);
    }

    private static short readSample16(byte[] data, int offset, boolean bigEndian) {
        int b0 = data[offset] & 0xff;
        int b1 = data[offset + 1] & 0xff;
        int value = bigEndian ? (b0 << 8) | b1 : (b1 << 8) | b0;
        return (short) value;
    }

    private static void writeSample16(byte[] data, int offset, short value) {
        data[offset] = (byte) (value & 0xff);
        data[offset + 1] = (byte) ((value >>> 8) & 0xff);
    }

    private static float clamp01(float v) {
        if (Float.isNaN(v)) return 1f;
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private static float clampGain(float v) {
        if (Float.isNaN(v)) return 1f;
        if (v < 0f) return 0f;
        if (v > 1.5f) return 1.5f;
        return v;
    }
}
