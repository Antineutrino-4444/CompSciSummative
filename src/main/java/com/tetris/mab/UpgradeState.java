package com.tetris.mab;

import com.tetris.mab.upgrade.UpgradeApplicationResult;
import com.tetris.mab.upgrade.UpgradeDefinition;
import com.tetris.mab.upgrade.UpgradeType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Per-participant upgrade inventory: stored points, levels per
 * {@link UpgradeType}, and recently-applied history. Step 9 replaces
 * the placeholder version.
 */
public class UpgradeState {

    private static final int RECENT_HISTORY_LIMIT = 10;

    private int upgradePoints;
    private int totalUpgradePointsEarned;
    private final Map<UpgradeType, Integer> levels = new EnumMap<>(UpgradeType.class);
    private final LinkedList<UpgradeType> recentlyApplied = new LinkedList<>();

    public int getUpgradePoints() { return upgradePoints; }
    public int getTotalUpgradePointsEarned() { return totalUpgradePointsEarned; }

    public int getLevel(UpgradeType type) {
        if (type == null) return 0;
        Integer v = levels.get(type);
        return v == null ? 0 : v;
    }

    public Map<UpgradeType, Integer> getLevels() {
        return Collections.unmodifiableMap(new EnumMap<>(levels));
    }

    public List<UpgradeType> getRecentlyApplied() {
        return Collections.unmodifiableList(new ArrayList<>(recentlyApplied));
    }

    /** Total number of upgrade levels applied (sum across all types). */
    public int getTotalLevels() {
        int sum = 0;
        for (int v : levels.values()) sum += v;
        return sum;
    }

    public void addUpgradePoints(int amount) {
        if (amount <= 0) return;
        upgradePoints += amount;
        totalUpgradePointsEarned += amount;
    }

    public boolean canAfford(UpgradeDefinition definition) {
        if (definition == null) return false;
        return upgradePoints >= costForNextLevel(definition);
    }

    public boolean hasPrerequisites(UpgradeDefinition definition) {
        if (definition == null) return false;
        for (UpgradeType pre : definition.prerequisites()) {
            if (getLevel(pre) < 1) return false;
        }
        return true;
    }

    public boolean isMaxed(UpgradeDefinition definition) {
        if (definition == null) return true;
        return getLevel(definition.type()) >= definition.maxLevel();
    }

    /**
     * Cost for the next level: {@code baseCost + currentLevel}. If the
     * baseCost is 0 the upgrade stays free regardless of current level.
     */
    public int costForNextLevel(UpgradeDefinition definition) {
        if (definition == null) return Integer.MAX_VALUE;
        int base = definition.baseCost();
        if (base <= 0) return 0;
        return base + getLevel(definition.type());
    }

    public UpgradeApplicationResult applyUpgrade(UpgradeDefinition definition) {
        if (definition == null) {
            return UpgradeApplicationResult.failed(null, "null definition");
        }
        if (isMaxed(definition)) {
            return UpgradeApplicationResult.failed(definition.type(), "already maxed");
        }
        if (!hasPrerequisites(definition)) {
            return UpgradeApplicationResult.failed(definition.type(), "missing prerequisites");
        }
        int cost = costForNextLevel(definition);
        if (upgradePoints < cost) {
            return UpgradeApplicationResult.failed(definition.type(),
                    "insufficient points (need " + cost + ", have " + upgradePoints + ")");
        }
        upgradePoints -= cost;
        int newLevel = getLevel(definition.type()) + 1;
        levels.put(definition.type(), newLevel);
        recentlyApplied.addLast(definition.type());
        while (recentlyApplied.size() > RECENT_HISTORY_LIMIT) recentlyApplied.removeFirst();
        return UpgradeApplicationResult.success(definition.type(), newLevel, cost,
                "level " + newLevel + " applied");
    }

    public String toDebugString() {
        StringBuilder sb = new StringBuilder("Upgrade{points=").append(upgradePoints)
                .append(" total=").append(totalUpgradePointsEarned)
                .append(" levels=");
        boolean first = true;
        for (Map.Entry<UpgradeType, Integer> e : levels.entrySet()) {
            if (e.getValue() == null || e.getValue() == 0) continue;
            if (!first) sb.append(',');
            sb.append(e.getKey()).append(':').append(e.getValue());
            first = false;
        }
        if (first) sb.append("none");
        sb.append('}');
        return sb.toString();
    }
}
