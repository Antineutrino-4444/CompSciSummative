package com.tetris.mab.pieces;

import com.tetris.model.BagRandomizer;
import com.tetris.model.TetrominoType;

import java.util.ArrayList;
import java.util.List;

/**
 * Step 21 — per-player cursor over a {@link MabSharedPieceSequence}.
 *
 * <p>Extends {@link BagRandomizer} so it can be dropped into the
 * existing {@link com.tetris.model.GameState} via the new MAB-specific
 * setter, without touching normal-Tetris piece-supply behavior.
 *
 * <p>Hold operations in {@code GameState} reuse the held piece slot
 * and do NOT call {@link #next()}; therefore hold cannot desync the
 * underlying shared sequence. Each {@link #next()} or {@link #peek}
 * advances/observes only this stream's local cursor; both players'
 * cursors index into the same {@link MabSharedPieceSequence}.
 */
public final class MabPieceStream extends BagRandomizer {

    private final MabSharedPieceSequence source;
    private int cursor = 0;

    MabPieceStream(MabSharedPieceSequence source) {
        super(); // existing BagRandomizer queue is created but never consulted.
        this.source = source;
    }

    public int cursorIndex() { return cursor; }

    public MabSharedPieceSequence getSource() { return source; }

    @Override
    public TetrominoType next() {
        return source.get(cursor++);
    }

    @Override
    public List<TetrominoType> peek(int count) {
        if (count <= 0) return List.of();
        List<TetrominoType> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(source.get(cursor + i));
        }
        return out;
    }
}
