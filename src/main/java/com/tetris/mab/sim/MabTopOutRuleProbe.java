package com.tetris.mab.sim;

import com.tetris.events.GameEventListener;
import com.tetris.events.GameEventListener.TopOutEvent;
import com.tetris.events.GameEventListener.TopOutReason;
import com.tetris.events.GarbageRowPattern;
import com.tetris.model.Board;
import com.tetris.model.GameState;

import java.util.List;

/**
 * Manual probe verifying the game's single top-out rule: the ONLY
 * condition that ends a game is the next piece being unable to spawn
 * (block-out). Locking a piece entirely in the hidden buffer rows
 * (formerly "lock-out") and rising garbage pushing cells past the buffer
 * ceiling (formerly "garbage overflow") must NOT end the game.
 *
 * <p>Run:
 * <pre>
 *   java -cp target\classes com.tetris.mab.sim.MabTopOutRuleProbe
 * </pre>
 *
 * <p><b>Offline-only.</b>
 */
public final class MabTopOutRuleProbe {

    public static void main(String[] args) {
        int pass = 0;
        int fail = 0;

        // ── Test 1: locking INTO the buffer never fires a LOCK_OUT ───────
        // Stack garbage to the visible ceiling (hole at col 0) and drop a
        // piece in the centre. It lands on top of the stack, locking with
        // cells inside the buffer zone (height > 20, the old "lock out").
        // The game may still end here — but ONLY because the very next
        // spawn lands in the now-occupied spawn rows (a legitimate
        // block-out). We capture the actual top-out reason and assert it
        // is never LOCK_OUT.
        {
            GameState gs = new GameState(1);
            final TopOutReason[] reasonSeen = { null };
            gs.addListener(new GameEventListener() {
                @Override public void onTopOut(TopOutEvent e) {
                    reasonSeen[0] = e.reason();
                }
            });
            gs.insertGarbagePattern(
                    GarbageRowPattern.cleanRepeated(Board.VISIBLE_HEIGHT, 0),
                    "test");
            gs.hardDrop(); // lock one piece on top → cells land in buffer
            final int height = gs.getBoardHeight();
            final boolean over = gs.isGameOver();
            final TopOutReason reason = reasonSeen[0];
            // Cells are proven to be in the buffer (height > visible). The
            // game must NOT have ended via the old lock-out rule.
            boolean noLockOut = height > Board.VISIBLE_HEIGHT
                    && reason != TopOutReason.LOCK_OUT;
            report("piece locked in buffer never triggers LOCK_OUT",
                    noLockOut,
                    () -> "stackHeight=" + height + " (visible="
                            + Board.VISIBLE_HEIGHT + ") gameOver=" + over
                            + " topOutReason=" + reason);
            if (noLockOut) pass++; else fail++;
        }

        // ── Test 2: rising garbage never tops out by itself ──────────────
        // Insert far more garbage rows than the board can hold. Cells get
        // pushed past the ceiling and discarded, but the game must not end
        // from the insert alone.
        {
            GameState gs = new GameState(1);
            boolean ended = false;
            for (int i = 0; i < 30 && !ended; i++) {
                gs.insertGarbagePattern(List.of(GarbageRowPattern.clean(3)), "flood");
                if (gs.isGameOver()) ended = true;
            }
            boolean survivedFlood = !gs.isGameOver();
            report("garbage flood (30 rows) does not top out", survivedFlood,
                    () -> "gameOver=" + gs.isGameOver());
            if (survivedFlood) pass++; else fail++;
        }

        // ── Test 3: a genuinely blocked spawn DOES top out ───────────────
        // Fill columns 1..9 to the ceiling, leaving only column 0 open.
        // No row is complete (col 0 is always a hole) so nothing clears on
        // lock, but the spawn area (centre columns) is buried — the next
        // piece cannot spawn, so the game must end (block-out).
        {
            GameState gs = new GameState(1);
            // More rows than the board is tall guarantees the playfield is
            // packed top-to-bottom in columns 1..9.
            gs.insertGarbagePattern(
                    GarbageRowPattern.cleanRepeated(Board.TOTAL_HEIGHT + 2, 0),
                    "wall");
            // Lock the current piece to force a fresh spawn into the packed
            // field.
            gs.hardDrop();
            final boolean over3 = gs.isGameOver();
            report("blocked spawn tops out (block-out)", over3,
                    () -> "gameOver=" + over3);
            if (over3) pass++; else fail++;
        }

        System.out.println();
        System.out.println("TopOut rule probe: " + pass + " passed, "
                + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    private static void report(String name, boolean ok,
                               java.util.function.Supplier<String> detail) {
        System.out.printf("[%s] %s — %s%n", ok ? "PASS" : "FAIL", name,
                detail.get());
    }

    private MabTopOutRuleProbe() {}
}
