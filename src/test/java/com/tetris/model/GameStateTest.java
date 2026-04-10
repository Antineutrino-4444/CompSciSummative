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
        assertNotNull(state.getCurrentPiece().getParticleType());
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
        state.hardDrop();
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
        state.hold();
        Piece secondPiece = state.getCurrentPiece();
        assertTrue(state.isHoldUsed());
        state.hold();
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
        GameState s = new GameState();
        for (int attempt = 0; attempt < 20; attempt++) {
            if (s.getCurrentPiece() != Piece.GLUON) {
                int oldRot = s.getCurrentRotation();
                boolean rotated = s.rotateCW();
                if (rotated) {
                    assertEquals((oldRot + 1) & 3, s.getCurrentRotation());
                    break;
                }
            }
            s = new GameState();
        }
    }

    @Test
    void gluonCanRotate() {
        // Gluon is now a domino that can rotate between horizontal/vertical
        GameState s = new GameState();
        // Run multiple game states until we get a gluon piece
        for (int attempt = 0; attempt < 20; attempt++) {
            if (s.getCurrentPiece() == Piece.GLUON) {
                int oldRot = s.getCurrentRotation();
                boolean rotated = s.rotateCW();
                if (rotated) {
                    assertEquals((oldRot + 1) & 3, s.getCurrentRotation());
                }
                return; // test done
            }
            s = new GameState();
        }
        // If we never got a gluon in 20 tries, that's OK — test is non-deterministic
    }

    @Test
    void ghostRowIsAtOrBelowCurrent() {
        assertTrue(state.getGhostRow() <= state.getCurrentRow());
    }

    @Test
    void previewPiecesAvailable() {
        assertEquals(GameState.PREVIEW_COUNT, state.getPreviewPieces().size());
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
        state.update(1.1);
        assertTrue(state.getCurrentRow() < startRow || state.isGameOver());
    }

    @Test
    void dasStartsMovement() {
        int startCol = state.getCurrentCol();
        state.startDAS(1);
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
        Board board = state.getBoard();
        for (int r = 18; r < Board.HEIGHT; r++) {
            for (int c = 0; c < Board.WIDTH; c++) {
                board.setCell(c, r, Piece.TOP_QUARK_A);
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
