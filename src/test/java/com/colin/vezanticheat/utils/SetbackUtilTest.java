package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.prediction.PredictionState;
import org.bukkit.Location;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SetbackUtilTest {

    @Test
    public void predictionStateStoresGroundAnchorWithTimestamp() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        Location anchor = new Location(null, 8.0, 69.0, 8.0);
        long now = System.currentTimeMillis();

        PredictionState state = data.getPredictionState();
        state.setLastValidGroundSetbackLocation(anchor, now);

        Location stored = state.getLastValidGroundSetbackLocation();
        assertNotNull(stored);
        assertEquals(8.0, stored.getX(), 0.001);
        assertEquals(69.0, stored.getY(), 0.001);
        assertEquals(8.0, stored.getZ(), 0.001);
        assertEquals(now, state.getLastValidGroundSetbackTimeMs());
    }

    @Test
    public void previousMoveFallbackIsPreservedOnPlayerData() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        Location previous = new Location(null, 9.0, 69.0, 9.0);
        data.setLastMoveFrom(previous);

        Location read = data.getLastMoveFrom();
        assertNotNull(read);
        assertEquals(9.0, read.getX(), 0.001);
        assertEquals(69.0, read.getY(), 0.001);
    }
}
