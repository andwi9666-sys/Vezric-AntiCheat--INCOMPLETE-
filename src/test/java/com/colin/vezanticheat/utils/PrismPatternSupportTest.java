package com.colin.vezanticheat.utils;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Unit tests for Polar-style click/scaffold pattern analyzers.
 */
public class PrismPatternSupportTest {

    @Test
    public void clickPatternEmptyReturnsEmpty() {
        Deque<Long> swings = new ArrayDeque<Long>();
        PrismPatternSupport.ClickPatternScore score =
                PrismPatternSupport.analyzeClickPattern(null, "PrismAutoClickD", swings, 36);
        Assert.assertEquals(0, score.samples);
        Assert.assertFalse(score.triangleLike);
    }

    @Test
    public void clickPatternFakeRandomIntervalsClusterInMiddleBand() {
        Deque<Long> swings = new ArrayDeque<Long>();
        long t = 1_000_000L;
        // Fake-random 8-14 CPS: mostly 85-105ms (9.5-11.7 CPS), rare extremes
        int[] intervals = {88, 92, 95, 90, 93, 91, 94, 89, 92, 95, 90, 93, 91, 88, 92, 94,
                90, 93, 91, 89, 92, 95, 90, 93};
        swings.add(t);
        for (int interval : intervals) {
            t += interval;
            swings.add(t);
        }

        PrismPatternSupport.ClickPatternScore score =
                PrismPatternSupport.analyzeClickPattern(null, "PrismAutoClickD", swings, 36);
        Assert.assertTrue("samples=" + score.samples + " occ=" + score.bandOccupancy
                        + " extreme=" + score.extremeHitRatio,
                score.samples >= 12 && score.bandOccupancy >= 0.5D);
    }

    @Test
    public void bridgeHumanImperfectionBlocksSuspiciousPattern() {
        com.colin.vezanticheat.data.PlayerData data = new com.colin.vezanticheat.data.PlayerData(
                java.util.UUID.randomUUID());
        ScaffoldUtil.Stats imperfect = new ScaffoldUtil.Stats(125, 22, 0.18, 95, 160);
        Assert.assertTrue(PrismPatternSupport.hasBridgeHumanImperfection(
                imperfect, 8.0, 0.08, 4.0, 75.0));
    }

    @Test
    public void scaffoldPatternRequiresStreakBeforeSuspicious() {
        com.colin.vezanticheat.data.PlayerData data = new com.colin.vezanticheat.data.PlayerData(
                java.util.UUID.randomUUID());
        data.setPrismPerfectPlacementStreak(3);
        ScaffoldUtil.Stats stats = new ScaffoldUtil.Stats(125, 5, 0.05, 120, 130);
        ScaffoldUtil.Context ctx = null;

        PrismPatternSupport.ScaffoldPatternScore low =
                PrismPatternSupport.analyzeScaffoldPattern(null, "PrismScaffoldA", data, ctx, stats);
        Assert.assertFalse(low.suspicious);

        data.setPrismPerfectPlacementStreak(8);
        PrismPatternSupport.ScaffoldPatternScore high =
                PrismPatternSupport.analyzeScaffoldPattern(null, "PrismScaffoldA", data, ctx, stats);
        Assert.assertTrue(high.suspicious);
    }
}
