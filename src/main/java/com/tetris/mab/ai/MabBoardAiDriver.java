package com.tetris.mab.ai;

import com.tetris.mab.IncomingThreatState;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.NukeBuildState;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ParticipantState;
import com.tetris.mab.ai.search.AiBoardModel;
import com.tetris.mab.ai.search.AiEvaluator;
import com.tetris.mab.ai.search.AiMove;
import com.tetris.mab.ai.search.AiSearch;
import com.tetris.mab.ai.search.AiSearchSettings;
import com.tetris.mab.launch.ThreatStatus;
import com.tetris.mab.nuke.NukeDesign;
import com.tetris.mab.nuke.NukeDoctrineType;
import com.tetris.model.Board;
import com.tetris.model.GameState;
import com.tetris.model.Tetromino;
import com.tetris.model.TetrominoType;

import java.util.ArrayList;
import java.util.List;

/**
 * Visible-board AI for Player B's {@link GameState}.
 *
 * <p>From-first-principles rewrite. The driver:
 * <ol>
 *   <li>Snapshots the playable board into an {@link AiBoardModel} once
 *       per piece.</li>
 *   <li>Asks {@link AiSearch} (configured by {@link AiSearchSettings})
 *       for the best landing for the current piece, considering hold
 *       and a slice of the preview queue.</li>
 *   <li>Drives the real {@code GameState} toward that landing one
 *       legal input at a time at the difficulty-tuned cadence.</li>
 * </ol>
 *
 * <p>Fair: never peeks beyond {@link AiSearchSettings#lookaheadDepth}
 * preview pieces. Never injects pieces, never alters the bag, never
 * touches the opponent.
 *
 * <p>Offline-only.
 */
public final class MabBoardAiDriver {

    private static final double TICKS_PER_SECOND = 1000.0 / 16.0;

    private final GameState board;
    private final AiEvaluator evaluator = new AiEvaluator();

    private MabAiDifficulty difficulty = MabAiDifficulty.MEDIUM;
    private AiSearchSettings settings = AiSearchSettings.forDifficulty(MabAiDifficulty.MEDIUM);
    private AiSearch search;
    private long seedBase = 31_4159_2653L;

    private boolean enabled = true;
    private long tickCount;
    private int hardDropCount;
    private int actionCounter;
    private int dwellLeft;
    private int planVersion;
    private MabBoardAiPlan.Phase phase = MabBoardAiPlan.Phase.PLANNING;
    private double planScore;
    private int targetCol = -1;
    private int targetRotation = 0;
    private boolean targetUsedHold = false;
    private boolean holdAttempted = false;
    private int planAge;
    private double measuredPps;
    private long lastDropMs;
    private int rotationAttempts;
    private int translationStalls;

    /** Optional B2B/combo carrier, kept in sync with line clears. */
    private boolean b2bActive = false;
    private int comboCount = 0;
    private int linesClearedTotal = 0;

    /** Telemetry counters. */
    private int searchCalls;
    private int searchCandidates;
    private int mistakes;
    private int holdsUsed;
    private int tetrises;
    private int tspins;
    private int perfectClears;

    /** EDT timing telemetry; read by the F3 performance overlay. */
    private long tickTimingSamples;
    private long lastTickNs;
    private long maxTickNs;
    private long totalTickNs;
    private long searchTimingSamples;
    private long lastSearchNs;
    private long maxSearchNs;
    private long totalSearchNs;
    private int lastSearchDepthReached;
    private int lastSearchCandidates;

    /** Optional MAB strategic context — when present, the evaluator gets
     *  armed/route/threat info pulled from the live match. */
    private MutuallyAssuredBlocksMatch contextMatch;
    private ParticipantId contextParticipantId;

    public MabBoardAiDriver(GameState board) {
        if (board == null) throw new IllegalArgumentException("board");
        this.board = board;
        applySettings(MabAiDifficulty.MEDIUM);
        board.addListener(new com.tetris.events.GameEventListener() {
            @Override
            public void onLinesCleared(LinesClearedEvent e) {
                linesClearedTotal += e.count();
                if (e.tetris()) tetrises++;
                if (e.tSpin()) tspins++;
                if (e.perfectClear()) perfectClears++;
                if (e.count() > 0) {
                    comboCount = e.combo() + 1;
                    b2bActive = e.backToBack() || e.tetris() || e.tSpin();
                } else {
                    comboCount = 0;
                }
            }
        });
    }

    public GameState getBoard() { return board; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public long getTickCount() { return tickCount; }
    public int getHardDropCount() { return hardDropCount; }
    public MabAiDifficulty getDifficulty() { return difficulty; }
    public double getTargetPps() { return settings.pacing.targetPps; }
    public double getMeasuredPps() { return measuredPps; }
    public int getSearchCalls() { return searchCalls; }
    public int getSearchCandidates() { return searchCandidates; }
    public int getMistakes() { return mistakes; }
    public int getHoldsUsed() { return holdsUsed; }
    public int getTetrises() { return tetrises; }
    public int getTspins() { return tspins; }
    public int getPerfectClears() { return perfectClears; }
    public int getLinesClearedTotal() { return linesClearedTotal; }
    public long getTickTimingSamples() { return tickTimingSamples; }
    public long getLastTickNs() { return lastTickNs; }
    public long getMaxTickNs() { return maxTickNs; }
    public long getAverageTickNs() {
        return tickTimingSamples <= 0L ? 0L : totalTickNs / tickTimingSamples;
    }
    public long getSearchTimingSamples() { return searchTimingSamples; }
    public long getLastSearchNs() { return lastSearchNs; }
    public long getMaxSearchNs() { return maxSearchNs; }
    public long getAverageSearchNs() {
        return searchTimingSamples <= 0L ? 0L : totalSearchNs / searchTimingSamples;
    }
    public int getLastSearchDepthReached() { return lastSearchDepthReached; }
    public int getLastSearchCandidates() { return lastSearchCandidates; }

    public void setDifficulty(MabAiDifficulty d) {
        applySettings(d == null ? MabAiDifficulty.MEDIUM : d);
    }

    public void capSearchTimeBudgetMillis(int maxMillis) {
        int capped = Math.max(2, maxMillis);
        if (settings.searchTimeBudgetMillis <= capped) return;
        settings = new AiSearchSettings(settings.beamWidth, settings.lookaheadDepth,
                settings.fullTuckSearch, settings.include180, settings.useHold,
                settings.mistakeRate, settings.pacing, settings.designAware,
                settings.planLaunches, settings.prefersTetrisWell,
                capped, settings.rotateBeforeMoveOptimisation);
        search = new AiSearch(settings, evaluator, seedBase ^ 0x9E3779B97F4A7C15L);
    }

    public void setSeed(long seed) {
        this.seedBase = seed;
        this.search = new AiSearch(settings, evaluator, seed ^ 0x5DEECE66DL);
    }

    /** Legacy compat — kept so older callers compile. */
    public void setActionIntervalTicks(int interval) {
        // The new pacing model is settings-derived. The override is
        // honoured by widening the dwell so total piece cadence still
        // roughly matches the requested interval.
        AiSearchSettings.Pacing cur = settings.pacing;
        AiSearchSettings.Pacing pacing =
                new AiSearchSettings.Pacing(cur.targetPps,
                        Math.max(1, interval), cur.dropDwellTicks);
        settings = new AiSearchSettings(settings.beamWidth, settings.lookaheadDepth,
                settings.fullTuckSearch, settings.include180, settings.useHold,
                settings.mistakeRate, pacing, settings.designAware,
                settings.planLaunches, settings.prefersTetrisWell,
                settings.searchTimeBudgetMillis,
                settings.rotateBeforeMoveOptimisation);
    }

    /** Legacy compat. */
    public void setDropDwellTicks(int dwell) {
        AiSearchSettings.Pacing cur = settings.pacing;
        AiSearchSettings.Pacing pacing =
                new AiSearchSettings.Pacing(cur.targetPps,
                        cur.actionIntervalTicks, Math.max(0, dwell));
        settings = new AiSearchSettings(settings.beamWidth, settings.lookaheadDepth,
                settings.fullTuckSearch, settings.include180, settings.useHold,
                settings.mistakeRate, pacing, settings.designAware,
                settings.planLaunches, settings.prefersTetrisWell,
                settings.searchTimeBudgetMillis,
                settings.rotateBeforeMoveOptimisation);
    }

    /** Read-only snapshot for HUD display. */
    public MabBoardAiPlan getPlan() {
        return new MabBoardAiPlan(phase, targetCol, targetRotation,
                (int) Math.round(planScore), settings.pacing.targetPps, hardDropCount);
    }

    public AiSearchSettings getSettings() { return settings; }

    /**
     * Attach the live MAB match + participant so the board AI can lift
     * strategic context (armed / route / incoming threats) into the
     * search. Optional — when unset the AI plays a pure-Tetris policy.
     */
    public void attachStrategicContext(MutuallyAssuredBlocksMatch match,
                                       ParticipantId participantId) {
        this.contextMatch = match;
        this.contextParticipantId = participantId;
    }

    private void applySettings(MabAiDifficulty d) {
        this.difficulty = d;
        this.settings = AiSearchSettings.forDifficulty(d);
        evaluator.setWellColumn(AiBoardModel.WIDTH - 1);
        evaluator.setWeights(AiEvaluator.Weights.forDifficulty(d));
        this.search = new AiSearch(settings, evaluator, seedBase ^ 0x9E3779B97F4A7C15L);
    }

    /** Advance one game-loop frame. */
    public void tick() {
        long startNs = System.nanoTime();
        try {
            if (!enabled) return;
            if (board.isGameOver()) {
                phase = MabBoardAiPlan.Phase.WAITING;
                return;
            }
            if (board.isPaused()) return;
            try { board.update(); } catch (RuntimeException ignored) {}
            tickCount++;
            actionCounter++;
            if (actionCounter < settings.pacing.actionIntervalTicks) return;
            actionCounter = 0;
            try { stepPlan(); } catch (RuntimeException ignored) {}
        } finally {
            recordTickTiming(System.nanoTime() - startNs);
        }
    }

    private void stepPlan() {
        Tetromino piece = board.getCurrentPiece();
        if (piece == null) {
            phase = MabBoardAiPlan.Phase.WAITING;
            return;
        }
        if (targetCol < 0 || phase == MabBoardAiPlan.Phase.WAITING) {
            replan(piece);
            phase = MabBoardAiPlan.Phase.PLANNING;
            dwellLeft = settings.pacing.dropDwellTicks;
            rotationAttempts = 0;
            translationStalls = 0;
        }

        // If we planned a hold-swap, actually press hold first (once).
        if (targetUsedHold && !holdAttempted) {
            holdAttempted = true;
            boolean ok = board.hold();
            if (ok) {
                holdsUsed++;
                // After hold, current piece changes — the plan's rotation
                // and column targets refer to the *new* piece type. We
                // re-derive the plan on the next tick for clarity.
                targetCol = -1;
                phase = MabBoardAiPlan.Phase.WAITING;
            } else {
                // Hold rejected (already used this piece) — fall through
                // and play the current piece anyway with the same target.
                targetUsedHold = false;
            }
            return;
        }

        // 1) Rotate to target rotation.
        if (piece.getRotationState() != targetRotation && rotationAttempts < 8) {
            int before = piece.getRotationState();
            int delta = (targetRotation - before + 4) % 4;
            boolean ok;
            if (delta == 2) ok = tryRotate180();
            else if (delta == 1) ok = board.rotateCW();
            else ok = board.rotateCCW();
            rotationAttempts++;
            int after = board.getCurrentPiece() == null
                    ? before : board.getCurrentPiece().getRotationState();
            if (!ok || after == before) {
                // Rotation rejected (kick failed) — bail out so we don't stall.
                if (rotationAttempts >= 6) {
                    targetRotation = after;
                    rotationAttempts = 0;
                }
            }
            phase = MabBoardAiPlan.Phase.ROTATING;
            return;
        }
        if (piece.getRotationState() != targetRotation) {
            // Stuck rotating — accept current rotation and try to land.
            targetRotation = piece.getRotationState();
        }

        // 2) Translate horizontally.
        int currentCol = piece.getBoardPosition().getX();
        if (currentCol < targetCol) {
            boolean moved = board.moveRight();
            if (!moved) translationStalls++;
            phase = MabBoardAiPlan.Phase.MOVING;
            if (translationStalls > 4) {
                // We're wedged — replan rather than stall forever.
                targetCol = -1;
            }
            return;
        }
        if (currentCol > targetCol) {
            boolean moved = board.moveLeft();
            if (!moved) translationStalls++;
            phase = MabBoardAiPlan.Phase.MOVING;
            if (translationStalls > 4) {
                targetCol = -1;
            }
            return;
        }

        // 3) Dwell briefly so the placement reads.
        if (dwellLeft > 0) {
            dwellLeft--;
            phase = MabBoardAiPlan.Phase.DROPPING;
            return;
        }

        // 4) Hard drop. Line-clear bookkeeping comes through the
        //    GameEventListener installed in the constructor.
        board.hardDrop();
        long now = System.currentTimeMillis();
        if (lastDropMs > 0) {
            double dt = (now - lastDropMs) / 1000.0;
            if (dt > 0) {
                double instant = 1.0 / dt;
                measuredPps = (measuredPps * 0.7) + (instant * 0.3);
            }
        }
        lastDropMs = now;
        hardDropCount++;
        phase = MabBoardAiPlan.Phase.WAITING;
        targetCol = -1;
    }

    private boolean tryRotate180() {
        // Game engine exposes rotate180 only via input action — we
        // approximate by two CW rotations.
        if (board.rotateCW()) {
            // best-effort: another CW lands us on the 180° state.
            board.rotateCW();
            return true;
        }
        return false;
    }

    private void replan(Tetromino piece) {
        searchCalls++;
        planAge = 0;
        AiBoardModel model = AiBoardModel.fromBoard(board.getBoard());
        TetrominoType current = piece.getType();
        TetrominoType holdType = board.getHoldPiece();
        boolean canHold = !board.isHoldUsed();
        List<TetrominoType> preview = new ArrayList<>();
        for (TetrominoType t : board.getPreviewPieces()) {
            if (t != null) preview.add(t);
            if (preview.size() >= settings.lookaheadDepth) break;
        }
        AiEvaluator.Context ctx = buildContext();
        AiSearch.Inputs inputs = new AiSearch.Inputs(
                model, current, holdType, canHold, preview,
                b2bActive, comboCount, ctx);
        long searchStartNs = System.nanoTime();
        AiSearch.Result r = search.search(inputs);
        recordSearchTiming(System.nanoTime() - searchStartNs, r);
        searchCandidates += Math.max(0, r.candidatesEvaluated);
        if (r.fromMistake) mistakes++;
        AiMove chosen = r.move;
        if (chosen == null) {
            targetCol = piece.getBoardPosition().getX();
            targetRotation = piece.getRotationState();
            targetUsedHold = false;
            holdAttempted = true; // skip hold-press path
            planScore = -1e6;
            return;
        }
        targetCol = chosen.targetCol;
        targetRotation = chosen.targetRotation;
        targetUsedHold = chosen.usedHold;
        holdAttempted = false;
        planScore = r.score;
        planVersion++;
        if (chosen.isTspin()) tspins++;
        if (chosen.perfectClear) perfectClears++;
    }

    private void recordTickTiming(long elapsedNs) {
        long safeNs = Math.max(0L, elapsedNs);
        tickTimingSamples++;
        lastTickNs = safeNs;
        totalTickNs += safeNs;
        if (safeNs > maxTickNs) maxTickNs = safeNs;
    }

    private void recordSearchTiming(long elapsedNs, AiSearch.Result result) {
        long safeNs = Math.max(0L, elapsedNs);
        searchTimingSamples++;
        lastSearchNs = safeNs;
        totalSearchNs += safeNs;
        if (safeNs > maxSearchNs) maxSearchNs = safeNs;
        if (result != null) {
            lastSearchDepthReached = result.depthReached;
            lastSearchCandidates = Math.max(0, result.candidatesEvaluated);
        }
    }

    /**
     * Build the evaluator context from the live MAB participant state.
     * Returns {@link AiEvaluator.Context#DEFAULT} if no MAB match has
     * been attached, or if any field is unreadable.
     */
    private AiEvaluator.Context buildContext() {
        if (contextMatch == null || contextParticipantId == null) {
            return AiEvaluator.Context.DEFAULT;
        }
        try {
            ParticipantState self = contextMatch.getParticipant(contextParticipantId);
            if (self == null) return AiEvaluator.Context.DEFAULT;
            NukeBuildState nb = self.getNukeBuildState();
            boolean armed = nb != null && nb.isArmed();
            NukeDesign design = nb == null ? null : nb.getCurrentDesign();
            int defcon = contextMatch.getDefconState() == null ? 5
                    : contextMatch.getDefconState().getLevel();
            int tetrisGoal = design == null ? 0 : design.effectiveLaunchTetrisGoal(defcon);
            int spinGoal = design == null ? 0 : design.effectiveLaunchSpinGoal(defcon);
            // Route preference: the simple driver can't execute a real
            // T-spin yet, so default to the tetris route while letting
            // the spin-slot bonus build a spin board organically.
            boolean tetrisRoute = armed && tetrisGoal > 0
                    && (design == null
                        || design.getDoctrineType() != NukeDoctrineType.MIRV);
            boolean spinRoute = armed && spinGoal > 0 && !tetrisRoute;
            int threatPieces = earliestIncomingWarning(self);
            int pipsRemaining = tetrisRoute ? tetrisGoal
                    : (spinRoute ? spinGoal : 0);
            return new AiEvaluator.Context(armed, tetrisRoute, spinRoute,
                    pipsRemaining, threatPieces);
        } catch (RuntimeException ignored) {
            return AiEvaluator.Context.DEFAULT;
        }
    }

    private static int earliestIncomingWarning(ParticipantState self) {
        int earliest = 0;
        for (IncomingThreatState t : self.getIncomingThreats()) {
            if (t.getStatus() == ThreatStatus.WARNING_ACTIVE
                    && t.getWarningPiecesRemaining() > 0) {
                if (earliest == 0 || t.getWarningPiecesRemaining() < earliest) {
                    earliest = t.getWarningPiecesRemaining();
                }
            }
        }
        return earliest;
    }
}
