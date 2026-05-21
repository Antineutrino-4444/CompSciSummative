package com.tetris.view;

import com.tetris.model.GameState;
import com.tetris.model.Position;
import com.tetris.model.ScoreSystem;
import com.tetris.model.TetrominoType;
import com.tetris.view.theme.BlockRenderer;
import com.tetris.view.theme.Theme;

import javax.swing.*;
import java.awt.*;

/**
 * SidePanel.java — TETR.IO-style left HUD column.
 *
 * Layout (top → bottom):
 *   • HOLD label + held piece preview (no heavy card chrome)
 *   • A thin separator
 *   • Stats stack: SCORE / LEVEL / LINES / TIME / PIECES / PPS
 *   • B2B chain badge + COMBO badge stacked beneath the stats
 *
 * The visual goal is the lean, content-first look used by tetr.io and
 * jstris: tiny labels, large mono numbers, almost no borders. Every
 * color and font comes from {@link Theme}.
 */
public class SidePanel extends JPanel {

    private static final int PREFERRED_WIDTH = 180;
    private static final int MINI_CELL       = 22;
    private static final int LABEL_GAP       = 6;
    private static final int STAT_ROW_H      = 34;
    private static final int SECTION_GAP     = 18;

    private GameState gameState;

    public SidePanel(GameState gameState) {
        this.gameState = gameState;
        setBackground(Theme.BG_0);
        setPreferredSize(new Dimension(PREFERRED_WIDTH, 720));
    }

    public void setGameState(GameState gameState) { this.gameState = gameState; }

    @Override
    protected void paintComponent(Graphics g) {
        long paintStartNs = System.nanoTime();
        super.paintComponent(g);
        try {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

            int y = 12;
            y = drawHold(g2, y);
            y += SECTION_GAP;
            drawDivider(g2, y - SECTION_GAP / 2);
            y = drawBadges(g2, y);
            y += SECTION_GAP;
            drawDivider(g2, y - SECTION_GAP / 2);
            drawStats(g2, y);
        } finally {
            SwingPaintDiagnostics.recordComponentPaint("SidePanel",
                    System.nanoTime() - paintStartNs);
        }
    }

    // ───────────────────── Sections ────────────────────────────

    private int drawHold(Graphics2D g2, int y) {
        int boxH = MINI_CELL * 2 + 28;
        drawSectionLabel(g2, "HOLD", y);
        // Subtle inset behind the piece — same vibe as TETR.IO's hold slot.
        int boxY = y + 18;
        int boxX = 10;
        int boxW = getWidth() - 20;
        g2.setColor(Theme.alpha(Theme.BG_1, 200));
        g2.fillRoundRect(boxX, boxY, boxW, boxH, Theme.RADIUS_M, Theme.RADIUS_M);
        g2.setColor(Theme.alpha(Theme.DIVIDER, 180));
        g2.drawRoundRect(boxX, boxY, boxW - 1, boxH - 1, Theme.RADIUS_M, Theme.RADIUS_M);

        TetrominoType holdType = gameState.getHoldPiece();
        if (holdType != null) {
            boolean used = gameState.isHoldUsed();
            Color color = used
                    ? Theme.blend(holdType.getColor(), Theme.BG_2, 0.8f)
                    : holdType.getColor();
            drawMiniPiece(g2, holdType, boxX, boxY, boxW, boxH, color, used ? 0.45f : 1f);
        }
        return boxY + boxH;
    }

    /** B2B chain + COMBO badges, drawn TETR.IO-style as bold side cards. */
    private int drawBadges(Graphics2D g2, int y) {
        ScoreSystem s = gameState.getScoreSystem();
        int chain = s.getB2bChain();
        int combo = s.getCombo();

        int badgeH = 44;
        int gap = 8;
        int x = 10;
        int w = getWidth() - 20;

        drawBadge(g2, x, y, w, badgeH,
                "B2B", chain >= 1 ? "x" + chain : "—",
                chain >= 1 ? Theme.HIGHLIGHT : Theme.TEXT_FAINT,
                chain >= 1);
        y += badgeH + gap;

        drawBadge(g2, x, y, w, badgeH,
                "COMBO", combo >= 1 ? "x" + combo : "—",
                combo >= 1 ? Theme.ACCENT_BRIGHT : Theme.TEXT_FAINT,
                combo >= 1);
        return y + badgeH;
    }

    private void drawBadge(Graphics2D g2, int x, int y, int w, int h,
                            String label, String value, Color tint, boolean active) {
        // Background — slightly brighter when active, like TETR.IO's
        // glowing chain counters.
        Color bg = active ? Theme.blend(Theme.BG_1, tint, 0.15f) : Theme.BG_1;
        g2.setColor(Theme.alpha(bg, 230));
        g2.fillRoundRect(x, y, w, h, Theme.RADIUS_M, Theme.RADIUS_M);
        g2.setColor(Theme.alpha(active ? tint : Theme.DIVIDER, active ? 200 : 180));
        g2.drawRoundRect(x, y, w - 1, h - 1, Theme.RADIUS_M, Theme.RADIUS_M);

        g2.setFont(Theme.FONT_CAPTION);
        g2.setColor(Theme.TEXT_MUTED);
        g2.drawString(label, x + 10, y + 15);

        g2.setFont(Theme.FONT_MONO_LARGE);
        g2.setColor(tint);
        FontMetrics fm = g2.getFontMetrics();
        int vw = fm.stringWidth(value);
        g2.drawString(value, x + w - 10 - vw, y + h - 12);
    }

    private void drawStats(Graphics2D g2, int y) {
        ScoreSystem s = gameState.getScoreSystem();
        long ms = s.getElapsedMs();
        int sec = (int) (ms / 1000);
        String time = String.format("%d:%02d", sec / 60, sec % 60);

        String[][] rows = {
            { "SCORE",  String.valueOf(s.getScore()) },
            { "LEVEL",  String.valueOf(s.getLevel()) },
            { "LINES",  String.valueOf(s.getTotalLinesCleared()) },
            { "TIME",   time },
            { "PIECES", String.valueOf(s.getPiecesPlaced()) },
            { "PPS",    String.format("%.2f", s.getPiecesPerSecond()) },
        };
        int rowY = y + 10;
        for (String[] r : rows) {
            drawStatRow(g2, r[0], r[1], rowY);
            rowY += STAT_ROW_H;
        }
    }

    private void drawStatRow(Graphics2D g2, String label, String value, int y) {
        g2.setFont(Theme.FONT_CAPTION);
        g2.setColor(Theme.TEXT_MUTED);
        g2.drawString(label, 14, y);

        g2.setFont(Theme.FONT_MONO_LARGE);
        g2.setColor(Theme.TEXT_PRIMARY);
        FontMetrics fm = g2.getFontMetrics();
        int vw = fm.stringWidth(value);
        g2.drawString(value, getWidth() - 14 - vw, y + LABEL_GAP + 14);
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
                                int boxX, int boxY, int boxW, int boxH,
                                Color color, float alpha) {
        Position[] cells = type.getCells(0);
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (Position c : cells) {
            minX = Math.min(minX, c.getX()); maxX = Math.max(maxX, c.getX());
            minY = Math.min(minY, c.getY()); maxY = Math.max(maxY, c.getY());
        }
        int pw = (maxX - minX + 1) * MINI_CELL;
        int ph = (maxY - minY + 1) * MINI_CELL;
        int ox = boxX + (boxW - pw) / 2;
        int oy = boxY + (boxH - ph) / 2;
        for (Position c : cells) {
            int cx = ox + (c.getX() - minX) * MINI_CELL;
            int cy = oy + (c.getY() - minY) * MINI_CELL;
            BlockRenderer.draw(g2, cx, cy, MINI_CELL, color, BlockRenderer.Style.SOLID, alpha);
        }
    }
}
