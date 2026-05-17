package com.tetris.mab.upgrade.draft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Step 24 \u2014 per-participant inventory of selected upgrade
 * cards. NOT global / not static. One inventory is attached to each
 * {@code ParticipantState}.
 *
 * <p>Stack count is recorded so repeatable cards (e.g.
 * {@code efficient_reactor}) can compound. Non-repeatable cards
 * silently cap at 1.
 */
public final class MabUpgradeInventory {

    /** Insertion-ordered: id -> stack count. */
    private final Map<String, Integer> stacks = new LinkedHashMap<>();
    /** id -> the card definition (for fast lookup at effect resolution). */
    private final Map<String, MabUpgradeCard> cards = new LinkedHashMap<>();

    public boolean hasUpgrade(String id) {
        return stacks.getOrDefault(id, 0) > 0;
    }

    public int getStacks(String id) {
        return stacks.getOrDefault(id, 0);
    }

    /** Adds one stack of the given card, capped by the card's
     *  {@code maxStacks}. Returns the new stack count. */
    public int addUpgrade(MabUpgradeCard card) {
        if (card == null) return 0;
        int cur = stacks.getOrDefault(card.getId(), 0);
        int max = card.isRepeatable() ? card.getMaxStacks() : 1;
        int next = Math.min(cur + 1, max);
        stacks.put(card.getId(), next);
        cards.put(card.getId(), card);
        return next;
    }

    /** True iff the card is at its stack cap and would not benefit
     *  from being offered again. */
    public boolean isMaxed(MabUpgradeCard card) {
        if (card == null) return true;
        int cur = stacks.getOrDefault(card.getId(), 0);
        int max = card.isRepeatable() ? card.getMaxStacks() : 1;
        return cur >= max;
    }

    public List<MabUpgradeCard> getAllSelectedCards() {
        return Collections.unmodifiableList(new ArrayList<>(cards.values()));
    }

    /** True iff the participant has any upgrade carrying the given
     *  effect tag. Used by {@link MabUpgradeEffectResolver}. */
    public boolean hasTag(String tag) {
        for (MabUpgradeCard c : cards.values()) {
            if (stacks.getOrDefault(c.getId(), 0) > 0 && c.hasTag(tag)) {
                return true;
            }
        }
        return false;
    }

    /** Sums stacks across every owned card carrying the tag. */
    public int countTagStacks(String tag) {
        int total = 0;
        for (MabUpgradeCard c : cards.values()) {
            if (c.hasTag(tag)) total += stacks.getOrDefault(c.getId(), 0);
        }
        return total;
    }

    public int getTotalSelectedCards() {
        int total = 0;
        for (int s : stacks.values()) total += s;
        return total;
    }

    /** Removes all stacks of the card with the given id (e.g. to reset
     *  a once-per-DEFCON-level card when the DEFCON level drops). */
    public void removeUpgrade(String id) {
        stacks.remove(id);
        cards.remove(id);
    }

    public void clear() {
        stacks.clear();
        cards.clear();
    }
}
