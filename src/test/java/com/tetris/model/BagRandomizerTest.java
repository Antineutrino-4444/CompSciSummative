package com.tetris.model;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the BagRandomizer class covering 7-bag distribution,
 * preview functionality, and deterministic seeding.
 */
class BagRandomizerTest {

    @Test
    void firstSevenPiecesContainAllTypes() {
        BagRandomizer bag = new BagRandomizer(5);
        Set<Piece> seen = EnumSet.noneOf(Piece.class);
        for (int i = 0; i < 7; i++) {
            seen.add(bag.next());
        }
        assertEquals(7, seen.size(), "First 7 pieces should include all 7 types");
    }

    @Test
    void secondBagAlsoContainsAllTypes() {
        BagRandomizer bag = new BagRandomizer(5);
        // Drain first bag
        for (int i = 0; i < 7; i++) bag.next();
        // Check second bag
        Set<Piece> seen = EnumSet.noneOf(Piece.class);
        for (int i = 0; i < 7; i++) {
            seen.add(bag.next());
        }
        assertEquals(7, seen.size());
    }

    @Test
    void previewDoesNotConsumeItems() {
        BagRandomizer bag = new BagRandomizer(5);
        List<Piece> preview1 = bag.peekNext(3);
        List<Piece> preview2 = bag.peekNext(3);
        assertEquals(preview1, preview2, "Peeking should not modify the queue");
    }

    @Test
    void previewMatchesNextPieces() {
        BagRandomizer bag = new BagRandomizer(5);
        List<Piece> preview = bag.peekNext(3);
        assertEquals(preview.get(0), bag.next());
        assertEquals(preview.get(1), bag.next());
        assertEquals(preview.get(2), bag.next());
    }

    @Test
    void deterministicWithSeed() {
        Random r1 = new Random(42);
        Random r2 = new Random(42);
        BagRandomizer bag1 = new BagRandomizer(5, r1);
        BagRandomizer bag2 = new BagRandomizer(5, r2);

        for (int i = 0; i < 21; i++) {
            assertEquals(bag1.next(), bag2.next(),
                    "Same seed should produce same sequence at index " + i);
        }
    }

    @Test
    void previewSizeIsCorrect() {
        BagRandomizer bag = new BagRandomizer(6);
        assertEquals(6, bag.getPreviewSize());
        assertEquals(6, bag.peekNext(6).size());
    }

    @Test
    void maxGapBetweenSamePieceIsAtMost12() {
        // In 7-bag, the maximum gap between same pieces is 12
        // (last in one bag, first in the next-next bag)
        BagRandomizer bag = new BagRandomizer(5);
        int[] lastSeen = new int[Piece.values().length];
        java.util.Arrays.fill(lastSeen, -1);
        int maxGap = 0;

        for (int i = 0; i < 70; i++) {
            Piece p = bag.next();
            int idx = p.ordinal();
            if (lastSeen[idx] >= 0) {
                int gap = i - lastSeen[idx] - 1;
                maxGap = Math.max(maxGap, gap);
            }
            lastSeen[idx] = i;
        }

        assertTrue(maxGap <= 12,
                "Maximum gap between same piece should be ≤12, was " + maxGap);
    }
}
