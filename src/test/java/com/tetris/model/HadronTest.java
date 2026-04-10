package com.tetris.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Hadron enum.
 */
class HadronTest {

    @Test
    void allHadronsHaveDisplayNames() {
        for (Hadron h : Hadron.values()) {
            assertNotNull(h.getDisplayName());
            assertFalse(h.getDisplayName().isEmpty());
        }
    }

    @Test
    void allHadronsHaveQuarkNotation() {
        for (Hadron h : Hadron.values()) {
            assertNotNull(h.getQuarkNotation());
            assertFalse(h.getQuarkNotation().isEmpty());
        }
    }

    @Test
    void allHadronsHavePixelArt() {
        for (Hadron h : Hadron.values()) {
            String[] art = h.getPixelArt();
            assertNotNull(art);
            assertEquals(8, art.length, h.name() + " should have 8-row pixel art");
        }
    }

    @Test
    void allHadronsHaveRecipes() {
        for (Hadron h : Hadron.values()) {
            int[] recipe = h.getRecipe();
            assertNotNull(recipe);
            assertEquals(3, recipe.length, h.name() + " recipe should have 3 elements");
        }
    }

    @Test
    void protonRecipeIs2Top1Bottom() {
        int[] recipe = Hadron.PROTON.getRecipe();
        assertEquals(2, recipe[0], "Proton needs 2 top quarks");
        assertEquals(1, recipe[1], "Proton needs 1 bottom quark");
        assertEquals(0, recipe[2], "Proton needs 0 gluons");
    }

    @Test
    void neutronRecipeIs1Top2Bottom() {
        int[] recipe = Hadron.NEUTRON.getRecipe();
        assertEquals(1, recipe[0], "Neutron needs 1 top quark");
        assertEquals(2, recipe[1], "Neutron needs 2 bottom quarks");
        assertEquals(0, recipe[2], "Neutron needs 0 gluons");
    }

    @Test
    void pionPlusRecipeIsTopPlusGluon() {
        int[] recipe = Hadron.PION_PLUS.getRecipe();
        assertEquals(1, recipe[0]);
        assertEquals(0, recipe[1]);
        assertEquals(1, recipe[2]);
    }

    @Test
    void pionMinusRecipeIsBottomPlusGluon() {
        int[] recipe = Hadron.PION_MINUS.getRecipe();
        assertEquals(0, recipe[0]);
        assertEquals(1, recipe[1]);
        assertEquals(1, recipe[2]);
    }

    @Test
    void allHadronsHaveColors() {
        for (Hadron h : Hadron.values()) {
            assertTrue(h.getColor() > 0);
            assertNotNull(h.getColorHex());
            assertTrue(h.getColorHex().startsWith("#"));
        }
    }

    @Test
    void fiveHadronTypes() {
        assertEquals(5, Hadron.values().length, "Should have 5 hadron types");
    }
}
