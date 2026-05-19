package com.tetris.mab.sim;

import com.tetris.mab.ActiveLaunchState;
import com.tetris.mab.IncomingThreatState;
import com.tetris.mab.MatchEventLogEntry;
import com.tetris.mab.MatchMode;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ParticipantState;
import com.tetris.mab.decoy.ActiveDecoyState;
import com.tetris.mab.launch.LaunchPhase;
import com.tetris.mab.launch.ThreatStatus;
import com.tetris.mab.upgrade.draft.MabUpgradeCard;
import com.tetris.mab.upgrade.draft.MabUpgradeDraftRegistry;
import com.tetris.mab.upgrade.draft.MabUpgradeEffectResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * Step 14 — sanity invariants for the MAB strategic layer. Returns a
 * list of human-readable failure messages; an empty list means all
 * checks passed.
 */
public final class MabSimulationInvariants {

    public List<String> check(MutuallyAssuredBlocksMatch match) {
        List<String> failures = new ArrayList<>();
        if (match == null) {
            failures.add("match is null");
            return failures;
        }
        ParticipantState a = match.getParticipant(ParticipantId.PLAYER_A);
        ParticipantState b = match.getParticipant(ParticipantId.PLAYER_B);
        if (a == null) failures.add("PLAYER_A missing");
        if (b == null) failures.add("PLAYER_B missing");

        if (a != null) checkParticipant(a, failures);
        if (b != null) checkParticipant(b, failures);

        if (match.getMatchMode() == MatchMode.PVP_LOCAL && !match.hasSharedPieceSequence()) {
            failures.add("local PvP match missing shared deterministic piece sequence");
        }
        checkUpgradeEffectTags(failures);

        // DEFCON range.
        int defcon = match.getDefconState().getLevel();
        if (defcon < 1 || defcon > 5) {
            failures.add("DEFCON out of range: " + defcon);
        }

        // Pending impact waves count >= 0 (always true for List.size(),
        // but we still verify the list is accessible without throwing).
        try {
            int pending = match.getPendingImpactWaves().size();
            if (pending < 0) failures.add("pending impact waves negative: " + pending);
        } catch (RuntimeException ex) {
            failures.add("pending impact waves inaccessible: " + ex.getMessage());
        }

        // Event-log monotonic sequence numbers.
        long previous = Long.MIN_VALUE;
        for (MatchEventLogEntry e : match.getEventLog()) {
            if (e.sequenceNumber() <= previous) {
                failures.add("event log sequence not strictly increasing at #"
                        + e.sequenceNumber());
                break;
            }
            previous = e.sequenceNumber();
        }
        return failures;
    }

    private static void checkParticipant(ParticipantState p, List<String> failures) {
        ParticipantId id = p.getId();
        int charge = p.getNukeBuildState().getCurrentBuildCharge();
        if (charge < 0) failures.add(id + " nuke charge negative: " + charge);

        int integrity = p.getSiloState().getIntegrity();
        if (integrity < 0 || integrity > 100) {
            failures.add(id + " silo integrity out of [0,100]: " + integrity);
        }

        int activeLaunches = p.getActiveLaunches().size();
        if (activeLaunches < 0) failures.add(id + " active launches negative");

        int incoming = p.getIncomingThreats().size();
        if (incoming < 0) failures.add(id + " incoming threats negative");

        int civChg = p.getCivilDefenseState().getActiveCharges();
        if (civChg < 0) failures.add(id + " civil defense charges negative: " + civChg);

        int pts = p.getUpgradeState().getUpgradePoints();
        if (pts < 0) failures.add(id + " upgrade points negative: " + pts);

        var strategic = p.getSimplifiedState();
        if (strategic.launchTetrisProgress() > strategic.launchTetrisGoal() + 1) {
            failures.add(id + " Tetris route progress exceeds target: "
                    + strategic.launchTetrisProgress() + "/" + strategic.launchTetrisGoal());
        }
        if (strategic.launchSpinProgress() > strategic.launchSpinGoal() + 1) {
            failures.add(id + " spin route progress exceeds target: "
                    + strategic.launchSpinProgress() + "/" + strategic.launchSpinGoal());
        }

        for (ActiveDecoyState d : p.getActiveDecoys()) {
            if (d.getDurationPiecesRemaining() < 0) {
                failures.add(id + " feint " + d.getDecoyId()
                        + " has negative remaining pieces: "
                        + d.getDurationPiecesRemaining());
            }
        }
        for (ActiveLaunchState l : p.getActiveLaunches()) {
            if (l.getPhase() == LaunchPhase.RESOLVED
                    || l.getPhase() == LaunchPhase.CANCELLED) {
                failures.add(id + " launch " + l.getLaunchId()
                        + " remains active after " + l.getPhase());
            }
        }
        for (IncomingThreatState t : p.getIncomingThreats()) {
            if (t.getStatus() == ThreatStatus.INTERCEPTED
                    || t.getStatus() == ThreatStatus.RESOLVED
                    || t.getStatus() == ThreatStatus.CANCELLED) {
                failures.add(id + " threat " + t.getThreatId()
                        + " remains live after " + t.getStatus());
            }
        }
    }

    private static void checkUpgradeEffectTags(List<String> failures) {
        MabUpgradeDraftRegistry registry = new MabUpgradeDraftRegistry();
        for (MabUpgradeCard card : registry.getAllCards()) {
            if (card.getEffectTags().isEmpty()) {
                failures.add("upgrade has no observed effect tags: " + card.getId());
            }
            for (String tag : card.getEffectTags()) {
                if (!MabUpgradeEffectResolver.isKnownEffectTag(tag)) {
                    failures.add("unknown upgrade effect tag: " + card.getId() + ":" + tag);
                }
                String lower = (card.getId() + " " + tag).toLowerCase();
                if (lower.contains("reroll")
                        || lower.contains("force_piece")
                        || lower.contains("shared_sequence")
                        || lower.contains("piece_source")
                        || lower.contains("bag")) {
                    failures.add("upgrade may mutate shared piece sequence: "
                            + card.getId() + ":" + tag);
                }
            }
        }
    }
}
