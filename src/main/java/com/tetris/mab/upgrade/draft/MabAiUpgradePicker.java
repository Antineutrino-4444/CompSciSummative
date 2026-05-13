package com.tetris.mab.upgrade.draft;

import com.tetris.mab.MatchDifficulty;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Step 24 \u2014 AI auto-picker for upgrade drafts. Stateless;
 * deterministic given the same draft + difficulty + seed.
 */
public final class MabAiUpgradePicker {

    private static final List<MabUpgradeCategory> CATEGORY_PRIORITY = List.of(
            MabUpgradeCategory.CHARGE,
            MabUpgradeCategory.DEFENSE,
            MabUpgradeCategory.TETRIS_ROUTE,
            MabUpgradeCategory.SPIN_ROUTE,
            MabUpgradeCategory.POWER,
            MabUpgradeCategory.TEMPO);

    private MabAiUpgradePicker() {}

    public static MabUpgradeCard pick(MabUpgradeDraft draft, MatchDifficulty difficulty, long seed) {
        if (draft == null) return null;
        List<MabUpgradeCard> choices = draft.getChoices();
        if (choices.isEmpty()) return null;
        Random rng = new Random(seed ^ ((long) draft.getLevel() * 0xD1B54A32D192ED03L));

        if (difficulty == MatchDifficulty.EASY) {
            // Random choice.
            return choices.get(rng.nextInt(choices.size()));
        }

        // Sort by (rarity desc, category priority asc, id asc) and take the
        // top. Hard / Doomsday gets the same priority but with a small
        // chance to follow synergy (here: prefer CHARGE over DEFENSE early).
        return choices.stream()
                .sorted(Comparator
                        .comparingInt((MabUpgradeCard c) -> -c.getRarity().ordinal())
                        .thenComparingInt(c -> CATEGORY_PRIORITY.indexOf(c.getCategory()))
                        .thenComparing(MabUpgradeCard::getId))
                .findFirst()
                .orElse(choices.get(0));
    }
}
