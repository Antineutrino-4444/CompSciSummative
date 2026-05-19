package com.tetris.audio;

import com.tetris.mab.ParticipantId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Priority-based music director for Mutually Assured Blocks. Public
 * methods are cheap and safe to call from Swing; clip loading, fading,
 * and closing happen off the EDT.
 */
public final class MusicDirector {

    private static final int DEFAULT_FADE_MS = 900;
    private static final int RESULT_FADE_MS = 350;
    private static final Path DEFAULT_ROOT = Path.of("music");
    private static final MusicDirector SHARED = new MusicDirector(DEFAULT_ROOT, true);

    private final Path musicRoot;
    private final boolean audioEnabled;
    private final ExecutorService audioExecutor;
    private final Map<PlaylistKey, MusicPlaylist> playlists = new EnumMap<>(PlaylistKey.class);
    private final List<MusicTrackHandle> activeHandles = new ArrayList<>();
    private final List<String> transitionLog = new ArrayList<>();

    private MusicRequest activeRequest;
    private MusicState defconState;
    private boolean menuActive;
    private boolean prematchLabActive;
    private Boolean midgameBuilderRedesignOnly;
    private boolean launchActive;
    private boolean highStackDanger;
    private ResultMusicMode resultMode;
    private float masterVolume = 0.85f;
    private long generation;
    private int naturalEndCount;

    public static MusicDirector shared() { return SHARED; }

    public static MusicDirector createForProbe(Path musicRoot, boolean audioEnabled) {
        return new MusicDirector(musicRoot, audioEnabled);
    }

    public MusicDirector(Path musicRoot, boolean audioEnabled) {
        this.musicRoot = musicRoot == null ? DEFAULT_ROOT : musicRoot;
        this.audioEnabled = audioEnabled;
        this.audioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mab-music-director");
            t.setDaemon(true);
            return t;
        });
        loadPlaylists();
    }

    public synchronized void playMenu() {
        resultMode = null;
        midgameBuilderRedesignOnly = null;
        prematchLabActive = false;
        launchActive = false;
        highStackDanger = false;
        defconState = null;
        menuActive = true;
        evaluateLocked();
    }

    public synchronized void playPrematchLab() {
        resultMode = null;
        prematchLabActive = true;
        midgameBuilderRedesignOnly = null;
        evaluateLocked();
    }

    public synchronized void playMidgameBuilder(boolean redesignUpgradeOwned) {
        resultMode = null;
        prematchLabActive = false;
        midgameBuilderRedesignOnly = redesignUpgradeOwned;
        evaluateLocked();
    }

    public synchronized void playGameplayDefcon(int defcon) {
        menuActive = false;
        prematchLabActive = false;
        midgameBuilderRedesignOnly = null;
        defconState = stateForDefcon(defcon);
        evaluateLocked();
    }

    public synchronized void setHighStackDanger(boolean active) {
        highStackDanger = active;
        evaluateLocked();
    }

    public synchronized void playLaunchUntilImpact() {
        launchActive = true;
        evaluateLocked();
    }

    public synchronized void onImpactResolved() {
        launchActive = false;
        evaluateLocked();
    }

    public synchronized void playResultPvE(boolean playerWon) {
        resultMode = playerWon ? ResultMusicMode.PVE_WIN : ResultMusicMode.PVE_LOSE;
        prematchLabActive = false;
        midgameBuilderRedesignOnly = null;
        evaluateLocked();
    }

    public synchronized void playResultPvP(ParticipantId winner) {
        resultMode = winner == ParticipantId.PLAYER_A
                ? ResultMusicMode.PVP_P1_WIN
                : ResultMusicMode.PVP_P2_WIN;
        prematchLabActive = false;
        midgameBuilderRedesignOnly = null;
        evaluateLocked();
    }

    public synchronized void stopAll() {
        resultMode = null;
        prematchLabActive = false;
        midgameBuilderRedesignOnly = null;
        launchActive = false;
        highStackDanger = false;
        defconState = null;
        menuActive = false;
        switchToLocked(null, DEFAULT_FADE_MS);
    }

    public synchronized void setMasterVolume(float volume) {
        masterVolume = clamp01(volume);
        for (MusicTrackHandle h : activeHandles) h.setMasterVolume(masterVolume);
    }

    public synchronized MusicState currentStateForProbe() {
        return activeRequest == null ? null : activeRequest.state;
    }

    public synchronized String currentRequestKeyForProbe() {
        return activeRequest == null ? "" : activeRequest.identity();
    }

    public MusicPlaylist playlistForProbe(String key) {
        try {
            return playlists.get(PlaylistKey.valueOf(key));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public synchronized List<String> transitionLogForProbe() {
        return List.copyOf(transitionLog);
    }

    private void evaluateLocked() {
        MusicRequest target = targetLocked();
        int fadeMs = target != null && target.state == MusicState.RESULT
                ? RESULT_FADE_MS : DEFAULT_FADE_MS;
        switchToLocked(target, fadeMs);
    }

    private MusicRequest targetLocked() {
        if (resultMode != null) return requestForResult(resultMode);
        if (prematchLabActive) {
            return MusicRequest.single(MusicState.PREMATCH_LAB, PlaylistKey.LAB);
        }
        if (midgameBuilderRedesignOnly != null) {
            return MusicRequest.single(MusicState.MIDGAME_BUILDER,
                    midgameBuilderRedesignOnly ? PlaylistKey.REDESIGN : PlaylistKey.LAB_REDESIGN);
        }
        if (launchActive) return MusicRequest.single(MusicState.LAUNCH, PlaylistKey.LAUNCH);
        if (highStackDanger) return MusicRequest.single(MusicState.HIGH_STACK, PlaylistKey.HIGHSTACK);
        if (defconState != null) return requestForDefcon(defconState);
        if (menuActive) return MusicRequest.single(MusicState.MENU, PlaylistKey.MAIN);
        return null;
    }

    private void switchToLocked(MusicRequest target, int fadeMs) {
        if (Objects.equals(activeRequest, target)) return;
        activeRequest = target;
        generation++;
        long token = generation;
        transitionLog.add(target == null ? "STOP" : target.identity());
        audioExecutor.execute(() -> applyRequest(target, token, fadeMs));
    }

    private void applyRequest(MusicRequest request, long token, int fadeMs) {
        List<MusicTrackHandle> old;
        synchronized (this) {
            if (token != generation) return;
            old = new ArrayList<>(activeHandles);
            activeHandles.clear();
        }

        List<MusicTrackHandle> next = new ArrayList<>();
        if (request != null) {
            for (TrackSpec spec : request.tracks) {
                Path path = nextPath(spec.playlistKey);
                if (path == null) continue;
                if (audioEnabled) {
                    try {
                        MusicTrackHandle handle = MusicTrackHandle.open(
                                path, spec.pan, masterVolume,
                                () -> onTrackEnded(token, request));
                        next.add(handle);
                    } catch (Exception ex) {
                        warn("Unable to play " + path + ": " + ex.getMessage());
                    }
                }
            }
        }

        synchronized (this) {
            if (token != generation) {
                for (MusicTrackHandle h : next) h.stopNow();
                return;
            }
            activeHandles.addAll(next);
            naturalEndCount = 0;
        }

        for (MusicTrackHandle h : next) {
            h.start();
            h.fadeIn(fadeMs);
        }
        for (MusicTrackHandle h : old) {
            h.fadeOutAndClose(fadeMs);
        }
    }

    private synchronized void onTrackEnded(long token, MusicRequest request) {
        if (token != generation || !Objects.equals(activeRequest, request)) return;
        naturalEndCount++;
        int expectedEnds = activeHandles.size();
        if (expectedEnds > 1 && naturalEndCount < expectedEnds) return;
        generation++;
        long nextToken = generation;
        audioExecutor.execute(() -> applyRequest(request, nextToken, 80));
    }

    private synchronized Path nextPath(PlaylistKey key) {
        MusicPlaylist playlist = playlists.get(key);
        if (playlist == null || playlist.isEmpty()) {
            warn("Music playlist is empty: " + key.folderLabel);
            return null;
        }
        return playlist.nextTrack();
    }

    private MusicRequest requestForDefcon(MusicState state) {
        return switch (state) {
            case DEFCON_EARLY -> MusicRequest.single(MusicState.DEFCON_EARLY, PlaylistKey.EARLY);
            case DEFCON_MID -> MusicRequest.single(MusicState.DEFCON_MID, PlaylistKey.MID);
            case DEFCON_LATE -> MusicRequest.single(MusicState.DEFCON_LATE, PlaylistKey.LATE);
            default -> MusicRequest.single(MusicState.DEFCON_EARLY, PlaylistKey.EARLY);
        };
    }

    private MusicRequest requestForResult(ResultMusicMode mode) {
        return switch (mode) {
            case PVE_WIN -> MusicRequest.result(mode,
                    new TrackSpec(PlaylistKey.WIN_STEREO, 0f));
            case PVE_LOSE -> MusicRequest.result(mode,
                    new TrackSpec(PlaylistKey.LOSE_STEREO, 0f));
            case PVP_P1_WIN -> MusicRequest.result(mode,
                    new TrackSpec(PlaylistKey.WIN_MONO, -1f),
                    new TrackSpec(PlaylistKey.LOSE_MONO, 1f));
            case PVP_P2_WIN -> MusicRequest.result(mode,
                    new TrackSpec(PlaylistKey.LOSE_MONO, -1f),
                    new TrackSpec(PlaylistKey.WIN_MONO, 1f));
        };
    }

    private static MusicState stateForDefcon(int defcon) {
        if (defcon <= 2) return MusicState.DEFCON_LATE;
        if (defcon == 3) return MusicState.DEFCON_MID;
        return MusicState.DEFCON_EARLY;
    }

    private void loadPlaylists() {
        Map<String, List<Path>> folders = new LinkedHashMap<>();
        for (String folder : List.of("main", "lab", "redesign", "early", "mid",
                "late", "highstack", "launch", "win", "lose")) {
            folders.put(folder, scan(folder));
        }

        put(PlaylistKey.MAIN, folders.get("main"));
        put(PlaylistKey.LAB, folders.get("lab"));
        put(PlaylistKey.REDESIGN, folders.get("redesign"));
        put(PlaylistKey.EARLY, folders.get("early"));
        put(PlaylistKey.MID, folders.get("mid"));
        put(PlaylistKey.LATE, folders.get("late"));
        put(PlaylistKey.HIGHSTACK, folders.get("highstack"));
        put(PlaylistKey.LAUNCH, folders.get("launch"));

        List<Path> labRedesign = new ArrayList<>();
        labRedesign.addAll(folders.get("lab"));
        labRedesign.addAll(folders.get("redesign"));
        put(PlaylistKey.LAB_REDESIGN, labRedesign);

        put(PlaylistKey.WIN_STEREO, filter(folders.get("win"), false));
        put(PlaylistKey.LOSE_STEREO, filter(folders.get("lose"), false));

        List<Path> winMono = filter(folders.get("win"), true);
        List<Path> loseMono = filter(folders.get("lose"), true);
        put(PlaylistKey.WIN_MONO, winMono.isEmpty() ? folders.get("win") : winMono);
        put(PlaylistKey.LOSE_MONO, loseMono.isEmpty() ? folders.get("lose") : loseMono);
    }

    private void put(PlaylistKey key, List<Path> tracks) {
        playlists.put(key, new MusicPlaylist(key.folderLabel, tracks));
        if (tracks == null || tracks.isEmpty()) {
            warn("No .wav tracks found for " + key.folderLabel);
        }
    }

    private List<Path> scan(String folder) {
        Path dir = musicRoot.resolve(folder);
        if (!Files.isDirectory(dir)) {
            warn("Missing music folder: " + dir);
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".wav"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        } catch (IOException ex) {
            warn("Unable to scan music folder " + dir + ": " + ex.getMessage());
            return List.of();
        }
    }

    private static List<Path> filter(List<Path> tracks, boolean mono) {
        if (tracks == null) return List.of();
        return tracks.stream()
                .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).contains("-mono") == mono)
                .toList();
    }

    private static float clamp01(float v) {
        if (Float.isNaN(v)) return 1f;
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private static void warn(String msg) {
        System.err.println("[MAB-MUSIC] " + msg);
    }

    private enum PlaylistKey {
        MAIN("main"),
        LAB("lab"),
        REDESIGN("redesign"),
        LAB_REDESIGN("lab+redesign"),
        EARLY("early"),
        MID("mid"),
        LATE("late"),
        HIGHSTACK("highstack"),
        LAUNCH("launch"),
        WIN_STEREO("win"),
        LOSE_STEREO("lose"),
        WIN_MONO("win-mono"),
        LOSE_MONO("lose-mono");

        private final String folderLabel;
        PlaylistKey(String folderLabel) { this.folderLabel = folderLabel; }
    }

    private record TrackSpec(PlaylistKey playlistKey, float pan) {}

    private static final class MusicRequest {
        private final MusicState state;
        private final ResultMusicMode resultMode;
        private final List<TrackSpec> tracks;

        private MusicRequest(MusicState state, ResultMusicMode resultMode, List<TrackSpec> tracks) {
            this.state = state;
            this.resultMode = resultMode;
            this.tracks = List.copyOf(tracks);
        }

        static MusicRequest single(MusicState state, PlaylistKey key) {
            return new MusicRequest(state, null, List.of(new TrackSpec(key, 0f)));
        }

        static MusicRequest result(ResultMusicMode mode, TrackSpec... specs) {
            return new MusicRequest(MusicState.RESULT, mode, List.of(specs));
        }

        String identity() {
            StringBuilder sb = new StringBuilder(state.name());
            if (resultMode != null) sb.append(':').append(resultMode.name());
            for (TrackSpec t : tracks) sb.append(':').append(t.playlistKey.name());
            return sb.toString();
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof MusicRequest other)) return false;
            return state == other.state
                    && resultMode == other.resultMode
                    && tracks.equals(other.tracks);
        }

        @Override public int hashCode() {
            return Objects.hash(state, resultMode, tracks);
        }
    }
}
