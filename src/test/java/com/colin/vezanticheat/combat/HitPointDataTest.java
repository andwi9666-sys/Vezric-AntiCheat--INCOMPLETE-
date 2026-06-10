package com.colin.vezanticheat.combat;

import com.colin.vezanticheat.combat.math.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.Assert;
import org.junit.Test;

public class HitPointDataTest {

    @Test
    public void centerTorsoHitIsCenterLike() {
        BoundingBox box = new BoundingBox(2.7D, 0.0D, -0.3D, 3.3D, 1.8D, 0.3D);
        Vector hitPoint = new Vector(3.0D, 1.0D, 0.0D);

        HitPointData data = HitPointData.fromHit(
                hitPoint, box, true, HitboxExpansionTier.NORMAL, 100L);

        Assert.assertNotNull(data);
        Assert.assertEquals(0.5D, data.getRelativeX(), 0.001D);
        Assert.assertEquals(0.555D, data.getRelativeY(), 0.01D);
        Assert.assertEquals(0.5D, data.getRelativeZ(), 0.001D);
        Assert.assertTrue(data.isCenterLikeHit());
        Assert.assertFalse(data.isEdgeHit());
        Assert.assertFalse(data.isExpansionShellHit());
    }

    @Test
    public void edgeHitNearBoxCorner() {
        BoundingBox box = new BoundingBox(2.7D, 0.0D, -0.3D, 3.3D, 1.8D, 0.3D);
        Vector hitPoint = new Vector(2.75D, 0.1D, -0.25D);

        HitPointData data = HitPointData.fromHit(
                hitPoint, box, true, HitboxExpansionTier.NORMAL, 200L);

        Assert.assertNotNull(data);
        Assert.assertTrue(data.isEdgeHit());
        Assert.assertFalse(data.isCenterLikeHit());
        Assert.assertFalse(data.isExpansionShellHit());
    }

    @Test
    public void expansionOnlyHitFlagsShell() {
        BoundingBox box = new BoundingBox(2.7D, 0.0D, -0.3D, 3.3D, 1.8D, 0.3D);
        Vector hitPoint = new Vector(3.35D, 0.9D, 0.0D);

        HitPointData data = HitPointData.fromHit(
                hitPoint, box, false, HitboxExpansionTier.FULL, 300L);

        Assert.assertNotNull(data);
        Assert.assertTrue(data.isExpansionShellHit());
        Assert.assertTrue(data.getRelativeX() > 1.0D);
    }

    @Test
    public void nullHitPointReturnsNull() {
        BoundingBox box = new BoundingBox(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
        Assert.assertNull(HitPointData.fromHit(null, box, true, HitboxExpansionTier.NORMAL, 1L));
    }
}
