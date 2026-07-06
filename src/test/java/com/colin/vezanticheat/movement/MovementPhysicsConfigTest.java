package com.colin.vezanticheat.movement;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MovementPhysicsConfigTest {

    @Test
    public void speedPotionScalesByTwentyPercentPerLevelWithoutCap() {
        MovementPhysicsConfig cfg = MovementPhysicsConfig.from(null);
        Player player = mock(Player.class);
        when(player.getWalkSpeed()).thenReturn(0.2F);
        when(player.getActivePotionEffects()).thenReturn(Collections.singletonList(
                new PotionEffect(PotionEffectType.SPEED, 200, 5)));

        assertEquals(0.22D, PotionResolver.movementAttribute(player, cfg), 0.0001D);
    }

    @Test
    public void jumpBoostAddsPointOnePerLevelWithoutCap() {
        MovementPhysicsConfig cfg = MovementPhysicsConfig.from(null);
        Player player = mock(Player.class);
        when(player.getActivePotionEffects()).thenReturn(Collections.singletonList(
                new PotionEffect(PotionEffectType.JUMP, 200, 5)));

        assertEquals(1.02D, PotionResolver.jumpVelocity(player, cfg), 0.0001D);
    }
}
