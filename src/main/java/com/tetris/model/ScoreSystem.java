package com.tetris.model;

/**
 * ScoreSystem.java
 * ================
 * Implements scoring identical to the official tetr.io solo-mode table
 * (see https://tetris.wiki/TETR.IO), with support for T-spins, combos,
 * back-to-back, perfect clears, and level-based gravity.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * SCORING RULES (identical to TETR.IO solo)
 * ═══════════════════════════════════════════════════════════════════════
 *
 * BASE LINE CLEAR POINTS (multiplied by current level):
 * ─────────────────────────────────────────────────────
 *   Single (1 line)   →  100 × level
 *   Double (2 lines)  →  300 × level
 *   Triple (3 lines)  →  500 × level
 *   Tetris / Quad     →  800 × level   ("difficult" → eligible for B2B)
 *
 * SOFT DROP:  1 point per cell (not level-multiplied).
 * HARD DROP:  2 points per cell (not level-multiplied).
 *
 * ═══════════════════════════════════════════════════════════════════════
 * T-SPIN SCORING (all "difficult" → eligible for B2B)
 * ═══════════════════════════════════════════════════════════════════════
 * A T-spin occurs when:
 *   1. The last successful move was a rotation (not a translation).
 *   2. The piece is a T-piece.
 *   3. At least 3 of the 4 corner cells of the T-piece's bounding box
 *      are occupied (by walls or locked blocks).
 *
 * T-SPIN MINI:
 *   Occurs when only 2 of the "front" corners are filled, or the kick
 *   used was a simple offset and fewer than 2 "pointing" corners are
 *   filled.
 *
 *   T-Spin Mini no lines →  100 × level
 *   T-Spin Mini Single   →  200 × level
 *   T-Spin Mini Double   →  400 × level
 *
 * T-SPIN (FULL):
 *   T-Spin no lines →  400 × level
 *   T-Spin Single   →  800 × level
 *   T-Spin Double   → 1200 × level
 *   T-Spin Triple   → 1600 × level
 *
 * ═══════════════════════════════════════════════════════════════════════
 * BACK-TO-BACK BONUS
 * ═══════════════════════════════════════════════════════════════════════
 * Consecutive "difficult" clears (Tetris, any T-Spin, any T-Spin Mini)
 * earn a +50% bonus (×1.5) on the line-clear value. The chain breaks
 * when a non-difficult clear occurs (single, double, triple without
 * T-spin).
 *
 * ═══════════════════════════════════════════════════════════════════════
 * ALL CLEAR (PERFECT CLEAR) — Tetris Guideline / TETR.IO
 * ═══════════════════════════════════════════════════════════════════════
 * If the board is completely empty after a line-clearing piece locks,
 * a per-line bonus is added on top of the normal score:
 *
 *   1-line PC (Single)   →  +800  × level
 *   2-line PC (Double)   →  +1200 × level
 *   3-line PC (Triple)   →  +1800 × level
 *   4-line PC (Tetris)   →  +2000 × level
 *   B2B Tetris PC        →  +3200 × level extra (only when the previous
 *                            difficult clear was also a Tetris/T-spin
 *                            and this clear is a 4-line PC)
 *
 * The PC bonus never breaks Back-to-Back.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * COMBO SYSTEM
 * ═══════════════════════════════════════════════════════════════════════
 * Each consecutive piece that clears at least one line increments the
 * combo counter. Bonus: 50 × combo × level.
 * The combo resets to -1 when a piece locks without clearing any lines.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * LEVEL & GRAVITY
 * ═══════════════════════════════════════════════════════════════════════
 * Level increases every 10 lines cleared.
 * Gravity (seconds per drop) = (0.8 − (level−1) × 0.007) ^ (level−1)
 *
 * At level 1:  ~1.0 seconds per drop
 * At level 10: ~0.1 seconds per drop
 * At level 15: ~0.01 seconds per drop (very fast)
 * At level 20: ~0.001 seconds (essentially instant gravity)
 */
public class ScoreSystem {

    /** Lines needed per level. */
    private static final int LINES_PER_LEVEL = 10;

    // ─────────────────────── State ───────────────────────────────

    private int score;
    private int level;
    private int totalLinesCleared;
    private int combo;           // -1 means no active combo
    private boolean backToBack;  // whether the last clear was "difficult"

    /** Length of the current Back-to-Back chain (0 when inactive,
     *  1 after the first difficult clear, 2 after two consecutive, etc.).
     *  Mirrors TETR.IO's b2b counter shown in the HUD. */
    private int b2bChain;

    /** Total tetrominoes locked onto the board this game. Used for PPS. */
    private int piecesPlaced;

    /** Wall-clock time of the first piece lock (ms), or 0 if no piece
     *  has been placed yet. Used for pieces-per-second calculation. */
    private long startTimeMs;

    /** Tracks the last action description for display purposes. */
    private String lastAction;

    // ─────────────────────── Constructor ─────────────────────────

    /**
     * Creates a new ScoreSystem starting at level 1.
     */
    public ScoreSystem() {
        this(1);
    }

    /**
     * Creates a new ScoreSystem starting at the specified level.
     *
     * @param startLevel the initial level (1-based)
     */
    public ScoreSystem(int startLevel) {
        this.score = 0;
        this.level = Math.max(1, startLevel);
        this.totalLinesCleared = 0;
        this.combo = -1;
        this.backToBack = false;
        this.b2bChain = 0;
        this.piecesPlaced = 0;
        this.startTimeMs = 0L;
        this.lastAction = "";
    }

    /**
     * Records that a tetromino was just locked. Call exactly once per lock
     * (regardless of whether lines were cleared). Used to track total
     * pieces and pieces-per-second.
     */
    public void onPieceLocked() {
        if (startTimeMs == 0L) {
            startTimeMs = System.currentTimeMillis();
        }
        piecesPlaced++;
    }

    // ─────────────────────── Scoring Events ─────────────────────

    /**
     * Awards points for a soft drop (pressing down).
     *
     * @param cellsDropped how many rows the piece moved down
     */
    public void addSoftDrop(int cellsDropped) {
        score += cellsDropped;  // 1 point per cell, not level-multiplied
    }

    /**
     * Awards points for a hard drop (instant drop).
     *
     * @param cellsDropped how many rows the piece moved down
     */
    public void addHardDrop(int cellsDropped) {
        score += cellsDropped * 2;  // 2 points per cell, not level-multiplied
    }

    /**
     * Processes a line clear event with optional T-spin information.
     *
     * Mirrors the official tetr.io solo-mode scoring table exactly:
     *   Single 100, Double 300, Triple 500, Quad (Tetris) 800,
     *   T-Spin 0/1/2/3   = 400 / 800 / 1200 / 1600,
     *   Mini-Spin 0/1/2  = 100 / 200 / 400,
     *   All Clear        = +3500 (flat),
     *   B2B difficult    = ×1.5 multiplier,
     *   Combo step       = +50 × combo
     * All values (except soft / hard drop) are then multiplied by the
     * current level.
     *
     * Call this after a piece locks and lines are cleared.
     *
     * @param linesCleared number of lines cleared (0–4)
     * @param isTSpin      true if this was a T-spin (full)
     * @param isTSpinMini  true if this was a T-spin mini
     * @param perfectClear true if the board is completely empty after the
     *                     clear (awards the All Clear bonus)
     */
    public void onLineClear(int linesCleared, boolean isTSpin,
                            boolean isTSpinMini, boolean perfectClear) {
        if (linesCleared == 0 && !isTSpin && !isTSpinMini) {
            // No lines and no T-spin → reset combo
            combo = -1;
            lastAction = "";
            return;
        }

        // Calculate base points — values are the official tetr.io table.
        int basePoints = 0;
        boolean isDifficult = false;
        StringBuilder actionBuilder = new StringBuilder();

        if (isTSpin) {
            isDifficult = true;
            switch (linesCleared) {
                case 0 -> { basePoints = 400;  actionBuilder.append("T-Spin"); }
                case 1 -> { basePoints = 800;  actionBuilder.append("T-Spin Single"); }
                case 2 -> { basePoints = 1200; actionBuilder.append("T-Spin Double"); }
                case 3 -> { basePoints = 1600; actionBuilder.append("T-Spin Triple"); }
            }
        } else if (isTSpinMini) {
            isDifficult = true;
            switch (linesCleared) {
                case 0 -> { basePoints = 100; actionBuilder.append("T-Spin Mini"); }
                case 1 -> { basePoints = 200; actionBuilder.append("T-Spin Mini Single"); }
                case 2 -> { basePoints = 400; actionBuilder.append("T-Spin Mini Double"); }
            }
        } else {
            switch (linesCleared) {
                case 1 -> { basePoints = 100; actionBuilder.append("Single"); }
                case 2 -> { basePoints = 300; actionBuilder.append("Double"); }
                case 3 -> { basePoints = 500; actionBuilder.append("Triple"); }
                case 4 -> { basePoints = 800; isDifficult = true; actionBuilder.append("Tetris"); }
            }
        }

        // Apply level multiplier
        int points = basePoints * level;

        // Back-to-back bonus: tetr.io uses a flat 1.5× on the line-clear
        // value for consecutive difficult clears.
        if (isDifficult && backToBack) {
            points = (int) (points * 1.5);
            actionBuilder.insert(0, "B2B ");
        }

        // All Clear (Perfect Clear): per-line guideline bonus on top of
        // the normal line-clear value. Never breaks B2B. The bonus depends
        // on how many lines the PC-triggering piece cleared:
        //   Single 800 / Double 1200 / Triple 1800 / Tetris 2000
        // Plus an extra +3200 × level when a Tetris PC continues a B2B.
        if (perfectClear) {
            int pcBase = switch (linesCleared) {
                case 1 -> 800;
                case 2 -> 1200;
                case 3 -> 1800;
                case 4 -> 2000;
                default -> 0;
            };
            points += pcBase * level;
            if (linesCleared == 4 && backToBack) {
                // B2B Tetris Perfect Clear — TETR.IO awards a substantial
                // additional bonus on top of the standard PC bonus.
                points += 3200 * level;
                actionBuilder.append(" • B2B Perfect Clear");
            } else {
                actionBuilder.append(" • Perfect Clear");
            }
        }

        // Update back-to-back state and chain length.
        if (linesCleared > 0) {
            if (isDifficult) {
                if (backToBack) {
                    b2bChain++;
                } else {
                    b2bChain = 1;
                }
                backToBack = true;
            } else {
                backToBack = false;
                b2bChain = 0;
            }
        }

        // Combo bonus: 50 × combo × level per tetr.io.
        if (linesCleared > 0) {
            combo++;
            if (combo > 0) {
                int comboBonus = 50 * combo * level;
                points += comboBonus;
                actionBuilder.append(" Combo ").append(combo);
            }
        }

        score += points;
        lastAction = actionBuilder.toString();

        // Update lines and level
        totalLinesCleared += linesCleared;
        level = Math.max(level, (totalLinesCleared / LINES_PER_LEVEL) + 1);
    }

    /**
     * Back-compat overload: assumes no perfect clear. Kept so any
     * external caller that hasn't been updated still compiles.
     */
    public void onLineClear(int linesCleared, boolean isTSpin, boolean isTSpinMini) {
        onLineClear(linesCleared, isTSpin, isTSpinMini, false);
    }

    // ─────────────────────── Gravity ────────────────────────────

    /**
     * Returns the gravity interval in milliseconds for the current level.
     *
     * Formula: interval = (0.8 − (level−1) × 0.007) ^ (level−1) seconds
     * Converted to milliseconds.
     *
     * This produces a smooth difficulty curve:
     *   Level 1:  ~1000ms (1 second)
     *   Level 5:  ~516ms
     *   Level 10: ~87ms
     *   Level 15: ~5ms
     *   Level 20+: 1ms (minimum)
     *
     * @return drop interval in milliseconds, minimum 1ms
     */
    public int getGravityInterval() {
        double seconds = Math.pow(0.8 - (level - 1) * 0.007, level - 1);
        int millis = (int) (seconds * 1000);
        return Math.max(millis, 1);
    }

    // ─────────────────────── Accessors ──────────────────────────

    public int getScore() { return score; }
    public int getLevel() { return level; }
    public int getTotalLinesCleared() { return totalLinesCleared; }
    public int getCombo() { return combo; }
    public boolean isBackToBack() { return backToBack; }
    public int getB2bChain() { return b2bChain; }
    public int getPiecesPlaced() { return piecesPlaced; }
    public String getLastAction() { return lastAction; }

    /**
     * Returns the number of milliseconds elapsed since the first piece
     * was locked, or 0 if no pieces have been placed yet.
     */
    public long getElapsedMs() {
        if (startTimeMs == 0L) return 0L;
        return System.currentTimeMillis() - startTimeMs;
    }

    /**
     * Returns pieces locked per second since the first lock. Returns 0
     * for the first ~250 ms to avoid a misleading spike on the first
     * piece. Mirrors the PPS readout shown in TETR.IO and Jstris.
     */
    public double getPiecesPerSecond() {
        long elapsed = getElapsedMs();
        if (elapsed < 250L || piecesPlaced <= 0) return 0.0;
        return piecesPlaced * 1000.0 / elapsed;
    }
}
