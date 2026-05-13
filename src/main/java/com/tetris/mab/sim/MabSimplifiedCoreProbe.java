package com.tetris.mab.sim;

import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ParticipantState;
import com.tetris.mab.clear.MabSimplifiedStrategicState;
import com.tetris.model.GameState;

/**
 * Step 21 \u2014 verifies the simplified MAB strategic core:
 * charge gain, ready state, Tetris-launch route (4 Tetrises after
 * full charge), spin-launch route (2 spins after full charge),
 * spin-intercept priority during incoming threat, and reset on launch.
 *
 * <p>Run: {@code java -cp target\classes com.tetris.mab.sim.MabSimplifiedCoreProbe}
 */
public final class MabSimplifiedCoreProbe {

    private static int failed;

    public static void main(String[] args) {
        System.out.println("=== MAB Simplified Core Probe ===");

        // 1. Charge values via direct calculator.
        int chargeTetris     = com.tetris.mab.clear.MabChargeCalculator
                .compute(4, false, false, false, 0, true);
        int chargeSpinDouble = com.tetris.mab.clear.MabChargeCalculator
                .compute(2, true,  false, false, 0, false);
        int chargeSpinTriple = com.tetris.mab.clear.MabChargeCalculator
                .compute(3, true,  false, false, 0, false);
        check("chargeTetris",     chargeTetris,     8);
        check("chargeSpinDouble", chargeSpinDouble, 17);
        check("chargeSpinTriple", chargeSpinTriple, 25);

        // 2. Charge fills to required, ready becomes true.
        boolean readyAtFullCharge = checkReadyAtFullCharge();
        System.out.println("readyAtFullCharge=" + readyAtFullCharge);
        if (!readyAtFullCharge) failed++;

        // 3. After full charge, 4 Tetrises fire launch.
        boolean launchAfter4Tetrises = checkLaunchAfterTetrises();
        System.out.println("launchAfter4Tetrises=" + launchAfter4Tetrises);
        if (!launchAfter4Tetrises) failed++;

        // 4. After full charge, 2 spins fire launch.
        boolean launchAfter2Spins = checkLaunchAfterSpins();
        System.out.println("launchAfter2Spins=" + launchAfter2Spins);
        if (!launchAfter2Spins) failed++;

        // 5. Intercept priority over launch progress.
        boolean interceptPriority = checkInterceptPriority();
        System.out.println("interceptPriority=" + interceptPriority);
        if (!interceptPriority) failed++;

        // 6. Reset after launch.
        boolean resetAfterLaunch = checkResetAfterLaunch();
        System.out.println("resetAfterLaunch=" + resetAfterLaunch);
        if (!resetAfterLaunch) failed++;

        // 7. PvE AI must not bypass the same launch route.
        boolean aiNoLaunchBypass = checkAiDoesNotBypassLaunchRoute();
        System.out.println("aiNoLaunchBypass=" + aiNoLaunchBypass);
        if (!aiNoLaunchBypass) failed++;

        boolean success = failed == 0;
        System.out.println("success=" + success);
        if (!success) System.exit(1);
    }

    private static MutuallyAssuredBlocksMatch newMatch() {
        MutuallyAssuredBlocksMatch m = MutuallyAssuredBlocksMatch.createLocalPvpShared(
                new GameState(0), new GameState(0), MatchDifficulty.NORMAL, 42L);
        m.startMatch();
        return m;
    }

    private static boolean checkReadyAtFullCharge() {
        MutuallyAssuredBlocksMatch m = newMatch();
        ParticipantState p = m.getParticipant(ParticipantId.PLAYER_A);
        // Pump enough Tetrises to fill charge.
        for (int i = 0; i < 25 && !p.getSimplifiedState().nukeReady(); i++) {
            m.debugSimplifiedFeedClear(ParticipantId.PLAYER_A, 4, false, false, false, 0);
        }
        return p.getSimplifiedState().nukeReady();
    }

    private static boolean checkLaunchAfterTetrises() {
        MutuallyAssuredBlocksMatch m = newMatch();
        ParticipantState p = m.getParticipant(ParticipantId.PLAYER_A);
        // Fill charge with non-Tetris clears so launch progress stays at 0.
        for (int i = 0; i < 60 && !p.getSimplifiedState().nukeReady(); i++) {
            m.debugSimplifiedFeedClear(ParticipantId.PLAYER_A, 2, false, false, false, 0);
        }
        if (!p.getSimplifiedState().nukeReady()) return false;
        int activeBefore = p.getActiveLaunches().size();
        // Fire 4 Tetrises.
        for (int i = 0; i < 4; i++) {
            m.debugSimplifiedFeedClear(ParticipantId.PLAYER_A, 4, false, false, false, 0);
        }
        int activeAfter = p.getActiveLaunches().size();
        return activeAfter > activeBefore
                && p.getSimplifiedState().lastStrategicTrigger()
                        == MabSimplifiedStrategicState.LastTrigger.LAUNCH_FIRED_TETRIS;
    }

    private static boolean checkLaunchAfterSpins() {
        MutuallyAssuredBlocksMatch m = newMatch();
        ParticipantState p = m.getParticipant(ParticipantId.PLAYER_A);
        for (int i = 0; i < 60 && !p.getSimplifiedState().nukeReady(); i++) {
            m.debugSimplifiedFeedClear(ParticipantId.PLAYER_A, 2, false, false, false, 0);
        }
        if (!p.getSimplifiedState().nukeReady()) return false;
        int activeBefore = p.getActiveLaunches().size();
        // Fire 2 spin doubles.
        for (int i = 0; i < 2; i++) {
            m.debugSimplifiedFeedClear(ParticipantId.PLAYER_A, 2, true, false, false, 0);
        }
        int activeAfter = p.getActiveLaunches().size();
        return activeAfter > activeBefore
                && p.getSimplifiedState().lastStrategicTrigger()
                        == MabSimplifiedStrategicState.LastTrigger.LAUNCH_FIRED_SPIN;
    }

    private static boolean checkInterceptPriority() {
        MutuallyAssuredBlocksMatch m = newMatch();
        ParticipantState p = m.getParticipant(ParticipantId.PLAYER_A);
        // Fill charge so spin would normally count toward launch progress.
        for (int i = 0; i < 60 && !p.getSimplifiedState().nukeReady(); i++) {
            m.debugSimplifiedFeedClear(ParticipantId.PLAYER_A, 2, false, false, false, 0);
        }
        if (!p.getSimplifiedState().nukeReady()) return false;
        // Inject incoming threat for A.
        m.debugInjectIncomingThreatForTesting(ParticipantId.PLAYER_A);
        int spinProgressBefore = p.getSimplifiedState().launchSpinProgress();
        // Spin during threat: must intercept, must NOT count toward launch.
        m.debugSimplifiedFeedClear(ParticipantId.PLAYER_A, 2, true, false, false, 0);
        boolean spinProgressUnchanged =
                p.getSimplifiedState().launchSpinProgress() == spinProgressBefore;
        boolean triggeredIntercept =
                p.getSimplifiedState().lastStrategicTrigger()
                        == MabSimplifiedStrategicState.LastTrigger.SPIN_INTERCEPT;
        return spinProgressUnchanged && triggeredIntercept;
    }

    private static boolean checkResetAfterLaunch() {
        MutuallyAssuredBlocksMatch m = newMatch();
        ParticipantState p = m.getParticipant(ParticipantId.PLAYER_A);
        for (int i = 0; i < 60 && !p.getSimplifiedState().nukeReady(); i++) {
            m.debugSimplifiedFeedClear(ParticipantId.PLAYER_A, 2, false, false, false, 0);
        }
        if (!p.getSimplifiedState().nukeReady()) return false;
        for (int i = 0; i < 4; i++) {
            m.debugSimplifiedFeedClear(ParticipantId.PLAYER_A, 4, false, false, false, 0);
        }
        MabSimplifiedStrategicState s = p.getSimplifiedState();
        return !s.nukeReady()
                && s.launchTetrisProgress() == 0
                && s.launchSpinProgress() == 0
                && s.chargeCurrent() == 0;
    }

    private static boolean checkAiDoesNotBypassLaunchRoute() {
        MutuallyAssuredBlocksMatch m = newMatch();
        ParticipantState p = m.getParticipant(ParticipantId.PLAYER_B);
        for (int i = 0; i < 60 && !p.getSimplifiedState().nukeReady(); i++) {
            m.debugSimplifiedFeedClear(ParticipantId.PLAYER_B, 2, false, false, false, 0);
        }
        if (!p.getSimplifiedState().nukeReady()) return false;

        int activeBefore = p.getActiveLaunches().size();
        com.tetris.mab.ai.MabAiDriver ai = new com.tetris.mab.ai.MabAiDriver(
                m,
                ParticipantId.PLAYER_B,
                com.tetris.mab.ai.MabAiArchetype.BALANCED,
                com.tetris.mab.ai.MabAiDifficulty.DEBUG,
                com.tetris.mab.balance.MabBalanceProfiles.debugFast());
        ai.setAdvanceHiddenClock(false);
        ai.setEnabled(true);
        ai.tick();

        boolean didNotDirectLaunch = p.getActiveLaunches().size() == activeBefore;
        for (int i = 0; i < 4; i++) {
            m.debugSimplifiedFeedClear(ParticipantId.PLAYER_B, 4, false, false, false, 0);
        }
        boolean routeStillLaunches = p.getActiveLaunches().size() > activeBefore
                && p.getSimplifiedState().lastStrategicTrigger()
                        == MabSimplifiedStrategicState.LastTrigger.LAUNCH_FIRED_TETRIS;
        return didNotDirectLaunch && routeStillLaunches;
    }

    private static void check(String label, int actual, int expected) {
        boolean ok = actual == expected;
        System.out.println(label + "=" + actual + (ok ? " ok" : " (expected " + expected + ")"));
        if (!ok) failed++;
    }

    private MabSimplifiedCoreProbe() {}
}
