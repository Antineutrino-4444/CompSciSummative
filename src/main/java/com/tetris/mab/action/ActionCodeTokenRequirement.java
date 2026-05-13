package com.tetris.mab.action;

/**
 * One required token in an action-code sequence.
 *
 * <p>Three flavors of token are supported:
 * <ol>
 *   <li><b>Normal replaceable</b> ({@code spinMayReplace=true},
 *       {@code confirmationToken=false}) — an ordinary line-clear
 *       requirement that an eligible spin wildcard may also satisfy.
 *       Most action-code positions use this.</li>
 *   <li><b>Anchored</b> ({@code spinMayReplace=false},
 *       {@code confirmationToken=false}) — still an ordinary line-clear
 *       requirement, but spins cannot replace it; the actual clear's
 *       line count must equal {@code requiredLineCount} exactly.
 *       Use sparingly to break ambiguity between same-length codes.</li>
 *   <li><b>Hard confirm</b> ({@code spinMayReplace=false},
 *       {@code confirmationToken=true}) — a hard 4-line clear that
 *       confirms the action; spins cannot replace it. Created via
 *       {@link #hardConfirmFour()}.</li>
 * </ol>
 */
public record ActionCodeTokenRequirement(int requiredLineCount,
                                         boolean spinMayReplace,
                                         boolean confirmationToken) {

    public ActionCodeTokenRequirement {
        if (requiredLineCount < 1 || requiredLineCount > 4) {
            throw new IllegalArgumentException(
                    "requiredLineCount must be 1..4, got " + requiredLineCount);
        }
        if (confirmationToken && spinMayReplace) {
            throw new IllegalArgumentException(
                    "confirmation tokens cannot be spin-replaceable");
        }
    }

    /** Normal token: spins may replace, not a confirmation token. */
    public static ActionCodeTokenRequirement normal(int requiredLineCount) {
        return new ActionCodeTokenRequirement(requiredLineCount, true, false);
    }

    /** Anchored token: ordinary clear of the exact requiredLineCount; spins cannot replace it. */
    public static ActionCodeTokenRequirement anchored(int requiredLineCount) {
        return new ActionCodeTokenRequirement(requiredLineCount, false, false);
    }

    /** Hard 4-line confirmation token: spins cannot replace it. */
    public static ActionCodeTokenRequirement hardConfirmFour() {
        return new ActionCodeTokenRequirement(4, false, true);
    }

    /** True iff this is an anchored token (non-spin, non-confirmation). */
    public boolean isAnchored() {
        return !spinMayReplace && !confirmationToken;
    }

    public String toDebugString() {
        if (confirmationToken) return "C" + requiredLineCount;
        if (isAnchored()) return "A" + requiredLineCount;
        return Integer.toString(requiredLineCount);
    }
}
