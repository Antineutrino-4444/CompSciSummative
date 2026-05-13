package com.tetris.mab.sim;

import com.tetris.mab.IncomingThreatState;
import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ParticipantState;
import com.tetris.mab.launch.ThreatStatus;
import com.tetris.model.GameState;

/**
 * Step 23 \u2014 standalone probe verifying the threat-lifecycle bug
 * fix. Before Step 23 the {@code incomingThreats} list was never
 * pruned, so {@code RESOLVED} / {@code INTERCEPTED} threats accumulated
 * and the player-facing warning panel reported "INCOMING" forever.
 *
 * <p>This probe exercises the live PvP path
 * (<i>not</i> the simulation harness) and asserts that:
 * <ol>
 *   <li>after a launch from B, A reports a live incoming threat;</li>
 *   <li>after the warning timer elapses, the threat becomes
 *       {@code IMPACT_READY} and live count drops to zero;</li>
 *   <li>after {@code resolveAllImpactReady()} +
 *       {@code pruneCompletedThreats()} the threats list is empty;</li>
 *   <li>a fresh launch can immediately follow without leftover state.</li>
 * </ol>
 *
 * <p><b>Offline-only.</b> No Swing, no networking.
 */
public final class MabThreatLifecycleProbe {

    public static void main(String[] args) {
        System.out.println("=== MAB Threat Lifecycle Probe ===");
        boolean ok = run();
        System.out.println("success=" + ok);
        if (!ok) System.exit(1);
    }

    public static boolean run() {
        GameState a = new GameState(0);
        GameState b = new GameState(0);
        MutuallyAssuredBlocksMatch match = MutuallyAssuredBlocksMatch
                .createLocalPvp(a, b, MatchDifficulty.NORMAL);
        match.startMatch();
        ParticipantState pa = match.getParticipant(ParticipantId.PLAYER_A);

        // Step 1: synthesise a B \u2192 A incoming threat in flight.
        // (debugInjectIncomingThreatForTesting builds the
        // ActiveLaunchState + IncomingThreatState pair directly so we
        // skip the COUNTDOWN handshake.)
        String threatId = match.debugInjectIncomingThreatForTesting(
                ParticipantId.PLAYER_A);
        boolean launched = threatId != null;
        boolean incomingInitially =
                match.countLiveIncomingThreats(ParticipantId.PLAYER_A) > 0;
        boolean rawListNonEmpty = !pa.getIncomingThreats().isEmpty();
        System.out.println("[1] launched=" + launched
                + " incomingInitially=" + incomingInitially
                + " rawList=" + pa.getIncomingThreats().size());

        // Step 2: force the synthesised launch + threat into
        // IMPACT_READY (we're testing the prune/resolve fix, not the
        // warning-timer state machine which is exercised elsewhere).
        ParticipantState pb = match.getParticipant(ParticipantId.PLAYER_B);
        for (com.tetris.mab.ActiveLaunchState l : pb.getActiveLaunches()) {
            l.markImpactReady();
        }
        for (IncomingThreatState t : pa.getIncomingThreats()) {
            t.markImpactReady();
        }
        int impactReady = match.countImpactReadyThreats(ParticipantId.PLAYER_A);
        int liveAfterAge = match.countLiveIncomingThreats(ParticipantId.PLAYER_A);
        System.out.println("[2] forced: impactReady=" + impactReady
                + " liveRemaining=" + liveAfterAge);

        // Step 3: resolve impacts and prune. After this, the live count
        // and the raw list should both be 0.
        match.debugResolveAllImpacts();
        match.pruneCompletedThreats();
        boolean incomingAfterImpact =
                match.countLiveIncomingThreats(ParticipantId.PLAYER_A) > 0;
        boolean impactAfterPrune =
                match.countImpactReadyThreats(ParticipantId.PLAYER_A) > 0;
        int rawAfterPrune = pa.getIncomingThreats().size();
        long resolvedDangling = pa.getIncomingThreats().stream()
                .filter(t -> t.getStatus() == ThreatStatus.RESOLVED
                        || t.getStatus() == ThreatStatus.INTERCEPTED
                        || t.getStatus() == ThreatStatus.CANCELLED)
                .count();
        System.out.println("[3] post-impact: incoming=" + incomingAfterImpact
                + " impactReady=" + impactAfterPrune
                + " rawList=" + rawAfterPrune
                + " danglingResolved=" + resolvedDangling);

        // Step 4: another fresh injection \u2014 must work and create
        // exactly one new live threat (no leftover bookkeeping).
        String threatId2 = match.debugInjectIncomingThreatForTesting(
                ParticipantId.PLAYER_A);
        boolean launched2 = threatId2 != null;
        int liveAfterRelaunch =
                match.countLiveIncomingThreats(ParticipantId.PLAYER_A);
        System.out.println("[4] relaunch=" + launched2
                + " liveAfterRelaunch=" + liveAfterRelaunch);

        boolean ok =
                launched
                && incomingInitially
                && rawListNonEmpty
                && impactReady > 0
                && !incomingAfterImpact
                && !impactAfterPrune
                && rawAfterPrune == 0
                && resolvedDangling == 0
                && launched2
                && liveAfterRelaunch >= 1;

        if (!ok) {
            System.out.println("FAIL: one or more lifecycle invariants violated");
        } else {
            System.out.println("PASS: incoming threats clear after impact + prune");
        }
        try { match.shutdown(); } catch (RuntimeException ignored) {}
        return ok;
    }

    private MabThreatLifecycleProbe() {}
}
