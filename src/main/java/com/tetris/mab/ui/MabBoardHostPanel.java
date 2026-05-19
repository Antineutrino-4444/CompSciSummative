package com.tetris.mab.ui;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Step 23 Refinement \u2014 thematic board station wrapper that mounts
 * an arbitrary playfield component (the existing {@code GamePanel} or
 * {@code MabOpponentBoardPanel}) inside a cold-war framed enclosure
 * matching the HTML mockup's {@code .board-wrap}.
 *
 * <p>The board host enforces a 1:2 (10:20) aspect ratio for the inner
 * playfield and centers it inside the available area. It sets explicit
 * bounds on the child every layout pass so the child's
 * {@code getWidth()/getHeight()} are non-zero and the renderer can
 * paint blocks correctly.
 *
 * <p>Used in MAB PvE only. Normal Tetris is unaffected.
 *
 * <p><b>Offline-only.</b>
 */
public final class MabBoardHostPanel extends JPanel {

    private final JComponent board;
    private final JLabel fieldTag;
    private final JLabel metaLabel;
    private final BoardCanvas canvas;
    private final boolean mirrorTag;

    public MabBoardHostPanel(JComponent board, String fieldTagText, boolean mirrorTag) {
        super(new BorderLayout());
        if (board == null) throw new IllegalArgumentException("board");
        this.board = board;
        this.mirrorTag = mirrorTag;
        setOpaque(true);
        setBackground(MabUiTheme.SHELL_PANEL_BG);
        setBorder(new LineBorder(MabUiTheme.GRID_LINE_HI, 1));

        // Top header strip with field tag (left or right) + meta on the
        // opposite side. Must NOT consume more than ~22 px so the board
        // dominates vertical space.
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setBorder(new EmptyBorder(4, 12, 2, 12));
        fieldTag = new JLabel(fieldTagText == null ? "FIELD" : fieldTagText);
        fieldTag.setFont(MabUiTheme.TERM_TINY);
        fieldTag.setForeground(MabUiTheme.TEXT_FAINT);
        metaLabel = new JLabel(" ");
        metaLabel.setFont(MabUiTheme.TERM_TINY);
        metaLabel.setForeground(MabUiTheme.TEXT_FAINT);
        if (mirrorTag) {
            fieldTag.setHorizontalAlignment(SwingConstants.RIGHT);
            metaLabel.setHorizontalAlignment(SwingConstants.LEFT);
            head.add(metaLabel, BorderLayout.WEST);
            head.add(fieldTag, BorderLayout.EAST);
        } else {
            head.add(fieldTag, BorderLayout.WEST);
            metaLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            head.add(metaLabel, BorderLayout.EAST);
        }
        add(head, BorderLayout.NORTH);

        canvas = new BoardCanvas();
        canvas.add(board);
        add(canvas, BorderLayout.CENTER);

        // Reasonable sizing so a parent GridBagLayout hands us real
        // space. The canvas honours these intelligently \u2014 the inner
        // board is always laid out to the largest 10:20 box that fits.
        setMinimumSize(new Dimension(180, 360));
        setPreferredSize(new Dimension(300, 560));
    }

    public void setMeta(String text) {
        metaLabel.setText(text == null ? " " : text);
    }

    public JComponent getBoard() { return board; }

    /**
     * Inner canvas \u2014 lays out exactly one child as a centered 10:20
     * box. Uses a custom layout because GridBagLayout falls back to the
     * child's minimum size (often 0) when its preferred size doesn't
     * fit, which is what made the player board invisible in the first
     * Step 23 attempt.
     */
    private static final class BoardCanvas extends JPanel {
        BoardCanvas() {
            super(null);
            setOpaque(false);
            setBorder(new EmptyBorder(2, 6, 8, 6));
        }
        @Override
        public void doLayout() {
            int n = getComponentCount();
            if (n == 0) return;
            java.awt.Insets ins = getInsets();
            int aw = getWidth() - ins.left - ins.right;
            int ah = getHeight() - ins.top - ins.bottom;
            if (aw <= 0 || ah <= 0) return;
            // Largest 10:20 (1:2) rectangle that fits.
            int w, h;
            if (aw * 2 <= ah) { w = aw; h = aw * 2; }
            else { h = ah; w = ah / 2; }
            // Round w to a multiple of 10 so cells are integer-sized.
            int cell = Math.max(8, w / 10);
            w = cell * 10;
            h = cell * 20;
            int x = ins.left + (aw - w) / 2;
            int y = ins.top + (ah - h) / 2;
            getComponent(0).setBounds(x, y, w, h);
        }
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(280, 560);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // Subtle CRT scanline overlay across the host frame.
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(0, 229, 224, 14));
        for (int y = 0; y < getHeight(); y += 3) {
            g2.drawLine(0, y, getWidth(), y);
        }
        // Corner brackets like the mockup overlay panels.
        g2.setColor(MabUiTheme.C_CYAN_DIM);
        int b = 10;
        g2.drawLine(0, 0, b, 0);
        g2.drawLine(0, 0, 0, b);
        g2.drawLine(getWidth() - 1, 0, getWidth() - 1 - b, 0);
        g2.drawLine(getWidth() - 1, 0, getWidth() - 1, b);
        g2.drawLine(0, getHeight() - 1, b, getHeight() - 1);
        g2.drawLine(0, getHeight() - 1, 0, getHeight() - 1 - b);
        g2.drawLine(getWidth() - 1, getHeight() - 1, getWidth() - 1 - b, getHeight() - 1);
        g2.drawLine(getWidth() - 1, getHeight() - 1, getWidth() - 1, getHeight() - 1 - b);
        g2.dispose();
    }

    /** Renders a label-only field tag (no playfield). Useful for opponent
     *  status placeholder panels that want the same chrome. */
    public static MabBoardHostPanel placeholder(String tag) {
        JPanel ph = new JPanel();
        ph.setOpaque(false);
        return new MabBoardHostPanel(ph, tag, false);
    }

    @SuppressWarnings("unused")
    private void noop() {}

    public boolean isMirrored() { return mirrorTag; }
}
