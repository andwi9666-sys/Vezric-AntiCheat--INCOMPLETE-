package com.colin.vezanticheat.engine;

import org.junit.Assert;
import org.junit.Test;

/**
 * Verifies the COMPENSATION BUDGET CAP: {@link UncertaintyHandler#reduceOffset(double)} may never
 * subtract more than the configured leniency budget from a raw offset, even when many compensation
 * triggers are stacked together. Teleport lenience is exempt from the cap.
 */
public class UncertaintyBudgetTest {

    private MovementPlayer mp;
    private UncertaintyHandler u;

    private void fresh() {
        mp = new MovementPlayer();
        u = mp.uncertaintyHandler;
    }

    @Test
    public void singleTriggerStaysWithinCap() {
        fresh();
        u.leniencyBudgetCap = 0.12D;
        u.blockChangeTicks = 1; // +0.05 alone
        double reduced = u.reduceOffset(1.0D);
        Assert.assertEquals(0.95D, reduced, 1.0E-9D);
    }

    @Test
    public void stackedTriggersClampedToBudget() {
        fresh();
        u.leniencyBudgetCap = 0.12D;

        // Stack MANY non-web compensation triggers that together far exceed 0.12.
        mp.couldSkipTick = true;        // +0.06
        u.blockChangeTicks = 1;         // +0.05
        mp.onIce = true;                // +0.02
        mp.onClimbable = true;          // +0.05
        u.nearBoat = true;              // +0.04
        u.pistonPushTick = true;        // +0.05
        u.stepUpTick = true;            // +0.10
        u.slabEdgeTick = true;          // +0.08
        u.dropTick = true;              // +0.08
        u.knockbackGraceTick = true;    // +0.16
        u.combatMotionTick = true;      // +0.10
        u.stuckOnEdge = true;           // +0.05

        double reduced = u.reduceOffset(1.0D);
        Assert.assertEquals(1.0D - 0.12D, reduced, 1.0E-9D);
    }

    @Test
    public void aggressiveCapIsTighter() {
        fresh();
        u.leniencyBudgetCap = 0.08D; // aggressive profile
        mp.couldSkipTick = true;
        u.knockbackGraceTick = true;
        u.combatMotionTick = true;
        double reduced = u.reduceOffset(1.0D);
        Assert.assertEquals(1.0D - 0.08D, reduced, 1.0E-9D);
    }

    @Test
    public void webLenienceNotUnderCompensatedBelowCap() {
        // Cobweb physics needs the full 0.15 web term even under a 0.12 cap.
        fresh();
        u.leniencyBudgetCap = 0.12D;
        mp.inWeb = true; // +0.15 web term
        double reduced = u.reduceOffset(1.0D);
        Assert.assertEquals(1.0D - 0.15D, reduced, 1.0E-9D);
    }

    @Test
    public void couldSkipTickAndBlockChangeDoNotFullyStack() {
        fresh();
        u.leniencyBudgetCap = 0.12D;
        mp.couldSkipTick = true;
        u.blockChangeTicks = 1;
        double reduced = u.reduceOffset(1.0D);
        Assert.assertEquals(1.0D - 0.08D, reduced, 1.0E-9D);
    }

    @Test
    public void couldSkipTickUncertaintyNotDoubleCounted() {
        fresh();
        mp.couldSkipTick = true;
        u.blockChangeTicks = 1;
        Assert.assertTrue(u.getHorizontalUncertainty() < 0.10D);
    }

    @Test
    public void teleportLenienceExemptFromCap() {
        // A fresh teleport applies an extra 0.20 OUTSIDE the cap.
        fresh();
        u.leniencyBudgetCap = 0.12D;
        u.lastTeleportTicks = 0;
        u.blockChangeTicks = 1;          // +0.05
        u.knockbackGraceTick = true;     // +0.16 -> capped portion to 0.12
        double reduced = u.reduceOffset(1.0D);
        // 0.12 (capped compensation) + 0.20 (teleport, exempt) = 0.32 total.
        Assert.assertEquals(1.0D - 0.32D, reduced, 1.0E-9D);
    }

    @Test
    public void zeroCapMeansUncapped() {
        fresh();
        u.leniencyBudgetCap = 0.0D; // disabled
        u.knockbackGraceTick = true;     // +0.16
        u.combatMotionTick = true;       // +0.10
        double reduced = u.reduceOffset(1.0D);
        Assert.assertEquals(1.0D - 0.26D, reduced, 1.0E-9D);
    }

    @Test
    public void reducedNeverNegative() {
        fresh();
        u.leniencyBudgetCap = 0.12D;
        u.knockbackGraceTick = true;
        Assert.assertEquals(0.0D, u.reduceOffset(0.01D), 1.0E-9D);
    }
}
