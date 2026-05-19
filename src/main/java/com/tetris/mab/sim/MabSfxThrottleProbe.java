package com.tetris.mab.sim;

import com.tetris.audio.SfxAssetResolver;
import com.tetris.audio.SoundEffect;
import com.tetris.audio.SoundEffectManager;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Verifies that throttle windows are set for the spam-prone effects
 * (move/softdrop/sidehit/damage_alert/garbage windup) and that consecutive
 * play() calls within the window are dropped.
 */
public final class MabSfxThrottleProbe {

    private static int failed;

    private MabSfxThrottleProbe() {}

    public static void main(String[] args) {
        System.out.println("=== MAB SFX Throttle Probe ===");
        Path repoSfx = Paths.get("sfx").toAbsolutePath().normalize();
        SoundEffectManager mgr = new SoundEffectManager(
                new SfxAssetResolver(repoSfx), false);

        // Each spam-prone ID must have a positive throttle window.
        assertThrottle(mgr, SoundEffect.MOVE);
        assertThrottle(mgr, SoundEffect.SOFTDROP);
        assertThrottle(mgr, SoundEffect.SIDEHIT);
        assertThrottle(mgr, SoundEffect.DAMAGE_ALERT);
        assertThrottle(mgr, SoundEffect.GARBAGE_WINDUP_1);
        assertThrottle(mgr, SoundEffect.GARBAGE_WINDUP_2);
        assertThrottle(mgr, SoundEffect.GARBAGE_WINDUP_3);
        assertThrottle(mgr, SoundEffect.GARBAGE_WINDUP_4);
        // Damage alert in particular needs a longer cool-down so it does not
        // chatter during sustained high-stack pressure.
        check("damageAlert.coolDownAtLeast500ms",
                mgr.getThrottleMs(SoundEffect.DAMAGE_ALERT) >= 500L);

        // Burst a single effect; only the first call should pass through.
        // Manager is in audio-disabled mode so play() bumps skippedCount on
        // each accepted call and droppedThrottledCount on each throttled
        // call. We exercise that here.
        mgr.setThrottleMs(SoundEffect.MOVE, 1000L);
        long accepted0 = mgr.getSkippedCount();
        long throttled0 = mgr.getDroppedThrottledCount();
        for (int i = 0; i < 25; i++) mgr.play(SoundEffect.MOVE);
        long accepted1 = mgr.getSkippedCount() - accepted0;
        long throttled1 = mgr.getDroppedThrottledCount() - throttled0;
        check("burst.exactlyOneAccepted", accepted1 == 1);
        check("burst.othersThrottled", throttled1 == 24);

        // Independent effects must not throttle each other.
        mgr.setThrottleMs(SoundEffect.ROTATE, 1000L);
        long throttledBase = mgr.getDroppedThrottledCount();
        mgr.play(SoundEffect.ROTATE);
        long throttledAfter = mgr.getDroppedThrottledCount();
        check("independence.differentIdsNoCrossThrottle",
                throttledAfter == throttledBase);

        // Clearing the throttle resumes pass-through.
        mgr.setThrottleMs(SoundEffect.MOVE, 0L);
        long accepted2 = mgr.getSkippedCount();
        mgr.play(SoundEffect.MOVE);
        mgr.play(SoundEffect.MOVE);
        check("clearThrottle.allAccepted",
                mgr.getSkippedCount() - accepted2 == 2);

        boolean ok = failed == 0;
        System.out.println("success=" + ok);
        if (!ok) System.exit(1);
    }

    private static void assertThrottle(SoundEffectManager mgr, SoundEffect effect) {
        long throttle = mgr.getThrottleMs(effect);
        check("throttle.set." + effect.assetName(), throttle > 0L);
    }

    private static void check(String name, boolean cond) {
        if (cond) {
            System.out.println("  pass " + name);
        } else {
            failed++;
            System.out.println("  FAIL " + name);
        }
    }
}
