package com.tetris.view;

import javax.swing.*;
import java.awt.*;

/**
 * F3 debug / performance overlay.
 *
 * Renders a compact stat block in the top-left corner of its parent.
 * Mount this panel in a JLayeredPane above POPUP_LAYER and call
 * {@link #update(String)} each render frame when visible.
 */
public final class PerfOverlayPanel extends JPanel {

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
     * Prefix a line with "§h" for header colour, "§w" for warning colour.
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

        // Measure widest visible line (strip colour prefix).
        int maxW = 0;
        for (String l : lines) maxW = Math.max(maxW, fm.stringWidth(strip(l)));

        int bgW = maxW + PAD * 2;
        int bgH = lines.length * lineH + PAD * 2;

        // Background tile.
        g2.setColor(BG_COL);
        g2.fillRoundRect(PAD, PAD, bgW, bgH, 5, 5);

        // Text rows.
        int y = PAD + fm.getAscent() + PAD;
        for (String l : lines) {
            if      (l.startsWith("§h")) { g2.setColor(FG_HEAD);  l = l.substring(2); }
            else if (l.startsWith("§w")) { g2.setColor(FG_WARN);  l = l.substring(2); }
            else if (l.startsWith("§l")) { g2.setColor(FG_LABEL); l = l.substring(2); }
            else                          { g2.setColor(FG_VALUE); }
            g2.drawString(l, PAD * 2, y);
            y += lineH;
        }
        g2.dispose();
    }

    private static String strip(String l) {
        return (l.startsWith("§h") || l.startsWith("§w") || l.startsWith("§l"))
                ? l.substring(2) : l;
    }
}
