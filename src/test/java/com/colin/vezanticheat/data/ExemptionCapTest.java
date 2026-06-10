package com.colin.vezanticheat.data;

import org.junit.Assert;
import org.junit.Test;

import java.util.UUID;

public class ExemptionCapTest {

    @Test
    public void blockChangeLenienceCapsPerWindow() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        long now = 10_000L;
        Assert.assertTrue(data.tryConsumeBlockChangeLenience(now, 2, 2000L));
        Assert.assertTrue(data.tryConsumeBlockChangeLenience(now + 10L, 2, 2000L));
        Assert.assertFalse(data.tryConsumeBlockChangeLenience(now + 20L, 2, 2000L));
        Assert.assertTrue(data.tryConsumeBlockChangeLenience(now + 2500L, 2, 2000L));
    }

    @Test
    public void combatGraceCapsPerWindow() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        long now = 5_000L;
        Assert.assertTrue(data.tryConsumeCombatGraceTick(now, 3, 2000L));
        Assert.assertTrue(data.tryConsumeCombatGraceTick(now + 5L, 3, 2000L));
        Assert.assertTrue(data.tryConsumeCombatGraceTick(now + 10L, 3, 2000L));
        Assert.assertFalse(data.tryConsumeCombatGraceTick(now + 15L, 3, 2000L));
    }
}
