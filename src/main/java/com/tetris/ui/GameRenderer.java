package com.tetris.ui;

import com.tetris.model.Board;
import com.tetris.model.GameState;
import com.tetris.model.Hadron;
import com.tetris.model.Piece;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the Particle Tetris game onto a JavaFX Canvas.
 *
 * <p>The renderer draws:</p>
 * <ul>
 *   <li>The playfield grid with placed particle blocks (pixel art style)</li>
 *   <li>The active falling piece with particle icon</li>
 *   <li>The ghost piece</li>
 *   <li>The hold piece box</li>
 *   <li>The next pieces preview queue</li>
 *   <li>Discovered hadrons panel with pixel art icons</li>
 *   <li>Action text, game over and pause overlays</li>
 * </ul>
 *
 * <h3>Layout</h3>
 * <pre>
 * [Hold]  [  Playfield  ]  [Next Queue]
 * [Info]  [             ]  [Hadrons   ]
 * </pre>
 */
public class GameRenderer {

    /** Size of each cell in pixels. */
    private static final int CELL_SIZE = 30;

    /** Padding around elements. */
    private static final int PADDING = 20;

    /** Width of the side panels. */
    private static final int SIDE_PANEL_WIDTH = 6 * CELL_SIZE;

    /** Playfield width in pixels. */
    private static final int FIELD_WIDTH = Board.WIDTH * CELL_SIZE;

    /** Playfield height in pixels. */
    private static final int FIELD_HEIGHT = Board.VISIBLE_HEIGHT * CELL_SIZE;

    /** X offset where the playfield starts. */
    private static final int FIELD_X = SIDE_PANEL_WIDTH + PADDING * 2;

    /** Y offset where the playfield starts. */
    private static final int FIELD_Y = PADDING;

    /** Total canvas width. */
    public static final int CANVAS_WIDTH = FIELD_X + FIELD_WIDTH + PADDING * 2 + SIDE_PANEL_WIDTH;

    /** Total canvas height. */
    public static final int CANVAS_HEIGHT = FIELD_Y + FIELD_HEIGHT + PADDING;

    /** Background color — dark space theme. */
    private static final Color BG_COLOR = Color.rgb(8, 8, 16);

    /** Grid line color. */
    private static final Color GRID_COLOR = Color.rgb(25, 25, 45);

    /** Border color — subtle neon. */
    private static final Color BORDER_COLOR = Color.rgb(60, 60, 100);

    /** Ghost piece opacity. */
    private static final double GHOST_OPACITY = 0.25;

    private final Canvas canvas;
    private final GraphicsContext gc;

    public GameRenderer() {
        canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        gc = canvas.getGraphicsContext2D();
    }

    public Canvas getCanvas() {
        return canvas;
    }

    /**
     * Renders the complete game state.
     */
    public void render(GameState state) {
        // Clear background
        gc.setFill(BG_COLOR);
        gc.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        drawPlayfield(state);
        drawGhostPiece(state);
        drawCurrentPiece(state);
        drawHoldBox(state);
        drawNextQueue(state);
        drawInfoPanel(state);
        drawHadronPanel(state);
        drawActionText(state);

        if (state.isGameOver()) {
            drawGameOverOverlay();
        } else if (state.isPaused()) {
            drawPauseOverlay();
        }
    }

    // ==================== PLAYFIELD ====================

    private void drawPlayfield(GameState state) {
        Board board = state.getBoard();

        // Playfield background
        gc.setFill(Color.rgb(5, 5, 12));
        gc.fillRect(FIELD_X, FIELD_Y, FIELD_WIDTH, FIELD_HEIGHT);

        // Grid lines
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

        // Locked blocks
        Piece[][] grid = board.getGrid();
        for (int r = 0; r < Board.VISIBLE_HEIGHT; r++) {
            for (int c = 0; c < Board.WIDTH; c++) {
                if (grid[r][c] != null) {
                    double x = FIELD_X + c * CELL_SIZE;
                    double y = FIELD_Y + (Board.VISIBLE_HEIGHT - 1 - r) * CELL_SIZE;
                    drawParticleCell(x, y, CELL_SIZE, grid[r][c], 1.0);
                }
            }
        }

        // Border
        gc.setStroke(BORDER_COLOR);
        gc.setLineWidth(2);
        gc.strokeRect(FIELD_X, FIELD_Y, FIELD_WIDTH, FIELD_HEIGHT);
    }

    // ==================== GHOST PIECE ====================

    private void drawGhostPiece(GameState state) {
        Piece piece = state.getCurrentPiece();
        if (piece == null || state.isGameOver()) return;

        int ghostRow = state.getGhostRow();
        if (ghostRow == state.getCurrentRow()) return;

        int[][] cells = piece.getCells(state.getCurrentRotation());
        for (int[] cell : cells) {
            int cx = state.getCurrentCol() + cell[0];
            int cy = ghostRow - cell[1];
            if (cy >= 0 && cy < Board.VISIBLE_HEIGHT) {
                double x = FIELD_X + cx * CELL_SIZE;
                double y = FIELD_Y + (Board.VISIBLE_HEIGHT - 1 - cy) * CELL_SIZE;
                drawParticleCell(x, y, CELL_SIZE, piece, GHOST_OPACITY);
            }
        }
    }

    // ==================== CURRENT PIECE ====================

    private void drawCurrentPiece(GameState state) {
        Piece piece = state.getCurrentPiece();
        if (piece == null || state.isGameOver()) return;

        int[][] cells = piece.getCells(state.getCurrentRotation());
        for (int[] cell : cells) {
            int cx = state.getCurrentCol() + cell[0];
            int cy = state.getCurrentRow() - cell[1];
            if (cy >= 0 && cy < Board.VISIBLE_HEIGHT) {
                double x = FIELD_X + cx * CELL_SIZE;
                double y = FIELD_Y + (Board.VISIBLE_HEIGHT - 1 - cy) * CELL_SIZE;
                drawParticleCell(x, y, CELL_SIZE, piece, 1.0);
            }
        }
    }

    // ==================== HOLD BOX ====================

    private void drawHoldBox(GameState state) {
        double boxX = PADDING;
        double boxY = PADDING;
        double boxSize = 4.5 * CELL_SIZE;

        // Label
        gc.setFill(Color.rgb(150, 150, 200));
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 14));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("HOLD", boxX + boxSize / 2, boxY - 5);

        // Box
        gc.setFill(Color.rgb(10, 10, 20));
        gc.fillRect(boxX, boxY, boxSize, boxSize);
        gc.setStroke(BORDER_COLOR);
        gc.setLineWidth(1.5);
        gc.strokeRect(boxX, boxY, boxSize, boxSize);

        Piece hold = state.getHoldPiece();
        if (hold != null) {
            double opacity = state.isHoldUsed() ? 0.3 : 1.0;
            drawPiecePreview(hold, boxX + boxSize / 2, boxY + boxSize / 2, opacity);
        }
    }

    // ==================== NEXT QUEUE ====================

    private void drawNextQueue(GameState state) {
        double queueX = FIELD_X + FIELD_WIDTH + PADDING;
        double queueY = PADDING;
        double boxWidth = 4.5 * CELL_SIZE;

        // Label
        gc.setFill(Color.rgb(150, 150, 200));
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 14));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("NEXT", queueX + boxWidth / 2, queueY - 5);

        List<Piece> preview = state.getPreviewPieces();
        double sectionHeight = 3 * CELL_SIZE;

        for (int i = 0; i < preview.size(); i++) {
            double py = queueY + i * sectionHeight;

            gc.setFill(Color.rgb(10, 10, 20));
            gc.fillRect(queueX, py, boxWidth, sectionHeight - 5);
            gc.setStroke(GRID_COLOR);
            gc.setLineWidth(0.5);
            gc.strokeRect(queueX, py, boxWidth, sectionHeight - 5);

            drawPiecePreview(preview.get(i), queueX + boxWidth / 2,
                    py + (sectionHeight - 5) / 2, 1.0);
        }
    }

    // ==================== INFO PANEL ====================

    private void drawInfoPanel(GameState state) {
        double panelX = PADDING;
        double panelY = PADDING + 5 * CELL_SIZE + 20;

        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Color.rgb(100, 100, 160));
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 13));

        double y = panelY;

        gc.fillText("LEVEL", panelX, y);
        y += 18;
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 18));
        gc.fillText(String.valueOf(state.getLevel()), panelX, y);

        y += 28;
        gc.setFill(Color.rgb(100, 100, 160));
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 13));
        gc.fillText("LINES", panelX, y);
        y += 18;
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 18));
        gc.fillText(String.valueOf(state.getTotalLinesCleared()), panelX, y);

        y += 28;
        gc.setFill(Color.rgb(100, 100, 160));
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 13));
        gc.fillText("HADRONS", panelX, y);
        y += 18;
        gc.setFill(Color.GOLD);
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 18));
        gc.fillText(String.valueOf(state.getDiscoveredHadrons().size()), panelX, y);

        // Particle legend
        y += 35;
        gc.setFill(Color.rgb(80, 80, 130));
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 11));
        gc.fillText("─ PARTICLES ─", panelX, y);

        y += 18;
        drawLegendEntry(panelX, y, Piece.TOP_QUARK_R, "Top Quark"); y += 16;
        drawLegendEntry(panelX, y, Piece.BOTTOM_QUARK_R, "Bottom Quark"); y += 16;
        drawLegendEntry(panelX, y, Piece.GLUON, "Gluon"); y += 24;

        gc.setFill(Color.rgb(80, 80, 130));
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 11));
        gc.fillText("─ RECIPES ─", panelX, y);

        y += 16;
        gc.setFill(Color.rgb(120, 120, 170));
        gc.setFont(Font.font("Monospace", 10));
        gc.fillText("Proton: 2t + 1b", panelX, y); y += 14;
        gc.fillText("Neutron: 1t + 2b", panelX, y); y += 14;
        gc.fillText("π⁺: top + gluon", panelX, y); y += 14;
        gc.fillText("π⁻: bot + gluon", panelX, y); y += 14;
        gc.fillText("π⁰: 2same + gluon", panelX, y);
    }

    private void drawLegendEntry(double x, double y, Piece piece, String label) {
        double size = 12;
        drawParticleCell(x, y - size + 2, size, piece, 1.0);
        gc.setFill(Color.rgb(180, 180, 210));
        gc.setFont(Font.font("Monospace", 10));
        gc.fillText(label, x + size + 4, y);
    }

    // ==================== HADRON DISCOVERY PANEL ====================

    private void drawHadronPanel(GameState state) {
        double panelX = FIELD_X + FIELD_WIDTH + PADDING;
        double panelY = PADDING + 5 * 3 * CELL_SIZE + 10;
        double panelWidth = SIDE_PANEL_WIDTH;

        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Color.rgb(100, 100, 160));
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 13));
        gc.fillText("DISCOVERED", panelX, panelY);

        // Count hadrons by type
        Map<Hadron, Integer> counts = new EnumMap<>(Hadron.class);
        for (Hadron h : state.getDiscoveredHadrons()) {
            counts.merge(h, 1, Integer::sum);
        }

        double y = panelY + 18;
        double iconSize = 32;
        double pixelSize = iconSize / 8.0;

        for (Hadron hadron : Hadron.values()) {
            int count = counts.getOrDefault(hadron, 0);

            // Draw pixel art icon
            drawHadronPixelArt(panelX, y, pixelSize, hadron, count > 0 ? 1.0 : 0.2);

            // Label and count
            gc.setFill(count > 0 ? Color.WHITE : Color.rgb(60, 60, 80));
            gc.setFont(Font.font("Monospace", FontWeight.BOLD, 11));
            gc.fillText(hadron.getDisplayName(), panelX + iconSize + 6, y + iconSize / 2 - 3);

            if (count > 0) {
                gc.setFill(Color.GOLD);
                gc.setFont(Font.font("Monospace", FontWeight.BOLD, 12));
                gc.fillText("×" + count, panelX + iconSize + 6, y + iconSize / 2 + 11);
            } else {
                gc.setFill(Color.rgb(50, 50, 70));
                gc.setFont(Font.font("Monospace", 10));
                gc.fillText(hadron.getQuarkNotation(), panelX + iconSize + 6, y + iconSize / 2 + 11);
            }

            y += iconSize + 8;
        }
    }

    /**
     * Draws an 8×8 pixel art icon for a hadron.
     */
    private void drawHadronPixelArt(double x, double y, double pixelSize,
                                     Hadron hadron, double opacity) {
        Color color = Color.web(hadron.getColorHex(), opacity);
        Color dark = Color.color(color.getRed() * 0.4, color.getGreen() * 0.4,
                color.getBlue() * 0.4, opacity);

        String[] art = hadron.getPixelArt();
        for (int row = 0; row < art.length; row++) {
            for (int col = 0; col < art[row].length(); col++) {
                if (art[row].charAt(col) == '#') {
                    double px = x + col * pixelSize;
                    double py = y + row * pixelSize;
                    gc.setFill(color);
                    gc.fillRect(px, py, pixelSize, pixelSize);
                    // Subtle shadow
                    gc.setFill(dark);
                    gc.fillRect(px + pixelSize * 0.75, py + pixelSize * 0.75,
                            pixelSize * 0.25, pixelSize * 0.25);
                }
            }
        }
    }

    // ==================== ACTION TEXT ====================

    private void drawActionText(GameState state) {
        String text = state.getActionText();
        if (text == null || text.isEmpty()) return;

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 22));

        // Glow effect
        gc.setFill(Color.rgb(255, 200, 50, 0.3));
        gc.fillText(text, FIELD_X + FIELD_WIDTH / 2.0 + 1,
                FIELD_Y + FIELD_HEIGHT / 2.0 + 1);
        gc.setFill(Color.GOLD);
        gc.fillText(text, FIELD_X + FIELD_WIDTH / 2.0, FIELD_Y + FIELD_HEIGHT / 2.0);
    }

    // ==================== OVERLAYS ====================

    private void drawGameOverOverlay() {
        gc.setFill(Color.rgb(0, 0, 0, 0.75));
        gc.fillRect(FIELD_X, FIELD_Y, FIELD_WIDTH, FIELD_HEIGHT);

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 28));
        gc.setFill(Color.RED);
        gc.fillText("CONTAINMENT", FIELD_X + FIELD_WIDTH / 2.0,
                FIELD_Y + FIELD_HEIGHT / 2.0 - 30);
        gc.fillText("BREACH", FIELD_X + FIELD_WIDTH / 2.0,
                FIELD_Y + FIELD_HEIGHT / 2.0);

        gc.setFont(Font.font("Monospace", FontWeight.NORMAL, 14));
        gc.setFill(Color.rgb(180, 180, 200));
        gc.fillText("Press R to restart", FIELD_X + FIELD_WIDTH / 2.0,
                FIELD_Y + FIELD_HEIGHT / 2.0 + 30);
    }

    private void drawPauseOverlay() {
        gc.setFill(Color.rgb(0, 0, 0, 0.5));
        gc.fillRect(FIELD_X, FIELD_Y, FIELD_WIDTH, FIELD_HEIGHT);

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 28));
        gc.setFill(Color.WHITE);
        gc.fillText("PAUSED", FIELD_X + FIELD_WIDTH / 2.0,
                FIELD_Y + FIELD_HEIGHT / 2.0);

        gc.setFont(Font.font("Monospace", FontWeight.NORMAL, 14));
        gc.fillText("Press ESC to resume", FIELD_X + FIELD_WIDTH / 2.0,
                FIELD_Y + FIELD_HEIGHT / 2.0 + 28);
    }

    // ==================== CELL RENDERING (PIXEL ART STYLE) ====================

    /**
     * Draws a single particle cell with pixel-art styling.
     *
     * <p>Each cell shows the particle's color with a pixel-art icon inside.
     * The style is intentionally blocky and retro, with a 1-pixel inner border
     * and a small icon drawn from the piece's pixel art data.</p>
     */
    private void drawParticleCell(double x, double y, double size,
                                   Piece piece, double opacity) {
        Color base = Color.web(piece.getColorHex(), opacity);
        Color light = Color.color(
                Math.min(1.0, base.getRed() + 0.25),
                Math.min(1.0, base.getGreen() + 0.25),
                Math.min(1.0, base.getBlue() + 0.25),
                opacity);
        Color dark = Color.color(
                base.getRed() * 0.4,
                base.getGreen() * 0.4,
                base.getBlue() * 0.4,
                opacity);
        Color inner = Color.color(
                base.getRed() * 0.7,
                base.getGreen() * 0.7,
                base.getBlue() * 0.7,
                opacity);

        double b = 2; // border thickness

        // Outer filled rect
        gc.setFill(base);
        gc.fillRect(x, y, size, size);

        // Top + left highlight
        gc.setFill(light);
        gc.fillRect(x, y, size, b);
        gc.fillRect(x, y, b, size);

        // Bottom + right shadow
        gc.setFill(dark);
        gc.fillRect(x, y + size - b, size, b);
        gc.fillRect(x + size - b, y, b, size);

        // Inner area with pixel art icon
        double innerX = x + b + 1;
        double innerY = y + b + 1;
        double innerSize = size - 2 * b - 2;

        // Draw the 4x4 pixel art icon inside the cell
        String[] art = piece.getPixelArt();
        double pixelW = innerSize / 4.0;
        double pixelH = innerSize / 4.0;

        for (int row = 0; row < Math.min(art.length, 4); row++) {
            for (int col = 0; col < Math.min(art[row].length(), 4); col++) {
                if (art[row].charAt(col) == '#') {
                    gc.setFill(inner);
                    gc.fillRect(innerX + col * pixelW, innerY + row * pixelH,
                            pixelW, pixelH);
                }
            }
        }
    }

    /**
     * Draws a piece preview (centered) at the given position.
     */
    private void drawPiecePreview(Piece piece, double centerX, double centerY, double opacity) {
        int[][] cells = piece.getCells(0);

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int[] cell : cells) {
            minX = Math.min(minX, cell[0]);
            maxX = Math.max(maxX, cell[0]);
            minY = Math.min(minY, cell[1]);
            maxY = Math.max(maxY, cell[1]);
        }

        double previewCellSize = CELL_SIZE * 0.7;
        double pieceWidth = (maxX - minX + 1) * previewCellSize;
        double pieceHeight = (maxY - minY + 1) * previewCellSize;
        double offsetX = centerX - pieceWidth / 2;
        double offsetY = centerY - pieceHeight / 2;

        for (int[] cell : cells) {
            double cx = offsetX + (cell[0] - minX) * previewCellSize;
            double cy = offsetY + (cell[1] - minY) * previewCellSize;
            drawParticleCell(cx, cy, previewCellSize, piece, opacity);
        }

        // Draw particle label below the preview
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 9));
        gc.setFill(Color.color(1, 1, 1, opacity * 0.6));
        gc.fillText(piece.getLabel().toUpperCase(), centerX,
                offsetY + pieceHeight + 10);
    }
}
