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
 *   <li>Collect quarks directly adjacent to any gluon in the cluster.
 *       <b>Only</b> quark cells individually touching a gluon participate —
 *       no quark-to-quark chaining is allowed.</li>
 *   <li>Check if any hadron recipe matches (Proton &gt; Neutron &gt; Pion priority)</li>
 *   <li>Consume the minimum cells needed for the hadron</li>
 * </ol>
 *
 * <h3>Design Implication</h3>
 * <p>Each participating quark cell must itself touch a gluon in the cluster. This
 * gives the player precise control — dropping a 3-cell quark where only the tip
 * touches the gluon network consumes only that tip. The choice of pion vs proton
 * is deliberate, not accidental.</p>
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
     * @return list of hadron formations detected (may be empty), each containing
     *         the hadron type and the board cells that were consumed
     */
    public List<HadronFormation> detect(Board board, Piece piece, int rotation, int col, int row) {
        List<HadronFormation> found = new ArrayList<>();

        // Compute seed area: cells belonging to the just-placed piece and their neighbors
        Set<Long> seedCells = new HashSet<>();
        int[][] cells = piece.getCells(rotation);
        for (int[] cell : cells) {
            int cx = col + cell[0];
            int cy = row - cell[1];
            long packed = pack(cx, cy);
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
                GluonGroup group = buildGluonGroup(board, sx, sy, visited, allConsumed);
                List<HadronFormation> groupHadrons = matchRecipes(group, allConsumed);
                found.addAll(groupHadrons);
            }
        }

        // Clear consumed cells
        for (long packed : allConsumed) {
            board.setCell(unpackCol(packed), unpackRow(packed), null);
        }

        // Apply sticky gravity if cells were consumed
        if (!allConsumed.isEmpty()) {
            applyStickyGravity(board);
        }

        return found;
    }

    /**
     * Previews what hadron formations would occur if the given piece were locked
     * at the specified position. Does NOT modify the board — runs detection on a copy.
     *
     * <p>Useful for ghost-piece highlighting: shows which cells would be consumed
     * and which recipe would fire before the player commits to the placement.</p>
     *
     * @param board    the game board (not modified)
     * @param piece    the piece to preview
     * @param rotation the rotation of the piece
     * @param col      the column of the piece
     * @param row      the row of the piece
     * @return list of formations that would occur (may be empty)
     */
    public List<HadronFormation> previewFormation(Board board, Piece piece, int rotation,
                                                    int col, int row) {
        Board copy = board.copy();
        copy.placePiece(piece, rotation, col, row);
        return detect(copy, piece, rotation, col, row);
    }

    /**
     * BFS from a gluon cell to find the complete gluon-linked group.
     * A gluon-linked group consists of:
     * - All gluon cells connected to the starting gluon (through other gluons)
     * - All quark cells that are directly adjacent to at least one gluon in the group
     *
     * Only quark cells individually touching a gluon participate — no quark-to-quark
     * chaining. This gives the player precise control over which cells are consumed.
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
     * Applies sticky (connected-component) gravity after cells are consumed.
     * Cells that are still touching each other fall as a rigid group, preserving
     * built structures. Groups are processed bottom-up to avoid mid-fall collisions.
     */
    private void applyStickyGravity(Board board) {
        boolean changed = true;
        while (changed) {
            changed = false;
            // Find all connected components
            boolean[][] visited = new boolean[Board.HEIGHT][Board.WIDTH];
            List<List<long[]>> floatingGroups = new ArrayList<>();

            for (int r = 0; r < Board.HEIGHT; r++) {
                for (int c = 0; c < Board.WIDTH; c++) {
                    if (board.getCell(c, r) != null && !visited[r][c]) {
                        List<long[]> component = new ArrayList<>();
                        boolean grounded = floodFillComponent(board, c, r, visited, component);
                        if (!grounded) {
                            floatingGroups.add(component);
                        }
                    }
                }
            }

            // Sort groups by their lowest row (ascending) so we drop bottom groups first
            floatingGroups.sort((a, b) -> {
                int minA = Integer.MAX_VALUE, minB = Integer.MAX_VALUE;
                for (long[] cell : a) minA = Math.min(minA, (int) cell[1]);
                for (long[] cell : b) minB = Math.min(minB, (int) cell[1]);
                return Integer.compare(minA, minB);
            });

            for (List<long[]> group : floatingGroups) {
                // Determine how far this group can drop
                int dropDist = computeDropDistance(board, group);
                if (dropDist > 0) {
                    changed = true;
                    // Sort cells top-to-bottom so we move lower rows first (avoid overwriting)
                    group.sort((a, b) -> Long.compare(a[1], b[1]));
                    // Clear old positions
                    Piece[] pieces = new Piece[group.size()];
                    for (int i = 0; i < group.size(); i++) {
                        long[] cell = group.get(i);
                        pieces[i] = board.getCell((int) cell[0], (int) cell[1]);
                        board.setCell((int) cell[0], (int) cell[1], null);
                    }
                    // Place at new positions
                    for (int i = 0; i < group.size(); i++) {
                        long[] cell = group.get(i);
                        board.setCell((int) cell[0], (int) cell[1] - dropDist, pieces[i]);
                    }
                }
            }
        }
    }

    /**
     * Flood-fills from (startCol, startRow) to find a connected component.
     * Returns true if the component is "grounded" (touches row 0 or sits on
     * a non-empty cell below any of its cells).
     */
    private boolean floodFillComponent(Board board, int startCol, int startRow,
                                        boolean[][] visited, List<long[]> component) {
        Queue<long[]> queue = new LinkedList<>();
        queue.add(new long[]{startCol, startRow});
        visited[startRow][startCol] = true;
        boolean grounded = false;

        while (!queue.isEmpty()) {
            long[] current = queue.poll();
            int cx = (int) current[0];
            int cy = (int) current[1];
            component.add(current);

            if (cy == 0) grounded = true;

            for (int[] n : NEIGHBORS) {
                int nx = cx + n[0];
                int ny = cy + n[1];
                if (inBounds(nx, ny) && !visited[ny][nx] && board.getCell(nx, ny) != null) {
                    visited[ny][nx] = true;
                    queue.add(new long[]{nx, ny});
                }
            }
        }

        return grounded;
    }

    /**
     * Computes how far a floating group can drop before hitting the floor
     * or another settled cell.
     */
    private int computeDropDistance(Board board, List<long[]> group) {
        Set<Long> groupSet = new HashSet<>();
        for (long[] cell : group) {
            groupSet.add(pack((int) cell[0], (int) cell[1]));
        }

        int minDrop = Integer.MAX_VALUE;
        for (long[] cell : group) {
            int cx = (int) cell[0];
            int cy = (int) cell[1];
            int drop = 0;
            for (int testY = cy - 1; testY >= 0; testY--) {
                long below = pack(cx, testY);
                if (groupSet.contains(below)) {
                    continue; // skip group's own cells
                }
                if (board.getCell(cx, testY) != null) {
                    break; // hit another settled cell
                }
                drop++;
            }
            // If no obstruction found, drop = distance to floor
            if (cy - drop < 0) drop = cy;
            minDrop = Math.min(minDrop, drop);
        }

        return minDrop;
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
