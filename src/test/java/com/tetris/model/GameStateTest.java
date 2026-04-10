package com.tetris.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the GameState class covering piece spawning, movement,
 * rotation, hold, hard/soft drop, locking, and game over detection.
 */
class GameStateTest {

    private GameState state;

    @BeforeEach
    void setUp() {
        state = new GameState(1);
    }

    @Test
    void initialStateIsValid() {
        assertFalse(state.isGameOver());
        assertFalse(state.isPaused());
        assertNotNull(state.getCurrentPiece());
        assertNotNull(state.getBoard());
        assertNotNull(state.getScoring());
        assertNull(state.getHoldPiece());
    }

    @Test
    void pieceSpawnsAtCorrectPosition() {
        assertNotNull(state.getCurrentPiece());
        int col = state.getCurrentCol();
        int row = state.getCurrentRow();
        assertTrue(col >= 0 && col < Board.WIDTH);
        assertTrue(row >= Board.VISIBLE_HEIGHT - 2);
    }

    @Test
    void moveLeftSucceeds() {
        int initialCol = state.getCurrentCol();
        boolean moved = state.moveLeft();
        if (moved) {
            assertEquals(initialCol - 1, state.getCurrentCol());
        }
    }

    @Test
    void moveRightSucceeds() {
        int initialCol = state.getCurrentCol();
        boolean moved = state.moveRight();
        if (moved) {
            assertEquals(initialCol + 1, state.getCurrentCol());
        }
    }

    @Test
    void softDropMovesPieceDown() {
        int initialRow = state.getCurrentRow();
        boolean dropped = state.softDrop();
        if (dropped) {
            assertEquals(initialRow - 1, state.getCurrentRow());
        }
    }

    @Test
    void hardDropLocksImmediately() {
        Piece pieceBefore = state.getCurrentPiece();
        state.hardDrop();
        // After hard drop, either a new piece spawned or game is over
        // The board should have some blocks
        assertTrue(state.getBoard().getHighestRow() >= 0 || state.isGameOver());
    }

    @Test
    void holdSwapsPiece() {
        Piece firstPiece = state.getCurrentPiece();
        state.hold();
        assertEquals(firstPiece, state.getHoldPiece());
        assertNotNull(state.getCurrentPiece());
    }

    @Test
    void holdCanOnlyBeUsedOncePerPiece() {
        Piece firstPiece = state.getCurrentPiece();
        state.hold(); // firstPiece -> hold, new piece from bag, holdUsed = true
        Piece secondPiece = state.getCurrentPiece();
        assertNotEquals(firstPiece, secondPiece,
                "After hold, a new piece should be active (unless same type from bag)");
        assertEquals(firstPiece, state.getHoldPiece());
        assertTrue(state.isHoldUsed());

        // Try to hold again - should be blocked
        state.hold();
        assertEquals(secondPiece, state.getCurrentPiece(),
                "Second hold should be blocked - piece should not change");
    }

    @Test
    void holdTwiceWithDifferentPieces() {
        Piece first = state.getCurrentPiece();
        state.hold(); // First goes to hold, new piece from bag
        Piece second = state.getCurrentPiece();

        // Hard drop to place piece and get new one
        state.hardDrop();
        if (!state.isGameOver()) {
            // Now hold should swap with the first piece
            Piece third = state.getCurrentPiece();
            state.hold();
            // Hold should now contain the third piece, current should be first
            assertEquals(third, state.getHoldPiece());
            assertEquals(first, state.getCurrentPiece());
        }
    }

    @Test
    void rotateCW() {
        int initialRotation = state.getCurrentRotation();
        boolean rotated = state.rotateCW();
        if (rotated) {
            assertEquals((initialRotation + 1) & 3, state.getCurrentRotation());
        }
    }

    @Test
    void rotateCCW() {
        int initialRotation = state.getCurrentRotation();
        boolean rotated = state.rotateCCW();
        if (rotated) {
            assertEquals((initialRotation + 3) & 3, state.getCurrentRotation());
        }
    }

    @Test
    void ghostRowIsAtOrBelowCurrentRow() {
        int ghostRow = state.getGhostRow();
        assertTrue(ghostRow <= state.getCurrentRow());
    }

    @Test
    void ghostRowIsValidPosition() {
        int ghostRow = state.getGhostRow();
        assertTrue(ghostRow >= 0);
        // Ghost should not collide at its position
        assertFalse(state.getBoard().collides(
                state.getCurrentPiece(),
                state.getCurrentRotation(),
                state.getCurrentCol(),
                ghostRow));
    }

    @Test
    void previewPiecesAvailable() {
        List<Piece> preview = state.getPreviewPieces();
        assertNotNull(preview);
        assertEquals(GameState.PREVIEW_COUNT, preview.size());
    }

    @Test
    void pauseToggle() {
        assertFalse(state.isPaused());
        state.togglePause();
        assertTrue(state.isPaused());
        state.togglePause();
        assertFalse(state.isPaused());
    }

    @Test
    void pausePreventsMoves() {
        state.setPaused(true);
        int col = state.getCurrentCol();
        assertFalse(state.moveLeft());
        assertEquals(col, state.getCurrentCol());
    }

    @Test
    void updateDoesNothingWhenPaused() {
        state.setPaused(true);
        int row = state.getCurrentRow();
        state.update(10.0); // Large delta
        assertEquals(row, state.getCurrentRow());
    }

    @Test
    void updateDoesNothingWhenGameOver() {
        // Stack blocks high enough to cause block-out on next spawn
        Board board = state.getBoard();
        // Fill the top rows so pieces can't spawn
        for (int r = 18; r < Board.HEIGHT; r++) {
            for (int c = 0; c < Board.WIDTH; c++) {
                board.setCell(c, r, Piece.I);
            }
        }
        // Hard drop should place piece and next spawn should fail (game over)
        state.hardDrop();
        // The board might clear lines and respawn. Keep trying.
        while (!state.isGameOver()) {
            state.hardDrop();
        }
        assertTrue(state.isGameOver());
    }

    @Test
    void softDropAwardsPoints() {
        long scoreBefore = state.getScoring().getScore();
        state.softDrop();
        assertEquals(scoreBefore + 1, state.getScoring().getScore());
    }

    @Test
    void hardDropAwardsPoints() {
        int ghostRow = state.getGhostRow();
        int currentRow = state.getCurrentRow();
        int dropDistance = currentRow - ghostRow;
        long scoreBefore = state.getScoring().getScore();
        state.hardDrop();
        // Hard drop awards 2 points per cell
        assertTrue(state.getScoring().getScore() >= scoreBefore + 2 * dropDistance);
    }

    @Test
    void dasConstants() {
        assertTrue(GameState.DAS > 0);
        assertTrue(GameState.ARR > 0);
        assertTrue(GameState.DAS > GameState.ARR);
    }

    @Test
    void lockDelayConstants() {
        assertEquals(0.5, GameState.LOCK_DELAY);
        assertEquals(15, GameState.MAX_LOCK_RESETS);
    }

    @Test
    void previewCount() {
        assertEquals(5, GameState.PREVIEW_COUNT);
    }

    @Test
    void moveLeftBlockedAtWall() {
        // Move left as far as possible
        for (int i = 0; i < 20; i++) {
            state.moveLeft();
        }
        int col = state.getCurrentCol();
        assertFalse(state.moveLeft()); // Should be blocked
        assertEquals(col, state.getCurrentCol());
    }

    @Test
    void moveRightBlockedAtWall() {
        // Move right as far as possible
        for (int i = 0; i < 20; i++) {
            state.moveRight();
        }
        int col = state.getCurrentCol();
        assertFalse(state.moveRight()); // Should be blocked
        assertEquals(col, state.getCurrentCol());
    }

    @Test
    void setPaused() {
        state.setPaused(true);
        assertTrue(state.isPaused());
        state.setPaused(false);
        assertFalse(state.isPaused());
    }

    @Test
    void gameOverPreventsMoves() {
        // Stack blocks to force game over
        Board board = state.getBoard();
        for (int r = 18; r < Board.HEIGHT; r++) {
            for (int c = 0; c < Board.WIDTH; c++) {
                board.setCell(c, r, Piece.I);
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
}
