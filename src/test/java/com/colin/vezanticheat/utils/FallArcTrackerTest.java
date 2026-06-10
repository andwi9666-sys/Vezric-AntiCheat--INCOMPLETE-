package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

public class FallArcTrackerTest {

    private static final FallArcTracker.Settings SETTINGS = FallArcTracker.Settings.defaults();

    @Test
    public void suppressesSetbackDuringActiveFallWithoutFallDamage() {
        PlayerData data = activeFallData(100.0D, 70.0D, 5_000L);
        data.setLastLoc(new Location(mockAirWorld(), 0.0D, 70.0D, 0.0D));

        assertTrue(FallArcTracker.shouldSuppressLegitFallSetback(data, 5_100L, SETTINGS));
    }

    @Test
    public void doesNotSuppressSetbackAfterFallArcClears() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setFallArcPeakY(100.0D);
        data.setFallArcMinY(60.0D);
        data.setFallArcStartMs(1_000L);
        data.setFallArcLandMs(2_000L);
        data.setFallArcActive(false);
        data.setLastLoc(new Location(mockGroundWorld(60), 0.0D, 60.0D, 0.0D));

        assertFalse(FallArcTracker.shouldSuppressLegitFallSetback(data, 3_000L, SETTINGS));
    }

    @Test
    public void keepsFallArcActiveOnClientOnlyGroundMidAir() {
        World world = mockAirWorld();
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setFallArcPeakY(100.0D);
        data.setFallArcMinY(80.0D);
        data.setFallArcStartMs(4_000L);
        data.setFallArcActive(true);
        data.setLastClientGround(true, 4_000L);

        Location from = new Location(world, 0.0D, 80.5D, 0.0D);
        Location to = new Location(world, 0.0D, 80.0D, 0.0D);

        FallArcTracker.observe(data, from, to, true, 4_050L, SETTINGS);

        assertTrue(data.isFallArcActive());
        assertTrue(data.getFallArcLandMs() <= 0L);
    }

    @Test
    public void extendsFallArcWindowWhileStillDescendingAboveLanding() {
        PlayerData data = activeFallData(100.0D, 50.0D, 1_000L);
        data.setFallArcActive(false);
        data.setLastLoc(new Location(mockAirWorld(), 0.0D, 60.0D, 0.0D));

        assertTrue(FallArcTracker.isInFallArcWindow(data, 4_500L, SETTINGS));
    }

    @Test
    public void startsFallArcWhenClientClaimsGroundOnWalkOff() {
        World world = mockWalkOffWorld(64);
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setLastClientGround(true, 1_000L);

        Location from = new Location(world, 0.0D, 64.0D, 0.0D);
        Location to = new Location(world, 0.0D, 63.05D, 0.0D);

        FallArcTracker.observe(data, from, to, true, 1_050L, SETTINGS);

        assertTrue(data.isFallArcActive());
        assertTrue(data.getFallArcStartMs() > 0L);
    }

    private static World mockWalkOffWorld(int groundY) {
        World world = Mockito.mock(World.class);
        Block air = Mockito.mock(Block.class);
        Block stone = Mockito.mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(stone.getType()).thenReturn(Material.STONE);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            int y = invocation.getArgument(1);
            if (y == groundY - 1) return stone;
            return air;
        });
        when(world.getName()).thenReturn("arena");
        return world;
    }

    private static PlayerData activeFallData(double peakY, double minY, long startMs) {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setFallArcPeakY(peakY);
        data.setFallArcMinY(minY);
        data.setFallArcStartMs(startMs);
        data.setFallArcActive(true);
        return data;
    }

    private static World mockAirWorld() {
        World world = Mockito.mock(World.class);
        Block air = Mockito.mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);
        when(world.getName()).thenReturn("arena");
        return world;
    }

    private static World mockGroundWorld(int groundY) {
        World world = Mockito.mock(World.class);
        Block air = Mockito.mock(Block.class);
        Block stone = Mockito.mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(stone.getType()).thenReturn(Material.STONE);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            int y = invocation.getArgument(1);
            return y <= groundY - 1 ? stone : air;
        });
        when(world.getName()).thenReturn("world");
        return world;
    }
}
