package com.tetris.mab.sim;

import com.tetris.mab.MatchDifficulty;
import com.tetris.mab.MutuallyAssuredBlocksMatch;
import com.tetris.mab.ParticipantId;
import com.tetris.mab.ParticipantState;
import com.tetris.mab.upgrade.draft.MabAiUpgradePicker;
import com.tetris.mab.upgrade.draft.MabUpgradeCard;
import com.tetris.mab.upgrade.draft.MabUpgradeDraft;
import com.tetris.mab.upgrade.draft.MabUpgradeDraftManager;
import com.tetris.mab.upgrade.draft.MabUpgradeDraftRegistry;
import com.tetris.model.GameState;

import java.util.HashSet;
import java.util.Set;

/**
 * Step 24 \u2014 verifies the level-up upgrade draft system: registry size,
 * three-choice generation, deterministic seeding, AI picker validity,
 * and apply-stores-upgrade behaviour.
 */
public final class MabUpgradeDraftProbe {

    private static int failed;

    public static void main(String[] args) {
        System.out.println("=== MAB Upgrade Draft Probe ===");

        MabUpgradeDraftRegistry reg = new MabUpgradeDraftRegistry();
        boolean registrySize = reg.getAllCards().size() >= 18;
        System.out.println("registrySize=" + reg.getAllCards().size() + " ok=" + registrySize);
        if (!registrySize) failed++;

        MutuallyAssuredBlocksMatch m1 = MutuallyAssuredBlocksMatch.createPveShared(
                new GameState(0), new GameState(0), MatchDifficulty.NORMAL, 12345L);
        m1.startMatch();
        ParticipantState pa1 = m1.getParticipant(ParticipantId.PLAYER_A);
        MabUpgradeDraftManager mgr1 = m1.getUpgradeDraftManager();
        MabUpgradeDraft draft = mgr1.generateDraft(pa1, 2);
        boolean threeChoices = draft != null && draft.getChoices().size() == 3;
        System.out.println("threeChoices=" + threeChoices);
        if (!threeChoices) failed++;

        Set<String> ids = new HashSet<>();
        for (MabUpgradeCard c : draft.getChoices()) ids.add(c.getId());
        boolean noDuplicates = ids.size() == 3;
        System.out.println("noDuplicates=" + noDuplicates);
        if (!noDuplicates) failed++;

        MutuallyAssuredBlocksMatch m2 = MutuallyAssuredBlocksMatch.createPveShared(
                new GameState(0), new GameState(0), MatchDifficulty.NORMAL, 12345L);
        m2.startMatch();
        ParticipantState pa2 = m2.getParticipant(ParticipantId.PLAYER_A);
        MabUpgradeDraft draft2 = m2.getUpgradeDraftManager().generateDraft(pa2, 2);
        boolean sameSeedStable = sameIds(draft, draft2);
        System.out.println("sameSeedStable=" + sameSeedStable);
        if (!sameSeedStable) failed++;

        MabUpgradeCard aiPick = MabAiUpgradePicker.pick(draft, MatchDifficulty.NORMAL, 12345L);
        boolean aiChoiceValid = aiPick != null && draft.getChoices().contains(aiPick);
        System.out.println("aiChoiceValid=" + aiChoiceValid);
        if (!aiChoiceValid) failed++;

        var inv = pa1.getUpgradeInventory();
        int beforeCount = inv.getTotalSelectedCards();
        mgr1.applyDirect(pa1, draft.getChoices().get(0));
        boolean applyStoresUpgrade = inv.getTotalSelectedCards() == beforeCount + 1
                && inv.hasUpgrade(draft.getChoices().get(0).getId());
        System.out.println("applyStoresUpgrade=" + applyStoresUpgrade);
        if (!applyStoresUpgrade) failed++;

        boolean pauseResume;
        try {
            m1.openUpgradePause("probe");
            m1.closeUpgradePause("probe");
            pauseResume = true;
        } catch (RuntimeException ex) {
            pauseResume = false;
        }
        System.out.println("pauseResume=" + pauseResume);
        if (!pauseResume) failed++;

        for (MabUpgradeCard c : reg.getAllCards()) {
            if (!c.isRepeatable() && !inv.hasUpgrade(c.getId())) {
                inv.addUpgrade(c);
            }
        }
        MabUpgradeDraft draft3 = mgr1.generateDraft(pa1, 8);
        boolean maxedNotOffered = true;
        if (draft3 != null) {
            for (MabUpgradeCard c : draft3.getChoices()) {
                if (!c.isRepeatable() && inv.isMaxed(c)) {
                    maxedNotOffered = false;
                    System.out.println("  re-offered maxed: " + c.getId());
                }
            }
        }
        System.out.println("maxedNotOffered=" + maxedNotOffered);
        if (!maxedNotOffered) failed++;

        boolean noNoOps = true;
        for (MabUpgradeCard c : reg.getAllCards()) {
            if (c.getEffectTags() == null || c.getEffectTags().isEmpty()) {
                noNoOps = false;
                System.out.println("  no-op card: " + c.getId());
            }
        }
        System.out.println("noNoOps=" + noNoOps);
        if (!noNoOps) failed++;

        boolean success = failed == 0;
        System.out.println("success=" + success);
        if (!success) System.exit(1);
    }

    private static boolean sameIds(MabUpgradeDraft a, MabUpgradeDraft b) {
        if (a == null || b == null) return false;
        if (a.getChoices().size() != b.getChoices().size()) return false;
        for (int i = 0; i < a.getChoices().size(); i++) {
            if (!a.getChoices().get(i).getId().equals(b.getChoices().get(i).getId())) {
                return false;
            }
        }
        return true;
    }
}
