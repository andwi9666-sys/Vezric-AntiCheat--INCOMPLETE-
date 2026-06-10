package com.colin.vezanticheat.engine;

import org.junit.Assert;
import org.junit.Test;

/**
 * Verifies the LONG-WINDOW OFFSET ADVANTAGE accumulator (Bukkit-free math in
 * {@link OffsetAdvantageAccumulator}): sustained sub-threshold offset must climb past a flag level
 * over many ticks, clean ticks must bleed it off only SLOWLY (not a hard reset), and the cap must
 * bound it.
 */
public class OffsetAdvantageTest {

    private static final double THRESHOLD = 0.001D;
    private static final double CLEAN_DECAY = 0.25D; // per clean second
    private static final long TICK = 50L;            // 50ms = 20 tps
    private static final double CAP = 2.0D;

    @Test
    public void sustainedSubThresholdSpeedAccrues() {
        // Tiny per-tick offset (0.004) that would never trip an instantaneous threshold of, say,
        // 0.02 still steadily climbs over time so sustained ~1.003-1.006 ratio speed is caught.
        double adv = 0.0D;
        for (int i = 0; i < 60; i++) { // ~3 seconds of cheating
            adv = OffsetAdvantageAccumulator.advance(adv, 0.004D, THRESHOLD, CLEAN_DECAY, TICK, CAP);
        }
        // Each tick gains (0.004 - 0.001) = 0.003; 60 ticks ~ 0.18 (no clean decay since every tick
        // gains). Well above a typical flag level.
        Assert.assertTrue("expected accrual to climb, got " + adv, adv > 0.15D);
    }

    @Test
    public void cleanTickDecaysSlowlyNotReset() {
        // Build up some advantage, then feed clean ticks. A SINGLE clean second must remove only
        // ~25%, not wipe it (a hard reset would zero it instantly).
        double adv = 1.0D;
        // One full second of clean ticks = 20 ticks * 50ms.
        for (int i = 0; i < 20; i++) {
            adv = OffsetAdvantageAccumulator.advance(adv, 0.0D, THRESHOLD, CLEAN_DECAY, TICK, CAP);
        }
        // 20 ticks each decaying by 0.25 * 0.05s = 1.25% => 0.9875^20 ~ 0.778 of original.
        Assert.assertTrue("clean second should not wipe advantage, got " + adv, adv > 0.5D);
        Assert.assertTrue("clean second should reduce advantage, got " + adv, adv < 1.0D);
    }

    @Test
    public void doesNotGoNegative() {
        double adv = 0.001D;
        for (int i = 0; i < 1000; i++) {
            adv = OffsetAdvantageAccumulator.advance(adv, 0.0D, THRESHOLD, CLEAN_DECAY, TICK, CAP);
        }
        Assert.assertTrue(adv >= 0.0D);
        Assert.assertTrue("should decay toward zero, got " + adv, adv < 0.001D);
    }

    @Test
    public void respectsCap() {
        double adv = 0.0D;
        for (int i = 0; i < 10000; i++) {
            adv = OffsetAdvantageAccumulator.advance(adv, 0.5D, THRESHOLD, CLEAN_DECAY, TICK, CAP);
        }
        Assert.assertEquals(CAP, adv, 1.0E-9D);
    }

    @Test
    public void longGapDecayIsBoundedToOneSecond() {
        // A single very-long gap (e.g. 10s) must not bleed off more than one second of decay, so a
        // cheater cannot wipe the ledger by stalling.
        double adv = 1.0D;
        double afterLongGap = OffsetAdvantageAccumulator.advance(adv, 0.0D, THRESHOLD, CLEAN_DECAY, 10_000L, CAP);
        // Max one second of decay = 25% => 0.75 remaining.
        Assert.assertEquals(0.75D, afterLongGap, 1.0E-9D);
    }

    @Test
    public void zeroGapTreatedAsOneTick() {
        // A 0ms reported gap falls back to one tick of decay (TICK_MS), never zero decay or NaN.
        double adv = 1.0D;
        double after = OffsetAdvantageAccumulator.advance(adv, 0.0D, THRESHOLD, CLEAN_DECAY, 0L, CAP);
        Assert.assertTrue(after < 1.0D && after > 0.95D);
    }

    @Test
    public void thresholdGatesAccrual() {
        // Offset exactly at threshold contributes no gain.
        double adv = OffsetAdvantageAccumulator.advance(0.0D, THRESHOLD, THRESHOLD, CLEAN_DECAY, TICK, CAP);
        Assert.assertEquals(0.0D, adv, 1.0E-12D);
    }
}
