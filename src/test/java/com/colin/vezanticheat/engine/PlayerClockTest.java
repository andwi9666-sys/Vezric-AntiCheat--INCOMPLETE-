package com.colin.vezanticheat.engine;

import org.junit.Assert;
import org.junit.Test;

public class PlayerClockTest {

    @Test
    public void tickMsIsFifty() {
        Assert.assertEquals(50L, PlayerClock.TICK_MS);
    }

    @Test
    public void evaluateDriftZeroWithoutAnchor() {
        Assert.assertEquals(0L, PlayerClock.evaluateDrift(null, null));
    }
}
