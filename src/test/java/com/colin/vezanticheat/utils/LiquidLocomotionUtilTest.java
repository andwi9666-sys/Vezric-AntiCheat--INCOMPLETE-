package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.engine.CompensatedWorld;
import org.bukkit.Material;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LiquidLocomotionUtilTest {

    @Test
    public void nearbyLadderWithoutIntersectionIsNotClimbable() {
        CompensatedWorld world = new CompensatedWorld();
        world.updateBlock(5, 64, 0, Material.LADDER, (byte) 0);

        assertFalse(LiquidLocomotionUtil.isOnClimbable(world, 3.5D, 64.0D, 0.5D));
    }

    @Test
    public void overlappingLadderIsClimbable() {
        CompensatedWorld world = new CompensatedWorld();
        world.updateBlock(5, 64, 0, Material.LADDER, (byte) 0);

        assertTrue(LiquidLocomotionUtil.isOnClimbable(world, 4.7D, 64.0D, 0.5D));
    }

    @Test
    public void vanillaClimbSpeedsAreLegit() {
        double maxDy = LiquidLocomotionUtil.DEFAULT_LADDER_LEGIT_MAX_DY;
        double maxVOff = LiquidLocomotionUtil.DEFAULT_LADDER_LEGIT_MAX_VOFF;

        assertTrue(LiquidLocomotionUtil.isLegitLadderMotion(
                LiquidLocomotionUtil.VANILLA_LADDER_CLIMB_DY, 0.05D, maxDy, maxVOff));
        assertTrue(LiquidLocomotionUtil.isLegitLadderMotion(
                LiquidLocomotionUtil.VANILLA_LADDER_PULLUP_DY, 0.08D, maxDy, maxVOff));
    }

    @Test
    public void blatantFastLadderDetected() {
        double maxDy = LiquidLocomotionUtil.DEFAULT_LADDER_LEGIT_MAX_DY;
        double maxVOff = LiquidLocomotionUtil.DEFAULT_LADDER_LEGIT_MAX_VOFF;
        double blatantDy = LiquidLocomotionUtil.DEFAULT_LADDER_BLATANT_DY;

        assertFalse(LiquidLocomotionUtil.isLegitLadderMotion(0.30D, 0.05D, maxDy, maxVOff));
        assertTrue(LiquidLocomotionUtil.isBlatantFastLadder(0.30D, 0.05D, maxDy, blatantDy, maxVOff));
    }
}
