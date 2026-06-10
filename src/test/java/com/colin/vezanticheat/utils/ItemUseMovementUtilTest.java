package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.data.PlayerData;
import org.junit.Assert;
import org.junit.Test;

public class ItemUseMovementUtilTest {

    @Test
    public void beginEatingResetsAdvantage() {
        PlayerData data = new PlayerData(java.util.UUID.randomUUID());
        data.setEngineOffsetAdvantage(0.85D);
        long now = System.currentTimeMillis();
        ItemUseMovementUtil.beginEating(null, data, now);
        Assert.assertEquals(0.0D, data.getEngineOffsetAdvantage(), 0.001D);
        Assert.assertTrue(data.getLastEatStart() > 0L);
        Assert.assertTrue(ItemUseMovementUtil.suppressesMovementFlags(data, now));
    }

    @Test
    public void inputScaleBlendsFromFullToSlow() {
        PlayerData data = new PlayerData(java.util.UUID.randomUUID());
        long start = 1_000_000L;
        ItemUseMovementUtil.beginEating(null, data, start);
        Assert.assertEquals(1.0D, ItemUseMovementUtil.movementInputScale(data, start), 0.001D);
        double mid = ItemUseMovementUtil.movementInputScale(data, start + 375L);
        Assert.assertTrue(mid > 0.5D && mid < 0.7D);
        Assert.assertEquals(0.2D, ItemUseMovementUtil.movementInputScale(data, start + 800L), 0.001D);
    }

    @Test
    public void postConsumeGraceUsesFullInput() {
        PlayerData data = new PlayerData(java.util.UUID.randomUUID());
        long now = System.currentTimeMillis();
        data.markEatMovementGrace(500L);
        Assert.assertEquals(1.0D, ItemUseMovementUtil.movementInputScale(data, now), 0.001D);
        Assert.assertTrue(ItemUseMovementUtil.suppressesMovementFlags(data, now));
    }
}
