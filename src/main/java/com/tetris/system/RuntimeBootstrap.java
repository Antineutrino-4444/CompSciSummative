package com.tetris.system;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Startup work that must also happen when the game is launched from a JAR.
 */
public final class RuntimeBootstrap {
    private static final String MUSIC_MANIFEST_RESOURCE = "/music-manifest.txt";
    private static boolean bootstrapped;

    private RuntimeBootstrap() {}

    public static synchronized void run() {
        if (bootstrapped) return;
        bootstrapped = true;

        try {
            AppPaths.ensureBaseDirectories();
            System.out.println("[runtime] App data: " + AppPaths.dataDir());
        } catch (IOException ex) {
            System.err.println("[runtime] Unable to create app data directory: " + ex.getMessage());
        }

        registerBundledFonts();
        checkFonts();
        bootstrapPackagedMusic();
    }

    /**
     * Fonts bundled with the app and registered into the JVM at startup so the
     * UI font chains ({@code MabUiTheme.pickFont}) resolve to the intended
     * faces on every host, even one that has none of them installed.
     *
     * <p>Covers the primary chain choices (Bahnschrift for headlines, Consolas
     * for the terminal text), their bundled fallbacks (Segoe UI, Helvetica
     * Neue, Franklin Gothic, Lucida Console), and the final bundled fallbacks
     * (Ubuntu / Ubuntu Mono). {@code .otf} loads fine via
     * {@link Font#TRUETYPE_FONT}.
     */
    private static final String[] BUNDLED_FONT_FILES = {
            // Headline chain.
            "Bahnschrift.ttf",
            "SegoeUI-Regular.ttf",
            "SegoeUI-Bold.ttf",
            "HelveticaNeue-Roman.otf",
            "HelveticaNeue-Bold.ttf",
            "FranklinGothic.ttf",
            // Terminal / mono chain.
            "Consolas-Regular.ttf",
            "Consolas-Bold.ttf",
            "LucidaConsole.ttf",
            // Open-licensed final fallbacks.
            "Ubuntu-Regular.ttf",
            "Ubuntu-Bold.ttf",
            "UbuntuMono-Regular.ttf",
            "UbuntuMono-Bold.ttf",
    };

    /**
     * Registers the bundled fonts into this JVM so the UI font chains in
     * {@code MabUiTheme.pickFont} can resolve them when the host lacks the
     * proprietary originals. Fonts are loaded process-locally (no system
     * install, no admin) and resolved from the classpath ({@code /fonts/...}
     * in a packaged JAR) with a {@code ./fonts} filesystem fallback for
     * development runs. Failures are non-fatal — the game always has the
     * JVM logical fonts to fall back on.
     */
    private static void registerBundledFonts() {
        if (!Boolean.parseBoolean(
                System.getProperty("tetris.bootstrap.registerFonts", "true"))) {
            System.out.println("[fonts] Bundled font registration disabled for this run.");
            return;
        }
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        int registered = 0;
        for (String name : BUNDLED_FONT_FILES) {
            try (InputStream in = openBundledFont(name)) {
                if (in == null) {
                    System.err.println("[fonts] Bundled font not found: " + name);
                    continue;
                }
                Font font = Font.createFont(Font.TRUETYPE_FONT, in);
                // registerFont returns false if the host already has a font
                // with the same name — harmless, the host copy is used.
                if (ge.registerFont(font)) {
                    registered++;
                }
            } catch (Exception ex) {
                System.err.println("[fonts] Unable to register " + name + ": "
                        + ex.getMessage());
            }
        }
        if (registered > 0) {
            System.out.println("[fonts] Registered " + registered
                    + " bundled font(s) for this run.");
        }
    }

    private static InputStream openBundledFont(String name) throws IOException {
        InputStream cp = RuntimeBootstrap.class.getResourceAsStream("/fonts/" + name);
        if (cp != null) {
            return cp;
        }
        Path local = Paths.get("fonts", name);
        if (Files.isRegularFile(local)) {
            return Files.newInputStream(local);
        }
        return null;
    }

    private static void checkFonts() {
        System.out.println("[fonts] Checking bundled runtime fonts...");
        Set<String> families = installedFontFamilies();
        if (families.isEmpty()) {
            System.out.println("[fonts]   Font list unavailable; JVM fallbacks will be used.");
            return;
        }

        List<String> checks = bundledFontFamilyChecks();
        List<String> missing = new ArrayList<>();
        for (String font : checks) {
            if (containsFamily(families, font)) {
                System.out.println("[fonts]   [OK]      " + font);
            } else {
                System.out.println("[fonts]   [MISSING] " + font);
                missing.add(font);
            }
        }

        if (missing.isEmpty()) {
            System.out.println("[fonts] Bundled font families available.");
        } else {
            System.out.println("[fonts] " + missing.size()
                    + " bundled font family/families unavailable; cosmetic only, "
                    + "game uses JVM fallbacks.");
        }
    }

    private static Set<String> installedFontFamilies() {
        try {
            String[] names = GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames(Locale.ENGLISH);
            Set<String> result = new TreeSet<>();
            for (String name : names) result.add(name.toLowerCase(Locale.ROOT));
            return result;
        } catch (Throwable ex) {
            return Set.of();
        }
    }

    private static List<String> bundledFontFamilyChecks() {
        return List.of("Bahnschrift", "Franklin Gothic", "Segoe UI",
                "Helvetica Neue", "Ubuntu", "Ubuntu Mono", "Consolas",
                "Lucida Console");
    }

    private static boolean containsFamily(Set<String> families, String wanted) {
        String key = wanted.toLowerCase(Locale.ROOT);
        if (families.contains(key)) return true;
        for (String family : families) {
            if (family.contains(key)) return true;
        }
        return false;
    }

    private static void bootstrapPackagedMusic() {
        if (!Boolean.parseBoolean(System.getProperty("tetris.bootstrap.extractMusic", "true"))) {
            System.out.println("[music] Packaged music extraction disabled for this run.");
            return;
        }

        List<MusicManifestEntry> entries = readMusicManifest();
        if (entries.isEmpty()) {
            return;
        }

        int copied = 0;
        long bytesCopied = 0L;
        Path dataDir = AppPaths.dataDir().toAbsolutePath().normalize();
        for (MusicManifestEntry entry : entries) {
            Path destination = dataDir.resolve(entry.resourcePath()).normalize();
            if (!destination.startsWith(dataDir)) {
                System.err.println("[music] Skipping suspicious music resource path: "
                        + entry.resourcePath());
                continue;
            }
            try {
                if (Files.isRegularFile(destination)
                        && Files.size(destination) == entry.sizeBytes()) {
                    continue;
                }
                Files.createDirectories(destination.getParent());
                try (InputStream in = RuntimeBootstrap.class.getResourceAsStream(
                        "/" + entry.resourcePath())) {
                    if (in == null) {
                        System.err.println("[music] Missing packaged resource: "
                                + entry.resourcePath());
                        continue;
                    }
                    Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
                }
                copied++;
                bytesCopied += entry.sizeBytes();
            } catch (IOException ex) {
                System.err.println("[music] Unable to extract " + entry.resourcePath()
                        + ": " + ex.getMessage());
            }
        }

        if (copied > 0) {
            System.out.println("[music] Extracted " + copied + " track(s), "
                    + formatBytes(bytesCopied) + " to " + AppPaths.packagedMusicDir());
        } else {
            System.out.println("[music] Packaged music already available at "
                    + AppPaths.packagedMusicDir());
        }

        markPackagedMusicReadyIfComplete(entries, dataDir);
    }

    private static void markPackagedMusicReadyIfComplete(
            List<MusicManifestEntry> entries,
            Path dataDir) {
        for (MusicManifestEntry entry : entries) {
            Path destination = dataDir.resolve(entry.resourcePath()).normalize();
            try {
                if (!Files.isRegularFile(destination)
                        || Files.size(destination) != entry.sizeBytes()) {
                    return;
                }
            } catch (IOException ex) {
                return;
            }
        }

        try {
            Files.writeString(AppPaths.packagedMusicReadyMarker(),
                    "tracks=" + entries.size() + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (IOException ex) {
            System.err.println("[music] Unable to write packaged music marker: "
                    + ex.getMessage());
        }
    }

    private static List<MusicManifestEntry> readMusicManifest() {
        InputStream raw = RuntimeBootstrap.class.getResourceAsStream(MUSIC_MANIFEST_RESOURCE);
        if (raw == null) return List.of();

        List<MusicManifestEntry> entries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(raw, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\t", 2);
                if (parts.length != 2) continue;
                try {
                    entries.add(new MusicManifestEntry(parts[0], Long.parseLong(parts[1])));
                } catch (NumberFormatException ignored) {
                    // Ignore malformed manifest rows so one bad row cannot stop startup.
                }
            }
        } catch (IOException ex) {
            System.err.println("[music] Unable to read packaged music manifest: "
                    + ex.getMessage());
        }
        return entries;
    }

    private static String formatBytes(long bytes) {
        double mib = bytes / (1024.0 * 1024.0);
        if (mib < 1024.0) {
            return String.format(Locale.ROOT, "%.1f MiB", mib);
        }
        return String.format(Locale.ROOT, "%.2f GiB", mib / 1024.0);
    }

    private record MusicManifestEntry(String resourcePath, long sizeBytes) {}
}
