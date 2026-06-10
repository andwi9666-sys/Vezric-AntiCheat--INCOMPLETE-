package com.colin.vezanticheat.utils;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CombatUtilExpansionTest {

    private World world;

    @Before
    public void setUp() {
        world = Mockito.mock(World.class);
    }

    @Test
    public void expandedHitboxImprovesLookDotNearEdge() {
        Location eye = new Location(world, 0.0, 1.62, 0.0);
        eye.setDirection(new Vector(0.995, -0.05, 0.08));
        Location base = new Location(world, 3.0, 0.0, 0.0);

        double raw = CombatUtil.lookDotToHitbox(eye, base, 0.6, 1.8, 0.0);
        double expanded = CombatUtil.lookDotToHitbox(eye, base, 0.6, 1.8, CombatUtil.VANILLA_HITBOX_EXPANSION);

        assertTrue(expanded >= raw);
    }

    @Test
    public void buildCombatAabbExpandsWidthAndHeightSymmetrically() {
        Location base = new Location(world, 0.0, 0.0, 0.0);
        CombatAabb box = CombatUtil.buildCombatAabb(base, 0.6, 1.8, CombatUtil.VANILLA_HITBOX_EXPANSION);

        assertEquals(0.8D, box.effectiveWidth(), 0.001D);
        assertEquals(2.0D, box.effectiveHeight(), 0.001D);
        assertEquals(-0.1D, box.minY, 0.001D);
        assertEquals(1.9D, box.maxY, 0.001D);
    }

    @Test
    public void downwardYExpansionReducesDistanceBelowFeet() {
        Location base = new Location(world, 0.0, 0.0, 0.0);
        Location eye = new Location(world, 0.0, -0.05, 0.0);

        double raw = CombatUtil.distanceToHitbox(eye, base, 0.6, 1.8, 0.0);
        double expanded = CombatUtil.distanceToHitbox(eye, base, 0.6, 1.8, CombatUtil.VANILLA_HITBOX_EXPANSION);

        assertEquals(0.05D, raw, 0.001D);
        assertEquals(0.0D, expanded, 0.001D);
    }

    @Test
    public void distanceAndRayTraceAgreeOnExpandedFrontFaceHit() {
        Location base = new Location(world, 0.0, 0.0, 0.0);
        Location eye = new Location(world, 0.35, 0.9, 0.0);
        eye.setDirection(new Vector(1, 0, 0));

        double distance = CombatUtil.distanceToHitbox(eye, base, 0.6, 1.8, CombatUtil.VANILLA_HITBOX_EXPANSION);
        CombatUtil.RayTraceResult ray = CombatUtil.rayTraceToHitbox(
                eye, base, 0.6, 1.8, CombatUtil.VANILLA_HITBOX_EXPANSION, 6.0D);

        assertEquals(0.0D, distance, 0.001D);
        assertTrue(ray == null || ray.isHit());
    }

    @Test
    public void rayTraceHitsToeRegionWithVanillaExpansion() {
        Location base = new Location(world, 3.0, 0.0, 0.0);
        Location eye = new Location(world, 0.0, -0.05, 0.0);
        eye.setDirection(new Vector(1, 0, 0));

        CombatUtil.RayTraceResult raw = CombatUtil.rayTraceToHitbox(eye, base, 0.6, 1.8, 0.0, 6.0D);
        CombatUtil.RayTraceResult expanded = CombatUtil.rayTraceToHitbox(
                eye, base, 0.6, 1.8, CombatUtil.VANILLA_HITBOX_EXPANSION, 6.0D);

        assertTrue(raw != null && !raw.isHit());
        assertTrue(expanded != null && expanded.isHit());
    }

    @Test
    public void expansionMarginHit_detectsToeRegion() {
        Location base = new Location(world, 3.0, 0.0, 0.0);
        Location eye = new Location(world, 0.0, -0.05, 0.0);
        eye.setDirection(new Vector(1, 0, 0));

        assertTrue(CombatUtil.isLegitExpansionMarginHit(eye, base, 0.6, 1.8));
    }

    @Test
    public void expansionMarginHit_rejectsCenterHit() {
        Location base = new Location(world, 0.0, 0.0, 0.0);
        Location eye = new Location(world, -2.0, 0.9, 0.0);
        eye.setDirection(new Vector(1, 0, 0));

        assertFalse(CombatUtil.isLegitExpansionMarginHit(eye, base, 0.6, 1.8));
    }

    @Test
    public void expansionMarginHit_rejectsClearMiss() {
        Location base = new Location(world, 3.0, 0.0, 0.0);
        Location eye = new Location(world, 0.0, 1.62, 0.0);
        eye.setDirection(new Vector(0, 0, 1));

        assertFalse(CombatUtil.isLegitExpansionMarginHit(eye, base, 0.6, 1.8));
    }
}
