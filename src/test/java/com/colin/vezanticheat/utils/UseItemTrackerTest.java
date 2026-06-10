package com.colin.vezanticheat.utils;

import org.junit.Assert;
import org.junit.Test;

public class UseItemTrackerTest {

    @Test
    public void notUsingWhenInactive() {
        com.colin.vezanticheat.data.PlayerData data = new com.colin.vezanticheat.data.PlayerData(java.util.UUID.randomUUID());
        Assert.assertFalse(UseItemTracker.isUsingItem(null, data));
    }

    @Test
    public void usingWhenFlagSet() {
        com.colin.vezanticheat.data.PlayerData data = new com.colin.vezanticheat.data.PlayerData(java.util.UUID.randomUUID());
        UseItemTracker.noteUseItem(data, System.currentTimeMillis());
        Assert.assertTrue(data.isUseItemActive());
        UseItemTracker.noteStopUse(data);
        Assert.assertFalse(data.isUseItemActive());
    }

    @Test
    public void jumpMeleeDoesNotConflictWithStaleUseFlag() {
        com.colin.vezanticheat.data.PlayerData data = new com.colin.vezanticheat.data.PlayerData(java.util.UUID.randomUUID());
        long now = 10_000L;
        data.setLastJumpTime(now - 120L);
        data.setLastArmSwingPacket(now - 40L);
        UseItemTracker.noteUseItem(data, now - 800L);
        Assert.assertTrue(data.isUseItemActive());
        Assert.assertFalse(UseItemTracker.isAttackConflictingItemUse(null, data, now));
    }

    @Test
    public void freshSwordBlockPacketIsNotSustainedUse() {
        com.colin.vezanticheat.data.PlayerData data = new com.colin.vezanticheat.data.PlayerData(java.util.UUID.randomUUID());
        long now = 20_000L;
        data.setAutoBlockALastBlockStartMs(now - 30L);
        data.setLastArmSwingPacket(now - 20L);
        Assert.assertFalse(UseItemTracker.isAttackConflictingItemUse(null, data, now));
    }

    @Test
    public void reconcileMeleeAttackClearsStaleUseFlag() {
        com.colin.vezanticheat.data.PlayerData data = new com.colin.vezanticheat.data.PlayerData(java.util.UUID.randomUUID());
        long now = 30_000L;
        data.setLastJumpTime(now - 100L);
        data.setLastArmSwingPacket(now - 30L);
        UseItemTracker.noteUseItem(data, now - 500L);
        UseItemTracker.reconcileMeleeAttack(null, data, now);
        Assert.assertFalse(data.isUseItemActive());
    }

    @Test
    public void sustainedSwordBlockDoesNotConflictWithAttack() {
        com.colin.vezanticheat.data.PlayerData data = new com.colin.vezanticheat.data.PlayerData(java.util.UUID.randomUUID());
        long now = 40_000L;
        data.setAutoBlockALastBlockStartMs(now - 500L);
        Assert.assertFalse(UseItemTracker.isAttackConflictingItemUse(null, data, now));
    }

    @Test
    public void swordSpamBlockReleaseBurstIsNotFakeRelease() {
        com.colin.vezanticheat.data.PlayerData data = new com.colin.vezanticheat.data.PlayerData(java.util.UUID.randomUUID());
        long now = 50_000L;
        data.setAutoBlockALastBlockStartMs(now - 120L);
        data.setLastArmSwingPacket(now - 40L);
        data.setLastReleaseUseItemMs(now - 20L);
        data.setReleaseUseItemStreak(3);
        Assert.assertFalse(UseItemTracker.isFakeReleaseUse(null, data, now));
    }

    @Test
    public void legitSwordBlockSessionDetectedFromAutoBlockTimestamp() {
        com.colin.vezanticheat.data.PlayerData data = new com.colin.vezanticheat.data.PlayerData(java.util.UUID.randomUUID());
        long now = 60_000L;
        data.setAutoBlockALastBlockStartMs(now - 200L);
        Assert.assertTrue(UseItemTracker.isLegitSwordBlockSession(null, data, now));
    }
}
