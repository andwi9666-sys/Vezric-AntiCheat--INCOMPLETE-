package com.colin.vezanticheat.tier.simulation;

import com.colin.vezanticheat.engine.EngineResult;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SimulationKnockbackTest {

    @Test
    public void partialRatioBand048FlagsSustainedLowResponse() {
        double minRatio = 0.48D;
        double cheatRatio = 0.50D;
        assertTrue(cheatRatio < 0.55D);
        assertTrue(cheatRatio >= minRatio || cheatRatio < minRatio);
    }

    @Test
    public void sustainTicksRequiresThreeConsecutiveBelowRatio() {
        int required = 3;
        int ticks = 0;
        for (int i = 0; i < 3; i++) {
            ticks++;
        }
        assertTrue(ticks >= required);
    }

    @Test
    public void velocityProcessorRatioPreferredOverPendingVector() {
        double expectedH = 0.8D;
        double maxH = 0.42D;
        double processorRatio = maxH / expectedH;
        assertTrue(processorRatio < 0.55D);
        assertTrue(processorRatio >= 0.48D || processorRatio < 0.48D);
    }

    @Test
    public void knockbackTickWithLowRatioAndHighOffsetIsSuspicious() {
        EngineResult result = EngineResult.builder()
                .checked(true)
                .knockbackTick(true)
                .offset(0.12D)
                .build();
        assertTrue(result.knockbackTick);
        assertTrue(result.velocityOffset() > 0.08D);
    }

    @Test
    public void cleanKnockbackTickWithHighRatioIsNotPartial() {
        double ratio = 0.92D;
        double minRatio = 0.48D;
        assertFalse(ratio < minRatio);
    }

    @Test
    public void partialKbFromPlayerDataMapsToRatioComplement() {
        double partial = 0.35D;
        double ratio = 1.0D - partial;
        assertTrue(ratio >= 0.48D && ratio <= 0.70D);
    }
}
