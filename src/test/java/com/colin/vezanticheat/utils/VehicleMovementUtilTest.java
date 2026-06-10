package com.colin.vezanticheat.utils;

import org.junit.Assert;
import org.junit.Test;

public class VehicleMovementUtilTest {

    @Test
    public void exceedsEnvelopeWhenAboveTolerance() {
        Assert.assertTrue(VehicleMovementUtil.exceedsEnvelope(0.80D, 0.50D, 1.15D));
        Assert.assertFalse(VehicleMovementUtil.exceedsEnvelope(0.50D, 0.50D, 1.15D));
    }

    @Test
    public void violationStreakResetsOnCleanTick() {
        Assert.assertEquals(0, VehicleMovementUtil.nextViolationStreak(3, false, 0));
        Assert.assertEquals(4, VehicleMovementUtil.nextViolationStreak(3, true, 0));
    }

    @Test
    public void shouldFlagAtBufferThreshold() {
        Assert.assertFalse(VehicleMovementUtil.shouldFlag(3, 4));
        Assert.assertTrue(VehicleMovementUtil.shouldFlag(4, 4));
    }
}
