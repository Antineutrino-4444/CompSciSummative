package com.tetris.view;

import com.tetris.model.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * GamePanel.java
 * ==============
 * The main playfield rendering panel. Draws the 10×20 visible Tetris board,
 * the current falling piece, and the ghost piece.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * RENDERING OVERVIEW
 * ═══════════════════════════════════════════════════════════════════════
 *
 * The panel renders in this order (back to front):
 *   1. Background: dark fill for the entire playfield
 *   2. Grid lines: subtle lines separating cells
 *   3. Locked pieces: colored cells already on the board
 *   4. Ghost piece: translucent outline showing where the piece will land
 *   5. Current piece: the actively falling tetromino
 *   6. Border: a frame around the playfield
 *
 * ═══════════════════════════════════════════════════════════════════════
 * CELL RENDERING
 * ═══════════════════════════════════════════════════════════════════════
 * Each cell is drawn as a filled rectangle with:
 *   - A main color (the tetromino's guideline color)
 *   - A brighter highlight on the top and left edges (3D bevel effect)
 *   - A darker shadow on the bottom and right edges
 *
 * This creates the classic Tetris "raised block" look.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * GHOST PIECE
 * ═══════════════════════════════════════════════════════════════════════
 * The ghost piece is drawn as a semi-transparent outline at the position
 * where the current piece would land if hard-dropped. This helps the
 * player judge where to place pieces.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * COORDINATE MAPPING
 * ═══════════════════════════════════════════════════════════════════════
 * Board coordinates → pixel coordinates:
 *   pixelX = boardX + col * cellSize
 *   pixelY = boardY + (row - Board.BUFFER_HEIGHT) * cellSize
 *
 * Only rows >= BUFFER_HEIGHT are visible (rows 0–3 are hidden buffer).
 *
 * Cell size is computed dynamically to fill the panel, adapting on resize.
 */
public class GamePanel extends JPanel {

    /** Padding around the playfield. */
    private static final int PADDING = 2;

    /** Border color around the playfield — CERN cyan accent. */
    private static final Color BORDER_COLOR = new Color(0, 160, 180);

    // ─────────────────────── Particle Network ──────────────────────

    private final List<NetNode> nodes = new ArrayList<>();
    private final Random rng = new Random();
    private boolean nodesInitialized = false;
    private int lastPw, lastPh;

    private static final int NODE_COUNT = 80;
    private static final double CONNECT_DIST = 150.0;

    // ─────────────────────── State ──────────────────────────────

    private GameState gameState;

    // ─────────────────────── Constructor ─────────────────────────

    public GamePanel(GameState gameState) {
        this.gameState = gameState;
        setBackground(new Color(4, 6, 14));
        int defaultCell = 30;
        int w = Board.WIDTH * defaultCell + PADDING * 2;
        int h = Board.VISIBLE_HEIGHT * defaultCell + PADDING * 2;
        setPreferredSize(new Dimension(w, h));
    }

    public void setGameState(GameState gameState) {
        this.gameState = gameState;
    }

    // ─────────────────────── Dynamic Sizing ─────────────────────

    private int cellSize() {
        int cw = getWidth() - PADDING * 2;
        int ch = getHeight() - PADDING * 2;
        return Math.max(1, Math.min(cw / Board.WIDTH, ch / Board.VISIBLE_HEIGHT));
    }

    private int boardX(int cs) { return (getWidth() - cs * Board.WIDTH) / 2; }
    private int boardY(int cs) { return (getHeight() - cs * Board.VISIBLE_HEIGHT) / 2; }

    // ─────────────────────── Rendering ──────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int cs = cellSize();
        int bx = boardX(cs);
        int by = boardY(cs);
        Settings s = Settings.get();

        // 0. Background network (behind game board)
        updateNodes();
        drawConnections(g2);
        drawNodes(g2);

        // 1. Board background
        g2.setColor(new Color(6, 10, 22, (int) (s.getBoardOpacity() * 255)));
        g2.fillRect(bx, by, Board.WIDTH * cs, Board.VISIBLE_HEIGHT * cs);

        // 2. Grid lines
        drawGrid(g2, s, cs, bx, by);

        // 3. Locked pieces on the board
        drawBoard(g2, cs, bx, by);

        // 4. Ghost piece
        drawGhostPiece(g2, s, cs, bx, by);

        // 5. Current piece
        drawCurrentPiece(g2, cs, bx, by);

        // 6. Border
        g2.setColor(BORDER_COLOR);
        g2.setStroke(new BasicStroke(2));
        g2.drawRect(bx, by, Board.WIDTH * cs, Board.VISIBLE_HEIGHT * cs);

        // 7. Overlays (pause, game over)
        if (gameState.isPaused()) {
            drawOverlay(g2, "PAUSED", "Press P to resume", cs, bx, by);
        } else if (gameState.isGameOver()) {
            drawOverlay(g2, "GAME OVER", "Press R to restart", cs, bx, by);
        }
    }

    private void drawGrid(Graphics2D g2, Settings s, int cs, int bx, int by) {
        g2.setColor(new Color(0, 140, 160, (int) (s.getGridOpacity() * 255)));
        g2.setStroke(new BasicStroke(1));
        for (int col = 0; col <= Board.WIDTH; col++) {
            int x = bx + col * cs;
            g2.drawLine(x, by, x, by + Board.VISIBLE_HEIGHT * cs);
        }
        for (int row = 0; row <= Board.VISIBLE_HEIGHT; row++) {
            int y = by + row * cs;
            g2.drawLine(bx, y, bx + Board.WIDTH * cs, y);
        }
    }

    private void drawBoard(Graphics2D g2, int cs, int bx, int by) {
        Color[][] grid = gameState.getBoard().getGridCopy();
        for (int row = Board.BUFFER_HEIGHT; row < Board.TOTAL_HEIGHT; row++) {
            for (int col = 0; col < Board.WIDTH; col++) {
                Color c = grid[row][col];
                if (c != null) {
                    drawCell(g2, col, row - Board.BUFFER_HEIGHT, c, false, cs, bx, by);
                }
            }
        }
    }

    private void drawGhostPiece(Graphics2D g2, Settings s, int cs, int bx, int by) {
        Tetromino ghost = gameState.getGhostPiece();
        if (ghost == null) return;
        Color gc = ghost.getType().getColor();
        int alpha = (int) (s.getGhostOpacity() * 255);
        Color tc = new Color(gc.getRed(), gc.getGreen(), gc.getBlue(), alpha);
        for (Position p : ghost.getAbsoluteCells()) {
            int vr = p.getY() - Board.BUFFER_HEIGHT;
            if (vr >= 0) drawCell(g2, p.getX(), vr, tc, true, cs, bx, by);
        }
    }

    private void drawCurrentPiece(Graphics2D g2, int cs, int bx, int by) {
        Tetromino cur = gameState.getCurrentPiece();
        if (cur == null) return;
        Color c = cur.getType().getColor();
        for (Position p : cur.getAbsoluteCells()) {
            int vr = p.getY() - Board.BUFFER_HEIGHT;
            if (vr >= 0) drawCell(g2, p.getX(), vr, c, false, cs, bx, by);
        }
    }

    private void drawCell(Graphics2D g2, int col, int row, Color color,
                           boolean isGhost, int cs, int bx, int by) {
        int x = bx + col * cs;
        int y = by + row * cs;
        if (isGhost) {
            g2.setColor(color);
            g2.fillRect(x + 1, y + 1, cs - 2, cs - 2);
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(),
                    Math.min(255, color.getAlpha() * 2)));
            g2.setStroke(new BasicStroke(1));
            g2.drawRect(x + 1, y + 1, cs - 3, cs - 3);
        } else {
            g2.setColor(color);
            g2.fillRect(x + 1, y + 1, cs - 2, cs - 2);
            g2.setColor(color.brighter());
            g2.fillRect(x + 1, y + 1, cs - 2, 2);
            g2.fillRect(x + 1, y + 1, 2, cs - 2);
            g2.setColor(color.darker());
            g2.fillRect(x + 1, y + cs - 3, cs - 2, 2);
            g2.fillRect(x + cs - 3, y + 1, 2, cs - 2);
        }
    }

    private void drawOverlay(Graphics2D g2, String title, String subtitle,
                              int cs, int bx, int by) {
        int w = Board.WIDTH * cs;
        int h = Board.VISIBLE_HEIGHT * cs;
        g2.setColor(new Color(2, 6, 16, 200));
        g2.fillRect(bx, by, w, h);
        int fontSize = Math.max(14, cs * 28 / 30);
        g2.setColor(new Color(0, 220, 240));
        g2.setFont(new Font("Monospaced", Font.BOLD, fontSize));
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(title);
        g2.drawString(title, bx + (w - tw) / 2, by + h / 2 - 10);
        int subSize = Math.max(9, cs * 14 / 30);
        g2.setColor(new Color(80, 160, 180));
        g2.setFont(new Font("Monospaced", Font.PLAIN, subSize));
        fm = g2.getFontMetrics();
        int sw = fm.stringWidth(subtitle);
        g2.drawString(subtitle, bx + (w - sw) / 2, by + h / 2 + 20);
    }

    // ═══════════════════════ Network Particle System ═══════════════════

    private void updateNodes() {
        int pw = getWidth(), ph = getHeight();
        if (pw <= 0 || ph <= 0) return;

        // First-time init
        if (!nodesInitialized || nodes.isEmpty()) {
            nodes.clear();
            for (int i = 0; i < NODE_COUNT; i++) {
                nodes.add(new NetNode(rng, pw, ph));
            }
            nodesInitialized = true;
            lastPw = pw;
            lastPh = ph;
        }

        // Rescale node positions when panel size changes
        if (pw != lastPw || ph != lastPh) {
            double sx = lastPw > 0 ? (double) pw / lastPw : 1;
            double sy = lastPh > 0 ? (double) ph / lastPh : 1;
            for (NetNode n : nodes) {
                n.x *= sx;
                n.y *= sy;
            }
            lastPw = pw;
            lastPh = ph;
        }

        for (NetNode n : nodes) {
            // Bounce off edges
            if (n.x < 0 || n.x > pw) n.vx *= -1;
            if (n.y < 0 || n.y > ph) n.vy *= -1;
            n.x = Math.max(0, Math.min(pw, n.x + n.vx));
            n.y = Math.max(0, Math.min(ph, n.y + n.vy));
            // Breathing pulse
            n.pulse += 0.02;
        }
    }

    private void drawNodes(Graphics2D g2) {
        for (NetNode n : nodes) {
            float pulseScale = 1f + (float) Math.sin(n.pulse) * 0.3f;
            float sz = n.size * pulseScale;

            float r = n.cr / 255f, gr = n.cg / 255f, b = n.cb / 255f;
            float baseAlpha = n.alpha;

            // Outer radial glow
            int glowR = (int) (sz * 5);
            if (glowR > 2) {
                RadialGradientPaint glow = new RadialGradientPaint(
                        (float) n.x, (float) n.y, glowR,
                        new float[]{0f, 0.5f, 1f},
                        new Color[]{
                                new Color(r, gr, b, baseAlpha * 0.55f),
                                new Color(r, gr, b, baseAlpha * 0.2f),
                                new Color(r, gr, b, 0f)
                        });
                g2.setPaint(glow);
                g2.fillOval((int) (n.x - glowR), (int) (n.y - glowR), glowR * 2, glowR * 2);
            }

            // Core dot (bright)
            g2.setColor(new Color(r, gr, b, Math.min(1f, baseAlpha * 1.2f)));
            int coreSize = Math.max(2, (int) sz);
            g2.fillOval((int) (n.x - coreSize / 2.0), (int) (n.y - coreSize / 2.0),
                    coreSize, coreSize);
        }
    }

    private void drawConnections(Graphics2D g2) {
        long time = System.currentTimeMillis();
        int sz = nodes.size();
        for (int i = 0; i < sz; i++) {
            NetNode a = nodes.get(i);
            for (int j = i + 1; j < sz; j++) {
                NetNode b = nodes.get(j);
                double dx = a.x - b.x;
                double dy = a.y - b.y;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist >= CONNECT_DIST) continue;

                float opacity = (float) (1.0 - dist / CONNECT_DIST) * 0.85f;
                float thickness = 0.4f + (float) (1.0 - dist / CONNECT_DIST) * 1.1f;

                // Color by distance: close=bright cyan, mid=teal, far=blue
                Color lineColor;
                if (dist < 50) {
                    lineColor = new Color(0f, 0.92f, 1f, opacity);
                } else if (dist < 100) {
                    lineColor = new Color(0f, 0.7f, 0.85f, opacity);
                } else {
                    lineColor = new Color(0.2f, 0.45f, 0.85f, opacity);
                }

                g2.setColor(lineColor);
                g2.setStroke(new BasicStroke(thickness));
                g2.drawLine((int) a.x, (int) a.y, (int) b.x, (int) b.y);

                // Midpoint oscillating dot
                double mx = (a.x + b.x) / 2 + Math.sin(time * 0.001 + a.x) * 5;
                double my = (a.y + b.y) / 2 + Math.cos(time * 0.001 + b.y) * 5;
                g2.setColor(new Color(0f, 0.85f, 0.95f, opacity * 0.7f));
                int dotSize = (int) (1.5 * thickness) + 1;
                g2.fillOval((int) mx - dotSize / 2, (int) my - dotSize / 2, dotSize, dotSize);
            }
        }
    }

    // ─────────────────────── Inner Types ────────────────────────

    private static class NetNode {
        double x, y, vx, vy, pulse;
        float size, alpha;
        int cr, cg, cb;

        NetNode(Random rng, int pw, int ph) {
            x = rng.nextDouble() * pw;
            y = rng.nextDouble() * ph;
            vx = (rng.nextDouble() - 0.5) * 0.7;
            vy = (rng.nextDouble() - 0.5) * 0.7;
            pulse = rng.nextDouble() * Math.PI * 2;
            size = 2f + rng.nextFloat() * 3f;
            alpha = 0.45f + rng.nextFloat() * 0.4f;
            // CERN color palette — vibrant
            int type = rng.nextInt(5);
            switch (type) {
                case 0 -> { cr = 0;   cg = 220; cb = 240; }  // bright cyan
                case 1 -> { cr = 0;   cg = 180; cb = 200; }  // teal
                case 2 -> { cr = 30;  cg = 255; cb = 255; }  // electric cyan
                case 3 -> { cr = 80;  cg = 120; cb = 230; }  // blue
                case 4 -> { cr = 120; cg = 80;  cb = 255; }  // violet accent
            }
        }
    }
}
