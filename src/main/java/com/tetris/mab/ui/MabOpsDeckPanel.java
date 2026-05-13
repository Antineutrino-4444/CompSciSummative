package com.tetris.mab.ui;

import com.tetris.mab.DefconState;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;

import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;

/**
 * Step 23 \u2014 central tactical command deck (DEFCON ladder + radar
 * + mode plate + match clock). Sits in the middle column of the
 * battle shell and is shared between the two stations.
 *
 * <p>This is a pure read-only HUD. It uses an internal repaint
 * {@link Timer} for the radar sweep animation but does not mutate
 * any match state.
 *
 * <p><b>Offline-only.</b>
 */
public final class MabOpsDeckPanel extends JPanel {

    private final Timer radarTimer;
    private double sweepAngle = 0.0;
    private int defconLevel = 5;
    private int humanThreats = 0;
    private int opponentThreats = 0;
    private int humanEtaPieces = -1;
    private int opponentEtaPieces = -1;
    private boolean humanActiveDoctrineAvailable = false;
    private String modeLine = "PVE :: NORMAL AI";
    private String modeSub = "SHARED SEQ // OFFLINE";
    private long matchClockMs = 0L;

    public MabOpsDeckPanel() {
        setOpaque(true);
        setBackground(MabUiTheme.SHELL_PANEL_BG);
        setBorder(new EmptyBorder(8, 6, 8, 6));
        setPreferredSize(new Dimension(224, 600));
        setMinimumSize(new Dimension(224, 380));

        radarTimer = new Timer(80, e -> {
            sweepAngle = (sweepAngle + 6.0) % 360.0;
            repaint();
        });
        radarTimer.setRepeats(true);
        radarTimer.start();
    }

    public void setModeLine(String line, String sub) {
        if (line != null) modeLine = line;
        if (sub != null) modeSub = sub;
        repaint();
    }

    public void setMatchClock(long ms) {
        this.matchClockMs = Math.max(0L, ms);
    }

    public void resetMatchClock() { this.matchClockMs = 0L; }

    public void shutdown() {
        if (radarTimer != null) radarTimer.stop();
    }

    public void refresh(MutuallyAssuredBlocksMatch match,
                        ParticipantId humanSide, ParticipantId opponentSide) {
        if (match == null) return;
        DefconState d = match.getDefconState();
        defconLevel = d == null ? 5 : d.getLevel();
        humanThreats = match.countLiveIncomingThreats(humanSide)
                + match.countImpactReadyThreats(humanSide);
        opponentThreats = match.countLiveIncomingThreats(opponentSide)
                + match.countImpactReadyThreats(opponentSide);
        humanEtaPieces = match.getEarliestIncomingWarningPieces(humanSide);
        opponentEtaPieces = match.getEarliestIncomingWarningPieces(opponentSide);
        humanActiveDoctrineAvailable = match.hasAnyActiveDoctrineAvailable(humanSide);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();
        // Outer frame.
        g2.setColor(MabUiTheme.GRID_LINE_HI);
        g2.drawRect(2, 2, w - 5, h - 5);

        // Header.
        g2.setColor(MabUiTheme.TEXT_FAINT);
        g2.setFont(MabUiTheme.TERM_TINY);
        g2.drawString("OPS DECK :: SHARED", 12, 18);
        g2.drawLine(12, 24, w - 12, 24);

        int y = 36;
        // ── DEFCON ladder ─────────────────────────────────────
        g2.setColor(MabUiTheme.TEXT_FAINT);
        g2.drawString("DEFCON", 12, y); y += 6;
        int rungW = w - 28;
        int rungX = 14;
        for (int lv = 5; lv >= 1; lv--) {
            boolean active = lv <= defconLevel; // lower DEFCON = higher alert; we light current and below
            boolean current = (lv == defconLevel);
            Color base = colorForDefcon(lv);
            g2.setColor(active ? base : MabUiTheme.GRID_LINE);
            g2.fillRect(rungX, y, rungW, 14);
            g2.setColor(current ? MabUiTheme.TEXT_BRIGHT : new Color(0, 0, 0, 80));
            g2.drawRect(rungX, y, rungW - 1, 13);
            g2.setFont(MabUiTheme.TERM_TINY);
            g2.setColor(current ? MabUiTheme.TEXT_BRIGHT : MabUiTheme.SHELL_DEEP_BG);
            String lbl = "DEFCON " + lv + (current ? " <" : "");
            g2.drawString(lbl, rungX + 6, y + 11);
            y += 18;
        }
        y += 6;

        // ── Radar disc ───────────────────────────────────────
        int radarSize = Math.min(w - 24, h - y - 110);
        radarSize = Math.max(80, Math.min(radarSize, 180));
        int rx = (w - radarSize) / 2;
        int ry = y;
        // Disc.
        g2.setColor(MabUiTheme.SHELL_DEEP_BG);
        g2.fillOval(rx, ry, radarSize, radarSize);
        g2.setColor(MabUiTheme.GRID_LINE_HI);
        g2.drawOval(rx, ry, radarSize, radarSize);
        g2.setColor(MabUiTheme.GRID_LINE);
        // Concentric rings.
        g2.drawOval(rx + radarSize / 4, ry + radarSize / 4, radarSize / 2, radarSize / 2);
        g2.drawOval(rx + radarSize / 3, ry + radarSize / 3, radarSize / 3, radarSize / 3);
        // Crosshairs.
        g2.drawLine(rx, ry + radarSize / 2, rx + radarSize, ry + radarSize / 2);
        g2.drawLine(rx + radarSize / 2, ry, rx + radarSize / 2, ry + radarSize);
        // Sweep arc (translucent green wedge).
        Arc2D wedge = new Arc2D.Double(rx, ry, radarSize, radarSize,
                90.0 - sweepAngle, -30.0, Arc2D.PIE);
        g2.setColor(new Color(52, 248, 110, 60));
        g2.fill(wedge);
        g2.setColor(MabUiTheme.C_GREEN);
        // Sweep line.
        double rad = Math.toRadians(90.0 - sweepAngle);
        int cx = rx + radarSize / 2;
        int cy = ry + radarSize / 2;
        int ex = (int) (cx + Math.cos(rad) * radarSize / 2);
        int ey = (int) (cy - Math.sin(rad) * radarSize / 2);
        g2.drawLine(cx, cy, ex, ey);
        // Threat pips.
        drawThreatPips(g2, cx, cy, radarSize / 2 - 8, humanThreats, MabUiTheme.C_AMBER, true);
        drawThreatPips(g2, cx, cy, radarSize / 2 - 8, opponentThreats, MabUiTheme.C_RED, false);
        y += radarSize + 12;

        int trackH = 38;
        g2.setColor(MabUiTheme.SHELL_DEEP_BG);
        g2.fillRect(12, y, w - 24, trackH);
        g2.setColor(MabUiTheme.GRID_LINE);
        g2.drawRect(12, y, w - 25, trackH - 1);
        g2.setFont(MabUiTheme.TERM_TINY);
        g2.setColor(MabUiTheme.TEXT_FAINT);
        g2.drawString("THREAT TRACK", 18, y + 12);
        g2.setColor(humanThreats > 0 ? MabUiTheme.C_AMBER : MabUiTheme.C_GREEN_DIM);
        g2.drawString("P1 " + threatText(humanThreats, humanEtaPieces), 18, y + 26);
        String active = humanActiveDoctrineAvailable ? "ACTIVE: Q READY" : "ACTIVE: Q";
        g2.setColor(humanActiveDoctrineAvailable ? MabUiTheme.C_AMBER : MabUiTheme.TEXT_GHOST);
        FontMetrics tfm = g2.getFontMetrics();
        g2.drawString(active, w - 18 - tfm.stringWidth(active), y + 26);
        y += trackH + 8;

        // ── Mode plate ───────────────────────────────────────
        int plateH = 36;
        g2.setColor(MabUiTheme.SHELL_DEEP_BG);
        g2.fillRect(12, y, w - 24, plateH);
        g2.setColor(MabUiTheme.C_CYAN_DIM);
        g2.drawRect(12, y, w - 25, plateH - 1);
        g2.setFont(MabUiTheme.STENCIL_SMALL);
        g2.setColor(MabUiTheme.C_CYAN);
        g2.drawString(modeLine, 18, y + 16);
        g2.setFont(MabUiTheme.TERM_TINY);
        g2.setColor(MabUiTheme.TEXT_FAINT);
        g2.drawString(modeSub, 18, y + 30);
        y += plateH + 8;

        // ── Match clock ──────────────────────────────────────
        long s = matchClockMs / 1000L;
        String mm = String.format("%02d:%02d", s / 60L, s % 60L);
        g2.setFont(MabUiTheme.TERM_BIG);
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(mm);
        g2.setColor(MabUiTheme.C_CYAN);
        g2.drawString(mm, (w - tw) / 2, y + fm.getAscent());
        g2.setFont(MabUiTheme.TERM_TINY);
        g2.setColor(MabUiTheme.TEXT_FAINT);
        String lbl = "MATCH CLOCK";
        g2.drawString(lbl, (w - g2.getFontMetrics().stringWidth(lbl)) / 2,
                y + fm.getAscent() + 12);

        g2.dispose();
    }

    private static Color colorForDefcon(int level) {
        switch (level) {
            case 1: return MabUiTheme.C_RED;
            case 2: return MabUiTheme.C_AMBER;
            case 3: return new Color(0xFF, 0xC0, 0x60);
            case 4: return MabUiTheme.C_GREEN_DIM;
            case 5:
            default: return new Color(0x10, 0x4A, 0x22);
        }
    }

    private static String threatText(int count, int etaPieces) {
        if (count <= 0) return "CLEAR";
        String eta = etaPieces < 0 ? "IMPACT" : "ETA " + etaPieces + "P";
        return count + "  " + eta;
    }

    private static void drawThreatPips(Graphics2D g2, int cx, int cy,
                                       int radius, int count, Color color,
                                       boolean upperHalf) {
        if (count <= 0) return;
        int n = Math.min(count, 6);
        for (int i = 0; i < n; i++) {
            double a = upperHalf
                    ? Math.PI / 2 + (Math.PI / (n + 1)) * (i + 1)
                    : -Math.PI / 2 + (Math.PI / (n + 1)) * (i + 1);
            int rr = radius - 6 - (i % 2) * 8;
            int px = (int) (cx + Math.cos(a) * rr);
            int py = (int) (cy - Math.sin(a) * rr);
            g2.setColor(color);
            g2.fillOval(px - 3, py - 3, 6, 6);
        }
    }
}
