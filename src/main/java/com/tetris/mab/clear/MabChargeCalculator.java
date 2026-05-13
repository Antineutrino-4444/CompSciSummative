package com.tetris.mab.clear;

import com.tetris.events.GameEventListener.LinesClearedEvent;
import com.tetris.events.PieceLockResult;
import com.tetris.mab.ParticipantId;
import com.tetris.model.TetrominoType;

/**
 * Step 21 \u2014 simplified MAB charge formula.
 *
 * <p>Pure stateless calculator. Maps a normal {@link LinesClearedEvent}
 * to a {@link MabClearResult} carrying both the original clear flags and
 * the charge value the participant should gain.
 *
 * <p><b>Formula</b>
 * <pre>
 *   base       single=1  double=3  triple=5  tetris=8
 *   perfectClr +12
 *   spin bonus none=4 single=8 double=14 triple=20 quad=24
 *   combo      0-1:+0  2-3:+1  4-6:+2  7+:+4
 *   B2B (tetris or spin): total *= 1.25 (rounded half-up)
 * </pre>
 */
public final class MabChargeCalculator {

    private MabChargeCalculator() {}

    /**
     * Convert a raw {@link LinesClearedEvent} into a {@link MabClearResult}
     * using the simplified formula. Display text is short and player-facing
     * (e.g. {@code "Spin Double +17"}, {@code "B2B Tetris +10"}).
     */
    public static MabClearResult fromEvent(ParticipantId pid, LinesClearedEvent e) {
        if (e == null) throw new IllegalArgumentException("event");
        MabClearResult.SpinKind spin =
                e.tSpin() ? MabClearResult.SpinKind.T_SPIN
                : e.tSpinMini() ? MabClearResult.SpinKind.T_SPIN_MINI
                : MabClearResult.SpinKind.NONE;
        int charge = compute(e.count(), spin != MabClearResult.SpinKind.NONE,
                e.perfectClear(), e.backToBack(), e.combo(), e.tetris());
        String text = renderDisplay(e.count(), spin, e.perfectClear(),
                e.backToBack(), e.combo(), e.tetris(), charge, null);
        return new MabClearResult(pid, e.count(), spin,
                e.perfectClear(), e.backToBack(), e.combo(),
                e.tetris(), charge, text);
    }

    /**
     * Step 22 \u2014 build a {@link MabClearResult} from the unified
     * {@link PieceLockResult}. This is the preferred entry point because
     * it carries 0-line spins, the locked piece type, and the hard-drop
     * flag. Spin kind is mapped from the lock result:
     * <ul>
     *   <li>{@code tSpinFull} \u2192 {@code T_SPIN}</li>
     *   <li>{@code tSpinMini} \u2192 {@code T_SPIN_MINI}</li>
     *   <li>any other {@code spin()} \u2192 {@code IMMOBILE}</li>
     * </ul>
     */
    public static MabClearResult fromLockResult(ParticipantId pid, PieceLockResult lr) {
        if (lr == null) throw new IllegalArgumentException("lockResult");
        MabClearResult.SpinKind spin;
        if (lr.tSpinFull()) spin = MabClearResult.SpinKind.T_SPIN;
        else if (lr.tSpinMini()) spin = MabClearResult.SpinKind.T_SPIN_MINI;
        else if (lr.spin()) spin = MabClearResult.SpinKind.IMMOBILE;
        else spin = MabClearResult.SpinKind.NONE;
        int charge = compute(lr.linesCleared(), spin != MabClearResult.SpinKind.NONE,
                lr.perfectClear(), lr.backToBack(), lr.comboCount(), lr.tetris());
        String text = renderDisplay(lr.linesCleared(), spin, lr.perfectClear(),
                lr.backToBack(), lr.comboCount(), lr.tetris(), charge, lr.pieceType());
        return new MabClearResult(pid, lr.linesCleared(), spin,
                lr.perfectClear(), lr.backToBack(), lr.comboCount(),
                lr.tetris(), charge, text, lr.pieceType());
    }

    /**
     * Public computation hook so probes can verify formula values without
     * having to synthesize {@link LinesClearedEvent}s.
     */
    public static int compute(int lines, boolean spin, boolean perfectClear,
                              boolean backToBack, int combo, boolean tetris) {
        int base = baseLineCharge(lines);
        if (spin) base += spinBonus(lines);
        if (perfectClear) base += 12;
        base += comboBonus(combo);
        if (backToBack && (tetris || spin)) {
            base = (int) Math.round(base * 1.25);
        }
        return Math.max(0, base);
    }

    private static int baseLineCharge(int lines) {
        switch (Math.max(0, lines)) {
            case 0: return 0;
            case 1: return 1;
            case 2: return 3;
            case 3: return 5;
            case 4: return 8;
            default: return 8 + (lines - 4) * 2; // exotic 5+, future-proof
        }
    }

    private static int spinBonus(int lines) {
        switch (Math.max(0, lines)) {
            case 0: return 4;
            case 1: return 8;
            case 2: return 14;
            case 3: return 20;
            case 4: return 24;
            default: return 24;
        }
    }

    private static int comboBonus(int combo) {
        if (combo <= 1) return 0;
        if (combo <= 3) return 1;
        if (combo <= 6) return 2;
        return 4;
    }

    private static String renderDisplay(int lines, MabClearResult.SpinKind spin,
                                        boolean pc, boolean b2b, int combo,
                                        boolean tetris, int charge,
                                        TetrominoType pieceType) {
        StringBuilder sb = new StringBuilder();
        if (b2b) sb.append("B2B ");
        if (spin == MabClearResult.SpinKind.T_SPIN_MINI) sb.append("T Spin Mini ");
        else if (spin == MabClearResult.SpinKind.T_SPIN) sb.append("T Spin ");
        else if (spin == MabClearResult.SpinKind.IMMOBILE) {
            sb.append(pieceType == null ? "Spin " : (pieceType.name() + " Spin "));
        }
        if (pc) sb.append("Perfect ");
        switch (lines) {
            case 0:
                if (spin != MabClearResult.SpinKind.NONE) sb.append("no-line");
                else sb.append("(no-line)");
                break;
            case 1: sb.append("Single"); break;
            case 2: sb.append("Double"); break;
            case 3: sb.append("Triple"); break;
            case 4: sb.append(tetris ? "Tetris" : "Quad"); break;
            default: sb.append(lines).append("-line"); break;
        }
        if (combo > 1) sb.append(" x").append(combo);
        sb.append(" +").append(charge);
        return sb.toString().trim();
    }
}
