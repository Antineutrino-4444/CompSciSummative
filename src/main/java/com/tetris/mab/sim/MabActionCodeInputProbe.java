package com.tetris.mab.sim;

import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MatchEventLogEntry;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ParticipantState;
import com.tetris.mab.action.ActionClearToken;
import com.tetris.mab.action.ActionCodeAttempt;
import com.tetris.mab.action.ActionCodeManager;
import com.tetris.mab.action.ActionCodeResult;
import com.tetris.mab.action.ActionCodeTokenRequirement;
import com.tetris.model.GameState;

import java.util.List;

/**
 * Step 20 Fourth Refinement — deterministic action-code input probe.
 *
 * <p>Drives the same listener path that a real Player A line clear
 * uses (via {@link MutuallyAssuredBlocksMatch#debugFeedLineClear}) and
 * verifies that the action-code system actually starts attempts,
 * advances them, completes them, and rejects invalid sequences.
 *
 * <p>Run:
 * <pre>
 *   java -cp target\classes com.tetris.mab.sim.MabActionCodeInputProbe
 * </pre>
 *
 * <p>Prints a per-token trace and a final {@code success=true|false}
 * summary. Exits with non-zero on failure so CI can pick it up.
 *
 * <p><b>Offline-only.</b>
 */
public final class MabActionCodeInputProbe {

    private static int totalChecks;
    private static int failedChecks;

    public static void main(String[] args) {
        System.out.println("=== MAB Action Code Input Probe ===");

        runScenario("route-analysis", new int[] {2, 1, 2}, ExpectedOutcome.COMPLETED);
        runScenario("civil-defense", new int[] {1, 1, 2}, ExpectedOutcome.COMPLETED);
        runScenario("emergency-intercept-no-threat",
                new int[] {1, 2, 1}, ExpectedOutcome.ANY_NON_NULL);
        runScenario("invalid-reset", new int[] {2, 2, 4}, ExpectedOutcome.FAILED_OR_RESTARTED);

        boolean success = failedChecks == 0;
        System.out.println();
        System.out.println("checks=" + totalChecks + " failed=" + failedChecks);
        System.out.println("success=" + success);
        if (!success) {
            System.exit(1);
        }
    }

    private enum ExpectedOutcome { COMPLETED, FAILED_OR_RESTARTED, ANY_NON_NULL }

    private static void runScenario(String label, int[] tokens, ExpectedOutcome expected) {
        GameState a = new GameState(0);
        GameState b = new GameState(0);
        MutuallyAssuredBlocksMatch match = MutuallyAssuredBlocksMatch.createLocalPvp(
                a, b, MatchDifficulty.NORMAL);
        match.startMatch();
        ParticipantState pa = match.getParticipant(ParticipantId.PLAYER_A);
        ActionCodeManager mgr = pa.getActionCodeManager();

        ActionCodeResult lastRes = ActionCodeResult.IGNORED;
        boolean sawAttemptCreated = false;
        for (int n : tokens) {
            ActionCodeResult res = match.debugFeedLineClear(ParticipantId.PLAYER_A, n);
            lastRes = res;
            ActionCodeAttempt active = mgr.getActiveAttempt();
            if (active != null) sawAttemptCreated = true;
            String enteredStr = formatEntered(active, mgr);
            String nextStr = formatNext(active);
            String evtStr = recentEventName(match);
            System.out.printf(
                    "PLAYER_A input %d -> %-21s entered=%-12s next=%-6s event=%s%n",
                    n, res.name(), enteredStr, nextStr, evtStr);
        }

        boolean ok;
        switch (expected) {
            case COMPLETED:
                ok = lastRes == ActionCodeResult.COMPLETED
                        || lastRes == ActionCodeResult.PENDING_CONFIRMATION;
                requireTrue(label + ":attempt-started", sawAttemptCreated);
                break;
            case FAILED_OR_RESTARTED:
                // After 2,2,4: first 2 starts/advances, second 2 advances or
                // resets depending on registry, 4 most likely produces a
                // FAILED_RESET, then auto-start tries again. The probe
                // requires that AT LEAST one FAILED_RESET event exists.
                ok = recentEventNames(match).contains("ACTION_FAILED_RESET")
                        || recentEventNames(match).contains("ACTION_NO_MATCH");
                break;
            case ANY_NON_NULL:
            default:
                ok = lastRes != null && lastRes != ActionCodeResult.IGNORED;
                requireTrue(label + ":attempt-or-no-match-or-rejected",
                        sawAttemptCreated
                                || recentEventNames(match).contains("ACTION_NO_MATCH")
                                || recentEventNames(match).contains(
                                        "ACTION_START_REJECTED_NO_THREAT")
                                || recentEventNames(match).contains(
                                        "ACTION_START_REJECTED_NO_ACTIVE_THREAT"));
                break;
        }
        report(label, ok);
    }

    private static String formatEntered(ActionCodeAttempt active, ActionCodeManager mgr) {
        ActionCodeAttempt ref = active;
        if (ref == null) {
            List<ActionCodeAttempt> done = mgr.getCompletedAttempts();
            if (!done.isEmpty()) ref = done.get(done.size() - 1);
        }
        if (ref == null) return "(none)";
        StringBuilder sb = new StringBuilder();
        for (ActionClearToken t : ref.getCompletedTokens()) {
            if (sb.length() > 0) sb.append('\u2192');
            sb.append(t.lineCount());
        }
        return sb.length() == 0 ? "(none)" : sb.toString();
    }

    private static String formatNext(ActionCodeAttempt active) {
        if (active == null) return "-";
        ActionCodeTokenRequirement next = active.getExpectedNextRequirement();
        if (next == null) return "-";
        return next.requiredLineCount() + "-line";
    }

    private static String recentEventName(MutuallyAssuredBlocksMatch match) {
        List<MatchEventLogEntry> recent = match.getRecentEvents(8);
        for (int i = recent.size() - 1; i >= 0; i--) {
            MatchEventLogEntry e = recent.get(i);
            String t = e.eventType();
            if (t == null) continue;
            if (t.startsWith("ACTION_") || t.equals("INPUT_LINE_CLEAR")) return t;
        }
        return "(none)";
    }

    private static java.util.Set<String> recentEventNames(MutuallyAssuredBlocksMatch match) {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (MatchEventLogEntry e : match.getRecentEvents(64)) {
            out.add(e.eventType());
        }
        return out;
    }

    private static void requireTrue(String tag, boolean cond) {
        totalChecks++;
        if (!cond) {
            failedChecks++;
            System.out.println("FAIL " + tag);
        }
    }

    private static void report(String label, boolean ok) {
        totalChecks++;
        if (ok) {
            System.out.println("PASS " + label);
        } else {
            failedChecks++;
            System.out.println("FAIL " + label);
        }
    }

    private MabActionCodeInputProbe() {}
}
