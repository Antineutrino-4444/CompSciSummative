package com.tetris.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HadronDetector — particle combination mechanic.
 */
class HadronDetectorTest {

    private Board board;
    private HadronDetector detector;

    @BeforeEach
    void setUp() {
        board = new Board();
        detector = new HadronDetector();
    }

    // ==================== PROTON TESTS ====================

    @Test
    void detectsProtonFromTwoTopOneBottom() {
        // Place 2 top quarks + 1 bottom quark in a connected line
        board.setCell(3, 0, Piece.TOP_QUARK_R);
        board.setCell(4, 0, Piece.TOP_QUARK_G);
        board.setCell(5, 0, Piece.BOTTOM_QUARK_R);

        // Detect using last placed piece at (5,0)
        List<Hadron> hadrons = detector.detect(board, Piece.BOTTOM_QUARK_R, 0, 3, 1);
        // The detect method searches around placed cells, so we need the
        // right parameters. Let's use a simpler approach:
        // Reset and use direct placement coordinates
        board = new Board();
        board.setCell(3, 0, Piece.TOP_QUARK_R);
        board.setCell(4, 0, Piece.TOP_QUARK_G);

        // Place a bottom quark adjacent
        board.setCell(4, 1, Piece.BOTTOM_QUARK_R);

        // Simulate detect from a piece that placed the bottom quark
        // Use Piece.BOTTOM_QUARK_R with J-shape, rotation 0, at col 3 row 1
        // But for simplicity, just directly test with the method
        hadrons = detectNearCell(board, 4, 1);

        assertTrue(hadrons.stream().anyMatch(h -> h == Hadron.PROTON),
                "Should detect a proton from 2 top + 1 bottom quark");
    }

    @Test
    void detectsNeutronFromOneTopTwoBottom() {
        board.setCell(3, 0, Piece.TOP_QUARK_R);
        board.setCell(4, 0, Piece.BOTTOM_QUARK_R);
        board.setCell(4, 1, Piece.BOTTOM_QUARK_G);

        List<Hadron> hadrons = detectNearCell(board, 4, 1);
        assertTrue(hadrons.stream().anyMatch(h -> h == Hadron.NEUTRON),
                "Should detect a neutron from 1 top + 2 bottom quarks");
    }

    // ==================== PION TESTS ====================

    @Test
    void detectsPionPlusFromTopQuarkAndGluon() {
        board.setCell(5, 0, Piece.TOP_QUARK_R);
        board.setCell(5, 1, Piece.GLUON);

        List<Hadron> hadrons = detectNearCell(board, 5, 1);
        assertTrue(hadrons.stream().anyMatch(h -> h == Hadron.PION_PLUS),
                "Should detect π+ from top quark + gluon");
    }

    @Test
    void detectsPionMinusFromBottomQuarkAndGluon() {
        board.setCell(5, 0, Piece.BOTTOM_QUARK_R);
        board.setCell(5, 1, Piece.GLUON);

        List<Hadron> hadrons = detectNearCell(board, 5, 1);
        assertTrue(hadrons.stream().anyMatch(h -> h == Hadron.PION_MINUS),
                "Should detect π- from bottom quark + gluon");
    }

    @Test
    void detectsPionZeroFromTwoSameQuarksAndGluon() {
        // Gluon in center, two top quarks on sides
        board.setCell(3, 0, Piece.TOP_QUARK_R);
        board.setCell(4, 0, Piece.GLUON);
        board.setCell(5, 0, Piece.TOP_QUARK_G);

        List<Hadron> hadrons = detectNearCell(board, 4, 0);
        assertTrue(hadrons.stream().anyMatch(h -> h == Hadron.PION_ZERO),
                "Should detect π0 from 2 top quarks + gluon");
    }

    @Test
    void detectsPionZeroFromTwoBottomQuarksAndGluon() {
        board.setCell(3, 0, Piece.BOTTOM_QUARK_R);
        board.setCell(4, 0, Piece.GLUON);
        board.setCell(5, 0, Piece.BOTTOM_QUARK_G);

        List<Hadron> hadrons = detectNearCell(board, 4, 0);
        assertTrue(hadrons.stream().anyMatch(h -> h == Hadron.PION_ZERO),
                "Should detect π0 from 2 bottom quarks + gluon");
    }

    // ==================== NO DETECTION TESTS ====================

    @Test
    void noHadronFromOnlyTopQuarks() {
        board.setCell(3, 0, Piece.TOP_QUARK_R);
        board.setCell(4, 0, Piece.TOP_QUARK_G);
        board.setCell(5, 0, Piece.TOP_QUARK_B);

        List<Hadron> hadrons = detectNearCell(board, 4, 0);
        // 3 top quarks doesn't match any recipe (proton needs 2t+1b)
        assertTrue(hadrons.stream().noneMatch(h -> h == Hadron.PROTON),
                "3 top quarks should not form a proton");
    }

    @Test
    void noHadronFromDisconnectedPieces() {
        // Top quark at (0,0) and bottom quark at (9,0) — not adjacent
        board.setCell(0, 0, Piece.TOP_QUARK_R);
        board.setCell(9, 0, Piece.GLUON);

        List<Hadron> hadrons = detectNearCell(board, 9, 0);
        assertTrue(hadrons.isEmpty(),
                "Disconnected pieces should not form hadrons");
    }

    // ==================== CELL CONSUMPTION TESTS ====================

    @Test
    void consumedCellsAreCleared() {
        board.setCell(5, 0, Piece.TOP_QUARK_R);
        board.setCell(5, 1, Piece.GLUON);

        detectNearCell(board, 5, 1);

        // The cells used for the pion should be cleared
        assertNull(board.getCell(5, 0), "Top quark cell should be consumed");
        assertNull(board.getCell(5, 1), "Gluon cell should be consumed");
    }

    @Test
    void gravityAppliesAfterConsumption() {
        // Place particles: gluon at (5,0), top quark at (5,1), and something at (5,2)
        board.setCell(5, 0, Piece.GLUON);
        board.setCell(5, 1, Piece.TOP_QUARK_R);
        board.setCell(5, 2, Piece.BOTTOM_QUARK_R);

        // Detect near the gluon — should form pion+ from gluon(5,0) + top(5,1)
        detectNearCell(board, 5, 0);

        // After pion+ consumes (5,0) and (5,1), the bottom quark at (5,2) should drop
        // It should now be at row 0 (the lowest available position)
        assertNotNull(board.getCell(5, 0), "Remaining piece should drop down");
        assertEquals(Piece.BOTTOM_QUARK_R, board.getCell(5, 0));
        assertNull(board.getCell(5, 2), "Original position should be empty after gravity");
    }

    // ==================== HELPER ====================

    /**
     * Helper: runs detect simulating a piece placed near the given cell.
     * Uses a single-cell "fake" placement to trigger area scanning.
     */
    private List<Hadron> detectNearCell(Board board, int col, int row) {
        // Use the piece at the given cell for detection context
        Piece p = board.getCell(col, row);
        if (p == null) p = Piece.GLUON; // fallback

        // Create a minimal placement context that includes this cell
        return detector.detect(board, p, 0, col, row);
    }
}
