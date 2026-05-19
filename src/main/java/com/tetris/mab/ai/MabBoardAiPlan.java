package com.tetris.mab.ai;

/**
 * Read-only snapshot of the visible-board AI's current intent. Surfaced to
 * {@link com.tetris.mab.ui.MabOpponentBoardPanel} so the player can see
 * what the opponent is aiming at.
 *
 * <p>Offline-only.
 */
public final class MabBoardAiPlan {

    public enum Phase { PLANNING, ROTATING, MOVING, DROPPING, WAITING }

    private final Phase phase;
    private final int targetCol;
    private final int targetRotation;
    private final int score;
    private final double targetPps;
    private final int hardDropCount;

    public MabBoardAiPlan(Phase phase, int targetCol, int targetRotation, int score,
                          double targetPps, int hardDropCount) {
        this.phase = phase == null ? Phase.PLANNING : phase;
        this.targetCol = targetCol;
        this.targetRotation = targetRotation;
        this.score = score;
        this.targetPps = targetPps;
        this.hardDropCount = hardDropCount;
    }

    public MabBoardAiPlan(Phase phase, int targetCol, int targetRotation, int score) {
        this(phase, targetCol, targetRotation, score, 0.0, 0);
    }

    public Phase getPhase() { return phase; }
    public int getTargetCol() { return targetCol; }
    public int getTargetRotation() { return targetRotation; }
    public int getScore() { return score; }
    public double getTargetPps() { return targetPps; }
    public int getHardDropCount() { return hardDropCount; }

    public String describe() {
        if (targetCol < 0) {
            return "phase=" + phase
                    + " pps=" + String.format("%.2f", targetPps)
                    + " drops=" + hardDropCount + " (no plan)";
        }
        return "phase=" + phase + " col=" + targetCol + " rot=" + targetRotation
                + " score=" + score
                + " pps=" + String.format("%.2f", targetPps)
                + " drops=" + hardDropCount;
    }
}
