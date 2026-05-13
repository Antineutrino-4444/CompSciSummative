package com.tetris.mab;

/** Placeholder restraint window (e.g. de-escalation grace period). */
public class RestraintState {

    private boolean restraintActive;
    private int restraintPieces;

    public boolean isRestraintActive() { return restraintActive; }
    public void setRestraintActive(boolean v) { this.restraintActive = v; }
    public int getRestraintPieces() { return restraintPieces; }
    public void setRestraintPieces(int v) { this.restraintPieces = v; }
}
