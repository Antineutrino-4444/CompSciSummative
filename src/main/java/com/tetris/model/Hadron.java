package com.tetris.model;

/**
 * Represents a discovered hadron — a composite particle made from quarks bound by gluons.
 *
 * <h3>Formation Mechanic</h3>
 * <p>Hadrons form when the correct combination of quarks are connected through
 * a network of gluon cells on the board. Simply placing quarks next to each other
 * is NOT enough — they must be bridged by gluons.</p>
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>Place quarks on the board</li>
 *   <li>Place gluons between/adjacent to quarks to create bridges</li>
 *   <li>When the correct quark recipe is connected through gluons, the hadron forms</li>
 *   <li>All participating cells (quarks + gluons) are consumed</li>
 * </ol>
 *
 * <h3>Recipes</h3>
 * <ul>
 *   <li><b>Proton</b> (uud): 2 Top + 1 Bottom quark, connected through gluons</li>
 *   <li><b>Neutron</b> (udd): 1 Top + 2 Bottom quark, connected through gluons</li>
 *   <li><b>Pion π⁺</b>: 1 Top + 1 Bottom quark, connected through exactly 1 gluon</li>
 * </ul>
 */
public enum Hadron {

    /**
     * Proton — 2 Top Quarks + 1 Bottom Quark, all connected through gluons.
     * Requires at least 2 gluon bridges in the connecting path.
     */
    PROTON("Proton", "uud", "2 Top + 1 Bottom, gluon-linked",
            2, 1, 2, 0xFF4444),

    /**
     * Neutron — 1 Top Quark + 2 Bottom Quarks, all connected through gluons.
     * Requires at least 2 gluon bridges in the connecting path.
     */
    NEUTRON("Neutron", "udd", "1 Top + 2 Bottom, gluon-linked",
            1, 2, 2, 0x4488FF),

    /**
     * Pion — 1 Top Quark + 1 Bottom Quark, connected through exactly 1 gluon.
     * The simplest hadron — a quark-antiquark pair mediated by a single gluon.
     */
    PION("Pion", "ud", "1 Top + 1 Bottom + 1 Gluon between",
            1, 1, 1, 0xFFAA00);

    private final String displayName;
    private final String quarkNotation;
    private final String description;
    private final int topQuarksNeeded;
    private final int bottomQuarksNeeded;
    private final int minGluons;
    private final int color;

    Hadron(String displayName, String quarkNotation, String description,
           int topQuarksNeeded, int bottomQuarksNeeded, int minGluons, int color) {
        this.displayName = displayName;
        this.quarkNotation = quarkNotation;
        this.description = description;
        this.topQuarksNeeded = topQuarksNeeded;
        this.bottomQuarksNeeded = bottomQuarksNeeded;
        this.minGluons = minGluons;
        this.color = color;
    }

    public String getDisplayName() { return displayName; }
    public String getQuarkNotation() { return quarkNotation; }
    public String getDescription() { return description; }
    public int getTopQuarksNeeded() { return topQuarksNeeded; }
    public int getBottomQuarksNeeded() { return bottomQuarksNeeded; }
    public int getMinGluons() { return minGluons; }
    public int getColor() { return color; }
    public String getColorHex() { return String.format("#%06X", color); }

    /** Total quarks required for this hadron. */
    public int getTotalQuarks() { return topQuarksNeeded + bottomQuarksNeeded; }
}
