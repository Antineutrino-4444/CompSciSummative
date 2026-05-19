package com.tetris.mab.ai.search;

/**
 * Heuristic evaluator for an {@link AiBoardModel}. Returns a scalar
 * "goodness" score (higher = better) using a weighted sum of standard
 * Tetris features plus MAB-aware bonuses (Tetris well preservation,
 * spin slot retention, downstack pressure).
 *
 * <p>Inspired by the well-known El-Tetris/Dellacherie features but
 * tuned for guideline play with B2B/Tetris/T-spin emphasis.
 */
public final class AiEvaluator {

    /** Tunable feature weights — picked to play strong guideline Tetris
     *  with explicit Tetris-route preference (charge per piece on a
     *  Tetris is dramatically higher than singles/doubles in the live
     *  MAB charge formula). */
    public static final class Weights {
        public double aggregateHeight = -0.28;
        public double bumpiness       = -0.20;
        public double holes           = -1.45;
        public double rowTransitions  = -0.13;
        public double colTransitions  = -0.18;
        public double maxHeight       = -0.22;
        public double wellSum         = -0.10;
        public double deepWellBonus   = +0.85;   // reward keeping a Tetris well open
        public double wellPollution   = -2.40;   // penalty for filling the well with non-I
        public double linesScore      = +0.10;   // base lines (singles included)
        public double tetrisBonus     = +9.00;   // 4-line clear bonus
        public double tspinBonus      = +9.00;   // T-spin lines bonus
        public double singleClearPen  = -4.50;   // strongly discourage singles
        public double doubleClearPen  = -2.20;   // discourage doubles too
        public double tripleClearPen  = -0.60;
        public double b2bBonus        = +1.20;
        public double comboBonus      = +0.35;
        public double perfectClear    = +12.0;
        public double topOutRisk      = -12.0;
        public double overhangPenalty = -0.20;
        public double spinSlotBonus   = +0.85;   // reward 3-corner T overhang
        public double wellColumnPref  = +0.20;
        public double useHoldPenalty  = -0.05;
        /** Multiplier on lines/clear bonuses when armed and on Tetris route. */
        public double armedTetrisDriveBonus = +12.0;
        /** Multiplier on lines/clear bonuses when armed and on spin route. */
        public double armedSpinDriveBonus   = +14.0;
        /** Per pip of incoming threat (closer = stronger). */
        public double threatSpinUrgency     = +6.0;
    }

    private final Weights weights;
    /** Column to reserve as Tetris well (default: rightmost, 9). */
    private int wellColumn = AiBoardModel.WIDTH - 1;

    public AiEvaluator() { this(new Weights()); }
    public AiEvaluator(Weights w) { this.weights = w; }
    public Weights getWeights() { return weights; }
    public void setWellColumn(int c) {
        this.wellColumn = Math.max(0, Math.min(AiBoardModel.WIDTH - 1, c));
    }
    public int getWellColumn() { return wellColumn; }

    /** Strategic posture supplied to the evaluator. */
    public static final class Context {
        public static final Context DEFAULT = new Context(false, false, false, 0, 0);
        public final boolean armed;
        public final boolean tetrisRoute;
        public final boolean spinRoute;
        public final int routePipsRemaining;
        public final int threatPiecesRemaining;
        public Context(boolean armed, boolean tetrisRoute, boolean spinRoute,
                        int routePipsRemaining, int threatPiecesRemaining) {
            this.armed = armed;
            this.tetrisRoute = tetrisRoute;
            this.spinRoute = spinRoute;
            this.routePipsRemaining = Math.max(0, routePipsRemaining);
            this.threatPiecesRemaining = Math.max(0, threatPiecesRemaining);
        }
    }

    /**
     * Score a move and the resulting board.
     *
     * @param move      the placement under evaluation
     * @param prevB2b   true if pre-placement B2B chain was active
     * @param prevCombo combo count entering this placement
     */
    public double score(AiMove move, boolean prevB2b, int prevCombo) {
        return score(move, prevB2b, prevCombo, Context.DEFAULT);
    }

    /** Score with strategic context (armed / route / threats). */
    public double score(AiMove move, boolean prevB2b, int prevCombo, Context ctx) {
        AiBoardModel after = move.postBoard;
        int[] h = after.columnHeights();
        double aggregate = 0;
        double bumpiness = 0;
        double maxHeight = 0;
        for (int x = 0; x < AiBoardModel.WIDTH; x++) {
            aggregate += h[x];
            if (h[x] > maxHeight) maxHeight = h[x];
        }
        // Bumpiness over all neighbours *except* the well column — the
        // well is supposed to be deep on purpose.
        for (int x = 0; x < AiBoardModel.WIDTH - 1; x++) {
            if (x == wellColumn || x + 1 == wellColumn) continue;
            bumpiness += Math.abs(h[x] - h[x + 1]);
        }
        int holes = 0;
        int colTransitions = 0;
        int rowTransitions = 0;
        int wellSum = 0;
        int overhang = 0;
        int totalHeight = AiBoardModel.TOTAL_HEIGHT;
        for (int x = 0; x < AiBoardModel.WIDTH; x++) {
            int top = totalHeight - h[x];
            boolean prevFilled = false;
            for (int y = top + 1; y < totalHeight; y++) {
                if (after.cell(x, y) == 0) {
                    holes++;
                    if (prevFilled) overhang++;
                }
                prevFilled = after.cell(x, y) != 0;
            }
            // Column transitions across full column
            boolean prev = false;
            for (int y = 0; y < totalHeight; y++) {
                boolean filled = after.cell(x, y) != 0;
                if (filled != prev) colTransitions++;
                prev = filled;
            }
            // Well sum (counts adjacent-empty cells above an empty col)
            if (x != wellColumn) {
                int leftH  = (x == 0) ? totalHeight : h[x - 1];
                int rightH = (x == AiBoardModel.WIDTH - 1) ? totalHeight : h[x + 1];
                int minNeighbour = Math.min(leftH, rightH);
                int depth = Math.max(0, minNeighbour - h[x]);
                wellSum += depth * (depth + 1) / 2;
            }
        }
        for (int y = 0; y < totalHeight; y++) {
            boolean prev = true; // walls count as filled
            for (int x = 0; x < AiBoardModel.WIDTH; x++) {
                boolean filled = after.cell(x, y) != 0;
                if (filled != prev) rowTransitions++;
                prev = filled;
            }
            if (!prev) rowTransitions++; // right wall transition
        }
        // Deep well bonus: a single column of ≥3 empty cells flanked by
        // taller neighbours acts as the Tetris setup.
        int wellH = h[wellColumn];
        int leftN  = (wellColumn == 0) ? totalHeight : h[wellColumn - 1];
        int rightN = (wellColumn == AiBoardModel.WIDTH - 1) ? totalHeight
                                                            : h[wellColumn + 1];
        int wellDepth = Math.max(0, Math.min(leftN, rightN) - wellH);
        double deepWell = (wellDepth >= 3) ? Math.min(8, wellDepth) : -wellDepth;

        boolean topRisk = maxHeight >= AiBoardModel.VISIBLE_HEIGHT - 2;
        boolean criticalRisk = maxHeight >= AiBoardModel.VISIBLE_HEIGHT + 1;

        // Spin slot detection: a T-overhang setup (3 corners + open mouth).
        int spinSlotCount = countTSpinSlots(after);

        // Well pollution: count well-column cells that are now filled but
        // weren't reachable as an I-clear. We treat every cell ABOVE the
        // current well floor as polluted if the move added a cell to the
        // well column.
        int wellFilledByMove = 0;
        for (com.tetris.model.Position p : move.landed.getAbsoluteCells()) {
            if (p.getX() == wellColumn) wellFilledByMove++;
        }
        boolean wellPolluted = wellFilledByMove > 0
                && move.type != com.tetris.model.TetrominoType.I;

        double score = 0;
        score += weights.aggregateHeight * aggregate;
        score += weights.bumpiness       * bumpiness;
        score += weights.holes           * holes;
        score += weights.rowTransitions  * rowTransitions;
        score += weights.colTransitions  * colTransitions;
        score += weights.maxHeight       * maxHeight;
        score += weights.wellSum         * wellSum;
        score += weights.deepWellBonus   * deepWell;
        if (wellPolluted) score += weights.wellPollution * wellFilledByMove;
        score += weights.linesScore      * move.linesCleared;
        if (move.isTetris())       score += weights.tetrisBonus;
        if (move.isTspin())        score += weights.tspinBonus;
        if (move.linesCleared == 1 && !move.isTspin()) score += weights.singleClearPen;
        if (move.linesCleared == 2 && !move.isTspin()) score += weights.doubleClearPen;
        if (move.linesCleared == 3 && !move.isTspin()) score += weights.tripleClearPen;
        boolean b2bAfter = prevB2b && (move.linesCleared == 0 || move.isB2B());
        if (b2bAfter && move.linesCleared > 0 && move.isB2B()) score += weights.b2bBonus;
        if (move.isLineClear()) score += weights.comboBonus * (prevCombo + 1);
        if (move.perfectClear) score += weights.perfectClear;
        if (topRisk) score += weights.topOutRisk * 0.4;
        if (criticalRisk) score += weights.topOutRisk;
        score += weights.overhangPenalty * overhang;
        score += weights.spinSlotBonus * spinSlotCount;
        if (move.usedHold) score += weights.useHoldPenalty;

        // ── Strategic context bonuses ───────────────────────────────────
        if (ctx != null && ctx.armed) {
            if (ctx.tetrisRoute && move.isTetris()) {
                score += weights.armedTetrisDriveBonus;
            }
            if (ctx.spinRoute && move.isTspin()) {
                score += weights.armedSpinDriveBonus;
            }
            // While armed, singles/doubles are even less useful — they
            // burn the route stack without paying a route pip.
            if (move.linesCleared > 0 && !move.isB2B()) {
                score += weights.singleClearPen;
            }
        }
        if (ctx != null && ctx.threatPiecesRemaining > 0
                && ctx.threatPiecesRemaining <= 6) {
            // Closer threats raise the spin-urgency bonus: a spin clear
            // inside the window actually performs the intercept via the
            // live MAB clear pathway.
            double urgency = (7 - ctx.threatPiecesRemaining) / 6.0;
            if (move.isTspin())    score += weights.threatSpinUrgency * urgency;
            else if (move.linesCleared > 0)
                score += weights.singleClearPen * urgency;
            // A spin SLOT setup is also valuable: it lets the next T
            // execute the intercept.
            score += weights.spinSlotBonus * spinSlotCount * urgency;
        }

        return score;
    }

    /** Count T-spin double-style slots: 3-corner cavity with an open mouth. */
    private int countTSpinSlots(AiBoardModel after) {
        int[] h = after.columnHeights();
        int count = 0;
        for (int x = 1; x < AiBoardModel.WIDTH - 1; x++) {
            int leftH = h[x - 1];
            int rightH = h[x + 1];
            int slotH = h[x];
            int leftDeficit = leftH - slotH;
            int rightDeficit = rightH - slotH;
            if (leftDeficit >= 2 && rightDeficit >= 2) {
                int slotTop = AiBoardModel.TOTAL_HEIGHT - slotH;
                if (slotTop - 1 >= 0) {
                    int corners = 0;
                    if (after.isOccupiedOrWall(x - 1, slotTop - 1)) corners++;
                    if (after.isOccupiedOrWall(x + 1, slotTop - 1)) corners++;
                    if (after.isOccupiedOrWall(x - 1, slotTop + 1)) corners++;
                    if (after.isOccupiedOrWall(x + 1, slotTop + 1)) corners++;
                    if (corners >= 3) count++;
                }
            }
        }
        return count;
    }
}
