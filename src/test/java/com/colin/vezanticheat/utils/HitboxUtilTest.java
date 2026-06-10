package com.colin.vezanticheat.utils;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HitboxUtilTest {

    private World world;

    @Before
    public void setUp() {
        world = Mockito.mock(World.class);
    }

    @Test
    public void bestRayTracePrefersHitCandidate() {
        Location base = new Location(world, 0.0, 0.0, 3.0);
        Location hitEye = new Location(world, 0.0, 1.62, 0.0);
        hitEye.setDirection(new Vector(0, 0, 1));

        Location missEye = new Location(world, 0.0, 1.62, 0.0);
        missEye.setDirection(new Vector(1, 0, 0));

        CombatUtil.RayTraceResult result = HitboxUtil.bestRayTraceToHitbox(
                Arrays.asList(missEye, hitEye),
                base,
                0.6,
                1.8,
                CombatUtil.VANILLA_HITBOX_EXPANSION,
                6.0
        );

        assertNotNull(result);
        assertTrue(result.isHit());
    }
}
