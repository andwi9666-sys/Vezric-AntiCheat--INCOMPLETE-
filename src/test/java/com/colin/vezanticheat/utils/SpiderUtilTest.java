package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

public class SpiderUtilTest {

    private static final SpiderUtil.TickSettings SETTINGS = SpiderUtil.TickSettings.defaults();

    @Test
    public void detectsWallCollisionAscend() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setLastLoc(new Location(mockWallWorld(), 0.5D, 64.0D, 0.5D));

        EngineResult er = EngineResult.builder()
                .checked(true)
                .collisionX(true)
                .clientGround(false)
                .predictedOnGround(false)
                .actual(new Vector(0.0D, 0.12D, 0.0D))
                .build();

        assertTrue(SpiderUtil.isSpiderAscendTick(null, null, data, er, 1000L, SETTINGS));
    }

    @Test
    public void rejectsLadderClimb() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setLastLoc(new Location(mockWallWorld(), 0.5D, 64.0D, 0.5D));

        EngineResult er = EngineResult.builder()
                .checked(true)
                .onClimbable(true)
                .collisionX(true)
                .clientGround(false)
                .predictedOnGround(false)
                .actual(new Vector(0.0D, 0.12D, 0.0D))
                .build();

        assertFalse(SpiderUtil.isSpiderAscendTick(null, null, data, er, 1000L, SETTINGS));
    }

    @Test
    public void wallSpiderDuringJumpArcIsDetected() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setLastLoc(new Location(mockWallWorld(), 0.5D, 64.0D, 0.5D));
        data.setLastJumpTime(800L);

        EngineResult er = EngineResult.builder()
                .checked(true)
                .collisionX(true)
                .clientGround(false)
                .predictedOnGround(false)
                .actual(new Vector(0.0D, 0.12D, 0.0D))
                .build();

        assertTrue(SpiderUtil.isSpiderAscendTick(null, null, data, er, 1000L, SETTINGS));
        assertFalse(SpiderUtil.shouldExempt(null, null, data, er, 1000L, SETTINGS));
    }

    @Test
    public void legitJumpArcWithoutWallIsExempt() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setLastLoc(new Location(mockOpenWorld(), 0.5D, 64.0D, 0.5D));
        data.setLastJumpTime(900L);

        EngineResult er = EngineResult.builder()
                .checked(true)
                .clientGround(false)
                .predictedOnGround(false)
                .actual(new Vector(0.0D, 0.12D, 0.0D))
                .build();

        assertTrue(SpiderUtil.shouldExempt(null, null, data, er, 1000L, SETTINGS));
        assertFalse(SpiderUtil.isSpiderAscendTick(null, null, data, er, 1000L, SETTINGS));
    }

    @Test
    public void streakIncrementsOnPositiveTicks() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        SpiderUtil.observe(data, true);
        SpiderUtil.observe(data, true);
        assertTrue(data.getSpiderAscendStreak() >= 2);
        SpiderUtil.observe(data, false);
        assertTrue(data.getSpiderAscendStreak() >= 1);
    }

    private static World mockWallWorld() {
        World world = Mockito.mock(World.class);
        Block air = Mockito.mock(Block.class);
        Block stone = Mockito.mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(stone.getType()).thenReturn(Material.STONE);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            int x = (Integer) invocation.getArguments()[0];
            if (x == 1) return stone;
            return air;
        });
        return world;
    }

    private static World mockOpenWorld() {
        World world = Mockito.mock(World.class);
        Block air = Mockito.mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);
        return world;
    }
}
