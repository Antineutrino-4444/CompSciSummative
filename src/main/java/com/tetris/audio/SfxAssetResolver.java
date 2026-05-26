package com.tetris.audio;

import com.tetris.system.AppPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Resolves logical {@link SoundEffect} IDs into concrete {@code *-converted.wav}
 * file paths. The audio library now ships exclusively in converted WAV form;
 * legacy {@code .ogg} files are never referenced at runtime.
 *
 * <p>Resolution is location-tolerant: a packaged install drops sfx files next
 * to the music data, while a development checkout keeps them in the repo's
 * {@code sfx/} folder. This resolver tries both, and returns {@code null} when
 * neither holds a matching file — callers must treat that as a soft miss
 * (warn + continue), not a crash.
 */
public final class SfxAssetResolver {

    /** Filename suffix every shipped sfx asset uses. */
    public static final String CONVERTED_SUFFIX = "-converted.wav";

    /**
     * Asset bases the prompt explicitly retired. These names must not resolve
     * to a playable path even if a stale file is found on disk.
     */
    private static final Set<String> DELETED_PREFIXES = Set.of(
            "b2bcharge_",
            "ihs",
            "irs",
            "losestock",
            "hyperalert",
            "zenith_upspeed_",
            "impact",
            "hit"
    );

    private final Path[] roots;

    public SfxAssetResolver() {
        this(defaultRoots());
    }

    public SfxAssetResolver(Path... roots) {
        Set<Path> unique = new LinkedHashSet<>();
        if (roots != null) {
            for (Path p : roots) {
                if (p != null) unique.add(p);
            }
        }
        this.roots = unique.toArray(new Path[0]);
    }

    /** Locations checked when no explicit root is provided. */
    public static Path[] defaultRoots() {
        Set<Path> unique = new LinkedHashSet<>();
        // Repo / development layout: ./sfx
        Path dev = Paths.get("sfx").toAbsolutePath().normalize();
        unique.add(dev);
        // Packaged install: <appdata>/MAB/sfx (sibling of music/)
        unique.add(AppPaths.dataDir().resolve("sfx"));
        // Music root sibling lookup, in case music was extracted elsewhere.
        Path musicRoot = AppPaths.musicDir();
        if (musicRoot != null) {
            Path musicSibling = musicRoot.toAbsolutePath().getParent();
            if (musicSibling != null) unique.add(musicSibling.resolve("sfx"));
        }
        return unique.toArray(new Path[0]);
    }

    /**
     * Resolves a {@link SoundEffect} ID to its file path, or {@code null} when
     * the asset is missing or has been retired. Retired asset bases short-
     * circuit before disk lookup so callers can never accidentally play one.
     */
    public Path resolve(SoundEffect effect) {
        if (effect == null) return null;
        return resolve(effect.assetName());
    }

    /**
     * Resolves a bare asset base name (without the {@code -converted.wav}
     * suffix) to a file path on disk, returning {@code null} when missing or
     * retired.
     */
    public Path resolve(String assetBase) {
        if (assetBase == null || assetBase.isBlank()) return null;
        String base = assetBase.toLowerCase(Locale.ROOT).trim();
        if (isDeleted(base)) return null;
        String filename = base + CONVERTED_SUFFIX;
        for (Path root : roots) {
            if (root == null) continue;
            Path candidate = root.resolve(filename);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        return null;
    }

    /** True if the given asset name belongs to the retired-asset list. */
    public static boolean isDeleted(String assetBase) {
        if (assetBase == null) return false;
        String base = assetBase.toLowerCase(Locale.ROOT).trim();
        for (String prefix : DELETED_PREFIXES) {
            if (prefix.endsWith("_")) {
                if (base.startsWith(prefix)) return true;
            } else if (base.equals(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** Read-only view of the asset roots inspected at resolution time. */
    public Set<Path> roots() {
        Set<Path> view = new LinkedHashSet<>();
        for (Path p : roots) {
            if (p != null) view.add(p);
        }
        return Collections.unmodifiableSet(view);
    }
}
