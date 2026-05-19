package com.tetris.events;

import java.awt.Color;
import java.util.Collections;
import java.util.List;

/**
 * GarbageRowPattern.java
 * ======================
 * Immutable description of a single garbage row to insert at the bottom
 * of the playfield.
 *
 * <p>Used by the new patterned garbage API
 * ({@link com.tetris.model.GameState#insertGarbagePattern(List, String)}
 * and {@link com.tetris.model.Board#insertGarbageRows(List)}) to support
 * future "messy" garbage where each row may have a different hole
 * pattern — for example radiation-damaged rows with multiple holes,
 * split-pattern rows, or nuke-themed coloured garbage.
 *
 * <p>Convenience factories ({@link #clean(int)} and
 * {@link #cleanRepeated(int, int)}) build the simple "single hole per
 * row" pattern that the existing {@code insertGarbage(rows, holeColumn)}
 * overloads use.
 *
 * @param holeColumns columns that should be left empty in this row
 *                    (zero or more — an empty list means a fully solid row,
 *                    multiple values mean multiple holes). Stored as an
 *                    unmodifiable copy.
 * @param color       optional render color for the filled cells of this
 *                    row; {@code null} means "use engine default".
 * @param tag         optional free-form tag, e.g. "clean", "dirty",
 *                    "radiation", "split", "nuke". {@code null} or empty
 *                    means untagged.
 */
public record GarbageRowPattern(List<Integer> holeColumns, Color color, String tag) {

    /** Canonical constructor — defensively copies the hole list. */
    public GarbageRowPattern {
        if (holeColumns == null) {
            holeColumns = Collections.emptyList();
        } else {
            holeColumns = List.copyOf(holeColumns);
        }
    }

    /** Convenience factory: a clean row with a single hole at {@code holeColumn}. */
    public static GarbageRowPattern clean(int holeColumn) {
        return new GarbageRowPattern(List.of(holeColumn), null, "clean");
    }

    /** Builds {@code rows} identical clean rows, all with the same hole column. */
    public static java.util.List<GarbageRowPattern> cleanRepeated(int rows, int holeColumn) {
        if (rows <= 0) return java.util.Collections.emptyList();
        java.util.List<GarbageRowPattern> out = new java.util.ArrayList<>(rows);
        GarbageRowPattern shared = clean(holeColumn);
        for (int i = 0; i < rows; i++) out.add(shared);
        return out;
    }
}
