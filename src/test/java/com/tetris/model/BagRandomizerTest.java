package com.tetris.model;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BagRandomizer.
 */
class BagRandomizerTest {

    @Test
    void firstBagContainsAllTypes() {
        BagRandomizer bag = new BagRandomizer(5);
        Set<Piece> seen = EnumSet.noneOf(Piece.class);
        for (int i = 0; i < Piece.values().length; i++) {
            seen.add(bag.next());
        }
        assertEquals(Piece.values().length, seen.size(),
                "First bag should include all " + Piece.values().length + " piece types");
    }

    @Test
    void secondBagAlsoContainsAllTypes() {
        BagRandomizer bag = new BagRandomizer(5);
        for (int i = 0; i < Piece.values().length; i++) bag.next();
        Set<Piece> seen = EnumSet.noneOf(Piece.class);
        for (int i = 0; i < Piece.values().length; i++) {
            seen.add(bag.next());
        }
        assertEquals(Piece.values().length, seen.size());
    }

    @Test
    void previewDoesNotConsumeItems() {
        BagRandomizer bag = new BagRandomizer(5);
        List<Piece> preview1 = bag.peekNext(3);
        List<Piece> preview2 = bag.peekNext(3);
        assertEquals(preview1, preview2);
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
        BagRandomizer bag1 = new BagRandomizer(5, new Random(42));
        BagRandomizer bag2 = new BagRandomizer(5, new Random(42));
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
    void maxGapIsReasonable() {
        BagRandomizer bag = new BagRandomizer(5);
        int[] lastSeen = new int[Piece.values().length];
        java.util.Arrays.fill(lastSeen, -1);
        int maxGap = 0;

        for (int i = 0; i < 50; i++) {
            Piece p = bag.next();
            int idx = p.ordinal();
            if (lastSeen[idx] >= 0) {
                maxGap = Math.max(maxGap, i - lastSeen[idx] - 1);
            }
            lastSeen[idx] = i;
        }

        // With 5-bag, max gap between same piece is (5-1) + (5-1) = 8
        assertTrue(maxGap <= 8,
                "Maximum gap between same piece should be reasonable, was " + maxGap);
    }
}
