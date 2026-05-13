package com.tetris.mab;

/** Placeholder second-strike / dead-hand state. */
public class SecondStrikeState {

    private boolean secondStrikeAvailable;
    private boolean deadHandEnabled;
    private int retaliationWindowPieces;

    public boolean isSecondStrikeAvailable() { return secondStrikeAvailable; }
    public void setSecondStrikeAvailable(boolean v) { this.secondStrikeAvailable = v; }
    public boolean isDeadHandEnabled() { return deadHandEnabled; }
    public void setDeadHandEnabled(boolean v) { this.deadHandEnabled = v; }
    public int getRetaliationWindowPieces() { return retaliationWindowPieces; }
    public void setRetaliationWindowPieces(int v) { this.retaliationWindowPieces = v; }
}
