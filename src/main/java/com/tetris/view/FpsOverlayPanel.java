package com.tetris.view;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Small always-on FPS readout mounted above the active game surface.
 */
public final class FpsOverlayPanel extends JPanel {

    private static final Color BG = new Color(0x05, 0x08, 0x0C, 205);
    private static final Color BORDER = new Color(0x45, 0xFF, 0xA5, 170);
    private static final Color TEXT = new Color(0xD8, 0xFF, 0xE6);
    private static final Color DIM = new Color(0x7D, 0xD8, 0xA8);
    private static final Font FONT = new Font("Monospaced", Font.BOLD, 12);
    private static final int PAD_X = 8;
    private static final int PAD_Y = 5;

    private int fps;
    private String text = "FPS --";

    public FpsOverlayPanel() {
        setOpaque(false);
        setFocusable(false);
        setFps(0);
    }

    public void setFps(int fps) {
        int safe = Math.max(0, fps);
        if (this.fps == safe) return;
        this.fps = safe;
        this.text = safe <= 0 ? "FPS --" : "FPS " + safe;
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(FONT);
        int w = fm.stringWidth(text) + PAD_X * 2;
        int h = fm.getHeight() + PAD_Y * 2;
        return new Dimension(Math.max(64, w), h);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(BG);
            g2.fillRoundRect(0, 0, w - 1, h - 1, 6, 6);
            g2.setColor(BORDER);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 6, 6);

            g2.setFont(FONT);
            FontMetrics fm = g2.getFontMetrics();
            int split = text.indexOf(' ');
            String label = split < 0 ? text : text.substring(0, split + 1);
            String value = split < 0 ? "" : text.substring(split + 1);
            int x = PAD_X;
            int y = PAD_Y + fm.getAscent();
            g2.setColor(DIM);
            g2.drawString(label, x, y);
            g2.setColor(TEXT);
            g2.drawString(value, x + fm.stringWidth(label), y);
        } finally {
            g2.dispose();
        }
    }
}
