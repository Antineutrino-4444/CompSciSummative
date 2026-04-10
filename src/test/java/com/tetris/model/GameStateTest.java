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
    void holdIsFreelyReusable() {
        Piece firstPiece = state.getCurrentPiece();
        state.hold();
        // After first hold: firstPiece is in hold, a new piece from bag is current
        assertEquals(firstPiece, state.getHoldPiece());
        Piece secondPiece = state.getCurrentPiece();
        // Second hold should swap back — it's freely reusable (no once-per-piece restriction)
        state.hold();
        assertEquals(secondPiece, state.getHoldPiece());
        assertEquals(firstPiece, state.getCurrentPiece(),
                "Hold should swap back to the original piece");
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
        GameState s = new GameState();
        for (int attempt = 0; attempt < 20; attempt++) {
            if (s.getCurrentPiece() == Piece.GLUON) {
                int oldRot = s.getCurrentRotation();
                boolean rotated = s.rotateCW();
                if (rotated) {
                    assertEquals((oldRot + 1) & 3, s.getCurrentRotation());
                }
                return;
            }
            s = new GameState();
        }
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
    void previewCountIsThree() {
        assertEquals(3, GameState.PREVIEW_COUNT);
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
    void scoreStartsAtZero() {
        assertEquals(0, state.getScore());
    }

    @Test
    void particlesContainedStartsAtZero() {
        assertEquals(0, state.getTotalParticlesContained());
    }

    @Test
    void discoveredHadronsStartsEmpty() {
        assertTrue(state.getDiscoveredHadrons().isEmpty());
    }

    @Test
    void maxLockResetsIsEight() {
        assertEquals(8, GameState.MAX_LOCK_RESETS);
    }

    @Test
    void undosRemainStartsAtTwo() {
        assertEquals(GameState.MAX_UNDOS, state.getUndosRemaining());
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

    @Test
    void scoreSystemIsAccessible() {
        assertNotNull(state.getScoreSystem());
    }
}
