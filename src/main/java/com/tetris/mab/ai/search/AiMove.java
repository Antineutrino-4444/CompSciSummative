package com.tetris.mab.ai.search;

import com.tetris.model.Tetromino;
import com.tetris.model.TetrominoType;

/**
 * A concrete final placement chosen by the search.
 *
 * <p>Carries the locked piece position, whether the move used hold, the
 * number of lines cleared, the spin tag (T-spin / T-spin-mini / none),
 * and the post-placement board so the driver can keep planning without
 * recomputing.
 */
public final class AiMove {

    public enum SpinTag {
        NONE,
        TSPIN,
        TSPIN_MINI,
        IMMOBILE_SPIN
    }

    public final TetrominoType type;
    public final int targetRotation;
    public final int targetCol;
    public final Tetromino landed;
    public final boolean usedHold;
    public final int linesCleared;
    public final SpinTag spinTag;
    public final boolean lastMoveWasRotation;
    public final AiBoardModel postBoard;
    public final boolean perfectClear;
    public final boolean isHardDropOnly;

    public AiMove(TetrominoType type, int targetRotation, int targetCol,
                  Tetromino landed, boolean usedHold, int linesCleared,
                  SpinTag spinTag, boolean lastMoveWasRotation,
                  AiBoardModel postBoard, boolean perfectClear,
                  boolean isHardDropOnly) {
        this.type = type;
        this.targetRotation = targetRotation;
        this.targetCol = targetCol;
        this.landed = landed;
        this.usedHold = usedHold;
        this.linesCleared = linesCleared;
        this.spinTag = spinTag;
        this.lastMoveWasRotation = lastMoveWasRotation;
        this.postBoard = postBoard;
        this.perfectClear = perfectClear;
        this.isHardDropOnly = isHardDropOnly;
    }

    public boolean isTetris() { return linesCleared == 4; }
    public boolean isTspin()  {
        return spinTag == SpinTag.TSPIN && linesCleared > 0;
    }
    public boolean isB2B()    { return isTetris() || isTspin(); }
    public boolean isLineClear() { return linesCleared > 0; }

    @Override public String toString() {
        return type + "@col=" + targetCol + ",rot=" + targetRotation
                + (usedHold ? ",hold" : "")
                + ",cleared=" + linesCleared
                + ",spin=" + spinTag;
    }
}
