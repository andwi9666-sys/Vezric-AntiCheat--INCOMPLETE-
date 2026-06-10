package com.colin.vezanticheat.data;

import org.junit.Assert;
import org.junit.Test;

import java.util.UUID;

/**
 * Position-packet-per-tick bucketing must key off the server tick id (not wall-clock/50), so the
 * count resets exactly when the tick changes and accumulates within a single tick regardless of
 * how the wall clock behaves under lag.
 */
public class PositionPacketBucketTest {

    @Test
    public void accumulatesWithinSameTick() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.incrementPositionPacketsThisTick(100L);
        data.incrementPositionPacketsThisTick(100L);
        data.incrementPositionPacketsThisTick(100L);
        Assert.assertEquals(3, data.getPositionPacketsThisTick());
    }

    @Test
    public void resetsWhenTickAdvances() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.incrementPositionPacketsThisTick(100L);
        data.incrementPositionPacketsThisTick(100L);
        Assert.assertEquals(2, data.getPositionPacketsThisTick());

        // New tick id -> bucket resets, then counts this packet.
        data.incrementPositionPacketsThisTick(101L);
        Assert.assertEquals(1, data.getPositionPacketsThisTick());
    }

    @Test
    public void laggySkippedTickStillBucketsCorrectly() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.incrementPositionPacketsThisTick(500L);
        // Several ticks pass with no packets (server lag); next burst is a fresh tick.
        data.incrementPositionPacketsThisTick(520L);
        data.incrementPositionPacketsThisTick(520L);
        Assert.assertEquals(2, data.getPositionPacketsThisTick());
    }

    @Test
    public void manualResetClearsBucket() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.incrementPositionPacketsThisTick(7L);
        data.incrementPositionPacketsThisTick(7L);
        data.resetPositionPacketsThisTick();
        Assert.assertEquals(0, data.getPositionPacketsThisTick());
    }
}
