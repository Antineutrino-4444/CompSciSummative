package com.tetris.mab.sim;

import com.tetris.mab.clear.MabChargeCalculator;

/**
 * Step 21 \u2014 verifies the simplified charge formula values match the spec.
 *
 * <p>Run: {@code java -cp target\classes com.tetris.mab.sim.MabChargeCalculatorProbe}
 */
public final class MabChargeCalculatorProbe {

    private static int failed;

    public static void main(String[] args) {
        System.out.println("=== MAB Charge Calculator Probe ===");

        // (lines, spin, perfectClear, backToBack, combo, tetris)
        int single        = MabChargeCalculator.compute(1, false, false, false, 0, false);
        int dbl           = MabChargeCalculator.compute(2, false, false, false, 0, false);
        int tetris        = MabChargeCalculator.compute(4, false, false, false, 0, true);
        int b2bTetris     = MabChargeCalculator.compute(4, false, false, true,  0, true);
        int spinSingle    = MabChargeCalculator.compute(1, true,  false, false, 0, false);
        int spinDouble    = MabChargeCalculator.compute(2, true,  false, false, 0, false);
        int spinTriple    = MabChargeCalculator.compute(3, true,  false, false, 0, false);
        int pcTetris      = MabChargeCalculator.compute(4, false, true,  false, 0, true);
        int b2bSpinDouble = MabChargeCalculator.compute(2, true,  false, true,  0, false);
        // Step 22: 0-line spin (immobile rule) must grant the +4 spin floor.
        int spinNoLine    = MabChargeCalculator.compute(0, true,  false, false, 0, false);

        check("single",        single,        1);
        check("double",        dbl,           3);
        check("tetris",        tetris,        8);
        check("b2bTetris",     b2bTetris,     10);
        check("spinSingle",    spinSingle,    9);
        check("spinDouble",    spinDouble,    17);
        check("spinTriple",    spinTriple,    25);
        check("pcTetris",      pcTetris,      20);
        check("b2bSpinDouble", b2bSpinDouble, 21);
        check("spinNoLine",    spinNoLine,    4);

        boolean success = failed == 0;
        System.out.println("success=" + success);
        if (!success) System.exit(1);
    }

    private static void check(String label, int actual, int expected) {
        boolean ok = actual == expected;
        System.out.println(label + "=" + actual + (ok ? "" : " (expected " + expected + ")"));
        if (!ok) failed++;
    }

    private MabChargeCalculatorProbe() {}
}
