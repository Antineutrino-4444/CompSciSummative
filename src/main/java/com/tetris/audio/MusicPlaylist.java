package com.tetris.audio;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Shuffle-bag playlist. Every track is used once per cycle; folders
 * with two or more tracks never repeat the same file adjacently, even
 * across cycle boundaries and after returning to the playlist later.
 */
public final class MusicPlaylist {

    private final String name;
    private final List<Path> tracks;
    private final Random random;
    private final List<Path> bag = new ArrayList<>();
    private Path lastServed;

    public MusicPlaylist(String name, List<Path> tracks) {
        this(name, tracks, new Random());
    }

    MusicPlaylist(String name, List<Path> tracks, Random random) {
        this.name = name == null ? "playlist" : name;
        this.tracks = tracks == null
                ? List.of()
                : List.copyOf(tracks);
        this.random = random == null ? new Random() : random;
    }

    public String getName() { return name; }
    public int size() { return tracks.size(); }
    public boolean isEmpty() { return tracks.isEmpty(); }
    public List<Path> tracks() { return tracks; }

    public synchronized Path nextTrack() {
        if (tracks.isEmpty()) return null;
        if (bag.isEmpty()) refillBag();
        if (bag.isEmpty()) return null;
        Path next = bag.remove(0);
        lastServed = next;
        return next;
    }

    private void refillBag() {
        bag.clear();
        bag.addAll(tracks);
        if (bag.size() <= 1) return;

        for (int attempt = 0; attempt < 12; attempt++) {
            Collections.shuffle(bag, random);
            if (!Objects.equals(bag.get(0), lastServed)) return;
        }

        if (Objects.equals(bag.get(0), lastServed)) {
            for (int i = 1; i < bag.size(); i++) {
                if (!Objects.equals(bag.get(i), lastServed)) {
                    Collections.swap(bag, 0, i);
                    return;
                }
            }
        }
    }
}
