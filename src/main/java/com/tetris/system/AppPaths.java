package com.tetris.system;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Centralized runtime paths for installed/packaged runs.
 */
public final class AppPaths {
    private static final String APP_DIR_NAME = "Modern Tetris";
    private static final String APP_DIR_NAME_UNIX = "modern-tetris";
    private static final Path DATA_DIR = computeDataDir();
    private static final Path SETTINGS_FILE = DATA_DIR.resolve("settings.properties");
    private static final Path PACKAGED_MUSIC_DIR = DATA_DIR.resolve("music");
    private static final Path PACKAGED_MUSIC_READY_MARKER =
            PACKAGED_MUSIC_DIR.resolve(".packaged-music-ready");

    private AppPaths() {}

    public static Path dataDir() {
        return DATA_DIR;
    }

    public static Path settingsFile() {
        return SETTINGS_FILE;
    }

    public static Path packagedMusicDir() {
        return PACKAGED_MUSIC_DIR;
    }

    public static Path packagedMusicReadyMarker() {
        return PACKAGED_MUSIC_READY_MARKER;
    }

    /**
     * Uses extracted music for packaged runs, while preserving the repo-local
     * music folder for development runs that were not launched from a JAR.
     */
    public static Path musicDir() {
        if (Files.isRegularFile(PACKAGED_MUSIC_READY_MARKER)) {
            return PACKAGED_MUSIC_DIR;
        }
        Path localMusic = Paths.get("music");
        if (hasAnyWav(localMusic)) {
            return localMusic;
        }
        if (hasAnyWav(PACKAGED_MUSIC_DIR)) {
            return PACKAGED_MUSIC_DIR;
        }
        return PACKAGED_MUSIC_DIR;
    }

    public static Path legacySettingsFile() {
        return Paths.get(System.getProperty("user.home"), ".modern-tetris", "settings.properties");
    }

    public static void ensureBaseDirectories() throws java.io.IOException {
        Files.createDirectories(DATA_DIR);
        Files.createDirectories(PACKAGED_MUSIC_DIR);
    }

    private static Path computeDataDir() {
        String override = trimToNull(System.getProperty("tetris.appDataDir"));
        if (override != null) {
            return Paths.get(override).toAbsolutePath().normalize();
        }

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home", ".");
        if (os.contains("win")) {
            String appData = trimToNull(System.getenv("APPDATA"));
            if (appData != null) return Paths.get(appData, APP_DIR_NAME).toAbsolutePath().normalize();
            String localAppData = trimToNull(System.getenv("LOCALAPPDATA"));
            if (localAppData != null) return Paths.get(localAppData, APP_DIR_NAME).toAbsolutePath().normalize();
            return Paths.get(home, "AppData", "Roaming", APP_DIR_NAME).toAbsolutePath().normalize();
        }
        if (os.contains("mac")) {
            return Paths.get(home, "Library", "Application Support", APP_DIR_NAME).toAbsolutePath().normalize();
        }

        String xdgDataHome = trimToNull(System.getenv("XDG_DATA_HOME"));
        if (xdgDataHome != null) return Paths.get(xdgDataHome, APP_DIR_NAME_UNIX).toAbsolutePath().normalize();
        return Paths.get(home, ".local", "share", APP_DIR_NAME_UNIX).toAbsolutePath().normalize();
    }

    private static boolean hasAnyWav(Path root) {
        if (!Files.isDirectory(root)) return false;
        try (var stream = Files.walk(root, 2)) {
            return stream
                    .filter(Files::isRegularFile)
                    .anyMatch(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".wav"));
        } catch (Exception ex) {
            return false;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
