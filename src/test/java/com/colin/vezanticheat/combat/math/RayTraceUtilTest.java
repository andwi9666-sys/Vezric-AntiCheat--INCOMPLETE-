package com.colin.vezanticheat.combat.math;

import org.bukkit.util.Vector;
import org.junit.Assert;
import org.junit.Test;

public class RayTraceUtilTest {

    @Test
    public void getLookVectorIsNormalized() {
        Vector look = RayTraceUtil.getLookVector(-90.0F, 0.0F);
        Assert.assertEquals(1.0D, look.length(), 0.001D);
    }

    @Test
    public void getLookVectorFacesPositiveXAtYawNegative90() {
        Vector look = RayTraceUtil.getLookVector(-90.0F, 0.0F);
        Assert.assertTrue(look.getX() > 0.9D);
        Assert.assertEquals(0.0D, look.getY(), 0.001D);
        Assert.assertEquals(0.0D, look.getZ(), 0.001D);
    }

    @Test
    public void rayIntersectsBoxThroughCenter() {
        BoundingBox box = new BoundingBox(2.7D, 0.0D, -0.3D, 3.3D, 1.8D, 0.3D);
        Vector origin = new Vector(0.0D, 0.9D, 0.0D);
        Vector direction = RayTraceUtil.getLookVector(-90.0F, 0.0F);
        Assert.assertTrue(RayTraceUtil.rayIntersectsBox(origin, direction, box, 6.0D));
    }

    @Test
    public void traceRayReturnsHitPointAndDistance() {
        BoundingBox box = new BoundingBox(2.7D, 0.0D, -0.3D, 3.3D, 1.8D, 0.3D);
        Vector origin = new Vector(0.0D, 0.9D, 0.0D);
        Vector direction = RayTraceUtil.getLookVector(-90.0F, 0.0F);

        RayTraceResult result = RayTraceUtil.traceRay(origin, direction, box, 6.0D);
        Assert.assertTrue(result.isHit());
        Assert.assertNotNull(result.getHitPoint());
        Assert.assertEquals(2.7D, result.getHitPoint().getX(), 0.001D);
        Assert.assertTrue(result.getDistance() > 0.0D);
        Assert.assertTrue(result.getDistance() < 6.0D);
    }

    @Test
    public void traceRayMissReturnsMissResult() {
        BoundingBox box = new BoundingBox(10.0D, 0.0D, -0.3D, 10.6D, 1.8D, 0.3D);
        Vector origin = new Vector(0.0D, 0.9D, 0.0D);
        Vector direction = RayTraceUtil.getLookVector(-90.0F, 0.0F);

        RayTraceResult result = RayTraceUtil.traceRay(origin, direction, box, 6.0D);
        Assert.assertFalse(result.isHit());
        Assert.assertNull(result.getHitPoint());
    }

    @Test
    public void distanceEyeToBoxMatchesBoundingBoxDistance() {
        BoundingBox box = new BoundingBox(3.0D, 0.0D, -0.3D, 3.6D, 1.8D, 0.3D);
        Vector eye = new Vector(0.0D, 1.62D, 0.0D);
        double expected = box.distanceTo(eye);
        Assert.assertEquals(expected, RayTraceUtil.distanceEyeToBox(eye, box), 0.001D);
    }
}
