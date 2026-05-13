package com.tetris.mab.pieces;

import com.tetris.model.TetrominoType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Step 21 — deterministic shared 7-bag source for MAB versus modes.
 *
 * <p>Both MAB players consume the SAME ordered sequence of tetrominoes:
 * Player A's piece #n equals Player B's piece #n. Each player keeps an
 * independent cursor (via {@link MabPieceStream}). The sequence grows
 * lazily on demand and is fully determined by the seed; the same seed
 * always produces the same sequence, regardless of timing.
 *
 * <p>Used in: MAB PvE, local PvP, MAB practice/debug. Normal
 * single-player Tetris keeps the original {@link com.tetris.model.BagRandomizer}.
 *
 * <p><b>Offline-only.</b> No networking, no synchronization by wall
 * clock; pieces are synchronized purely by index.
 */
public final class MabSharedPieceSequence {

    private final long seed;
    private final Random rng;
    private final List<TetrominoType> sequence = new ArrayList<>();

    public MabSharedPieceSequence(long seed) {
        this.seed = seed;
        this.rng = new Random(seed);
        // Pre-fill two bags so peek(7) works immediately.
        appendBag();
        appendBag();
    }

    public long getSeed() { return seed; }

    /** Returns the piece at position {@code index} (0-based), growing the
     *  sequence as needed. */
    public synchronized TetrominoType get(int index) {
        if (index < 0) throw new IllegalArgumentException("index<0");
        while (index >= sequence.size()) {
            appendBag();
        }
        return sequence.get(index);
    }

    /** Current cached sequence length (for debug/probe). */
    public synchronized int generatedCount() { return sequence.size(); }

    /** Creates an independent player cursor over this shared sequence. */
    public MabPieceStream newStream() {
        return new MabPieceStream(this);
    }

    private void appendBag() {
        List<TetrominoType> bag = new ArrayList<>(List.of(TetrominoType.values()));
        // Seeded shuffle so the sequence is fully reproducible.
        Collections.shuffle(bag, rng);
        sequence.addAll(bag);
    }
}
