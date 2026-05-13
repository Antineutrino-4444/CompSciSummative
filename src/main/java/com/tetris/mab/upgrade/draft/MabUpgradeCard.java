package com.tetris.mab.upgrade.draft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Step 24 \u2014 immutable definition of one upgrade card offered by
 * the level-up draft. Cards are stateless and shared across
 * participants; per-participant ownership and stack count live on
 * {@link MabUpgradeInventory}.
 *
 * <p>Effect tags are short string identifiers consumed by
 * {@link MabUpgradeEffectResolver} to apply real changes to the
 * simplified MAB mechanics.
 */
public final class MabUpgradeCard {

    private final String id;
    private final String displayName;
    private final String shortName;
    private final String iconText;
    private final MabUpgradeCategory category;
    private final MabUpgradeRarity rarity;
    private final String oneLineDescription;
    private final String detailedDescription;
    private final int maxStacks;
    private final boolean repeatable;
    private final List<String> effectTags;
    /** Relative sampling weight within its rarity tier (default 1.0). */
    private final double weight;
    /** Archetypes this card belongs to (1 or 2). Empty if unassigned. */
    private final List<MabUpgradeArchetype> archetypes;
    /** True for cards that trigger a mid-match pause overlay (Manual Override, EMP). */
    private final boolean activeCard;

    public MabUpgradeCard(String id, String displayName, String shortName, String iconText,
                          MabUpgradeCategory category, MabUpgradeRarity rarity,
                          String oneLineDescription, String detailedDescription,
                          int maxStacks, boolean repeatable, List<String> effectTags) {
        this(id, displayName, shortName, iconText, category, rarity,
             oneLineDescription, detailedDescription, maxStacks, repeatable, effectTags,
             1.0, Collections.emptyList(), false);
    }

    public MabUpgradeCard(String id, String displayName, String shortName, String iconText,
                          MabUpgradeCategory category, MabUpgradeRarity rarity,
                          String oneLineDescription, String detailedDescription,
                          int maxStacks, boolean repeatable, List<String> effectTags, double weight) {
        this(id, displayName, shortName, iconText, category, rarity,
             oneLineDescription, detailedDescription, maxStacks, repeatable, effectTags,
             weight, Collections.emptyList(), false);
    }

    public MabUpgradeCard(String id, String displayName, String shortName, String iconText,
                          MabUpgradeCategory category, MabUpgradeRarity rarity,
                          String oneLineDescription, String detailedDescription,
                          int maxStacks, boolean repeatable, List<String> effectTags,
                          double weight, List<MabUpgradeArchetype> archetypes, boolean activeCard) {
        this.id = id;
        this.displayName = displayName;
        this.shortName = shortName;
        this.iconText = iconText;
        this.category = category;
        this.rarity = rarity;
        this.oneLineDescription = oneLineDescription;
        this.detailedDescription = detailedDescription == null ? oneLineDescription : detailedDescription;
        this.maxStacks = Math.max(1, maxStacks);
        this.repeatable = repeatable;
        this.effectTags = effectTags == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(effectTags);
        this.weight = Math.max(0.01, weight);
        this.archetypes = archetypes == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(archetypes));
        this.activeCard = activeCard;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getShortName() { return shortName; }
    public String getIconText() { return iconText; }
    public MabUpgradeCategory getCategory() { return category; }
    public MabUpgradeRarity getRarity() { return rarity; }
    public String getOneLineDescription() { return oneLineDescription; }
    public String getDetailedDescription() { return detailedDescription; }
    public int getMaxStacks() { return maxStacks; }
    public boolean isRepeatable() { return repeatable; }
    public List<String> getEffectTags() { return effectTags; }
    public double getWeight() { return weight; }

    public List<MabUpgradeArchetype> getArchetypes() { return archetypes; }
    public boolean isActiveCard() { return activeCard; }

    public boolean hasTag(String tag) { return effectTags.contains(tag); }

    @Override public String toString() {
        return "MabUpgradeCard{id=" + id + ",rarity=" + rarity + "}";
    }
}
