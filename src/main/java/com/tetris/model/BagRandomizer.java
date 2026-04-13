package com.tetris.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * BagRandomizer.java
 * ===================
 * Implements the "7-bag" random generation system used in modern Tetris.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * HOW THE 7-BAG SYSTEM WORKS
 * ═══════════════════════════════════════════════════════════════════════
 * Rather than choosing each piece independently (pure random), which could
 * produce long droughts of needed pieces or floods of unwanted pieces,
 * modern Tetris uses a "bag" system:
 *
 *   1. Take all 7 tetrominoes (I, O, T, S, Z, J, L).
 *   2. Shuffle them into a random order — this is one "bag."
 *   3. Deal pieces from the bag one at a time.
 *   4. When the bag is empty, create a new shuffled bag.
 *
 * GUARANTEES:
 *   - You will see each piece exactly once per bag of 7.
 *   - The maximum drought (gap between two of the same piece) is 12
 *     (worst case: piece is last in one bag, first in the next-next bag).
 *   - The sequence feels "fair" while still being random.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * PREVIEW QUEUE
 * ═══════════════════════════════════════════════════════════════════════
 * Modern Tetris shows upcoming pieces (typically 5 or 6 in the "Next" box).
 * This class maintains a queue long enough to always have enough preview
 * pieces. When the queue runs low, a new bag is generated and appended.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * USAGE
 * ═══════════════════════════════════════════════════════════════════════
 *   BagRandomizer bag = new BagRandomizer();
 *   TetrominoType next = bag.next();         // get next piece
 *   List<TetrominoType> preview = bag.peek(5); // see next 5
 */
public class BagRandomizer {

    /** How many preview pieces to keep available at all times. */
    private static final int MIN_QUEUE_SIZE = 7;

    /** The queue of upcoming pieces. */
    private final Queue<TetrominoType> queue;

    // ─────────────────────────── Constructor ──────────────────────

    /**
     * Creates a new BagRandomizer with 2 initial bags queued up.
     */
    public BagRandomizer() {
        queue = new LinkedList<>();
        // Pre-fill with 2 bags to ensure enough preview pieces
        fillBag();
        fillBag();
    }

    // ─────────────────────────── Public API ──────────────────────

    /**
     * Returns and removes the next piece from the queue.
     * Automatically generates a new bag if the queue is running low.
     *
     * @return the next TetrominoType to spawn
     */
    public TetrominoType next() {
        if (queue.size() <= MIN_QUEUE_SIZE) {
            fillBag();
        }
        return queue.poll();
    }

    /**
     * Peeks at the next N upcoming pieces without removing them.
     * Used for the "Next" piece preview display.
     *
     * @param count number of pieces to preview
     * @return list of upcoming TetrominoTypes (first = next to spawn)
     */
    public List<TetrominoType> peek(int count) {
        // Ensure we have enough pieces queued
        while (queue.size() < count) {
            fillBag();
        }

        List<TetrominoType> preview = new ArrayList<>();
        int i = 0;
        for (TetrominoType type : queue) {
            if (i >= count) break;
            preview.add(type);
            i++;
        }
        return preview;
    }

    // ─────────────────────────── Internals ───────────────────────

    /**
     * Generates a new bag of 7 shuffled tetrominoes and appends them to the queue.
     */
    private void fillBag() {
        List<TetrominoType> bag = new ArrayList<>(List.of(TetrominoType.values()));
        Collections.shuffle(bag);
        queue.addAll(bag);
    }
}
