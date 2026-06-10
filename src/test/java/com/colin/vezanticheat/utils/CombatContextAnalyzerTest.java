package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.UUID;

public class CombatContextAnalyzerTest {

    @Test
    public void kbDisplacementDetectsOutAndBackYawOscillation() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        long attackTime = 10_000L;

        data.getPositionHistory().addLast(new PlayerData.PositionSample(
                attackTime - 120L, "world", 0, 64, 0, 0.0F, 0.0F, false));
        data.getPositionHistory().addLast(new PlayerData.PositionSample(
                attackTime - 80L, "world", 0, 64, 0, 55.0F, 0.0F, false));
        data.getPositionHistory().addLast(new PlayerData.PositionSample(
                attackTime - 40L, "world", 0, 64, 0, -50.0F, 0.0F, false));
        data.getPositionHistory().addLast(new PlayerData.PositionSample(
                attackTime - 10L, "world", 0, 64, 0, 2.0F, 0.0F, false));

        Assert.assertTrue(CombatContextAnalyzer.isKbDisplacementFlick(data, attackTime, 180L));
    }

    @Test
    public void singleDirectionSnapIsNotKbDisplacement() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        long attackTime = 20_000L;

        data.getPositionHistory().addLast(new PlayerData.PositionSample(
                attackTime - 100L, "world", 0, 64, 0, 10.0F, 0.0F, false));
        data.getPositionHistory().addLast(new PlayerData.PositionSample(
                attackTime - 60L, "world", 0, 64, 0, 40.0F, 0.0F, false));
        data.getPositionHistory().addLast(new PlayerData.PositionSample(
                attackTime - 20L, "world", 0, 64, 0, 85.0F, 0.0F, false));

        Assert.assertFalse(CombatContextAnalyzer.isKbDisplacementFlick(data, attackTime, 150L));
    }

    @Test
    public void activePvpEngagementDetectsRapidHits() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        long now = 30_000L;
        data.getAttackTimestamps().addLast(now - 500L);
        data.getAttackTimestamps().addLast(now - 320L);
        data.getAttackTimestamps().addLast(now - 140L);
        Assert.assertTrue(CombatContextAnalyzer.isActivePvpEngagement(data, now));
    }

    @Test
    public void shouldExemptCombatInteractionDuringSpamClicking() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        long now = 40_000L;
        data.getAttackTimestamps().addLast(now - 480L);
        data.getAttackTimestamps().addLast(now - 300L);
        data.getAttackTimestamps().addLast(now - 120L);
        Assert.assertTrue(CombatContextAnalyzer.shouldExemptCombatInteractionFlagging(null, data, now));
    }

    @Test
    public void forwardJumpRushIsLegitCombatMovement() {
        World world = Mockito.mock(World.class);
        PlayerData data = new PlayerData(UUID.randomUUID());
        long now = 50_000L;
        Entity target = Mockito.mock(Entity.class);

        data.setLastJumpTime(now - 200L);
        data.setLastUseEntity(target, true, 2.5D, null, 0L, now - 50L);
        data.getAttackTimestamps().addLast(now - 480L);
        data.getAttackTimestamps().addLast(now - 300L);
        data.getAttackTimestamps().addLast(now - 120L);
        data.setLastMoveFrom(new Location(world, 0.0, 64.0, 0.0));
        data.setLastLoc(new Location(world, 0.25, 64.0, 0.0));

        Assert.assertTrue(CombatContextAnalyzer.isLikelyLegitCombatMovement(data, now));
    }

    @Test
    public void standingStillSpamStillLegitCombatMovement() {
        World world = Mockito.mock(World.class);
        PlayerData data = new PlayerData(UUID.randomUUID());
        long now = 60_000L;
        Entity target = Mockito.mock(Entity.class);

        data.setLastUseEntity(target, true, 2.5D, null, 0L, now - 40L);
        data.getAttackTimestamps().addLast(now - 420L);
        data.getAttackTimestamps().addLast(now - 240L);
        data.setLastMoveFrom(new Location(world, 1.0, 64.0, 1.0));
        data.setLastLoc(new Location(world, 1.02, 64.0, 1.01));

        Assert.assertTrue(CombatContextAnalyzer.isLikelyLegitCombatMovement(data, now));
    }

    @Test
    public void shouldExemptAimHeuristicsDuringJumpSpam() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        Player player = Mockito.mock(Player.class);
        long now = 70_000L;

        Mockito.when(player.isOnGround()).thenReturn(false);
        data.setLastJumpTime(now - 180L);
        data.getAttackTimestamps().addLast(now - 360L);
        data.getAttackTimestamps().addLast(now - 200L);

        Assert.assertTrue(CombatContextAnalyzer.shouldExemptAimHeuristics(null, null, data, player, now));
    }

    @Test
    public void backwardSTapIsSpacingMovement() {
        World world = Mockito.mock(World.class);
        PlayerData data = new PlayerData(UUID.randomUUID());
        Entity target = Mockito.mock(Entity.class);
        long now = 80_000L;

        data.setLastUseEntity(target, true, 2.5D, null, 0L, now - 50L);
        data.getAttackTimestamps().addLast(now - 50L);
        data.setLastMoveFrom(new Location(world, 0.0, 64.0, 0.0, 0.0F, 0.0F));
        data.setLastLoc(new Location(world, 0.0, 64.0, -0.25, 0.0F, 0.0F));

        Assert.assertTrue(CombatContextAnalyzer.isLikelySpacingMovement(data, null, now));
        Assert.assertTrue(CombatContextAnalyzer.shouldExemptAimHeuristics(null, null, data, null, now));
    }

    @Test
    public void counterstrafeSpacingExemptsHeuristics() {
        World world = Mockito.mock(World.class);
        PlayerData data = new PlayerData(UUID.randomUUID());
        Entity target = Mockito.mock(Entity.class);
        long now = 90_000L;

        data.setLastUseEntity(target, true, 2.5D, null, 0L, now - 40L);
        data.getAttackTimestamps().addLast(now - 40L);
        data.getPositionHistory().addLast(new PlayerData.PositionSample(
                now - 120L, "world", 0, 64, -0.3, 0.0F, 0.0F, false));
        data.getPositionHistory().addLast(new PlayerData.PositionSample(
                now - 80L, "world", 0, 64, -0.5, 0.0F, 0.0F, false));
        data.setLastMoveFrom(new Location(world, 0.0, 64.0, -0.5, 0.0F, 0.0F));
        data.setLastLoc(new Location(world, 0.0, 64.0, -0.2, 0.0F, 0.0F));

        Assert.assertTrue(CombatContextAnalyzer.isLikelyCounterstrafeSpacing(data, now));
        Assert.assertTrue(CombatContextAnalyzer.shouldExemptAimHeuristics(null, null, data, null, now));
    }

    @Test
    public void forwardChaseIsNotSpacingMovement() {
        World world = Mockito.mock(World.class);
        PlayerData data = new PlayerData(UUID.randomUUID());
        Entity target = Mockito.mock(Entity.class);
        long now = 100_000L;

        data.setLastUseEntity(target, true, 2.5D, null, 0L, now - 50L);
        data.setLastMoveFrom(new Location(world, 0.0, 64.0, 0.0, 0.0F, 0.0F));
        data.setLastLoc(new Location(world, 0.0, 64.0, 0.25, 0.0F, 0.0F));

        Assert.assertFalse(CombatContextAnalyzer.isLikelySpacingMovement(data, null, now));
        Assert.assertFalse(CombatContextAnalyzer.isLikelyCounterstrafeSpacing(data, now));
    }
}
