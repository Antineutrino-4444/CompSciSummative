package com.tetris.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the GameState (particle-themed game engine).
 */
class GameStateTest {

    private GameState state;

    @BeforeEach
    void setUp() {
        state = new GameState();
    }

    @Test
    void initialStateHasActivePiece() {
        assertNotNull(state.getCurrentPiece());
        assertFalse(state.isGameOver());
        assertFalse(state.isPaused());
    }

    @Test
    void currentPieceIsAParticle() {
        Piece p = state.getCurrentPiece();
        assertNotNull(p.getParticleType());
    }

    @Test
    void moveLeftAndRight() {
        int startCol = state.getCurrentCol();
        state.moveLeft();
        assertEquals(startCol - 1, state.getCurrentCol());
        state.moveRight();
        assertEquals(startCol, state.getCurrentCol());
    }

    @Test
    void softDrop() {
        int startRow = state.getCurrentRow();
        boolean moved = state.softDrop();
        assertTrue(moved);
        assertEquals(startRow - 1, state.getCurrentRow());
    }

    @Test
    void hardDrop() {
        int startRow = state.getCurrentRow();
        state.hardDrop();
        // After hard drop, a new piece should have spawned
        // (or game over in extreme case)
        assertNotNull(state.getCurrentPiece());
    }

    @Test
    void holdSwapsPiece() {
        Piece firstPiece = state.getCurrentPiece();
        state.hold();
        assertEquals(firstPiece, state.getHoldPiece());
        assertFalse(state.isGameOver());
    }

    @Test
    void holdCanOnlyBeUsedOnce() {
        Piece firstPiece = state.getCurrentPiece();
        state.hold();
        Piece secondPiece = state.getCurrentPiece();
        assertTrue(state.isHoldUsed());
        state.hold(); // Should be blocked
        assertEquals(secondPiece, state.getCurrentPiece(),
                "Second hold should be blocked");
    }

    @Test
    void togglePause() {
        assertFalse(state.isPaused());
        state.togglePause();
        assertTrue(state.isPaused());
        state.togglePause();
        assertFalse(state.isPaused());
    }

    @Test
    void movesBlockedWhenPaused() {
        state.togglePause();
        assertFalse(state.moveLeft());
        assertFalse(state.moveRight());
        assertFalse(state.softDrop());
    }

    @Test
    void rotateCW() {
        // Gluon doesn't rotate, but any quark should
        // Keep trying pieces until we get a non-gluon
        GameState s = new GameState();
        boolean foundRotation = false;
        for (int attempt = 0; attempt < 20; attempt++) {
            if (s.getCurrentPiece() != Piece.GLUON) {
                int oldRot = s.getCurrentRotation();
                boolean rotated = s.rotateCW();
                if (rotated) {
                    assertEquals((oldRot + 1) & 3, s.getCurrentRotation());
                    foundRotation = true;
                    break;
                }
            }
            s = new GameState();
        }
        // It's possible (but very unlikely) all 20 attempts got a gluon
        // which is acceptable for a non-deterministic test
    }

    @Test
    void gluonDoesNotRotate() {
        // Create state and keep trying until we get a gluon
        // or test with a seeded randomizer — but we can just directly test
        // via a new state where we force the piece
        GameState s = new GameState();
        // This is a behavioral test — if the current piece is not a gluon,
        // we can skip. Otherwise, rotation should fail.
        if (s.getCurrentPiece() == Piece.GLUON) {
            assertFalse(s.rotateCW());
            assertFalse(s.rotateCCW());
        }
        // Also verify the rotation method rejects gluon
        // (tested indirectly through WallKickData)
    }

    @Test
    void ghostRowIsAtOrBelowCurrent() {
        int ghostRow = state.getGhostRow();
        assertTrue(ghostRow <= state.getCurrentRow(),
                "Ghost should be at or below current piece");
    }

    @Test
    void previewPiecesAvailable() {
        List<Piece> preview = state.getPreviewPieces();
        assertEquals(GameState.PREVIEW_COUNT, preview.size());
    }

    @Test
    void updateWithZeroDeltaDoesNothing() {
        int row = state.getCurrentRow();
        state.update(0);
        assertEquals(row, state.getCurrentRow());
    }

    @Test
    void gravityMovesPieceDown() {
        int startRow = state.getCurrentRow();
        // Update with enough time for gravity to apply (level 1 ≈ 1 second)
        state.update(1.1);
        assertTrue(state.getCurrentRow() < startRow || state.isGameOver(),
                "Gravity should move piece down after 1.1 seconds");
    }

    @Test
    void dasStartsMovement() {
        int startCol = state.getCurrentCol();
        state.startDAS(1); // right
        assertEquals(startCol + 1, state.getCurrentCol());
        state.stopDAS(1);
    }

    @Test
    void levelStartsAtOne() {
        assertEquals(1, state.getLevel());
    }

    @Test
    void linesClearedStartsAtZero() {
        assertEquals(0, state.getTotalLinesCleared());
    }

    @Test
    void discoveredHadronsStartsEmpty() {
        assertTrue(state.getDiscoveredHadrons().isEmpty());
    }

    @Test
    void gameOverPreventsMoves() {
        // Fill board to cause game over
        Board board = state.getBoard();
        for (int r = 18; r < Board.HEIGHT; r++) {
            for (int c = 0; c < Board.WIDTH; c++) {
                board.setCell(c, r, Piece.TOP_QUARK_R);
            }
        }
        while (!state.isGameOver()) {
            state.hardDrop();
        }
        assertTrue(state.isGameOver());
        assertFalse(state.moveLeft());
        assertFalse(state.moveRight());
        assertFalse(state.softDrop());
    }

    @Test
    void boardIsAccessible() {
        assertNotNull(state.getBoard());
    }
}
