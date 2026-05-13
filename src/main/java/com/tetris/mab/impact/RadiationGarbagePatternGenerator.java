package com.tetris.mab.impact;

import com.tetris.events.GarbageRowPattern;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic garbage-row pattern generator. The hole column for
 * each row is computed from {@code (rowIndex, radiationLevel, tagHash)}
 * with no random source, so the same inputs always produce the same
 * patterns — useful for tests, debugging, and replay.
 *
 * <p>Each generated row has exactly one hole. Higher radiation levels
 * shift the hole more often, producing rows that are harder to clear
 * with a single Tetris.
 */
public final class RadiationGarbagePatternGenerator {

    /** Default render color for nuke garbage rows. */
    private static final Color NUKE_GARBAGE_COLOR = Color.DARK_GRAY;

    public List<GarbageRowPattern> generate(int rows,
                                            int boardWidth,
                                            RadiationLevel radiationLevel,
                                            String tag) {
        if (rows <= 0) return List.of();
        if (boardWidth <= 0) {
            throw new IllegalArgumentException("boardWidth must be positive");
        }
        RadiationLevel rad = radiationLevel == null ? RadiationLevel.CLEAN : radiationLevel;
        String safeTag = tag == null ? "" : tag;
        int tagHash = safeTag.hashCode();
        if (boardWidth == 1) {
            // Only one column possible; every row has hole at column 0
            // (otherwise a fully-blocking row would top the player out
            // immediately, which is not the intent for radiation rows).
            List<GarbageRowPattern> single = new ArrayList<>(rows);
            for (int i = 0; i < rows; i++) {
                single.add(new GarbageRowPattern(List.of(0), NUKE_GARBAGE_COLOR,
                        "nuke:" + rad.name().toLowerCase()));
            }
            return single;
        }

        int messiness = rad.messinessScore();
        // For CLEAN we always reuse the same column. For higher tiers
        // we change column on a schedule that gets more aggressive.
        int baseColumn = Math.floorMod(tagHash, boardWidth);
        List<GarbageRowPattern> out = new ArrayList<>(rows);
        int previousHole = baseColumn;
        for (int i = 0; i < rows; i++) {
            int hole;
            if (messiness == 0) {
                hole = baseColumn;
            } else {
                // Determine whether to shift on this row based on a
                // pseudo-random but deterministic schedule.
                boolean shift = shouldShift(i, messiness, tagHash);
                if (!shift) {
                    hole = previousHole;
                } else {
                    int step = deterministicStep(i, messiness, tagHash, boardWidth);
                    hole = Math.floorMod(previousHole + step, boardWidth);
                    // SALTED occasionally jumps far rather than stepping
                    if (messiness >= 5 && (Math.floorMod(i + tagHash, 3) == 0)) {
                        hole = Math.floorMod(previousHole + boardWidth / 2 + 1, boardWidth);
                    }
                }
            }
            previousHole = hole;
            out.add(new GarbageRowPattern(List.of(hole), NUKE_GARBAGE_COLOR,
                    "nuke:" + rad.name().toLowerCase()));
        }
        return out;
    }

    private static boolean shouldShift(int rowIndex, int messiness, int tagHash) {
        // messiness 1: shift roughly every 4 rows
        // messiness 2: every 3 rows
        // messiness 3: every 2 rows
        // messiness 4-5: every row
        int period = switch (messiness) {
            case 1 -> 4;
            case 2 -> 3;
            case 3 -> 2;
            default -> 1;
        };
        if (period <= 1) return true;
        return Math.floorMod(rowIndex + tagHash, period) == 0;
    }

    private static int deterministicStep(int rowIndex, int messiness, int tagHash, int width) {
        // Use a simple mixing function so step varies predictably with
        // row index and tag. Step magnitude grows with messiness.
        int mixed = rowIndex * 31 + tagHash * 17 + messiness * 7;
        int magnitude = 1 + Math.floorMod(Math.abs(mixed), Math.max(1, messiness));
        // Alternate direction
        int direction = (Math.floorMod(mixed, 2) == 0) ? 1 : -1;
        int step = direction * magnitude;
        // Clamp to within board width to keep it sensible.
        if (step >= width) step = width - 1;
        if (step <= -width) step = -(width - 1);
        return step;
    }
}
