package com.tetris.mab.ui;

import com.tetris.mab.DefconState;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.NukeBuildState;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ParticipantState;
import com.tetris.mab.nuke.NukeDesign;

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

/**
 * Step 23 central tactical command deck. Sits in the middle column of
 * the battle shell and is shared between the two stations.
 *
 * <p>This is a pure read-only HUD. It uses an internal repaint
 * {@link Timer} only for a subtle pulse; it does not mutate match state.
 *
 * <p><b>Offline-only.</b>
 */
public final class MabOpsDeckPanel extends JPanel {

    private final Timer pulseTimer;
    private double pulsePhase = 0.0;
    private int defconLevel = 5;
    private double defconProgress = 0.0;
    private double gravityMultiplier = 1.0;
    private int humanThreats = 0;
    private int opponentThreats = 0;
    private int humanEtaPieces = -1;
    private int opponentEtaPieces = -1;
    private boolean humanActiveDoctrineAvailable = false;
    private String modeLine = "PVE :: NORMAL AI";
    private String modeSub = "SHARED SEQ // OFFLINE";
    private long matchClockMs = 0L;
    private WarheadSummary humanWarhead = WarheadSummary.empty("YOU");
    private WarheadSummary opponentWarhead = WarheadSummary.empty("RIVAL");

    public MabOpsDeckPanel() {
        setOpaque(true);
        setBackground(MabUiTheme.SHELL_PANEL_BG);
        setBorder(new EmptyBorder(8, 6, 8, 6));
        setPreferredSize(new Dimension(224, 600));
        setMinimumSize(new Dimension(224, 380));

        pulseTimer = new Timer(120, e -> {
            pulsePhase = (pulsePhase + 0.08) % 1.0;
            repaint();
        });
        pulseTimer.setRepeats(true);
        pulseTimer.start();
    }

    public void setModeLine(String line, String sub) {
        if (line != null) modeLine = line;
        if (sub != null) modeSub = sub;
        repaint();
    }

    public void setMatchClock(long ms) {
        this.matchClockMs = Math.max(0L, ms);
        repaint();
    }

    public void resetMatchClock() { this.matchClockMs = 0L; }

    public void shutdown() {
        if (pulseTimer != null) pulseTimer.stop();
    }

    public void refresh(MutuallyAssuredBlocksMatch match,
                        ParticipantId humanSide, ParticipantId opponentSide) {
        if (match == null) return;
        DefconState d = match.getDefconState();
        defconLevel = d == null ? 5 : d.getLevel();
        defconProgress = d == null ? 0.0 : d.getProgressToNextThreshold();
        gravityMultiplier = d == null ? 1.0 : d.getGravityMultiplier();
        humanThreats = match.countLiveIncomingThreats(humanSide)
                + match.countImpactReadyThreats(humanSide);
        opponentThreats = match.countLiveIncomingThreats(opponentSide)
                + match.countImpactReadyThreats(opponentSide);
        humanEtaPieces = match.getEarliestIncomingWarningPieces(humanSide);
        opponentEtaPieces = match.getEarliestIncomingWarningPieces(opponentSide);
        humanActiveDoctrineAvailable = match.hasAnyActiveDoctrineAvailable(humanSide);
        humanWarhead = WarheadSummary.from(match, humanSide, "YOU", defconLevel);
        opponentWarhead = WarheadSummary.from(match, opponentSide, "RIVAL", defconLevel);
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

        g2.setFont(MabUiTheme.TERM_TINY);
        g2.setColor(MabUiTheme.TEXT);
        String tempo = String.format("NEXT %.0f%%   GRAVITY %.2fx",
                defconProgress * 100.0, gravityMultiplier);
        g2.drawString(tempo, 12, y + 10);
        y += 18;

        // Warhead tempo / payload panel.
        int warheadH = Math.min(198, Math.max(96, h - y - 140));
        paintWarheadPanel(g2, 12, y, w - 24, warheadH);
        y += warheadH + 8;

        int trackH = 56;
        g2.setColor(MabUiTheme.SHELL_DEEP_BG);
        g2.fillRect(12, y, w - 24, trackH);
        g2.setColor(MabUiTheme.GRID_LINE);
        g2.drawRect(12, y, w - 25, trackH - 1);
        g2.setFont(MabUiTheme.TERM_TINY);
        g2.setColor(MabUiTheme.TEXT_FAINT);
        g2.drawString("THREAT TRACK", 18, y + 12);
        drawThreatLine(g2, "YOU", threatText(humanThreats, humanEtaPieces),
                humanThreats > 0 ? MabUiTheme.C_AMBER : MabUiTheme.C_GREEN,
                18, y + 28, w - 36);
        drawThreatLine(g2, "RIVAL", threatText(opponentThreats, opponentEtaPieces),
                opponentThreats > 0 ? MabUiTheme.C_RED : MabUiTheme.TEXT_FAINT,
                18, y + 43, w - 36);
        String active = humanActiveDoctrineAvailable ? "ACTIVE: Q READY" : "ACTIVE: Q";
        g2.setColor(humanActiveDoctrineAvailable ? MabUiTheme.C_AMBER : MabUiTheme.TEXT_GHOST);
        FontMetrics tfm = g2.getFontMetrics();
        g2.drawString(active, w - 18 - tfm.stringWidth(active), y + 12);
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

    private void paintWarheadPanel(Graphics2D g2, int x, int y, int w, int h) {
        g2.setColor(MabUiTheme.SHELL_DEEP_BG);
        g2.fillRect(x, y, w, h);
        g2.setColor(MabUiTheme.GRID_LINE);
        g2.drawRect(x, y, w - 1, h - 1);

        int pulseX = x + 1 + (int) Math.round((w - 3) * pulsePhase);
        g2.setColor(new Color(MabUiTheme.C_CYAN.getRed(), MabUiTheme.C_CYAN.getGreen(),
                MabUiTheme.C_CYAN.getBlue(), 70));
        g2.drawLine(pulseX, y + 1, pulseX, y + h - 2);

        g2.setFont(MabUiTheme.TERM_TINY);
        g2.setColor(MabUiTheme.TEXT_FAINT);
        g2.drawString("WARHEAD TEMPO", x + 6, y + 12);

        int yy = y + 29;
        yy = drawWarheadBlock(g2, humanWarhead, x + 6, yy, w - 12, true);
        yy += 6;
        g2.setColor(MabUiTheme.GRID_LINE);
        g2.drawLine(x + 6, yy, x + w - 7, yy);
        yy += 13;
        drawWarheadBlock(g2, opponentWarhead, x + 6, yy, w - 12, false);
    }

    private static int drawWarheadBlock(Graphics2D g2, WarheadSummary s,
                                        int x, int y, int w, boolean detailed) {
        if (s == null) s = WarheadSummary.empty(detailed ? "YOU" : "RIVAL");
        g2.setFont(MabUiTheme.TERM_TINY);
        g2.setColor(detailed ? MabUiTheme.C_CYAN : MabUiTheme.TEXT_FAINT);
        g2.drawString(s.ownerLabel, x, y);
        FontMetrics fm = g2.getFontMetrics();
        String design = ellipsize(g2, s.designName, Math.max(40, w - fm.stringWidth(s.ownerLabel) - 10));
        g2.setColor(MabUiTheme.TEXT);
        g2.drawString(design, x + w - fm.stringWidth(design), y);
        y += 14;

        drawKvLine(g2, "PAYLOAD", s.payloadLabel, x, y, w, MabUiTheme.TEXT);
        y += 13;
        drawKvLine(g2, "CHARGE", s.chargeCurrent + "/" + s.chargeRequired
                + "  T" + s.tetrisGoal + " S" + s.spinGoal,
                x, y, w, s.armed ? MabUiTheme.C_GREEN : MabUiTheme.TEXT);
        y += 13;
        drawKvLine(g2, "TIMING", "COUNT " + s.countdownPieces + "P  IMPACT "
                + s.impactDelayPieces + "P", x, y, w, MabUiTheme.TEXT);
        y += 13;

        if (detailed) {
            drawKvLine(g2, "BLAST/RAD", s.blastRating + " / " + s.radiationRating,
                    x, y, w, MabUiTheme.C_AMBER);
            y += 13;
            drawKvLine(g2, "EMP/DISARM/SILO", s.empRating + " / "
                    + s.disarmRating + " / " + s.siloDamageRating,
                    x, y, w, MabUiTheme.C_AMBER);
            y += 13;
            drawKvLine(g2, "INTERCEPT", "DIFF " + s.interceptDifficulty,
                    x, y, w, MabUiTheme.TEXT);
            y += 13;
        }
        return y;
    }

    private static void drawKvLine(Graphics2D g2, String label, String value,
                                   int x, int y, int w, Color valueColor) {
        g2.setFont(MabUiTheme.TERM_TINY);
        FontMetrics fm = g2.getFontMetrics();
        String l = label == null ? "" : label;
        String v = value == null ? "" : value;
        g2.setColor(MabUiTheme.TEXT_FAINT);
        g2.drawString(l, x, y);
        String out = ellipsize(g2, v, Math.max(20, w - fm.stringWidth(l) - 8));
        g2.setColor(valueColor == null ? MabUiTheme.TEXT : valueColor);
        g2.drawString(out, x + w - fm.stringWidth(out), y);
    }

    private static String ellipsize(Graphics2D g2, String text, int maxWidth) {
        if (text == null) return "";
        FontMetrics fm = g2.getFontMetrics();
        if (maxWidth <= 0 || fm.stringWidth(text) <= maxWidth) return text;
        String out = text;
        String dots = "...";
        int dotsW = fm.stringWidth(dots);
        while (out.length() > 1 && fm.stringWidth(out) + dotsW > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out.length() <= 1 ? dots : out + dots;
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

    private static void drawThreatLine(Graphics2D g2, String label, String value,
                                       Color valueColor, int x, int y, int width) {
        g2.setFont(MabUiTheme.TERM_TINY);
        FontMetrics fm = g2.getFontMetrics();
        String left = label == null ? "" : label;
        String right = value == null ? "" : value;
        g2.setColor(MabUiTheme.TEXT_FAINT);
        g2.drawString(left, x, y);
        int rw = fm.stringWidth(right);
        int rx = Math.max(x + fm.stringWidth(left) + 12, x + width - rw);
        g2.setColor(valueColor == null ? MabUiTheme.TEXT : valueColor);
        g2.drawString(right, rx, y);
    }

    private static final class WarheadSummary {
        final String ownerLabel;
        final String designName;
        final String payloadLabel;
        final int chargeCurrent;
        final int chargeRequired;
        final boolean armed;
        final int tetrisGoal;
        final int spinGoal;
        final int countdownPieces;
        final int impactDelayPieces;
        final int blastRating;
        final int radiationRating;
        final int empRating;
        final int disarmRating;
        final int siloDamageRating;
        final int interceptDifficulty;

        private WarheadSummary(String ownerLabel,
                               String designName,
                               String payloadLabel,
                               int chargeCurrent,
                               int chargeRequired,
                               boolean armed,
                               int tetrisGoal,
                               int spinGoal,
                               int countdownPieces,
                               int impactDelayPieces,
                               int blastRating,
                               int radiationRating,
                               int empRating,
                               int disarmRating,
                               int siloDamageRating,
                               int interceptDifficulty) {
            this.ownerLabel = ownerLabel;
            this.designName = designName;
            this.payloadLabel = payloadLabel;
            this.chargeCurrent = chargeCurrent;
            this.chargeRequired = chargeRequired;
            this.armed = armed;
            this.tetrisGoal = tetrisGoal;
            this.spinGoal = spinGoal;
            this.countdownPieces = countdownPieces;
            this.impactDelayPieces = impactDelayPieces;
            this.blastRating = blastRating;
            this.radiationRating = radiationRating;
            this.empRating = empRating;
            this.disarmRating = disarmRating;
            this.siloDamageRating = siloDamageRating;
            this.interceptDifficulty = interceptDifficulty;
        }

        static WarheadSummary empty(String ownerLabel) {
            return new WarheadSummary(ownerLabel, "-", "-", 0, 1, false,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        static WarheadSummary from(MutuallyAssuredBlocksMatch match,
                                   ParticipantId pid,
                                   String ownerLabel,
                                   int defconLevel) {
            if (match == null || pid == null) return empty(ownerLabel);
            ParticipantState p = match.getParticipant(pid);
            if (p == null || p.getNukeBuildState() == null) return empty(ownerLabel);
            NukeBuildState nb = p.getNukeBuildState();
            NukeDesign d = nb.getCurrentDesign();
            if (d == null) return empty(ownerLabel);
            return new WarheadSummary(
                    ownerLabel,
                    d.getDisplayName(),
                    d.getDoctrineType() == null ? "-" : d.getDoctrineType().displayLabel(),
                    nb.getCurrentBuildCharge(),
                    Math.max(1, nb.getEffectiveBuildChargeRequired()),
                    nb.isArmed(),
                    d.effectiveLaunchTetrisGoal(defconLevel),
                    d.effectiveLaunchSpinGoal(defconLevel),
                    d.effectiveLaunchTimePieces(defconLevel),
                    d.effectiveImpactDelayPieces(defconLevel),
                    d.getBlastRating(),
                    d.getRadiationRating(),
                    d.getEmpRating(),
                    d.getDisarmRating(),
                    d.getSiloDamageRating(),
                    d.interceptDifficultyRating());
        }
    }
}
