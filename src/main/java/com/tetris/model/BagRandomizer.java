package com.tetris.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

/**
 * Implements a weighted 7-piece bag randomizer for particle piece types.
 *
 * <p>Each bag contains 7 pieces: 2 top-quarks, 2 bottom-quarks, and 3 gluons
 * (~43% gluons). This weighting ensures gluons — the connective tissue every
 * hadron recipe needs — are the most common piece. Shape variants (A vs B) for
 * quarks are chosen randomly when each quark slot is dispensed.</p>
 *
 * <p>A preview queue is maintained to allow showing upcoming pieces to the
 * player.</p>
 */
public class BagRandomizer {

    /** Number of pieces in each bag. */
    static final int BAG_SIZE = 7;

    /** Top-quark shape variants, chosen randomly per slot. */
    private static final Piece[] TOP_VARIANTS = { Piece.TOP_QUARK_A, Piece.TOP_QUARK_B };

    /** Bottom-quark shape variants, chosen randomly per slot. */
    private static final Piece[] BOTTOM_VARIANTS = { Piece.BOTTOM_QUARK_A, Piece.BOTTOM_QUARK_B };

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

    // TODO: accelerator meter — let the player choose their next piece type

    /**
     * Ensures the queue has enough pieces to serve the next piece plus
     * all preview pieces.
     *
     * <p>Each bag is: 2 top-quarks (random shape), 2 bottom-quarks (random shape),
     * 3 gluons — shuffled together.</p>
     */
    private void fillQueue() {
        while (queue.size() <= previewSize) {
            List<Piece> bag = new ArrayList<>(BAG_SIZE);
            bag.add(TOP_VARIANTS[random.nextInt(TOP_VARIANTS.length)]);
            bag.add(TOP_VARIANTS[random.nextInt(TOP_VARIANTS.length)]);
            bag.add(BOTTOM_VARIANTS[random.nextInt(BOTTOM_VARIANTS.length)]);
            bag.add(BOTTOM_VARIANTS[random.nextInt(BOTTOM_VARIANTS.length)]);
            bag.add(Piece.GLUON);
            bag.add(Piece.GLUON);
            bag.add(Piece.GLUON);
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
