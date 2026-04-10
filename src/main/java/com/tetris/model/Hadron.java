package com.tetris.model;

/**
 * Represents a discovered hadron — a composite particle made from quarks.
 *
 * <p>In the Particle Tetris game, players build structures on the board that
 * match known hadron recipes. When a valid combination of quarks is detected
 * adjacent to each other, the hadron is "discovered" and added to the
 * collection.</p>
 *
 * <h3>Hadrons (for now: Top and Bottom quarks only)</h3>
 * <ul>
 *   <li><b>Proton</b> (uud) — 2 Top Quarks + 1 Bottom Quark in an L/triangle shape</li>
 *   <li><b>Neutron</b> (udd) — 1 Top Quark + 2 Bottom Quarks in an L/triangle shape</li>
 *   <li><b>Pion π+</b> (u d̄) — Top Quark + Gluon touching (up-type meson)</li>
 *   <li><b>Pion π−</b> (d ū) — Bottom Quark + Gluon touching (down-type meson)</li>
 *   <li><b>Pion π0</b> (uū/dd̄) — 2 same-type Quarks + Gluon in a row</li>
 * </ul>
 *
 * <p>The particle names use simplified quark notation for educational purposes.</p>
 */
public enum Hadron {

    /**
     * Proton — made of 2 Top Quarks (up) and 1 Bottom Quark (down).
     * The three quarks must form a connected 3-cell L-shape or line on the board.
     * Recipe: any 3 connected cells with exactly 2 TOP_QUARK_* and 1 BOTTOM_QUARK_*.
     */
    PROTON("Proton", "uud", "2 Top + 1 Bottom Quark",
            new int[]{2, 1, 0}, // 2 top, 1 bottom, 0 gluon needed
            0xFF4444,
            new String[] {
                "  ####  ",
                " ##  ## ",
                " ##  ## ",
                " ###### ",
                " ##     ",
                " ##     ",
                " ##     ",
                "  ####  "
            }),

    /**
     * Neutron — made of 1 Top Quark (up) and 2 Bottom Quarks (down).
     * Recipe: any 3 connected cells with exactly 1 TOP_QUARK_* and 2 BOTTOM_QUARK_*.
     */
    NEUTRON("Neutron", "udd", "1 Top + 2 Bottom Quark",
            new int[]{1, 2, 0},
            0x4444FF,
            new String[] {
                " ##  ## ",
                " ### ## ",
                " ###### ",
                " ## ### ",
                " ##  ## ",
                " ##  ## ",
                " ##  ## ",
                " ##  ## "
            }),

    /**
     * Pion π+ — a meson made of a Top Quark and a Gluon.
     * Recipe: 1 TOP_QUARK_* cell adjacent to 1 GLUON cell.
     */
    PION_PLUS("Pion π⁺", "u + g", "Top Quark + Gluon",
            new int[]{1, 0, 1},
            0xFF8800,
            new String[] {
                "   ##   ",
                "  ####  ",
                " ###### ",
                "   ##   ",
                "   ##   ",
                "   ##   ",
                "   ##   ",
                "   ##   "
            }),

    /**
     * Pion π− — a meson made of a Bottom Quark and a Gluon.
     * Recipe: 1 BOTTOM_QUARK_* cell adjacent to 1 GLUON cell.
     */
    PION_MINUS("Pion π⁻", "d + g", "Bottom Quark + Gluon",
            new int[]{0, 1, 1},
            0x4488FF,
            new String[] {
                "   ##   ",
                "   ##   ",
                "   ##   ",
                "   ##   ",
                "   ##   ",
                " ###### ",
                "  ####  ",
                "   ##   "
            }),

    /**
     * Pion π0 — a neutral meson. 2 quarks of same flavor + gluon in a line.
     * Recipe: 2 TOP_QUARK_* (or 2 BOTTOM_QUARK_*) with a GLUON between them.
     */
    PION_ZERO("Pion π⁰", "q q̄ g", "2 Same Quarks + Gluon",
            new int[]{2, 0, 1}, // special: also accepts {0, 2, 1}
            0xCCCCCC,
            new String[] {
                "  ####  ",
                " ##  ## ",
                " ##  ## ",
                " ##  ## ",
                " ##  ## ",
                " ##  ## ",
                " ##  ## ",
                "  ####  "
            });

    private final String displayName;
    private final String quarkNotation;
    private final String description;
    private final int[] recipe; // [topQuarks, bottomQuarks, gluons]
    private final int color;
    private final String[] pixelArt; // 8×8 pixel art pattern (# = filled)

    Hadron(String displayName, String quarkNotation, String description,
           int[] recipe, int color, String[] pixelArt) {
        this.displayName = displayName;
        this.quarkNotation = quarkNotation;
        this.description = description;
        this.recipe = recipe;
        this.color = color;
        this.pixelArt = pixelArt;
    }

    public String getDisplayName() { return displayName; }
    public String getQuarkNotation() { return quarkNotation; }
    public String getDescription() { return description; }
    public int[] getRecipe() { return recipe; }
    public int getColor() { return color; }
    public String[] getPixelArt() { return pixelArt; }
    public String getColorHex() { return String.format("#%06X", color); }
}
