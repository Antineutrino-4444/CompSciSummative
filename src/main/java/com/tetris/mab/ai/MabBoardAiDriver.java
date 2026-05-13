package com.tetris.mab.ai;

import com.tetris.mab.ai.MabBoardAiPlan;
import com.tetris.model.Board;
import com.tetris.model.GameState;
import com.tetris.model.Position;
import com.tetris.model.Tetromino;

import java.awt.Color;

/**
 * Step 20 Second Refinement (extended in Third Refinement) — visible
 * placement AI for Player B.
 *
 * <p>Drives Player B's {@link GameState} so the player sees plausible
 * opponent activity. Heuristic placement search:
 *
 * <ol>
 *   <li>Snapshot the current board into a primitive {@code int[][]}.</li>
 *   <li>Try every (rotation 0..maxRot, target x) for the current piece.</li>
 *   <li>Score each landing by lines cleared, aggregate height, holes,
 *       bumpiness, and top-out risk — weights vary by difficulty.</li>
 *   <li>Pick the best deterministic plan (tie-break: lowest target x,
 *       then lowest rotation count).</li>
 *   <li>Move the real piece one step toward the plan per AI tick:
 *       rotate first, then translate, then dwell, then hard drop.</li>
 * </ol>
 *
 * <p>Step 20 Third Refinement adds:
 * <ul>
 *   <li>{@link MabAiDifficulty}-driven pacing (target PPS) and search
 *       weights — see {@link #setDifficulty}.</li>
 *   <li>{@code hardDropCount} accumulator surfaced via
 *       {@link MabBoardAiPlan#getHardDropCount()} so the player can
 *       confirm the AI is actually locking pieces.</li>
 * </ul>
 *
 * <p><b>Offline-only.</b> No randomness, no lookahead, no t-spin
 * recognition.
 */
public final class MabBoardAiDriver {

    /** Game-loop tick rate; matches {@code GameController.FRAME_INTERVAL_MS}. */
    private static final double TICKS_PER_SECOND = 1000.0 / 16.0;

    // ── Pacing ──
    private int actionIntervalTicks = 10;
    private int dropDwellTicks = 10;
    private double targetPps = 1.0;

    // ── Search weights (per difficulty) ──
    private int wLines = 100;
    private int wAggregate = 4;
    private int wHoles = 20;
    private int wBumpiness = 3;
    private int wMaxHeight = 6;
    private int wTopOut = 10000;
    /** Number of rotations to evaluate (1..4). EASY may use fewer. */
    private int rotationCandidates = 4;

    private final GameState board;
    private long tickCount;
    private int actionCounter;
    private boolean enabled = true;
    private MabAiDifficulty difficulty = MabAiDifficulty.NORMAL;
    private int hardDropCount;

    // Current plan
    private MabBoardAiPlan.Phase phase = MabBoardAiPlan.Phase.PLANNING;
    private int targetCol = -1;
    private int targetRotation = 0;
    private int planScore = 0;
    private int dwellLeft = 0;
    /** Number of rotation attempts since the current plan started; if a
     *  kick is illegal we eventually give up so the AI doesn't stall. */
    private int rotationAttempts = 0;

    public MabBoardAiDriver(GameState board) {
        if (board == null) throw new IllegalArgumentException("board");
        this.board = board;
        setDifficulty(MabAiDifficulty.NORMAL);
    }

    public GameState getBoard() { return board; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public long getTickCount() { return tickCount; }
    public int getHardDropCount() { return hardDropCount; }
    public double getTargetPps() { return targetPps; }
    public MabAiDifficulty getDifficulty() { return difficulty; }

    /**
     * Step 20 Third Refinement — set pacing + scoring weights from a
     * difficulty bucket. Higher difficulty = faster PPS and stricter
     * placement scoring (heavier hole/top-out penalties).
     *
     * <p>Approximate target PPS:
     * <ul>
     *   <li>EASY ≈ 0.50 PPS</li>
     *   <li>NORMAL ≈ 1.00 PPS</li>
     *   <li>HARD ≈ 1.67 PPS</li>
     *   <li>DEBUG ≈ 3.00 PPS</li>
     * </ul>
     *
     * <p>Pacing model: each piece takes roughly
     * {@code (avg_actions + dwellTicks + 1) * actionIntervalTicks}
     * ticks (≈ 5 input-like steps for rotate+move + N dwell decrements
     * + 1 hard-drop step). At a 60 Hz tick rate, target PPS is
     * {@code 60 / ((6 + dwell) * interval)}.
     */
    public void setDifficulty(MabAiDifficulty d) {
        this.difficulty = (d == null) ? MabAiDifficulty.NORMAL : d;
        switch (this.difficulty) {
            case EASY:
                actionIntervalTicks = 12;
                dropDwellTicks = 4;
                rotationCandidates = 2;
                wLines = 50;
                wAggregate = 3;
                wHoles = 8;
                wBumpiness = 2;
                wMaxHeight = 4;
                wTopOut = 3000;
                break;
            case HARD:
                actionIntervalTicks = 4;
                dropDwellTicks = 3;
                rotationCandidates = 4;
                wLines = 140;
                wAggregate = 5;
                wHoles = 35;
                wBumpiness = 5;
                wMaxHeight = 7;
                wTopOut = 50000;
                break;
            case DEBUG:
                actionIntervalTicks = 2;
                dropDwellTicks = 4;
                rotationCandidates = 4;
                wLines = 140;
                wAggregate = 5;
                wHoles = 35;
                wBumpiness = 5;
                wMaxHeight = 7;
                wTopOut = 50000;
                break;
            case NORMAL:
            default:
                actionIntervalTicks = 6;
                dropDwellTicks = 4;
                rotationCandidates = 4;
                wLines = 100;
                wAggregate = 4;
                wHoles = 20;
                wBumpiness = 3;
                wMaxHeight = 6;
                wTopOut = 10000;
                break;
        }
        recomputePps();
    }

    /** Manual override (used by legacy callers / tests). */
    public void setActionIntervalTicks(int interval) {
        this.actionIntervalTicks = Math.max(1, interval);
        recomputePps();
    }

    /** Manual override (used by legacy callers / tests). */
    public void setDropDwellTicks(int dwell) {
        this.dropDwellTicks = Math.max(0, dwell);
        recomputePps();
    }

    private void recomputePps() {
        // Average piece cycle ≈ 5 input-like actions + dwell decrements + 1 drop,
        // each separated by actionIntervalTicks ticks.
        double stepPlanCalls = 6.0 + dropDwellTicks;
        double ticksPerPiece = stepPlanCalls * actionIntervalTicks;
        targetPps = TICKS_PER_SECOND / Math.max(1.0, ticksPerPiece);
    }

    /** Snapshot of the current AI plan / phase / pps / drop count. */
    public MabBoardAiPlan getPlan() {
        return new MabBoardAiPlan(phase, targetCol, targetRotation, planScore,
                targetPps, hardDropCount);
    }

    /** Advance one frame. Drives gravity + occasional input-like steps. */
    public void tick() {
        if (!enabled) return;
        if (board.isGameOver()) {
            phase = MabBoardAiPlan.Phase.WAITING;
            return;
        }
        if (board.isPaused()) return;
        try {
            board.update();
        } catch (RuntimeException ignored) {}
        tickCount++;
        actionCounter++;
        if (actionCounter < actionIntervalTicks) return;
        actionCounter = 0;
        try {
            stepPlan();
        } catch (RuntimeException ignored) {}
    }

    private void stepPlan() {
        Tetromino piece = board.getCurrentPiece();
        if (piece == null) {
            phase = MabBoardAiPlan.Phase.WAITING;
            return;
        }
        if (targetCol < 0 || phase == MabBoardAiPlan.Phase.WAITING) {
            buildPlan(piece);
            phase = MabBoardAiPlan.Phase.ROTATING;
            dwellLeft = dropDwellTicks;
            rotationAttempts = 0;
        }

        // 1) Rotate toward the plan. Bail out after a few attempts so a
        //    rejected kick does not stall the piece forever.
        if (piece.getRotationState() != targetRotation && rotationAttempts < 6) {
            int before = piece.getRotationState();
            board.rotateCW();
            rotationAttempts++;
            int after = board.getCurrentPiece() == null
                    ? before
                    : board.getCurrentPiece().getRotationState();
            if (after == before) {
                // Rotation failed (kick rejected). Force-accept current
                // rotation so the AI can still translate + drop.
                targetRotation = before;
            }
            phase = MabBoardAiPlan.Phase.ROTATING;
            return;
        }
        if (piece.getRotationState() != targetRotation) {
            // Stalled — give up on rotating; place at current rotation.
            targetRotation = piece.getRotationState();
        }

        // 2) Translate horizontally toward the plan.
        int currentCol = piece.getBoardPosition().getX();
        if (currentCol < targetCol) {
            board.moveRight();
            phase = MabBoardAiPlan.Phase.MOVING;
            return;
        }
        if (currentCol > targetCol) {
            board.moveLeft();
            phase = MabBoardAiPlan.Phase.MOVING;
            return;
        }

        // 3) Aligned. Dwell briefly so the player can read the intent.
        if (dwellLeft > 0) {
            dwellLeft--;
            phase = MabBoardAiPlan.Phase.DROPPING;
            return;
        }

        // 4) Hard drop and invalidate the plan; next tick replans.
        board.hardDrop();
        hardDropCount++;
        phase = MabBoardAiPlan.Phase.WAITING;
        targetCol = -1;
    }

    private void buildPlan(Tetromino piece) {
        int boardW = Board.WIDTH;
        int boardH = Board.TOTAL_HEIGHT;
        int[][] snap = snapshotBoard(board.getBoard(), boardW, boardH);

        int bestScore = Integer.MIN_VALUE;
        int bestCol = piece.getBoardPosition().getX();
        int bestRot = piece.getRotationState();

        int rotMax = Math.max(1, Math.min(4, rotationCandidates));
        for (int rot = 0; rot < rotMax; rot++) {
            Tetromino rotated = piece.withRotation(rot);
            int pieceMinX = Integer.MAX_VALUE, pieceMaxX = Integer.MIN_VALUE;
            for (Position c : rotated.getAbsoluteCells()) {
                pieceMinX = Math.min(pieceMinX, c.getX());
                pieceMaxX = Math.max(pieceMaxX, c.getX());
            }
            int minDx = -pieceMinX;
            int maxDx = (boardW - 1) - pieceMaxX;

            for (int dx = minDx; dx <= maxDx; dx++) {
                Tetromino shifted = rotated.translate(dx, 0);
                Tetromino landed = dropToLanding(shifted, snap, boardW, boardH);
                if (landed == null) continue;
                int score = scoreLanding(landed, snap, boardW, boardH);
                int landedCol = shifted.getBoardPosition().getX();
                if (score > bestScore
                        || (score == bestScore && landedCol < bestCol)
                        || (score == bestScore && landedCol == bestCol && rot < bestRot)) {
                    bestScore = score;
                    bestCol = landedCol;
                    bestRot = rot;
                }
            }
        }
        targetCol = bestCol;
        targetRotation = bestRot;
        planScore = bestScore;
    }

    private static int[][] snapshotBoard(Board b, int w, int h) {
        int[][] grid = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Color c = b.getCell(x, y);
                grid[y][x] = (c == null) ? 0 : 1;
            }
        }
        return grid;
    }

    private static Tetromino dropToLanding(Tetromino piece, int[][] snap, int w, int h) {
        if (collides(piece, snap, w, h)) return null;
        Tetromino cur = piece;
        while (true) {
            Tetromino next = cur.moveDown();
            if (collides(next, snap, w, h)) return cur;
            cur = next;
        }
    }

    private static boolean collides(Tetromino piece, int[][] snap, int w, int h) {
        for (Position c : piece.getAbsoluteCells()) {
            int x = c.getX();
            int y = c.getY();
            if (x < 0 || x >= w || y < 0 || y >= h) return true;
            if (snap[y][x] != 0) return true;
        }
        return false;
    }

    private int scoreLanding(Tetromino landed, int[][] snap, int w, int h) {
        int[][] copy = new int[h][];
        for (int y = 0; y < h; y++) copy[y] = snap[y].clone();
        for (Position c : landed.getAbsoluteCells()) {
            if (c.getY() >= 0 && c.getY() < h && c.getX() >= 0 && c.getX() < w) {
                copy[c.getY()][c.getX()] = 1;
            }
        }
        int linesCleared = 0;
        for (int y = h - 1; y >= 0; y--) {
            boolean full = true;
            for (int x = 0; x < w; x++) {
                if (copy[y][x] == 0) { full = false; break; }
            }
            if (full) {
                linesCleared++;
                for (int yy = y; yy > 0; yy--) {
                    System.arraycopy(copy[yy - 1], 0, copy[yy], 0, w);
                }
                java.util.Arrays.fill(copy[0], 0);
                y++;
            }
        }
        int[] heights = new int[w];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (copy[y][x] != 0) {
                    heights[x] = h - y;
                    break;
                }
            }
        }
        int aggregate = 0;
        int maxHeight = 0;
        int holes = 0;
        int bumpiness = 0;
        for (int x = 0; x < w; x++) {
            aggregate += heights[x];
            maxHeight = Math.max(maxHeight, heights[x]);
        }
        for (int x = 0; x < w; x++) {
            int top = h - heights[x];
            for (int y = top + 1; y < h; y++) {
                if (copy[y][x] == 0) holes++;
            }
        }
        for (int x = 0; x < w - 1; x++) {
            bumpiness += Math.abs(heights[x] - heights[x + 1]);
        }
        boolean topOutRisk = maxHeight >= h - 4;

        int score = 0;
        score += linesCleared * wLines;
        score -= aggregate * wAggregate;
        score -= holes * wHoles;
        score -= bumpiness * wBumpiness;
        score -= maxHeight * wMaxHeight;
        if (topOutRisk) score -= wTopOut;
        return score;
    }
}
