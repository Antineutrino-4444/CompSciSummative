package com.tetris.view.theme;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Components.java
 * ===============
 * Factory helpers that produce styled Swing components which conform to
 * {@link Theme}. Consumers should prefer these over hand-styling each
 * widget so the visual language stays consistent.
 */
public final class Components {

    private Components() {}

    public static enum ButtonStyle {
        /** Bold accent fill — used for the single dominant action on a screen. */
        PRIMARY,
        /** Outlined / ghost — most actions. */
        SECONDARY,
        /** Subtle text-only — tertiary actions. */
        TEXT,
        /** Red-tinted — destructive actions (Quit, Reset Build). */
        DANGER
    }

    /** Builds a polished button with hover / pressed feedback. */
    public static JButton button(String text, ButtonStyle style) {
        FlatButton b = new FlatButton(text, style);
        b.setFont(Theme.FONT_BODY_BOLD);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(Theme.SPACE_S, Theme.SPACE_L, Theme.SPACE_S, Theme.SPACE_L));
        return b;
    }

    /** A label with the given style applied. */
    public static JLabel label(String text, Font font, Color fg) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(fg);
        return l;
    }

    public static JLabel title(String text) {
        return label(text, Theme.FONT_TITLE, Theme.ACCENT);
    }

    public static JLabel sectionHeader(String text) {
        JLabel l = label(text.toUpperCase(), Theme.FONT_MONO_BOLD, Theme.ACCENT);
        return l;
    }

    public static JLabel body(String text) {
        return label(text, Theme.FONT_BODY, Theme.TEXT_BODY);
    }

    public static JLabel muted(String text) {
        return label(text, Theme.FONT_CAPTION, Theme.TEXT_MUTED);
    }

    /** A panel pre-painted with the chosen background. */
    public static JPanel panel(Color bg) {
        JPanel p = new JPanel();
        p.setBackground(bg);
        return p;
    }

    public static JPanel panel(Color bg, java.awt.LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setBackground(bg);
        return p;
    }

    /** A "card" container — rounded line border + standard padding. */
    public static JPanel card() {
        JPanel p = new JPanel();
        p.setBackground(Theme.BG_1);
        p.setBorder(Theme.card());
        return p;
    }

    public static JPanel card(java.awt.LayoutManager layout) {
        JPanel p = card();
        p.setLayout(layout);
        return p;
    }

    /** A card with a header strip on top. */
    public static JPanel sectionCard(String title) {
        JPanel card = panel(Theme.BG_1, new BorderLayout(0, Theme.SPACE_S));
        card.setBorder(Theme.card());
        if (title != null && !title.isEmpty()) {
            JLabel head = sectionHeader(title);
            head.setBorder(new EmptyBorder(0, 0, Theme.SPACE_XS, 0));
            card.add(head, BorderLayout.NORTH);
        }
        return card;
    }

    /** Wraps content in a styled scroll pane (transparent, thin scrollbar). */
    public static JScrollPane scrollPane(JComponent inner) {
        JScrollPane sp = new JScrollPane(inner);
        sp.setBorder(null);
        sp.setBackground(Theme.BG_1);
        sp.getViewport().setBackground(Theme.BG_1);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);

        styleScrollBar(sp.getVerticalScrollBar());
        styleScrollBar(sp.getHorizontalScrollBar());
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    private static void styleScrollBar(JScrollBar sb) {
        sb.setPreferredSize(new Dimension(8, 8));
        sb.setBackground(Theme.BG_0);
        sb.setUI(new BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                this.thumbColor = Theme.ACCENT_DIM;
                this.trackColor = Theme.BG_0;
            }
            @Override protected JButton createDecreaseButton(int o) { return zeroButton(); }
            @Override protected JButton createIncreaseButton(int o) { return zeroButton(); }
            private JButton zeroButton() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                b.setMinimumSize(new Dimension(0, 0));
                b.setMaximumSize(new Dimension(0, 0));
                return b;
            }
        });
    }

    // ─────────────────── FlatButton (internal) ───────────────────

    /**
     * Minimal flat button with hover / pressed alpha feedback and a
     * rounded fill. Avoids native L&F entirely so our buttons look
     * identical on every OS.
     */
    public static class FlatButton extends JButton {
        private final ButtonStyle style;
        private boolean hovered = false;
        private boolean pressed = false;

        FlatButton(String text, ButtonStyle style) {
            super(text);
            this.style = style;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setForeground(textColor());
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true;  repaint(); }
                @Override public void mouseExited (MouseEvent e) { hovered = false; pressed = false; repaint(); }
                @Override public void mousePressed (MouseEvent e) { pressed = true;  repaint(); }
                @Override public void mouseReleased(MouseEvent e) { pressed = false; repaint(); }
            });
        }

        private Color baseFill() {
            return switch (style) {
                case PRIMARY   -> Theme.ACCENT;
                case SECONDARY -> Theme.BG_2;
                case TEXT      -> Theme.alpha(Theme.BG_2, 0);
                case DANGER    -> Theme.alpha(Theme.DANGER, 50);
            };
        }

        private Color borderColor() {
            return switch (style) {
                case PRIMARY   -> Theme.ACCENT_BRIGHT;
                case SECONDARY -> Theme.ACCENT_DIM;
                case TEXT      -> Theme.alpha(Theme.ACCENT_DIM, 0);
                case DANGER    -> Theme.DANGER;
            };
        }

        private Color textColor() {
            return switch (style) {
                case PRIMARY   -> Theme.TEXT_ON_ACCENT;
                case SECONDARY -> Theme.TEXT_PRIMARY;
                case TEXT      -> Theme.TEXT_BODY;
                case DANGER    -> Theme.DANGER;
            };
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            Color fill = baseFill();
            if (pressed)      fill = Theme.blend(fill, Color.BLACK, 0.20f);
            else if (hovered) fill = Theme.blend(fill, Color.WHITE, 0.10f);

            g.setColor(fill);
            g.fillRoundRect(0, 0, w, h, Theme.RADIUS_M, Theme.RADIUS_M);

            // Border
            g.setColor(borderColor());
            g.drawRoundRect(0, 0, w - 1, h - 1, Theme.RADIUS_M, Theme.RADIUS_M);

            // Hover glow on primary
            if (hovered && style == ButtonStyle.PRIMARY) {
                g.setComposite(AlphaComposite.SrcOver.derive(0.35f));
                g.setColor(Theme.ACCENT_BRIGHT);
                g.drawRoundRect(-1, -1, w + 1, h + 1, Theme.RADIUS_M + 2, Theme.RADIUS_M + 2);
            }

            // Text
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(isEnabled() ? textColor() : Theme.TEXT_FAINT);
            Font f = getFont();
            g.setFont(f);
            java.awt.FontMetrics fm = g.getFontMetrics();
            String t = getText() == null ? "" : getText();
            int tw = fm.stringWidth(t);
            int tx = (w - tw) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
            g.drawString(t, tx, ty);
            g.dispose();
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.max(d.width, 80), Math.max(d.height, 32));
        }
    }

    /** Force a fixed maximum height so a `BoxLayout.Y_AXIS` doesn't stretch a button vertically. */
    public static <T extends JComponent> T fixHeight(T c, int height) {
        Dimension pref = c.getPreferredSize();
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        c.setPreferredSize(new Dimension(pref.width, height));
        return c;
    }

    public static JComponent vSpacer(int px) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(1, px));
        p.setMinimumSize(new Dimension(1, px));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, px));
        return p;
    }
}
