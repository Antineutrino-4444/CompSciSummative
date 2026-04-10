package com.tetris.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Scans the board for valid hadron patterns after each piece locks.
 *
 * <h3>Gluon-Bridge Detection</h3>
 * <p>The key mechanic: quarks don't combine just by being adjacent. They must be
 * connected through a network of <b>gluon cells</b>. A "gluon-linked group" is a
 * connected component of cells where quarks are linked through gluon bridges.</p>
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>After a piece locks, find all gluon cells near the placed piece</li>
 *   <li>For each gluon, BFS outward through adjacent gluons and quarks to find
 *       the connected "gluon-linked group" (quarks must touch a gluon, not just
 *       each other)</li>
 *   <li>Count top quarks, bottom quarks, and gluons in each group</li>
 *   <li>Check if any hadron recipe matches</li>
 *   <li>Consume the minimum cells needed for the hadron</li>
 * </ol>
 *
 * <h3>What counts as "gluon-linked"</h3>
 * <p>A quark is part of a gluon network if it is orthogonally adjacent to at least
 * one gluon cell. Two quarks are gluon-linked if there exists a path of cells
 * between them that passes through at least one gluon. Quark-to-quark adjacency
 * WITHOUT an intervening gluon does NOT count.</p>
 */
public class HadronDetector {

    private static final int[][] NEIGHBORS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

    /**
     * Scans the board for hadron formations near the placed piece.
     *
     * @param board    the game board
     * @param piece    the piece that was just placed
     * @param rotation the rotation of the placed piece
     * @param col      the column of the placed piece
     * @param row      the row of the placed piece
     * @return list of hadrons detected (may be empty)
     */
    public List<Hadron> detect(Board board, Piece piece, int rotation, int col, int row) {
        List<Hadron> found = new ArrayList<>();

        // Collect cells belonging to the just-placed piece and their neighbors
        Set<Long> seedCells = new HashSet<>();
        int[][] cells = piece.getCells(rotation);
        for (int[] cell : cells) {
            int cx = col + cell[0];
            int cy = row - cell[1];
            seedCells.add(pack(cx, cy));
            // Also add neighbors
            for (int[] n : NEIGHBORS) {
                int nx = cx + n[0];
                int ny = cy + n[1];
                if (inBounds(nx, ny) && board.getCell(nx, ny) != null) {
                    seedCells.add(pack(nx, ny));
                }
            }
        }

        // Find gluon-linked groups starting from gluon cells in the seed area
        Set<Long> visited = new HashSet<>();
        Set<Long> allConsumed = new HashSet<>();

        for (long seed : seedCells) {
            if (visited.contains(seed) || allConsumed.contains(seed)) continue;
            int sx = unpackCol(seed);
            int sy = unpackRow(seed);
            Piece sp = board.getCell(sx, sy);
            if (sp == null) continue;

            // Start BFS from gluon cells to find gluon-linked groups
            if (sp.isGluon()) {
                GluonGroup group = buildGluonGroup(board, sx, sy, visited, allConsumed);
                List<Hadron> groupHadrons = matchRecipes(group, allConsumed);
                found.addAll(groupHadrons);
            }
        }

        // Clear consumed cells
        for (long packed : allConsumed) {
            board.setCell(unpackCol(packed), unpackRow(packed), null);
        }

        // Apply gravity if cells were consumed
        if (!allConsumed.isEmpty()) {
            applyGravity(board);
        }

        return found;
    }

    /**
     * BFS from a gluon cell to find the complete gluon-linked group.
     * A gluon-linked group consists of:
     * - All gluon cells connected to the starting gluon (through other gluons)
     * - All quark cells that are directly adjacent to at least one gluon in the group
     *
     * Quarks that only touch other quarks (not gluons) are NOT included.
     */
    private GluonGroup buildGluonGroup(Board board, int startCol, int startRow,
                                        Set<Long> globalVisited, Set<Long> consumed) {
        GluonGroup group = new GluonGroup();
        Queue<Long> gluonQueue = new LinkedList<>();
        Set<Long> localVisited = new HashSet<>();

        long start = pack(startCol, startRow);
        gluonQueue.add(start);
        localVisited.add(start);

        // Phase 1: BFS through gluon cells
        while (!gluonQueue.isEmpty()) {
            long current = gluonQueue.poll();
            int cx = unpackCol(current);
            int cy = unpackRow(current);
            Piece p = board.getCell(cx, cy);

            if (p == null || consumed.contains(current)) continue;

            if (p.isGluon()) {
                group.gluons.add(current);
                group.allCells.add(current);
                globalVisited.add(current);

                // Explore neighbors
                for (int[] n : NEIGHBORS) {
                    int nx = cx + n[0];
                    int ny = cy + n[1];
                    long np = pack(nx, ny);
                    if (inBounds(nx, ny) && !localVisited.contains(np) && !consumed.contains(np)) {
                        Piece neighbor = board.getCell(nx, ny);
                        if (neighbor != null) {
                            localVisited.add(np);
                            if (neighbor.isGluon()) {
                                gluonQueue.add(np);
                            }
                            // Quarks adjacent to gluons will be collected in phase 2
                        }
                    }
                }
            }
        }

        // Phase 2: Collect quarks that are adjacent to ANY gluon in the group
        for (long gluonPacked : group.gluons) {
            int gx = unpackCol(gluonPacked);
            int gy = unpackRow(gluonPacked);
            for (int[] n : NEIGHBORS) {
                int nx = gx + n[0];
                int ny = gy + n[1];
                long np = pack(nx, ny);
                if (inBounds(nx, ny) && !consumed.contains(np) && !group.allCells.contains(np)) {
                    Piece neighbor = board.getCell(nx, ny);
                    if (neighbor != null && neighbor.isQuark()) {
                        group.allCells.add(np);
                        if (neighbor.isTopQuark()) {
                            group.topQuarks.add(np);
                        } else {
                            group.bottomQuarks.add(np);
                        }
                    }
                }
            }
        }

        return group;
    }

    /**
     * Checks if a gluon-linked group matches any hadron recipe.
     * Tries larger recipes first (Proton/Neutron need 3 quarks),
     * then smaller ones (Pion needs 2 quarks).
     *
     * Adds only the cells actually used to the consumed set.
     */
    private List<Hadron> matchRecipes(GluonGroup group, Set<Long> consumed) {
        List<Hadron> results = new ArrayList<>();

        // Make consumable iterators from the sets
        List<Long> availableTop = new ArrayList<>(group.topQuarks);
        List<Long> availableBottom = new ArrayList<>(group.bottomQuarks);
        List<Long> availableGluons = new ArrayList<>(group.gluons);

        // Proton: 2 top + 1 bottom + 2 gluons
        while (availableTop.size() >= 2 && availableBottom.size() >= 1
                && availableGluons.size() >= 2) {
            results.add(Hadron.PROTON);
            consumed.add(availableTop.remove(availableTop.size() - 1));
            consumed.add(availableTop.remove(availableTop.size() - 1));
            consumed.add(availableBottom.remove(availableBottom.size() - 1));
            consumed.add(availableGluons.remove(availableGluons.size() - 1));
            consumed.add(availableGluons.remove(availableGluons.size() - 1));
        }

        // Neutron: 1 top + 2 bottom + 2 gluons
        while (availableTop.size() >= 1 && availableBottom.size() >= 2
                && availableGluons.size() >= 2) {
            results.add(Hadron.NEUTRON);
            consumed.add(availableTop.remove(availableTop.size() - 1));
            consumed.add(availableBottom.remove(availableBottom.size() - 1));
            consumed.add(availableBottom.remove(availableBottom.size() - 1));
            consumed.add(availableGluons.remove(availableGluons.size() - 1));
            consumed.add(availableGluons.remove(availableGluons.size() - 1));
        }

        // Pion: 1 top + 1 bottom + 1 gluon
        while (availableTop.size() >= 1 && availableBottom.size() >= 1
                && availableGluons.size() >= 1) {
            results.add(Hadron.PION);
            consumed.add(availableTop.remove(availableTop.size() - 1));
            consumed.add(availableBottom.remove(availableBottom.size() - 1));
            consumed.add(availableGluons.remove(availableGluons.size() - 1));
        }

        return results;
    }

    /**
     * Applies column gravity after cells are consumed.
     */
    private void applyGravity(Board board) {
        for (int c = 0; c < Board.WIDTH; c++) {
            int writeRow = 0;
            for (int r = 0; r < Board.HEIGHT; r++) {
                Piece p = board.getCell(c, r);
                if (p != null) {
                    if (writeRow != r) {
                        board.setCell(c, writeRow, p);
                        board.setCell(c, r, null);
                    }
                    writeRow++;
                }
            }
        }
    }

    private boolean inBounds(int col, int row) {
        return col >= 0 && col < Board.WIDTH && row >= 0 && row < Board.HEIGHT;
    }

    private long pack(int col, int row) {
        return ((long) col << 32) | (row & 0xFFFFFFFFL);
    }

    private int unpackCol(long packed) {
        return (int) (packed >> 32);
    }

    private int unpackRow(long packed) {
        return (int) packed;
    }

    /**
     * Represents a group of cells linked through gluons.
     */
    private static class GluonGroup {
        final Set<Long> gluons = new HashSet<>();
        final Set<Long> topQuarks = new HashSet<>();
        final Set<Long> bottomQuarks = new HashSet<>();
        final Set<Long> allCells = new HashSet<>();
    }
}
