package com.tetris.model;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BagRandomizer (weighted 7-piece bag).
 */
class BagRandomizerTest {

    @Test
    void bagContainsCorrectParticleDistribution() {
        BagRandomizer bag = new BagRandomizer(5, new Random(42));
        int topCount = 0, bottomCount = 0, gluonCount = 0;
        for (int i = 0; i < BagRandomizer.BAG_SIZE; i++) {
            Piece p = bag.next();
            if (p.isTopQuark()) topCount++;
            else if (p.isBottomQuark()) bottomCount++;
            else if (p.isGluon()) gluonCount++;
        }
        assertEquals(2, topCount, "Each bag should have 2 top quarks");
        assertEquals(2, bottomCount, "Each bag should have 2 bottom quarks");
        assertEquals(3, gluonCount, "Each bag should have 3 gluons");
    }

    @Test
    void secondBagAlsoHasCorrectDistribution() {
        BagRandomizer bag = new BagRandomizer(5, new Random(99));
        // Consume first bag
        for (int i = 0; i < BagRandomizer.BAG_SIZE; i++) bag.next();
        // Check second bag
        int topCount = 0, bottomCount = 0, gluonCount = 0;
        for (int i = 0; i < BagRandomizer.BAG_SIZE; i++) {
            Piece p = bag.next();
            if (p.isTopQuark()) topCount++;
            else if (p.isBottomQuark()) bottomCount++;
            else if (p.isGluon()) gluonCount++;
        }
        assertEquals(2, topCount);
        assertEquals(2, bottomCount);
        assertEquals(3, gluonCount);
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
    void gluonFrequencyIsRoughly43Percent() {
        BagRandomizer bag = new BagRandomizer(5, new Random(123));
        int gluonCount = 0;
        int total = BagRandomizer.BAG_SIZE * 10; // 10 bags
        for (int i = 0; i < total; i++) {
            if (bag.next().isGluon()) gluonCount++;
        }
        double ratio = (double) gluonCount / total;
        // Expected: 3/7 ≈ 0.4286
        assertTrue(ratio > 0.40 && ratio < 0.46,
                "Gluon frequency should be ~43%, was " + (ratio * 100) + "%");
    }
}
