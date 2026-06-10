package com.colin.vezanticheat.combat.math;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class AngleUtilTest {

    private World world;

    @Before
    public void setUp() {
        world = Mockito.mock(World.class);
    }

    @Test
    public void getYawToFacesWest() {
        Vector from = new Vector(0.0D, 0.0D, 0.0D);
        Vector to = new Vector(-1.0D, 0.0D, 0.0D);
        Assert.assertEquals(90.0F, AngleUtil.getYawTo(from, to), 0.5F);
    }

    @Test
    public void getPitchToLevelIsZero() {
        Vector from = new Vector(0.0D, 1.0D, 0.0D);
        Vector to = new Vector(1.0D, 1.0D, 0.0D);
        Assert.assertEquals(0.0F, AngleUtil.getPitchTo(from, to), 0.5F);
    }

    @Test
    public void angleDifferenceWrapsAcrossZero() {
        Assert.assertEquals(2.0F, AngleUtil.angleDifference(359.0F, 1.0F), 0.001F);
        Assert.assertEquals(90.0F, AngleUtil.angleDifference(0.0F, 90.0F), 0.001F);
    }

    @Test
    public void getYawPitchErrorIsZeroWhenFacingTarget() {
        Location eye = new Location(world, 0.0D, 1.62D, 0.0D);
        BoundingBox target = BoundingBox.fromFeet(new Location(world, 3.0D, 0.0D, 0.0D), 0.6D, 1.8D);
        AngleUtil.YawPitchError error = AngleUtil.getYawPitchError(eye, -90.0F, 0.0F, target);
        Assert.assertTrue(error.getYawError() < 5.0D);
        Assert.assertTrue(error.getPitchError() < 5.0D);
    }
}
