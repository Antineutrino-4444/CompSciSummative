package com.tetris.mab.nuke;

/**
 * How a nuke damages an enemy silo (hardening / launch-security).
 * Schema only; silo-damage resolution is not implemented in Step 3.
 */
public record SiloDamageProfile(
        int siloDamagePower,
        boolean canDamageSilo,
        boolean targetsHardening,
        boolean targetsLaunchSecurity) {
}
