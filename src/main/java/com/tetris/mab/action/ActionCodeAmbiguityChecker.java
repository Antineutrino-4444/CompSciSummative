package com.tetris.mab.action;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Detects pairs of action-code definitions that could plausibly be
 * completed by the same player input under spin-wildcard rules.
 *
 * <p>Two definitions are flagged as ambiguous if all of the following hold:
 * <ul>
 *   <li>they have the same sequence length,</li>
 *   <li>they have the same confirmation mode,</li>
 *   <li>they belong to the same category, OR both action types report
 *       {@link ActionType#isLaunch()} == true,</li>
 *   <li>and at every position both required tokens are jointly
 *       satisfiable by some single {@link ActionClearToken} (see
 *       {@link #positionsCompatible(ActionCodeTokenRequirement, ActionCodeTokenRequirement)}).</li>
 * </ul>
 */
public final class ActionCodeAmbiguityChecker {

    private ActionCodeAmbiguityChecker() {}

    public static List<String> findAmbiguities(Collection<ActionCodeDefinition> definitions) {
        List<String> warnings = new ArrayList<>();
        if (definitions == null || definitions.size() < 2) return warnings;
        List<ActionCodeDefinition> list = new ArrayList<>(definitions);
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                ActionCodeDefinition a = list.get(i);
                ActionCodeDefinition b = list.get(j);
                if (mayCollide(a, b)) {
                    warnings.add("AMBIGUOUS: " + a.getId() + " " + sequenceString(a)
                            + " vs " + b.getId() + " " + sequenceString(b)
                            + " (spin wildcard could collapse them)");
                }
            }
        }
        return warnings;
    }

    private static boolean mayCollide(ActionCodeDefinition a, ActionCodeDefinition b) {
        if (a.sequenceLength() != b.sequenceLength()) return false;
        if (a.getConfirmationMode() != b.getConfirmationMode()) return false;

        boolean sameCategory = a.getCategory() == b.getCategory();
        boolean bothLaunches = a.getActionType().isLaunch() && b.getActionType().isLaunch();
        if (!sameCategory && !bothLaunches) return false;

        for (int i = 0; i < a.sequenceLength(); i++) {
            if (!positionsCompatible(a.getSequence().get(i), b.getSequence().get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * True iff some single {@link ActionClearToken} could satisfy both
     * required tokens (assuming the most permissive matching mode that
     * still respects spinMayReplace / confirmation / anchor semantics).
     */
    static boolean positionsCompatible(ActionCodeTokenRequirement x,
                                       ActionCodeTokenRequirement y) {
        // Confirmation tokens: only compatible with another confirmation
        // token of the same line count, because no spin can satisfy them
        // and an anchored/normal of a different line count cannot be
        // satisfied by an ordinary 4 (anchors require exact match).
        if (x.confirmationToken() || y.confirmationToken()) {
            if (x.confirmationToken() && y.confirmationToken()) {
                return x.requiredLineCount() == y.requiredLineCount();
            }
            ActionCodeTokenRequirement conf = x.confirmationToken() ? x : y;
            ActionCodeTokenRequirement other = x.confirmationToken() ? y : x;
            // Other side must accept an ordinary 4-line clear.
            if (other.isAnchored()) {
                return other.requiredLineCount() == conf.requiredLineCount();
            }
            // Normal replaceable: an ordinary 4 satisfies any 1..4 under
            // the most permissive mode (FLEXIBLE).
            return other.requiredLineCount() <= conf.requiredLineCount();
        }

        boolean xa = x.isAnchored();
        boolean ya = y.isAnchored();

        // Two anchored: only compatible if exactly the same line count.
        if (xa && ya) {
            return x.requiredLineCount() == y.requiredLineCount();
        }

        // Anchored vs normal replaceable: a single ordinary clear of
        // anchorVal must also satisfy the normal side under at least one
        // matching mode. FLEXIBLE accepts lineCount >= requiredLineCount,
        // so anchorVal >= normalVal is sufficient. (Spins satisfy the
        // normal side but never the anchor side, so spins do not help.)
        if (xa) {
            return x.requiredLineCount() >= y.requiredLineCount();
        }
        if (ya) {
            return y.requiredLineCount() >= x.requiredLineCount();
        }

        // Both normal replaceable: an eligible spin satisfies both at
        // any line count, and any sufficiently large ordinary clear
        // does so too under FLEXIBLE. Always compatible.
        return true;
    }

    private static String sequenceString(ActionCodeDefinition d) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < d.sequenceLength(); i++) {
            if (i > 0) sb.append(',');
            sb.append(d.getSequence().get(i).toDebugString());
        }
        return sb.append(']').toString();
    }
}
