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
 * <h3>Visual Style</h3>
 * <ul>
 *   <li>Particles are drawn as colored circles (balls)</li>
 *   <li>Gluons show a distinctive smaller yellow circle</li>
 *   <li>Adjacent gluon-quark pairs show a connecting bridge line</li>
 *   <li>Dark space theme background</li>
 * </ul>
 */
public class GameRenderer {

    private static final int CELL_SIZE = 30;
    private static final int PADDING = 20;
    private static final int SIDE_PANEL_WIDTH = 6 * CELL_SIZE;
    private static final int FIELD_WIDTH = Board.WIDTH * CELL_SIZE;
    private static final int FIELD_HEIGHT = Board.VISIBLE_HEIGHT * CELL_SIZE;
    private static final int FIELD_X = SIDE_PANEL_WIDTH + PADDING * 2;
    private static final int FIELD_Y = PADDING;

    public static final int CANVAS_WIDTH = FIELD_X + FIELD_WIDTH + PADDING * 2 + SIDE_PANEL_WIDTH;
    public static final int CANVAS_HEIGHT = FIELD_Y + FIELD_HEIGHT + PADDING;

    private static final Color BG_COLOR = Color.rgb(8, 8, 16);
    private static final Color GRID_COLOR = Color.rgb(20, 20, 35);
    private static final Color BORDER_COLOR = Color.rgb(60, 60, 100);
    private static final double GHOST_OPACITY = 0.25;

    private static final int[][] NEIGHBORS = {{1, 0}, {0, -1}, {-1, 0}, {0, 1}};

    private final Canvas canvas;
    private final GraphicsContext gc;

    public GameRenderer() {
        canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        gc = canvas.getGraphicsContext2D();
    }

    public Canvas getCanvas() { return canvas; }

    public void render(GameState state) {
        gc.setFill(BG_COLOR);
        gc.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        drawPlayfield(state);
        drawGluonBridges(state);
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

        gc.setFill(Color.rgb(5, 5, 12));
        gc.fillRect(FIELD_X, FIELD_Y, FIELD_WIDTH, FIELD_HEIGHT);

        // Subtle grid
        gc.setStroke(GRID_COLOR);
        gc.setLineWidth(0.3);
        for (int c = 0; c <= Board.WIDTH; c++) {
            double x = FIELD_X + c * CELL_SIZE;
            gc.strokeLine(x, FIELD_Y, x, FIELD_Y + FIELD_HEIGHT);
        }
        for (int r = 0; r <= Board.VISIBLE_HEIGHT; r++) {
            double y = FIELD_Y + r * CELL_SIZE;
            gc.strokeLine(FIELD_X, y, FIELD_X + FIELD_WIDTH, y);
        }

        // Draw locked particles as circles
        Piece[][] grid = board.getGrid();
        for (int r = 0; r < Board.VISIBLE_HEIGHT; r++) {
            for (int c = 0; c < Board.WIDTH; c++) {
                if (grid[r][c] != null) {
                    double x = FIELD_X + c * CELL_SIZE;
                    double y = FIELD_Y + (Board.VISIBLE_HEIGHT - 1 - r) * CELL_SIZE;
                    drawParticleBall(x, y, CELL_SIZE, grid[r][c], 1.0);
                }
            }
        }

        gc.setStroke(BORDER_COLOR);
        gc.setLineWidth(2);
        gc.strokeRect(FIELD_X, FIELD_Y, FIELD_WIDTH, FIELD_HEIGHT);
    }

    // ==================== GLUON BRIDGES ====================

    /**
     * Draws connecting lines between gluon cells and adjacent quark cells.
     * This shows which quarks are "bound" by the strong force.
     */
    private void drawGluonBridges(GameState state) {
        Board board = state.getBoard();
        Piece[][] grid = board.getGrid();

        gc.setLineWidth(3);

        for (int r = 0; r < Board.VISIBLE_HEIGHT; r++) {
            for (int c = 0; c < Board.WIDTH; c++) {
                Piece p = grid[r][c];
                if (p == null || !p.isGluon()) continue;

                double gx = FIELD_X + c * CELL_SIZE + CELL_SIZE / 2.0;
                double gy = FIELD_Y + (Board.VISIBLE_HEIGHT - 1 - r) * CELL_SIZE + CELL_SIZE / 2.0;

                for (int[] n : NEIGHBORS) {
                    int nc = c + n[0];
                    int nr = r + n[1];
                    if (nc >= 0 && nc < Board.WIDTH && nr >= 0 && nr < Board.VISIBLE_HEIGHT) {
                        Piece neighbor = grid[nr][nc];
                        if (neighbor != null && (neighbor.isQuark() || neighbor.isGluon())) {
                            double nx = FIELD_X + nc * CELL_SIZE + CELL_SIZE / 2.0;
                            double ny = FIELD_Y + (Board.VISIBLE_HEIGHT - 1 - nr) * CELL_SIZE + CELL_SIZE / 2.0;

                            // Draw the bridge line
                            Color bridgeColor = neighbor.isGluon()
                                    ? Color.rgb(255, 204, 0, 0.4)
                                    : Color.rgb(255, 204, 0, 0.6);
                            gc.setStroke(bridgeColor);
                            gc.strokeLine(gx, gy, nx, ny);
                        }
                    }
                }
            }
        }
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
                drawParticleBall(x, y, CELL_SIZE, piece, GHOST_OPACITY);
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
                drawParticleBall(x, y, CELL_SIZE, piece, 1.0);
            }
        }
    }

    // ==================== HOLD BOX ====================

    private void drawHoldBox(GameState state) {
        double boxX = PADDING;
        double boxY = PADDING;
        double boxSize = 4.5 * CELL_SIZE;

        gc.setFill(Color.rgb(150, 150, 200));
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 14));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("HOLD", boxX + boxSize / 2, boxY - 5);

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

        gc.setFill(Color.rgb(150, 150, 200));
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 14));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("NEXT", queueX + boxWidth / 2, queueY - 5);

        List<Piece> preview = state.getPreviewPieces();
        double sectionHeight = 2.5 * CELL_SIZE;

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

        // Legend
        y += 35;
        gc.setFill(Color.rgb(80, 80, 130));
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 11));
        gc.fillText("─ PARTICLES ─", panelX, y);

        y += 18;
        drawLegendBall(panelX, y, Piece.TOP_QUARK_A, "Top Quark (u)"); y += 18;
        drawLegendBall(panelX, y, Piece.BOTTOM_QUARK_A, "Bottom Quark (d)"); y += 18;
        drawLegendBall(panelX, y, Piece.GLUON, "Gluon (glue)"); y += 26;

        gc.setFill(Color.rgb(80, 80, 130));
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 11));
        gc.fillText("─ HOW TO FORM ─", panelX, y);

        y += 16;
        gc.setFill(Color.rgb(120, 120, 170));
        gc.setFont(Font.font("Monospace", 10));
        gc.fillText("Quarks MUST be", panelX, y); y += 13;
        gc.fillText("linked by gluons!", panelX, y); y += 17;

        gc.setFill(Color.rgb(180, 140, 60));
        gc.fillText("Proton: 2u+1d+2g", panelX, y); y += 13;
        gc.fillText("Neutron: 1u+2d+2g", panelX, y); y += 13;
        gc.fillText("Pion: 1u+1d+1g", panelX, y);
    }

    private void drawLegendBall(double x, double y, Piece piece, String label) {
        double size = 14;
        drawParticleBall(x, y - size + 3, size, piece, 1.0);
        gc.setFill(Color.rgb(180, 180, 210));
        gc.setFont(Font.font("Monospace", 10));
        gc.fillText(label, x + size + 4, y);
    }

    // ==================== HADRON PANEL ====================

    private void drawHadronPanel(GameState state) {
        double panelX = FIELD_X + FIELD_WIDTH + PADDING;
        double panelY = PADDING + 5 * 2.5 * CELL_SIZE + 10;

        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Color.rgb(100, 100, 160));
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 13));
        gc.fillText("DISCOVERED", panelX, panelY);

        Map<Hadron, Integer> counts = new EnumMap<>(Hadron.class);
        for (Hadron h : state.getDiscoveredHadrons()) {
            counts.merge(h, 1, Integer::sum);
        }

        double y = panelY + 22;
        double iconSize = 36;

        for (Hadron hadron : Hadron.values()) {
            int count = counts.getOrDefault(hadron, 0);

            // Draw hadron icon as a composite of colored balls
            drawHadronIcon(panelX + 2, y, iconSize, hadron, count > 0 ? 1.0 : 0.2);

            gc.setFill(count > 0 ? Color.WHITE : Color.rgb(60, 60, 80));
            gc.setFont(Font.font("Monospace", FontWeight.BOLD, 12));
            gc.fillText(hadron.getDisplayName(), panelX + iconSize + 8, y + iconSize / 2 - 2);

            if (count > 0) {
                gc.setFill(Color.GOLD);
                gc.setFont(Font.font("Monospace", FontWeight.BOLD, 13));
                gc.fillText("×" + count, panelX + iconSize + 8, y + iconSize / 2 + 14);
            } else {
                gc.setFill(Color.rgb(50, 50, 70));
                gc.setFont(Font.font("Monospace", 10));
                gc.fillText(hadron.getDescription(), panelX + iconSize + 8, y + iconSize / 2 + 14);
            }

            y += iconSize + 12;
        }
    }

    /**
     * Draws a hadron icon as a small group of particle balls with gluon bridges.
     */
    private void drawHadronIcon(double x, double y, double size, Hadron hadron, double opacity) {
        double ballSize = size / 3.0;
        double centerX = x + size / 2;
        double centerY = y + size / 2;

        Color gluonColor = Color.color(1.0, 0.8, 0.0, opacity * 0.5);
        gc.setStroke(gluonColor);
        gc.setLineWidth(2);

        switch (hadron) {
            case PROTON -> {
                // 2 red/blue top quarks + 1 green bottom quark in triangle + gluon bridges
                double[] ax = {centerX - ballSize, centerX + ballSize, centerX};
                double[] ay = {centerY - ballSize * 0.5, centerY - ballSize * 0.5, centerY + ballSize};

                // Bridge lines
                gc.strokeLine(ax[0] + ballSize/2, ay[0] + ballSize/2,
                              ax[1] + ballSize/2, ay[1] + ballSize/2);
                gc.strokeLine(ax[1] + ballSize/2, ay[1] + ballSize/2,
                              ax[2] + ballSize/2, ay[2] + ballSize/2);
                gc.strokeLine(ax[0] + ballSize/2, ay[0] + ballSize/2,
                              ax[2] + ballSize/2, ay[2] + ballSize/2);

                drawSmallBall(ax[0], ay[0], ballSize, Color.web(Piece.TOP_QUARK_A.getColorHex(), opacity), "u");
                drawSmallBall(ax[1], ay[1], ballSize, Color.web(Piece.TOP_QUARK_B.getColorHex(), opacity), "u");
                drawSmallBall(ax[2], ay[2], ballSize, Color.web(Piece.BOTTOM_QUARK_A.getColorHex(), opacity), "d");
            }
            case NEUTRON -> {
                double[] ax = {centerX - ballSize, centerX + ballSize, centerX};
                double[] ay = {centerY - ballSize * 0.5, centerY - ballSize * 0.5, centerY + ballSize};

                gc.strokeLine(ax[0] + ballSize/2, ay[0] + ballSize/2,
                              ax[1] + ballSize/2, ay[1] + ballSize/2);
                gc.strokeLine(ax[1] + ballSize/2, ay[1] + ballSize/2,
                              ax[2] + ballSize/2, ay[2] + ballSize/2);
                gc.strokeLine(ax[0] + ballSize/2, ay[0] + ballSize/2,
                              ax[2] + ballSize/2, ay[2] + ballSize/2);

                drawSmallBall(ax[0], ay[0], ballSize, Color.web(Piece.TOP_QUARK_A.getColorHex(), opacity), "u");
                drawSmallBall(ax[1], ay[1], ballSize, Color.web(Piece.BOTTOM_QUARK_A.getColorHex(), opacity), "d");
                drawSmallBall(ax[2], ay[2], ballSize, Color.web(Piece.BOTTOM_QUARK_B.getColorHex(), opacity), "d");
            }
            case PION -> {
                double lx = centerX - ballSize * 0.7;
                double rx = centerX + ballSize * 0.3;
                double my = centerY - ballSize / 2;

                gc.strokeLine(lx + ballSize/2, my + ballSize/2,
                              rx + ballSize/2, my + ballSize/2);

                drawSmallBall(lx, my, ballSize, Color.web(Piece.TOP_QUARK_A.getColorHex(), opacity), "u");
                drawSmallBall(rx, my, ballSize, Color.web(Piece.BOTTOM_QUARK_A.getColorHex(), opacity), "d");
            }
        }
    }

    private void drawSmallBall(double x, double y, double size, Color color, String label) {
        double inset = 1;
        gc.setFill(color);
        gc.fillOval(x + inset, y + inset, size - inset * 2, size - inset * 2);

        // Highlight
        Color light = Color.color(
                Math.min(1.0, color.getRed() + 0.3),
                Math.min(1.0, color.getGreen() + 0.3),
                Math.min(1.0, color.getBlue() + 0.3),
                color.getOpacity() * 0.5);
        gc.setFill(light);
        gc.fillOval(x + size * 0.2, y + size * 0.15, size * 0.3, size * 0.25);

        // Label
        gc.setFill(Color.color(1, 1, 1, color.getOpacity() * 0.9));
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, Math.max(7, size * 0.5)));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(label, x + size / 2, y + size * 0.65);
    }

    // ==================== ACTION TEXT ====================

    private void drawActionText(GameState state) {
        String text = state.getActionText();
        if (text == null || text.isEmpty()) return;

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 22));
        gc.setFill(Color.rgb(255, 200, 50, 0.3));
        gc.fillText(text, FIELD_X + FIELD_WIDTH / 2.0 + 1, FIELD_Y + FIELD_HEIGHT / 2.0 + 1);
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
        gc.fillText("CONTAINMENT", FIELD_X + FIELD_WIDTH / 2.0, FIELD_Y + FIELD_HEIGHT / 2.0 - 30);
        gc.fillText("BREACH", FIELD_X + FIELD_WIDTH / 2.0, FIELD_Y + FIELD_HEIGHT / 2.0);

        gc.setFont(Font.font("Monospace", FontWeight.NORMAL, 14));
        gc.setFill(Color.rgb(180, 180, 200));
        gc.fillText("Press R to restart", FIELD_X + FIELD_WIDTH / 2.0, FIELD_Y + FIELD_HEIGHT / 2.0 + 30);
    }

    private void drawPauseOverlay() {
        gc.setFill(Color.rgb(0, 0, 0, 0.5));
        gc.fillRect(FIELD_X, FIELD_Y, FIELD_WIDTH, FIELD_HEIGHT);

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 28));
        gc.setFill(Color.WHITE);
        gc.fillText("PAUSED", FIELD_X + FIELD_WIDTH / 2.0, FIELD_Y + FIELD_HEIGHT / 2.0);

        gc.setFont(Font.font("Monospace", FontWeight.NORMAL, 14));
        gc.fillText("Press ESC to resume", FIELD_X + FIELD_WIDTH / 2.0, FIELD_Y + FIELD_HEIGHT / 2.0 + 28);
    }

    // ==================== PARTICLE BALL RENDERING ====================

    /**
     * Draws a single particle as a colored circle (ball).
     * Quarks are larger circles with a letter label.
     * Gluons are smaller golden circles.
     */
    private void drawParticleBall(double x, double y, double size,
                                   Piece piece, double opacity) {
        Color base = Color.web(piece.getColorHex(), opacity);
        double inset = size * 0.08;
        double diameter = size - inset * 2;

        // Shadow
        Color shadow = Color.color(0, 0, 0, opacity * 0.3);
        gc.setFill(shadow);
        gc.fillOval(x + inset + 1, y + inset + 1, diameter, diameter);

        // Main ball
        gc.setFill(base);
        if (piece.isGluon()) {
            // Gluon: slightly smaller, centered
            double gluonInset = size * 0.18;
            double gluonDiam = size - gluonInset * 2;
            gc.fillOval(x + gluonInset, y + gluonInset, gluonDiam, gluonDiam);

            // Gluon highlight
            Color light = Color.color(
                    Math.min(1.0, base.getRed() + 0.3),
                    Math.min(1.0, base.getGreen() + 0.3),
                    Math.min(1.0, base.getBlue() + 0.2),
                    opacity * 0.6);
            gc.setFill(light);
            gc.fillOval(x + gluonInset + gluonDiam * 0.15, y + gluonInset + gluonDiam * 0.1,
                    gluonDiam * 0.35, gluonDiam * 0.3);

            // 'g' label
            gc.setFill(Color.color(0.3, 0.2, 0, opacity * 0.8));
            gc.setFont(Font.font("Monospace", FontWeight.BOLD, size * 0.35));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("g", x + size / 2, y + size * 0.6);
        } else {
            // Quark: full-size circle
            gc.fillOval(x + inset, y + inset, diameter, diameter);

            // Highlight (specular)
            Color light = Color.color(
                    Math.min(1.0, base.getRed() + 0.3),
                    Math.min(1.0, base.getGreen() + 0.3),
                    Math.min(1.0, base.getBlue() + 0.3),
                    opacity * 0.5);
            gc.setFill(light);
            gc.fillOval(x + inset + diameter * 0.15, y + inset + diameter * 0.1,
                    diameter * 0.35, diameter * 0.3);

            // Label
            gc.setFill(Color.color(1, 1, 1, opacity * 0.9));
            gc.setFont(Font.font("Monospace", FontWeight.BOLD, size * 0.4));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(piece.getLabel(), x + size / 2, y + size * 0.62);
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

        double previewCellSize = CELL_SIZE * 0.65;
        double pieceWidth = (maxX - minX + 1) * previewCellSize;
        double pieceHeight = (maxY - minY + 1) * previewCellSize;
        double offsetX = centerX - pieceWidth / 2;
        double offsetY = centerY - pieceHeight / 2;

        for (int[] cell : cells) {
            double cx = offsetX + (cell[0] - minX) * previewCellSize;
            double cy = offsetY + (cell[1] - minY) * previewCellSize;
            drawParticleBall(cx, cy, previewCellSize, piece, opacity);
        }

        // Particle type label
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 9));
        gc.setFill(Color.color(1, 1, 1, opacity * 0.5));
        String typeName = piece.isGluon() ? "Gluon" :
                (piece.isTopQuark() ? "Top" : "Bottom");
        gc.fillText(typeName, centerX, offsetY + pieceHeight + 10);
    }
}
