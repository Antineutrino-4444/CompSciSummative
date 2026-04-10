package com.tetris.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

/**
 * Implements the 7-bag randomizer as specified by the Tetris Guideline.
 *
 * <p>The 7-bag system ensures fair piece distribution by placing all 7 piece
 * types into a "bag", shuffling them, and dispensing them one at a time.
 * When the bag is empty, a new bag is generated. This guarantees that the
 * maximum gap between any two of the same piece type is 12.</p>
 *
 * <p>A preview queue is maintained to allow showing upcoming pieces to the
 * player (typically 5 or 6 pieces ahead).</p>
 */
public class BagRandomizer {

    /** The internal queue of upcoming pieces. */
    private final Queue<Piece> queue = new LinkedList<>();

    /** Random number generator for shuffling bags. */
    private final Random random;

    /** Number of pieces to keep available in the preview queue. */
    private final int previewSize;

    /**
     * Creates a new BagRandomizer with the specified preview size using
     * a random seed.
     *
     * @param previewSize number of upcoming pieces to maintain in the queue
     */
    public BagRandomizer(int previewSize) {
        this(previewSize, new Random());
    }

    /**
     * Creates a new BagRandomizer with the specified preview size and
     * random number generator. Useful for deterministic testing.
     *
     * @param previewSize number of upcoming pieces to maintain in the queue
     * @param random      the random number generator to use for shuffling
     */
    public BagRandomizer(int previewSize, Random random) {
        this.previewSize = previewSize;
        this.random = random;
        fillQueue();
    }

    /**
     * Ensures the queue has enough pieces to serve the next piece plus
     * all preview pieces.
     */
    private void fillQueue() {
        while (queue.size() <= previewSize) {
            List<Piece> bag = new ArrayList<>(List.of(Piece.values()));
            Collections.shuffle(bag, random);
            queue.addAll(bag);
        }
    }

    /**
     * Returns and removes the next piece from the queue.
     * Automatically refills the bag when needed.
     *
     * @return the next piece to play
     */
    public Piece next() {
        Piece piece = queue.poll();
        fillQueue();
        return piece;
    }

    /**
     * Returns a list of the next N pieces in the preview queue
     * without removing them.
     *
     * @param count number of preview pieces to return
     * @return unmodifiable list of upcoming pieces
     */
    public List<Piece> peekNext(int count) {
        List<Piece> preview = new ArrayList<>();
        int i = 0;
        for (Piece p : queue) {
            if (i >= count) break;
            preview.add(p);
            i++;
        }
        return Collections.unmodifiableList(preview);
    }

    /**
     * Returns the configured preview size.
     *
     * @return the number of pieces shown in the next queue
     */
    public int getPreviewSize() {
        return previewSize;
    }
}
