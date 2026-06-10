package com.colin.vezanticheat.utils;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NoFallUtilTest {

    @Test
    public void expectedFallDamageUsesVanillaFormula() {
        assertTrue(Math.abs(NoFallUtil.expectedFallDamage(null, 2.9D)) < 1.0E-6);
        assertTrue(Math.abs(NoFallUtil.expectedFallDamage(null, 5.0D) - 2.0D) < 1.0E-6);
    }

    @Test
    public void damageMatchesExpectedWithinTolerance() {
        assertTrue(NoFallUtil.damageMatchesExpected(4.0D, 3.8D, 0.35D));
        assertFalse(NoFallUtil.damageMatchesExpected(4.0D, 0.0D, 0.35D));
        assertTrue(NoFallUtil.damageMatchesExpected(0.0D, 0.0D, 0.35D));
    }

    @Test
    public void serverFallDistanceUsesPeakAndCurrentY() {
        com.colin.vezanticheat.data.PlayerData data = new com.colin.vezanticheat.data.PlayerData(java.util.UUID.randomUUID());
        data.setNoFallAPeakY(70.0D);
        data.setNoFallAHasPeakY(true);
        data.setLastLoc(new org.bukkit.Location(null, 0, 65, 0));
        assertTrue(Math.abs(NoFallUtil.serverFallDistance(data) - 5.0D) < 1.0E-6);
    }
}
