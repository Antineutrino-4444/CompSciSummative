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
    void allHadronsHaveDescriptions() {
        for (Hadron h : Hadron.values()) {
            assertNotNull(h.getDescription());
            assertFalse(h.getDescription().isEmpty());
        }
    }

    @Test
    void protonRecipe() {
        assertEquals(2, Hadron.PROTON.getTopQuarksNeeded());
        assertEquals(1, Hadron.PROTON.getBottomQuarksNeeded());
        assertEquals(2, Hadron.PROTON.getMinGluons());
        assertEquals(3, Hadron.PROTON.getTotalQuarks());
    }

    @Test
    void neutronRecipe() {
        assertEquals(1, Hadron.NEUTRON.getTopQuarksNeeded());
        assertEquals(2, Hadron.NEUTRON.getBottomQuarksNeeded());
        assertEquals(2, Hadron.NEUTRON.getMinGluons());
        assertEquals(3, Hadron.NEUTRON.getTotalQuarks());
    }

    @Test
    void pionRecipe() {
        assertEquals(1, Hadron.PION.getTopQuarksNeeded());
        assertEquals(1, Hadron.PION.getBottomQuarksNeeded());
        assertEquals(1, Hadron.PION.getMinGluons());
        assertEquals(2, Hadron.PION.getTotalQuarks());
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
    void threeHadronTypes() {
        assertEquals(3, Hadron.values().length, "Should have 3 hadron types");
    }
}
