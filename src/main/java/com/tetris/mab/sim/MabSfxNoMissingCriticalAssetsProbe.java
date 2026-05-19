package com.tetris.mab.sim;

import com.tetris.audio.SfxAssetResolver;
import com.tetris.audio.SoundEffect;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Asserts the subset of effects the engine relies on at runtime are present
 * as {@code *-converted.wav} files. A failure here means a real gameplay
 * cue will silently no-op instead of producing sound.
 */
public final class MabSfxNoMissingCriticalAssetsProbe {

    private static int failed;

    private static final List<SoundEffect> CRITICAL = List.of(
            // Movement / control
            SoundEffect.MOVE, SoundEffect.ROTATE, SoundEffect.SIDEHIT,
            SoundEffect.SOFTDROP, SoundEffect.HARDDROP, SoundEffect.HOLD,

            // Spawn voices
            SoundEffect.PIECE_I, SoundEffect.PIECE_J, SoundEffect.PIECE_L,
            SoundEffect.PIECE_O, SoundEffect.PIECE_S, SoundEffect.PIECE_T,
            SoundEffect.PIECE_Z,

            // Line clears
            SoundEffect.CLEAR_LINE, SoundEffect.CLEAR_QUAD, SoundEffect.CLEAR_SPIN,
            SoundEffect.CLEAR_BTB, SoundEffect.ALL_CLEAR,

            // B2B
            SoundEffect.BTB_1, SoundEffect.BTB_2, SoundEffect.BTB_3,
            SoundEffect.BTB_BREAK,

            // Combos (full bench plus break)
            SoundEffect.COMBO_1, SoundEffect.COMBO_2, SoundEffect.COMBO_3,
            SoundEffect.COMBO_4, SoundEffect.COMBO_16,
            SoundEffect.COMBO_1_POWER, SoundEffect.COMBO_16_POWER,
            SoundEffect.COMBO_BREAK,

            // Countdown
            SoundEffect.COUNTDOWN5, SoundEffect.COUNTDOWN4, SoundEffect.COUNTDOWN3,
            SoundEffect.COUNTDOWN2, SoundEffect.COUNTDOWN1, SoundEffect.GO,

            // Garbage / damage
            SoundEffect.GARBAGE_WINDUP_1, SoundEffect.GARBAGE_WINDUP_2,
            SoundEffect.GARBAGE_WINDUP_3, SoundEffect.GARBAGE_WINDUP_4,
            SoundEffect.GARBAGE_IN_SMALL, SoundEffect.GARBAGE_IN_MEDIUM,
            SoundEffect.GARBAGE_IN_LARGE, SoundEffect.GARBAGE_OUT_SMALL,
            SoundEffect.GARBAGE_OUT_MEDIUM, SoundEffect.GARBAGE_OUT_LARGE,
            SoundEffect.GARBAGE_RISE, SoundEffect.GARBAGE_SMASH,
            SoundEffect.DAMAGE_SMALL, SoundEffect.DAMAGE_MEDIUM,
            SoundEffect.DAMAGE_LARGE, SoundEffect.DAMAGE_ALERT,

            // Menu / UI
            SoundEffect.MENU_HOVER, SoundEffect.MENU_TAP, SoundEffect.MENU_CLICK,
            SoundEffect.MENU_CONFIRM, SoundEffect.MENU_BACK,
            SoundEffect.MENU_HIT_1, SoundEffect.MENU_HIT_2, SoundEffect.MENU_HIT_3,

            // Spin / level-up / topout
            SoundEffect.SPIN, SoundEffect.SPIN_END,
            SoundEffect.ZENITH_LEVELUP_A, SoundEffect.ZENITH_LEVELUP_AHALFSHARP,
            SoundEffect.ZENITH_LEVELUP_B, SoundEffect.ZENITH_LEVELUP_C,
            SoundEffect.ZENITH_LEVELUP_E, SoundEffect.ZENITH_LEVELUP_FSHARP,
            SoundEffect.ZENITH_LEVELUP_G,
            SoundEffect.TOPOUT
    );

    private MabSfxNoMissingCriticalAssetsProbe() {}

    public static void main(String[] args) {
        System.out.println("=== MAB SFX No Missing Critical Assets Probe ===");
        Path repoSfx = Paths.get("sfx").toAbsolutePath().normalize();
        SfxAssetResolver resolver = new SfxAssetResolver(repoSfx);

        for (SoundEffect effect : CRITICAL) {
            Path p = resolver.resolve(effect);
            check("present." + effect.assetName(),
                    p != null && p.toString().endsWith("-converted.wav"));
        }

        // Retired IDs that must NOT be referenced anywhere.
        check("retired.b2bcharge_1", resolver.resolve("b2bcharge_1") == null);
        check("retired.b2bcharge_2", resolver.resolve("b2bcharge_2") == null);
        check("retired.ihs", resolver.resolve("ihs") == null);
        check("retired.irs", resolver.resolve("irs") == null);
        check("retired.losestock", resolver.resolve("losestock") == null);
        check("retired.hyperalert", resolver.resolve("hyperalert") == null);
        check("retired.zenith_upspeed_1", resolver.resolve("zenith_upspeed_1") == null);
        check("retired.impact", resolver.resolve("impact") == null);
        check("retired.hit", resolver.resolve("hit") == null);

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
