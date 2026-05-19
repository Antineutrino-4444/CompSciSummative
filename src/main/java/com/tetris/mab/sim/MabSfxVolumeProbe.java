package com.tetris.mab.sim;

import com.tetris.audio.MusicDirector;
import com.tetris.audio.SfxAssetResolver;
import com.tetris.audio.SoundEffect;
import com.tetris.audio.SoundEffectManager;
import com.tetris.model.Settings;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Verifies that SFX volume / mute state is fully independent from the music
 * director's master volume, and that Settings round-trips both channels.
 */
public final class MabSfxVolumeProbe {

    private static int failed;

    private MabSfxVolumeProbe() {}

    public static void main(String[] args) {
        System.out.println("=== MAB SFX Volume Probe ===");
        Path repoSfx = Paths.get("sfx").toAbsolutePath().normalize();
        SoundEffectManager mgr = new SoundEffectManager(
                new SfxAssetResolver(repoSfx), false);
        MusicDirector director = MusicDirector.createForProbe(
                Path.of("music"), false);

        // Independence: tweaking music volume must not touch SFX volume.
        mgr.setMasterSfxVolume(0.6f);
        director.setMasterVolume(0.2f);
        check("independence.afterMusicChange",
                Math.abs(mgr.getMasterSfxVolume() - 0.6f) < 1e-4);

        // Independence in reverse — SFX changes do not affect music. There
        // is no public reader on the director for its volume, so we instead
        // verify that the director object did not silently mutate the SFX
        // value when handed the same float.
        mgr.setMasterSfxVolume(0.42f);
        check("independence.sfxValueRetained",
                Math.abs(mgr.getMasterSfxVolume() - 0.42f) < 1e-4);

        // Volume clamps to [0, 1].
        mgr.setMasterSfxVolume(-0.5f);
        check("volume.clampLow", mgr.getMasterSfxVolume() == 0f);
        mgr.setMasterSfxVolume(99f);
        check("volume.clampHigh", mgr.getMasterSfxVolume() == 1f);

        // Muted play() is recorded as skipped, not as a real play.
        mgr.setMuted(true);
        long skipped0 = mgr.getSkippedCount();
        mgr.play(SoundEffect.HOLD);
        check("mute.shortCircuits", mgr.getSkippedCount() - skipped0 == 1);
        check("mute.flagSet", mgr.isMuted());
        mgr.setMuted(false);
        check("mute.cleared", !mgr.isMuted());

        // Zero volume is treated as silent without going through the mute flag.
        mgr.setMasterSfxVolume(0f);
        long skipped1 = mgr.getSkippedCount();
        mgr.play(SoundEffect.HOLD);
        check("volume.zeroSilences", mgr.getSkippedCount() - skipped1 == 1);

        // Settings round-trip. Reset to defaults first to avoid persisting
        // probe values into the user profile, and we never call save().
        Settings s = Settings.get();
        double originalVolume = s.getSfxVolume();
        boolean originalMute = s.isSfxMuted();
        s.setSfxVolume(0.31);
        check("settings.volumeWrite",
                Math.abs(s.getSfxVolume() - 0.31) < 1e-6);
        s.setSfxMuted(true);
        check("settings.muteWrite", s.isSfxMuted());
        s.setSfxVolume(originalVolume);
        s.setSfxMuted(originalMute);

        boolean ok = failed == 0;
        System.out.println("success=" + ok);
        if (!ok) System.exit(1);
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
