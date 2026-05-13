package com.tetris.mab.defense;

import com.tetris.mab.ParticipantState;
import com.tetris.model.Board;
import com.tetris.model.GameState;

import java.awt.Color;

/**
 * Deterministic policy that decides how many of the impact's planned
 * immediate garbage rows can safely be inserted right now without
 * causing an instant unavoidable top-out, and how many should be
 * shifted into delayed waves.
 *
 * <p>The grace buffer is a fairness tool, not immunity: deferred rows
 * are still scheduled for delivery a few pieces later. Large attacks
 * remain dangerous.
 */
public class ImpactGracePolicy {

    public static final int DEFAULT_BASE_HEADROOM = 2;
    /** Fallback board height if no game state is available. */
    public static final int DEFAULT_BOARD_HEIGHT = Board.VISIBLE_HEIGHT;

    public ImpactGraceDecision decide(ParticipantState defender,
                                      int requestedImmediateRows,
                                      int alreadyDelayedRows,
                                      int extraGraceRows,
                                      String reason) {
        if (requestedImmediateRows <= 0) {
            return new ImpactGraceDecision(
                    Math.max(0, requestedImmediateRows), 0, 0, 0, 0,
                    DEFAULT_BOARD_HEIGHT,
                    DEFAULT_BASE_HEADROOM + Math.max(0, extraGraceRows),
                    reason == null ? "no-rows" : reason);
        }

        int boardHeight = DEFAULT_BOARD_HEIGHT;
        int stackHeight = 0;
        if (defender != null && defender.getGameState() != null) {
            GameState gs = defender.getGameState();
            // GameState.getBoardHeight() actually returns the current
            // stack height in this engine; the playfield's visible
            // height is Board.VISIBLE_HEIGHT.
            stackHeight = Math.max(0, gs.getBoardHeight());
            // Defensive cross-check from the snapshot if the helper
            // returned 0 — a zero stack with non-empty board would skew
            // the safe-row calculation.
            if (stackHeight == 0) {
                stackHeight = estimateStackHeightFromSnapshot(gs);
            }
        }

        int headroom = DEFAULT_BASE_HEADROOM + Math.max(0, extraGraceRows);
        int safeRows = Math.max(0, boardHeight - stackHeight - headroom);
        int allowed = Math.min(requestedImmediateRows, safeRows);
        int deferred = Math.max(0, requestedImmediateRows - allowed);

        return new ImpactGraceDecision(
                requestedImmediateRows, allowed, deferred,
                safeRows, stackHeight, boardHeight, headroom,
                reason == null ? "impact" : reason);
    }

    private static int estimateStackHeightFromSnapshot(GameState gs) {
        Color[][] snap;
        try { snap = gs.getBoardSnapshot(); }
        catch (RuntimeException e) { return 0; }
        if (snap == null || snap.length == 0) return 0;
        int total = snap.length;
        int width = snap[0] == null ? 0 : snap[0].length;
        // Snapshot is indexed [row][col], row 0 at top of buffer. Find
        // the topmost row that has any occupied cell.
        for (int r = 0; r < total; r++) {
            Color[] row = snap[r];
            if (row == null) continue;
            for (int c = 0; c < width; c++) {
                if (row[c] != null) {
                    int fromBottom = total - r;
                    return Math.min(fromBottom, Board.VISIBLE_HEIGHT);
                }
            }
        }
        return 0;
    }
}
