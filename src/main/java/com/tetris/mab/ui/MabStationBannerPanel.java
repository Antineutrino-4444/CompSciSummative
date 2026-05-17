package com.tetris.mab.ui;

import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ParticipantState;
import com.tetris.mab.clear.MabSimplifiedStrategicState;

import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Step 23 \u2014 cold-war station banner painted on top of each player's
 * column. Shows: station title, lamp, big stencil headline (BUILD CHARGE /
 * NUKE READY / INCOMING NUKE / LAUNCH FIRED / IMPACT RESOLVED) and a
 * two-cell sub-line with the most relevant numbers.
 *
 * <p>Self-paints; does not own a refresh timer. The owning shell calls
 * {@link #refresh(MutuallyAssuredBlocksMatch, ParticipantId, boolean)}.
 *
 * <p><b>Offline-only.</b>
 */
public final class MabStationBannerPanel extends JPanel {

    /** Visual stage \u2014 drives color / lamp / flash. */
    public enum BannerStage {
        BUILD, READY, INCOMING, INTERCEPT, LAUNCH, IMPACT, OVER
    }

    private final boolean mirrored;
    private String stationTitle;
    private BannerStage stage = BannerStage.BUILD;
    private String headline = "BUILD CHARGE";
    private String subA = "";
    private String subB = "";
    private String nukeDesignLine = "";
    private String comboHint = "";

    public MabStationBannerPanel(String stationTitle, boolean mirrored) {
        super(new BorderLayout());
        this.mirrored = mirrored;
        this.stationTitle = stationTitle == null ? "" : stationTitle;
        setOpaque(true);
        setBackground(MabUiTheme.SHELL_PANEL_BG);
        setBorder(new LineBorder(MabUiTheme.GRID_LINE, 1));
        setPreferredSize(new Dimension(420, 88));
        setMinimumSize(new Dimension(280, 70));
    }

    public void setStationTitle(String t) {
        this.stationTitle = t == null ? "" : t;
        repaint();
    }

    public void setStage(BannerStage s) {
        if (s == null) s = BannerStage.BUILD;
        if (s != this.stage) {
            this.stage = s;
            setBorder(new LineBorder(borderColor(), 1));
        }
        repaint();
    }

    public void setHeadline(String h) { this.headline = h == null ? "" : h; repaint(); }
    public void setSubA(String s) { this.subA = s == null ? "" : s; repaint(); }
    public void setSubB(String s) { this.subB = s == null ? "" : s; repaint(); }
    public void setNukeDesignLine(String line) {
        this.nukeDesignLine = line == null ? "" : line;
        repaint();
    }

    public void setComboHint(String hint) {
        this.comboHint = hint == null ? "" : hint;
        int h = comboHint.isEmpty() ? 88 : 112;
        setPreferredSize(new Dimension(420, h));
        setMinimumSize(new Dimension(280, comboHint.isEmpty() ? 70 : 90));
        revalidate();
        repaint();
    }

    /** Convenience: derive everything from the live match. */
    public void refresh(MutuallyAssuredBlocksMatch match, ParticipantId pid,
                        boolean isHumanSide) {
        if (match == null || pid == null) return;
        ParticipantState p = match.getParticipant(pid);
        if (p == null) return;
        MabSimplifiedStrategicState s = p.getSimplifiedState();
        int liveIncoming = match.countLiveIncomingThreats(pid);
        int impactReady = match.countImpactReadyThreats(pid);

        // Active outgoing launches (not RESOLVED/CANCELLED).
        int activeLaunches = 0;
        for (com.tetris.mab.ActiveLaunchState l : p.getActiveLaunches()) {
            if (l.getPhase() != com.tetris.mab.launch.LaunchPhase.RESOLVED
                    && l.getPhase() != com.tetris.mab.launch.LaunchPhase.CANCELLED) {
                activeLaunches++;
            }
        }

        if (match.isGameOver()) {
            setStage(BannerStage.OVER);
            setHeadline("MATCH OVER");
            setSubA("RESULT BELOW");
            setSubB("");
            return;
        }
        if (liveIncoming > 0) {
            setStage(BannerStage.INCOMING);
            setHeadline("INCOMING NUKE");
            setSubA("SPIN ANY PIECE");
            setSubB("TO INTERCEPT");
            return;
        }
        if (impactReady > 0) {
            setStage(BannerStage.IMPACT);
            setHeadline("IMPACT RESOLVING");
            setSubA("STABILIZE");
            setSubB("REBUILD CHARGE");
            return;
        }
        if (activeLaunches > 0) {
            setStage(BannerStage.LAUNCH);
            setHeadline("LAUNCH FIRED");
            setSubA(activeLaunches + " IN FLIGHT");
            setSubB("AWAITING IMPACT");
            return;
        }
        if (s.nukeReady()) {
            setStage(BannerStage.READY);
            setHeadline("NUKE READY");
            setSubA("TETRISES " + s.launchTetrisProgress() + " / " + s.launchTetrisGoal());
            setSubB("SPINS " + s.launchSpinProgress() + " / " + s.launchSpinGoal());
            return;
        }
        setStage(BannerStage.BUILD);
        setHeadline("BUILD CHARGE");
        setSubA("CHARGE " + pad3(s.chargeCurrent()) + " / " + pad3(s.chargeRequired()));
        String last = s.lastClearText();
        String subBText = nukeDesignLine.isEmpty()
                ? "LAST: " + (last == null || last.isEmpty() ? "\u2014" : last.toUpperCase())
                : "DESIGN: " + nukeDesignLine;
        setSubB(subBText);
    }

    private static String pad3(int n) {
        if (n < 0) n = 0;
        return String.format("%03d", n);
    }

    private Color stageColor() {
        switch (stage) {
            case READY:     return MabUiTheme.C_GREEN;
            case INCOMING:  return MabUiTheme.C_RED;
            case INTERCEPT: return MabUiTheme.C_AMBER;
            case LAUNCH:    return MabUiTheme.C_MAGENTA;
            case IMPACT:    return MabUiTheme.TEXT_FAINT;
            case OVER:      return MabUiTheme.TEXT_MUTED;
            case BUILD:
            default:        return MabUiTheme.C_CYAN;
        }
    }

    private Color borderColor() {
        switch (stage) {
            case READY:     return MabUiTheme.C_GREEN_DIM;
            case INCOMING:  return MabUiTheme.C_RED;
            case INTERCEPT: return MabUiTheme.C_AMBER;
            case LAUNCH:    return MabUiTheme.C_MAGENTA;
            case IMPACT:    return MabUiTheme.GRID_LINE;
            case OVER:      return MabUiTheme.GRID_LINE;
            case BUILD:
            default:        return MabUiTheme.C_CYAN_DIM;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        int padL = 14, padR = 14, padT = 8, padB = 10;

        Color color = stageColor();

        g2.setPaint(new GradientPaint(0, 0, MabUiTheme.SHELL_PANEL_BG,
                w, h, MabUiTheme.SHELL_DEEP_BG));
        g2.fillRect(0, 0, w, h);
        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 26));
        if (mirrored) {
            g2.fillRect(w - 5, 0, 5, h);
        } else {
            g2.fillRect(0, 0, 5, h);
        }
        g2.setColor(new Color(255, 255, 255, 9));
        for (int y = 2; y < h; y += 4) {
            g2.drawLine(0, y, w, y);
        }

        // Top row: station + STATE label
        g2.setFont(MabUiTheme.TERM_TINY);
        g2.setColor(MabUiTheme.TEXT_FAINT);
        FontMetrics fmTop = g2.getFontMetrics();
        // Lamp
        int lampSize = 8;
        int lampY = padT + (fmTop.getAscent() / 2) - lampSize / 2 + 1;
        if (mirrored) {
            String stateLbl = "STATE";
            g2.drawString(stateLbl, padL, padT + fmTop.getAscent());
            int titleW = fmTop.stringWidth(stationTitle);
            g2.drawString(stationTitle, w - padR - lampSize - 6 - titleW,
                    padT + fmTop.getAscent());
            g2.setColor(color);
            g2.fillRect(w - padR - lampSize, lampY, lampSize, lampSize);
        } else {
            g2.setColor(color);
            g2.fillRect(padL, lampY, lampSize, lampSize);
            g2.setColor(MabUiTheme.TEXT_FAINT);
            g2.drawString(stationTitle, padL + lampSize + 6, padT + fmTop.getAscent());
            String stateLbl = "STATE";
            int sw = fmTop.stringWidth(stateLbl);
            g2.drawString(stateLbl, w - padR - sw, padT + fmTop.getAscent());
        }

        // Reserve space at bottom for doctrine hint strip when active
        int stripH = comboHint.isEmpty() ? 0 : 24;

        // Headline (stencil, big, glowed)
        g2.setFont(fittedFont(g2, MabUiTheme.STENCIL_HEADLINE,
                headline, w - padL - padR, 18f));
        FontMetrics fmH = g2.getFontMetrics();
        int hy = h - padB - stripH - fmH.getDescent() - fmTop.getHeight() - 4;
        // Glow
        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 70));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                drawHeadline(g2, headline, w, padL, padR, hy, dx, dy);
            }
        }
        g2.setColor(color);
        drawHeadline(g2, headline, w, padL, padR, hy, 0, 0);

        // Sub-line
        g2.setFont(MabUiTheme.TERM_SMALL);
        FontMetrics fmS = g2.getFontMetrics();
        int sy = h - padB - stripH;
        g2.setColor(MabUiTheme.TEXT);
        if (mirrored) {
            String right = ellipsize(g2, subB, Math.max(20, (w - padL - padR) / 2));
            int sbW = fmS.stringWidth(right);
            String left = ellipsize(g2, subA,
                    Math.max(20, w - padL - padR - sbW - 24));
            g2.drawString(left, padL, sy);
            String sep = "|";
            int sepW = fmS.stringWidth(sep);
            g2.setColor(MabUiTheme.TEXT_FAINT);
            g2.drawString(sep, w - padR - sbW - 8 - sepW, sy);
            g2.setColor(MabUiTheme.TEXT);
            g2.drawString(right, w - padR - sbW, sy);
        } else {
            String left = ellipsize(g2, subA, Math.max(20, (w - padL - padR) / 2));
            g2.drawString(left, padL, sy);
            int saW = fmS.stringWidth(left);
            g2.setColor(MabUiTheme.TEXT_FAINT);
            String sep = "|";
            g2.drawString(sep, padL + saW + 8, sy);
            int sepW = fmS.stringWidth(sep);
            g2.setColor(MabUiTheme.TEXT);
            int bx = padL + saW + 8 + sepW + 8;
            String right = ellipsize(g2, subB, Math.max(20, w - padR - bx));
            g2.drawString(right, bx, sy);
        }

        // Doctrine combo hint strip
        if (!comboHint.isEmpty()) {
            int sy2 = h - stripH;
            g2.setColor(new Color(
                    MabUiTheme.C_AMBER.getRed(),
                    MabUiTheme.C_AMBER.getGreen(),
                    MabUiTheme.C_AMBER.getBlue(), 28));
            g2.fillRect(0, sy2, w, stripH);
            g2.setColor(MabUiTheme.C_AMBER_DIM);
            g2.drawLine(0, sy2, w, sy2);
            g2.setFont(MabUiTheme.STENCIL_SMALL);
            FontMetrics fmHint = g2.getFontMetrics();
            String txt = ellipsize(g2, comboHint, w - padL - padR);
            int ty = sy2 + (stripH + fmHint.getAscent() - fmHint.getDescent()) / 2;
            g2.setColor(MabUiTheme.C_AMBER);
            if (mirrored) {
                g2.drawString(txt, w - padR - fmHint.stringWidth(txt), ty);
            } else {
                g2.drawString(txt, padL, ty);
            }
        }

        g2.dispose();
    }

    private static Font fittedFont(Graphics2D g2, Font base, String text,
                                   int maxWidth, float minSize) {
        if (text == null || text.isEmpty()) return base;
        float size = base.getSize2D();
        Font f = base;
        while (size > minSize && g2.getFontMetrics(f).stringWidth(text) > maxWidth) {
            size -= 1f;
            f = base.deriveFont(size);
        }
        return f;
    }

    private static String ellipsize(Graphics2D g2, String text, int maxWidth) {
        if (text == null) return "";
        if (maxWidth <= 0) return "";
        FontMetrics fm = g2.getFontMetrics();
        if (fm.stringWidth(text) <= maxWidth) return text;
        String dots = "...";
        int dotsW = fm.stringWidth(dots);
        String out = text;
        while (out.length() > 1 && fm.stringWidth(out) + dotsW > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out.length() <= 1 ? dots : out + dots;
    }

    private void drawHeadline(Graphics2D g2, String text, int w,
                              int padL, int padR, int baseY, int dx, int dy) {
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(text);
        int x;
        if (mirrored) {
            x = w - padR - tw + dx;
        } else {
            x = padL + dx;
        }
        g2.drawString(text, x, baseY + dy);
    }
}
