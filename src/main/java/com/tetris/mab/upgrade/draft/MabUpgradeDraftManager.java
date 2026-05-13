package com.tetris.mab.upgrade.draft;

import com.tetris.mab.ParticipantId;
import com.tetris.mab.ParticipantState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Step 24 \u2014 detects participant level-ups, builds 3-card drafts
 * with a deterministic RNG seeded from match seed + participant id +
 * level, queues pending drafts, and applies the player's selection.
 *
 * <p>This class is engine-side; UI presentation lives in
 * {@code MabUpgradeDraftOverlayPanel} and the controller's refresh
 * timer.
 */
public final class MabUpgradeDraftManager {

    private final MabUpgradeDraftRegistry registry;
    private final long matchSeed;
    private final Map<ParticipantId, Integer> lastDraftedLevel = new EnumMap<>(ParticipantId.class);
    private final Deque<MabUpgradeDraft> pendingHumanDrafts = new ArrayDeque<>();
    /** True while a human draft is awaiting selection. The match
     *  should be in upgrade pause whenever this is non-null. */
    private MabUpgradeDraft activeHumanDraft;

    public MabUpgradeDraftManager(MabUpgradeDraftRegistry registry, long matchSeed) {
        this.registry = registry;
        this.matchSeed = matchSeed;
        for (ParticipantId pid : ParticipantId.values()) {
            lastDraftedLevel.put(pid, 1); // never draft for the starting level
        }
    }

    /** Polls each participant's current level. If any participant has
     *  advanced past its {@code lastDraftedLevel}, a draft is built
     *  for each new level. Human drafts are queued, AI drafts are
     *  returned for the caller to auto-pick. Returns the list of
     *  AI drafts created during this poll (may be empty). */
    public List<MabUpgradeDraft> checkLevelUps(ParticipantState player, ParticipantState ai) {
        return checkLevelUps(player, true, ai, false);
    }

    /** Flexible level-up polling used by PvE and local PvP. */
    public List<MabUpgradeDraft> checkLevelUps(ParticipantState first, boolean firstHuman,
                                               ParticipantState second, boolean secondHuman) {
        List<MabUpgradeDraft> aiDrafts = new ArrayList<>();
        if (first != null) collectFor(first, firstHuman, aiDrafts);
        if (second != null) collectFor(second, secondHuman, aiDrafts);
        return aiDrafts;
    }

    private void collectFor(ParticipantState p, boolean human, List<MabUpgradeDraft> aiOut) {
        int curLevel = p.getGameState().getScoreSystem().getLevel();
        int last = lastDraftedLevel.getOrDefault(p.getId(), 1);
        while (curLevel > last) {
            int newLevel = last + 1;
            MabUpgradeDraft d = generateDraft(p, newLevel);
            if (human) pendingHumanDrafts.add(d);
            else aiOut.add(d);
            last = newLevel;
        }
        lastDraftedLevel.put(p.getId(), last);
    }

    /** Build a deterministic 3-card draft for the given participant
     *  + level using {@code matchSeed ^ pidHash ^ level} as the
     *  Random seed so reruns are stable. */
    public MabUpgradeDraft generateDraft(ParticipantState p, int level) {
        long seed = matchSeed
                ^ ((long) p.getId().ordinal() * 0x9E3779B97F4A7C15L)
                ^ ((long) level * 0xC6BC279692B5C323L);
        Random rng = new Random(seed);
        MabUpgradeInventory inv = p.getUpgradeInventory();

        // Compute per-rarity weights for the participant's current level.
        int[] rarityWeights = rarityWeightsForLevel(level);

        // Filter out maxed cards.
        List<MabUpgradeCard> pool = new ArrayList<>();
        for (MabUpgradeCard c : registry.getAllCards()) {
            if (inv == null || !inv.isMaxed(c)) pool.add(c);
        }

        List<MabUpgradeCard> chosen = new ArrayList<>(3);
        for (int slot = 0; slot < 3 && !pool.isEmpty(); slot++) {
            MabUpgradeRarity tier = pickRarity(rng, rarityWeights);
            MabUpgradeCard pick = weightedPick(rng, pool, tier);
            if (pick == null) {
                // tier empty: fall back to any card in pool.
                pick = pool.get(rng.nextInt(pool.size()));
            }
            chosen.add(pick);
            pool.remove(pick); // no duplicates within the same draft
        }

        // If the registry was tiny or most cards were maxed, pad
        // with random non-maxed cards (allowing duplicates as a last
        // resort if even the non-maxed pool is exhausted).
        java.util.List<MabUpgradeCard> nonMaxed = new ArrayList<>();
        for (MabUpgradeCard c : registry.getAllCards()) {
            if (inv == null || !inv.isMaxed(c)) nonMaxed.add(c);
        }
        while (chosen.size() < 3) {
            if (!nonMaxed.isEmpty()) {
                chosen.add(nonMaxed.get(rng.nextInt(nonMaxed.size())));
            } else {
                // Truly nothing left — fall back to any card to keep
                // the contract of exactly 3 choices.
                chosen.add(registry.getAllCards().get(rng.nextInt(registry.size())));
            }
        }

        return new MabUpgradeDraft(p.getId(), level, chosen);
    }

    private static int[] rarityWeightsForLevel(int level) {
        // Returns weights in order [STANDARD, ADVANCED, CRITICAL].
        if (level <= 3)  return new int[] { 85, 15, 0  };
        if (level <= 6)  return new int[] { 65, 30, 5  };
        return                 new int[] { 50, 35, 15 };
    }

    private static MabUpgradeRarity pickRarity(Random rng, int[] w) {
        int total = w[0] + w[1] + w[2];
        int r = rng.nextInt(total);
        if (r < w[0]) return MabUpgradeRarity.STANDARD;
        if (r < w[0] + w[1]) return MabUpgradeRarity.ADVANCED;
        return MabUpgradeRarity.CRITICAL;
    }

    private static MabUpgradeCard weightedPick(Random rng, List<MabUpgradeCard> pool, MabUpgradeRarity tier) {
        List<MabUpgradeCard> filtered = new ArrayList<>();
        for (MabUpgradeCard c : pool) if (c.getRarity() == tier) filtered.add(c);
        if (filtered.isEmpty()) return null;
        double totalWeight = 0;
        for (MabUpgradeCard c : filtered) totalWeight += c.getWeight();
        double r = rng.nextDouble() * totalWeight;
        double acc = 0;
        for (MabUpgradeCard c : filtered) {
            acc += c.getWeight();
            if (r < acc) return c;
        }
        return filtered.get(filtered.size() - 1);
    }

    // ---------------- Human draft queue ----------------

    /** Returns the next pending human draft and marks it active, or
     *  null if none. The caller is responsible for entering upgrade
     *  pause and showing the overlay. */
    public MabUpgradeDraft pollNextHumanDraft() {
        if (activeHumanDraft != null) return activeHumanDraft;
        activeHumanDraft = pendingHumanDrafts.pollFirst();
        return activeHumanDraft;
    }

    public boolean hasActiveHumanDraft() { return activeHumanDraft != null; }
    public MabUpgradeDraft getActiveHumanDraft() { return activeHumanDraft; }
    public boolean hasPendingHumanDrafts() { return !pendingHumanDrafts.isEmpty(); }

    /** Apply the player's selection from the active human draft.
     *  Stores the card in the participant's inventory, clears the
     *  active draft, returns the chosen card. */
    public MabUpgradeCard applyHumanSelection(ParticipantState player, MabUpgradeCard chosen) {
        if (activeHumanDraft == null || chosen == null) return null;
        if (!activeHumanDraft.getChoices().contains(chosen)) return null;
        if (player != null && player.getUpgradeInventory() != null) {
            player.getUpgradeInventory().addUpgrade(chosen);
            MabUpgradeEffectResolver.syncSimplifiedStateConfig(
                    player.getUpgradeInventory(), player.getSimplifiedState());
        }
        activeHumanDraft = null;
        return chosen;
    }

    /** Apply a chosen card to a participant outside the human queue
     *  (used by AI auto-pick). */
    public void applyDirect(ParticipantState p, MabUpgradeCard chosen) {
        if (p == null || chosen == null || p.getUpgradeInventory() == null) return;
        p.getUpgradeInventory().addUpgrade(chosen);
        MabUpgradeEffectResolver.syncSimplifiedStateConfig(
                p.getUpgradeInventory(), p.getSimplifiedState());
    }

    public List<MabUpgradeDraft> snapshotPendingHumanDrafts() {
        return Collections.unmodifiableList(new ArrayList<>(pendingHumanDrafts));
    }

    /** Force-set the last-drafted level for a participant. Used by
     *  tests that don't want a draft to fire at level 1. */
    public void setLastDraftedLevel(ParticipantId pid, int level) {
        lastDraftedLevel.put(pid, level);
    }
}
