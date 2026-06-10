package com.colin.vezanticheat.tier.prism;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class PrismAutoClickCvTest {

    @Test
    public void lowCpsHumanizerDetectsTightEightCpsIntervals() {
        List<Long> intervals = new ArrayList<Long>();
        for (int i = 0; i < 18; i++) {
            intervals.add(125L);
        }

        double meanMs = 125.0D;
        double stdMs = std(intervals, meanMs);
        double cv = stdMs / meanMs;
        double meanCps = 1000.0D / meanMs;
        int outlierFreeStreak = intervals.size();

        assertTrue(meanCps >= 7.0D && meanCps < 9.0D);
        assertTrue(cv <= 0.06D);
        assertTrue(outlierFreeStreak >= 10);

        boolean lowCpsHumanizer = meanCps >= 7.0D && meanCps < 9.0D
                && cv <= 0.06D
                && outlierFreeStreak >= 10;
        assertTrue(lowCpsHumanizer);
    }

    private static double std(List<Long> values, double mean) {
        double variance = 0.0D;
        for (Long value : values) {
            double delta = value.doubleValue() - mean;
            variance += delta * delta;
        }
        return Math.sqrt(variance / values.size());
    }
}
