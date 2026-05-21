package com.tetris.view;

import javax.swing.*;
import java.awt.*;

/**
 * F3 debug / performance overlay.
 *
 * Renders a stat block in the top-left corner of its parent, flowing into
 * additional columns when the diagnostic text is taller than the window.
 * Mount this panel in a JLayeredPane above POPUP_LAYER and call
 * {@link #update(String)} each render frame when visible.
 */
public final class PerfOverlayPanel extends JPanel {

    private static final char MARKUP = '\u00a7';
    private static final Color BG_COL   = new Color(0x08, 0x08, 0x08, 190);
    private static final Color FG_LABEL = new Color(0x80, 0xFF, 0x80);
    private static final Color FG_VALUE = new Color(0xD8, 0xFF, 0xD8);
    private static final Color FG_HEAD  = new Color(0x40, 0xFF, 0xA0);
    private static final Color FG_WARN  = new Color(0xFF, 0xD0, 0x40);
    private static final Font  FONT     = new Font("Monospaced", Font.PLAIN, 12);
    private static final int   PAD      = 7;

    private String[] lines = new String[0];

    public PerfOverlayPanel() {
        setOpaque(false);
        setFocusable(false);
    }

    /**
     * Replace the displayed text. Each newline becomes a new row.
     * Prefix a line with "\u00a7h" for header colour, "\u00a7w" for warning colour.
     * Lines without a prefix use the default value colour.
     */
    public void update(String text) {
        lines = (text == null || text.isBlank()) ? new String[0] : text.split("\n", -1);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (lines.length == 0 || !isVisible()) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);
        g2.setFont(FONT);
        FontMetrics fm = g2.getFontMetrics();
        int lineH = fm.getHeight();
        int rowsPerColumn = Math.max(1, (getHeight() - PAD * 4) / lineH);

        int x = PAD;
        for (int start = 0; start < lines.length; start += rowsPerColumn) {
            int end = Math.min(lines.length, start + rowsPerColumn);
            int columnW = columnWidth(fm, start, end);
            paintColumn(g2, fm, lineH, x, start, end, columnW);
            x += columnW + PAD;
            if (x >= getWidth()) break;
        }
        g2.dispose();
    }

    private void paintColumn(Graphics2D g2, FontMetrics fm, int lineH,
                             int x, int start, int end, int columnW) {
        int bgH = (end - start) * lineH + PAD * 2;

        g2.setColor(BG_COL);
        g2.fillRoundRect(x, PAD, columnW, bgH, 5, 5);

        int y = PAD + fm.getAscent() + PAD;
        for (int i = start; i < end; i++) {
            String line = lines[i];
            char style = stylePrefix(line);
            if      (style == 'h') g2.setColor(FG_HEAD);
            else if (style == 'w') g2.setColor(FG_WARN);
            else if (style == 'l') g2.setColor(FG_LABEL);
            else                   g2.setColor(FG_VALUE);
            g2.drawString(strip(line), x + PAD, y);
            y += lineH;
        }
    }

    private int columnWidth(FontMetrics fm, int start, int end) {
        int maxW = 0;
        for (int i = start; i < end; i++) {
            maxW = Math.max(maxW, fm.stringWidth(strip(lines[i])));
        }
        return maxW + PAD * 2;
    }

    private static String strip(String line) {
        int len = prefixLength(line);
        return len == 0 ? line : line.substring(len);
    }

    private static char stylePrefix(String line) {
        int len = prefixLength(line);
        return len == 0 ? '\0' : line.charAt(len - 1);
    }

    private static int prefixLength(String line) {
        if (line == null) return 0;
        if (line.length() >= 2
                && line.charAt(0) == MARKUP
                && isStyle(line.charAt(1))) {
            return 2;
        }
        if (line.length() >= 3
                && line.charAt(0) == '\u00c2'
                && line.charAt(1) == MARKUP
                && isStyle(line.charAt(2))) {
            return 3;
        }
        return 0;
    }

    private static boolean isStyle(char c) {
        return c == 'h' || c == 'w' || c == 'l';
    }
}
