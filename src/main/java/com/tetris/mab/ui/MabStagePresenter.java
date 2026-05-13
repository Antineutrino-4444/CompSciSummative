package com.tetris.mab.ui;

import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ParticipantState;
import com.tetris.mab.clear.MabSimplifiedStrategicState;

/**
 * Step 22 \u2014 produces a {@link Snapshot} describing what the player
 * should see on the dramatic stage strip right now. Pure read-only.
 */
public final class MabStagePresenter {

    /** Immutable view-model bundle for {@link MabStageStripPanel}. */
    public static final class Snapshot {
        public final MabStage stage;
        public final String   subtitle;
        public final String   progressText;
        public final String   ctaText;
        /** 0..1 progress for the primary bar, or {@code -1} if N/A. */
        public final double   progressFraction;

        public Snapshot(MabStage stage, String subtitle,
                        String progressText, String ctaText,
                        double progressFraction) {
            this.stage = stage;
            this.subtitle = subtitle == null ? "" : subtitle;
            this.progressText = progressText == null ? "" : progressText;
            this.ctaText = ctaText == null ? "" : ctaText;
            this.progressFraction = progressFraction;
        }
    }

    public static Snapshot present(MutuallyAssuredBlocksMatch match,
                                   ParticipantId humanId) {
        if (match == null || humanId == null) {
            return new Snapshot(MabStage.BUILD_CHARGE, "", "", "", -1);
        }
        ParticipantState p = match.getParticipant(humanId);
        if (p == null) {
            return new Snapshot(MabStage.BUILD_CHARGE, "", "", "", -1);
        }
        MabSimplifiedStrategicState s = p.getSimplifiedState();

        // Highest priority: match over.
        if (match.isGameOver()) {
            return new Snapshot(MabStage.MATCH_OVER,
                    "Result panel below",
                    "", "Restart or back to menu", -1);
        }

        // Incoming threat (defender's perspective).
        int incoming = match.countLiveIncomingThreats(humanId);
        int impactReady = match.countImpactReadyThreats(humanId);
        if (incoming > 0) {
            return new Snapshot(MabStage.INCOMING_THREAT,
                    incoming + " hostile launch" + (incoming == 1 ? "" : "es") + " inbound",
                    "Spin to intercept",
                    "Land any spin (any piece) to shoot down a launch",
                    -1);
        }
        if (impactReady > 0) {
            return new Snapshot(MabStage.IMPACT_READY,
                    "Impact resolving",
                    "STABILIZE",
                    "Brace for incoming garbage",
                    -1);
        }

        // Launch fired by us, in flight (we have an active launch).
        int outgoing = 0;
        if (p.getActiveLaunches() != null) {
            for (com.tetris.mab.ActiveLaunchState l : p.getActiveLaunches()) {
                if (l.getPhase() != com.tetris.mab.launch.LaunchPhase.RESOLVED
                        && l.getPhase() != com.tetris.mab.launch.LaunchPhase.CANCELLED) {
                    outgoing++;
                }
            }
        }
        if (outgoing > 0) {
            return new Snapshot(MabStage.LAUNCH_FIRED,
                    outgoing + " of our launches in flight",
                    "Awaiting impact",
                    "Keep building \u2014 next charge starts now",
                    -1);
        }

        // Nuke ready: pick whichever route progresses faster.
        if (s.nukeReady()) {
            int tProg = s.launchTetrisProgress();
            int sProg = s.launchSpinProgress();
            int tGoal = Math.max(1, s.launchTetrisGoal());
            int sGoal = Math.max(1, s.launchSpinGoal());
            double tFrac = (double) tProg / tGoal;
            double sFrac = (double) sProg / sGoal;
            if (sFrac > tFrac) {
                return new Snapshot(MabStage.LAUNCH_SPIN_ROUTE,
                        "Spins fired: " + sProg + " / " + sGoal,
                        "SPIN ROUTE \u2022 " + sProg + "/" + sGoal,
                        "Land " + (sGoal - sProg) + " more spin(s) to launch",
                        sFrac);
            }
            if (tProg > 0 || tFrac >= sFrac) {
                return new Snapshot(MabStage.LAUNCH_TETRIS_ROUTE,
                        "Tetrises: " + tProg + " / " + tGoal,
                        "TETRIS ROUTE \u2022 " + tProg + "/" + tGoal,
                        "Land " + (tGoal - tProg) + " more Tetris(es) to launch",
                        tFrac);
            }
            return new Snapshot(MabStage.NUKE_READY,
                    "Choose a launch route",
                    "Charge full",
                    "Tetrises OR spins now fire your launch",
                    1.0);
        }

        // Default: building charge.
        int cur = s.chargeCurrent();
        int req = Math.max(1, s.chargeRequired());
        double frac = Math.max(0.0, Math.min(1.0, (double) cur / req));
        return new Snapshot(MabStage.BUILD_CHARGE,
                "Last clear: " + (s.lastClearText() == null || s.lastClearText().isEmpty()
                        ? "(none yet)" : s.lastClearText()),
                "CHARGE " + cur + " / " + req,
                "Clear lines or land spins to charge faster",
                frac);
    }

    private MabStagePresenter() {}
}
