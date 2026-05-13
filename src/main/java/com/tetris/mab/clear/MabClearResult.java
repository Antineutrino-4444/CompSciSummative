package com.tetris.mab.clear;

import com.tetris.mab.ParticipantId;
import com.tetris.model.TetrominoType;

/**
 * Step 21 \u2014 simplified MAB-relevant clear event.
 *
 * <p>Step 22: extended with {@link SpinKind#IMMOBILE} so non-T pieces
 * (J/L/S/Z/I) detected via the all-spin immobile rule are first-class
 * spins, including 0-line spins. Carries the locked piece type so the
 * presenter can render labels like {@code "J Spin"} or {@code "I Spin Triple"}.
 */
public final class MabClearResult {

    public enum SpinKind {
        NONE,
        T_SPIN,
        T_SPIN_MINI,
        /** Step 22 \u2014 generic immobile spin (J / L / S / Z / I). */
        IMMOBILE
    }

    private final ParticipantId participantId;
    private final int linesCleared;
    private final SpinKind spinKind;
    private final boolean perfectClear;
    private final boolean backToBack;
    private final int comboCount;
    private final boolean tetris;
    private final int chargeGained;
    private final String displayText;
    private final TetrominoType pieceType;

    public MabClearResult(ParticipantId participantId,
                          int linesCleared,
                          SpinKind spinKind,
                          boolean perfectClear,
                          boolean backToBack,
                          int comboCount,
                          boolean tetris,
                          int chargeGained,
                          String displayText) {
        this(participantId, linesCleared, spinKind, perfectClear, backToBack,
                comboCount, tetris, chargeGained, displayText, null);
    }

    public MabClearResult(ParticipantId participantId,
                          int linesCleared,
                          SpinKind spinKind,
                          boolean perfectClear,
                          boolean backToBack,
                          int comboCount,
                          boolean tetris,
                          int chargeGained,
                          String displayText,
                          TetrominoType pieceType) {
        this.participantId = participantId;
        this.linesCleared = linesCleared;
        this.spinKind = spinKind == null ? SpinKind.NONE : spinKind;
        this.perfectClear = perfectClear;
        this.backToBack = backToBack;
        this.comboCount = Math.max(0, comboCount);
        this.tetris = tetris;
        this.chargeGained = Math.max(0, chargeGained);
        this.displayText = displayText == null ? "" : displayText;
        this.pieceType = pieceType;
    }

    public ParticipantId participantId()    { return participantId; }
    public int linesCleared()               { return linesCleared; }
    public SpinKind spinKind()              { return spinKind; }
    /** Any spin (T full / T mini / generic immobile) counts for routing. */
    public boolean isSpin()                 { return spinKind != SpinKind.NONE; }
    public int spinLines()                  { return isSpin() ? linesCleared : 0; }
    public boolean perfectClear()           { return perfectClear; }
    public boolean backToBack()             { return backToBack; }
    public int comboCount()                 { return comboCount; }
    public boolean tetris()                 { return tetris; }
    public int chargeGained()               { return chargeGained; }
    public String displayText()             { return displayText; }
    public TetrominoType pieceType()        { return pieceType; }

    @Override
    public String toString() {
        return "MabClearResult{" + participantId + " " + displayText
                + " +" + chargeGained + "}";
    }
}
