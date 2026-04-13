package com.tetris.model;

/**
 * ScoreSystem.java
 * ================
 * Implements the modern Tetris Guideline scoring system with support for
 * T-spins, combos, back-to-back bonuses, and level-based gravity.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * SCORING RULES (Tetris Guideline / Modern Standards)
 * ═══════════════════════════════════════════════════════════════════════
 *
 * BASE LINE CLEAR POINTS (multiplied by current level):
 * ─────────────────────────────────────────────────────
 *   Single (1 line)  → 100 × level
 *   Double (2 lines) → 300 × level
 *   Triple (3 lines) → 500 × level
 *   Tetris (4 lines) → 800 × level
 *
 * SOFT DROP:
 *   1 point per cell dropped (not multiplied by level).
 *
 * HARD DROP:
 *   2 points per cell dropped (not multiplied by level).
 *
 * ═══════════════════════════════════════════════════════════════════════
 * T-SPIN SCORING
 * ═══════════════════════════════════════════════════════════════════════
 * A T-spin occurs when:
 *   1. The last successful move was a rotation (not a translation).
 *   2. The piece is a T-piece.
 *   3. At least 3 of the 4 corner cells of the T-piece's bounding box
 *      are occupied (by walls or locked blocks).
 *
 * T-SPIN MINI:
 *   Occurs when only 2 of the "front" corners are filled, or the kick
 *   offset used was (0, 0) or a simple offset, and fewer than 2
 *   "pointing" corners are filled. Simplified: we detect mini when
 *   the wall kick used was NOT the last resort kick (test 4).
 *
 *   T-Spin Mini no lines → 100 × level
 *   T-Spin Mini Single   → 200 × level
 *   T-Spin Mini Double   → 400 × level
 *
 * T-SPIN (FULL):
 *   T-Spin no lines → 400 × level
 *   T-Spin Single   → 800 × level
 *   T-Spin Double   → 1200 × level
 *   T-Spin Triple   → 1600 × level
 *
 * ═══════════════════════════════════════════════════════════════════════
 * BACK-TO-BACK BONUS
 * ═══════════════════════════════════════════════════════════════════════
 * Consecutive "difficult" clears (Tetris, T-Spin, T-Spin Mini) earn a
 * 50% score bonus (×1.5). The chain breaks when a non-difficult clear
 * occurs (single, double, triple without T-spin).
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
        this.lastAction = "";
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
     * This is the main scoring method — call it after a piece locks and
     * lines are cleared.
     *
     * @param linesCleared number of lines cleared (0–4)
     * @param isTSpin      true if this was a T-spin (full)
     * @param isTSpinMini  true if this was a T-spin mini
     */
    public void onLineClear(int linesCleared, boolean isTSpin, boolean isTSpinMini) {
        if (linesCleared == 0 && !isTSpin && !isTSpinMini) {
            // No lines and no T-spin → reset combo
            combo = -1;
            lastAction = "";
            return;
        }

        // Calculate base points
        int basePoints = 0;
        boolean isDifficult = false;
        StringBuilder actionBuilder = new StringBuilder();

        if (isTSpin) {
            isDifficult = true;
            switch (linesCleared) {
                case 0 -> { basePoints = 400; actionBuilder.append("T-Spin"); }
                case 1 -> { basePoints = 800; actionBuilder.append("T-Spin Single"); }
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

        // Back-to-back bonus (50% extra for consecutive difficult clears)
        if (isDifficult && backToBack) {
            points = (int) (points * 1.5);
            actionBuilder.insert(0, "B2B ");
        }

        // Update back-to-back state
        if (linesCleared > 0) {
            backToBack = isDifficult;
        }

        // Combo bonus
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
    public String getLastAction() { return lastAction; }
}
