package com.tetris.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Board class.
 */
class BoardTest {

    private Board board;

    @BeforeEach
    void setUp() {
        board = new Board();
    }

    @Test
    void newBoardIsEmpty() {
        for (int r = 0; r < Board.HEIGHT; r++) {
            for (int c = 0; c < Board.WIDTH; c++) {
                assertNull(board.getCell(c, r));
                assertTrue(board.isEmpty(c, r));
            }
        }
    }

    @Test
    void setAndGetCell() {
        board.setCell(5, 3, Piece.TOP_QUARK_R);
        assertEquals(Piece.TOP_QUARK_R, board.getCell(5, 3));
        assertFalse(board.isEmpty(5, 3));
    }

    @Test
    void clearCell() {
        board.setCell(5, 3, Piece.GLUON);
        board.setCell(5, 3, null);
        assertNull(board.getCell(5, 3));
        assertTrue(board.isEmpty(5, 3));
    }

    @Test
    void outOfBoundsGetReturnsNull() {
        assertNull(board.getCell(-1, 0));
        assertNull(board.getCell(Board.WIDTH, 0));
        assertNull(board.getCell(0, -1));
        assertNull(board.getCell(0, Board.HEIGHT));
    }

    @Test
    void outOfBoundsIsNotEmpty() {
        assertFalse(board.isEmpty(-1, 0));
        assertFalse(board.isEmpty(Board.WIDTH, 0));
    }

    @Test
    void collidesWithWalls() {
        assertTrue(board.collides(Piece.TOP_QUARK_R, 0, -2, 5));
        assertTrue(board.collides(Piece.TOP_QUARK_R, 0, Board.WIDTH, 5));
    }

    @Test
    void collidesWithExistingBlocks() {
        board.setCell(4, 18, Piece.GLUON);
        // TOP_QUARK_R (T-shape) at rotation 0: cells at {1,0},{0,1},{1,1},{2,1}
        // At col=3, row=19: cell (3+1, 19-0)=(4,19) and (3+0, 19-1)=(3,18), etc.
        // cell (3+1, 19-1) = (4, 18) → occupied!
        assertTrue(board.collides(Piece.TOP_QUARK_R, 0, 3, 19));
    }

    @Test
    void noCollisionOnEmptyBoard() {
        assertFalse(board.collides(Piece.TOP_QUARK_R, 0, 3, 19));
    }

    @Test
    void placePiece() {
        board.placePiece(Piece.GLUON, 0, 4, 1);
        // GLUON at rot 0: {1,0},{2,0},{1,1},{2,1} → cells at (5,1),(6,1),(5,0),(6,0)
        assertEquals(Piece.GLUON, board.getCell(5, 1));
        assertEquals(Piece.GLUON, board.getCell(6, 1));
        assertEquals(Piece.GLUON, board.getCell(5, 0));
        assertEquals(Piece.GLUON, board.getCell(6, 0));
    }

    @Test
    void clearFullLine() {
        // Fill row 0 completely
        for (int c = 0; c < Board.WIDTH; c++) {
            board.setCell(c, 0, Piece.TOP_QUARK_R);
        }
        int cleared = board.clearLines();
        assertEquals(1, cleared);

        // Row should now be empty
        for (int c = 0; c < Board.WIDTH; c++) {
            assertNull(board.getCell(c, 0));
        }
    }

    @Test
    void clearMultipleLines() {
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < Board.WIDTH; c++) {
                board.setCell(c, r, Piece.BOTTOM_QUARK_R);
            }
        }
        int cleared = board.clearLines();
        assertEquals(4, cleared);
    }

    @Test
    void partialLineNotCleared() {
        for (int c = 0; c < Board.WIDTH - 1; c++) {
            board.setCell(c, 0, Piece.GLUON);
        }
        int cleared = board.clearLines();
        assertEquals(0, cleared);
    }

    @Test
    void linesAboveDrop() {
        // Fill row 0 and place a block on row 1
        for (int c = 0; c < Board.WIDTH; c++) {
            board.setCell(c, 0, Piece.TOP_QUARK_R);
        }
        board.setCell(3, 1, Piece.BOTTOM_QUARK_G);

        board.clearLines();

        // The block that was on row 1 should now be on row 0
        assertEquals(Piece.BOTTOM_QUARK_G, board.getCell(3, 0));
    }

    @Test
    void isPerfectClear() {
        assertTrue(board.isPerfectClear());
        board.setCell(0, 0, Piece.GLUON);
        assertFalse(board.isPerfectClear());
    }

    @Test
    void hasBlocksAboveVisible() {
        assertFalse(board.hasBlocksAboveVisible());
        board.setCell(5, Board.VISIBLE_HEIGHT, Piece.TOP_QUARK_R);
        assertTrue(board.hasBlocksAboveVisible());
    }

    @Test
    void getHighestRow() {
        assertEquals(-1, board.getHighestRow());
        board.setCell(5, 10, Piece.GLUON);
        assertEquals(10, board.getHighestRow());
    }

    @Test
    void copy() {
        board.setCell(3, 3, Piece.TOP_QUARK_R);
        Board copy = board.copy();
        assertEquals(Piece.TOP_QUARK_R, copy.getCell(3, 3));
        // Modify copy, original unchanged
        copy.setCell(3, 3, null);
        assertEquals(Piece.TOP_QUARK_R, board.getCell(3, 3));
    }

    @Test
    void boardDimensions() {
        assertEquals(10, Board.WIDTH);
        assertEquals(40, Board.HEIGHT);
        assertEquals(20, Board.VISIBLE_HEIGHT);
    }
}
