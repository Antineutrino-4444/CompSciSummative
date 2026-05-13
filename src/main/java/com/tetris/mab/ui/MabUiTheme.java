package com.tetris.mab.ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

/**
 * Step 20 Second Refinement — shared dark/readable styling for the
 * embedded MAB PvE dashboard.
 *
 * <p>Centralises colors, fonts, and small helper builders so every
 * compact card shares the same look without depending on the player
 * Tetris {@code Theme} (which targets the play surface).
 *
 * <p><b>Offline-only.</b>
 */
public final class MabUiTheme {

    private MabUiTheme() {}

    // ── Backgrounds ──
    public static final Color ROOT_BG    = new Color(0x0A, 0x0E, 0x16);
    public static final Color PANEL_BG   = new Color(0x14, 0x1A, 0x26);
    public static final Color CARD_BG    = new Color(0x18, 0x1F, 0x2E);
    public static final Color BANNER_BG  = new Color(0x10, 0x14, 0x1F);

    // ── Text ──
    public static final Color TEXT       = new Color(0xE8, 0xEC, 0xF2);
    public static final Color TEXT_MUTED = new Color(0x90, 0x9A, 0xAE);
    public static final Color TITLE      = new Color(0x9CC2FF);

    // ── Severity ──
    public static final Color INFO       = new Color(0x80, 0xB8, 0xFF);
    public static final Color SUCCESS    = new Color(0x70, 0xE0, 0xA0);
    public static final Color WARNING    = new Color(0xFF, 0xC0, 0x60);
    public static final Color CRITICAL   = new Color(0xFF, 0x70, 0x70);

    // ── Borders ──
    public static final Color DIVIDER    = new Color(0x2A, 0x33, 0x46);

    // ── Step 23 cold-war / nuclear-ops palette ──
    public static final Color SHELL_BG       = new Color(0x07, 0x0C, 0x14);
    public static final Color SHELL_PANEL_BG = new Color(0x0C, 0x14, 0x1E);
    public static final Color SHELL_DEEP_BG  = new Color(0x04, 0x08, 0x0E);
    public static final Color GRID_LINE      = new Color(0x14, 0x28, 0x3D);
    public static final Color GRID_LINE_HI   = new Color(0x1E, 0x3D, 0x5C);
    public static final Color C_CYAN         = new Color(0x00, 0xE5, 0xE0);
    public static final Color C_CYAN_DIM     = new Color(0x0E, 0x59, 0x63);
    public static final Color C_GREEN        = new Color(0x34, 0xF8, 0x6E);
    public static final Color C_GREEN_DIM    = new Color(0x15, 0x5E, 0x2A);
    public static final Color C_AMBER        = new Color(0xFF, 0xB3, 0x00);
    public static final Color C_AMBER_DIM    = new Color(0x6A, 0x4A, 0x00);
    public static final Color C_RED          = new Color(0xFF, 0x31, 0x42);
    public static final Color C_RED_DIM      = new Color(0x6E, 0x10, 0x18);
    public static final Color C_MAGENTA      = new Color(0xFF, 0x3D, 0xC9);
    public static final Color TEXT_BRIGHT    = new Color(0xE0, 0xEE, 0xF8);
    public static final Color TEXT_FAINT    = new Color(0x6F, 0x82, 0x94);
    public static final Color TEXT_GHOST     = new Color(0x3C, 0x4E, 0x5E);

    // ── Fonts ──
    public static final Font  TITLE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    public static final Font  BODY_FONT  = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    public static final Font  BODY_BOLD  = new Font(Font.MONOSPACED, Font.BOLD, 12);
    public static final Font  BIG_FONT   = new Font(Font.SANS_SERIF, Font.BOLD, 16);
    public static final Font  HUGE_FONT  = new Font(Font.SANS_SERIF, Font.BOLD, 22);

    /** Step 23 \u2014 condensed headline fonts. Impact is intentionally excluded:
     *  it renders with excessive fuzziness in Java Swing at all sizes.
     *  Cross-platform fallback chain: Bahnschrift (Win10+) \u2192 Franklin Gothic
     *  Medium (Win) \u2192 Segoe UI (Win) \u2192 Helvetica Neue (macOS) \u2192 Ubuntu /
     *  Liberation Sans (Linux) \u2192 JVM logical SansSerif. */
    public static final Font STENCIL_HEADLINE = pickFont(26, Font.BOLD,
            "Bahnschrift", "Franklin Gothic Medium", "Segoe UI",
            "Helvetica Neue", "Ubuntu", "Liberation Sans", Font.SANS_SERIF);
    public static final Font STENCIL_MID = pickFont(15, Font.BOLD,
            "Bahnschrift", "Franklin Gothic Medium", "Segoe UI",
            "Helvetica Neue", "Ubuntu", "Liberation Sans", Font.SANS_SERIF);
    public static final Font STENCIL_SMALL = pickFont(12, Font.BOLD,
            "Bahnschrift", "Franklin Gothic Medium", "Segoe UI",
            "Helvetica Neue", "Ubuntu", "Liberation Sans", Font.SANS_SERIF);
    public static final Font TERM_TINY = pickFont(9, Font.PLAIN,
            "Consolas", "Lucida Console", Font.MONOSPACED);
    public static final Font TERM_SMALL = pickFont(10, Font.PLAIN,
            "Consolas", "Lucida Console", Font.MONOSPACED);
    public static final Font TERM_MED = pickFont(13, Font.PLAIN,
            "Consolas", "Lucida Console", Font.MONOSPACED);
    public static final Font TERM_BIG = pickFont(18, Font.BOLD,
            "Consolas", "Lucida Console", Font.MONOSPACED);

    private static Font pickFont(int size, int style, String... preferred) {
        java.util.Set<String> available = new java.util.HashSet<>();
        for (String name : java.awt.GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames()) {
            available.add(name);
        }
        for (String name : preferred) {
            if (available.contains(name)) return new Font(name, style, size);
        }
        // Final fallback
        return new Font(preferred[preferred.length - 1], style, size);
    }

    /** Compound border: thin divider line + inner padding. */
    public static Border cardBorder() {
        return new CompoundBorder(new LineBorder(DIVIDER, 1, true),
                new EmptyBorder(4, 8, 6, 8));
    }

    public static Border padding(int top, int left, int bottom, int right) {
        return new EmptyBorder(top, left, bottom, right);
    }

    /** Small uppercase title shown at the top of a card. */
    public static JLabel titleLabel(String text) {
        JLabel l = new JLabel(text == null ? "" : text.toUpperCase());
        l.setFont(TITLE_FONT);
        l.setForeground(TITLE);
        return l;
    }

    /** Body text label. */
    public static JLabel bodyLabel(String text) {
        JLabel l = new JLabel(text == null ? " " : text);
        l.setFont(BODY_FONT);
        l.setForeground(TEXT);
        return l;
    }

    /** Muted body label for secondary info. */
    public static JLabel mutedLabel(String text) {
        JLabel l = new JLabel(text == null ? " " : text);
        l.setFont(BODY_FONT);
        l.setForeground(TEXT_MUTED);
        return l;
    }

    /**
     * Build a compact "card" with a title bar and a body component.
     * The card uses {@link #CARD_BG}, has {@link #cardBorder()} and
     * is opaque so it doesn't inherit a parent's white background.
     */
    public static JPanel card(String title, Component body) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setBackground(CARD_BG);
        p.setOpaque(true);
        p.setBorder(cardBorder());
        if (title != null && !title.isEmpty()) {
            p.add(titleLabel(title), BorderLayout.NORTH);
        }
        if (body != null) {
            p.add(body, BorderLayout.CENTER);
        }
        return p;
    }

    /** Apply dark style to an existing panel (background + opaque). */
    public static void styleAsRoot(JPanel p) {
        p.setBackground(ROOT_BG);
        p.setOpaque(true);
    }

    /** Apply dark style to an existing panel (PANEL_BG). */
    public static void styleAsPanel(JPanel p) {
        p.setBackground(PANEL_BG);
        p.setOpaque(true);
    }

    /** Right-aligned single-line stat label. */
    public static JLabel statValue(String text, Color color) {
        JLabel l = new JLabel(text == null ? " " : text, SwingConstants.RIGHT);
        l.setFont(BODY_BOLD);
        l.setForeground(color == null ? TEXT : color);
        return l;
    }

    /** Banner background by severity. */
    public static Color bannerBgFor(Color fg) {
        return BANNER_BG;
    }

    /**
     * Create a 2-column "label : value" row.
     */
    public static JPanel kvRow(String key, JLabel value) {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setOpaque(false);
        JLabel k = mutedLabel(key);
        p.add(k, BorderLayout.WEST);
        p.add(value, BorderLayout.CENTER);
        return p;
    }
}
