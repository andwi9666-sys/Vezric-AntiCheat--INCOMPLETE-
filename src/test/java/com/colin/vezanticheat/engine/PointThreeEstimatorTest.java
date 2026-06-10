package com.colin.vezanticheat.engine;

import org.junit.Assert;
import org.junit.Test;

public class PointThreeEstimatorTest {

    @Test
    public void positionlessAlwaysSkips() {
        MovementPlayer mp = new MovementPlayer();
        PointThreeEstimator est = new PointThreeEstimator(mp);
        Assert.assertTrue(est.determineCanSkipTick(false));
    }

    @Test
    public void webAllowsSkip() {
        MovementPlayer mp = new MovementPlayer();
        mp.inWeb = true;
        mp.clientVelocity.setX(0.5);
        PointThreeEstimator est = new PointThreeEstimator(mp);
        Assert.assertTrue(est.determineCanSkipTick(true));
    }

    @Test
    public void injectZeroWhenNearStationary() {
        MovementPlayer mp = new MovementPlayer();
        mp.clientVelocity.setX(0.01);
        mp.clientVelocity.setY(0.01);
        PointThreeEstimator est = new PointThreeEstimator(mp);
        Assert.assertTrue(est.shouldInjectZeroMovement());
    }
}
