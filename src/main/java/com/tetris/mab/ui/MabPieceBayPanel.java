package com.tetris.mab.ui;

import com.tetris.model.GameState;
import com.tetris.model.TetrominoType;

import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

/**
 * Step 23 Refinement \u2014 thematic HOLD / NEXT bay matching the HTML
 * mockup's {@code .bay} component. Reads from a {@link GameState}
 * (the player's). Pure read-only; never mutates piece generation.
 *
 * <p>Renders:
 * <ul>
 *   <li>HOLD label + 4\u00d72 piece preview cell.</li>
 *   <li>NEXT label + 3 stacked piece preview cells.</li>
 * </ul>
 *
 * <p>Mirroring is purely cosmetic (the labels gain a leading dot and
 * align to the right edge so the bay reads "from the outside in" on
 * the AI side, just like the mockup).
 *
 * <p><b>Offline-only.</b>
 */
public final class MabPieceBayPanel extends JPanel {

    private static final int PREVIEW_COUNT = 3;

    private final GameState gameState;
    private final boolean mirrored;

    public MabPieceBayPanel(GameState gameState, boolean mirrored) {
        this.gameState = gameState;
        this.mirrored = mirrored;
        setOpaque(true);
        setBackground(MabUiTheme.SHELL_PANEL_BG);
        setBorder(new LineBorder(MabUiTheme.GRID_LINE, 1));
        setMinimumSize(new Dimension(96, 240));
        setPreferredSize(new Dimension(132, 480));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        int padX = 8;
        int y = 10;
        int previewWInner = w - padX * 2;
        if (previewWInner <= 0) { g2.dispose(); return; }
        // HOLD section.
        y = drawLabel(g2, "HOLD", padX, y, previewWInner);
        int holdH = Math.max(36, previewWInner / 2);
        drawPieceCell(g2, padX, y, previewWInner, holdH,
                gameState == null ? null : gameState.getHoldPiece());
        y += holdH + 12;
        // NEXT section.
        y = drawLabel(g2, "NEXT", padX, y, previewWInner);
        int remaining = h - y - 10;
        int gap = 6;
        int slotH = Math.max(28, (remaining - gap * (PREVIEW_COUNT - 1)) / PREVIEW_COUNT);
        List<TetrominoType> next = gameState == null
                ? java.util.Collections.emptyList()
                : gameState.getPreviewPieces();
        for (int i = 0; i < PREVIEW_COUNT; i++) {
            TetrominoType t = i < next.size() ? next.get(i) : null;
            drawPieceCell(g2, padX, y, previewWInner, slotH, t);
            y += slotH + gap;
        }
        g2.dispose();
    }

    private int drawLabel(Graphics2D g2, String text, int x, int y, int w) {
        g2.setFont(MabUiTheme.TERM_TINY);
        FontMetrics fm = g2.getFontMetrics();
        // Square dot (lit lamp style) on the leading edge.
        g2.setColor(MabUiTheme.C_CYAN_DIM);
        if (mirrored) {
            int tw = fm.stringWidth(text);
            g2.fillRect(x + w - 4, y, 4, 4);
            g2.setColor(MabUiTheme.TEXT_FAINT);
            g2.drawString(text, x + w - tw - 8, y + fm.getAscent());
        } else {
            g2.fillRect(x, y, 4, 4);
            g2.setColor(MabUiTheme.TEXT_FAINT);
            g2.drawString(text, x + 8, y + fm.getAscent());
        }
        return y + fm.getAscent() + 4;
    }

    private void drawPieceCell(Graphics2D g2, int x, int y, int w, int h,
                               TetrominoType type) {
        // Cell frame.
        g2.setColor(MabUiTheme.SHELL_DEEP_BG);
        g2.fillRect(x, y, w, h);
        g2.setColor(MabUiTheme.GRID_LINE);
        g2.drawRect(x, y, w - 1, h - 1);
        if (type == null) return;
        // Piece grid: 4 cols x 2 rows, scaled to fit.
        int cell = Math.min((w - 8) / 4, (h - 8) / 2);
        if (cell <= 1) return;
        int gridW = cell * 4;
        int gridH = cell * 2;
        int gx = x + (w - gridW) / 2;
        int gy = y + (h - gridH) / 2;
        boolean[][] shape = shapeFor(type);
        Color c = type.getColor();
        if (c == null) c = MabUiTheme.C_CYAN;
        for (int ry = 0; ry < 2; ry++) {
            for (int cx = 0; cx < 4; cx++) {
                if (!shape[ry][cx]) continue;
                int bx = gx + cx * cell;
                int by = gy + ry * cell;
                g2.setColor(c);
                g2.fillRect(bx, by, cell - 1, cell - 1);
                // Block highlight \u2014 thin top-left lighter line.
                g2.setColor(new Color(255, 255, 255, 60));
                g2.drawLine(bx, by, bx + cell - 2, by);
                g2.drawLine(bx, by, bx, by + cell - 2);
                g2.setColor(new Color(0, 0, 0, 80));
                g2.drawLine(bx + cell - 2, by, bx + cell - 2, by + cell - 2);
                g2.drawLine(bx, by + cell - 2, bx + cell - 2, by + cell - 2);
            }
        }
    }

    /** Compact 4x2 representation for preview only. */
    private static boolean[][] shapeFor(TetrominoType t) {
        switch (t) {
            case I: return new boolean[][]{{true, true, true, true},   {false,false,false,false}};
            case O: return new boolean[][]{{false,true, true, false},  {false,true, true, false}};
            case T: return new boolean[][]{{false,true, false,false},  {true, true, true, false}};
            case S: return new boolean[][]{{false,true, true, false},  {true, true, false,false}};
            case Z: return new boolean[][]{{true, true, false,false},  {false,true, true, false}};
            case J: return new boolean[][]{{true, false,false,false},  {true, true, true, false}};
            case L: return new boolean[][]{{false,false,true, false},  {true, true, true, false}};
            default: return new boolean[][]{{false,false,false,false}, {false,false,false,false}};
        }
    }
}
