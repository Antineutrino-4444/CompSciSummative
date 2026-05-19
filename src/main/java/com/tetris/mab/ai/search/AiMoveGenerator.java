package com.tetris.mab.ai.search;

import com.tetris.model.Position;
import com.tetris.model.SRSData;
import com.tetris.model.Tetromino;
import com.tetris.model.TetrominoType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Enumerates legal final placements for a piece on an {@link AiBoardModel}.
 *
 * <p>Two enumeration depths:
 * <ul>
 *   <li><b>Simple drop</b>: for each rotation, scan all horizontal
 *       positions, drop straight down to the resting row. Used by the
 *       lower difficulty tiers.</li>
 *   <li><b>Tuck + spin search</b>: BFS over reachable positions using
 *       rotate / left / right / soft-drop transitions, then collect all
 *       resting states (where the piece cannot move down). Captures
 *       T-spin slots, tucks, and immobile spins.</li>
 * </ul>
 *
 * <p>SRS wall-kick offsets are applied via {@link SRSData}. The 180°
 * rotation table is used when {@link #include180} is set.
 */
public final class AiMoveGenerator {

    private final boolean fullTuckSearch;
    private final boolean include180;

    public AiMoveGenerator(boolean fullTuckSearch, boolean include180) {
        this.fullTuckSearch = fullTuckSearch;
        this.include180 = include180;
    }

    /**
     * Enumerate placements for both the current piece and (optionally)
     * the held piece. Returns a list of fresh {@link AiMove} records,
     * each with its own post-placement board so the search can score
     * without re-simulating.
     */
    public List<AiMove> generate(AiBoardModel board, TetrominoType currentType,
                                  TetrominoType holdType, boolean canHold) {
        List<AiMove> moves = new ArrayList<>();
        addMovesForPiece(moves, board, currentType, /*usedHold*/ false);
        if (canHold) {
            TetrominoType swapped = (holdType != null) ? holdType : null;
            // If hold slot is empty, swap "pulls" the queue's NEXT piece —
            // outside of this generator's view. The caller approximates
            // by passing a non-null holdType where available. With no
            // hold piece, we skip the alternative (it costs the same
            // piece but loses one tempo).
            if (swapped != null && swapped != currentType) {
                addMovesForPiece(moves, board, swapped, /*usedHold*/ true);
            }
        }
        return moves;
    }

    private void addMovesForPiece(List<AiMove> moves, AiBoardModel board,
                                  TetrominoType type, boolean usedHold) {
        if (type == null) return;
        int rotCount = (type == TetrominoType.O) ? 1
                : (type == TetrominoType.I || type == TetrominoType.S || type == TetrominoType.Z)
                ? 2 : 4;
        // Always include simple drop landings — fast and always reachable
        // by the visible-board driver. BFS adds tucks/spins on top when
        // enabled, but the driver only realises spin landings opportunistically
        // (via the post-translation rotate-into-slot pass).
        int simpleStart = moves.size();
        collectViaSimpleDrop(moves, board, type, usedHold, rotCount);
        if (fullTuckSearch) {
            int beforeBfs = moves.size();
            collectViaBfs(moves, board, type, usedHold);
            // Dedupe BFS results against simple-drop using landing keys.
            HashSet<Long> simpleKeys = new HashSet<>();
            for (int i = simpleStart; i < beforeBfs; i++) {
                simpleKeys.add(landingKey(moves.get(i).landed));
            }
            int writeIdx = beforeBfs;
            for (int i = beforeBfs; i < moves.size(); i++) {
                AiMove m = moves.get(i);
                if (simpleKeys.contains(landingKey(m.landed))) continue;
                if (writeIdx != i) moves.set(writeIdx, m);
                writeIdx++;
            }
            while (moves.size() > writeIdx) moves.remove(moves.size() - 1);
        }
    }

    // ─────────────────────────── Simple drop ───────────────────────────

    private void collectViaSimpleDrop(List<AiMove> moves, AiBoardModel board,
                                      TetrominoType type, boolean usedHold,
                                      int rotCount) {
        Set<Long> seen = new HashSet<>();
        Tetromino base = AiBoardModel.spawn(type);
        for (int rot = 0; rot < rotCount; rot++) {
            Tetromino rotated = base.withRotation(rot);
            int pieceMinX = Integer.MAX_VALUE, pieceMaxX = Integer.MIN_VALUE;
            for (Position c : rotated.getAbsoluteCells()) {
                pieceMinX = Math.min(pieceMinX, c.getX());
                pieceMaxX = Math.max(pieceMaxX, c.getX());
            }
            int minDx = -pieceMinX;
            int maxDx = (AiBoardModel.WIDTH - 1) - pieceMaxX;
            for (int dx = minDx; dx <= maxDx; dx++) {
                Tetromino shifted = rotated.translate(dx, 0);
                Tetromino landed = board.drop(shifted);
                if (landed == null) continue;
                long key = landingKey(landed);
                if (!seen.add(key)) continue;
                appendMove(moves, board, type, rot, landed, usedHold,
                        /*lastMoveWasRotation*/ false, /*isHardDropOnly*/ true);
            }
        }
    }

    // ─────────────────────────── BFS tuck/spin ─────────────────────────

    private static final int MAX_BFS_NODES = 2400;

    private void collectViaBfs(List<AiMove> moves, AiBoardModel board,
                               TetrominoType type, boolean usedHold) {
        Tetromino spawn = AiBoardModel.spawn(type);
        if (!board.fits(spawn)) {
            // Spawn collides — record one move at spawn col so the
            // evaluator can still rank top-out severity.
            appendMove(moves, board, type, spawn.getRotationState(),
                    spawn, usedHold, false, true);
            return;
        }
        // Each BFS node carries: piece state + whether the last successful
        // move was a rotation (needed for T-spin detection).
        Set<Long> visited = new HashSet<>();
        ArrayDeque<Node> queue = new ArrayDeque<>();
        Node start = new Node(spawn, false);
        queue.add(start);
        visited.add(state(spawn));
        Set<Long> landings = new HashSet<>();
        int visitedNodes = 0;
        while (!queue.isEmpty() && visitedNodes < MAX_BFS_NODES) {
            Node n = queue.poll();
            visitedNodes++;
            // Try each move: rotate-CW, rotate-CCW, rotate-180 (opt), left, right, down.
            tryRotate(board, n, +1, visited, queue);
            tryRotate(board, n, -1, visited, queue);
            if (include180) tryRotate180(board, n, visited, queue);
            tryTranslate(board, n, -1, 0, visited, queue);
            tryTranslate(board, n, +1, 0, visited, queue);
            tryTranslate(board, n, 0, +1, visited, queue);
            // If the piece can't move down, this is a resting placement.
            Tetromino down = n.piece.moveDown();
            if (!board.fits(down)) {
                long key = landingKey(n.piece);
                if (landings.add(key)) {
                    appendMove(moves, board, type, n.piece.getRotationState(),
                            n.piece, usedHold, n.lastWasRotation, false);
                }
            }
        }
    }

    private void tryRotate(AiBoardModel board, Node from, int dir,
                            Set<Long> visited, ArrayDeque<Node> queue) {
        int fromState = from.piece.getRotationState();
        int toState = (fromState + (dir > 0 ? 1 : 3)) % 4;
        Tetromino rotated = from.piece.withRotation(toState);
        if (board.fits(rotated)) {
            if (visited.add(state(rotated))) queue.add(new Node(rotated, true));
            return;
        }
        Position[] kicks = SRSData.getKicks(from.piece.getType(), fromState, toState);
        for (Position k : kicks) {
            Tetromino kicked = rotated.translate(k.getX(), k.getY());
            if (board.fits(kicked)) {
                if (visited.add(state(kicked))) queue.add(new Node(kicked, true));
                return;
            }
        }
    }

    private void tryRotate180(AiBoardModel board, Node from,
                               Set<Long> visited, ArrayDeque<Node> queue) {
        int fromState = from.piece.getRotationState();
        int toState = (fromState + 2) % 4;
        Tetromino rotated = from.piece.withRotation(toState);
        if (board.fits(rotated)) {
            if (visited.add(state(rotated))) queue.add(new Node(rotated, true));
            return;
        }
        Position[] kicks = SRSData.getKicks180(from.piece.getType(), fromState, toState);
        for (Position k : kicks) {
            Tetromino kicked = rotated.translate(k.getX(), k.getY());
            if (board.fits(kicked)) {
                if (visited.add(state(kicked))) queue.add(new Node(kicked, true));
                return;
            }
        }
    }

    private void tryTranslate(AiBoardModel board, Node from, int dx, int dy,
                               Set<Long> visited, ArrayDeque<Node> queue) {
        Tetromino moved = from.piece.translate(dx, dy);
        if (!board.fits(moved)) return;
        if (visited.add(state(moved))) queue.add(new Node(moved, false));
    }

    // ─────────────────────────── Append + score ────────────────────────

    private void appendMove(List<AiMove> moves, AiBoardModel board,
                             TetrominoType type, int rot, Tetromino landed,
                             boolean usedHold, boolean lastWasRotation,
                             boolean hardDropOnly) {
        AiBoardModel after = board.deepCopy();
        after.lock(landed);
        int cleared = after.clearLines();
        AiMove.SpinTag spinTag = detectSpin(type, landed, lastWasRotation, board);
        boolean perfectClear = cleared > 0 && after.topRowsClear(AiBoardModel.TOTAL_HEIGHT);
        moves.add(new AiMove(
                type, rot, landed.getBoardPosition().getX(),
                landed, usedHold, cleared, spinTag, lastWasRotation,
                after, perfectClear, hardDropOnly));
    }

    private static AiMove.SpinTag detectSpin(TetrominoType type, Tetromino landed,
                                              boolean lastWasRotation,
                                              AiBoardModel boardBeforeLock) {
        if (!lastWasRotation) return AiMove.SpinTag.NONE;
        if (type == TetrominoType.T) {
            int corners = countOccupiedCorners(landed, boardBeforeLock);
            if (corners >= 3) return AiMove.SpinTag.TSPIN;
        } else if (type != TetrominoType.O) {
            // Immobile-spin detection: rotation succeeded but the piece
            // cannot move L / R / D in any direction. Common for S/Z/L/J/I.
            boolean immobile = !boardBeforeLock.fits(landed.moveLeft())
                    && !boardBeforeLock.fits(landed.moveRight())
                    && !boardBeforeLock.fits(landed.moveDown());
            if (immobile) return AiMove.SpinTag.IMMOBILE_SPIN;
        }
        return AiMove.SpinTag.NONE;
    }

    private static int countOccupiedCorners(Tetromino landed, AiBoardModel board) {
        // For a 3×3 T bounding box, corners are at (bx, by), (bx+2, by),
        // (bx, by+2), (bx+2, by+2).
        int bx = landed.getBoardPosition().getX();
        int by = landed.getBoardPosition().getY();
        int n = 0;
        if (board.isOccupiedOrWall(bx, by)) n++;
        if (board.isOccupiedOrWall(bx + 2, by)) n++;
        if (board.isOccupiedOrWall(bx, by + 2)) n++;
        if (board.isOccupiedOrWall(bx + 2, by + 2)) n++;
        return n;
    }

    private static long state(Tetromino p) {
        return ((long) p.getBoardPosition().getY() & 0xFFFFL)
                | (((long) p.getBoardPosition().getX() & 0xFFFFL) << 16)
                | (((long) p.getRotationState() & 0xFFL) << 32)
                | (((long) p.getType().ordinal() & 0xFFL) << 40);
    }

    private static long landingKey(Tetromino p) {
        // Use the absolute occupied cells so equivalent landings (e.g. O
        // piece with different rotation indices) collapse.
        long h = 1469598103934665603L;
        for (Position c : p.getAbsoluteCells()) {
            h ^= ((long) (c.getY() * AiBoardModel.WIDTH + c.getX()) & 0xFFFFL);
            h *= 1099511628211L;
        }
        return h;
    }

    private static final class Node {
        final Tetromino piece;
        final boolean lastWasRotation;
        Node(Tetromino p, boolean rot) { this.piece = p; this.lastWasRotation = rot; }
    }

    // Tiny ArrayDeque alias to avoid a new import in every file.
    private static final class ArrayDeque<E> extends java.util.ArrayDeque<E> {}
}
