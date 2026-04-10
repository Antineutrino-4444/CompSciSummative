package com.tetris.ui;

import com.tetris.model.Board;
import com.tetris.model.GameState;
import com.tetris.model.Piece;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.List;

/**
 * Renders the Tetris game onto a JavaFX Canvas.
 *
 * <p>The renderer draws:</p>
 * <ul>
 *   <li>The playfield grid with placed blocks</li>
 *   <li>The active falling piece</li>
 *   <li>The ghost piece (translucent preview of where piece will land)</li>
 *   <li>The hold piece box</li>
 *   <li>The next pieces preview queue</li>
 *   <li>Score, level, and lines information</li>
 *   <li>Action text (e.g. "Tetris", "T-Spin Double")</li>
 *   <li>Game over and pause overlays</li>
 * </ul>
 *
 * <h3>Layout</h3>
 * <p>The rendering area is organized as:</p>
 * <pre>
 * [Hold] [   Playfield   ] [Next Queue]
 *        [               ] [  Score   ]
 *        [               ] [  Level   ]
 *        [               ] [  Lines   ]
 * </pre>
 */
public class GameRenderer {

    /** Size of each cell in pixels. */
    private static final int CELL_SIZE = 30;

    /** Padding around the playfield. */
    private static final int PADDING = 20;

    /** Width of the side panels (hold / next). */
    private static final int SIDE_PANEL_WIDTH = 6 * CELL_SIZE;

    /** Playfield width in pixels. */
    private static final int FIELD_WIDTH = Board.WIDTH * CELL_SIZE;

    /** Playfield height in pixels (visible rows only). */
    private static final int FIELD_HEIGHT = Board.VISIBLE_HEIGHT * CELL_SIZE;

    /** X offset where the playfield starts. */
    private static final int FIELD_X = SIDE_PANEL_WIDTH + PADDING * 2;

    /** Y offset where the playfield starts. */
    private static final int FIELD_Y = PADDING;

    /** Total canvas width. */
    public static final int CANVAS_WIDTH = FIELD_X + FIELD_WIDTH + PADDING * 2 + SIDE_PANEL_WIDTH;

    /** Total canvas height. */
    public static final int CANVAS_HEIGHT = FIELD_Y + FIELD_HEIGHT + PADDING;

    /** Background color. */
    private static final Color BG_COLOR = Color.rgb(20, 20, 30);

    /** Grid line color. */
    private static final Color GRID_COLOR = Color.rgb(40, 40, 60);

    /** Border color. */
    private static final Color BORDER_COLOR = Color.rgb(80, 80, 120);

    /** Ghost piece opacity. */
    private static final double GHOST_OPACITY = 0.3;

    private final Canvas canvas;
    private final GraphicsContext gc;

    /**
     * Creates a new GameRenderer with its own Canvas.
     */
    public GameRenderer() {
        canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        gc = canvas.getGraphicsContext2D();
    }

    /**
     * Returns the Canvas that this renderer draws to.
     *
     * @return the JavaFX Canvas
     */
    public Canvas getCanvas() {
        return canvas;
    }

    /**
     * Renders the complete game state onto the canvas.
     *
     * @param state the current game state to render
     */
    public void render(GameState state) {
        // Clear background
        gc.setFill(BG_COLOR);
        gc.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        // Draw components
        drawPlayfield(state);
        drawGhostPiece(state);
        drawCurrentPiece(state);
        drawHoldBox(state);
        drawNextQueue(state);
        drawScorePanel(state);
        drawActionText(state);

        // Draw overlays
        if (state.isGameOver()) {
            drawGameOverOverlay();
        } else if (state.isPaused()) {
            drawPauseOverlay();
        }
    }

    /**
     * Draws the playfield grid and all locked blocks.
     */
    private void drawPlayfield(GameState state) {
        Board board = state.getBoard();

        // Draw playfield background
        gc.setFill(Color.rgb(10, 10, 15));
        gc.fillRect(FIELD_X, FIELD_Y, FIELD_WIDTH, FIELD_HEIGHT);

        // Draw grid lines
        gc.setStroke(GRID_COLOR);
        gc.setLineWidth(0.5);
        for (int c = 0; c <= Board.WIDTH; c++) {
            double x = FIELD_X + c * CELL_SIZE;
            gc.strokeLine(x, FIELD_Y, x, FIELD_Y + FIELD_HEIGHT);
        }
        for (int r = 0; r <= Board.VISIBLE_HEIGHT; r++) {
            double y = FIELD_Y + r * CELL_SIZE;
            gc.strokeLine(FIELD_X, y, FIELD_X + FIELD_WIDTH, y);
        }

        // Draw locked blocks (only visible rows)
        Piece[][] grid = board.getGrid();
        for (int r = 0; r < Board.VISIBLE_HEIGHT; r++) {
            for (int c = 0; c < Board.WIDTH; c++) {
                if (grid[r][c] != null) {
                    drawCell(FIELD_X + c * CELL_SIZE,
                             FIELD_Y + (Board.VISIBLE_HEIGHT - 1 - r) * CELL_SIZE,
                             pieceToColor(grid[r][c]), 1.0);
                }
            }
        }

        // Draw border
        gc.setStroke(BORDER_COLOR);
        gc.setLineWidth(2);
        gc.strokeRect(FIELD_X, FIELD_Y, FIELD_WIDTH, FIELD_HEIGHT);
    }

    /**
     * Draws the ghost piece (translucent preview of hard drop destination).
     */
    private void drawGhostPiece(GameState state) {
        Piece piece = state.getCurrentPiece();
        if (piece == null || state.isGameOver()) return;

        int ghostRow = state.getGhostRow();
        if (ghostRow == state.getCurrentRow()) return; // Already at bottom

        int[][] cells = piece.getCells(state.getCurrentRotation());
        Color color = pieceToColor(piece);

        for (int[] cell : cells) {
            int cx = state.getCurrentCol() + cell[0];
            int cy = ghostRow - cell[1];
            if (cy >= 0 && cy < Board.VISIBLE_HEIGHT) {
                drawCell(FIELD_X + cx * CELL_SIZE,
                         FIELD_Y + (Board.VISIBLE_HEIGHT - 1 - cy) * CELL_SIZE,
                         color, GHOST_OPACITY);
            }
        }
    }

    /**
     * Draws the currently active (falling) piece.
     */
    private void drawCurrentPiece(GameState state) {
        Piece piece = state.getCurrentPiece();
        if (piece == null || state.isGameOver()) return;

        int[][] cells = piece.getCells(state.getCurrentRotation());
        Color color = pieceToColor(piece);

        for (int[] cell : cells) {
            int cx = state.getCurrentCol() + cell[0];
            int cy = state.getCurrentRow() - cell[1];
            if (cy >= 0 && cy < Board.VISIBLE_HEIGHT) {
                drawCell(FIELD_X + cx * CELL_SIZE,
                         FIELD_Y + (Board.VISIBLE_HEIGHT - 1 - cy) * CELL_SIZE,
                         color, 1.0);
            }
        }
    }

    /**
     * Draws the hold piece box on the left side panel.
     */
    private void drawHoldBox(GameState state) {
        double boxX = PADDING;
        double boxY = PADDING;
        double boxSize = 4.5 * CELL_SIZE;

        // Label
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 16));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("HOLD", boxX + boxSize / 2, boxY - 5);

        // Box background
        gc.setFill(Color.rgb(15, 15, 25));
        gc.fillRect(boxX, boxY, boxSize, boxSize);
        gc.setStroke(BORDER_COLOR);
        gc.setLineWidth(1.5);
        gc.strokeRect(boxX, boxY, boxSize, boxSize);

        // Draw hold piece
        Piece hold = state.getHoldPiece();
        if (hold != null) {
            double opacity = state.isHoldUsed() ? 0.4 : 1.0;
            drawPiecePreview(hold, boxX + boxSize / 2, boxY + boxSize / 2, opacity);
        }
    }

    /**
     * Draws the next pieces preview queue on the right side panel.
     */
    private void drawNextQueue(GameState state) {
        double queueX = FIELD_X + FIELD_WIDTH + PADDING;
        double queueY = PADDING;
        double boxWidth = 4.5 * CELL_SIZE;

        // Label
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 16));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("NEXT", queueX + boxWidth / 2, queueY - 5);

        List<Piece> preview = state.getPreviewPieces();
        double pieceSectionHeight = 3 * CELL_SIZE;

        for (int i = 0; i < preview.size(); i++) {
            double py = queueY + i * pieceSectionHeight;

            // Box
            gc.setFill(Color.rgb(15, 15, 25));
            gc.fillRect(queueX, py, boxWidth, pieceSectionHeight - 5);
            gc.setStroke(GRID_COLOR);
            gc.setLineWidth(0.5);
            gc.strokeRect(queueX, py, boxWidth, pieceSectionHeight - 5);

            // Piece
            drawPiecePreview(preview.get(i),
                             queueX + boxWidth / 2,
                             py + (pieceSectionHeight - 5) / 2,
                             1.0);
        }
    }

    /**
     * Draws the score, level, and lines panel on the right side.
     */
    private void drawScorePanel(GameState state) {
        double panelX = FIELD_X + FIELD_WIDTH + PADDING;
        double panelY = PADDING + 5 * 3 * CELL_SIZE + 10;

        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Color.rgb(150, 150, 200));
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 14));

        double lineSpacing = 22;
        double y = panelY;

        gc.fillText("SCORE", panelX, y);
        y += lineSpacing;
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 20));
        gc.fillText(String.valueOf(state.getScoring().getScore()), panelX, y);

        y += lineSpacing + 10;
        gc.setFill(Color.rgb(150, 150, 200));
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 14));
        gc.fillText("LEVEL", panelX, y);
        y += lineSpacing;
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 20));
        gc.fillText(String.valueOf(state.getScoring().getLevel()), panelX, y);

        y += lineSpacing + 10;
        gc.setFill(Color.rgb(150, 150, 200));
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 14));
        gc.fillText("LINES", panelX, y);
        y += lineSpacing;
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 20));
        gc.fillText(String.valueOf(state.getScoring().getTotalLinesCleared()), panelX, y);

        // Combo
        if (state.getScoring().getCombo() > 0) {
            y += lineSpacing + 10;
            gc.setFill(Color.YELLOW);
            gc.setFont(Font.font("Monospace", FontWeight.BOLD, 14));
            gc.fillText("COMBO ×" + state.getScoring().getCombo(), panelX, y);
        }

        // Back-to-Back indicator
        if (state.getScoring().isBackToBack()) {
            y += lineSpacing;
            gc.setFill(Color.CYAN);
            gc.setFont(Font.font("Monospace", FontWeight.BOLD, 12));
            gc.fillText("BACK-TO-BACK", panelX, y);
        }
    }

    /**
     * Draws the action text (e.g. "Tetris", "T-Spin Double") in the center.
     */
    private void drawActionText(GameState state) {
        String text = state.getActionText();
        if (text == null || text.isEmpty()) return;

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 24));
        gc.setFill(Color.YELLOW);
        gc.fillText(text, FIELD_X + FIELD_WIDTH / 2.0, FIELD_Y + FIELD_HEIGHT / 2.0);
    }

    /**
     * Draws the game over overlay.
     */
    private void drawGameOverOverlay() {
        // Semi-transparent overlay
        gc.setFill(Color.rgb(0, 0, 0, 0.7));
        gc.fillRect(FIELD_X, FIELD_Y, FIELD_WIDTH, FIELD_HEIGHT);

        // Text
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 32));
        gc.setFill(Color.RED);
        gc.fillText("GAME OVER", FIELD_X + FIELD_WIDTH / 2.0,
                     FIELD_Y + FIELD_HEIGHT / 2.0 - 20);

        gc.setFont(Font.font("Monospace", FontWeight.NORMAL, 16));
        gc.setFill(Color.WHITE);
        gc.fillText("Press R to restart", FIELD_X + FIELD_WIDTH / 2.0,
                     FIELD_Y + FIELD_HEIGHT / 2.0 + 20);
    }

    /**
     * Draws the pause overlay.
     */
    private void drawPauseOverlay() {
        gc.setFill(Color.rgb(0, 0, 0, 0.5));
        gc.fillRect(FIELD_X, FIELD_Y, FIELD_WIDTH, FIELD_HEIGHT);

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 32));
        gc.setFill(Color.WHITE);
        gc.fillText("PAUSED", FIELD_X + FIELD_WIDTH / 2.0,
                     FIELD_Y + FIELD_HEIGHT / 2.0);

        gc.setFont(Font.font("Monospace", FontWeight.NORMAL, 16));
        gc.fillText("Press ESC to resume", FIELD_X + FIELD_WIDTH / 2.0,
                     FIELD_Y + FIELD_HEIGHT / 2.0 + 30);
    }

    // ==================== HELPER METHODS ====================

    /**
     * Draws a single cell with 3D-effect styling.
     *
     * @param x       the x pixel coordinate
     * @param y       the y pixel coordinate
     * @param color   the base color
     * @param opacity the opacity (1.0 = fully opaque)
     */
    private void drawCell(double x, double y, Color color, double opacity) {
        Color fill = Color.color(color.getRed(), color.getGreen(), color.getBlue(), opacity);
        Color light = Color.color(
                Math.min(1.0, color.getRed() + 0.3),
                Math.min(1.0, color.getGreen() + 0.3),
                Math.min(1.0, color.getBlue() + 0.3),
                opacity);
        Color dark = Color.color(
                color.getRed() * 0.5,
                color.getGreen() * 0.5,
                color.getBlue() * 0.5,
                opacity);

        double s = CELL_SIZE;
        double border = 2;

        // Main fill
        gc.setFill(fill);
        gc.fillRect(x + border, y + border, s - border * 2, s - border * 2);

        // Top/left highlight
        gc.setFill(light);
        gc.fillRect(x, y, s, border);           // top
        gc.fillRect(x, y, border, s);            // left

        // Bottom/right shadow
        gc.setFill(dark);
        gc.fillRect(x, y + s - border, s, border);  // bottom
        gc.fillRect(x + s - border, y, border, s);   // right
    }

    /**
     * Draws a piece preview (centered) at the given position.
     *
     * @param piece   the piece to draw
     * @param centerX the center X coordinate
     * @param centerY the center Y coordinate
     * @param opacity the opacity
     */
    private void drawPiecePreview(Piece piece, double centerX, double centerY, double opacity) {
        int[][] cells = piece.getCells(0); // Always show spawn rotation
        Color color = pieceToColor(piece);

        // Calculate bounding box of the piece cells for centering
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int[] cell : cells) {
            minX = Math.min(minX, cell[0]);
            maxX = Math.max(maxX, cell[0]);
            minY = Math.min(minY, cell[1]);
            maxY = Math.max(maxY, cell[1]);
        }

        double previewCellSize = CELL_SIZE * 0.75;
        double pieceWidth = (maxX - minX + 1) * previewCellSize;
        double pieceHeight = (maxY - minY + 1) * previewCellSize;
        double offsetX = centerX - pieceWidth / 2;
        double offsetY = centerY - pieceHeight / 2;

        for (int[] cell : cells) {
            double cx = offsetX + (cell[0] - minX) * previewCellSize;
            double cy = offsetY + (cell[1] - minY) * previewCellSize;
            drawPreviewCell(cx, cy, previewCellSize, color, opacity);
        }
    }

    /**
     * Draws a single cell for preview areas (smaller size).
     */
    private void drawPreviewCell(double x, double y, double size, Color color, double opacity) {
        Color fill = Color.color(color.getRed(), color.getGreen(), color.getBlue(), opacity);
        Color light = Color.color(
                Math.min(1.0, color.getRed() + 0.3),
                Math.min(1.0, color.getGreen() + 0.3),
                Math.min(1.0, color.getBlue() + 0.3),
                opacity);
        Color dark = Color.color(
                color.getRed() * 0.5,
                color.getGreen() * 0.5,
                color.getBlue() * 0.5,
                opacity);

        double border = 1.5;

        gc.setFill(fill);
        gc.fillRect(x + border, y + border, size - border * 2, size - border * 2);

        gc.setFill(light);
        gc.fillRect(x, y, size, border);
        gc.fillRect(x, y, border, size);

        gc.setFill(dark);
        gc.fillRect(x, y + size - border, size, border);
        gc.fillRect(x + size - border, y, border, size);
    }

    /**
     * Converts a Piece enum to its JavaFX Color.
     *
     * @param piece the piece type
     * @return the corresponding color
     */
    private Color pieceToColor(Piece piece) {
        return Color.web(piece.getColorHex());
    }
}
