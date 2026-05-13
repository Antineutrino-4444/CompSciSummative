package com.tetris.mab.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;

/**
 * Step 22 \u2014 dramatic top-of-screen MAB stage strip.
 *
 * <p>Composed of three rows:
 * <ol>
 *   <li>Tiny mode title (\u201cMAB \u2014 PvE\u201d).</li>
 *   <li>HUGE stage headline (e.g. <b>BUILD CHARGE</b>,
 *       <b>NUKE READY</b>, <b>INCOMING THREAT</b>) coloured by severity.</li>
 *   <li>Subtitle, progress text, and call-to-action.</li>
 * </ol>
 *
 * <p>On stage transition the entire strip background flashes for a few
 * refresh ticks so the change is unmissable.
 */
public final class MabStageStripPanel extends JPanel {

    private static final Font HEADLINE_FONT =
            new Font(Font.SANS_SERIF, Font.BOLD, 32);
    private static final Font SUBTITLE_FONT =
            new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    private static final Font PROGRESS_FONT =
            new Font(Font.MONOSPACED, Font.BOLD, 14);
    private static final int  FLASH_TICKS  = 4;

    private final JLabel modeLabel     = new JLabel(" ", SwingConstants.LEFT);
    private final JLabel headlineLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel subtitleLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel progressLabel = new JLabel(" ", SwingConstants.LEFT);
    private final JLabel ctaLabel      = new JLabel(" ", SwingConstants.RIGHT);
    private final ProgressBar progressBar = new ProgressBar();

    private MabStage lastStage = null;
    private int flashTicksRemaining = 0;
    private Color flashColor = MabUiTheme.BANNER_BG;

    public MabStageStripPanel() {
        super(new BorderLayout(0, 4));
        setOpaque(true);
        setBackground(MabUiTheme.BANNER_BG);
        setBorder(MabUiTheme.padding(8, 14, 8, 14));

        modeLabel.setFont(MabUiTheme.TITLE_FONT);
        modeLabel.setForeground(MabUiTheme.TEXT_MUTED);
        modeLabel.setText("MAB \u2014 dramatic stage");

        headlineLabel.setFont(HEADLINE_FONT);
        headlineLabel.setForeground(MabUiTheme.TITLE);

        subtitleLabel.setFont(SUBTITLE_FONT);
        subtitleLabel.setForeground(MabUiTheme.TEXT);

        progressLabel.setFont(PROGRESS_FONT);
        progressLabel.setForeground(MabUiTheme.TEXT);

        ctaLabel.setFont(MabUiTheme.BODY_BOLD);
        ctaLabel.setForeground(MabUiTheme.TEXT_MUTED);

        // ── NORTH: tiny mode title.
        add(modeLabel, BorderLayout.NORTH);

        // ── CENTER: huge headline + subtitle stacked.
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = 0;
        gc.weightx = 1.0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        center.add(headlineLabel, gc);
        gc.gridy = 1;
        center.add(subtitleLabel, gc);
        add(center, BorderLayout.CENTER);

        // ── SOUTH: progress bar + (progressLabel / ctaLabel).
        JPanel south = new JPanel(new BorderLayout(8, 2));
        south.setOpaque(false);
        progressBar.setPreferredSize(new Dimension(200, 8));
        south.add(progressBar, BorderLayout.NORTH);
        JPanel labels = new JPanel(new BorderLayout());
        labels.setOpaque(false);
        labels.add(progressLabel, BorderLayout.WEST);
        labels.add(ctaLabel,      BorderLayout.EAST);
        south.add(labels, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        setPreferredSize(new Dimension(800, 110));
    }

    /**
     * Refreshes the strip from the current match state. Called from the
     * existing 10 Hz refresh timer in {@code GameController}.
     */
    public void refresh(MutuallyAssuredBlocksMatch match,
                        ParticipantId humanId,
                        boolean pveMode) {
        MabStagePresenter.Snapshot snap = MabStagePresenter.present(match, humanId);
        modeLabel.setText(pveMode ? "MAB \u2014 PvE" : "MAB \u2014 PvP");
        headlineLabel.setText(snap.stage.headline());
        headlineLabel.setForeground(snap.stage.color());
        subtitleLabel.setText(snap.subtitle.isEmpty() ? " " : snap.subtitle);
        progressLabel.setText(snap.progressText.isEmpty() ? " " : snap.progressText);
        ctaLabel.setText(snap.ctaText.isEmpty() ? " " : snap.ctaText);
        progressBar.setColor(snap.stage.color());
        progressBar.setFraction(snap.progressFraction);

        if (lastStage != null && lastStage != snap.stage) {
            // Stage transition \u2014 trigger a flash for a few refreshes.
            flashTicksRemaining = FLASH_TICKS;
            flashColor = darken(snap.stage.color(), 0.30f);
        }
        lastStage = snap.stage;

        if (flashTicksRemaining > 0) {
            setBackground(flashColor);
            flashTicksRemaining--;
        } else {
            setBackground(MabUiTheme.BANNER_BG);
        }
        repaint();
    }

    private static Color darken(Color c, float f) {
        int r = Math.max(0, Math.min(255, Math.round(c.getRed()   * f)));
        int g = Math.max(0, Math.min(255, Math.round(c.getGreen() * f)));
        int b = Math.max(0, Math.min(255, Math.round(c.getBlue()  * f)));
        return new Color(r, g, b);
    }

    /** Inner thin progress bar painted with the stage colour. */
    private static final class ProgressBar extends JPanel {
        private double fraction = -1;
        private Color  color    = MabUiTheme.INFO;

        ProgressBar() { setOpaque(false); }

        void setFraction(double f) { this.fraction = f; }
        void setColor(Color c)     { this.color = c == null ? MabUiTheme.INFO : c; }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = getHeight();
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(MabUiTheme.CARD_BG);
                g2.fillRoundRect(0, 0, w, h, h, h);
                if (fraction >= 0) {
                    int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * w);
                    g2.setColor(color);
                    g2.fillRoundRect(0, 0, filled, h, h, h);
                }
                g2.setColor(MabUiTheme.DIVIDER);
                g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);
            } finally {
                g2.dispose();
            }
        }
    }
}
