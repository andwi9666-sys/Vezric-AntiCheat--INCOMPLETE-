package com.colin.vezanticheat.tier.prism.scaffold;

import com.colin.vezanticheat.utils.ScaffoldUtil;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Proves the signal separation the silent scaffold system relies on: robotic placement timing and
 * repeated values collapse to near-zero variance, while genuine human bridging keeps natural spread.
 * These are the primitives behind the LegitScaffold consistency categories and the Eagle accumulators,
 * verified with no Bukkit dependency.
 */
public class ScaffoldDetectionTest {

    @Test
    public void meanStdIsZeroForIdenticalValuesAndPositiveForVaried() {
        double[] flat = ScaffoldAnalyzer.meanStd(Arrays.asList(50.0D, 50.0D, 50.0D, 50.0D));
        assertEquals(50.0D, flat[0], 1.0E-9D);
        assertEquals("identical samples must have zero std (robotic)", 0.0D, flat[1], 1.0E-9D);

        double[] varied = ScaffoldAnalyzer.meanStd(Arrays.asList(10.0D, 40.0D, 25.0D, 70.0D, 5.0D));
        assertTrue("human-varied samples must have real spread", varied[1] > 10.0D);
    }

    @Test
    public void roboticPlacementTimingHasLowCvHumanHasHigh() {
        // Robotic legit-scaffold: ~constant 100ms cadence with sub-ms jitter -> CV below the 0.075 gate.
        Deque<Long> robotic = new ArrayDeque<Long>();
        for (long v : new long[] {100L, 100L, 101L, 99L, 100L, 100L, 101L, 100L}) robotic.addLast(v);
        ScaffoldUtil.Stats rs = ScaffoldUtil.timingStats(robotic, 8);
        assertNotNull(rs);
        assertTrue("robotic cv should be < 0.075, was " + rs.cv, rs.cv < 0.075D);

        // Human bridging: natural variance -> CV above the 0.13 human gate, so it is never scored.
        Deque<Long> human = new ArrayDeque<Long>();
        for (long v : new long[] {90L, 140L, 75L, 160L, 110L, 200L, 85L, 130L}) human.addLast(v);
        ScaffoldUtil.Stats hs = ScaffoldUtil.timingStats(human, 8);
        assertNotNull(hs);
        assertTrue("human cv should be > 0.13, was " + hs.cv, hs.cv > 0.13D);
    }

    @Test
    public void timingStatsNeedsEnoughSamples() {
        Deque<Long> few = new ArrayDeque<Long>();
        few.addLast(100L);
        few.addLast(100L);
        assertNull("under the sample floor there is not enough evidence to score", ScaffoldUtil.timingStats(few, 7));
    }
}
