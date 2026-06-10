package com.colin.vezanticheat.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CombatUtilReachLimitTest {

    @Test
    public void effectiveMaxReach_newConfigUsesMaxReachDirectly() {
        assertEquals(3.1D, CombatUtil.effectiveMaxReachFromValues(3.1D, 0.0D), 0.001D);
    }

    @Test
    public void effectiveMaxReach_legacyConfigAddsMargin() {
        assertEquals(3.1D, CombatUtil.effectiveMaxReachFromValues(3.0D, 0.1D), 0.001D);
    }

    @Test
    public void effectiveMaxReach_doesNotDoubleCountWhenBothPresent() {
        assertEquals(3.1D, CombatUtil.effectiveMaxReachFromValues(3.1D, 0.1D), 0.001D);
    }

    @Test
    public void expandedReachAt305_notOverAtEffectiveLimit() {
        double reach = 3.05D;
        double limit = CombatUtil.effectiveMaxReachFromValues(3.1D, 0.0D);
        assertFalse(reach > limit);
    }

    @Test
    public void expandedReachAt305_overAtOldEngineLimit() {
        double reach = 3.05D;
        double oldLimit = CombatUtil.VANILLA_BASE_REACH;
        assertTrue(reach > oldLimit);
    }
}
