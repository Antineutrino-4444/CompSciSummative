package com.tetris.mab.action;

import com.tetris.events.GameEventListener;

/**
 * One concrete line-clear "token" that a player produced. Built from a
 * {@link com.tetris.events.GameEventListener.LinesClearedEvent} via
 * {@link #fromLinesClearedEvent(GameEventListener.LinesClearedEvent)}.
 *
 * <p>{@code lineCount} is the actual number of lines cleared (0..4).
 * Zero-line spins do exist, but in this step they do not advance
 * action codes.
 */
public record ActionClearToken(int lineCount,
                               SpinKind spinKind,
                               boolean perfectClear,
                               boolean backToBack) {

    public ActionClearToken {
        if (spinKind == null) spinKind = SpinKind.NONE;
    }

    /**
     * Translate an engine {@link GameEventListener.LinesClearedEvent}
     * into an {@link ActionClearToken}. T-spin / T-spin mini flags are
     * mapped to {@link SpinKind#T_SPIN} / {@link SpinKind#T_SPIN_MINI}.
     */
    public static ActionClearToken fromLinesClearedEvent(GameEventListener.LinesClearedEvent event) {
        if (event == null) {
            return new ActionClearToken(0, SpinKind.NONE, false, false);
        }
        SpinKind kind;
        if (event.tSpinMini()) kind = SpinKind.T_SPIN_MINI;
        else if (event.tSpin()) kind = SpinKind.T_SPIN;
        else kind = SpinKind.NONE;
        return new ActionClearToken(event.count(), kind,
                event.perfectClear(), event.backToBack());
    }

    /** True if this clear can stand in for a spin-replaceable required token. */
    public boolean isSpinWildcard() {
        return lineCount > 0 && spinKind.isEligibleActionWildcard();
    }

    /** True if at least one line was cleared. */
    public boolean isLineClear() {
        return lineCount > 0;
    }

    public String toDebugString() {
        StringBuilder sb = new StringBuilder();
        sb.append(lineCount);
        if (spinKind != SpinKind.NONE) sb.append('+').append(spinKind);
        if (perfectClear) sb.append("+PC");
        if (backToBack) sb.append("+B2B");
        return sb.toString();
    }
}
