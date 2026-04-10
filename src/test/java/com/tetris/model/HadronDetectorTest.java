package com.tetris.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HadronDetector — gluon-bridge particle combination mechanic.
 */
class HadronDetectorTest {

    private Board board;
    private HadronDetector detector;

    @BeforeEach
    void setUp() {
        board = new Board();
        detector = new HadronDetector();
    }

    // ==================== PION TESTS (simplest: 1 top + 1 bottom + 1 gluon) ====================

    @Test
    void detectsPionWhenQuarksLinkedByGluon() {
        // top_quark - gluon - bottom_quark (in a line)
        board.setCell(3, 0, Piece.TOP_QUARK_A);
        board.setCell(4, 0, Piece.GLUON);
        board.setCell(5, 0, Piece.BOTTOM_QUARK_A);

        List<Hadron> hadrons = detectHadronsNearCell(board, 4, 0);
        assertTrue(hadrons.stream().anyMatch(h -> h == Hadron.PION),
                "Should detect a pion from top quark + gluon + bottom quark");
    }

    @Test
    void noPionWithoutGluonBridge() {
        // Two quarks adjacent but NO gluon bridge → no hadron
        board.setCell(3, 0, Piece.TOP_QUARK_A);
        board.setCell(4, 0, Piece.BOTTOM_QUARK_A);

        List<Hadron> hadrons = detectHadronsNearCell(board, 4, 0);
        assertTrue(hadrons.isEmpty(),
                "Quarks touching without gluon should NOT form a pion");
    }

    @Test
    void noPionFromTwoSameQuarksAndGluon() {
        // 2 top quarks + 1 gluon → pion needs 1 top + 1 bottom
        board.setCell(3, 0, Piece.TOP_QUARK_A);
        board.setCell(4, 0, Piece.GLUON);
        board.setCell(5, 0, Piece.TOP_QUARK_B);

        List<Hadron> hadrons = detectHadronsNearCell(board, 4, 0);
        assertTrue(hadrons.stream().noneMatch(h -> h == Hadron.PION),
                "2 top quarks + gluon should NOT form a pion");
    }

    @Test
    void pionWithVerticalArrangement() {
        // Vertical: top at (5,0), gluon at (5,1), bottom at (5,2)
        board.setCell(5, 0, Piece.TOP_QUARK_A);
        board.setCell(5, 1, Piece.GLUON);
        board.setCell(5, 2, Piece.BOTTOM_QUARK_B);

        List<Hadron> hadrons = detectHadronsNearCell(board, 5, 1);
        assertTrue(hadrons.stream().anyMatch(h -> h == Hadron.PION),
                "Vertical quark-gluon-quark should form a pion");
    }

    // ==================== PROTON TESTS (2 top + 1 bottom + 2 gluons) ====================

    @Test
    void detectsProtonWithGluonBridges() {
        // Layout: quarks around a connected gluon pair
        //   top(3,1)   top(4,1)
        //   gluon(3,0) gluon(4,0)
        //              bottom(5,0)
        board.setCell(3, 0, Piece.GLUON);
        board.setCell(4, 0, Piece.GLUON);
        board.setCell(3, 1, Piece.TOP_QUARK_A);
        board.setCell(4, 1, Piece.TOP_QUARK_B);
        board.setCell(5, 0, Piece.BOTTOM_QUARK_A);

        List<Hadron> hadrons = detectHadronsNearCell(board, 3, 0);
        assertTrue(hadrons.stream().anyMatch(h -> h == Hadron.PROTON),
                "Should detect proton from 2 top + 1 bottom + 2 connected gluons");
    }

    @Test
    void noProtonWithoutEnoughGluons() {
        // 2 top + 1 bottom + only 1 gluon → not enough for proton
        board.setCell(3, 0, Piece.TOP_QUARK_A);
        board.setCell(4, 0, Piece.GLUON);
        board.setCell(5, 0, Piece.BOTTOM_QUARK_A);
        board.setCell(6, 0, Piece.TOP_QUARK_B);

        List<Hadron> hadrons = detectHadronsNearCell(board, 4, 0);
        // Should form a pion (1t+1b+1g) but NOT a proton
        assertTrue(hadrons.stream().noneMatch(h -> h == Hadron.PROTON),
                "Should not form proton with only 1 gluon");
    }

    @Test
    void noProtonWithoutGluons() {
        // 2 top + 1 bottom adjacent, NO gluons at all
        board.setCell(3, 0, Piece.TOP_QUARK_A);
        board.setCell(4, 0, Piece.TOP_QUARK_B);
        board.setCell(5, 0, Piece.BOTTOM_QUARK_A);

        List<Hadron> hadrons = detectHadronsNearCell(board, 4, 0);
        assertTrue(hadrons.isEmpty(),
                "Quarks without gluon bridges should not form any hadron");
    }

    // ==================== NEUTRON TESTS (1 top + 2 bottom + 2 gluons) ====================

    @Test
    void detectsNeutronWithGluonBridges() {
        // Layout: quarks around a connected gluon pair
        //   bottom(3,1)  top(4,1)
        //   gluon(3,0)   gluon(4,0)
        //                bottom(5,0)
        board.setCell(3, 0, Piece.GLUON);
        board.setCell(4, 0, Piece.GLUON);
        board.setCell(3, 1, Piece.BOTTOM_QUARK_A);
        board.setCell(4, 1, Piece.TOP_QUARK_A);
        board.setCell(5, 0, Piece.BOTTOM_QUARK_B);

        List<Hadron> hadrons = detectHadronsNearCell(board, 3, 0);
        assertTrue(hadrons.stream().anyMatch(h -> h == Hadron.NEUTRON),
                "Should detect neutron from 1 top + 2 bottom + 2 connected gluons");
    }

    @Test
    void noNeutronWithOnly1Gluon() {
        // 1 top + 2 bottom + 1 gluon → pion possible, not neutron
        board.setCell(3, 0, Piece.BOTTOM_QUARK_A);
        board.setCell(4, 0, Piece.GLUON);
        board.setCell(5, 0, Piece.TOP_QUARK_A);
        board.setCell(6, 0, Piece.BOTTOM_QUARK_B);

        List<Hadron> hadrons = detectHadronsNearCell(board, 4, 0);
        assertTrue(hadrons.stream().noneMatch(h -> h == Hadron.NEUTRON),
                "Should not form neutron with only 1 gluon");
    }

    // ==================== DISCONNECTED TESTS ====================

    @Test
    void noHadronFromDisconnectedPieces() {
        board.setCell(0, 0, Piece.TOP_QUARK_A);
        board.setCell(9, 0, Piece.GLUON);

        List<Hadron> hadrons = detectHadronsNearCell(board, 9, 0);
        assertTrue(hadrons.isEmpty(),
                "Disconnected pieces should not form hadrons");
    }

    @Test
    void noHadronFromGluonOnly() {
        // Gluons alone don't form anything
        board.setCell(4, 0, Piece.GLUON);
        board.setCell(5, 0, Piece.GLUON);

        List<Hadron> hadrons = detectHadronsNearCell(board, 4, 0);
        assertTrue(hadrons.isEmpty(),
                "Gluons alone should not form hadrons");
    }

    // ==================== CELL CONSUMPTION TESTS ====================

    @Test
    void consumedCellsAreCleared() {
        board.setCell(3, 0, Piece.TOP_QUARK_A);
        board.setCell(4, 0, Piece.GLUON);
        board.setCell(5, 0, Piece.BOTTOM_QUARK_A);

        detectHadronsNearCell(board, 4, 0);

        assertNull(board.getCell(3, 0), "Top quark cell should be consumed");
        assertNull(board.getCell(4, 0), "Gluon cell should be consumed");
        assertNull(board.getCell(5, 0), "Bottom quark cell should be consumed");
    }

    @Test
    void gravityAppliesAfterConsumption() {
        // Layout: gluon pair with quarks, plus an uninvolved piece above
        // Row 0: TOP_QUARK_A(3,0), GLUON(4,0), BOTTOM_QUARK_A(5,0)
        // Row 1: unrelated piece at (3,1)
        board.setCell(3, 0, Piece.TOP_QUARK_A);
        board.setCell(4, 0, Piece.GLUON);
        board.setCell(5, 0, Piece.BOTTOM_QUARK_A);
        board.setCell(3, 1, Piece.BOTTOM_QUARK_B); // sitting above top quark

        detectHadronsNearCell(board, 4, 0);

        // After pion consumes row 0 cells under (3,0), BOTTOM_QUARK_B at (3,1) should drop to (3,0)
        assertEquals(Piece.BOTTOM_QUARK_B, board.getCell(3, 0),
                "Remaining piece should drop down after gravity");
        assertNull(board.getCell(3, 1),
                "Original position should be empty after gravity");
    }

    // ==================== GLUON-BRIDGE SPECIFIC TESTS ====================

    @Test
    void quarksLinkedThroughMultipleGluons() {
        // top - gluon - gluon - bottom → gluon chain connects them
        board.setCell(2, 0, Piece.TOP_QUARK_A);
        board.setCell(3, 0, Piece.GLUON);
        board.setCell(4, 0, Piece.GLUON);
        board.setCell(5, 0, Piece.BOTTOM_QUARK_A);

        List<Hadron> hadrons = detectHadronsNearCell(board, 3, 0);
        assertTrue(hadrons.stream().anyMatch(h -> h == Hadron.PION),
                "Quarks linked through gluon chain should form a pion");
    }

    @Test
    void lShapedGluonBridge() {
        // Gluon at (4,0) and (4,1) forming an L
        // Top quark at (3,0) touches gluon at (4,0)
        // Bottom quark at (5,1) touches gluon at (4,1)
        board.setCell(3, 0, Piece.TOP_QUARK_A);
        board.setCell(4, 0, Piece.GLUON);
        board.setCell(4, 1, Piece.GLUON);
        board.setCell(5, 1, Piece.BOTTOM_QUARK_A);

        List<Hadron> hadrons = detectHadronsNearCell(board, 4, 0);
        assertTrue(hadrons.stream().anyMatch(h -> h == Hadron.PION),
                "L-shaped gluon bridge should connect quarks for pion");
    }

    // ==================== PIECE-DROP QUARK CHAIN TESTS ====================
    // These tests simulate dropping a multi-cell quark piece onto a gluon network.
    // Phase 3 extends through the placed piece's cells only.

    @Test
    void neutronWhenDroppingBottomQuarkPieceOntoGluonNetwork() {
        // Pre-existing: 2 gluons + 1 top quark
        board.setCell(3, 0, Piece.GLUON);
        board.setCell(4, 0, Piece.GLUON);
        board.setCell(3, 1, Piece.TOP_QUARK_A);

        // Simulate dropping BOTTOM_QUARK_B (line-tromino: {0,0},{1,0},{2,0}) at col=5, row=0
        // Board cells: (5,0), (6,0), (7,0) — cell (5,0) is adjacent to gluon(4,0)
        board.setCell(5, 0, Piece.BOTTOM_QUARK_B);
        board.setCell(6, 0, Piece.BOTTOM_QUARK_B);
        board.setCell(7, 0, Piece.BOTTOM_QUARK_B);

        List<Hadron> hadrons = detectHadrons(board, Piece.BOTTOM_QUARK_B, 0, 5, 0);
        assertTrue(hadrons.stream().anyMatch(h -> h == Hadron.NEUTRON),
                "Dropping bottom quark piece near gluon network should form neutron");
    }

    @Test
    void protonWhenDroppingTopQuarkPieceOntoGluonNetwork() {
        // Pre-existing: 2 gluons + 1 bottom quark
        board.setCell(3, 0, Piece.GLUON);
        board.setCell(4, 0, Piece.GLUON);
        board.setCell(3, 1, Piece.BOTTOM_QUARK_A);

        // Simulate dropping TOP_QUARK_B (line-tromino: {0,0},{1,0},{2,0}) at col=5, row=0
        board.setCell(5, 0, Piece.TOP_QUARK_B);
        board.setCell(6, 0, Piece.TOP_QUARK_B);
        board.setCell(7, 0, Piece.TOP_QUARK_B);

        List<Hadron> hadrons = detectHadrons(board, Piece.TOP_QUARK_B, 0, 5, 0);
        assertTrue(hadrons.stream().anyMatch(h -> h == Hadron.PROTON),
                "Dropping top quark piece near gluon network should form proton");
    }

    @Test
    void previouslyPlacedQuarksDontChainThroughQuarkAdjacency() {
        // Pre-existing: 2 gluons + 1 top quark + 2 separate bottom quarks
        // that happen to be adjacent to each other but NOT part of the placed piece.
        // Should NOT form neutron — only quarks adjacent to gluons participate.
        board.setCell(3, 0, Piece.GLUON);
        board.setCell(4, 0, Piece.GLUON);
        board.setCell(3, 1, Piece.TOP_QUARK_A);
        board.setCell(5, 0, Piece.BOTTOM_QUARK_A);  // adjacent to gluon(4,0)
        board.setCell(6, 0, Piece.BOTTOM_QUARK_A);  // adjacent to bottom(5,0) only

        // Place a gluon piece — so placed piece is the gluon, not the quarks
        List<Hadron> hadrons = detectHadronsNearCell(board, 3, 0);
        // Only bottom(5,0) should be included (adjacent to gluon), not bottom(6,0)
        // Group: 2g + 1t + 1b → pion, NOT neutron
        assertTrue(hadrons.stream().noneMatch(h -> h == Hadron.NEUTRON),
                "Previously placed quarks should not chain through quark adjacency");
        assertTrue(hadrons.stream().anyMatch(h -> h == Hadron.PION),
                "Should still form pion from directly gluon-adjacent quarks");
    }

    @Test
    void isolatedSingleQuarkFormsPion() {
        // To purposefully form a pion, attach exactly 1 quark to the gluon network
        board.setCell(3, 0, Piece.GLUON);
        board.setCell(3, 1, Piece.TOP_QUARK_A);
        board.setCell(4, 0, Piece.BOTTOM_QUARK_A);

        List<Hadron> hadrons = detectHadronsNearCell(board, 3, 0);
        assertTrue(hadrons.stream().anyMatch(h -> h == Hadron.PION),
                "Isolated single quark should form pion");
        assertTrue(hadrons.stream().noneMatch(h -> h == Hadron.NEUTRON),
                "Single bottom quark should not form neutron");
    }

    // ==================== FORMATION CELL TRACKING TESTS ====================

    @Test
    void formationContainsConsumedCellPositions() {
        board.setCell(3, 0, Piece.TOP_QUARK_A);
        board.setCell(4, 0, Piece.GLUON);
        board.setCell(5, 0, Piece.BOTTOM_QUARK_A);

        List<HadronFormation> formations = detectNearCell(board, 4, 0);
        assertEquals(1, formations.size(), "Should have exactly one formation");

        HadronFormation f = formations.get(0);
        assertEquals(Hadron.PION, f.getHadron());
        assertEquals(3, f.getConsumedCells().size(), "Pion should consume 3 cells");
    }

    // ==================== HELPERS ====================

    /** Detect near a cell using the cell's piece as the "placed piece". */
    private List<HadronFormation> detectNearCell(Board board, int col, int row) {
        Piece p = board.getCell(col, row);
        if (p == null) p = Piece.GLUON;
        return detector.detect(board, p, 0, col, row);
    }

    /** Convenience: detect near cell and extract just the hadron types. */
    private List<Hadron> detectHadronsNearCell(Board board, int col, int row) {
        return detectNearCell(board, col, row).stream()
                .map(HadronFormation::getHadron).toList();
    }

    /** Detect with explicit piece placement info. */
    private List<Hadron> detectHadrons(Board board, Piece piece, int rotation, int col, int row) {
        return detector.detect(board, piece, rotation, col, row).stream()
                .map(HadronFormation::getHadron).toList();
    }
}
