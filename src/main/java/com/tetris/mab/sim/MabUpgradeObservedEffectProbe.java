package com.tetris.mab.sim;

import com.tetris.mab.upgrade.draft.MabUpgradeCard;
import com.tetris.mab.upgrade.draft.MabUpgradeDraftRegistry;
import com.tetris.mab.upgrade.draft.MabUpgradeEffectResolver;

/** Step 26 - verifies every drafted upgrade advertises an observed effect tag. */
public final class MabUpgradeObservedEffectProbe {

    private MabUpgradeObservedEffectProbe() {}

    public static void main(String[] args) {
        System.out.println("=== MAB Upgrade Observed Effect Probe ===");
        MabUpgradeDraftRegistry registry = new MabUpgradeDraftRegistry();
        boolean noMissingTags = true;
        boolean noUnknownTags = true;
        boolean noSequenceMutationTags = true;
        int activeCards = 0;
        for (MabUpgradeCard card : registry.getAllCards()) {
            if (card.getEffectTags().isEmpty()) {
                noMissingTags = false;
                System.out.println("missingEffectTags=" + card.getId());
            }
            if (card.isActiveCard()) activeCards++;
            for (String tag : card.getEffectTags()) {
                if (!MabUpgradeEffectResolver.isKnownEffectTag(tag)) {
                    noUnknownTags = false;
                    System.out.println("unknownEffectTag=" + card.getId() + ":" + tag);
                }
                String lower = (card.getId() + " " + tag).toLowerCase();
                if (lower.contains("bag")
                        || lower.contains("reroll")
                        || lower.contains("force_piece")
                        || lower.contains("piece_source")
                        || lower.contains("shared_sequence")) {
                    noSequenceMutationTags = false;
                }
            }
        }
        boolean activeCardsObserved = activeCards >= 2;
        boolean success = noMissingTags && noUnknownTags
                && noSequenceMutationTags && activeCardsObserved;

        report("registryCards", registry.getAllCards().size());
        report("noMissingTags", noMissingTags);
        report("noUnknownTags", noUnknownTags);
        report("noSequenceMutationTags", noSequenceMutationTags);
        report("activeCardsObserved", activeCardsObserved);
        report("success", success);
        if (!success) System.exit(1);
    }

    private static void report(String name, Object value) {
        System.out.println(name + "=" + value);
    }
}
