package com.tetris.mab.ai.search;

import com.tetris.model.TetrominoType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Beam search over the AI's upcoming pieces with optional hold.
 *
 * <p>Given the current piece, a hold slot, and an ordered preview, the
 * search produces the best immediate {@link AiMove} for the current
 * tempo. Search width and depth are controlled by {@link AiSearchSettings}.
 *
 * <p>The implementation is intentionally side-effect-free (apart from
 * the supplied random number generator used for mistake injection).
 */
public final class AiSearch {

    private final AiSearchSettings settings;
    private final AiEvaluator evaluator;
    private final AiMoveGenerator moveGenerator;
    private final Random rng;

    public AiSearch(AiSearchSettings settings, AiEvaluator evaluator, long seed) {
        if (settings == null) throw new IllegalArgumentException("settings");
        if (evaluator == null) throw new IllegalArgumentException("evaluator");
        this.settings = settings;
        this.evaluator = evaluator;
        this.moveGenerator = new AiMoveGenerator(settings.fullTuckSearch, settings.include180);
        this.rng = new Random(seed);
    }

    /** Returned by the search, with both the chosen move and its score. */
    public static final class Result {
        public final AiMove move;
        public final double score;
        public final int candidatesEvaluated;
        public final int depthReached;
        public final boolean fromMistake;

        public Result(AiMove move, double score, int candidates,
                      int depth, boolean fromMistake) {
            this.move = move;
            this.score = score;
            this.candidatesEvaluated = candidates;
            this.depthReached = depth;
            this.fromMistake = fromMistake;
        }
    }

    /** Search inputs collected into a single record. */
    public static final class Inputs {
        public final AiBoardModel board;
        public final TetrominoType current;
        public final TetrominoType hold;
        public final boolean canHold;
        public final List<TetrominoType> preview;
        public final boolean prevB2b;
        public final int prevCombo;
        public final AiEvaluator.Context context;

        public Inputs(AiBoardModel board, TetrominoType current, TetrominoType hold,
                       boolean canHold, List<TetrominoType> preview,
                       boolean prevB2b, int prevCombo) {
            this(board, current, hold, canHold, preview, prevB2b, prevCombo,
                    AiEvaluator.Context.DEFAULT);
        }

        public Inputs(AiBoardModel board, TetrominoType current, TetrominoType hold,
                       boolean canHold, List<TetrominoType> preview,
                       boolean prevB2b, int prevCombo,
                       AiEvaluator.Context context) {
            this.board = board;
            this.current = current;
            this.hold = hold;
            this.canHold = canHold;
            this.preview = preview == null ? List.of() : preview;
            this.prevB2b = prevB2b;
            this.prevCombo = prevCombo;
            this.context = context == null ? AiEvaluator.Context.DEFAULT : context;
        }
    }

    public Result search(Inputs in) {
        if (in == null || in.current == null) {
            return new Result(null, -1e9, 0, 0, false);
        }
        long deadline = System.currentTimeMillis() + settings.searchTimeBudgetMillis;
        List<AiMove> rootMoves = moveGenerator.generate(
                in.board, in.current, in.hold, settings.useHold && in.canHold);
        if (rootMoves.isEmpty()) {
            return new Result(null, -1e9, 0, 0, false);
        }
        AiEvaluator.Context ctx = in.context;
        // Score & sort.
        rootMoves.sort(Comparator.<AiMove>comparingDouble(
                m -> -evaluator.score(m, in.prevB2b, in.prevCombo, ctx)));
        int beam = Math.min(settings.beamWidth, rootMoves.size());
        List<AiMove> candidates = new ArrayList<>(rootMoves.subList(0, beam));

        int candidatesEvaluated = candidates.size();
        int depthReached = 0;

        // Iterate over preview pieces for lookahead.
        List<TetrominoType> preview = in.preview;
        int maxDepth = Math.min(settings.lookaheadDepth, preview.size());
        // We need to remember which root each candidate descended from.
        List<Candidate> wave = new ArrayList<>();
        for (AiMove m : candidates) {
            wave.add(new Candidate(m, m,
                    evaluator.score(m, in.prevB2b, in.prevCombo, ctx),
                    in.hold, in.canHold,
                    advanceB2b(in.prevB2b, m), advanceCombo(in.prevCombo, m)));
        }

        for (int d = 0; d < maxDepth; d++) {
            if (System.currentTimeMillis() > deadline) break;
            depthReached = d + 1;
            TetrominoType next = preview.get(d);
            List<Candidate> nextWave = new ArrayList<>();
            for (Candidate c : wave) {
                if (System.currentTimeMillis() > deadline) {
                    nextWave.add(c);
                    continue;
                }
                List<AiMove> child = moveGenerator.generate(
                        c.lastMove.postBoard, next, c.hold,
                        settings.useHold && c.canHold);
                if (child.isEmpty()) {
                    nextWave.add(c);
                    continue;
                }
                child.sort(Comparator.<AiMove>comparingDouble(
                        m -> -evaluator.score(m, c.b2b, c.combo, ctx)));
                int branch = Math.min(3, child.size());
                for (int i = 0; i < branch; i++) {
                    AiMove m = child.get(i);
                    double sc = c.cumulativeScore + evaluator.score(m, c.b2b, c.combo, ctx);
                    candidatesEvaluated++;
                    nextWave.add(new Candidate(c.root, m, sc,
                            m.usedHold ? c.lastMove.type : c.hold,
                            c.canHold && !m.usedHold,
                            advanceB2b(c.b2b, m), advanceCombo(c.combo, m)));
                }
            }
            // Prune to beam width.
            nextWave.sort(Comparator.<Candidate>comparingDouble(c -> -c.cumulativeScore));
            if (nextWave.size() > settings.beamWidth) {
                nextWave = new ArrayList<>(nextWave.subList(0, settings.beamWidth));
            }
            wave = nextWave;
        }

        // Aggregate: best cumulative-score wave member per root.
        AiMove bestRoot = null;
        double bestScore = -Double.MAX_VALUE;
        for (Candidate c : wave) {
            if (c.cumulativeScore > bestScore) {
                bestScore = c.cumulativeScore;
                bestRoot = c.root;
            }
        }
        if (bestRoot == null) {
            bestRoot = candidates.get(0);
            bestScore = evaluator.score(bestRoot, in.prevB2b, in.prevCombo, ctx);
        }

        // Mistake injection: with probability mistakeRate, replace the
        // chosen move with a near-but-not-best alternative.
        boolean mistake = false;
        if (settings.mistakeRate > 0 && rng.nextDouble() < settings.mistakeRate
                && rootMoves.size() > 1) {
            int range = Math.min(rootMoves.size(), Math.max(2, beam + 1));
            int pick = 1 + rng.nextInt(range - 1);
            bestRoot = rootMoves.get(pick);
            bestScore = evaluator.score(bestRoot, in.prevB2b, in.prevCombo, ctx);
            mistake = true;
        }

        return new Result(bestRoot, bestScore, candidatesEvaluated,
                depthReached, mistake);
    }

    private static boolean advanceB2b(boolean prev, AiMove m) {
        if (m.linesCleared == 0) return prev;
        return m.isB2B();
    }
    private static int advanceCombo(int prev, AiMove m) {
        if (m.linesCleared == 0) return 0;
        return prev + 1;
    }

    private static final class Candidate {
        final AiMove root;
        final AiMove lastMove;
        final double cumulativeScore;
        final TetrominoType hold;
        final boolean canHold;
        final boolean b2b;
        final int combo;
        Candidate(AiMove root, AiMove lastMove, double cum,
                  TetrominoType hold, boolean canHold,
                  boolean b2b, int combo) {
            this.root = root;
            this.lastMove = lastMove;
            this.cumulativeScore = cum;
            this.hold = hold;
            this.canHold = canHold;
            this.b2b = b2b;
            this.combo = combo;
        }
    }
}
