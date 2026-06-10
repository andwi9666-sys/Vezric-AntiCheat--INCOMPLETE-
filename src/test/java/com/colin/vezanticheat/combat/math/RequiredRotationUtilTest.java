package com.colin.vezanticheat.combat.math;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;

public class RequiredRotationUtilTest {

    private World world;

    @Before
    public void setUp() {
        world = Mockito.mock(World.class);
    }

    @Test
    public void facingHitStaysWithinTolerance() {
        Location eye = new Location(world, 0.0D, 1.62D, 0.0D);
        Location feet = new Location(world, 3.0D, 0.0D, 0.0D);
        BoundingBox box = BoundingBox.fromFeet(feet, 0.6D, 1.8D);

        RequiredRotationUtil.Result result = RequiredRotationUtil.evaluate(eye, -90.0F, 0.0F, box, 40);
        assertFalse(result.exceedsTolerance());
        assertTrue(result.getCombinedError() < result.getHitboxAngularRadius() + result.getPingTolerance());
    }

    @Test
    public void silentAimFacingAwayExceedsTolerance() {
        Location eye = new Location(world, 0.0D, 1.62D, 0.0D);
        Location feet = new Location(world, 3.0D, 0.0D, 0.0D);
        BoundingBox box = BoundingBox.fromFeet(feet, 0.6D, 1.8D);

        RequiredRotationUtil.Result result = RequiredRotationUtil.evaluate(eye, 90.0F, 0.0F, box, 40);
        assertTrue(result.exceedsTolerance());
        assertTrue(result.getCombinedError() > 45.0D);
    }

    @Test
    public void snapThresholdScalesWithPing() {
        double lowPing = RequiredRotationUtil.snapAngularVelocityThreshold(40);
        double highPing = RequiredRotationUtil.snapAngularVelocityThreshold(180);
        assertTrue(highPing > lowPing);
        assertTrue(lowPing >= 800.0D);
    }
}
