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
        board.setCell(5, 3, Piece.TOP_QUARK_A);
        assertEquals(Piece.TOP_QUARK_A, board.getCell(5, 3));
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
        assertTrue(board.collides(Piece.TOP_QUARK_A, 0, -2, 5));
        assertTrue(board.collides(Piece.TOP_QUARK_A, 0, Board.WIDTH, 5));
    }

    @Test
    void collidesWithExistingBlocks() {
        // TOP_QUARK_A rot 0 cells: {0,0},{1,0},{1,1}
        // At col=3, row=19: cells at (3,19),(4,19),(4,18)
        board.setCell(4, 18, Piece.GLUON);
        assertTrue(board.collides(Piece.TOP_QUARK_A, 0, 3, 19));
    }

    @Test
    void noCollisionOnEmptyBoard() {
        assertFalse(board.collides(Piece.TOP_QUARK_A, 0, 3, 19));
    }

    @Test
    void placePiece() {
        // GLUON rot 0 is just {0,0} → cell at (4, 1)
        board.placePiece(Piece.GLUON, 0, 4, 1);
        assertEquals(Piece.GLUON, board.getCell(4, 1));
    }

    @Test
    void placeTriomino() {
        // TOP_QUARK_A rot 0: {0,0},{1,0},{1,1} at col=3, row=2
        // → cells at (3,2), (4,2), (4,1)
        board.placePiece(Piece.TOP_QUARK_A, 0, 3, 2);
        assertEquals(Piece.TOP_QUARK_A, board.getCell(3, 2));
        assertEquals(Piece.TOP_QUARK_A, board.getCell(4, 2));
        assertEquals(Piece.TOP_QUARK_A, board.getCell(4, 1));
    }

    @Test
    void clearFullLine() {
        for (int c = 0; c < Board.WIDTH; c++) {
            board.setCell(c, 0, Piece.TOP_QUARK_A);
        }
        int cleared = board.clearLines();
        assertEquals(1, cleared);
        for (int c = 0; c < Board.WIDTH; c++) {
            assertNull(board.getCell(c, 0));
        }
    }

    @Test
    void clearMultipleLines() {
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < Board.WIDTH; c++) {
                board.setCell(c, r, Piece.BOTTOM_QUARK_A);
            }
        }
        assertEquals(4, board.clearLines());
    }

    @Test
    void partialLineNotCleared() {
        for (int c = 0; c < Board.WIDTH - 1; c++) {
            board.setCell(c, 0, Piece.GLUON);
        }
        assertEquals(0, board.clearLines());
    }

    @Test
    void linesAboveDrop() {
        for (int c = 0; c < Board.WIDTH; c++) {
            board.setCell(c, 0, Piece.TOP_QUARK_A);
        }
        board.setCell(3, 1, Piece.BOTTOM_QUARK_A);
        board.clearLines();
        assertEquals(Piece.BOTTOM_QUARK_A, board.getCell(3, 0));
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
        board.setCell(5, Board.VISIBLE_HEIGHT, Piece.TOP_QUARK_A);
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
        board.setCell(3, 3, Piece.TOP_QUARK_A);
        Board copy = board.copy();
        assertEquals(Piece.TOP_QUARK_A, copy.getCell(3, 3));
        copy.setCell(3, 3, null);
        assertEquals(Piece.TOP_QUARK_A, board.getCell(3, 3));
    }

    @Test
    void boardDimensions() {
        assertEquals(10, Board.WIDTH);
        assertEquals(40, Board.HEIGHT);
        assertEquals(20, Board.VISIBLE_HEIGHT);
    }
}
