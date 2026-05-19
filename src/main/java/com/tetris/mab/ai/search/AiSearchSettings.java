package com.tetris.mab.ai.search;

import com.tetris.mab.ai.MabAiDifficulty;

/**
 * Difficulty-derived knobs for the AI search.
 *
 * <p>Scales beam width, lookahead depth, soft-drop/spin search, mistake
 * rate and pacing. The {@link #pacing()} field is consumed by the board
 * driver to compute target pieces-per-second.
 */
public final class AiSearchSettings {

    public final int beamWidth;
    public final int lookaheadDepth;
    public final boolean fullTuckSearch;
    public final boolean include180;
    public final boolean useHold;
    public final double mistakeRate;
    public final Pacing pacing;
    public final boolean designAware;
    public final boolean planLaunches;
    public final boolean prefersTetrisWell;
    public final int searchTimeBudgetMillis;
    public final boolean rotateBeforeMoveOptimisation;

    public AiSearchSettings(int beamWidth, int lookaheadDepth,
                            boolean fullTuckSearch, boolean include180,
                            boolean useHold, double mistakeRate,
                            Pacing pacing, boolean designAware,
                            boolean planLaunches, boolean prefersTetrisWell,
                            int searchTimeBudgetMillis,
                            boolean rotateBeforeMoveOptimisation) {
        this.beamWidth = Math.max(1, beamWidth);
        this.lookaheadDepth = Math.max(0, lookaheadDepth);
        this.fullTuckSearch = fullTuckSearch;
        this.include180 = include180;
        this.useHold = useHold;
        this.mistakeRate = Math.max(0, Math.min(1, mistakeRate));
        this.pacing = pacing;
        this.designAware = designAware;
        this.planLaunches = planLaunches;
        this.prefersTetrisWell = prefersTetrisWell;
        this.searchTimeBudgetMillis = Math.max(2, searchTimeBudgetMillis);
        this.rotateBeforeMoveOptimisation = rotateBeforeMoveOptimisation;
    }

    public static AiSearchSettings forDifficulty(MabAiDifficulty d) {
        return switch (d) {
            case EASY -> new AiSearchSettings(
                    /*beam*/  2, /*depth*/ 0,
                    /*tuck*/  false, /*r180*/ false, /*hold*/ false,
                    /*mistakes*/ 0.55,
                    new Pacing(1.0, 10, 3),
                    /*designAware*/ false, /*planLaunches*/ false,
                    /*tetrisWell*/ false, /*timeMs*/ 10,
                    /*rotFirst*/ true);
            case MEDIUM, NORMAL -> new AiSearchSettings(
                    /*beam*/  4, /*depth*/ 1,
                    /*tuck*/  false, /*r180*/ false, /*hold*/ true,
                    /*mistakes*/ 0.15,
                    new Pacing(1.7, 6, 2),
                    /*designAware*/ true, /*planLaunches*/ true,
                    /*tetrisWell*/ false, /*timeMs*/ 20,
                    /*rotFirst*/ true);
            case HARD -> new AiSearchSettings(
                    /*beam*/  8, /*depth*/ 2,
                    /*tuck*/  false, /*r180*/ false, /*hold*/ true,
                    /*mistakes*/ 0.04,
                    new Pacing(2.6, 4, 2),
                    /*designAware*/ true, /*planLaunches*/ true,
                    /*tetrisWell*/ true, /*timeMs*/ 35,
                    /*rotFirst*/ true);
            case EXPERT -> new AiSearchSettings(
                    /*beam*/ 12, /*depth*/ 3,
                    /*tuck*/  false, /*r180*/ false, /*hold*/ true,
                    /*mistakes*/ 0.01,
                    new Pacing(3.5, 2, 0),
                    /*designAware*/ true, /*planLaunches*/ true,
                    /*tetrisWell*/ true, /*timeMs*/ 40,
                    /*rotFirst*/ true);
            case MASTER -> new AiSearchSettings(
                    /*beam*/ 18, /*depth*/ 4,
                    /*tuck*/  false, /*r180*/ false, /*hold*/ true,
                    /*mistakes*/ 0.0,
                    new Pacing(5.5, 2, 0),
                    /*designAware*/ true, /*planLaunches*/ true,
                    /*tetrisWell*/ true, /*timeMs*/ 45,
                    /*rotFirst*/ true);
            case DEBUG -> new AiSearchSettings(
                    /*beam*/ 14, /*depth*/ 3,
                    /*tuck*/  false, /*r180*/ false, /*hold*/ true,
                    /*mistakes*/ 0.0,
                    new Pacing(6.0, 1, 0),
                    /*designAware*/ true, /*planLaunches*/ true,
                    /*tetrisWell*/ true, /*timeMs*/ 30,
                    /*rotFirst*/ true);
        };
    }

    public Pacing pacing() { return pacing; }

    /** Target pieces-per-second + per-input intervals. */
    public static final class Pacing {
        public final double targetPps;
        public final int actionIntervalTicks;
        public final int dropDwellTicks;

        public Pacing(double targetPps, int actionIntervalTicks, int dropDwellTicks) {
            this.targetPps = targetPps;
            this.actionIntervalTicks = Math.max(1, actionIntervalTicks);
            this.dropDwellTicks = Math.max(0, dropDwellTicks);
        }
    }
}
