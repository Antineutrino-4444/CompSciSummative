package com.tetris.view;

import com.tetris.model.GameState;
import com.tetris.model.Position;
import com.tetris.model.Settings;
import com.tetris.model.TetrominoType;
import com.tetris.view.theme.BlockRenderer;
import com.tetris.view.theme.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * NextPanel.java — TETR.IO-style right HUD column.
 *
 * Shows the upcoming piece queue (vertical stack — first-up larger than
 * the rest, like tetr.io and jstris) plus a small controls cheatsheet
 * underneath. The column has the same minimal aesthetic as
 * {@link SidePanel}: tiny labels, almost-invisible chrome, and the
 * shared {@link BlockRenderer} for cells.
 */
public class NextPanel extends JPanel {

    private static final int PREFERRED_WIDTH = 180;
    private static final int FIRST_CELL      = 26;
    private static final int REST_CELL       = 18;
    private static final int FIRST_BOX_PAD   = 10;
    private static final int REST_BOX_PAD    = 6;

    private GameState gameState;

    public NextPanel(GameState gameState) {
        this.gameState = gameState;
        setBackground(Theme.BG_0);
        setPreferredSize(new Dimension(PREFERRED_WIDTH, 720));
    }

    public void setGameState(GameState gameState) { this.gameState = gameState; }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        int y = 12;
        y = drawNext(g2, y);
        y += 18;
        drawDivider(g2, y - 9);
        drawControls(g2, y);
    }

    // ───────────────────── Sections ────────────────────────────

    private int drawNext(Graphics2D g2, int y) {
        drawSectionLabel(g2, "NEXT", y);
        y += 18;

        List<TetrominoType> previews = gameState.getPreviewPieces();
        if (previews.isEmpty()) return y;

        // First piece — bigger, styled as a slot.
        int boxX = 10;
        int boxW = getWidth() - 20;
        int firstH = FIRST_CELL * 3 + FIRST_BOX_PAD * 2;
        g2.setColor(Theme.alpha(Theme.BG_1, 200));
        g2.fillRoundRect(boxX, y, boxW, firstH, Theme.RADIUS_M, Theme.RADIUS_M);
        g2.setColor(Theme.alpha(Theme.DIVIDER, 180));
        g2.drawRoundRect(boxX, y, boxW - 1, firstH - 1, Theme.RADIUS_M, Theme.RADIUS_M);
        drawMiniPiece(g2, previews.get(0), boxX, y, boxW, firstH, FIRST_CELL);
        y += firstH + 8;

        // Remaining previews — tighter, no slot border.
        int restH = REST_CELL * 2 + REST_BOX_PAD * 2;
        for (int i = 1; i < previews.size(); i++) {
            drawMiniPiece(g2, previews.get(i), boxX, y, boxW, restH, REST_CELL);
            y += restH + 4;
        }
        return y;
    }

    private void drawControls(Graphics2D g2, int y) {
        Settings s = Settings.get();
        drawSectionLabel(g2, "CONTROLS", y);
        int rowY = y + 26;
        int rowH = 16;

        String[][] rows = {
            { keyName(s.getKeyMoveLeft()) + " / " + keyName(s.getKeyMoveRight()), "Move" },
            { keyName(s.getKeySoftDrop()),  "Soft drop" },
            { keyName(s.getKeyHardDrop()),  "Hard drop" },
            { keyName(s.getKeyRotateCW()),  "Rotate \u21bb" },
            { keyName(s.getKeyRotateCCW()), "Rotate \u21ba" },
            { keyName(s.getKeyRotate180()), "Rotate 180\u00b0" },
            { keyName(s.getKeyHold()),      "Hold" },
            { keyName(s.getKeyPause()),     "Pause" },
            { keyName(s.getKeyReset()),     "Reset" },
            { keyName(s.getKeySettings()),  "Settings" },
        };
        g2.setFont(Theme.FONT_MONO);
        FontMetrics fm = g2.getFontMetrics();
        int leftX = 14;
        int rightX = getWidth() - 14;
        for (String[] r : rows) {
            g2.setColor(Theme.TEXT_MUTED);
            g2.drawString(r[1], leftX, rowY);
            g2.setColor(Theme.ACCENT);
            int kw = fm.stringWidth(r[0]);
            g2.drawString(r[0], rightX - kw, rowY);
            rowY += rowH;
        }
    }

    // ───────────────────── Helpers ────────────────────────────

    private void drawSectionLabel(Graphics2D g2, String label, int y) {
        g2.setFont(Theme.FONT_MONO_BOLD);
        g2.setColor(Theme.ACCENT);
        g2.drawString(label, 14, y + 12);
    }

    private void drawDivider(Graphics2D g2, int y) {
        g2.setColor(Theme.alpha(Theme.DIVIDER, 200));
        g2.fillRect(14, y, getWidth() - 28, 1);
    }

    private void drawMiniPiece(Graphics2D g2, TetrominoType type,
                                int boxX, int boxY, int boxW, int boxH, int cell) {
        Position[] cells = type.getCells(0);
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (Position c : cells) {
            minX = Math.min(minX, c.getX()); maxX = Math.max(maxX, c.getX());
            minY = Math.min(minY, c.getY()); maxY = Math.max(maxY, c.getY());
        }
        int pw = (maxX - minX + 1) * cell;
        int ph = (maxY - minY + 1) * cell;
        int ox = boxX + (boxW - pw) / 2;
        int oy = boxY + (boxH - ph) / 2;
        for (Position c : cells) {
            int cx = ox + (c.getX() - minX) * cell;
            int cy = oy + (c.getY() - minY) * cell;
            BlockRenderer.draw(g2, cx, cy, cell, type.getColor(), BlockRenderer.Style.SOLID);
        }
    }

    private static String keyName(int keyCode) {
        if (keyCode == 0) return "—";
        return switch (keyCode) {
            case KeyEvent.VK_LEFT  -> "\u2190";
            case KeyEvent.VK_RIGHT -> "\u2192";
            case KeyEvent.VK_UP    -> "\u2191";
            case KeyEvent.VK_DOWN  -> "\u2193";
            case KeyEvent.VK_SPACE -> "Space";
            case KeyEvent.VK_SHIFT -> "Shift";
            case KeyEvent.VK_ESCAPE -> "Esc";
            case KeyEvent.VK_ENTER -> "Enter";
            default -> KeyEvent.getKeyText(keyCode);
        };
    }
}
