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
 * <h3>Detection Mechanic</h3>
 * <p>After a piece locks onto the board, the detector scans all cells belonging
 * to the just-placed piece and their neighbors to find connected groups of
 * particles that match a known hadron recipe.</p>
 *
 * <h3>How Hadrons Form</h3>
 * <p>A hadron is formed when the right combination of quarks and gluons are
 * <b>adjacent</b> (orthogonally connected — up/down/left/right, not diagonal)
 * on the board. When a valid combination is found:</p>
 * <ol>
 *   <li>The hadron is recorded in the discovery log</li>
 *   <li>The participating cells are cleared from the board (consumed)</li>
 *   <li>Cells above drop down (gravity)</li>
 * </ol>
 *
 * <h3>Recipes</h3>
 * <ul>
 *   <li><b>Proton</b>: 3 connected cells — exactly 2 Top Quarks + 1 Bottom Quark</li>
 *   <li><b>Neutron</b>: 3 connected cells — exactly 1 Top Quark + 2 Bottom Quarks</li>
 *   <li><b>Pion π+</b>: 2 connected cells — 1 Top Quark + 1 Gluon</li>
 *   <li><b>Pion π−</b>: 2 connected cells — 1 Bottom Quark + 1 Gluon</li>
 *   <li><b>Pion π0</b>: 3 connected cells — 2 same-flavor Quarks + 1 Gluon in the middle</li>
 * </ul>
 */
public class HadronDetector {

    /** Orthogonal neighbor offsets: right, up, left, down. */
    private static final int[][] NEIGHBORS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

    /**
     * Scans the board for hadron patterns near the given piece placement.
     *
     * <p>Returns a list of detected hadrons. For each detection, the participating
     * cells are cleared from the board. Multiple hadrons can be detected from
     * a single piece placement.</p>
     *
     * @param board the game board
     * @param piece the piece that was just placed
     * @param rotation the rotation of the placed piece
     * @param col the column of the placed piece
     * @param row the row of the placed piece
     * @return list of hadrons detected (may be empty)
     */
    public List<Hadron> detect(Board board, Piece piece, int rotation, int col, int row) {
        List<Hadron> found = new ArrayList<>();

        // Collect all cells belonging to the just-placed piece
        Set<Long> pieceCells = new HashSet<>();
        int[][] cells = piece.getCells(rotation);
        for (int[] cell : cells) {
            int cx = col + cell[0];
            int cy = row - cell[1];
            pieceCells.add(packCoord(cx, cy));
        }

        // Also include immediate neighbors of placed cells for detection
        Set<Long> searchArea = new HashSet<>(pieceCells);
        for (long packed : new HashSet<>(pieceCells)) {
            int cx = unpackCol(packed);
            int cy = unpackRow(packed);
            for (int[] n : NEIGHBORS) {
                int nx = cx + n[0];
                int ny = cy + n[1];
                if (nx >= 0 && nx < Board.WIDTH && ny >= 0 && ny < Board.HEIGHT) {
                    if (board.getCell(nx, ny) != null) {
                        searchArea.add(packCoord(nx, ny));
                    }
                }
            }
        }

        // Try to find hadrons starting from each cell in the search area
        Set<Long> consumed = new HashSet<>(); // Cells already used in a hadron

        // Priority: larger hadrons first (Proton/Neutron/Pion0 = 3 cells, then Pion+/- = 2 cells)
        // Check 3-cell hadrons first
        found.addAll(findBaryons(board, searchArea, consumed));
        found.addAll(findPionZero(board, searchArea, consumed));
        // Then 2-cell mesons
        found.addAll(findPionCharged(board, searchArea, consumed));

        // Clear consumed cells from the board
        for (long packed : consumed) {
            board.setCell(unpackCol(packed), unpackRow(packed), null);
        }

        // If cells were consumed, apply gravity (drop cells down)
        if (!consumed.isEmpty()) {
            applyGravity(board);
        }

        return found;
    }

    /**
     * Finds Proton (uud) and Neutron (udd) baryons — 3 connected cells.
     */
    private List<Hadron> findBaryons(Board board, Set<Long> searchArea, Set<Long> consumed) {
        List<Hadron> found = new ArrayList<>();

        for (long packed : searchArea) {
            int cx = unpackCol(packed);
            int cy = unpackRow(packed);
            if (consumed.contains(packed)) continue;
            Piece p = board.getCell(cx, cy);
            if (p == null) continue;

            // Try to form 3-cell connected groups
            for (int[] n1 : NEIGHBORS) {
                int x1 = cx + n1[0];
                int y1 = cy + n1[1];
                long p1 = packCoord(x1, y1);
                if (consumed.contains(p1)) continue;
                Piece piece1 = board.getCell(x1, y1);
                if (piece1 == null) continue;

                for (int[] n2 : NEIGHBORS) {
                    int x2 = x1 + n2[0];
                    int y2 = y1 + n2[1];
                    long p2 = packCoord(x2, y2);
                    if (p2 == packed || consumed.contains(p2)) continue;
                    Piece piece2 = board.getCell(x2, y2);
                    if (piece2 == null) continue;

                    // Count particle types in this trio
                    int topCount = countType(Piece.ParticleType.TOP_QUARK, p, piece1, piece2);
                    int bottomCount = countType(Piece.ParticleType.BOTTOM_QUARK, p, piece1, piece2);
                    int gluonCount = countType(Piece.ParticleType.GLUON, p, piece1, piece2);

                    // Proton: 2 top + 1 bottom
                    if (topCount == 2 && bottomCount == 1 && gluonCount == 0) {
                        found.add(Hadron.PROTON);
                        consumed.add(packed);
                        consumed.add(p1);
                        consumed.add(p2);
                    }
                    // Neutron: 1 top + 2 bottom
                    else if (topCount == 1 && bottomCount == 2 && gluonCount == 0) {
                        found.add(Hadron.NEUTRON);
                        consumed.add(packed);
                        consumed.add(p1);
                        consumed.add(p2);
                    }
                }
            }
        }
        return found;
    }

    /**
     * Finds neutral pion π0 — 3 connected cells: 2 same quarks + 1 gluon.
     * The gluon must be in the middle (connecting the two quarks).
     */
    private List<Hadron> findPionZero(Board board, Set<Long> searchArea, Set<Long> consumed) {
        List<Hadron> found = new ArrayList<>();

        for (long packed : searchArea) {
            int cx = unpackCol(packed);
            int cy = unpackRow(packed);
            if (consumed.contains(packed)) continue;
            Piece p = board.getCell(cx, cy);
            if (p == null || !p.isGluon()) continue;

            // This gluon is the center — look for 2 same-type quarks on opposite sides
            // or in an L-shape around it
            List<long[]> quarkNeighbors = new ArrayList<>();
            for (int[] n : NEIGHBORS) {
                int nx = cx + n[0];
                int ny = cy + n[1];
                long np = packCoord(nx, ny);
                if (consumed.contains(np)) continue;
                Piece neighbor = board.getCell(nx, ny);
                if (neighbor != null && (neighbor.isTopQuark() || neighbor.isBottomQuark())) {
                    quarkNeighbors.add(new long[]{np, neighbor.isTopQuark() ? 1 : 2});
                }
            }

            // Need 2 quarks of same flavor adjacent to this gluon
            for (int i = 0; i < quarkNeighbors.size(); i++) {
                for (int j = i + 1; j < quarkNeighbors.size(); j++) {
                    if (quarkNeighbors.get(i)[1] == quarkNeighbors.get(j)[1]) {
                        long qp1 = quarkNeighbors.get(i)[0];
                        long qp2 = quarkNeighbors.get(j)[0];
                        if (!consumed.contains(qp1) && !consumed.contains(qp2)) {
                            found.add(Hadron.PION_ZERO);
                            consumed.add(packed);
                            consumed.add(qp1);
                            consumed.add(qp2);
                            break;
                        }
                    }
                }
                if (consumed.contains(packed)) break;
            }
        }
        return found;
    }

    /**
     * Finds charged pions — 2 connected cells: 1 quark + 1 gluon.
     * π+ = Top Quark + Gluon, π− = Bottom Quark + Gluon.
     */
    private List<Hadron> findPionCharged(Board board, Set<Long> searchArea, Set<Long> consumed) {
        List<Hadron> found = new ArrayList<>();

        for (long packed : searchArea) {
            int cx = unpackCol(packed);
            int cy = unpackRow(packed);
            if (consumed.contains(packed)) continue;
            Piece p = board.getCell(cx, cy);
            if (p == null) continue;

            // Look for quark-gluon pairs
            if (p.isGluon()) {
                // Check neighbors for quarks
                for (int[] n : NEIGHBORS) {
                    int nx = cx + n[0];
                    int ny = cy + n[1];
                    long np = packCoord(nx, ny);
                    if (consumed.contains(np)) continue;
                    Piece neighbor = board.getCell(nx, ny);
                    if (neighbor == null) continue;

                    if (neighbor.isTopQuark()) {
                        found.add(Hadron.PION_PLUS);
                        consumed.add(packed);
                        consumed.add(np);
                        break;
                    } else if (neighbor.isBottomQuark()) {
                        found.add(Hadron.PION_MINUS);
                        consumed.add(packed);
                        consumed.add(np);
                        break;
                    }
                }
            } else if (p.isTopQuark() || p.isBottomQuark()) {
                // Check neighbors for gluons
                for (int[] n : NEIGHBORS) {
                    int nx = cx + n[0];
                    int ny = cy + n[1];
                    long np = packCoord(nx, ny);
                    if (consumed.contains(np)) continue;
                    Piece neighbor = board.getCell(nx, ny);
                    if (neighbor != null && neighbor.isGluon()) {
                        found.add(p.isTopQuark() ? Hadron.PION_PLUS : Hadron.PION_MINUS);
                        consumed.add(packed);
                        consumed.add(np);
                        break;
                    }
                }
            }
        }
        return found;
    }

    /**
     * Applies naive gravity after cells are consumed — drops floating cells down.
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

    /** Counts how many of the given pieces match the specified particle type. */
    private int countType(Piece.ParticleType type, Piece... pieces) {
        int count = 0;
        for (Piece p : pieces) {
            if (p.getParticleType() == type) count++;
        }
        return count;
    }

    /** Packs column and row into a single long for use as a hash key. */
    private long packCoord(int col, int row) {
        return ((long) col << 32) | (row & 0xFFFFFFFFL);
    }

    /** Unpacks the column from a packed coordinate. */
    private int unpackCol(long packed) {
        return (int) (packed >> 32);
    }

    /** Unpacks the row from a packed coordinate. */
    private int unpackRow(long packed) {
        return (int) packed;
    }
}
