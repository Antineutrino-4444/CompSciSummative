package com.tetris.model;

import java.util.Collections;
import java.util.Set;

/**
 * Represents a single hadron formation event — the hadron that was created
 * plus the board cells (col, row) that were consumed to create it.
 *
 * <p>Used to drive the merge animation in the renderer: the consumed cells
 * flash/glow briefly before being cleared from the board.</p>
 */
public final class HadronFormation {

    private final Hadron hadron;
    private final Set<Long> consumedCells;

    public HadronFormation(Hadron hadron, Set<Long> consumedCells) {
        this.hadron = hadron;
        this.consumedCells = Collections.unmodifiableSet(consumedCells);
    }

    /** The hadron that was formed. */
    public Hadron getHadron() { return hadron; }

    /** Packed (col, row) positions of consumed cells. Use unpack helpers. */
    public Set<Long> getConsumedCells() { return consumedCells; }

    /** Extracts the column from a packed cell coordinate. */
    public static int unpackCol(long packed) {
        return (int) (packed >> 32);
    }

    /** Extracts the row from a packed cell coordinate. */
    public static int unpackRow(long packed) {
        return (int) packed;
    }
}
