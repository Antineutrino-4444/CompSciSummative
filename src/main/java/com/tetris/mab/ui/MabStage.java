package com.tetris.mab.ui;

import java.awt.Color;

/**
 * Step 22 \u2014 dramatic, top-level visual stages of a MAB participant.
 *
 * <p>The presenter ({@link MabStagePresenter}) folds the live match
 * state into exactly one of these values; the UI strip
 * ({@link MabStageStripPanel}) renders the chosen stage in a large,
 * unmissable bar across the top of the gameplay panel.
 *
 * <p>Priority order (highest first), per spec Part 14:
 * <ol>
 *   <li>{@link #MATCH_OVER}</li>
 *   <li>{@link #INCOMING_THREAT}</li>
 *   <li>{@link #IMPACT_READY}</li>
 *   <li>{@link #LAUNCH_FIRED}</li>
 *   <li>{@link #SPIN_INTERCEPT}</li>
 *   <li>{@link #NUKE_READY}</li>
 *   <li>{@link #LAUNCH_TETRIS_ROUTE} / {@link #LAUNCH_SPIN_ROUTE}</li>
 *   <li>{@link #BUILD_CHARGE}</li>
 * </ol>
 */
public enum MabStage {

    BUILD_CHARGE         ("BUILD CHARGE",       MabUiTheme.INFO),
    NUKE_READY           ("NUKE READY",         MabUiTheme.SUCCESS),
    LAUNCH_TETRIS_ROUTE  ("LAUNCH \u2022 TETRIS",  MabUiTheme.SUCCESS),
    LAUNCH_SPIN_ROUTE    ("LAUNCH \u2022 SPIN",    MabUiTheme.SUCCESS),
    INCOMING_THREAT      ("INCOMING THREAT",    MabUiTheme.CRITICAL),
    SPIN_INTERCEPT       ("SPIN INTERCEPT!",    MabUiTheme.WARNING),
    LAUNCH_FIRED         ("LAUNCH FIRED",       MabUiTheme.WARNING),
    IMPACT_READY         ("IMPACT INCOMING",    MabUiTheme.CRITICAL),
    MATCH_OVER           ("MATCH OVER",         MabUiTheme.INFO);

    private final String headline;
    private final Color  color;

    MabStage(String headline, Color color) {
        this.headline = headline;
        this.color = color;
    }

    public String headline() { return headline; }
    public Color  color()    { return color;    }
}
