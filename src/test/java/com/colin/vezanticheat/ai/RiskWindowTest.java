package com.colin.vezanticheat.ai;

import org.junit.Assert;
import org.junit.Test;

/**
 * Pure window-eviction / decay math for the risk store.
 */
public class RiskWindowTest {

    @Test
    public void decayReducesScoreLinearlyOverTime() {
        // 10 points, 1 pt/sec, after 4 seconds -> 6.
        double decayed = RiskWindow.decay(10.0D, 0L + 1L, 4001L, 1.0D);
        Assert.assertEquals(6.0D, decayed, 1.0E-6D);
    }

    @Test
    public void decayNeverGoesNegativeAndSnapsToZero() {
        double decayed = RiskWindow.decay(2.0D, 1L, 100_000L, 1.0D);
        Assert.assertEquals(0.0D, decayed, 0.0D);
    }

    @Test
    public void decayWithNoAnchorReturnsScoreUnchanged() {
        Assert.assertEquals(5.0D, RiskWindow.decay(5.0D, 0L, 9999L, 1.0D), 0.0D);
        Assert.assertEquals(5.0D, RiskWindow.decay(5.0D, -1L, 9999L, 1.0D), 0.0D);
    }

    @Test
    public void decayWithZeroElapsedIsNoOp() {
        Assert.assertEquals(5.0D, RiskWindow.decay(5.0D, 1000L, 1000L, 1.0D), 0.0D);
        Assert.assertEquals(5.0D, RiskWindow.decay(5.0D, 1000L, 500L, 1.0D), 0.0D);
    }

    @Test
    public void doesNotEvictWhileScoreRemains() {
        // Score still positive even though stale -> keep.
        Assert.assertFalse(RiskWindow.shouldEvict(3.0D, 1L, 1_000_000L, 45_000L));
    }

    @Test
    public void doesNotEvictZeroScoreInsideWindow() {
        long now = 100_000L;
        long lastUpdate = now - 10_000L; // 10s ago, window 45s -> still in window
        Assert.assertFalse(RiskWindow.shouldEvict(0.0D, lastUpdate, now, 45_000L));
    }

    @Test
    public void evictsZeroScorePastWindow() {
        long now = 100_000L;
        long lastUpdate = now - 60_000L; // 60s ago, window 45s -> evict
        Assert.assertTrue(RiskWindow.shouldEvict(0.0D, lastUpdate, now, 45_000L));
    }

    @Test
    public void evictsNeverSeenState() {
        Assert.assertTrue(RiskWindow.shouldEvict(0.0D, 0L, 100_000L, 45_000L));
    }

    @Test
    public void decayThenEvictModelsRollingWindow() {
        // Player flagged once (score 5) then went quiet. After the score fully decays AND the
        // entry ages past the window, it must be eligible for eviction.
        long flagTime = 0L;
        double score = 5.0D;
        long now = 200_000L; // 200s later
        double decayed = RiskWindow.decay(score, flagTime + 1L, now, 0.35D);
        Assert.assertEquals(0.0D, decayed, 0.0D);
        Assert.assertTrue(RiskWindow.shouldEvict(decayed, flagTime + 1L, now, 45_000L));
    }
}
