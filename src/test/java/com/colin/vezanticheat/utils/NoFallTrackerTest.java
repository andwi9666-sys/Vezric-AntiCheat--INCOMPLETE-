package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.data.PlayerData;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NoFallTrackerTest {

    @Test
    public void pendingDamageCheckWaitsUntilExpectedTime() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        long landMs = 5000L;
        data.setNoFallALandAtMs(landMs);
        data.setNoFallAExpectedDamageAfterMs(landMs + 140L);
        data.setNoFallDamageResolved(false);

        assertFalse(NoFallTracker.hasPendingDamageCheck(data, landMs + 50L));
        assertTrue(NoFallTracker.hasPendingDamageCheck(data, landMs + 140L));
    }

    @Test
    public void fallDamageResolutionClearsPendingState() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setNoFallALandAtMs(1000L);
        data.setNoFallAHasPeakY(true);
        data.setNoFallAPeakY(70.0D);

        NoFallTracker.onFallDamage(null, null, data, 4.0D, 1200L);

        assertTrue(data.isNoFallDamageResolved());
        assertFalse(data.hasNoFallAPeakY());
    }

    @Test
    public void blinkGapStoresPeakY() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setLastFlyingIntervalMs(150L);
        data.setLastLoc(new org.bukkit.Location(null, 0, 80, 0));
        data.setNoFallAPeakY(82.0D);
        data.setNoFallAHasPeakY(true);

        NoFallTracker.noteBlinkGap(null, null, data, 2000L);

        assertTrue(data.getPreBlinkPeakY() >= 82.0D);
        assertEquals(2000L, data.getPreBlinkMs());
    }
}
