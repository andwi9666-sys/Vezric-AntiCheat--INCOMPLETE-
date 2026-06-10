package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.engine.MovementPlayer;
import com.colin.vezanticheat.engine.PredictionEngine;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collections;

public class PotionUtilTest {

    @Test
    public void walkSpeedAllowanceAboveVanilla() {
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.getWalkSpeed()).thenReturn(0.24F);
        Mockito.when(player.isSprinting()).thenReturn(true);
        Mockito.when(player.isOnGround()).thenReturn(true);
        Assert.assertTrue(PotionUtil.hasSpeedBoost(player));
        Assert.assertTrue(PotionUtil.walkSpeedOffsetAllowance(player) > 0.0D);
        Assert.assertEquals(PotionUtil.walkSpeedOffsetAllowance(player),
                PotionUtil.combinedSpeedOffsetAllowance(player), 0.001D);
    }

    @Test
    public void effectiveLandMovementSpeedSpeedI() {
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.getWalkSpeed()).thenReturn(0.2F);
        Mockito.when(player.getActivePotionEffects()).thenReturn(
                Collections.singleton(new PotionEffect(PotionEffectType.SPEED, 600, 0)));
        Assert.assertEquals(0.12F, PotionUtil.effectiveLandMovementSpeed(player), 0.001F);
    }

    @Test
    public void effectiveLandMovementSpeedSpeedII() {
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.getWalkSpeed()).thenReturn(0.2F);
        Mockito.when(player.getActivePotionEffects()).thenReturn(
                Collections.singleton(new PotionEffect(PotionEffectType.SPEED, 600, 1)));
        Assert.assertEquals(0.14F, PotionUtil.effectiveLandMovementSpeed(player), 0.001F);
    }

    @Test
    public void effectiveLandMovementSpeedNoDoubleCount() {
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.getWalkSpeed()).thenReturn(0.28F);
        Mockito.when(player.getActivePotionEffects()).thenReturn(
                Collections.singleton(new PotionEffect(PotionEffectType.SPEED, 600, 1)));
        Assert.assertEquals(0.14F, PotionUtil.effectiveLandMovementSpeed(player), 0.001F);
    }

    @Test
    public void effectiveSpeedLevelCapsAtTwo() {
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.getActivePotionEffects()).thenReturn(
                Collections.singleton(new PotionEffect(PotionEffectType.SPEED, 600, 5)));
        Assert.assertEquals(2, PotionUtil.effectiveSpeedLevel(player));
    }

    @Test
    public void speedIIAllowanceGreaterThanSpeedI() {
        Player speedI = Mockito.mock(Player.class);
        Mockito.when(speedI.getWalkSpeed()).thenReturn(0.2F);
        Mockito.when(speedI.isSprinting()).thenReturn(true);
        Mockito.when(speedI.isOnGround()).thenReturn(true);
        Mockito.when(speedI.getActivePotionEffects()).thenReturn(
                Collections.singleton(new PotionEffect(PotionEffectType.SPEED, 600, 0)));

        Player speedII = Mockito.mock(Player.class);
        Mockito.when(speedII.getWalkSpeed()).thenReturn(0.2F);
        Mockito.when(speedII.isSprinting()).thenReturn(true);
        Mockito.when(speedII.isOnGround()).thenReturn(true);
        Mockito.when(speedII.getActivePotionEffects()).thenReturn(
                Collections.singleton(new PotionEffect(PotionEffectType.SPEED, 600, 1)));

        Assert.assertTrue(PotionUtil.combinedSpeedOffsetAllowance(speedII)
                > PotionUtil.combinedSpeedOffsetAllowance(speedI));
    }

    @Test
    public void aiMoveSpeedUsesWalkSpeed() {
        MovementPlayer mp = new MovementPlayer();
        mp.movementSpeed = 0.12F;
        mp.sprinting = true;
        double speed = PredictionEngine.aiMoveSpeed(mp);
        Assert.assertEquals(0.12D * 1.3D, speed, 0.001D);
    }

    @Test
    public void aiMoveSpeedSpeedIISprint() {
        MovementPlayer mp = new MovementPlayer();
        mp.movementSpeed = 0.14F;
        mp.sprinting = true;
        double speed = PredictionEngine.aiMoveSpeed(mp);
        Assert.assertEquals(0.14D * 1.3D, speed, 0.001D);
    }
}
