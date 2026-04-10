package com.tetris.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Board class covering grid operations, collision detection,
 * line clearing, and perfect clear detection.
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
    void setCellAndGetCell() {
        board.setCell(3, 5, Piece.T);
        assertEquals(Piece.T, board.getCell(3, 5));
        assertFalse(board.isEmpty(3, 5));
    }

    @Test
    void outOfBoundsGetCellReturnsNull() {
        assertNull(board.getCell(-1, 0));
        assertNull(board.getCell(Board.WIDTH, 0));
        assertNull(board.getCell(0, -1));
        assertNull(board.getCell(0, Board.HEIGHT));
    }

    @Test
    void outOfBoundsIsEmptyReturnsFalse() {
        assertFalse(board.isEmpty(-1, 0));
        assertFalse(board.isEmpty(Board.WIDTH, 0));
        assertFalse(board.isEmpty(0, -1));
    }

    @Test
    void collidesWithWalls() {
        // I-piece at spawn rotation, going off left edge
        assertTrue(board.collides(Piece.I, 0, -1, 5));
        // I-piece going off right edge
        assertTrue(board.collides(Piece.I, 0, 8, 5));
        // I-piece going off bottom
        assertTrue(board.collides(Piece.I, 0, 3, 0));
    }

    @Test
    void collidesWithExistingBlocks() {
        board.setCell(4, 5, Piece.S);
        // T-piece overlapping at that position
        assertTrue(board.collides(Piece.T, 0, 3, 6));
    }

    @Test
    void noCollisionInEmptyArea() {
        assertFalse(board.collides(Piece.T, 0, 3, 19));
    }

    @Test
    void placePieceAddsBlocksToBoard() {
        board.placePiece(Piece.T, 0, 3, 5);
        // T-piece rotation 0: {1,0},{0,1},{1,1},{2,1}
        // At col=3, row=5: cells at (4,5), (3,4), (4,4), (5,4)
        assertNotNull(board.getCell(4, 5)); // (3+1, 5-0)
        assertNotNull(board.getCell(3, 4)); // (3+0, 5-1)
        assertNotNull(board.getCell(4, 4)); // (3+1, 5-1)
        assertNotNull(board.getCell(5, 4)); // (3+2, 5-1)
    }

    @Test
    void clearLinesRemovesFullRows() {
        // Fill row 0 completely
        for (int c = 0; c < Board.WIDTH; c++) {
            board.setCell(c, 0, Piece.I);
        }
        assertEquals(1, board.clearLines());
        // Row 0 should now be empty
        for (int c = 0; c < Board.WIDTH; c++) {
            assertNull(board.getCell(c, 0));
        }
    }

    @Test
    void clearLinesDropsRowsDown() {
        // Fill rows 0 and 1, leave gap in row 0
        for (int c = 0; c < Board.WIDTH; c++) {
            board.setCell(c, 0, Piece.I);
        }
        // Add a block on row 1
        board.setCell(5, 1, Piece.T);

        int cleared = board.clearLines();
        assertEquals(1, cleared);
        // The block from row 1 should now be at row 0
        assertEquals(Piece.T, board.getCell(5, 0));
    }

    @Test
    void clearMultipleLines() {
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < Board.WIDTH; c++) {
                board.setCell(c, r, Piece.I);
            }
        }
        assertEquals(4, board.clearLines());
    }

    @Test
    void noLinesCleared() {
        board.setCell(0, 0, Piece.I);
        assertEquals(0, board.clearLines());
    }

    @Test
    void isPerfectClearOnEmptyBoard() {
        assertTrue(board.isPerfectClear());
    }

    @Test
    void isNotPerfectClearWithBlocks() {
        board.setCell(0, 0, Piece.T);
        assertFalse(board.isPerfectClear());
    }

    @Test
    void isLineFullReturnsTrueForFullRow() {
        for (int c = 0; c < Board.WIDTH; c++) {
            board.setCell(c, 3, Piece.I);
        }
        assertTrue(board.isLineFull(3));
    }

    @Test
    void isLineFullReturnsFalseForPartialRow() {
        for (int c = 0; c < Board.WIDTH - 1; c++) {
            board.setCell(c, 3, Piece.I);
        }
        assertFalse(board.isLineFull(3));
    }

    @Test
    void hasBlocksAboveVisible() {
        assertFalse(board.hasBlocksAboveVisible());
        board.setCell(5, Board.VISIBLE_HEIGHT, Piece.T);
        assertTrue(board.hasBlocksAboveVisible());
    }

    @Test
    void getHighestRowEmptyBoard() {
        assertEquals(-1, board.getHighestRow());
    }

    @Test
    void getHighestRow() {
        board.setCell(0, 0, Piece.I);
        board.setCell(5, 10, Piece.T);
        assertEquals(10, board.getHighestRow());
    }

    @Test
    void copyCreatesIndependentBoard() {
        board.setCell(3, 3, Piece.T);
        Board copy = board.copy();
        assertEquals(Piece.T, copy.getCell(3, 3));

        // Modify original, copy should not change
        board.setCell(3, 3, null);
        assertEquals(Piece.T, copy.getCell(3, 3));
    }

    @Test
    void boardDimensions() {
        assertEquals(10, Board.WIDTH);
        assertEquals(40, Board.HEIGHT);
        assertEquals(20, Board.VISIBLE_HEIGHT);
    }
}
