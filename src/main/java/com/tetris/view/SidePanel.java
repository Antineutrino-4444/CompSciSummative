package com.tetris.view;

import com.tetris.model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * SidePanel.java
 * ==============
 * Renders the side information panels: Hold piece, Next pieces preview,
 * score, level, lines, combo, and controls reference.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * LAYOUT (top to bottom)
 * ═══════════════════════════════════════════════════════════════════════
 *
 *   ┌────────────────┐
 *   │    HOLD         │ ← Shows the held piece (or empty)
 *   ├────────────────┤
 *   │    NEXT         │ ← Shows the next 5 upcoming pieces
 *   │    [piece 1]    │
 *   │    [piece 2]    │
 *   │    [piece 3]    │
 *   │    [piece 4]    │
 *   │    [piece 5]    │
 *   ├────────────────┤
 *   │   SCORE        │ ← Current score
 *   │   LEVEL        │ ← Current level
 *   │   LINES        │ ← Total lines cleared
 *   │   COMBO        │ ← Current combo
 *   ├────────────────┤
 *   │   Last Action  │ ← e.g., "B2B Tetris Combo 3"
 *   ├────────────────┤
 *   │   CONTROLS     │ ← Key reference
 *   └────────────────┘
 *
 * ═══════════════════════════════════════════════════════════════════════
 * MINI PIECE RENDERING
 * ═══════════════════════════════════════════════════════════════════════
 * The Hold and Next panels render pieces at a smaller cell size (MINI_CELL)
 * centered within a fixed-size preview box. Each piece is drawn in its
 * spawn orientation (rotation state 0).
 *
 * The hold piece is grayed out if it was already used this turn.
 */
public class SidePanel extends JPanel {

    /** Cell size for mini piece previews. */
    private static final int MINI_CELL = 18;

    /** Width of the side panel in pixels. */
    private static final int PANEL_WIDTH = 180;

    /** Spacing between sections. */
    private static final int SECTION_GAP = 15;

    // ─────────────────────── Colors (CERN/LHC theme) ─────────

    private static final Color BG_COLOR = new Color(6, 8, 16);
    private static final Color TEXT_COLOR = new Color(0, 200, 220);
    private static final Color LABEL_COLOR = new Color(80, 120, 140);
    private static final Color VALUE_COLOR = new Color(0, 240, 255);
    private static final Color SECTION_BG = new Color(10, 16, 28);
    private static final Color SECTION_BORDER = new Color(0, 80, 100);
    private static final Color ACTION_COLOR = new Color(255, 180, 40);

    // ─────────────────────── Fonts (monospace — data readout feel) ────

    private static final Font TITLE_FONT = new Font("Monospaced", Font.BOLD, 13);
    private static final Font VALUE_FONT = new Font("Monospaced", Font.BOLD, 18);
    private static final Font LABEL_FONT = new Font("Monospaced", Font.PLAIN, 10);
    private static final Font SMALL_FONT = new Font("Monospaced", Font.PLAIN, 10);

    // ─────────────────────── State ──────────────────────────────

    private GameState gameState;

    // ─────────────────────── Constructor ─────────────────────────

    public SidePanel(GameState gameState) {
        this.gameState = gameState;
        setBackground(BG_COLOR);
        // Height must accommodate: Hold(70) + Next(270) + Score(120) + Action(30) + Controls(~141)
        // plus SECTION_GAP(15)*4 gaps + top padding(10) = ~701px minimum
        setPreferredSize(new Dimension(PANEL_WIDTH, 710));
    }

    public void setGameState(GameState gameState) {
        this.gameState = gameState;
    }

    // ─────────────────────── Rendering ──────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        // Subtle CRT scanlines
        drawScanlines(g2);

        int y = 10; // current vertical drawing position

        // ──── HOLD section ────
        y = drawHoldSection(g2, y);
        y += SECTION_GAP;

        // ──── NEXT section ────
        y = drawNextSection(g2, y);
        y += SECTION_GAP;

        // ──── SCORE section ────
        y = drawScoreSection(g2, y);
        y += SECTION_GAP;

        // ──── LAST ACTION ────
        y = drawActionSection(g2, y);
        y += SECTION_GAP;

        // ──── CONTROLS section ────
        drawControlsSection(g2, y);
    }

    /**
     * Draws the HOLD piece section.
     *
     * @return y position after this section
     */
    private int drawHoldSection(Graphics2D g2, int y) {
        int boxHeight = 70;
        drawSectionBox(g2, y, boxHeight, "HOLD");

        TetrominoType holdType = gameState.getHoldPiece();
        if (holdType != null) {
            Color color = holdType.getColor();
            if (gameState.isHoldUsed()) {
                // Gray out if hold already used this piece
                color = new Color(80, 80, 80);
            }
            drawMiniPiece(g2, holdType, 10, y + 22, color);
        }

        return y + boxHeight;
    }

    /**
     * Draws the NEXT pieces preview section.
     *
     * @return y position after this section
     */
    private int drawNextSection(Graphics2D g2, int y) {
        List<TetrominoType> previews = gameState.getPreviewPieces();
        int boxHeight = 20 + previews.size() * 50;
        drawSectionBox(g2, y, boxHeight, "NEXT");

        int pieceY = y + 22;
        for (TetrominoType type : previews) {
            drawMiniPiece(g2, type, 10, pieceY, type.getColor());
            pieceY += 50;
        }

        return y + boxHeight;
    }

    /**
     * Draws the score/level/lines/combo section.
     *
     * @return y position after this section
     */
    private int drawScoreSection(Graphics2D g2, int y) {
        ScoreSystem score = gameState.getScoreSystem();

        int boxHeight = 120;
        drawSectionBox(g2, y, boxHeight, null);

        int textY = y + 18;
        int lineHeight = 28;

        // Score
        drawLabelValue(g2, "SCORE", String.valueOf(score.getScore()), textY);
        textY += lineHeight;

        // Level
        drawLabelValue(g2, "LEVEL", String.valueOf(score.getLevel()), textY);
        textY += lineHeight;

        // Lines
        drawLabelValue(g2, "LINES", String.valueOf(score.getTotalLinesCleared()), textY);
        textY += lineHeight;

        // Combo
        String comboText = score.getCombo() > 0 ? String.valueOf(score.getCombo()) : "-";
        drawLabelValue(g2, "COMBO", comboText, textY);

        return y + boxHeight;
    }

    /**
     * Draws the last action display (e.g., "B2B Tetris Combo 3").
     *
     * @return y position after this section
     */
    private int drawActionSection(Graphics2D g2, int y) {
        String action = gameState.getScoreSystem().getLastAction();
        if (action == null || action.isEmpty()) {
            return y;
        }

        int boxHeight = 30;
        drawSectionBox(g2, y, boxHeight, null);

        g2.setColor(ACTION_COLOR);
        g2.setFont(new Font("Monospaced", Font.BOLD, 12));
        FontMetrics fm = g2.getFontMetrics();
        int textWidth = fm.stringWidth(action);
        g2.drawString(action, (getWidth() - textWidth) / 2, y + 20);

        return y + boxHeight;
    }

    /**
     * Draws the controls reference section.
     * Key bindings are read dynamically from Settings.
     */
    private void drawControlsSection(Graphics2D g2, int y) {
        Settings s = Settings.get();
        String[] controls = {
            keyName(s.getKeyMoveLeft()) + "/" + keyName(s.getKeyMoveRight()) + "  Move",
            keyName(s.getKeySoftDrop()) + "  Soft Drop",
            keyName(s.getKeyHardDrop()) + "  Hard Drop",
            keyName(s.getKeyRotateCW()) + "  Rotate CW",
            keyName(s.getKeyRotateCCW()) + "  Rotate CCW",
            keyName(s.getKeyRotate180()) + "  Rotate 180",
            keyName(s.getKeyHold()) + "/" + keyName(s.getKeyHoldAlt()) + "  Hold",
            keyName(s.getKeyPause()) + "/" + keyName(s.getKeyPauseAlt()) + "  Pause",
            keyName(s.getKeyReset()) + "  Reset",
            keyName(s.getKeySettings()) + "  Settings"
        };

        int boxHeight = 15 + controls.length * 14;
        drawSectionBox(g2, y, boxHeight, "CONTROLS");

        g2.setFont(SMALL_FONT);
        g2.setColor(LABEL_COLOR);
        int textY = y + 24;
        for (String line : controls) {
            g2.drawString(line, 12, textY);
            textY += 14;
        }
    }

    // ─────────────────────── Helpers ────────────────────────────

    /**
     * Draws a rounded section box with an optional title.
     */
    private void drawSectionBox(Graphics2D g2, int y, int height, String title) {
        // Background
        g2.setColor(SECTION_BG);
        g2.fillRoundRect(4, y, getWidth() - 8, height, 8, 8);

        // Border
        g2.setColor(SECTION_BORDER);
        g2.setStroke(new BasicStroke(1));
        g2.drawRoundRect(4, y, getWidth() - 8, height, 8, 8);

        // Title
        if (title != null) {
            g2.setColor(TEXT_COLOR);
            g2.setFont(TITLE_FONT);
            g2.drawString(title, 12, y + 15);
        }
    }

    /**
     * Draws a label–value pair (e.g., "SCORE" and "12400").
     */
    private void drawLabelValue(Graphics2D g2, String label, String value, int y) {
        // Label (left-aligned)
        g2.setColor(LABEL_COLOR);
        g2.setFont(LABEL_FONT);
        g2.drawString(label, 12, y);

        // Value (right-aligned)
        g2.setColor(VALUE_COLOR);
        g2.setFont(VALUE_FONT);
        FontMetrics fm = g2.getFontMetrics();
        int valueWidth = fm.stringWidth(value);
        g2.drawString(value, getWidth() - 16 - valueWidth, y);
    }

    /**
     * Draws a miniature tetromino piece (for hold/next preview).
     *
     * Renders the piece in rotation state 0, centered horizontally
     * within the panel.
     *
     * @param g2    graphics context
     * @param type  the piece type to draw
     * @param x     left offset
     * @param y     top offset
     * @param color the color to use
     */
    private void drawMiniPiece(Graphics2D g2, TetrominoType type, int x, int y, Color color) {
        Position[] cells = type.getCells(0); // spawn orientation

        // Find the bounding box to center the piece
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (Position cell : cells) {
            minX = Math.min(minX, cell.getX());
            maxX = Math.max(maxX, cell.getX());
            minY = Math.min(minY, cell.getY());
            maxY = Math.max(maxY, cell.getY());
        }

        int pieceWidth = (maxX - minX + 1) * MINI_CELL;
        int offsetX = x + (getWidth() - 20 - pieceWidth) / 2;
        int offsetY = y + 5;

        for (Position cell : cells) {
            int cx = offsetX + (cell.getX() - minX) * MINI_CELL;
            int cy = offsetY + (cell.getY() - minY) * MINI_CELL;

            // Main fill
            g2.setColor(color);
            g2.fillRect(cx + 1, cy + 1, MINI_CELL - 2, MINI_CELL - 2);

            // Highlight
            g2.setColor(color.brighter());
            g2.fillRect(cx + 1, cy + 1, MINI_CELL - 2, 2);
            g2.fillRect(cx + 1, cy + 1, 2, MINI_CELL - 2);

            // Shadow
            g2.setColor(color.darker());
            g2.fillRect(cx + 1, cy + MINI_CELL - 3, MINI_CELL - 2, 2);
            g2.fillRect(cx + MINI_CELL - 3, cy + 1, 2, MINI_CELL - 2);
        }
    }

    /**
     * Returns a short display name for a key code.
     * Uses arrow symbols for arrow keys, and KeyEvent.getKeyText() for others.
     */
    private String keyName(int keyCode) {
        if (keyCode == 0) return "None";
        return switch (keyCode) {
            case KeyEvent.VK_LEFT  -> "\u2190";
            case KeyEvent.VK_RIGHT -> "\u2192";
            case KeyEvent.VK_UP    -> "\u2191";
            case KeyEvent.VK_DOWN  -> "\u2193";
            case KeyEvent.VK_SPACE -> "Space";
            case KeyEvent.VK_SHIFT -> "Shift";
            case KeyEvent.VK_ESCAPE -> "Esc";
            default -> KeyEvent.getKeyText(keyCode);
        };
    }

    /**
     * Draws subtle CRT-style scanlines and a drifting bright line.
     */
    private void drawScanlines(Graphics2D g2) {
        int w = getWidth(), h = getHeight();
        long time = System.currentTimeMillis();
        float scroll = (time % 8000) / 8000f * 20;
        g2.setColor(new Color(0, 160, 180, 14));
        for (float y = -20 + scroll; y < h; y += 4) {
            g2.drawLine(0, (int) y, w, (int) y);
        }
        float brightY = (time % 4000) / 4000f * h;
        g2.setColor(new Color(0, 220, 240, 30));
        g2.fillRect(0, (int) brightY - 1, w, 3);
    }
}
