package com.tetris.mab.sim;

import com.tetris.mab.ParticipantId;
import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MatchEventLogEntry;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.clear.MabClearResult;
import com.tetris.mab.clear.MabClearResult.SpinKind;
import com.tetris.mab.clear.MabSimplifiedStrategicState;
import com.tetris.mab.upgrade.draft.MabUpgradeCard;
import com.tetris.mab.upgrade.draft.MabUpgradeDraftRegistry;
import com.tetris.mab.upgrade.draft.MabUpgradeEffectResolver;
import com.tetris.mab.upgrade.draft.MabUpgradeInventory;
import com.tetris.model.Board;
import com.tetris.model.GameState;

import java.awt.Color;

/**
 * Step 24 \u2014 verifies the upgrade effect resolver: charge modifiers,
 * route targets, intercept/defense deltas, and tempo refunds.
 */
public final class MabUpgradeEffectProbe {

    private static int failed;
    private static MabUpgradeDraftRegistry reg;

    public static void main(String[] args) {
        System.out.println("=== MAB Upgrade Effect Probe ===");
        reg = new MabUpgradeDraftRegistry();

        // Synthetic Tetris clear (B2B = false, base = 8).
        MabClearResult tetris = new MabClearResult(
                ParticipantId.PLAYER_A, 4, SpinKind.NONE,
                false, false, 0, true, 8, "TETRIS");
        // Synthetic B2B Tetris (B2B = true, base = 8).
        MabClearResult b2bTetris = new MabClearResult(
                ParticipantId.PLAYER_A, 4, SpinKind.NONE,
                false, true, 1, true, 8, "B2B TETRIS");

        // 1. Efficient Reactor: +1 per stack.
        MabUpgradeInventory inv1 = new MabUpgradeInventory();
        inv1.addUpgrade(reg.getById("efficient_reactor"));
        int er1 = MabUpgradeEffectResolver.applyChargeModifiers(inv1, tetris, 8);
        boolean efficientReactor1 = er1 == 9;
        inv1.addUpgrade(reg.getById("efficient_reactor"));
        int er2 = MabUpgradeEffectResolver.applyChargeModifiers(inv1, tetris, 8);
        boolean efficientReactor2 = er2 == 10;
        check("efficientReactor", efficientReactor1 && efficientReactor2);

        // 2. B2B Amplifier: 8 * (1.40 / 1.25) = 8 * 1.12 = ~9.
        MabUpgradeInventory inv2 = new MabUpgradeInventory();
        inv2.addUpgrade(reg.getById("b2b_amplifier"));
        int b2bResult = MabUpgradeEffectResolver.applyChargeModifiers(inv2, b2bTetris, 8);
        boolean b2bAmplifier = b2bResult == 9;
        check("b2bAmplifier", b2bAmplifier);

        // 3. Fast Fuse: route_tetris_minus1 sets target to 3.
        MabUpgradeInventory inv3 = new MabUpgradeInventory();
        inv3.addUpgrade(reg.getById("fast_fuse"));
        int target = MabUpgradeEffectResolver.getTetrisRouteTarget(inv3);
        boolean fastFuse = target == 3;
        check("fastFuse", fastFuse);

        // 4. Spin Launch Crew: keeps 1 pip after launch.
        MabUpgradeInventory inv4 = new MabUpgradeInventory();
        inv4.addUpgrade(reg.getById("spin_launch_crew"));
        boolean spinKeep = MabUpgradeEffectResolver.spinRouteKeepsOnePipAfterLaunch(inv4);
        check("spinLaunchCrew", spinKeep);

        // 5. Intercept crews: defense_intercept_str +1.
        MabUpgradeInventory inv5 = new MabUpgradeInventory();
        inv5.addUpgrade(reg.getById("intercept_crews"));
        int icDelta = MabUpgradeEffectResolver.interceptStrengthDelta(inv5);
        boolean interceptCrews = icDelta == 1;
        check("interceptCrews", interceptCrews);

        // 6. Shelters: defense_shelters mitigation = 1 (when stack in upper 1/3).
        MabUpgradeInventory inv6 = new MabUpgradeInventory();
        inv6.addUpgrade(reg.getById("shelters"));
        int mit = MabUpgradeEffectResolver.incomingGarbageMitigation(inv6, true, false);
        boolean shelters = mit >= 1;
        check("shelters", shelters);

        // 7. Rapid Assembly: refund 10 after launch.
        MabUpgradeInventory inv7 = new MabUpgradeInventory();
        inv7.addUpgrade(reg.getById("rapid_assembly"));
        int refund = MabUpgradeEffectResolver.onLaunchFiredChargeRefund(inv7);
        boolean rapidAssembly = refund == 10;
        check("rapidAssembly", rapidAssembly);

        // 8. Retaliation Doctrine: +20 charge on impact taken.
        MabUpgradeInventory inv8 = new MabUpgradeInventory();
        inv8.addUpgrade(reg.getById("retaliation_doctrine"));
        int retCharge = MabUpgradeEffectResolver.onImpactTakenCharge(inv8);
        boolean retaliationDoctrine = retCharge == 20;
        check("retaliationDoctrine", retaliationDoctrine);

        // 9. syncSimplifiedStateConfig pushes targets onto state.
        MabSimplifiedStrategicState state = new MabSimplifiedStrategicState();
        MabUpgradeInventory inv9 = new MabUpgradeInventory();
        inv9.addUpgrade(reg.getById("fast_fuse"));
        inv9.addUpgrade(reg.getById("spin_launch_crew"));
        MabUpgradeEffectResolver.syncSimplifiedStateConfig(inv9, state);
        boolean stateSync = state.launchTetrisGoal() == 3
                && state.launchSpinGoal() == 2;
        check("stateSync", stateSync);

        // 10. Empty inventory -> no modification.
        MabUpgradeInventory empty = new MabUpgradeInventory();
        int emptyCharge = MabUpgradeEffectResolver.applyChargeModifiers(empty, tetris, 8);
        boolean noOpEmpty = emptyCharge == 8
                && MabUpgradeEffectResolver.getTetrisRouteTarget(empty) == 4
                && MabUpgradeEffectResolver.onLaunchFiredChargeRefund(empty) == 0;
        check("noOpEmpty", noOpEmpty);

        // 11. Dead Hand fatal-impact prevention fires once, then stops.
        boolean deadHandOnce = verifyDeadHandOnce();
        check("deadHandOnce", deadHandOnce);

        boolean success = failed == 0;
        System.out.println("success=" + success);
        if (!success) System.exit(1);
    }

    private static void check(String name, boolean ok) {
        System.out.println(name + "=" + ok);
        if (!ok) failed++;
    }

    private static boolean verifyDeadHandOnce() {
        MutuallyAssuredBlocksMatch m = MutuallyAssuredBlocksMatch.createPveShared(
                new GameState(0), new GameState(0), MatchDifficulty.NORMAL, 777L);
        m.startMatch();
        var defender = m.getParticipant(ParticipantId.PLAYER_A);
        defender.getUpgradeInventory().addUpgrade(reg.getById("dead_hand_protocol"));
        fillVisibleStack(defender.getGameState().getBoard(), 19);

        int before = countEvents(m, "MAB_DEAD_HAND_TRIGGERED");
        resolveOneDebugImpact(m);
        boolean firstUsed = defender.isDeadHandUsed()
                && countEvents(m, "MAB_DEAD_HAND_TRIGGERED") == before + 1;
        int afterFirst = countEvents(m, "MAB_DEAD_HAND_TRIGGERED");
        resolveOneDebugImpact(m);
        boolean secondNotPreventedAgain = countEvents(m, "MAB_DEAD_HAND_TRIGGERED") == afterFirst
                && !m.getPendingImpactWaves().isEmpty();
        return firstUsed && secondNotPreventedAgain;
    }

    private static void resolveOneDebugImpact(MutuallyAssuredBlocksMatch m) {
        m.debugStartLaunch(ParticipantId.PLAYER_B);
        m.debugAdvanceStrategicClockOnly(ParticipantId.PLAYER_B, 16);
        m.debugAdvanceStrategicClockOnly(ParticipantId.PLAYER_A, 16);
        m.resolveAllImpactReady();
    }

    private static void fillVisibleStack(Board board, int rows) {
        int clamped = Math.max(0, Math.min(rows, Board.VISIBLE_HEIGHT));
        for (int y = 0; y < clamped; y++) {
            int row = Board.TOTAL_HEIGHT - 1 - y;
            for (int x = 0; x < Board.WIDTH; x++) {
                board.setCell(x, row, Color.DARK_GRAY);
            }
        }
    }

    private static int countEvents(MutuallyAssuredBlocksMatch m, String type) {
        int n = 0;
        for (MatchEventLogEntry e : m.getEventLog()) {
            if (type.equals(e.eventType())) n++;
        }
        return n;
    }
}
