package com.colin.vezanticheat.combat.math;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class BoundingBoxTest {

    private World world;

    @Before
    public void setUp() {
        world = Mockito.mock(World.class);
    }

    @Test
    public void fromFeetUsesHalfWidthAndHeight() {
        BoundingBox box = BoundingBox.fromFeet(new Location(world, 10.0D, 64.0D, -5.0D), 0.6D, 1.8D);
        Assert.assertEquals(9.7D, box.minX, 0.001D);
        Assert.assertEquals(10.3D, box.maxX, 0.001D);
        Assert.assertEquals(64.0D, box.minY, 0.001D);
        Assert.assertEquals(65.8D, box.maxY, 0.001D);
        Assert.assertEquals(-5.3D, box.minZ, 0.001D);
        Assert.assertEquals(-4.7D, box.maxZ, 0.001D);
    }

    @Test
    public void expandGrowsAllAxes() {
        BoundingBox base = new BoundingBox(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
        BoundingBox expanded = base.expand(0.1D);
        Assert.assertEquals(-0.1D, expanded.minX, 0.001D);
        Assert.assertEquals(1.1D, expanded.maxX, 0.001D);
        Assert.assertEquals(-0.1D, expanded.minY, 0.001D);
        Assert.assertEquals(1.1D, expanded.maxY, 0.001D);
    }

    @Test
    public void containsPointInsideAndOutside() {
        BoundingBox box = new BoundingBox(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
        Assert.assertTrue(box.contains(new Vector(0.5D, 0.5D, 0.5D)));
        Assert.assertFalse(box.contains(new Vector(2.0D, 0.5D, 0.5D)));
    }

    @Test
    public void distanceToCenterIsZero() {
        BoundingBox box = new BoundingBox(0.0D, 0.0D, 0.0D, 2.0D, 2.0D, 2.0D);
        Assert.assertEquals(0.0D, box.distanceTo(new Vector(1.0D, 1.0D, 1.0D)), 0.001D);
    }

    @Test
    public void distanceToCorner() {
        BoundingBox box = new BoundingBox(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
        Assert.assertEquals(Math.sqrt(3.0D), box.distanceTo(new Vector(2.0D, 2.0D, 2.0D)), 0.001D);
    }

    @Test
    public void intersectsRayHitsBoxAhead() {
        BoundingBox box = new BoundingBox(-0.5D, 0.0D, 4.5D, 0.5D, 2.0D, 5.5D);
        Vector origin = new Vector(0.0D, 1.0D, 0.0D);
        Vector direction = new Vector(0.0D, 0.0D, 1.0D);
        Assert.assertTrue(box.intersectsRay(origin, direction, 10.0D));
    }

    @Test
    public void intersectsRayMissesBox() {
        BoundingBox box = new BoundingBox(5.0D, 0.0D, 5.0D, 6.0D, 2.0D, 6.0D);
        Vector origin = new Vector(0.0D, 1.0D, 0.0D);
        Vector direction = new Vector(0.0D, 0.0D, 1.0D);
        Assert.assertFalse(box.intersectsRay(origin, direction, 10.0D));
    }

    @Test
    public void intersectsRayOriginInsideCountsAsHit() {
        BoundingBox box = new BoundingBox(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
        Vector origin = new Vector(0.5D, 0.5D, 0.5D);
        Vector direction = new Vector(1.0D, 0.0D, 0.0D);
        Assert.assertTrue(box.intersectsRay(origin, direction, 5.0D));
    }
}
