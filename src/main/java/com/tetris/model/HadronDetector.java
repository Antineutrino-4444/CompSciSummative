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
 *   <li>For each gluon, BFS outward through adjacent gluons to find
 *       the connected gluon cluster</li>
 *   <li>Collect quarks directly adjacent to any gluon in the cluster</li>
 *   <li>For quarks that are part of the <b>just-placed piece</b>, extend through
 *       quark-quark adjacency within that piece's cells. This ensures multi-cell
 *       quark pieces contribute all their cells when dropped onto a gluon network,
 *       preferring larger hadrons. Previously-placed quarks are NOT pulled in
 *       through quark chains — only the dropped piece extends.</li>
 *   <li>Check if any hadron recipe matches (Proton &gt; Neutron &gt; Pion priority)</li>
 *   <li>Consume the minimum cells needed for the hadron</li>
 * </ol>
 *
 * <h3>Design Implication</h3>
 * <p>Dropping a multi-cell quark piece onto a gluon network includes all cells of
 * that piece, preferring larger hadrons. But previously-placed quarks sitting
 * adjacent to the gluon network only contribute their directly gluon-adjacent cells.
 * To deliberately form a <b>pion</b>, ensure only 1 top + 1 bottom + 1 gluon
 * are connected.</p>
 *
 * <h3>What counts as "gluon-linked"</h3>
 * <p>A quark is part of a gluon network if it is orthogonally adjacent to at least
 * one gluon cell. Two quarks are gluon-linked if there exists a path of cells
 * between them that passes through at least one gluon. Quark-to-quark adjacency
 * WITHOUT an intervening gluon does NOT count (except within the just-placed piece).</p>
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
     * @return list of hadron formations detected (may be empty), each containing
     *         the hadron type and the board cells that were consumed
     */
    public List<HadronFormation> detect(Board board, Piece piece, int rotation, int col, int row) {
        List<HadronFormation> found = new ArrayList<>();

        // Compute cells belonging to the just-placed piece
        Set<Long> placedPieceCells = new HashSet<>();
        Set<Long> seedCells = new HashSet<>();
        int[][] cells = piece.getCells(rotation);
        for (int[] cell : cells) {
            int cx = col + cell[0];
            int cy = row - cell[1];
            long packed = pack(cx, cy);
            placedPieceCells.add(packed);
            seedCells.add(packed);
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
                GluonGroup group = buildGluonGroup(board, sx, sy, visited, allConsumed,
                        placedPieceCells);
                List<HadronFormation> groupHadrons = matchRecipes(group, allConsumed);
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
     * - Additional quark cells of the just-placed piece reachable through quark-quark
     *   adjacency from any quark already in the group
     *
     * Quarks from previously-placed pieces that only touch other quarks (not gluons)
     * are NOT included — only the just-placed piece extends through quark chains.
     */
    private GluonGroup buildGluonGroup(Board board, int startCol, int startRow,
                                        Set<Long> globalVisited, Set<Long> consumed,
                                        Set<Long> placedPieceCells) {
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
        Queue<Long> quarkBfsQueue = new LinkedList<>();
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
                        quarkBfsQueue.add(np);
                        if (neighbor.isTopQuark()) {
                            group.topQuarks.add(np);
                        } else {
                            group.bottomQuarks.add(np);
                        }
                    }
                }
            }
        }

        // Phase 3: BFS through quark cells that belong to the just-placed piece.
        // If a quark from the placed piece touches the gluon network, extend to
        // other cells of the same placed piece. This ensures multi-cell quark pieces
        // contribute all their cells when dropped, preferring larger hadrons.
        // Previously-placed quarks are NOT pulled in through quark chains.
        while (!quarkBfsQueue.isEmpty()) {
            long current = quarkBfsQueue.poll();
            // Only extend from quarks that belong to the just-placed piece
            if (!placedPieceCells.contains(current)) continue;
            int cx = unpackCol(current);
            int cy = unpackRow(current);
            for (int[] n : NEIGHBORS) {
                int nx = cx + n[0];
                int ny = cy + n[1];
                long np = pack(nx, ny);
                if (inBounds(nx, ny) && !consumed.contains(np) && !group.allCells.contains(np)
                        && placedPieceCells.contains(np)) {
                    Piece neighbor = board.getCell(nx, ny);
                    if (neighbor != null && neighbor.isQuark()) {
                        group.allCells.add(np);
                        quarkBfsQueue.add(np);
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
     * <p>Gluons are sorted so that those with the most adjacent quarks are consumed
     * first. This ensures the "bridging" gluon (the one actually between the quarks)
     * is consumed rather than an unrelated gluon in the cluster.</p>
     *
     * Returns HadronFormation objects that include the consumed cell positions.
     * Also adds consumed cells to the global consumed set.
     */
    private List<HadronFormation> matchRecipes(GluonGroup group, Set<Long> consumed) {
        List<HadronFormation> results = new ArrayList<>();

        // Make consumable iterators from the sets
        List<Long> availableTop = new ArrayList<>(group.topQuarks);
        List<Long> availableBottom = new ArrayList<>(group.bottomQuarks);

        // Sort gluons so those most connected to quarks are at the END of the list
        // (removed first by remove(size-1)). This ensures bridging gluons are consumed
        // before isolated gluons in the same cluster.
        Set<Long> allQuarks = new HashSet<>(group.topQuarks);
        allQuarks.addAll(group.bottomQuarks);
        List<Long> availableGluons = new ArrayList<>(group.gluons);
        availableGluons.sort((a, b) -> {
            int adjA = countAdjacentInSet(a, allQuarks);
            int adjB = countAdjacentInSet(b, allQuarks);
            return Integer.compare(adjA, adjB); // ascending: most-connected at end
        });

        // Proton: 2 top + 1 bottom + 2 gluons
        while (availableTop.size() >= 2 && availableBottom.size() >= 1
                && availableGluons.size() >= 2) {
            Set<Long> cells = new HashSet<>();
            cells.add(availableTop.remove(availableTop.size() - 1));
            cells.add(availableTop.remove(availableTop.size() - 1));
            cells.add(availableBottom.remove(availableBottom.size() - 1));
            cells.add(availableGluons.remove(availableGluons.size() - 1));
            cells.add(availableGluons.remove(availableGluons.size() - 1));
            consumed.addAll(cells);
            results.add(new HadronFormation(Hadron.PROTON, cells));
        }

        // Neutron: 1 top + 2 bottom + 2 gluons
        while (availableTop.size() >= 1 && availableBottom.size() >= 2
                && availableGluons.size() >= 2) {
            Set<Long> cells = new HashSet<>();
            cells.add(availableTop.remove(availableTop.size() - 1));
            cells.add(availableBottom.remove(availableBottom.size() - 1));
            cells.add(availableBottom.remove(availableBottom.size() - 1));
            cells.add(availableGluons.remove(availableGluons.size() - 1));
            cells.add(availableGluons.remove(availableGluons.size() - 1));
            consumed.addAll(cells);
            results.add(new HadronFormation(Hadron.NEUTRON, cells));
        }

        // Pion: 1 top + 1 bottom + 1 gluon
        while (availableTop.size() >= 1 && availableBottom.size() >= 1
                && availableGluons.size() >= 1) {
            Set<Long> cells = new HashSet<>();
            cells.add(availableTop.remove(availableTop.size() - 1));
            cells.add(availableBottom.remove(availableBottom.size() - 1));
            cells.add(availableGluons.remove(availableGluons.size() - 1));
            consumed.addAll(cells);
            results.add(new HadronFormation(Hadron.PION, cells));
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

    /**
     * Counts how many cells in the given set are orthogonally adjacent to the packed cell.
     */
    private int countAdjacentInSet(long packed, Set<Long> cells) {
        int cx = unpackCol(packed);
        int cy = unpackRow(packed);
        int count = 0;
        for (int[] n : NEIGHBORS) {
            if (cells.contains(pack(cx + n[0], cy + n[1]))) {
                count++;
            }
        }
        return count;
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
