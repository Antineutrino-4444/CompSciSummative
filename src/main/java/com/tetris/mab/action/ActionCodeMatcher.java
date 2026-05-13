package com.tetris.mab.action;

/** Stateless helper that checks one actual token against one required token. */
public final class ActionCodeMatcher {

    private ActionCodeMatcher() {}

    public static boolean matches(ActionClearToken actual,
                                  ActionCodeTokenRequirement required,
                                  ActionCodeMatchMode matchMode,
                                  boolean strategicAction) {
        if (actual == null || required == null) return false;
        if (actual.lineCount() <= 0) return false;

        // Confirmation tokens (hard 4): must be an ordinary line clear
        // of exactly the required count, no spin allowed.
        if (required.confirmationToken()) {
            if (actual.spinKind() != SpinKind.NONE) return false;
            return actual.lineCount() == required.requiredLineCount();
        }

        // Anchored tokens: ordinary line clear of EXACTLY the required
        // count; spins cannot satisfy them, and even FLEXIBLE matching
        // does not let a higher line count substitute. This is what makes
        // anchors useful for breaking ambiguity between same-length codes.
        if (required.isAnchored()) {
            if (actual.spinKind() != SpinKind.NONE) return false;
            return actual.lineCount() == required.requiredLineCount();
        }

        // Spin wildcard short-circuit (normal replaceable token only).
        if (actual.isSpinWildcard() && required.spinMayReplace()) {
            return true;
        }

        // Ordinary line-clear matching.
        return switch (matchMode) {
            case STRICT -> actual.lineCount() == required.requiredLineCount();
            case FLEXIBLE -> actual.lineCount() >= required.requiredLineCount();
            case MOSTLY_FLEXIBLE -> strategicAction
                    ? actual.lineCount() == required.requiredLineCount()
                    : actual.lineCount() >= required.requiredLineCount();
        };
    }
}
