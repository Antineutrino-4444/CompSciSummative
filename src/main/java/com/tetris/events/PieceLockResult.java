package com.tetris.events;

import com.tetris.model.TetrominoType;

/**
 * Step 22 \u2014 detailed lock result emitted once per locked piece, even
 * if zero lines were cleared. Used by Mutually Assured Blocks for spin
 * routing (charge / launch progress / intercept) and by future systems
 * that need a unified "what happened on this lock" signal.
 *
 * <p>Single-player Tetris does not depend on this event; legacy
 * {@code onLinesCleared} / {@code onPieceLocked} are still emitted.
 *
 * <p><b>Offline-only.</b>
 */
public final class PieceLockResult {

    private final TetrominoType pieceType;
    private final int linesCleared;
    private final boolean spin;
    private final boolean tSpinFull;
    private final boolean tSpinMini;
    private final int spinLines;
    private final String spinName;
    private final boolean perfectClear;
    private final boolean backToBack;
    private final int comboCount;
    private final boolean hardDropped;
    private final int piecesLocked;

    public PieceLockResult(TetrominoType pieceType,
                           int linesCleared,
                           boolean spin,
                           boolean tSpinFull,
                           boolean tSpinMini,
                           String spinName,
                           boolean perfectClear,
                           boolean backToBack,
                           int comboCount,
                           boolean hardDropped,
                           int piecesLocked) {
        this.pieceType = pieceType;
        this.linesCleared = Math.max(0, linesCleared);
        this.spin = spin;
        this.tSpinFull = tSpinFull;
        this.tSpinMini = tSpinMini;
        this.spinLines = spin ? this.linesCleared : 0;
        this.spinName = spinName == null ? "" : spinName;
        this.perfectClear = perfectClear;
        this.backToBack = backToBack;
        this.comboCount = Math.max(0, comboCount);
        this.hardDropped = hardDropped;
        this.piecesLocked = Math.max(0, piecesLocked);
    }

    public TetrominoType pieceType() { return pieceType; }
    public int linesCleared()        { return linesCleared; }
    public boolean spin()            { return spin; }
    /** True only for T-piece 3-corner spin (full). */
    public boolean tSpinFull()       { return tSpinFull; }
    /** True only for T-piece 3-corner mini. */
    public boolean tSpinMini()       { return tSpinMini; }
    public int spinLines()           { return spinLines; }
    public String spinName()         { return spinName; }
    public boolean perfectClear()    { return perfectClear; }
    public boolean backToBack()      { return backToBack; }
    public int comboCount()          { return comboCount; }
    public boolean hardDropped()     { return hardDropped; }
    public int piecesLocked()        { return piecesLocked; }
    public boolean tetris()          { return linesCleared == 4 && !spin; }

    @Override
    public String toString() {
        return "PieceLockResult{" + pieceType + " lines=" + linesCleared
                + (spin ? " " + spinName : "")
                + (perfectClear ? " PC" : "")
                + (backToBack ? " B2B" : "")
                + (comboCount > 1 ? " x" + comboCount : "")
                + "}";
    }
}
