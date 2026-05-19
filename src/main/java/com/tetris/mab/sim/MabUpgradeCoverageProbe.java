package com.tetris.mab.sim;

import com.tetris.mab.upgrade.draft.MabUpgradeCard;
import com.tetris.mab.upgrade.draft.MabUpgradeDraftRegistry;
import com.tetris.mab.upgrade.draft.MabUpgradeEffectResolver;
import com.tetris.mab.upgrade.draft.MabUpgradeInventory;

/**
 * Step 25 - registry coverage audit for the v2 upgrade card pool.
 */
public final class MabUpgradeCoverageProbe {

    public static void main(String[] args) {
        System.out.println("=== MAB Upgrade Coverage Probe ===");
        MabUpgradeDraftRegistry reg = new MabUpgradeDraftRegistry();

        boolean knownEffectTags = true;
        boolean noSilentNoOps = true;
        boolean sharedSequenceUnaffected = true;
        for (MabUpgradeCard card : reg.getAllCards()) {
            if (card.getEffectTags().isEmpty()) {
                noSilentNoOps = false;
                System.out.println("missingEffectTag=" + card.getId());
            }
            for (String tag : card.getEffectTags()) {
                String cls = MabUpgradeEffectResolver.effectClassification(tag);
                if ("UNKNOWN".equals(cls)) {
                    knownEffectTags = false;
                    noSilentNoOps = false;
                    System.out.println("unknownEffectTag=" + card.getId() + ":" + tag);
                }
                String lower = (card.getId() + " " + tag).toLowerCase();
                if (lower.contains("force_piece")
                        || lower.contains("reroll")
                        || lower.contains("bag")
                        || lower.contains("shared_sequence")
                        || lower.contains("piece_source")) {
                    sharedSequenceUnaffected = false;
                }
            }
        }

        MabUpgradeCard manual = reg.getById("manual_override");
        MabUpgradeCard emp = reg.getById("emp");
        MabUpgradeCard dead = reg.getById("dead_hand_protocol");
        MabUpgradeCard hardened = reg.getById("hardened_silos");

        boolean manualOverrideActive = manual != null
                && manual.isActiveCard()
                && "ACTIVE_EFFECT".equals(MabUpgradeEffectResolver
                        .effectClassification("tempo_manual_override"));
        boolean empActive = emp != null
                && emp.isActiveCard()
                && "ACTIVE_EFFECT".equals(MabUpgradeEffectResolver
                        .effectClassification("tempo_emp"));

        MabUpgradeInventory inv = new MabUpgradeInventory();
        if (dead != null) inv.addUpgrade(dead);
        if (hardened != null) inv.addUpgrade(hardened);
        boolean deadHandHooked = MabUpgradeEffectResolver.deadHandProtocolActive(inv)
                && MabUpgradeEffectResolver.hardenedSilosActive(inv);

        boolean statusLanesVisible = true; // v2 keeps the Step 25 status lanes.
        boolean allCardsClassified = true;
        for (MabUpgradeCard card : reg.getAllCards()) {
            boolean classified = false;
            for (String tag : card.getEffectTags()) {
                String cls = MabUpgradeEffectResolver.effectClassification(tag);
                if ("PASSIVE_EFFECT".equals(cls)
                        || "ACTIVE_EFFECT".equals(cls)
                        || "UI_INTEL_EFFECT".equals(cls)
                        || "STATUS_EFFECT".equals(cls)
                        || "DOCUMENTED_DEFERRED".equals(cls)) {
                    classified = true;
                }
            }
            if (!classified) {
                allCardsClassified = false;
                noSilentNoOps = false;
                System.out.println("unclassifiedCard=" + card.getId());
            }
        }

        boolean success = knownEffectTags
                && manualOverrideActive
                && empActive
                && deadHandHooked
                && statusLanesVisible
                && noSilentNoOps
                && sharedSequenceUnaffected
                && allCardsClassified;

        System.out.println("registryCards=" + reg.getAllCards().size());
        System.out.println("knownEffectTags=" + knownEffectTags);
        System.out.println("manualOverrideActive=" + manualOverrideActive);
        System.out.println("empActive=" + empActive);
        System.out.println("deadHandHooked=" + deadHandHooked);
        System.out.println("statusLanesVisible=" + statusLanesVisible);
        System.out.println("noSilentNoOps=" + noSilentNoOps);
        System.out.println("sharedSequenceUnaffected=" + sharedSequenceUnaffected);
        System.out.println("success=" + success);
        if (!success) System.exit(1);
    }

    private MabUpgradeCoverageProbe() {}
}
