package com.tetris.mab.ui;

import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ParticipantState;
import com.tetris.mab.clear.MabSimplifiedStrategicState;

import javax.swing.JPanel;
import javax.swing.border.LineBorder;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Step 23 \u2014 per-side bottom tactical strip showing the four core
 * tactical readouts: CHARGE / LAUNCH / DEFENSE / LAST CLEAR.
 *
 * <p>Custom-painted to match the cold-war operations console theme.
 * No scroll panes, no clipped text, no debug clutter.
 *
 * <p><b>Offline-only.</b>
 */
public final class MabTacticalStripPanel extends JPanel {

    private final boolean mirrored;
    private String stationTag;
    private int chargeCurrent = 0;
    private int chargeRequired = 100;
    private int tetrisProgress = 0;
    private int tetrisGoal = 4;
    private int spinProgress = 0;
    private int spinGoal = 2;
    private DefenseStatus defense = DefenseStatus.SAFE;
    private String lastClear = "\u2014";
    private String doctrineSummary = "DOCTRINES 0";

    public enum DefenseStatus { SAFE, INCOMING, INTERCEPT, IMPACT }

    public MabTacticalStripPanel(String stationTag, boolean mirrored) {
        this.mirrored = mirrored;
        this.stationTag = stationTag == null ? "" : stationTag;
        setOpaque(true);
        setBackground(MabUiTheme.SHELL_PANEL_BG);
        setBorder(new LineBorder(MabUiTheme.GRID_LINE, 1));
        setPreferredSize(new Dimension(540, 96));
        setMinimumSize(new Dimension(380, 80));
    }

    public void setStationTag(String t) { this.stationTag = t == null ? "" : t; repaint(); }

    public void refresh(MutuallyAssuredBlocksMatch match, ParticipantId pid) {
        if (match == null || pid == null) return;
        ParticipantState p = match.getParticipant(pid);
        if (p == null) return;
        MabSimplifiedStrategicState s = p.getSimplifiedState();
        chargeCurrent = s.chargeCurrent();
        chargeRequired = Math.max(1, s.chargeRequired());
        tetrisProgress = s.launchTetrisProgress();
        tetrisGoal = Math.max(1, s.launchTetrisGoal());
        spinProgress = s.launchSpinProgress();
        spinGoal = Math.max(1, s.launchSpinGoal());

        int liveIncoming = match.countLiveIncomingThreats(pid);
        int impactReady = match.countImpactReadyThreats(pid);
        if (impactReady > 0) defense = DefenseStatus.IMPACT;
        else if (liveIncoming > 0) defense = DefenseStatus.INCOMING;
        else defense = DefenseStatus.SAFE;

        String last = s.lastClearText();
        lastClear = (last == null || last.isEmpty()) ? "\u2014" : last.toUpperCase();
        int doctrines = p.getUpgradeInventory().getTotalSelectedCards();
        if (match.hasAnyActiveDoctrineAvailable(pid)) {
            doctrineSummary = "DOCTRINES " + doctrines + "  ACTIVE Q READY";
        } else if (match.hasAnyActiveDoctrineOwned(pid)) {
            doctrineSummary = "DOCTRINES " + doctrines + "  ACTIVE Q";
        } else {
            doctrineSummary = "DOCTRINES " + doctrines;
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();

        // Stencil tag (top-left or top-right, mirrored)
        g2.setFont(MabUiTheme.TERM_TINY);
        FontMetrics fmTag = g2.getFontMetrics();
        g2.setColor(MabUiTheme.TEXT_FAINT);
        String docText = ellipsize(g2, doctrineSummary, Math.max(80, w / 2 - 20));
        if (mirrored) {
            int tagW = fmTag.stringWidth(stationTag);
            g2.drawString(stationTag, w - tagW - 14, fmTag.getAscent() + 4);
            g2.drawString(docText, 14, fmTag.getAscent() + 4);
        } else {
            g2.drawString(stationTag, 14, fmTag.getAscent() + 4);
            int dsW = fmTag.stringWidth(docText);
            g2.drawString(docText, w - dsW - 14, fmTag.getAscent() + 4);
        }

        // Layout: 4 columns (CHARGE 35%, LAUNCH 28%, DEFENSE 17%, LAST CLEAR 20%)
        int padTop = 18;
        int padBottom = 8;
        int innerH = h - padTop - padBottom;
        int padL = 12, padR = 12;
        int colsW = w - padL - padR;
        int gap = 12;
        int wCharge = (int) (colsW * 0.32) - gap;
        int wLaunch = (int) (colsW * 0.30) - gap;
        int wDef = (int) (colsW * 0.16) - gap;
        int wLast = colsW - wCharge - wLaunch - wDef - 3 * gap;

        int x = padL;
        paintCharge(g2, x, padTop, wCharge, innerH);
        x += wCharge + gap;
        paintLaunch(g2, x, padTop, wLaunch, innerH);
        x += wLaunch + gap;
        paintDefense(g2, x, padTop, wDef, innerH);
        x += wDef + gap;
        paintLast(g2, x, padTop, wLast, innerH);

        g2.dispose();
    }

    private void paintLabel(Graphics2D g2, String label, int x, int y) {
        g2.setFont(MabUiTheme.TERM_TINY);
        g2.setColor(MabUiTheme.C_CYAN_DIM);
        g2.fillRect(x, y - 6, 4, 4);
        g2.setColor(MabUiTheme.TEXT_FAINT);
        g2.drawString(label, x + 8, y);
    }

    private void paintCharge(Graphics2D g2, int x, int y, int w, int h) {
        FontMetrics fmL = g2.getFontMetrics(MabUiTheme.TERM_TINY);
        int labelY = y + fmL.getAscent();
        paintLabel(g2, "CHARGE", x, labelY);

        String value = String.format("%03d / %03d", chargeCurrent, chargeRequired);
        g2.setFont(fittedFont(g2, MabUiTheme.TERM_BIG, value, w, 12f));
        FontMetrics fmV = g2.getFontMetrics();
        boolean full = chargeCurrent >= chargeRequired;
        g2.setColor(full ? MabUiTheme.C_GREEN : MabUiTheme.C_CYAN);
        int valY = labelY + fmV.getAscent() + 4;
        g2.drawString(value, x, valY);

        // Gauge
        int gaugeY = y + h - 8;
        int gaugeH = 6;
        g2.setColor(MabUiTheme.SHELL_DEEP_BG);
        g2.fillRect(x, gaugeY, w, gaugeH);
        g2.setColor(MabUiTheme.GRID_LINE);
        g2.drawRect(x, gaugeY, w, gaugeH);
        double frac = Math.min(1.0, (double) chargeCurrent / chargeRequired);
        int fillW = (int) ((w - 1) * frac);
        g2.setColor(full ? MabUiTheme.C_GREEN : MabUiTheme.C_CYAN);
        g2.fillRect(x + 1, gaugeY + 1, Math.max(0, fillW), gaugeH - 1);
    }

    private void paintLaunch(Graphics2D g2, int x, int y, int w, int h) {
        FontMetrics fmL = g2.getFontMetrics(MabUiTheme.TERM_TINY);
        int labelY = y + fmL.getAscent();
        paintLabel(g2, "LAUNCH", x, labelY);

        // Pips: T x4, separator, S x2
        int pipSize = 10;
        int pipGap = 3;
        int sepGap = 8;
        int pipsY = labelY + 8;

        int px = x;
        for (int i = 0; i < tetrisGoal; i++) {
            boolean lit = i < tetrisProgress;
            drawPip(g2, px, pipsY, pipSize, lit ? MabUiTheme.C_GREEN : null);
            px += pipSize + pipGap;
        }
        g2.setFont(MabUiTheme.TERM_TINY);
        g2.setColor(MabUiTheme.TEXT_FAINT);
        FontMetrics fmP = g2.getFontMetrics();
        g2.drawString("T", px + sepGap, pipsY + pipSize - 1);
        px += sepGap + fmP.stringWidth("T") + sepGap;
        for (int i = 0; i < spinGoal; i++) {
            boolean lit = i < spinProgress;
            drawPip(g2, px, pipsY, pipSize, lit ? MabUiTheme.C_AMBER : null);
            px += pipSize + pipGap;
        }
        g2.setColor(MabUiTheme.TEXT_FAINT);
        g2.drawString("S", px + sepGap, pipsY + pipSize - 1);

        // Sub text
        g2.setFont(MabUiTheme.TERM_SMALL);
        FontMetrics fmS = g2.getFontMetrics();
        String sub = "T " + tetrisProgress + "/" + tetrisGoal
                + " \u00B7 S " + spinProgress + "/" + spinGoal;
        g2.setColor(MabUiTheme.TEXT_FAINT);
        g2.drawString(sub, x, y + h - 6);
    }

    private void drawPip(Graphics2D g2, int x, int y, int size, Color fill) {
        g2.setColor(MabUiTheme.GRID_LINE_HI);
        g2.drawRect(x, y, size, size);
        if (fill != null) {
            g2.setColor(fill);
            g2.fillRect(x + 1, y + 1, size - 1, size - 1);
        }
    }

    private void paintDefense(Graphics2D g2, int x, int y, int w, int h) {
        FontMetrics fmL = g2.getFontMetrics(MabUiTheme.TERM_TINY);
        int labelY = y + fmL.getAscent();
        paintLabel(g2, "DEFENSE", x, labelY);

        String value;
        Color color;
        switch (defense) {
            case INCOMING:
                value = "INCOMING"; color = MabUiTheme.C_RED; break;
            case INTERCEPT:
                value = "INTERCEPT"; color = MabUiTheme.C_AMBER; break;
            case IMPACT:
                value = "IMPACT"; color = MabUiTheme.TEXT_FAINT; break;
            case SAFE:
            default:
                value = "SAFE"; color = MabUiTheme.C_CYAN; break;
        }
        g2.setFont(fittedFont(g2, MabUiTheme.TERM_BIG, value, w, 11f));
        FontMetrics fmV = g2.getFontMetrics();
        g2.setColor(color);
        g2.drawString(value, x, labelY + fmV.getAscent() + 4);
    }

    private void paintLast(Graphics2D g2, int x, int y, int w, int h) {
        FontMetrics fmL = g2.getFontMetrics(MabUiTheme.TERM_TINY);
        int labelY = y + fmL.getAscent();
        paintLabel(g2, "LAST CLEAR", x, labelY);

        g2.setFont(MabUiTheme.TERM_MED);
        FontMetrics fmV = g2.getFontMetrics();
        g2.setColor(MabUiTheme.TEXT_BRIGHT);
        // Truncate if too wide
        String txt = lastClear;
        txt = ellipsize(g2, txt, w);
        g2.drawString(txt, x, labelY + fmV.getAscent() + 4);
    }

    private static java.awt.Font fittedFont(Graphics2D g2, java.awt.Font base,
                                            String text, int maxWidth,
                                            float minSize) {
        if (text == null) return base;
        float size = base.getSize2D();
        java.awt.Font f = base;
        while (size > minSize && g2.getFontMetrics(f).stringWidth(text) > maxWidth) {
            size -= 1f;
            f = base.deriveFont(size);
        }
        return f;
    }

    private static String ellipsize(Graphics2D g2, String text, int maxWidth) {
        if (text == null) return "";
        FontMetrics fm = g2.getFontMetrics();
        if (maxWidth <= 0 || fm.stringWidth(text) <= maxWidth) return text;
        String dots = "...";
        int dotsW = fm.stringWidth(dots);
        String out = text;
        while (out.length() > 1 && fm.stringWidth(out) + dotsW > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out.length() <= 1 ? dots : out + dots;
    }
}
