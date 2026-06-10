package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.prediction.PredictionState;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    public void staleLastMoveFromFallbackIsRejected() {
        long maxAgeMs = 2500L;
        long now = System.currentTimeMillis();

        World world = mock(World.class);
        VezAntiCheat plugin = mock(VezAntiCheat.class);
        FileConfiguration config = mock(FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(config);
        when(config.getLong("prediction.setback.max-valid-age-ms", 2500L)).thenReturn(maxAgeMs);

        Player player = mock(Player.class);
        Location current = new Location(world, 0.0, 70.0, 0.0);
        when(player.getLocation()).thenReturn(current);

        PlayerData data = new PlayerData(UUID.randomUUID());
        // No primary ground anchor; rely on the lastMoveFrom fallback.
        Location previous = new Location(world, 5.0, 70.0, 5.0);
        data.setLastMoveFrom(previous);
        // Stamp it far older than max-valid-age — fallback must be rejected on age (before any
        // ground/chunk lookup), so no Block mocking is needed.
        data.setLastMoveMillis(now - (maxAgeMs + 10_000L));

        Location target = SetbackUtil.resolveSetbackTarget(plugin, player, data);
        assertNull("stale lastMoveFrom fallback must be rejected by max-age check", target);
    }

    @Test
    public void crossWorldLastMoveFromFallbackIsRejected() {
        long maxAgeMs = 2500L;
        long now = System.currentTimeMillis();

        World worldA = mock(World.class);
        World worldB = mock(World.class);
        VezAntiCheat plugin = mock(VezAntiCheat.class);
        FileConfiguration config = mock(FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(config);
        when(config.getLong("prediction.setback.max-valid-age-ms", 2500L)).thenReturn(maxAgeMs);

        Player player = mock(Player.class);
        Location current = new Location(worldA, 0.0, 70.0, 0.0);
        when(player.getLocation()).thenReturn(current);

        PlayerData data = new PlayerData(UUID.randomUUID());
        // Fresh timestamp so age passes, but a DIFFERENT world so isUsableTarget short-circuits
        // before any ground/chunk lookup.
        Location previous = new Location(worldB, 5.0, 70.0, 5.0);
        data.setLastMoveFrom(previous);
        data.setLastMoveMillis(now);

        Location target = SetbackUtil.resolveSetbackTarget(plugin, player, data);
        assertNull("cross-world lastMoveFrom fallback must be rejected", target);
    }
}
