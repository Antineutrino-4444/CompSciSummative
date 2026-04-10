package com.tetris.model;

/**
 * Handles the modern Tetris scoring system as defined by the Tetris Guideline.
 *
 * <p>This system tracks score, level, total lines cleared, and applies the
 * following scoring rules:</p>
 *
 * <h3>Base Line Clear Scoring (multiplied by level)</h3>
 * <ul>
 *   <li>Single: 100 × level</li>
 *   <li>Double: 300 × level</li>
 *   <li>Triple: 500 × level</li>
 *   <li>Tetris (4 lines): 800 × level</li>
 * </ul>
 *
 * <h3>T-Spin Scoring (multiplied by level)</h3>
 * <ul>
 *   <li>T-Spin (no lines): 400 × level</li>
 *   <li>T-Spin Single: 800 × level</li>
 *   <li>T-Spin Double: 1200 × level</li>
 *   <li>T-Spin Triple: 1600 × level</li>
 *   <li>Mini T-Spin (no lines): 100 × level</li>
 *   <li>Mini T-Spin Single: 200 × level</li>
 * </ul>
 *
 * <h3>Back-to-Back Bonus</h3>
 * <p>Consecutive "difficult" line clears (Tetris or any T-Spin line clear)
 * receive a 1.5× multiplier. The chain breaks when a non-difficult line
 * clear occurs.</p>
 *
 * <h3>Combo Scoring</h3>
 * <p>Each consecutive piece placement that clears lines adds 50 × combo × level
 * to the score. The combo counter resets when a piece is placed without
 * clearing any lines.</p>
 *
 * <h3>Perfect Clear Scoring</h3>
 * <ul>
 *   <li>Single Perfect Clear: 800 × level</li>
 *   <li>Double Perfect Clear: 1200 × level</li>
 *   <li>Triple Perfect Clear: 1800 × level</li>
 *   <li>Tetris Perfect Clear: 2000 × level</li>
 * </ul>
 *
 * <h3>Other Scoring</h3>
 * <ul>
 *   <li>Soft drop: 1 point per cell</li>
 *   <li>Hard drop: 2 points per cell</li>
 * </ul>
 *
 * <h3>Level Progression</h3>
 * <p>The player starts at level 1. Every 10 lines cleared advances the level
 * by 1. Gravity speed increases with each level according to the standard
 * formula: {@code (0.8 - (level-1) * 0.007)^(level-1)} seconds per row.</p>
 */
public class ScoreSystem {

    private long score;
    private int level;
    private int totalLinesCleared;
    private int combo;       // -1 means no active combo, 0+ is the combo count
    private boolean backToBack; // true if last difficult clear qualifies for B2B

    /** The last action description for display purposes. */
    private String lastAction = "";

    /**
     * Creates a new ScoreSystem starting at the specified level.
     *
     * @param startLevel the starting level (typically 1)
     */
    public ScoreSystem(int startLevel) {
        this.level = Math.max(1, startLevel);
        this.score = 0;
        this.totalLinesCleared = 0;
        this.combo = -1;
        this.backToBack = false;
    }

    /**
     * Creates a new ScoreSystem starting at level 1.
     */
    public ScoreSystem() {
        this(1);
    }

    /**
     * Processes a line clear event and updates the score accordingly.
     *
     * @param linesCleared  number of lines cleared (0-4)
     * @param isTSpin       true if the clear was a T-Spin
     * @param isMiniTSpin   true if the clear was a Mini T-Spin
     * @param isPerfectClear true if the board is empty after the clear
     */
    public void onLineClear(int linesCleared, boolean isTSpin, boolean isMiniTSpin,
                            boolean isPerfectClear) {
        if (linesCleared == 0 && !isTSpin && !isMiniTSpin) {
            // No lines and no T-spin: reset combo
            combo = -1;
            return;
        }

        // Calculate base score
        int baseScore = 0;
        boolean isDifficult = false;

        if (isTSpin) {
            isDifficult = linesCleared > 0;
            switch (linesCleared) {
                case 0 -> { baseScore = 400; lastAction = "T-Spin"; }
                case 1 -> { baseScore = 800; lastAction = "T-Spin Single"; }
                case 2 -> { baseScore = 1200; lastAction = "T-Spin Double"; }
                case 3 -> { baseScore = 1600; lastAction = "T-Spin Triple"; }
                default -> baseScore = 0;
            }
        } else if (isMiniTSpin) {
            switch (linesCleared) {
                case 0 -> { baseScore = 100; lastAction = "Mini T-Spin"; }
                case 1 -> { baseScore = 200; lastAction = "Mini T-Spin Single"; isDifficult = true; }
                default -> baseScore = 0;
            }
        } else if (linesCleared > 0) {
            switch (linesCleared) {
                case 1 -> { baseScore = 100; lastAction = "Single"; }
                case 2 -> { baseScore = 300; lastAction = "Double"; }
                case 3 -> { baseScore = 500; lastAction = "Triple"; }
                case 4 -> { baseScore = 800; lastAction = "Tetris"; isDifficult = true; }
                default -> baseScore = 0;
            }
        }

        // Apply level multiplier
        long addScore = (long) baseScore * level;

        // Back-to-back bonus: 1.5× for consecutive difficult clears
        if (isDifficult) {
            if (backToBack) {
                addScore = addScore * 3 / 2; // 1.5× bonus
                lastAction = "B2B " + lastAction;
            }
            backToBack = true;
        } else if (linesCleared > 0) {
            // Non-difficult line clear breaks B2B chain
            backToBack = false;
        }

        score += addScore;

        // Combo scoring
        if (linesCleared > 0) {
            combo++;
            if (combo > 0) {
                score += 50L * combo * level;
                lastAction += " Combo " + combo;
            }
        } else {
            combo = -1;
        }

        // Perfect clear bonus
        if (isPerfectClear && linesCleared > 0) {
            int pcBonus = switch (linesCleared) {
                case 1 -> 800;
                case 2 -> 1200;
                case 3 -> 1800;
                case 4 -> 2000;
                default -> 0;
            };
            score += (long) pcBonus * level;
            lastAction += " Perfect Clear";
        }

        // Update lines and level
        if (linesCleared > 0) {
            totalLinesCleared += linesCleared;
            updateLevel();
        }
    }

    /**
     * Adds points for a soft drop (1 point per cell dropped).
     *
     * @param cells number of cells dropped
     */
    public void onSoftDrop(int cells) {
        score += cells;
    }

    /**
     * Adds points for a hard drop (2 points per cell dropped).
     *
     * @param cells number of cells dropped
     */
    public void onHardDrop(int cells) {
        score += 2L * cells;
    }

    /**
     * Called when a piece is placed without clearing lines, to reset the combo.
     */
    public void onPieceLockNoLines() {
        combo = -1;
    }

    /**
     * Updates the level based on total lines cleared.
     * Level advances every 10 lines.
     */
    private void updateLevel() {
        int newLevel = (totalLinesCleared / 10) + 1;
        if (newLevel > level) {
            level = newLevel;
        }
    }

    /**
     * Calculates the gravity speed (seconds per row) for the current level.
     *
     * <p>Uses the standard Tetris Guideline formula:
     * {@code (0.8 - (level-1) * 0.007) ^ (level-1)}</p>
     *
     * @return seconds per gravity drop (lower = faster)
     */
    public double getGravityInterval() {
        return Math.pow(0.8 - (level - 1) * 0.007, level - 1);
    }

    // --- Getters ---

    public long getScore() { return score; }
    public int getLevel() { return level; }
    public int getTotalLinesCleared() { return totalLinesCleared; }
    public int getCombo() { return combo; }
    public boolean isBackToBack() { return backToBack; }
    public String getLastAction() { return lastAction; }
}
