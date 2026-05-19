package com.tetris.mab.sim;

import com.tetris.audio.SfxAssetResolver;
import com.tetris.audio.SoundEffect;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Verifies that every logical {@link SoundEffect} ID resolves to a real
 * {@code *-converted.wav} file on disk and that retired asset bases never
 * resolve, even if a stale file is shipped.
 */
public final class MabSfxAssetResolverProbe {

    private static int failed;

    private MabSfxAssetResolverProbe() {}

    public static void main(String[] args) {
        System.out.println("=== MAB SFX Asset Resolver Probe ===");
        Path repoSfx = Paths.get("sfx").toAbsolutePath().normalize();
        SfxAssetResolver resolver = new SfxAssetResolver(repoSfx);

        int total = SoundEffect.values().length;
        int resolved = 0;
        for (SoundEffect effect : SoundEffect.values()) {
            Path p = resolver.resolve(effect);
            boolean ok = p != null && p.toString().endsWith("-converted.wav");
            check("resolve." + effect.assetName(), ok);
            if (ok) resolved++;
        }
        check("resolve.allKnownIdsHaveFiles", resolved == total);

        // Deleted assets must never resolve.
        String[] retired = {
                "b2bcharge_1", "b2bcharge_2",
                "ihs", "irs", "losestock", "hyperalert",
                "zenith_upspeed_1", "zenith_upspeed_2",
                "impact", "hit"
        };
        for (String name : retired) {
            check("retired.deleted." + name, resolver.resolve(name) == null);
            check("retired.flagged." + name, SfxAssetResolver.isDeleted(name));
        }

        // Names that *contain* a deleted prefix as a substring but are not
        // deleted themselves should still resolve normally — e.g. "spin"
        // must not be flagged just because "spin" was retired (it wasn't).
        check("nonRetired.spin", !SfxAssetResolver.isDeleted("spin"));
        check("nonRetired.hold", !SfxAssetResolver.isDeleted("hold"));
        check("nonRetired.harddrop", !SfxAssetResolver.isDeleted("harddrop"));

        // Filenames must always carry the converted suffix.
        check("suffix.constant", SfxAssetResolver.CONVERTED_SUFFIX.equals("-converted.wav"));

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
