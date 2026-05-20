package com.tetris.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central sound-effect manager. Independent of {@link MusicDirector} so the
 * music system can be tested, muted, or swapped without affecting SFX.
 *
 * <h2>Guarantees</h2>
 * <ul>
 *   <li>All disk IO and clip start/close happens on a dedicated daemon
 *       executor — the Swing EDT never blocks on {@link Clip#open}.</li>
 *   <li>Missing files emit a one-shot warning and the call returns silently;
 *       playback never throws back into the caller.</li>
 *   <li>Per-asset throttle windows prevent spam events (move/sidehit/
 *       softdrop/damage_alert/garbage windup) from drowning the mix.</li>
 *   <li>Recorded clip data is cached in memory once the file is read, so
 *       subsequent plays only open a fresh {@link Clip} instance (clips are
 *       not poolable for parallel playback because Java Sound clips disallow
 *       overlapping {@link Clip#start} on a single instance).</li>
 *   <li>Master SFX volume is controlled separately from music volume.</li>
 * </ul>
 */
public final class SoundEffectManager {

    private static final float OUTPUT_GAIN_BOOST = 1.15f;

    /** Minimum gap (ms) between consecutive plays of the same throttled effect. */
    private static final Map<SoundEffect, Long> DEFAULT_THROTTLE_MS = new EnumMap<>(SoundEffect.class);
    static {
        DEFAULT_THROTTLE_MS.put(SoundEffect.MOVE, 18L);
        DEFAULT_THROTTLE_MS.put(SoundEffect.ROTATE, 24L);
        DEFAULT_THROTTLE_MS.put(SoundEffect.SOFTDROP, 55L);
        DEFAULT_THROTTLE_MS.put(SoundEffect.SIDEHIT, 90L);
        DEFAULT_THROTTLE_MS.put(SoundEffect.DAMAGE_ALERT, 950L);
        DEFAULT_THROTTLE_MS.put(SoundEffect.GARBAGE_WINDUP_1, 280L);
        DEFAULT_THROTTLE_MS.put(SoundEffect.GARBAGE_WINDUP_2, 280L);
        DEFAULT_THROTTLE_MS.put(SoundEffect.GARBAGE_WINDUP_3, 280L);
        DEFAULT_THROTTLE_MS.put(SoundEffect.GARBAGE_WINDUP_4, 280L);
        DEFAULT_THROTTLE_MS.put(SoundEffect.MENU_HOVER, 30L);
        DEFAULT_THROTTLE_MS.put(SoundEffect.MENU_TAP, 35L);
    }

    /**
     * Critical assets are decoded eagerly at startup so the first in-game
     * play never blocks on disk IO. Everything else is lazy-loaded on first use.
     */
    private static final List<SoundEffect> PRELOAD = List.of(
            SoundEffect.MOVE, SoundEffect.ROTATE, SoundEffect.SIDEHIT,
            SoundEffect.SOFTDROP, SoundEffect.HARDDROP, SoundEffect.HOLD,
            SoundEffect.CLEAR_LINE, SoundEffect.CLEAR_QUAD, SoundEffect.CLEAR_SPIN,
            SoundEffect.CLEAR_BTB, SoundEffect.ALL_CLEAR, SoundEffect.COMBO_BREAK,
            SoundEffect.BTB_BREAK, SoundEffect.TOPOUT,
            SoundEffect.MENU_HOVER, SoundEffect.MENU_TAP, SoundEffect.MENU_CLICK,
            SoundEffect.MENU_CONFIRM, SoundEffect.MENU_BACK,
            SoundEffect.COUNTDOWN3, SoundEffect.COUNTDOWN2, SoundEffect.COUNTDOWN1,
            SoundEffect.GO,
            SoundEffect.DAMAGE_ALERT, SoundEffect.GARBAGE_RISE
    );

    private static final SoundEffectManager SHARED = new SoundEffectManager();

    private final SfxAssetResolver resolver;
    private final ExecutorService executor;
    private final boolean audioEnabled;
    private final Map<SoundEffect, byte[]> cachedClipBytes = new ConcurrentHashMap<>();
    private final Map<SoundEffect, AudioFormat> cachedFormats = new ConcurrentHashMap<>();
    private final Map<SoundEffect, Long> lastPlayMs = new ConcurrentHashMap<>();
    private final Map<SoundEffect, Long> throttleMs;
    private final Map<String, Long> warnedMissing = new ConcurrentHashMap<>();
    private final Deque<Clip> activeClips = new ArrayDeque<>();
    private final Random random = new Random();
    private final AtomicLong playCount = new AtomicLong();
    private final AtomicLong skippedCount = new AtomicLong();
    private final AtomicLong droppedThrottled = new AtomicLong();

    private volatile float masterVolume = 0.85f;
    private volatile boolean muted;

    public static SoundEffectManager shared() { return SHARED; }

    /** Standard runtime instance — uses {@link SfxAssetResolver#defaultRoots()}. */
    private SoundEffectManager() {
        this(new SfxAssetResolver(), true);
    }

    /** Probe-friendly constructor. Audio playback may be disabled for headless tests. */
    public SoundEffectManager(SfxAssetResolver resolver, boolean audioEnabled) {
        this.resolver = resolver == null ? new SfxAssetResolver() : resolver;
        this.audioEnabled = audioEnabled;
        this.throttleMs = new EnumMap<>(DEFAULT_THROTTLE_MS);
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "tetris-sfx");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        };
        this.executor = Executors.newSingleThreadExecutor(factory);
        if (audioEnabled) {
            this.executor.execute(this::preloadCritical);
        }
    }

    // ─────────────────────── public API ────────────────────────

    /** Plays the given effect respecting throttle, mute and volume. */
    public void play(SoundEffect effect) {
        play(effect, 1f);
    }

    /** Plays the given effect at a gain multiplier (typically 0..1.25). */
    public void play(SoundEffect effect, float gainMultiplier) {
        if (effect == null) return;
        // Throttle takes priority over mute / disabled so the manager has
        // identical timing semantics in headless probe runs and real audio.
        long now = System.currentTimeMillis();
        long throttle = throttleMs.getOrDefault(effect, 0L);
        if (throttle > 0L) {
            Long last = lastPlayMs.get(effect);
            if (last != null && now - last < throttle) {
                droppedThrottled.incrementAndGet();
                return;
            }
        }
        lastPlayMs.put(effect, now);
        if (muted || masterVolume <= 0.001f || !audioEnabled
                || AudioHealth.shared().isDisabled()) {
            skippedCount.incrementAndGet();
            return;
        }
        float gain = clamp(gainMultiplier, 0f, 1.5f);
        executor.execute(() -> playInternal(effect, gain));
    }

    /** Picks a random effect from a group and plays it. */
    public void playRandom(SoundEffect... group) {
        if (group == null || group.length == 0) return;
        SoundEffect choice = group[random.nextInt(group.length)];
        play(choice);
    }

    /**
     * Side-aware play stub. The current build mixes both players to the same
     * stereo bus; the side argument is preserved so a future panned mix can
     * be added without touching call sites.
     */
    public void playForSide(SoundEffect effect, int side) {
        play(effect);
    }

    /** True iff the given effect was last played within {@code windowMs}. */
    public boolean wasRecentlyPlayed(SoundEffect effect, long windowMs) {
        Long last = lastPlayMs.get(effect);
        if (last == null) return false;
        return System.currentTimeMillis() - last <= windowMs;
    }

    public void setMasterSfxVolume(float volume) {
        masterVolume = clamp(volume, 0f, 1f);
    }

    public float getMasterSfxVolume() { return masterVolume; }

    public void setMuted(boolean muted) { this.muted = muted; }

    public boolean isMuted() { return muted; }

    public boolean isAudioEnabled() { return audioEnabled; }

    public long getPlayCount() { return playCount.get(); }

    public long getSkippedCount() { return skippedCount.get(); }

    public long getDroppedThrottledCount() { return droppedThrottled.get(); }

    public SfxAssetResolver getResolver() { return resolver; }

    /** Overrides the default throttle for a given effect. Use 0 to clear. */
    public void setThrottleMs(SoundEffect effect, long millis) {
        if (effect == null) return;
        if (millis <= 0) throttleMs.remove(effect);
        else throttleMs.put(effect, millis);
    }

    public long getThrottleMs(SoundEffect effect) {
        return throttleMs.getOrDefault(effect, 0L);
    }

    /**
     * Stops every currently playing clip and clears active handles. Safe to
     * call from any thread — actual {@link Clip#stop()} runs on the audio
     * executor.
     */
    public void stopAll() {
        executor.execute(() -> {
            synchronized (activeClips) {
                while (!activeClips.isEmpty()) {
                    Clip c = activeClips.poll();
                    try { c.stop(); } catch (RuntimeException ignored) {}
                    try { c.close(); } catch (RuntimeException ignored) {}
                }
            }
        });
    }

    // ─────────────────────── internals ────────────────────────

    private void preloadCritical() {
        for (SoundEffect effect : PRELOAD) {
            loadBytes(effect);
        }
    }

    private void playInternal(SoundEffect effect, float gain) {
        if (AudioHealth.shared().isDisabled()) {
            skippedCount.incrementAndGet();
            return;
        }
        try {
            byte[] data = loadBytes(effect);
            if (data == null) return;
            AudioFormat format = cachedFormats.get(effect);
            if (format == null) return;
            try (AudioInputStream stream = new AudioInputStream(
                    new ByteArrayInputStream(data), format,
                    data.length / Math.max(1, format.getFrameSize()))) {
                Clip clip = AudioSystem.getClip();
                clip.open(stream);
                applyGain(clip, gain);
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        synchronized (activeClips) { activeClips.remove(clip); }
                        try { clip.close(); } catch (RuntimeException ignored) {}
                    }
                });
                synchronized (activeClips) {
                    pruneFinishedClips();
                    activeClips.add(clip);
                }
                clip.start();
                playCount.incrementAndGet();
            }
        } catch (LineUnavailableException | IOException ex) {
            warnOnce("sfx-play-" + effect.assetName(),
                    "[SFX] Unable to play " + effect.assetName() + ": " + ex.getMessage());
            AudioHealth.shared().recordFailure("SFX " + effect.assetName()
                    + ": " + ex.getMessage());
        } catch (RuntimeException ex) {
            warnOnce("sfx-play-" + effect.assetName(),
                    "[SFX] Unexpected playback failure for " + effect.assetName()
                            + ": " + ex.getMessage());
            AudioHealth.shared().recordFailure("SFX " + effect.assetName()
                    + ": " + ex.getMessage());
        }
    }

    private byte[] loadBytes(SoundEffect effect) {
        byte[] cached = cachedClipBytes.get(effect);
        if (cached != null) return cached;
        Path path = resolver.resolve(effect);
        if (path == null) {
            warnOnce("sfx-missing-" + effect.assetName(),
                    "[SFX] Missing asset for id '" + effect.assetName() + "'");
            return null;
        }
        if (!Files.isRegularFile(path)) {
            warnOnce("sfx-missing-" + effect.assetName(),
                    "[SFX] Asset path is not a file: " + path);
            return null;
        }
        try (AudioInputStream src = AudioSystem.getAudioInputStream(path.toFile())) {
            AudioFormat format = src.getFormat();
            cachedFormats.put(effect, format);
            byte[] bytes = readAll(src);
            cachedClipBytes.put(effect, bytes);
            return bytes;
        } catch (UnsupportedAudioFileException | IOException ex) {
            warnOnce("sfx-load-" + effect.assetName(),
                    "[SFX] Unable to read " + path + ": " + ex.getMessage());
            return null;
        }
    }

    private void pruneFinishedClips() {
        List<Clip> remove = new ArrayList<>();
        for (Clip c : activeClips) {
            if (!c.isOpen() || !c.isRunning()) remove.add(c);
        }
        for (Clip c : remove) {
            activeClips.remove(c);
            try { c.close(); } catch (RuntimeException ignored) {}
        }
    }

    private void applyGain(Clip clip, float gainMultiplier) {
        try {
            if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
            FloatControl ctrl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float volume = clamp(masterVolume * gainMultiplier * OUTPUT_GAIN_BOOST, 0f, 1f);
            float db;
            if (volume <= 0.0001f) {
                db = ctrl.getMinimum();
            } else {
                db = (float) (20.0 * Math.log10(volume));
            }
            db = Math.max(ctrl.getMinimum(), Math.min(ctrl.getMaximum(), db));
            ctrl.setValue(db);
        } catch (RuntimeException ignored) {
            // Some lines silently fail to apply gain on Windows.
        }
    }

    private static byte[] readAll(InputStream stream) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(4096);
        byte[] buf = new byte[4096];
        int n;
        while ((n = stream.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private void warnOnce(String key, String message) {
        Long stamp = warnedMissing.get(key);
        long now = System.currentTimeMillis();
        if (stamp != null && now - stamp < 60_000L) return;
        warnedMissing.put(key, now);
        System.err.println(message);
    }

    private static float clamp(float v, float lo, float hi) {
        if (Float.isNaN(v)) return lo;
        return Math.max(lo, Math.min(hi, v));
    }

    /**
     * Probe-facing snapshot of last-play timestamps. Returned map is a copy.
     */
    public Map<SoundEffect, Long> lastPlayedSnapshot() {
        return new HashMap<>(lastPlayMs);
    }

    /**
     * Forces a synchronous load+decode of the named asset (probe helper).
     * Returns true when the asset is present and readable.
     */
    public boolean ensureLoaded(SoundEffect effect) {
        if (effect == null) return false;
        if (cachedClipBytes.containsKey(effect)) return true;
        return loadBytes(effect) != null;
    }

    /**
     * Returns the resolved disk path for an effect ID, or {@code null} when
     * the asset is missing or retired.
     */
    public Path resolvePath(SoundEffect effect) {
        return resolver.resolve(effect);
    }

    @Override public String toString() {
        return "SoundEffectManager{vol=" + masterVolume
                + ", muted=" + muted
                + ", plays=" + playCount.get()
                + ", throttled=" + droppedThrottled.get()
                + ", skipped=" + skippedCount.get()
                + ", cached=" + cachedClipBytes.size()
                + "}";
    }

    /** Quick identity check used by probes. */
    public static boolean isSharedInstance(SoundEffectManager other) {
        return Objects.equals(other, SHARED);
    }
}
