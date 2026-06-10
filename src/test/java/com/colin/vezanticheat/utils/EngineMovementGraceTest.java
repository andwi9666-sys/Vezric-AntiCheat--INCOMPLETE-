package com.colin.vezanticheat.utils;

import org.bukkit.Material;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EngineMovementGraceTest {

    @Test
    public void stepLikeMaterialsIncludeSlabsAndStairs() {
        assertTrue(EngineMovementGrace.isStepLikeMaterial(Material.WOOD_STAIRS));
        assertTrue(EngineMovementGrace.isStepLikeMaterial(Material.WOOD_STEP));
        assertTrue(EngineMovementGrace.isStepLikeMaterial(Material.CARPET));
    }

    @Test
    public void stepLikeMaterialsRejectAir() {
        assertFalse(EngineMovementGrace.isStepLikeMaterial(Material.AIR));
        assertFalse(EngineMovementGrace.isStepLikeMaterial(null));
    }
}
