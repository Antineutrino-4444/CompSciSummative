package com.tetris.mab.upgrade.draft;

/**
 * Result of a local active-doctrine command.
 */
public record MabActiveDoctrineUseResult(
        MabActiveDoctrineType type,
        boolean success,
        String title,
        String detail,
        String launchId,
        String threatId,
        int chargeSpent) {
}
