package com.tetris.view.theme;

import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.Color;
import java.awt.Font;

/**
 * Theme.java
 * ==========
 * Single source of truth for the application's visual identity.
 *
 * Every color, font, padding, radius, and stroke used anywhere in the
 * UI is declared here. View classes must never construct their own
 * {@code new Color(...)} or {@code new Font(...)} — they pull tokens
 * from this class so the look stays consistent and the palette can be
 * retuned in one place.
 *
 * The visual identity is a "data-room" / control-panel aesthetic:
 * very dark blue-black backgrounds, a layered family of cyan accents
 * for the primary brand color, warm amber for emphasis, and a small
 * set of state colors (warn / danger / success).
 */
public final class Theme {

    private Theme() {}

    // ───────────────────── Color tokens ───────────────────────────
    // bg.0 = deepest background (window fill)
    // bg.1 = panel surface (cards / sections)
    // bg.2 = elevated surface (drawer / popup)
    // bg.3 = highlighted surface (selected row, hovered button)

    public static final Color BG_0 = new Color(6, 9, 16);
    public static final Color BG_1 = new Color(12, 18, 30);
    public static final Color BG_2 = new Color(18, 26, 42);
    public static final Color BG_3 = new Color(28, 40, 60);

    /** Subtle separator between surfaces. */
    public static final Color DIVIDER = new Color(28, 38, 56);

    // Primary brand accent: electric cyan, plus a dimmed variant for
    // borders and inactive chrome.
    public static final Color ACCENT          = new Color(0, 210, 230);
    public static final Color ACCENT_BRIGHT   = new Color(80, 240, 255);
    public static final Color ACCENT_DIM      = new Color(0, 130, 150);
    public static final Color ACCENT_SUBTLE   = new Color(0, 80, 100);

    // Warm amber for "you are here" / focus emphasis. Used sparingly.
    public static final Color HIGHLIGHT       = new Color(255, 190, 90);
    public static final Color HIGHLIGHT_DIM   = new Color(180, 130, 60);

    // State colors
    public static final Color SUCCESS = new Color(120, 220, 140);
    public static final Color WARN    = new Color(255, 180, 70);
    public static final Color DANGER  = new Color(255, 95, 95);

    // Text
    public static final Color TEXT_PRIMARY = new Color(220, 232, 240);
    public static final Color TEXT_BODY    = new Color(180, 198, 215);
    public static final Color TEXT_MUTED   = new Color(120, 142, 162);
    public static final Color TEXT_FAINT   = new Color(80, 100, 120);
    public static final Color TEXT_ON_ACCENT = new Color(8, 14, 22);

    // ───────────────────── Spacing scale ──────────────────────────
    // Increments of 4 px, named by t-shirt size.
    public static final int SPACE_XS  = 4;
    public static final int SPACE_S   = 8;
    public static final int SPACE_M   = 12;
    public static final int SPACE_L   = 16;
    public static final int SPACE_XL  = 24;
    public static final int SPACE_XXL = 36;

    // ───────────────────── Radii ──────────────────────────────────
    public static final int RADIUS_S  = 4;
    public static final int RADIUS_M  = 8;
    public static final int RADIUS_L  = 12;

    // ───────────────────── Typography ─────────────────────────────
    // One sans-serif family and one mono family. Sizes follow a
    // simple modular scale.
    private static final String SANS = "SansSerif";
    private static final String MONO = "Monospaced";

    public static final Font FONT_DISPLAY   = new Font(SANS, Font.BOLD,   34);
    public static final Font FONT_TITLE     = new Font(SANS, Font.BOLD,   22);
    public static final Font FONT_H1        = new Font(SANS, Font.BOLD,   18);
    public static final Font FONT_H2        = new Font(SANS, Font.BOLD,   14);
    public static final Font FONT_BODY      = new Font(SANS, Font.PLAIN,  13);
    public static final Font FONT_BODY_BOLD = new Font(SANS, Font.BOLD,   13);
    public static final Font FONT_CAPTION   = new Font(SANS, Font.PLAIN,  11);
    public static final Font FONT_SMALL     = new Font(SANS, Font.PLAIN,  10);

    public static final Font FONT_MONO_TITLE = new Font(MONO, Font.BOLD,  14);
    public static final Font FONT_MONO       = new Font(MONO, Font.PLAIN, 12);
    public static final Font FONT_MONO_BOLD  = new Font(MONO, Font.BOLD,  12);
    public static final Font FONT_MONO_LARGE = new Font(MONO, Font.BOLD,  20);
    public static final Font FONT_MONO_HUGE  = new Font(MONO, Font.BOLD,  28);

    // ───────────────────── Borders ────────────────────────────────

    public static Border padding(int v, int h) {
        return new EmptyBorder(v, h, v, h);
    }

    public static Border padding(int t, int l, int b, int r) {
        return new EmptyBorder(t, l, b, r);
    }

    public static Border card() {
        return new CompoundBorder(
                new LineBorder(DIVIDER, 1, true),
                new EmptyBorder(SPACE_M, SPACE_M, SPACE_M, SPACE_M));
    }

    public static Border cardElevated() {
        return new CompoundBorder(
                new LineBorder(ACCENT_DIM, 1, true),
                new EmptyBorder(SPACE_M, SPACE_M, SPACE_M, SPACE_M));
    }

    /** Outer border for a focused / active card. */
    public static Border cardActive() {
        return new CompoundBorder(
                new LineBorder(ACCENT, 1, true),
                new EmptyBorder(SPACE_M, SPACE_M, SPACE_M, SPACE_M));
    }

    // ───────────────────── Helpers ────────────────────────────────

    /** Returns a copy of {@code c} with the given alpha (0-255). */
    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(),
                Math.max(0, Math.min(255, a)));
    }

    /** Linear blend a→b. t=0 returns a, t=1 returns b. */
    public static Color blend(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r  = (int) (a.getRed()   * (1 - t) + b.getRed()   * t);
        int g  = (int) (a.getGreen() * (1 - t) + b.getGreen() * t);
        int bl = (int) (a.getBlue()  * (1 - t) + b.getBlue()  * t);
        return new Color(r, g, bl);
    }
}
