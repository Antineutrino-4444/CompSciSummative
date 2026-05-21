package com.tetris.view;

import com.tetris.model.Board;
import com.tetris.model.GameState;
import com.tetris.model.Position;
import com.tetris.model.Settings;
import com.tetris.model.Tetromino;
import com.tetris.view.theme.BlockRenderer;
import com.tetris.view.theme.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * GamePanel.java — central playfield renderer.
 *
 * Responsibilities:
 *   • Paint the 10×20 visible board, the locked stack, the active piece,
 *     and the ghost / drop preview.
 *   • Animate a TETR.IO-style line-clear effect: a brief bright pulse on
 *     the cleared rows followed by colored shatter-shards that spray
 *     outward and fall under gravity.
 *   • Render a transient action splash ("TETRIS!", "T-SPIN DOUBLE",
 *     "PERFECT CLEAR", "B2B x4 …") that fades over ~1 s.
 *   • Render Pause / Game Over overlays as semi-transparent cards.
 *
 * Painting cost is kept low — fonts and colors come from {@link Theme},
 * cells go through the shared {@link BlockRenderer}, particles are pooled
 * in a small ArrayList and culled aggressively.
 */
public class GamePanel extends JPanel {

    private static final int VISIBLE_HEIGHT = Board.VISIBLE_HEIGHT;
    private static final int WIDTH          = Board.WIDTH;
    private static final int BUFFER         = Board.BUFFER_HEIGHT;

    /** Cached strokes — reused every frame. */
    private static final BasicStroke STROKE_GRID  = new BasicStroke(1f);
    private static final BasicStroke STROKE_BOARD = new BasicStroke(2f);
    private static final BasicStroke STROKE_OVERLAY = new BasicStroke(1.5f);

    private static final boolean FAST_BACKDROP = true;

    private GameState gameState;

    private BufferedImage cachedBackdrop;
    private int cachedBackdropW = -1;
    private int cachedBackdropH = -1;
    private int cachedBackdropStyleKey = 0;
    private boolean backdropDirty = true;

    // ── Line-clear animation ────────────────────────────────────────
    /** Wall-clock start of the current clear flash window (ns). 0 = idle. */
    private long clearAnimStartNs = 0;
    /** Y-indices of rows currently flashing (in board coords). */
    private final List<Integer> flashingRows = new ArrayList<>(4);
    /** Active shatter shards. Pooled and pruned every frame. */
    private final List<Shard> shards = new ArrayList<>(64);
    /** Total length of the flash phase. */
    private static final long FLASH_PHASE_NS    = 110_000_000L;  // 110 ms
    /** Total length of the shatter phase (after flash). */
    private static final long SHATTER_PHASE_NS  = 520_000_000L;  // 520 ms

    private int lastTotalLinesCleared = 0;

    // ── Action splash ──────────────────────────────────────────────
    private String splashText = "";
    private long  splashStartNs = 0;
    private static final long SPLASH_DURATION_NS = 1_100_000_000L;   // 1.1 s
    private String lastSplashSource = "";

    public GamePanel(GameState gameState) {
        this.gameState = gameState;
        setBackground(Theme.BG_0);
        setFocusable(true);
        setPreferredSize(new Dimension(380, 760));
    }

    public void setGameState(GameState gameState) {
        this.gameState = gameState;
        flashingRows.clear();
        shards.clear();
        clearAnimStartNs = 0;
        splashText = "";
        lastTotalLinesCleared = gameState != null
                ? gameState.getScoreSystem().getTotalLinesCleared() : 0;
        lastSplashSource = "";
    }

    public void invalidateBackdropCache() {
        backdropDirty = true;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        // ── Layout ──
        int cs   = cellSize();
        int boardW = cs * WIDTH;
        int boardH = cs * VISIBLE_HEIGHT;
        int bx   = (getWidth()  - boardW) / 2;
        int by   = (getHeight() - boardH) / 2;

        // ── Cached backdrop ──
        long backdropStartNs = System.nanoTime();
        try {
            paintBackdrop(g2);
        } finally {
            SwingPaintDiagnostics.recordBackdropPaint(System.nanoTime() - backdropStartNs);
        }

        // ── Board surface ──
        Settings settings = Settings.get();
        float boardAlpha = (float) settings.getBoardOpacity();
        Composite prev = g2.getComposite();
        g2.setComposite(AlphaComposite.SrcOver.derive(boardAlpha));
        g2.setColor(Theme.BG_1);
        g2.fillRect(bx, by, boardW, boardH);
        g2.setComposite(prev);

        // ── Grid lines (very faint, TETR.IO-like) ──
        float gridAlpha = (float) settings.getGridOpacity();
        if (gridAlpha > 0.01f) {
            g2.setComposite(AlphaComposite.SrcOver.derive(gridAlpha));
            g2.setColor(Theme.alpha(Theme.ACCENT_DIM, 60));
            g2.setStroke(STROKE_GRID);
            for (int x = 1; x < WIDTH; x++) {
                int gx = bx + x * cs;
                g2.drawLine(gx, by, gx, by + boardH);
            }
            for (int y = 1; y < VISIBLE_HEIGHT; y++) {
                int gy = by + y * cs;
                g2.drawLine(bx, gy, bx + boardW, gy);
            }
            g2.setComposite(prev);
        }

        // ── Detect newly cleared lines BEFORE drawing the stack so the
        //     flash phase paints correctly on this same frame.
        detectAndStartClearAnim();
        detectAndStartSplash();

        long now = System.nanoTime();
        long sinceClear = now - clearAnimStartNs;
        boolean inFlash   = clearAnimStartNs != 0 && sinceClear < FLASH_PHASE_NS;
        boolean animating = clearAnimStartNs != 0 && sinceClear < FLASH_PHASE_NS + SHATTER_PHASE_NS;

        // ── Locked stack. Rows currently flashing get a bright overlay. ──
        Board board = gameState.getBoard();
        for (int row = BUFFER; row < BUFFER + VISIBLE_HEIGHT; row++) {
            boolean isFlashingRow = inFlash && flashingRows.contains(row);
            for (int col = 0; col < WIDTH; col++) {
                Color c = board.getCell(col, row);
                if (c == null) continue;
                int x = bx + col * cs;
                int y = by + (row - BUFFER) * cs;
                BlockRenderer.draw(g2, x, y, cs, c, BlockRenderer.Style.SOLID);
                if (isFlashingRow) {
                    float t = sinceClear / (float) FLASH_PHASE_NS;
                    float a = Math.max(0f, 1f - t);
                    g2.setComposite(AlphaComposite.SrcOver.derive(a));
                    g2.setColor(Color.WHITE);
                    g2.fillRect(x + 1, y + 1, cs - 2, cs - 2);
                    g2.setComposite(prev);
                }
            }
        }

        // ── Flash-phase rows that have already shifted out of the grid:
        //     paint a fading white band where they used to be. ──
        if (inFlash) {
            float t = sinceClear / (float) FLASH_PHASE_NS;
            float a = Math.max(0f, 1f - t * 0.5f);
            g2.setComposite(AlphaComposite.SrcOver.derive(a));
            for (int row : flashingRows) {
                if (row < BUFFER) continue;
                int y = by + (row - BUFFER) * cs;
                g2.setColor(Color.WHITE);
                g2.fillRect(bx, y, boardW, cs);
            }
            g2.setComposite(prev);
        }

        // ── Ghost piece ──
        if (gameState.getCurrentPiece() != null && !gameState.isGameOver()) {
            Tetromino ghost = gameState.getGhostPiece();
            if (ghost != null) {
                float ghostAlpha = (float) settings.getGhostOpacity();
                g2.setComposite(AlphaComposite.SrcOver.derive(ghostAlpha));
                drawPiece(g2, ghost, bx, by, cs, BlockRenderer.Style.GHOST);
                g2.setComposite(prev);
            }
        }

        // ── Active piece ──
        if (gameState.getCurrentPiece() != null && !gameState.isGameOver()) {
            drawPiece(g2, gameState.getCurrentPiece(), bx, by, cs, BlockRenderer.Style.SOLID);
        }

        // ── Shatter particles (drawn over the board so they overlap
        //     the surrounding chrome too). ──
        if (!shards.isEmpty()) {
            paintShards(g2, now);
        }

        // ── Border around the board ──
        g2.setColor(Theme.ACCENT_DIM);
        g2.setStroke(STROKE_BOARD);
        g2.drawRect(bx, by, boardW, boardH);

        // ── Action splash (TETRIS! / PERFECT CLEAR / B2B x3) ──
        paintSplash(g2, now, bx, by, boardW, boardH);

        // Keep repainting while anything is animating.
        if (animating || !shards.isEmpty() || splashStartNs != 0) {
            repaint();
        }

        // ── Overlays ──
        if (gameState.isGameOver()) {
            drawCenterOverlay(g2, "GAME OVER",
                    "Press " + keyName(Settings.get().getKeyReset())
                            + " to restart  •  "
                            + keyName(Settings.get().getKeyExitStage()) + " to menu",
                    Theme.DANGER);
        } else if (gameState.isPaused()) {
            drawCenterOverlay(g2, "PAUSED",
                    "Press " + keyName(Settings.get().getKeyPause()) + " to resume",
                    Theme.ACCENT);
        }
        } finally {
            g2.dispose();
        }
    }

    private static String keyName(int keyCode) {
        return keyCode == 0 ? "UNBOUND" : java.awt.event.KeyEvent.getKeyText(keyCode).toUpperCase();
    }

    // ───────────────────── Clear animation ──────────────────────────

    private void detectAndStartClearAnim() {
        int total = gameState.getScoreSystem().getTotalLinesCleared();
        if (total > lastTotalLinesCleared) {
            lastTotalLinesCleared = total;
            startClearAnimation();
        } else if (total < lastTotalLinesCleared) {
            // Restart — sync silently.
            lastTotalLinesCleared = total;
        }

        if (clearAnimStartNs != 0 &&
                System.nanoTime() - clearAnimStartNs >= FLASH_PHASE_NS + SHATTER_PHASE_NS) {
            clearAnimStartNs = 0;
            flashingRows.clear();
        }
    }

    private void startClearAnimation() {
        Board board = gameState.getBoard();
        List<Integer> rows = board.getLastClearedRowIndices();
        List<Color[]> colors = board.getLastClearedRowColors();
        if (rows == null || rows.isEmpty()) return;

        flashingRows.clear();
        flashingRows.addAll(rows);
        clearAnimStartNs = System.nanoTime();

        // Spawn shards. Each cleared cell produces 4 shards spraying
        // outward with gravity and drag — like TETR.IO's "shatter" clear.
        int cs = cellSize();
        int boardW = cs * WIDTH;
        int bx = (getWidth()  - boardW) / 2;
        int by = (getHeight() - cs * VISIBLE_HEIGHT) / 2;

        java.util.Random rng = new java.util.Random();
        for (int i = 0; i < rows.size(); i++) {
            int row = rows.get(i);
            if (row < BUFFER) continue;
            Color[] rowColors = colors.get(i);
            int py = by + (row - BUFFER) * cs;
            for (int col = 0; col < WIDTH; col++) {
                Color c = rowColors[col];
                if (c == null) continue;
                int px = bx + col * cs;
                spawnShardsForCell(px, py, cs, c, rng);
            }
        }
    }

    private void spawnShardsForCell(int x, int y, int cs, Color color, java.util.Random rng) {
        int half = cs / 2;
        for (int q = 0; q < 4; q++) {
            int qx = q % 2;          // 0 left, 1 right
            int qy = q / 2;          // 0 top,  1 bottom
            float sx = x + qx * half + rng.nextFloat() * (half - 4) + 2;
            float sy = y + qy * half + rng.nextFloat() * (half - 4) + 2;

            // Velocity: outward from cell center, with an upward boost.
            float dirX = (qx == 0 ? -1f : 1f) * (0.5f + rng.nextFloat() * 0.8f);
            float dirY = (qy == 0 ? -1f : 1f) * (0.4f + rng.nextFloat() * 0.6f);
            dirY -= 0.6f + rng.nextFloat() * 0.4f;

            float speed = cs * (0.05f + rng.nextFloat() * 0.04f); // px/ms
            float vx = dirX * speed;
            float vy = dirY * speed;

            float size = half * (0.45f + rng.nextFloat() * 0.45f);
            float spin = (rng.nextFloat() - 0.5f) * 0.012f;       // rad/ms
            shards.add(new Shard(sx, sy, vx, vy, size, color, spin,
                    rng.nextFloat() * (float) Math.PI));
        }
    }

    private void paintShards(Graphics2D g2, long now) {
        if (clearAnimStartNs == 0) {
            shards.clear();
            return;
        }
        long sinceFlashEnd = now - (clearAnimStartNs + FLASH_PHASE_NS);
        if (sinceFlashEnd < 0) return;       // Still in flash phase — hold shards.
        float dtMs = 16f;                    // Fixed step — repaint cadence.
        float lifeMs = sinceFlashEnd / 1_000_000f;
        float lifeFrac = Math.min(1f, lifeMs / (SHATTER_PHASE_NS / 1_000_000f));

        Composite prev = g2.getComposite();
        for (int i = shards.size() - 1; i >= 0; i--) {
            Shard s = shards.get(i);
            s.x += s.vx * dtMs;
            s.y += s.vy * dtMs;
            s.vy += 0.0011f * dtMs;          // gravity (px/ms²)
            s.vx *= 0.985f;                  // air drag
            s.angle += s.spin * dtMs;

            float a = Math.max(0f, 1f - lifeFrac);
            if (a <= 0.02f) {
                shards.remove(i);
                continue;
            }
            g2.setComposite(AlphaComposite.SrcOver.derive(a));
            paintShard(g2, s);
        }
        g2.setComposite(prev);

        if (lifeFrac >= 1f) shards.clear();
    }

    private void paintShard(Graphics2D g2, Shard s) {
        java.awt.geom.AffineTransform old = g2.getTransform();
        g2.translate(s.x, s.y);
        g2.rotate(s.angle);
        int half = (int) (s.size / 2f);
        g2.setColor(s.color);
        g2.fillRect(-half, -half, (int) s.size, (int) s.size);
        g2.setColor(Theme.alpha(Color.WHITE, 60));
        g2.fillRect(-half, -half, (int) s.size, 1);
        g2.setTransform(old);
    }

    // ───────────────────── Action splash ────────────────────────────

    private void detectAndStartSplash() {
        String action = gameState.getScoreSystem().getLastAction();
        if (action == null || action.isEmpty()) {
            lastSplashSource = "";
            return;
        }
        if (!action.equals(lastSplashSource)) {
            lastSplashSource = action;
            splashText = displayActionFor(action);
            splashStartNs = System.nanoTime();
        }
    }

    private String displayActionFor(String full) {
        String upper = full.toUpperCase();
        if (upper.contains("PERFECT CLEAR")) return "PERFECT CLEAR";
        if (upper.contains("T-SPIN TRIPLE")) return "T-SPIN TRIPLE";
        if (upper.contains("T-SPIN DOUBLE")) return "T-SPIN DOUBLE";
        if (upper.contains("T-SPIN SINGLE")) return "T-SPIN SINGLE";
        if (upper.contains("T-SPIN MINI"))   return "T-SPIN MINI";
        if (upper.contains("T-SPIN"))        return "T-SPIN";
        if (upper.contains("TETRIS"))        return "TETRIS";
        if (upper.contains("TRIPLE"))        return "TRIPLE";
        if (upper.contains("DOUBLE"))        return "DOUBLE";
        if (upper.contains("SINGLE"))        return "SINGLE";
        return upper;
    }

    private void paintSplash(Graphics2D g2, long now, int bx, int by, int boardW, int boardH) {
        if (splashStartNs == 0 || splashText.isEmpty()) return;
        long elapsed = now - splashStartNs;
        if (elapsed >= SPLASH_DURATION_NS) {
            splashStartNs = 0;
            return;
        }
        float t = elapsed / (float) SPLASH_DURATION_NS;
        // Fade: pop in fast, hold, fade out.
        float alpha;
        if (t < 0.15f)      alpha = t / 0.15f;
        else if (t < 0.65f) alpha = 1f;
        else                alpha = 1f - (t - 0.65f) / 0.35f;
        alpha = Math.max(0f, Math.min(1f, alpha));
        if (alpha <= 0.01f) return;

        // Scale: pop slightly past 1.0 then settle.
        float scale;
        if (t < 0.15f)      scale = 0.85f + 0.20f * (t / 0.15f);
        else if (t < 0.30f) scale = 1.05f - 0.05f * ((t - 0.15f) / 0.15f);
        else                scale = 1.0f;

        Composite prev = g2.getComposite();
        g2.setComposite(AlphaComposite.SrcOver.derive(alpha));

        boolean b2b = lastSplashSource.startsWith("B2B");
        int chain = gameState.getScoreSystem().getB2bChain();
        int combo = gameState.getScoreSystem().getCombo();

        Font main = Theme.FONT_DISPLAY.deriveFont(Font.BOLD,
                Theme.FONT_DISPLAY.getSize2D() * scale);
        Font sub  = Theme.FONT_MONO_BOLD;

        g2.setFont(main);
        FontMetrics fmMain = g2.getFontMetrics(main);
        int textW = fmMain.stringWidth(splashText);
        int cx = bx + boardW / 2 - textW / 2;
        int cy = by + boardH / 2;

        // Soft glow halo behind the text.
        Color glow = b2b ? Theme.HIGHLIGHT : Theme.ACCENT_BRIGHT;
        for (int r = 12; r >= 4; r -= 4) {
            g2.setColor(Theme.alpha(glow, 22));
            g2.fillRoundRect(cx - r - 8, cy - fmMain.getAscent() - r,
                    textW + (r + 8) * 2, fmMain.getHeight() + r * 2, 18, 18);
        }

        // Chain qualifier above the main text.
        g2.setFont(sub);
        FontMetrics fmSub = g2.getFontMetrics(sub);
        String qualifier = "";
        if (b2b && chain >= 2) qualifier = "BACK-TO-BACK x" + chain;
        else if (b2b)          qualifier = "BACK-TO-BACK";
        if (combo >= 2) {
            qualifier = qualifier.isEmpty()
                    ? "COMBO x" + combo
                    : qualifier + "   •   COMBO x" + combo;
        }
        if (!qualifier.isEmpty()) {
            int qw = fmSub.stringWidth(qualifier);
            g2.setColor(Theme.HIGHLIGHT);
            g2.drawString(qualifier, bx + boardW / 2 - qw / 2,
                    cy - fmMain.getAscent() - 6);
        }

        // Main word with subtle drop shadow.
        g2.setFont(main);
        g2.setColor(Theme.alpha(Color.BLACK, 180));
        g2.drawString(splashText, cx + 2, cy + 2);
        g2.setColor(Color.WHITE);
        g2.drawString(splashText, cx, cy);

        g2.setComposite(prev);
    }

    // ───────────────────── Helpers ──────────────────────────────────

    private int cellSize() {
        int cw = (getWidth()  - 24) / WIDTH;
        int ch = (getHeight() - 24) / VISIBLE_HEIGHT;
        return Math.max(8, Math.min(cw, ch));
    }

    private void paintBackdrop(Graphics2D g2) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        int styleKey = backdropStyleKey();
        if (cachedBackdrop == null
                || cachedBackdropW != w
                || cachedBackdropH != h
                || cachedBackdropStyleKey != styleKey
                || backdropDirty) {
            rebuildBackdropCache(w, h, styleKey);
        }

        g2.drawImage(cachedBackdrop, 0, 0, null);
    }

    private void rebuildBackdropCache(int w, int h, int styleKey) {
        BufferedImage img = createOpaqueCompatibleImage(w, h);
        Graphics2D bg = img.createGraphics();
        try {
            bg.setComposite(AlphaComposite.Src);
            bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            bg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

            if (FAST_BACKDROP) {
                bg.setColor(Theme.BG_0);
                bg.fillRect(0, 0, w, h);
            } else {
                paintSimpleCachedBackdrop(bg, w, h);
            }
        } finally {
            bg.dispose();
        }

        cachedBackdrop = img;
        cachedBackdropW = w;
        cachedBackdropH = h;
        cachedBackdropStyleKey = styleKey;
        backdropDirty = false;
    }

    private static BufferedImage createOpaqueCompatibleImage(int w, int h) {
        if (!GraphicsEnvironment.isHeadless()) {
            GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration();
            return gc.createCompatibleImage(w, h, Transparency.OPAQUE);
        }
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    }

    private static void paintSimpleCachedBackdrop(Graphics2D bg, int w, int h) {
        bg.setColor(Theme.blend(Theme.BG_0, Theme.BG_1, 0.35f));
        bg.fillRect(0, 0, w, h);

        int band = Math.max(24, h / 10);
        bg.setColor(Theme.BG_0);
        bg.fillRect(0, 0, w, band);
        bg.fillRect(0, h - band, w, band);
    }

    private static int backdropStyleKey() {
        int key = Boolean.hashCode(FAST_BACKDROP);
        key = 31 * key + Theme.BG_0.getRGB();
        key = 31 * key + Theme.BG_1.getRGB();
        return key;
    }

    private void drawPiece(Graphics2D g2, Tetromino piece, int bx, int by,
                            int cs, BlockRenderer.Style style) {
        Color color = piece.getType().getColor();
        for (Position p : piece.getAbsoluteCells()) {
            int row = p.getY();
            int col = p.getX();
            if (row < BUFFER) continue; // hidden in buffer
            int x = bx + col * cs;
            int y = by + (row - BUFFER) * cs;
            BlockRenderer.draw(g2, x, y, cs, color, style);
        }
    }

    private void drawCenterOverlay(Graphics2D g2, String title, String sub, Color accent) {
        int cardW = 360, cardH = 140;
        int w = getWidth(), h = getHeight();
        int cx = (w - cardW) / 2;
        int cy = (h - cardH) / 2;

        g2.setColor(Theme.BG_2);
        g2.fillRoundRect(cx, cy, cardW, cardH, Theme.RADIUS_L, Theme.RADIUS_L);
        g2.setColor(accent);
        g2.setStroke(STROKE_OVERLAY);
        g2.drawRoundRect(cx, cy, cardW, cardH, Theme.RADIUS_L, Theme.RADIUS_L);

        g2.setFont(Theme.FONT_TITLE);
        g2.setColor(accent);
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(title);
        g2.drawString(title, cx + (cardW - tw) / 2, cy + 60);

        g2.setFont(Theme.FONT_BODY);
        g2.setColor(Theme.TEXT_BODY);
        fm = g2.getFontMetrics();
        int sw = fm.stringWidth(sub);
        g2.drawString(sub, cx + (cardW - sw) / 2, cy + 92);
    }

    /** A single line-clear shatter shard. */
    private static final class Shard {
        float x, y, vx, vy, size, angle, spin;
        Color color;
        Shard(float x, float y, float vx, float vy, float size, Color color, float spin, float angle) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.size = size; this.color = color; this.spin = spin; this.angle = angle;
        }
    }
}
