package com.tetris.view.theme;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;

/**
 * BlockRenderer.java
 * ==================
 * Single source of truth for drawing one Tetris cell. Used by both the
 * playfield and the hold / next previews so cells look identical
 * everywhere.
 *
 * Three styles are supported:
 *   - SOLID   — a full opaque block with a 3D bevel (locked + active).
 *   - GHOST   — translucent fill, no bevel (drop preview).
 *   - LOCKED  — flashed white briefly when a line clear starts.
 */
public final class BlockRenderer {

    private BlockRenderer() {}

    public enum Style { SOLID, GHOST, FLASH }

    /**
     * @param g2     graphics context (will not be disposed)
     * @param x      pixel x of the cell's top-left
     * @param y      pixel y of the cell's top-left
     * @param size   cell size in px
     * @param color  the tetromino color
     * @param style  rendering style
     * @param alpha  0..1 — multiplied into the final alpha for animations
     */
    public static void draw(Graphics2D g2, int x, int y, int size,
                             Color color, Style style, float alpha) {
        AlphaComposite prev = (AlphaComposite) g2.getComposite();
        if (alpha < 0.999f) {
            g2.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, alpha))));
        }
        switch (style) {
            case GHOST -> drawGhost(g2, x, y, size, color);
            case FLASH -> drawFlash(g2, x, y, size);
            case SOLID -> drawSolid(g2, x, y, size, color);
        }
        if (alpha < 0.999f) g2.setComposite(prev);
    }

    public static void draw(Graphics2D g2, int x, int y, int size, Color color, Style style) {
        draw(g2, x, y, size, color, style, 1f);
    }

    private static void drawSolid(Graphics2D g2, int x, int y, int size, Color color) {
        // Body
        g2.setColor(color);
        g2.fillRect(x + 1, y + 1, size - 2, size - 2);

        // Top + left highlight
        Color hl = brighten(color, 0.35f);
        g2.setColor(hl);
        g2.fillRect(x + 1, y + 1, size - 2, 2);
        g2.fillRect(x + 1, y + 1, 2, size - 2);

        // Bottom + right shadow
        Color sh = darken(color, 0.40f);
        g2.setColor(sh);
        g2.fillRect(x + 1, y + size - 3, size - 2, 2);
        g2.fillRect(x + size - 3, y + 1, 2, size - 2);

        // Subtle inner highlight for a glassy look
        g2.setColor(Theme.alpha(Color.WHITE, 25));
        g2.fillRect(x + 3, y + 3, size - 6, 1);
    }

    private static void drawGhost(Graphics2D g2, int x, int y, int size, Color color) {
        // Translucent fill + bold colored outline so the player can
        // clearly read where the active piece will land.
        g2.setColor(Theme.alpha(color, 60));
        g2.fillRect(x + 1, y + 1, size - 2, size - 2);

        java.awt.Stroke prev = g2.getStroke();
        g2.setStroke(new java.awt.BasicStroke(2f));
        g2.setColor(Theme.alpha(color, 230));
        g2.drawRect(x + 2, y + 2, size - 4, size - 4);
        g2.setStroke(prev);
    }

    private static void drawFlash(Graphics2D g2, int x, int y, int size) {
        g2.setColor(Color.WHITE);
        g2.fillRect(x + 1, y + 1, size - 2, size - 2);
    }

    private static Color brighten(Color c, float pct) {
        int r = (int) Math.min(255, c.getRed()   + (255 - c.getRed())   * pct);
        int g = (int) Math.min(255, c.getGreen() + (255 - c.getGreen()) * pct);
        int b = (int) Math.min(255, c.getBlue()  + (255 - c.getBlue())  * pct);
        return new Color(r, g, b);
    }

    private static Color darken(Color c, float pct) {
        int r = (int) Math.max(0, c.getRed()   * (1 - pct));
        int g = (int) Math.max(0, c.getGreen() * (1 - pct));
        int b = (int) Math.max(0, c.getBlue()  * (1 - pct));
        return new Color(r, g, b);
    }
}
