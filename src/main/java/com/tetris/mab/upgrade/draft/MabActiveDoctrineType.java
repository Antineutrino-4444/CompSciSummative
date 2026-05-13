package com.tetris.mab.upgrade.draft;

/**
 * Player-invoked v2 doctrine cards. These are local/offline match commands,
 * not action-code or networking primitives.
 */
public enum MabActiveDoctrineType {
    MANUAL_OVERRIDE("manual_override", "MANUAL OVERRIDE", "tempo_manual_override", 0),
    EMP("emp", "EMP", "tempo_emp", 40);

    private final String cardId;
    private final String displayName;
    private final String effectTag;
    private final int chargeCost;

    MabActiveDoctrineType(String cardId, String displayName, String effectTag, int chargeCost) {
        this.cardId = cardId;
        this.displayName = displayName;
        this.effectTag = effectTag;
        this.chargeCost = chargeCost;
    }

    public String cardId() { return cardId; }
    public String displayName() { return displayName; }
    public String effectTag() { return effectTag; }
    public int chargeCost() { return chargeCost; }
}
